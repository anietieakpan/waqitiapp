package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.compliance.service.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.common.notification.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade consumer for failed KYC expiration events
 * Handles critical KYC compliance failures with immediate regulatory escalation
 * Provides emergency compliance protection and customer service recovery
 *
 * Critical for: KYC compliance, regulatory reporting, customer onboarding
 * SLA: Must process failed KYC expirations within 10 seconds for compliance continuity
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KycExpirationFailedConsumer {

    private final KycComplianceService kycComplianceService;
    private final CustomerComplianceService customerComplianceService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final ComplianceIncidentService complianceIncidentService;
    private final CustomerOnboardingService customerOnboardingService;
    private final DocumentVerificationService documentVerificationService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 72-hour TTL for compliance events
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 72;

    // Metrics
    private final Counter failedKycExpirationCounter = Counter.builder("kyc_expiration_failed_processed_total")
            .description("Total number of failed KYC expiration events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter criticalKycFailureCounter = Counter.builder("critical_kyc_expiration_failures_total")
            .description("Total number of critical KYC expiration failures processed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("kyc_expiration_failed_processing_duration")
            .description("Time taken to process failed KYC expiration events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"compliance.kyc.expiration.failed"},
        groupId = "compliance-service-kyc-expiration-failed-processor",
        containerFactory = "criticalComplianceKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "kyc-expiration-failed-processor", fallbackMethod = "handleKycExpirationFailedFailure")
    @Retry(name = "kyc-expiration-failed-processor")
    public void processKycExpirationFailed(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();

        log.error("KYC EXPIRATION FAILED: Processing failed KYC expiration: {} from topic: {} partition: {} offset: {}",
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("KYC expiration failed event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate KYC expiration failure data
            KycExpirationFailureData kycData = extractKycFailureData(event.getPayload());
            validateKycFailureData(kycData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Critical compliance and customer service assessment
            KycFailureComplianceAssessment assessment = assessKycFailureCompliance(kycData, event);

            // Process failed KYC expiration with compliance procedures
            processFailedKycExpiration(kycData, assessment, event);

            // Record successful processing metrics
            failedKycExpirationCounter.increment();

            if ("CRITICAL".equals(assessment.getComplianceLevel())) {
                criticalKycFailureCounter.increment();
            }

            // Audit the KYC failure processing
            auditKycExpirationFailureProcessing(kycData, event, assessment, "SUCCESS");

            log.error("KYC EXPIRATION FAILED: Successfully processed KYC failure: {} - Compliance: {} Customer: {} Action: {}",
                    eventId, assessment.getComplianceLevel(), kycData.getCustomerId(),
                    assessment.getComplianceAction());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("KYC EXPIRATION FAILED: Invalid KYC expiration failure data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("KYC EXPIRATION FAILED: Failed to process KYC expiration failure event: {}", eventId, e);
            auditKycExpirationFailureProcessing(null, event, null, "FAILED: " + e.getMessage());
            throw new RuntimeException("KYC expiration failure processing failed", e);

        } finally {
            sample.stop(processingTimer);
            cleanupIdempotencyCache();
        }
    }

    private boolean isEventAlreadyProcessed(String eventId) {
        Instant processedTime = processedEventIds.get(eventId);
        if (processedTime != null) {
            if (ChronoUnit.HOURS.between(processedTime, Instant.now()) < IDEMPOTENCY_TTL_HOURS) {
                return true;
            } else {
                processedEventIds.remove(eventId);
            }
        }
        return false;
    }

    private void markEventAsProcessed(String eventId) {
        processedEventIds.put(eventId, Instant.now());
    }

    private void cleanupIdempotencyCache() {
        Instant cutoff = Instant.now().minus(IDEMPOTENCY_TTL_HOURS, ChronoUnit.HOURS);
        processedEventIds.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private KycExpirationFailureData extractKycFailureData(Map<String, Object> payload) {
        return KycExpirationFailureData.builder()
                .eventId(extractString(payload, "eventId"))
                .kycRecordId(extractString(payload, "kycRecordId"))
                .customerId(extractString(payload, "customerId"))
                .accountId(extractString(payload, "accountId"))
                .documentType(extractString(payload, "documentType"))
                .documentId(extractString(payload, "documentId"))
                .expirationDate(extractInstant(payload, "expirationDate"))
                .failureReason(extractString(payload, "failureReason"))
                .originalExpirationData(extractMap(payload, "originalExpirationData"))
                .failedTimestamp(extractInstant(payload, "failedTimestamp"))
                .customerTier(extractString(payload, "customerTier"))
                .complianceStatus(extractString(payload, "complianceStatus"))
                .riskLevel(extractString(payload, "riskLevel"))
                .jurisdictions(extractStringList(payload, "jurisdictions"))
                .regulatoryRequirements(extractStringList(payload, "regulatoryRequirements"))
                .complianceFlags(extractStringList(payload, "complianceFlags"))
                .businessImpact(extractString(payload, "businessImpact"))
                .customerProfile(extractMap(payload, "customerProfile"))
                .documentStatus(extractString(payload, "documentStatus"))
                .verificationLevel(extractString(payload, "verificationLevel"))
                .gracePeriodExpired(extractBoolean(payload, "gracePeriodExpired"))
                .automaticRenewalFailed(extractBoolean(payload, "automaticRenewalFailed"))
                .customerNotificationFailed(extractBoolean(payload, "customerNotificationFailed"))
                .relatedAccounts(extractStringList(payload, "relatedAccounts"))
                .activeTransactions(extractBoolean(payload, "activeTransactions"))
                .highValueCustomer(extractBoolean(payload, "highValueCustomer"))
                .build();
    }

    private void validateKycFailureData(KycExpirationFailureData kycData) {
        if (kycData.getEventId() == null || kycData.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }

        if (kycData.getKycRecordId() == null || kycData.getKycRecordId().trim().isEmpty()) {
            throw new IllegalArgumentException("KYC record ID is required");
        }

        if (kycData.getCustomerId() == null || kycData.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID is required");
        }

        if (kycData.getDocumentType() == null || kycData.getDocumentType().trim().isEmpty()) {
            throw new IllegalArgumentException("Document type is required");
        }

        List<String> validDocumentTypes = List.of(
            "PASSPORT", "NATIONAL_ID", "DRIVERS_LICENSE", "UTILITY_BILL",
            "BANK_STATEMENT", "BUSINESS_LICENSE", "INCORPORATION_DOCUMENT",
            "TAX_DOCUMENT", "PROOF_OF_ADDRESS", "EMPLOYMENT_VERIFICATION"
        );
        if (!validDocumentTypes.contains(kycData.getDocumentType())) {
            throw new IllegalArgumentException("Invalid document type: " + kycData.getDocumentType());
        }

        if (kycData.getFailureReason() == null || kycData.getFailureReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Failure reason is required");
        }

        List<String> validComplianceStatuses = List.of("COMPLIANT", "NON_COMPLIANT", "PENDING", "EXPIRED", "SUSPENDED");
        if (kycData.getComplianceStatus() != null && !validComplianceStatuses.contains(kycData.getComplianceStatus())) {
            throw new IllegalArgumentException("Invalid compliance status: " + kycData.getComplianceStatus());
        }

        List<String> validRiskLevels = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
        if (kycData.getRiskLevel() != null && !validRiskLevels.contains(kycData.getRiskLevel())) {
            throw new IllegalArgumentException("Invalid risk level: " + kycData.getRiskLevel());
        }
    }

    private KycFailureComplianceAssessment assessKycFailureCompliance(KycExpirationFailureData kycData, GenericKafkaEvent event) {
        // Critical compliance assessment for failed KYC expirations
        String complianceLevel = determineComplianceLevel(kycData);
        String complianceAction = determineComplianceAction(kycData, complianceLevel);
        boolean requiresImmediateAction = requiresImmediateAction(kycData, complianceLevel);
        List<String> complianceMeasures = determineComplianceMeasures(kycData, complianceLevel);
        String customerImpact = assessCustomerImpact(kycData);

        return KycFailureComplianceAssessment.builder()
                .complianceLevel(complianceLevel)
                .complianceAction(complianceAction)
                .requiresImmediateAction(requiresImmediateAction)
                .complianceMeasures(complianceMeasures)
                .customerImpact(customerImpact)
                .executiveEscalation(determineExecutiveEscalation(kycData, complianceLevel))
                .customerNotificationRequired(determineCustomerNotification(kycData))
                .regulatoryReportingRequired(determineRegulatoryReporting(kycData))
                .accountSuspensionRequired(determineAccountSuspension(kycData, complianceLevel))
                .documentRenewalRequired(determineDocumentRenewal(kycData))
                .complianceIncidentRequired(determineComplianceIncident(kycData, complianceLevel))
                .gracePeriodExtensionAvailable(determineGracePeriodExtension(kycData))
                .build();
    }

    private String determineComplianceLevel(KycExpirationFailureData kycData) {
        if ("CRITICAL".equals(kycData.getRiskLevel()) ||
            kycData.getHighValueCustomer() ||
            kycData.getActiveTransactions() ||
            kycData.getGracePeriodExpired()) {
            return "CRITICAL";
        } else if ("HIGH".equals(kycData.getRiskLevel()) ||
                   "VIP".equals(kycData.getCustomerTier()) ||
                   kycData.getComplianceFlags().contains("REGULATORY_ATTENTION")) {
            return "HIGH";
        } else if ("MEDIUM".equals(kycData.getRiskLevel()) ||
                   kycData.getAutomaticRenewalFailed()) {
            return "ELEVATED";
        } else {
            return "STANDARD";
        }
    }

    private String determineComplianceAction(KycExpirationFailureData kycData, String complianceLevel) {
        if ("CRITICAL".equals(complianceLevel)) {
            if (kycData.getActiveTransactions()) {
                return "IMMEDIATE_ACCOUNT_RESTRICTION_WITH_GRACE";
            } else if (kycData.getHighValueCustomer()) {
                return "PRIORITY_DOCUMENT_RENEWAL_PROCESS";
            } else {
                return "IMMEDIATE_COMPLIANCE_SUSPENSION";
            }
        } else if ("HIGH".equals(complianceLevel)) {
            return "EXPEDITED_DOCUMENT_COLLECTION";
        } else {
            return "STANDARD_RENEWAL_PROCESS";
        }
    }

    private boolean requiresImmediateAction(KycExpirationFailureData kycData, String complianceLevel) {
        return "CRITICAL".equals(complianceLevel) ||
               kycData.getActiveTransactions() ||
               kycData.getHighValueCustomer() ||
               kycData.getGracePeriodExpired();
    }

    private List<String> determineComplianceMeasures(KycExpirationFailureData kycData, String complianceLevel) {
        if ("CRITICAL".equals(complianceLevel)) {
            return List.of(
                "ACCOUNT_TRANSACTION_RESTRICTION",
                "EMERGENCY_DOCUMENT_COLLECTION",
                "REGULATORY_NOTIFICATION",
                "CUSTOMER_PRIORITY_CONTACT",
                "COMPLIANCE_INCIDENT_CREATION",
                "ENHANCED_MONITORING",
                "GRACE_PERIOD_ASSESSMENT"
            );
        } else if ("HIGH".equals(complianceLevel)) {
            return List.of(
                "EXPEDITED_DOCUMENT_REQUEST",
                "CUSTOMER_NOTIFICATION",
                "COMPLIANCE_TRACKING",
                "RISK_ASSESSMENT_UPDATE"
            );
        } else {
            return List.of(
                "STANDARD_DOCUMENT_REQUEST",
                "CUSTOMER_NOTIFICATION",
                "COMPLIANCE_MONITORING"
            );
        }
    }

    private String assessCustomerImpact(KycExpirationFailureData kycData) {
        if (kycData.getActiveTransactions() || kycData.getHighValueCustomer()) {
            return "HIGH";
        } else if ("VIP".equals(kycData.getCustomerTier()) ||
                   kycData.getRelatedAccounts().size() > 1) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private boolean determineExecutiveEscalation(KycExpirationFailureData kycData, String complianceLevel) {
        return "CRITICAL".equals(complianceLevel) ||
               kycData.getHighValueCustomer() ||
               (kycData.getActiveTransactions() && "VIP".equals(kycData.getCustomerTier()));
    }

    private boolean determineCustomerNotification(KycExpirationFailureData kycData) {
        return !kycData.getCustomerNotificationFailed() ||
               kycData.getActiveTransactions() ||
               kycData.getHighValueCustomer();
    }

    private boolean determineRegulatoryReporting(KycExpirationFailureData kycData) {
        return kycData.getComplianceFlags().contains("REGULATORY_REPORTING_REQUIRED") ||
               kycData.getRegulatoryRequirements().contains("EXPIRATION_REPORTING") ||
               "CRITICAL".equals(kycData.getRiskLevel());
    }

    private boolean determineAccountSuspension(KycExpirationFailureData kycData, String complianceLevel) {
        return "CRITICAL".equals(complianceLevel) &&
               kycData.getGracePeriodExpired() &&
               !kycData.getActiveTransactions();
    }

    private boolean determineDocumentRenewal(KycExpirationFailureData kycData) {
        return !kycData.getAutomaticRenewalFailed() ||
               kycData.getHighValueCustomer() ||
               "VIP".equals(kycData.getCustomerTier());
    }

    private boolean determineComplianceIncident(KycExpirationFailureData kycData, String complianceLevel) {
        return "CRITICAL".equals(complianceLevel) ||
               kycData.getHighValueCustomer() ||
               kycData.getComplianceFlags().contains("INCIDENT_REQUIRED");
    }

    private boolean determineGracePeriodExtension(KycExpirationFailureData kycData) {
        return !kycData.getGracePeriodExpired() &&
               (kycData.getActiveTransactions() || kycData.getHighValueCustomer());
    }

    private void processFailedKycExpiration(KycExpirationFailureData kycData,
                                          KycFailureComplianceAssessment assessment,
                                          GenericKafkaEvent event) {
        log.error("KYC EXPIRATION FAILED: Processing failed KYC expiration - Customer: {}, Document: {}, Type: {}, Compliance: {}",
                kycData.getCustomerId(), kycData.getDocumentId(),
                kycData.getDocumentType(), assessment.getComplianceLevel());

        try {
            // Create compliance incident if required
            String incidentId = null;
            if (assessment.isComplianceIncidentRequired()) {
                incidentId = complianceIncidentService.createKycExpirationIncident(kycData, assessment);
            }

            // Execute immediate compliance measures
            executeComplianceMeasures(kycData, assessment, incidentId);

            // Send immediate notifications
            sendImmediateNotifications(kycData, assessment, incidentId);

            // Account suspension if required
            if (assessment.isAccountSuspensionRequired()) {
                suspendAccount(kycData, assessment, incidentId);
            }

            // Document renewal process if required
            if (assessment.isDocumentRenewalRequired()) {
                initiateDocumentRenewal(kycData, assessment, incidentId);
            }

            // Grace period extension if available
            if (assessment.isGracePeriodExtensionAvailable()) {
                extendGracePeriod(kycData, assessment, incidentId);
            }

            // Regulatory reporting if required
            if (assessment.isRegulatoryReportingRequired()) {
                initiateRegulatoryReporting(kycData, assessment, incidentId);
            }

            // Customer notification if required
            if (assessment.isCustomerNotificationRequired()) {
                notifyCustomer(kycData, assessment);
            }

            // Executive escalation if required
            if (assessment.isExecutiveEscalation()) {
                escalateToExecutives(kycData, assessment, incidentId);
            }

            log.error("KYC EXPIRATION FAILED: KYC failure processed - IncidentId: {}, ComplianceApplied: {}, CustomerImpact: {}",
                    incidentId, assessment.getComplianceMeasures().size(), assessment.getCustomerImpact());

        } catch (Exception e) {
            log.error("KYC EXPIRATION FAILED: Failed to process KYC expiration failure for customer: {}", kycData.getCustomerId(), e);

            // Emergency fallback procedures
            executeEmergencyFallback(kycData, e);

            throw new RuntimeException("KYC expiration failure processing failed", e);
        }
    }

    private void executeComplianceMeasures(KycExpirationFailureData kycData,
                                         KycFailureComplianceAssessment assessment,
                                         String incidentId) {
        // Execute compliance action based on assessment
        switch (assessment.getComplianceAction()) {
            case "IMMEDIATE_ACCOUNT_RESTRICTION_WITH_GRACE":
                customerComplianceService.restrictAccountWithGrace(kycData.getAccountId(), incidentId);
                kycComplianceService.initiateEmergencyDocumentCollection(kycData.getCustomerId(), incidentId);
                break;

            case "PRIORITY_DOCUMENT_RENEWAL_PROCESS":
                customerOnboardingService.initiatePriorityDocumentRenewal(kycData.getCustomerId(), kycData.getDocumentType(), incidentId);
                break;

            case "IMMEDIATE_COMPLIANCE_SUSPENSION":
                customerComplianceService.immediateComplianceSuspension(kycData.getAccountId(), incidentId);
                break;

            case "EXPEDITED_DOCUMENT_COLLECTION":
                kycComplianceService.initiateExpeditedDocumentCollection(kycData.getCustomerId(), kycData.getDocumentType(), incidentId);
                break;

            default:
                kycComplianceService.standardRenewalProcess(kycData.getKycRecordId(), kycData);
        }

        // Apply additional compliance measures
        for (String measure : assessment.getComplianceMeasures()) {
            try {
                switch (measure) {
                    case "ACCOUNT_TRANSACTION_RESTRICTION":
                        customerComplianceService.restrictTransactions(kycData.getAccountId(), incidentId);
                        break;

                    case "EMERGENCY_DOCUMENT_COLLECTION":
                        documentVerificationService.initiateEmergencyCollection(
                                kycData.getCustomerId(), kycData.getDocumentType(), incidentId);
                        break;

                    case "REGULATORY_NOTIFICATION":
                        regulatoryReportingService.notifyKycExpiration(kycData, assessment, incidentId);
                        break;

                    case "CUSTOMER_PRIORITY_CONTACT":
                        customerOnboardingService.priorityCustomerContact(kycData.getCustomerId(), kycData, incidentId);
                        break;

                    case "ENHANCED_MONITORING":
                        kycComplianceService.enableEnhancedMonitoring(kycData.getCustomerId(), incidentId);
                        break;

                    case "EXPEDITED_DOCUMENT_REQUEST":
                        documentVerificationService.expeditedDocumentRequest(
                                kycData.getCustomerId(), kycData.getDocumentType(), incidentId);
                        break;

                    case "RISK_ASSESSMENT_UPDATE":
                        kycComplianceService.updateRiskAssessment(kycData.getCustomerId(), kycData.getRiskLevel());
                        break;

                    default:
                        kycComplianceService.applyGenericComplianceMeasure(measure, kycData, incidentId);
                }
            } catch (Exception e) {
                log.error("Failed to apply compliance measure: {}", measure, e);
            }
        }
    }

    private void sendImmediateNotifications(KycExpirationFailureData kycData,
                                          KycFailureComplianceAssessment assessment,
                                          String incidentId) {
        // Critical compliance levels require immediate escalation
        if ("CRITICAL".equals(assessment.getComplianceLevel()) || assessment.isRequiresImmediateAction()) {
            notificationService.sendCriticalAlert(
                    "CRITICAL KYC EXPIRATION FAILURE - COMPLIANCE ACTION REQUIRED",
                    String.format("Critical KYC document expiration failed processing: Customer %s, Document: %s %s, Impact: %s",
                            kycData.getCustomerId(), kycData.getDocumentType(),
                            kycData.getDocumentId(), assessment.getCustomerImpact()),
                    Map.of(
                            "customerId", kycData.getCustomerId(),
                            "documentType", kycData.getDocumentType(),
                            "documentId", kycData.getDocumentId(),
                            "complianceLevel", assessment.getComplianceLevel(),
                            "customerImpact", assessment.getCustomerImpact(),
                            "incidentId", incidentId != null ? incidentId : "N/A",
                            "complianceAction", assessment.getComplianceAction()
                    )
            );

            // Page compliance team for critical failures
            notificationService.pageComplianceTeam(
                    "KYC_EXPIRATION_FAILURE_CRITICAL",
                    kycData.getDocumentType(),
                    assessment.getComplianceLevel(),
                    incidentId != null ? incidentId : "N/A"
            );

            // Page customer service for high-value customers
            if (kycData.getHighValueCustomer() || "VIP".equals(kycData.getCustomerTier())) {
                notificationService.pageCustomerServiceTeam(
                        "KYC_EXPIRATION_HIGH_VALUE_CUSTOMER",
                        kycData.getCustomerId(),
                        assessment.getCustomerImpact(),
                        incidentId != null ? incidentId : "N/A"
                );
            }
        }
    }

    private void suspendAccount(KycExpirationFailureData kycData,
                              KycFailureComplianceAssessment assessment,
                              String incidentId) {
        // Suspend account for critical compliance violations
        customerComplianceService.suspendAccountForKycExpiration(
                kycData.getAccountId(),
                kycData.getCustomerId(),
                kycData.getDocumentType(),
                incidentId
        );

        // Suspend related accounts if applicable
        for (String relatedAccountId : kycData.getRelatedAccounts()) {
            try {
                customerComplianceService.suspendRelatedAccount(
                        relatedAccountId,
                        kycData.getCustomerId(),
                        "RELATED_KYC_EXPIRATION",
                        incidentId
                );
            } catch (Exception e) {
                log.error("Failed to suspend related account: {}", relatedAccountId, e);
            }
        }
    }

    private void initiateDocumentRenewal(KycExpirationFailureData kycData,
                                       KycFailureComplianceAssessment assessment,
                                       String incidentId) {
        // Initiate document renewal process
        customerOnboardingService.initiateDocumentRenewal(
                kycData.getCustomerId(),
                kycData.getDocumentType(),
                kycData.getExpirationDate(),
                assessment.getCustomerImpact(),
                incidentId
        );

        // Set priority based on customer tier
        if (kycData.getHighValueCustomer() || "VIP".equals(kycData.getCustomerTier())) {
            customerOnboardingService.setPriorityRenewal(
                    kycData.getCustomerId(),
                    kycData.getDocumentType(),
                    "HIGH_VALUE_CUSTOMER"
            );
        }
    }

    private void extendGracePeriod(KycExpirationFailureData kycData,
                                 KycFailureComplianceAssessment assessment,
                                 String incidentId) {
        // Extend grace period for eligible customers
        kycComplianceService.extendGracePeriod(
                kycData.getKycRecordId(),
                kycData.getCustomerId(),
                assessment.getCustomerImpact(),
                incidentId
        );
    }

    private void initiateRegulatoryReporting(KycExpirationFailureData kycData,
                                           KycFailureComplianceAssessment assessment,
                                           String incidentId) {
        // Generate regulatory reports for KYC expiration failures
        for (String requirement : kycData.getRegulatoryRequirements()) {
            try {
                regulatoryReportingService.fileKycExpirationReport(
                        requirement,
                        kycData,
                        assessment,
                        incidentId
                );
            } catch (Exception e) {
                log.error("Failed to file KYC regulatory report: {}", requirement, e);
            }
        }

        // International reporting for multi-jurisdiction customers
        for (String jurisdiction : kycData.getJurisdictions()) {
            try {
                regulatoryReportingService.fileInternationalKycReport(
                        jurisdiction,
                        kycData,
                        assessment,
                        incidentId
                );
            } catch (Exception e) {
                log.error("Failed to file international KYC report for jurisdiction: {}", jurisdiction, e);
            }
        }
    }

    private void notifyCustomer(KycExpirationFailureData kycData, KycFailureComplianceAssessment assessment) {
        if (kycData.getCustomerId() != null) {
            String message;
            if ("CRITICAL".equals(assessment.getComplianceLevel())) {
                message = String.format("URGENT: Your %s document has expired and requires immediate renewal. " +
                        "Please upload a new document to avoid account restrictions.",
                        kycData.getDocumentType());
            } else {
                message = String.format("Your %s document has expired. Please upload a new document at your earliest convenience.",
                        kycData.getDocumentType());
            }

            notificationService.sendComplianceAlert(
                    kycData.getCustomerId(),
                    "Document Expiration - Action Required",
                    message,
                    Map.of(
                            "documentType", kycData.getDocumentType(),
                            "urgency", assessment.getComplianceLevel(),
                            "actionRequired", true
                    )
            );
        }
    }

    private void escalateToExecutives(KycExpirationFailureData kycData,
                                    KycFailureComplianceAssessment assessment,
                                    String incidentId) {
        notificationService.sendExecutiveAlert(
                "Critical KYC Expiration Failure - Executive Action Required",
                String.format("Critical KYC compliance failure requiring executive attention: Customer %s, Document: %s, Impact: %s",
                        kycData.getCustomerId(),
                        kycData.getDocumentType(),
                        assessment.getCustomerImpact()),
                Map.of(
                        "incidentId", incidentId != null ? incidentId : "N/A",
                        "customerId", kycData.getCustomerId(),
                        "complianceLevel", assessment.getComplianceLevel(),
                        "customerImpact", assessment.getCustomerImpact(),
                        "documentType", kycData.getDocumentType()
                )
        );
    }

    private void executeEmergencyFallback(KycExpirationFailureData kycData, Exception error) {
        log.error("EMERGENCY: Executing emergency KYC compliance fallback due to processing failure");

        try {
            // Emergency compliance protection
            customerComplianceService.emergencyKycProtection(
                    kycData.getCustomerId(),
                    kycData.getAccountId()
            );

            // Emergency notification
            notificationService.sendEmergencyAlert(
                    "CRITICAL: KYC Expiration Failure Processing Failed",
                    String.format("Failed to process critical KYC expiration failure for customer %s: %s",
                            kycData.getCustomerId(), error.getMessage())
            );

            // Manual intervention alert
            notificationService.escalateToManualIntervention(
                    kycData.getEventId(),
                    "KYC_EXPIRATION_FAILURE_PROCESSING_FAILED",
                    error.getMessage()
            );

        } catch (Exception e) {
            log.error("EMERGENCY: Emergency KYC compliance fallback procedures also failed", e);
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("KYC EXPIRATION FAILED: Validation failed for KYC expiration failure event: {}", event.getEventId(), e);

        auditService.auditSecurityEvent(
                "KYC_EXPIRATION_FAILURE_VALIDATION_ERROR",
                null,
                "KYC expiration failure validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );
    }

    private void auditKycExpirationFailureProcessing(KycExpirationFailureData kycData,
                                                   GenericKafkaEvent event,
                                                   KycFailureComplianceAssessment assessment,
                                                   String status) {
        try {
            auditService.auditSecurityEvent(
                    "KYC_EXPIRATION_FAILURE_PROCESSED",
                    kycData != null ? kycData.getCustomerId() : null,
                    String.format("KYC expiration failure processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "kycRecordId", kycData != null ? kycData.getKycRecordId() : "unknown",
                            "customerId", kycData != null ? kycData.getCustomerId() : "unknown",
                            "documentType", kycData != null ? kycData.getDocumentType() : "unknown",
                            "complianceLevel", assessment != null ? assessment.getComplianceLevel() : "unknown",
                            "complianceAction", assessment != null ? assessment.getComplianceAction() : "none",
                            "customerImpact", assessment != null ? assessment.getCustomerImpact() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit KYC expiration failure processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("CRITICAL: KYC expiration failure event sent to DLT - EventId: {}", event.getEventId());

        try {
            KycExpirationFailureData kycData = extractKycFailureData(event.getPayload());

            // Emergency compliance protection for DLT events
            customerComplianceService.emergencyKycProtection(
                    kycData.getCustomerId(),
                    kycData.getAccountId()
            );

            // Critical alert for DLT
            notificationService.sendEmergencyAlert(
                    "CRITICAL: KYC Expiration Failure in DLT",
                    "Critical KYC expiration failure could not be processed - immediate compliance action required"
            );

        } catch (Exception e) {
            log.error("Failed to handle KYC expiration failure DLT event: {}", event.getEventId(), e);
        }
    }

    public void handleKycExpirationFailedFailure(GenericKafkaEvent event, String topic, int partition,
                                                long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("EMERGENCY: Circuit breaker activated for KYC expiration failure processing - EventId: {}",
                event.getEventId(), e);

        try {
            KycExpirationFailureData kycData = extractKycFailureData(event.getPayload());

            // Emergency protection
            customerComplianceService.emergencyKycProtection(
                    kycData.getCustomerId(),
                    kycData.getAccountId()
            );

            // Emergency alert
            notificationService.sendEmergencyAlert(
                    "KYC Expiration Failure Circuit Breaker Open",
                    "KYC expiration failure processing is failing - compliance severely compromised"
            );

        } catch (Exception ex) {
            log.error("Failed to handle KYC expiration failure circuit breaker fallback", ex);
        }

        acknowledgment.acknowledge();
    }

    // Helper methods
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Boolean extractBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Map.of();
    }

    // Data classes
    @lombok.Data
    @lombok.Builder
    public static class KycExpirationFailureData {
        private String eventId;
        private String kycRecordId;
        private String customerId;
        private String accountId;
        private String documentType;
        private String documentId;
        private Instant expirationDate;
        private String failureReason;
        private Map<String, Object> originalExpirationData;
        private Instant failedTimestamp;
        private String customerTier;
        private String complianceStatus;
        private String riskLevel;
        private List<String> jurisdictions;
        private List<String> regulatoryRequirements;
        private List<String> complianceFlags;
        private String businessImpact;
        private Map<String, Object> customerProfile;
        private String documentStatus;
        private String verificationLevel;
        private Boolean gracePeriodExpired;
        private Boolean automaticRenewalFailed;
        private Boolean customerNotificationFailed;
        private List<String> relatedAccounts;
        private Boolean activeTransactions;
        private Boolean highValueCustomer;
    }

    @lombok.Data
    @lombok.Builder
    public static class KycFailureComplianceAssessment {
        private String complianceLevel;
        private String complianceAction;
        private boolean requiresImmediateAction;
        private List<String> complianceMeasures;
        private String customerImpact;
        private boolean executiveEscalation;
        private boolean customerNotificationRequired;
        private boolean regulatoryReportingRequired;
        private boolean accountSuspensionRequired;
        private boolean documentRenewalRequired;
        private boolean complianceIncidentRequired;
        private boolean gracePeriodExtensionAvailable;
    }
}