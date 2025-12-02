package com.waqiti.kyc.kafka;

import com.waqiti.kyc.domain.KycDocument;
import com.waqiti.kyc.domain.KycStatus;
import com.waqiti.kyc.domain.UserKycProfile;
import com.waqiti.kyc.domain.AccountRestriction;
import com.waqiti.kyc.dto.KycDocumentExpiredEvent;
import com.waqiti.kyc.repository.KycDocumentRepository;
import com.waqiti.kyc.repository.UserKycProfileRepository;
import com.waqiti.kyc.repository.AccountRestrictionRepository;
import com.waqiti.kyc.service.KycService;
import com.waqiti.kyc.service.NotificationService;
import com.waqiti.kyc.service.ComplianceService;
import com.waqiti.kyc.service.AccountService;
import com.waqiti.common.distributed.DistributedLockService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise-grade consumer for handling expired KYC documents
 * Critical for maintaining regulatory compliance and preventing financial crimes
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KycDocumentExpiredConsumer {

    private final KycDocumentRepository documentRepository;
    private final UserKycProfileRepository profileRepository;
    private final AccountRestrictionRepository restrictionRepository;
    private final KycService kycService;
    private final NotificationService notificationService;
    private final ComplianceService complianceService;
    private final AccountService accountService;
    private final DistributedLockService lockService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kyc.grace-period.days:30}")
    private int gracePeriodDays;

    @Value("${kyc.suspension.days:90}")
    private int suspensionDays;

    @Value("${kyc.max-transaction-limit:1000}")
    private BigDecimal maxTransactionLimit;

    private static final Set<String> CRITICAL_DOCUMENTS = Set.of(
        "PASSPORT", "NATIONAL_ID", "DRIVERS_LICENSE", "RESIDENCE_PERMIT"
    );

    @KafkaListener(
        topics = "kyc-document-expired",
        groupId = "kyc-document-renewal-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    @Transactional
    public void processExpiredDocument(
            @Payload KycDocumentExpiredEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String lockKey = "kyc-expired-" + event.getDocumentId();
        Counter expiredDocsCounter = meterRegistry.counter("kyc.documents.expired", 
            "type", event.getDocumentType());
        
        try {
            log.info("Processing expired KYC document: {} for user: {}, type: {}", 
                    event.getDocumentId(), event.getUserId(), event.getDocumentType());

            // Acquire distributed lock
            boolean lockAcquired = lockService.tryLock(lockKey, 30, TimeUnit.SECONDS);
            if (!lockAcquired) {
                log.warn("Could not acquire lock for expired document: {}", event.getDocumentId());
                throw new RuntimeException("Lock acquisition failed");
            }

            try {
                // 1. Validate and update document status
                KycDocument document = validateAndUpdateDocument(event);
                
                // 2. Assess impact and determine restrictions
                KycImpactAssessment impact = assessExpirationImpact(document, event);
                
                // 3. Update user KYC profile
                UserKycProfile profile = updateUserKycProfile(event.getUserId(), document, impact);
                
                // 4. Apply graduated account restrictions
                applyGraduatedRestrictions(profile, impact, event);
                
                // 5. Handle critical document expiration
                if (CRITICAL_DOCUMENTS.contains(event.getDocumentType())) {
                    handleCriticalDocumentExpiration(profile, document, event);
                }
                
                // 6. Send notifications with urgency levels
                sendGraduatedNotifications(profile, document, impact);
                
                // 7. Schedule renewal workflow
                scheduleRenewalWorkflow(profile, document, event);
                
                // 8. Report to regulatory systems
                reportToRegulatorySystem(profile, document, event);
                
                // 9. Update risk scoring
                updateRiskScoring(profile, impact);
                
                // 10. Trigger dependent workflows
                triggerDependentWorkflows(profile, document, event);
                
                expiredDocsCounter.increment();
                log.info("Successfully processed expired document {} with impact level: {}", 
                    event.getDocumentId(), impact.getSeverity());
                
                acknowledgment.acknowledge();
                
            } finally {
                lockService.unlock(lockKey);
            }
            
        } catch (Exception e) {
            log.error("Error processing expired KYC document: {}", event.getDocumentId(), e);
            handleProcessingError(event, e);
            throw e;
        }
    }

    private KycDocument validateAndUpdateDocument(KycDocumentExpiredEvent event) {
        KycDocument document = documentRepository.findById(UUID.fromString(event.getDocumentId()))
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + event.getDocumentId()));
        
        // Check if already processed
        if (document.getStatus() == KycStatus.EXPIRED) {
            log.info("Document {} already marked as expired", event.getDocumentId());
            return document;
        }
        
        // Update document status with audit trail
        document.setStatus(KycStatus.EXPIRED);
        document.setExpiredAt(LocalDateTime.now());
        document.setLastStatusChangeReason("DOCUMENT_EXPIRY");
        document.setLastModifiedBy("SYSTEM_EXPIRY_PROCESSOR");
        document.setLastModifiedAt(LocalDateTime.now());
        
        // Calculate days expired
        long daysExpired = ChronoUnit.DAYS.between(event.getExpiryDate(), LocalDateTime.now());
        document.setMetadata(enrichMetadata(document.getMetadata(), daysExpired));
        
        return documentRepository.save(document);
    }

    private KycImpactAssessment assessExpirationImpact(KycDocument document, KycDocumentExpiredEvent event) {
        KycImpactAssessment assessment = new KycImpactAssessment();
        
        // Calculate severity based on document type and expiration duration
        long daysExpired = ChronoUnit.DAYS.between(event.getExpiryDate(), LocalDateTime.now());
        
        if (CRITICAL_DOCUMENTS.contains(event.getDocumentType())) {
            assessment.setSeverity(daysExpired > 30 ? "CRITICAL" : "HIGH");
            assessment.setRequiresImmediateAction(true);
            assessment.setMaxTransactionLimit(BigDecimal.ZERO);
        } else {
            assessment.setSeverity(daysExpired > 60 ? "HIGH" : "MEDIUM");
            assessment.setRequiresImmediateAction(daysExpired > 90);
            assessment.setMaxTransactionLimit(maxTransactionLimit);
        }
        
        // Check for regulatory requirements
        assessment.setRegulatoryImpact(complianceService.assessRegulatoryImpact(
            event.getUserId(), event.getDocumentType(), daysExpired));
        
        // Determine grace period
        assessment.setGracePeriodEnds(LocalDateTime.now().plusDays(
            CRITICAL_DOCUMENTS.contains(event.getDocumentType()) ? 7 : gracePeriodDays));
        
        return assessment;
    }

    private UserKycProfile updateUserKycProfile(String userId, KycDocument document, KycImpactAssessment impact) {
        UserKycProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("KYC profile not found for user: " + userId));
        
        // Update overall KYC status based on impact
        KycStatus newStatus = determineOverallKycStatus(profile, document, impact);
        profile.setKycStatus(newStatus);
        profile.setLastVerificationDate(LocalDateTime.now());
        profile.setNextReviewDate(LocalDateTime.now().plusDays(7));
        
        // Update risk level
        profile.setRiskLevel(calculateRiskLevel(profile, impact));
        
        // Add to expired documents list
        profile.getExpiredDocuments().add(document.getId());
        
        // Update compliance flags
        profile.setCompliant("CRITICAL".equals(impact.getSeverity()) ? false : profile.isCompliant());
        profile.setRequiresManualReview(impact.isRequiresImmediateAction());
        
        return profileRepository.save(profile);
    }

    private void applyGraduatedRestrictions(UserKycProfile profile, KycImpactAssessment impact, 
                                           KycDocumentExpiredEvent event) {
        log.info("Applying restrictions for user {} with severity {}", 
            profile.getUserId(), impact.getSeverity());
        
        AccountRestriction restriction = new AccountRestriction();
        restriction.setUserId(profile.getUserId());
        restriction.setRestrictionType("KYC_DOCUMENT_EXPIRED");
        restriction.setReason("KYC document expired: " + event.getDocumentType());
        restriction.setAppliedAt(LocalDateTime.now());
        restriction.setExpiresAt(impact.getGracePeriodEnds());
        restriction.setActive(true);
        
        switch (impact.getSeverity()) {
            case "CRITICAL":
                // Severe restrictions
                restriction.setRestrictions(Map.of(
                    "WITHDRAWALS_ALLOWED", false,
                    "DEPOSITS_ALLOWED", true,
                    "TRANSFERS_ALLOWED", false,
                    "INTERNATIONAL_TRANSFERS_ALLOWED", false,
                    "CARD_TRANSACTIONS_ALLOWED", false,
                    "MAX_TRANSACTION_LIMIT", "0"
                ));
                accountService.freezeAccount(profile.getUserId(), "KYC_EXPIRED_CRITICAL");
                break;
                
            case "HIGH":
                // Moderate restrictions
                restriction.setRestrictions(Map.of(
                    "WITHDRAWALS_ALLOWED", true,
                    "DEPOSITS_ALLOWED", true,
                    "TRANSFERS_ALLOWED", true,
                    "INTERNATIONAL_TRANSFERS_ALLOWED", false,
                    "CARD_TRANSACTIONS_ALLOWED", true,
                    "MAX_TRANSACTION_LIMIT", "1000",
                    "DAILY_LIMIT", "2000"
                ));
                accountService.applyTransactionLimits(profile.getUserId(), 
                    BigDecimal.valueOf(1000), BigDecimal.valueOf(2000));
                break;
                
            case "MEDIUM":
                // Light restrictions
                restriction.setRestrictions(Map.of(
                    "WITHDRAWALS_ALLOWED", true,
                    "DEPOSITS_ALLOWED", true,
                    "TRANSFERS_ALLOWED", true,
                    "INTERNATIONAL_TRANSFERS_ALLOWED", true,
                    "CARD_TRANSACTIONS_ALLOWED", true,
                    "MAX_TRANSACTION_LIMIT", "5000",
                    "REQUIRES_ADDITIONAL_VERIFICATION", true
                ));
                accountService.requireAdditionalVerification(profile.getUserId());
                break;
        }
        
        restrictionRepository.save(restriction);
        
        // Publish restriction event
        kafkaTemplate.send("account-restrictions-applied", restriction);
    }

    private void handleCriticalDocumentExpiration(UserKycProfile profile, KycDocument document, 
                                                 KycDocumentExpiredEvent event) {
        log.warn("Critical document expired for user {}: {}", profile.getUserId(), event.getDocumentType());
        
        // Immediate compliance reporting
        complianceService.reportCriticalKycExpiration(profile.getUserId(), document);
        
        // Escalate to compliance team
        notificationService.alertComplianceTeam(
            "CRITICAL_KYC_EXPIRY",
            profile.getUserId(),
            event.getDocumentType(),
            "Immediate action required - critical document expired"
        );
        
        // Create compliance case
        String caseId = complianceService.createComplianceCase(
            profile.getUserId(),
            "KYC_DOCUMENT_EXPIRED",
            "CRITICAL",
            Map.of(
                "document_type", event.getDocumentType(),
                "expired_date", event.getExpiryDate().toString(),
                "days_expired", ChronoUnit.DAYS.between(event.getExpiryDate(), LocalDateTime.now())
            )
        );
        
        profile.setComplianceCaseId(caseId);
        profileRepository.save(profile);
    }

    private void sendGraduatedNotifications(UserKycProfile profile, KycDocument document, 
                                           KycImpactAssessment impact) {
        // Send immediate notification
        notificationService.sendKycExpirationNotification(
            profile.getUserId(),
            document.getDocumentType(),
            impact.getSeverity(),
            impact.getGracePeriodEnds()
        );
        
        // Send in-app notification
        notificationService.sendInAppNotification(
            profile.getUserId(),
            "KYC_DOCUMENT_EXPIRED",
            buildNotificationContent(document, impact)
        );
        
        // Send email with urgency
        notificationService.sendEmail(
            profile.getUserId(),
            impact.getSeverity().equals("CRITICAL") ? "URGENT: KYC Document Expired" : "KYC Document Renewal Required",
            buildEmailContent(document, impact),
            impact.getSeverity().equals("CRITICAL") ? "HIGH" : "NORMAL"
        );
        
        // Send SMS for critical cases
        if (impact.isRequiresImmediateAction()) {
            notificationService.sendSms(
                profile.getUserId(),
                String.format("URGENT: Your %s has expired. Account restrictions applied. Renew immediately to restore access.",
                    document.getDocumentType())
            );
        }
    }

    private void scheduleRenewalWorkflow(UserKycProfile profile, KycDocument document, 
                                        KycDocumentExpiredEvent event) {
        // Create renewal task
        Map<String, Object> renewalTask = Map.of(
            "userId", profile.getUserId(),
            "documentId", document.getId(),
            "documentType", document.getDocumentType(),
            "priority", CRITICAL_DOCUMENTS.contains(document.getDocumentType()) ? "HIGH" : "NORMAL",
            "dueDate", LocalDateTime.now().plusDays(7)
        );
        
        kafkaTemplate.send("kyc-renewal-tasks", renewalTask);
        
        // Schedule reminders
        scheduleReminders(profile, document);
        
        // Set up auto-escalation
        scheduleEscalation(profile, document);
    }

    private void scheduleReminders(UserKycProfile profile, KycDocument document) {
        int[] reminderDays = CRITICAL_DOCUMENTS.contains(document.getDocumentType()) 
            ? new int[]{1, 3, 5, 7} 
            : new int[]{7, 14, 21, 28};
        
        for (int days : reminderDays) {
            Map<String, Object> reminder = Map.of(
                "userId", profile.getUserId(),
                "documentType", document.getDocumentType(),
                "reminderType", days <= 7 ? "URGENT" : "STANDARD",
                "sendAt", LocalDateTime.now().plusDays(days)
            );
            
            kafkaTemplate.send("scheduled-notifications", reminder);
        }
    }

    private void scheduleEscalation(UserKycProfile profile, KycDocument document) {
        if (CRITICAL_DOCUMENTS.contains(document.getDocumentType())) {
            Map<String, Object> escalation = Map.of(
                "userId", profile.getUserId(),
                "documentId", document.getId(),
                "escalationType", "ACCOUNT_SUSPENSION",
                "triggerAt", LocalDateTime.now().plusDays(7)
            );
            
            kafkaTemplate.send("scheduled-escalations", escalation);
        }
    }

    private void reportToRegulatorySystem(UserKycProfile profile, KycDocument document, 
                                         KycDocumentExpiredEvent event) {
        // Report to regulatory system
        Map<String, Object> regulatoryReport = Map.of(
            "reportType", "KYC_DOCUMENT_EXPIRED",
            "userId", profile.getUserId(),
            "documentType", document.getDocumentType(),
            "expiryDate", event.getExpiryDate(),
            "reportedAt", LocalDateTime.now(),
            "riskLevel", profile.getRiskLevel(),
            "accountRestricted", true
        );
        
        complianceService.submitRegulatoryReport(regulatoryReport);
        
        // Update regulatory compliance status
        complianceService.updateComplianceStatus(
            profile.getUserId(),
            "KYC_EXPIRED",
            "NON_COMPLIANT"
        );
    }

    private void updateRiskScoring(UserKycProfile profile, KycImpactAssessment impact) {
        // Calculate new risk score
        Map<String, Object> riskFactors = Map.of(
            "kyc_expired", true,
            "document_type", impact.getSeverity(),
            "days_expired", ChronoUnit.DAYS.between(
                profile.getLastVerificationDate(), LocalDateTime.now()),
            "previous_risk_level", profile.getRiskLevel()
        );
        
        double newRiskScore = complianceService.calculateRiskScore(profile.getUserId(), riskFactors);
        
        // Update risk profile
        profile.setRiskScore(newRiskScore);
        profile.setRiskLevel(determineRiskLevel(newRiskScore));
        profileRepository.save(profile);
        
        // Publish risk update event
        kafkaTemplate.send("risk-score-updated", Map.of(
            "userId", profile.getUserId(),
            "newScore", newRiskScore,
            "reason", "KYC_DOCUMENT_EXPIRED"
        ));
    }

    private void triggerDependentWorkflows(UserKycProfile profile, KycDocument document, 
                                          KycDocumentExpiredEvent event) {
        // Trigger transaction monitoring update
        kafkaTemplate.send("transaction-monitoring-update", Map.of(
            "userId", profile.getUserId(),
            "monitoringLevel", "ENHANCED",
            "reason", "KYC_EXPIRED"
        ));
        
        // Trigger limit recalculation
        kafkaTemplate.send("limit-recalculation-required", Map.of(
            "userId", profile.getUserId(),
            "triggerReason", "KYC_STATUS_CHANGE"
        ));
        
        // Trigger partner notification if applicable
        if (profile.hasBusinessAccount()) {
            kafkaTemplate.send("partner-kyc-status-change", Map.of(
                "partnerId", profile.getBusinessPartnerId(),
                "userId", profile.getUserId(),
                "newStatus", "KYC_EXPIRED"
            ));
        }
    }

    private void handleProcessingError(KycDocumentExpiredEvent event, Exception error) {
        // Send to DLQ for critical errors
        if (shouldSendToDlq(error)) {
            Map<String, Object> dlqMessage = Map.of(
                "event", event,
                "error", error.getMessage(),
                "timestamp", LocalDateTime.now(),
                "retryCount", 3
            );
            
            kafkaTemplate.send("kyc-document-expired-dlq", dlqMessage);
            
            // Alert operations team
            notificationService.alertOperationsTeam(
                "KYC_PROCESSING_FAILURE",
                event.getDocumentId(),
                error.getMessage()
            );
        }
    }

    // Helper methods
    private Map<String, Object> enrichMetadata(Map<String, Object> metadata, long daysExpired) {
        if (metadata == null) metadata = new HashMap<>();
        metadata.put("days_expired", daysExpired);
        metadata.put("expiry_processed_at", LocalDateTime.now().toString());
        metadata.put("processor_version", "2.0");
        return metadata;
    }

    private KycStatus determineOverallKycStatus(UserKycProfile profile, KycDocument document, 
                                               KycImpactAssessment impact) {
        if ("CRITICAL".equals(impact.getSeverity())) {
            return KycStatus.EXPIRED_CRITICAL;
        } else if (profile.hasOtherValidDocuments(document.getDocumentType())) {
            return KycStatus.PARTIALLY_VERIFIED;
        } else {
            return KycStatus.RENEWAL_REQUIRED;
        }
    }

    private String calculateRiskLevel(UserKycProfile profile, KycImpactAssessment impact) {
        if ("CRITICAL".equals(impact.getSeverity()) || profile.getRiskScore() > 0.8) {
            return "HIGH";
        } else if (profile.getRiskScore() > 0.5) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String determineRiskLevel(double score) {
        if (score > 0.8) return "CRITICAL";
        if (score > 0.6) return "HIGH";
        if (score > 0.4) return "MEDIUM";
        return "LOW";
    }

    private boolean shouldSendToDlq(Exception e) {
        return e instanceof IllegalArgumentException ||
               e instanceof IllegalStateException;
    }

    private Map<String, Object> buildNotificationContent(KycDocument document, KycImpactAssessment impact) {
        return Map.of(
            "title", "KYC Document Expired",
            "message", String.format("Your %s has expired. Please renew immediately.", document.getDocumentType()),
            "severity", impact.getSeverity(),
            "actionRequired", true,
            "actionUrl", "/kyc/renew/" + document.getId()
        );
    }

    private String buildEmailContent(KycDocument document, KycImpactAssessment impact) {
        return String.format(
            "Your %s has expired. Account restrictions have been applied. " +
            "Please renew your document by %s to restore full account access. " +
            "Current restrictions: %s",
            document.getDocumentType(),
            impact.getGracePeriodEnds(),
            impact.getSeverity()
        );
    }

    // Inner classes
    @lombok.Data
    private static class KycImpactAssessment {
        private String severity;
        private boolean requiresImmediateAction;
        private BigDecimal maxTransactionLimit;
        private Map<String, Object> regulatoryImpact;
        private LocalDateTime gracePeriodEnds;
    }
}