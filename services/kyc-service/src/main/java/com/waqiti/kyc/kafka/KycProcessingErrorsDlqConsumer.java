package com.waqiti.kyc.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.kyc.service.KycService;
import com.waqiti.kyc.service.DocumentVerificationService;
import com.waqiti.kyc.repository.KycVerificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.Map;

/**
 * DLQ Consumer for KYC processing errors that failed to process.
 * Handles critical customer identity verification failures with compliance implications.
 */
@Component
@Slf4j
public class KycProcessingErrorsDlqConsumer extends BaseDlqConsumer {

    private final KycService kycService;
    private final DocumentVerificationService documentVerificationService;
    private final KycVerificationRepository kycVerificationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KycProcessingErrorsDlqConsumer(DlqHandler dlqHandler,
                                         AuditService auditService,
                                         NotificationService notificationService,
                                         MeterRegistry meterRegistry,
                                         KycService kycService,
                                         DocumentVerificationService documentVerificationService,
                                         KycVerificationRepository kycVerificationRepository,
                                         KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.kycService = kycService;
        this.documentVerificationService = documentVerificationService;
        this.kycVerificationRepository = kycVerificationRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"kyc-processing-errors-dlq"},
        groupId = "kyc-processing-dlq-consumer-group",
        containerFactory = "criticalComplianceKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "kyc-processing-dlq", fallbackMethod = "handleKycProcessingDlqFallback")
    public void handleKycProcessingDlq(@Payload Object originalMessage,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                      @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                      @Header(KafkaHeaders.OFFSET) long offset,
                                      Acknowledgment acknowledgment,
                                      @Header Map<String, Object> headers) {

        log.info("Processing KYC processing errors DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String kycId = extractKycId(originalMessage);
            String userId = extractUserId(originalMessage);
            String verificationType = extractVerificationType(originalMessage);
            String verificationStatus = extractVerificationStatus(originalMessage);
            String documentType = extractDocumentType(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing KYC DLQ: kycId={}, userId={}, verificationType={}, status={}, messageId={}",
                kycId, userId, verificationType, verificationStatus, messageId);

            // Validate KYC verification status
            if (kycId != null) {
                validateKycVerificationStatus(kycId, messageId);
                assessComplianceRisk(kycId, userId, verificationType, originalMessage, messageId);
                handleKycComplianceFailure(kycId, verificationType, originalMessage, exceptionMessage, messageId);
            }

            // Generate domain-specific alerts
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Handle customer account implications
            assessCustomerAccountImpact(userId, verificationType, verificationStatus, messageId);

            // Handle document verification issues
            if (documentType != null) {
                handleDocumentVerificationIssues(kycId, userId, documentType, originalMessage, messageId);
            }

            // Handle specific KYC failure types
            handleSpecificKycFailure(verificationType, kycId, userId, originalMessage, messageId);

            // Trigger manual KYC review
            triggerManualKycReview(kycId, userId, verificationType, verificationStatus, messageId);

        } catch (Exception e) {
            log.error("Error in KYC processing DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "kyc-processing-errors-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "KYC_COMPLIANCE";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String verificationType = extractVerificationType(originalMessage);
        String verificationStatus = extractVerificationStatus(originalMessage);

        // Critical for customer onboarding or high-risk verifications
        if ("CUSTOMER_ONBOARDING".equals(verificationType) || "HIGH_RISK_VERIFICATION".equals(verificationType)) {
            return true;
        }

        // Critical if customer is pending verification
        return "PENDING_VERIFICATION".equals(verificationStatus) || "VERIFICATION_REQUIRED".equals(verificationStatus);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String kycId = extractKycId(originalMessage);
        String userId = extractUserId(originalMessage);
        String verificationType = extractVerificationType(originalMessage);
        String verificationStatus = extractVerificationStatus(originalMessage);
        String documentType = extractDocumentType(originalMessage);

        try {
            // KYC failures have compliance and regulatory implications
            String alertTitle = String.format("COMPLIANCE CRITICAL: KYC Processing Failed - %s", verificationType);
            String alertMessage = String.format(
                "⚠️ KYC COMPLIANCE FAILURE ⚠️\n\n" +
                "A KYC verification process has failed and requires immediate attention:\n\n" +
                "KYC ID: %s\n" +
                "User ID: %s\n" +
                "Verification Type: %s\n" +
                "Current Status: %s\n" +
                "Document Type: %s\n" +
                "Error: %s\n\n" +
                "🚨 COMPLIANCE RISKS:\n" +
                "• Customer may not be properly identified\n" +
                "• BSA/CIP compliance violation risk\n" +
                "• Potential unverified customer activity\n" +
                "• Regulatory examination findings risk\n\n" +
                "⚠️ IMMEDIATE ACTIONS REQUIRED:\n" +
                "1. Manual KYC review initiated\n" +
                "2. Customer account status evaluation\n" +
                "3. Service restriction assessment\n" +
                "4. Compliance documentation review",
                kycId != null ? kycId : "unknown",
                userId != null ? userId : "unknown",
                verificationType != null ? verificationType : "unknown",
                verificationStatus != null ? verificationStatus : "unknown",
                documentType != null ? documentType : "unknown",
                exceptionMessage
            );

            // Send compliance team alert
            notificationService.sendComplianceAlert(
                "KYC Processing Failure",
                alertMessage,
                "HIGH"
            );

            // Send KYC operations team alert
            notificationService.sendKycAlert(
                "KYC Verification System Failure",
                alertMessage,
                "CRITICAL"
            );

            // Send risk management alert for compliance risk
            notificationService.sendRiskManagementAlert(
                "KYC Compliance Risk",
                String.format("KYC verification failure for user %s creates compliance risk. " +
                    "Customer identity verification compromised.", userId),
                "HIGH"
            );

            // Send operational alert for immediate action
            notificationService.sendOperationalAlert(
                "KYC Operations - Manual Review Required",
                String.format("KYC verification failed for user %s. Manual verification required. " +
                    "Consider account restrictions pending verification.", userId),
                "HIGH"
            );

            // Customer notification for verification issues
            if (isCustomerFacingVerification(verificationType) && userId != null) {
                notificationService.sendNotification(userId,
                    "Identity Verification Update",
                    "We're experiencing a temporary delay with your identity verification process. " +
                    "Our team is working to complete your verification. We'll contact you if additional documents are needed.",
                    messageId);
            }

            // Legal team alert for compliance implications
            if (hasLegalImplications(verificationType)) {
                notificationService.sendLegalAlert(
                    "KYC Compliance - Legal Review Required",
                    String.format("KYC failure for %s may have regulatory compliance implications. " +
                        "Review BSA/CIP requirements.", verificationType),
                    "MEDIUM"
                );
            }

            // Audit team alert for documentation
            notificationService.sendAuditAlert(
                "KYC Audit Trail Issue",
                String.format("KYC verification failure affects compliance audit trail. " +
                    "Review documentation for KYC ID %s", kycId),
                "MEDIUM"
            );

        } catch (Exception e) {
            log.error("Failed to send KYC processing DLQ alerts: {}", e.getMessage());
        }
    }

    private void validateKycVerificationStatus(String kycId, String messageId) {
        try {
            var kycVerification = kycVerificationRepository.findById(kycId);
            if (kycVerification.isPresent()) {
                String status = kycVerification.get().getStatus();
                String riskLevel = kycVerification.get().getRiskLevel();
                String verificationType = kycVerification.get().getVerificationType();

                log.info("KYC verification status validation for DLQ: kycId={}, status={}, riskLevel={}, type={}, messageId={}",
                    kycId, status, riskLevel, verificationType, messageId);

                // Check for high-risk verifications
                if ("HIGH_RISK".equals(riskLevel) || "ENHANCED_DUE_DILIGENCE".equals(riskLevel)) {
                    log.warn("High-risk KYC verification in DLQ: kycId={}, riskLevel={}", kycId, riskLevel);

                    notificationService.sendComplianceAlert(
                        "High-Risk KYC Verification Failed",
                        String.format("High-risk KYC verification %s (risk: %s) has failed processing. " +
                            "Enhanced due diligence requirements may not be met.", kycId, riskLevel),
                        "HIGH"
                    );
                }

                // Check for critical verification types
                if ("CUSTOMER_ONBOARDING".equals(verificationType) || "ACCOUNT_OPENING".equals(verificationType)) {
                    notificationService.sendKycAlert(
                        "Customer Onboarding KYC Failed",
                        String.format("Customer onboarding KYC %s has failed. New customer may not be properly verified.", kycId),
                        "CRITICAL"
                    );
                }

                // Check for pending verifications
                if ("PENDING_DOCUMENTS".equals(status) || "UNDER_REVIEW".equals(status)) {
                    notificationService.sendOperationalAlert(
                        "Active KYC Verification Failed",
                        String.format("Active KYC verification %s has failed. Customer may be waiting for verification completion.", kycId),
                        "HIGH"
                    );
                }
            } else {
                log.error("KYC verification not found for DLQ: kycId={}, messageId={}", kycId, messageId);
            }
        } catch (Exception e) {
            log.error("Error validating KYC verification status for DLQ: kycId={}, error={}",
                kycId, e.getMessage());
        }
    }

    private void assessComplianceRisk(String kycId, String userId, String verificationType,
                                    Object originalMessage, String messageId) {
        try {
            log.info("Assessing compliance risk: kycId={}, userId={}, verificationType={}", kycId, userId, verificationType);

            // Check customer's verification status
            if (userId != null) {
                boolean isFullyVerified = kycService.isCustomerFullyVerified(userId);
                if (!isFullyVerified) {
                    log.warn("KYC DLQ for unverified customer: userId={}, kycId={}", userId, kycId);

                    notificationService.sendComplianceAlert(
                        "Unverified Customer - KYC Failure",
                        String.format("KYC verification failed for unverified customer %s. " +
                            "CIP compliance requirements may not be met.", userId),
                        "HIGH"
                    );

                    // Consider account restrictions
                    assessAccountRestrictions(userId, verificationType, messageId);
                }

                // Check for expired documents
                boolean hasExpiredDocuments = kycService.hasExpiredDocuments(userId);
                if (hasExpiredDocuments) {
                    log.warn("KYC DLQ for customer with expired documents: userId={}", userId);

                    notificationService.sendKycAlert(
                        "Expired Documents - KYC Failure",
                        String.format("KYC verification failed for customer %s with expired documents. " +
                            "Document renewal required.", userId),
                        "MEDIUM"
                    );
                }

                // Check for high-risk customer profile
                boolean isHighRiskCustomer = kycService.isHighRiskCustomer(userId);
                if (isHighRiskCustomer) {
                    notificationService.sendRiskManagementAlert(
                        "High-Risk Customer KYC Failure",
                        String.format("KYC verification failed for high-risk customer %s. " +
                            "Enhanced monitoring required.", userId),
                        "HIGH"
                    );
                }
            }

        } catch (Exception e) {
            log.error("Error assessing compliance risk: kycId={}, error={}", kycId, e.getMessage());
        }
    }

    private void handleKycComplianceFailure(String kycId, String verificationType, Object originalMessage,
                                          String exceptionMessage, String messageId) {
        try {
            // Record KYC compliance failure for audit
            kycService.recordKycComplianceFailure(kycId, Map.of(
                "failureType", "KYC_PROCESSING_DLQ",
                "verificationType", verificationType,
                "errorMessage", exceptionMessage,
                "messageId", messageId,
                "timestamp", Instant.now(),
                "regulatoryImpact", "BSA_CIP_COMPLIANCE_RISK",
                "requiresManualReview", true
            ));

            // Check if this failure affects CIP compliance
            boolean affectsCipCompliance = kycService.affectsCipCompliance(verificationType);
            if (affectsCipCompliance) {
                log.warn("KYC processing DLQ affects CIP compliance: kycId={}, verificationType={}",
                    kycId, verificationType);

                notificationService.sendComplianceAlert(
                    "CIP Compliance Risk - KYC Failure",
                    String.format("KYC failure for %s affects Customer Identification Program compliance. " +
                        "Review BSA requirements.", kycId),
                    "HIGH"
                );

                // Create compliance review record
                kafkaTemplate.send("compliance-review-queue", Map.of(
                    "reviewType", "KYC_CIP_COMPLIANCE",
                    "kycId", kycId,
                    "verificationType", verificationType,
                    "complianceRisk", "CIP_VIOLATION",
                    "priority", "HIGH",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error handling KYC compliance failure: kycId={}, error={}", kycId, e.getMessage());
        }
    }

    private void assessCustomerAccountImpact(String userId, String verificationType, String verificationStatus, String messageId) {
        try {
            if (userId != null && shouldRestrictAccount(verificationType, verificationStatus)) {
                log.warn("Customer account restrictions required due to KYC DLQ: userId={}, verificationType={}",
                    userId, verificationType);

                // Apply temporary account restrictions
                kycService.applyTemporaryRestrictions(userId, Map.of(
                    "restrictionReason", "KYC_PROCESSING_DLQ",
                    "verificationType", verificationType,
                    "verificationStatus", verificationStatus,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));

                notificationService.sendOperationalAlert(
                    "Customer Account Restricted - KYC Failure",
                    String.format("Customer %s account restricted due to KYC verification failure. " +
                        "Manual verification required for restriction removal.", userId),
                    "HIGH"
                );

                // Notify customer of restrictions
                notificationService.sendNotification(userId,
                    "Account Verification Required",
                    "Your account has temporary restrictions while we complete your identity verification. " +
                    "Please contact customer service to complete the verification process.",
                    messageId);
            }
        } catch (Exception e) {
            log.error("Error assessing customer account impact: userId={}, error={}", userId, e.getMessage());
        }
    }

    private void handleDocumentVerificationIssues(String kycId, String userId, String documentType,
                                                 Object originalMessage, String messageId) {
        try {
            // Handle specific document verification failures
            switch (documentType) {
                case "GOVERNMENT_ID":
                    handleGovernmentIdFailure(kycId, userId, originalMessage, messageId);
                    break;
                case "PROOF_OF_ADDRESS":
                    handleAddressProofFailure(kycId, userId, originalMessage, messageId);
                    break;
                case "INCOME_VERIFICATION":
                    handleIncomeVerificationFailure(kycId, userId, originalMessage, messageId);
                    break;
                case "BANK_STATEMENT":
                    handleBankStatementFailure(kycId, userId, originalMessage, messageId);
                    break;
                default:
                    log.info("No specific handling for document type: {}", documentType);
                    break;
            }

            // Record document verification failure
            documentVerificationService.recordVerificationFailure(kycId, documentType, Map.of(
                "failureReason", "KYC_DLQ_PROCESSING",
                "messageId", messageId,
                "timestamp", Instant.now()
            ));

        } catch (Exception e) {
            log.error("Error handling document verification issues: kycId={}, documentType={}, error={}",
                kycId, documentType, e.getMessage());
        }
    }

    private void handleSpecificKycFailure(String verificationType, String kycId, String userId,
                                        Object originalMessage, String messageId) {
        try {
            switch (verificationType) {
                case "CUSTOMER_ONBOARDING":
                    handleOnboardingKycFailure(kycId, userId, originalMessage, messageId);
                    break;
                case "PERIODIC_REVIEW":
                    handlePeriodicReviewFailure(kycId, userId, originalMessage, messageId);
                    break;
                case "ENHANCED_DUE_DILIGENCE":
                    handleEddFailure(kycId, userId, originalMessage, messageId);
                    break;
                case "DOCUMENT_REFRESH":
                    handleDocumentRefreshFailure(kycId, userId, originalMessage, messageId);
                    break;
                case "RISK_ASSESSMENT_UPDATE":
                    handleRiskAssessmentFailure(kycId, userId, originalMessage, messageId);
                    break;
                default:
                    log.info("No specific handling for KYC verification type: {}", verificationType);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling specific KYC failure: verificationType={}, kycId={}, error={}",
                verificationType, kycId, e.getMessage());
        }
    }

    private void handleOnboardingKycFailure(String kycId, String userId, Object originalMessage, String messageId) {
        notificationService.sendKycAlert(
            "Customer Onboarding KYC Failed",
            String.format("Customer onboarding KYC %s failed for user %s. " +
                "New customer may not be able to access services.", kycId, userId),
            "CRITICAL"
        );

        // Block account until verification complete
        kycService.blockAccountPendingVerification(userId, "ONBOARDING_KYC_FAILURE");
    }

    private void handlePeriodicReviewFailure(String kycId, String userId, Object originalMessage, String messageId) {
        notificationService.sendKycAlert(
            "Periodic KYC Review Failed",
            String.format("Periodic KYC review %s failed for user %s. " +
                "Ongoing compliance monitoring compromised.", kycId, userId),
            "MEDIUM"
        );
    }

    private void handleEddFailure(String kycId, String userId, Object originalMessage, String messageId) {
        notificationService.sendComplianceAlert(
            "Enhanced Due Diligence Failed",
            String.format("EDD verification %s failed for user %s. " +
                "High-risk customer requirements may not be met.", kycId, userId),
            "HIGH"
        );
    }

    private void handleDocumentRefreshFailure(String kycId, String userId, Object originalMessage, String messageId) {
        notificationService.sendKycAlert(
            "Document Refresh Failed",
            String.format("Document refresh %s failed for user %s. " +
                "Customer documents may remain expired.", kycId, userId),
            "MEDIUM"
        );
    }

    private void handleRiskAssessmentFailure(String kycId, String userId, Object originalMessage, String messageId) {
        notificationService.sendRiskManagementAlert(
            "Risk Assessment Update Failed",
            String.format("Risk assessment update %s failed for user %s. " +
                "Customer risk profile may be outdated.", kycId, userId),
            "MEDIUM"
        );
    }

    private void handleGovernmentIdFailure(String kycId, String userId, Object originalMessage, String messageId) {
        notificationService.sendKycAlert(
            "Government ID Verification Failed",
            String.format("Government ID verification failed for user %s. " +
                "Primary identity verification compromised.", userId),
            "HIGH"
        );
    }

    private void handleAddressProofFailure(String kycId, String userId, Object originalMessage, String messageId) {
        notificationService.sendKycAlert(
            "Address Proof Verification Failed",
            String.format("Address proof verification failed for user %s. " +
                "Address verification requirements not met.", userId),
            "MEDIUM"
        );
    }

    private void handleIncomeVerificationFailure(String kycId, String userId, Object originalMessage, String messageId) {
        notificationService.sendKycAlert(
            "Income Verification Failed",
            String.format("Income verification failed for user %s. " +
                "Financial capacity assessment incomplete.", userId),
            "MEDIUM"
        );
    }

    private void handleBankStatementFailure(String kycId, String userId, Object originalMessage, String messageId) {
        notificationService.sendKycAlert(
            "Bank Statement Verification Failed",
            String.format("Bank statement verification failed for user %s. " +
                "Financial history verification incomplete.", userId),
            "MEDIUM"
        );
    }

    private void triggerManualKycReview(String kycId, String userId, String verificationType,
                                       String verificationStatus, String messageId) {
        try {
            // All KYC DLQ messages require manual review due to compliance implications
            kafkaTemplate.send("manual-kyc-review-queue", Map.of(
                "kycId", kycId,
                "userId", userId,
                "verificationType", verificationType,
                "verificationStatus", verificationStatus,
                "reviewReason", "KYC_PROCESSING_DLQ",
                "priority", "HIGH",
                "complianceReviewRequired", true,
                "messageId", messageId,
                "timestamp", Instant.now()
            ));

            log.info("Triggered manual KYC review for DLQ: kycId={}, userId={}", kycId, userId);
        } catch (Exception e) {
            log.error("Error triggering manual KYC review: kycId={}, error={}", kycId, e.getMessage());
        }
    }

    private void assessAccountRestrictions(String userId, String verificationType, String messageId) {
        try {
            // Determine what restrictions should be applied
            Map<String, Object> restrictions = kycService.determineAccountRestrictions(userId, verificationType);
            if (!restrictions.isEmpty()) {
                log.info("Applying account restrictions for KYC DLQ: userId={}, restrictions={}", userId, restrictions);

                kycService.applyAccountRestrictions(userId, restrictions);

                notificationService.sendOperationalAlert(
                    "Account Restrictions Applied - KYC Failure",
                    String.format("Account restrictions applied to user %s due to KYC verification failure. " +
                        "Restrictions: %s", userId, restrictions),
                    "HIGH"
                );
            }
        } catch (Exception e) {
            log.error("Error assessing account restrictions: userId={}, error={}", userId, e.getMessage());
        }
    }

    // Circuit breaker fallback method
    public void handleKycProcessingDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                              int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String kycId = extractKycId(originalMessage);
        String userId = extractUserId(originalMessage);

        try {
            notificationService.sendExecutiveAlert(
                "Critical: KYC DLQ Circuit Breaker Triggered",
                String.format("Circuit breaker triggered for KYC DLQ processing on %s (user: %s). " +
                    "This indicates a systemic issue affecting customer verification systems.", kycId, userId)
            );

            // Mark as emergency KYC issue
            kycService.markEmergencyKycIssue(kycId, "CIRCUIT_BREAKER_KYC_DLQ");

        } catch (Exception e) {
            log.error("Error in KYC processing DLQ fallback: {}", e.getMessage());
        }
    }

    // Helper methods
    private boolean isCustomerFacingVerification(String verificationType) {
        return verificationType != null && (
            verificationType.contains("ONBOARDING") || verificationType.contains("DOCUMENT") ||
            verificationType.contains("IDENTITY") || verificationType.contains("ADDRESS")
        );
    }

    private boolean hasLegalImplications(String verificationType) {
        return verificationType != null && (
            verificationType.contains("ENHANCED_DUE_DILIGENCE") || verificationType.contains("HIGH_RISK") ||
            verificationType.contains("SANCTIONS") || verificationType.contains("PEP")
        );
    }

    private boolean shouldRestrictAccount(String verificationType, String verificationStatus) {
        // Restrict for critical verification types or certain statuses
        if ("CUSTOMER_ONBOARDING".equals(verificationType) || "ENHANCED_DUE_DILIGENCE".equals(verificationType)) {
            return true;
        }
        return "VERIFICATION_REQUIRED".equals(verificationStatus) || "FAILED_VERIFICATION".equals(verificationStatus);
    }

    // Data extraction helper methods
    private String extractKycId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object kycId = messageMap.get("kycId");
                if (kycId == null) kycId = messageMap.get("verificationId");
                return kycId != null ? kycId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract kycId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractUserId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object userId = messageMap.get("userId");
                if (userId == null) userId = messageMap.get("customerId");
                return userId != null ? userId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract userId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractVerificationType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object verificationType = messageMap.get("verificationType");
                if (verificationType == null) verificationType = messageMap.get("type");
                return verificationType != null ? verificationType.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract verificationType from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractVerificationStatus(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object verificationStatus = messageMap.get("verificationStatus");
                if (verificationStatus == null) verificationStatus = messageMap.get("status");
                return verificationStatus != null ? verificationStatus.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract verificationStatus from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractDocumentType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object documentType = messageMap.get("documentType");
                return documentType != null ? documentType.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract documentType from message: {}", e.getMessage());
        }
        return null;
    }
}