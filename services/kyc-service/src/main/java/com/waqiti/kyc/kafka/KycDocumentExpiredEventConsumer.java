package com.waqiti.kyc.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.kyc.domain.KycDocument;
import com.waqiti.kyc.domain.KycDocumentStatus;
import com.waqiti.kyc.domain.KycVerificationStatus;
import com.waqiti.kyc.domain.ComplianceAction;
import com.waqiti.kyc.entity.DocumentExpiryRecord;
import com.waqiti.kyc.repository.KycDocumentRepository;
import com.waqiti.kyc.repository.KycVerificationRepository;
import com.waqiti.kyc.repository.DocumentExpiryRecordRepository;
import com.waqiti.kyc.service.KycNotificationService;
import com.waqiti.kyc.service.ComplianceActionService;
import com.waqiti.kyc.service.UserServiceClient;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.compliance.RegulatoryReportingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Critical Kafka Consumer for KYC Document Expiration Events
 * 
 * Handles document expiration events to maintain regulatory compliance:
 * - Government ID expiration
 * - Proof of address expiration
 * - Business registration expiration
 * - Professional licenses expiration
 * - Income verification expiration
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KycDocumentExpiredEventConsumer {

    private final KycDocumentRepository documentRepository;
    private final KycVerificationRepository verificationRepository;
    private final DocumentExpiryRecordRepository expiryRecordRepository;
    private final KycNotificationService notificationService;
    private final ComplianceActionService complianceActionService;
    private final UserServiceClient userServiceClient;
    private final RegulatoryReportingService regulatoryReportingService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(
        topics = "kyc-document-expired",
        groupId = "kyc-document-expiry-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleKycDocumentExpired(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("Processing KYC document expiry: key={}, partition={}, offset={}", 
                key, partition, record.offset());
            
            // Parse the expiry event
            Map<String, Object> expiryEvent = objectMapper.readValue(message, Map.class);
            
            String documentId = (String) expiryEvent.get("documentId");
            String userId = (String) expiryEvent.get("userId");
            String documentType = (String) expiryEvent.get("documentType");
            String expiryDate = (String) expiryEvent.get("expiryDate");
            String detectionMethod = (String) expiryEvent.getOrDefault("detectionMethod", "SCHEDULED_CHECK");
            Boolean gracePeriodExpired = (Boolean) expiryEvent.getOrDefault("gracePeriodExpired", false);
            
            // Validate required fields
            if (documentId == null || userId == null || documentType == null) {
                log.error("Invalid expiry event - missing required fields: {}", expiryEvent);
                publishExpiryProcessingFailedEvent(documentId, userId, "VALIDATION_ERROR", "Missing required fields");
                acknowledgment.acknowledge();
                return;
            }
            
            // Find the expired document
            Optional<KycDocument> documentOpt = documentRepository.findById(UUID.fromString(documentId));
            if (documentOpt.isEmpty()) {
                log.error("KYC document not found for expiry processing: {}", documentId);
                publishExpiryProcessingFailedEvent(documentId, userId, "DOCUMENT_NOT_FOUND", "Document not found");
                acknowledgment.acknowledge();
                return;
            }
            
            KycDocument document = documentOpt.get();
            
            // Validate document belongs to the user
            if (!document.getUserId().equals(userId)) {
                log.error("Document userId mismatch: document={}, event={}", document.getUserId(), userId);
                publishExpiryProcessingFailedEvent(documentId, userId, "USER_MISMATCH", "Document user mismatch");
                acknowledgment.acknowledge();
                return;
            }
            
            // Process the document expiry
            processDocumentExpiry(document, detectionMethod, gracePeriodExpired);
            
            // Audit the expiry processing
            auditService.logComplianceEvent("KYC_DOCUMENT_EXPIRY_PROCESSED", 
                Map.of(
                    "documentId", documentId,
                    "userId", userId,
                    "documentType", documentType,
                    "detectionMethod", detectionMethod,
                    "gracePeriodExpired", gracePeriodExpired.toString()
                ));
            
            log.info("Successfully processed KYC document expiry: documentId={}, userId={}, type={}", 
                documentId, userId, documentType);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Critical error processing KYC document expiry", e);
            
            // Try to extract identifiers for error event
            String documentId = null;
            String userId = null;
            try {
                Map<String, Object> event = objectMapper.readValue(message, Map.class);
                documentId = (String) event.get("documentId");
                userId = (String) event.get("userId");
            } catch (Exception parseException) {
                log.error("Failed to parse event message for error reporting", parseException);
                auditService.logSecurityEvent("KYC_EXPIRY_PARSE_ERROR", Map.of(
                    "rawMessage", message,
                    "error", parseException.getMessage(),
                    "timestamp", LocalDateTime.now().toString()
                ));
            }
            
            publishExpiryProcessingFailedEvent(documentId, userId, "PROCESSING_ERROR", e.getMessage());
            
            // Audit the critical failure with full context
            auditService.logSecurityEvent("KYC_DOCUMENT_EXPIRY_FAILURE", Map.of(
                "documentId", documentId != null ? documentId : "unknown",
                "userId", userId != null ? userId : "unknown",
                "errorType", e.getClass().getSimpleName(),
                "errorMessage", e.getMessage(),
                "stackTrace", getStackTraceAsString(e),
                "timestamp", LocalDateTime.now().toString()
            ));
            
            // Don't acknowledge - will be retried
            throw new RuntimeException("Failed to process KYC document expiry", e);
        }
    }

    private void processDocumentExpiry(KycDocument document, String detectionMethod, 
                                     Boolean gracePeriodExpired) {
        
        try {
            log.info("Processing expiry for document: documentId={}, type={}, userId={}", 
                document.getId(), document.getDocumentType(), document.getUserId());
            
            // Create expiry record
            DocumentExpiryRecord expiryRecord = createExpiryRecord(document, detectionMethod, gracePeriodExpired);
            
            // Update document status
            updateDocumentStatus(document, gracePeriodExpired);
            
            // Assess compliance impact
            ComplianceImpactAssessment impact = assessComplianceImpact(document);
            
            // Execute compliance actions based on impact
            executeComplianceActions(document, impact, gracePeriodExpired);
            
            // Send notifications based on criticality
            sendExpiryNotifications(document, impact, gracePeriodExpired);
            
            // Update user verification status if needed
            updateUserVerificationStatus(document, impact);
            
            // Schedule follow-up actions
            scheduleFollowUpActions(document, impact);
            
            // Generate regulatory reports if required
            generateRegulatoryReports(document, impact);
            
            // Publish expiry processed event
            publishExpiryProcessedEvent(document, expiryRecord, impact);
            
            log.info("Document expiry processing completed: documentId={}, impact={}", 
                document.getId(), impact.getSeverity());
            
        } catch (Exception e) {
            log.error("Error processing document expiry: documentId={}", document.getId(), e);
            throw new RuntimeException("Failed to process document expiry", e);
        }
    }

    private DocumentExpiryRecord createExpiryRecord(KycDocument document, String detectionMethod, 
                                                  Boolean gracePeriodExpired) {
        
        DocumentExpiryRecord record = DocumentExpiryRecord.builder()
            .id(UUID.randomUUID())
            .documentId(document.getId())
            .userId(document.getUserId())
            .documentType(document.getDocumentType())
            .originalExpiryDate(document.getExpiryDate())
            .detectionMethod(detectionMethod)
            .gracePeriodExpired(gracePeriodExpired)
            .processedAt(LocalDateTime.now())
            .build();
        
        return expiryRecordRepository.save(record);
    }

    private void updateDocumentStatus(KycDocument document, Boolean gracePeriodExpired) {
        KycDocumentStatus newStatus = gracePeriodExpired ? 
            KycDocumentStatus.EXPIRED_GRACE_PERIOD_ENDED : 
            KycDocumentStatus.EXPIRED;
        
        document.setStatus(newStatus);
        document.setExpiredAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        
        documentRepository.save(document);
        
        log.info("Updated document status to {}: documentId={}", newStatus, document.getId());
    }

    private ComplianceImpactAssessment assessComplianceImpact(KycDocument document) {
        String documentType = document.getDocumentType();
        String userTier = getUserTier(document.getUserId());
        boolean isBusinessAccount = isBusinessAccount(document.getUserId());
        List<String> activeTransactions = getActiveTransactions(document.getUserId());
        
        // Determine severity based on document type and user profile
        String severity = calculateSeverity(documentType, userTier, isBusinessAccount, activeTransactions);
        
        // Determine required actions
        List<ComplianceAction> requiredActions = determineRequiredActions(
            documentType, severity, activeTransactions, isBusinessAccount);
        
        // Calculate grace period if not already expired
        Integer gracePeriodDays = calculateGracePeriod(documentType, userTier, isBusinessAccount);
        
        return ComplianceImpactAssessment.builder()
            .documentId(document.getId())
            .userId(document.getUserId())
            .documentType(documentType)
            .severity(severity)
            .requiredActions(requiredActions)
            .gracePeriodDays(gracePeriodDays)
            .affectedTransactions(activeTransactions.size())
            .businessAccount(isBusinessAccount)
            .userTier(userTier)
            .assessedAt(LocalDateTime.now())
            .build();
    }

    private String calculateSeverity(String documentType, String userTier, 
                                   boolean isBusinessAccount, List<String> activeTransactions) {
        
        // Critical documents that immediately affect compliance
        if (List.of("GOVERNMENT_ID", "PASSPORT", "BUSINESS_LICENSE").contains(documentType)) {
            return "CRITICAL";
        }
        
        // High impact for business accounts or premium users
        if (isBusinessAccount || "PREMIUM".equals(userTier)) {
            return "HIGH";
        }
        
        // High impact if user has active high-value transactions
        if (!activeTransactions.isEmpty()) {
            return "HIGH";
        }
        
        // Medium impact for address or income documents
        if (List.of("PROOF_OF_ADDRESS", "INCOME_VERIFICATION").contains(documentType)) {
            return "MEDIUM";
        }
        
        return "LOW";
    }

    private List<ComplianceAction> determineRequiredActions(String documentType, String severity, 
                                                          List<String> activeTransactions, 
                                                          boolean isBusinessAccount) {
        
        List<ComplianceAction> actions = new java.util.ArrayList<>();
        
        switch (severity) {
            case "CRITICAL" -> {
                actions.add(ComplianceAction.IMMEDIATE_VERIFICATION_REQUIRED);
                actions.add(ComplianceAction.RESTRICT_HIGH_VALUE_TRANSACTIONS);
                if (!activeTransactions.isEmpty()) {
                    actions.add(ComplianceAction.REVIEW_ACTIVE_TRANSACTIONS);
                }
                if (isBusinessAccount) {
                    actions.add(ComplianceAction.BUSINESS_COMPLIANCE_REVIEW);
                }
            }
            case "HIGH" -> {
                actions.add(ComplianceAction.URGENT_VERIFICATION_REQUIRED);
                actions.add(ComplianceAction.LIMIT_TRANSACTION_AMOUNTS);
                actions.add(ComplianceAction.ENHANCED_MONITORING);
            }
            case "MEDIUM" -> {
                actions.add(ComplianceAction.STANDARD_VERIFICATION_REQUIRED);
                actions.add(ComplianceAction.NOTIFY_USER);
            }
            case "LOW" -> {
                actions.add(ComplianceAction.NOTIFY_USER);
                actions.add(ComplianceAction.SCHEDULE_FOLLOW_UP);
            }
        }
        
        return actions;
    }

    private Integer calculateGracePeriod(String documentType, String userTier, boolean isBusinessAccount) {
        // Critical documents get shorter grace periods
        if (List.of("GOVERNMENT_ID", "PASSPORT").contains(documentType)) {
            return isBusinessAccount ? 7 : 14; // 1-2 weeks
        }
        
        // Business licenses get longer grace periods
        if ("BUSINESS_LICENSE".equals(documentType)) {
            return 30; // 1 month
        }
        
        // Standard documents
        if ("PREMIUM".equals(userTier)) {
            return 21; // 3 weeks for premium users
        }
        
        return 30; // 1 month for standard users
    }

    private void executeComplianceActions(KycDocument document, ComplianceImpactAssessment impact, 
                                        Boolean gracePeriodExpired) {
        
        for (ComplianceAction action : impact.getRequiredActions()) {
            try {
                switch (action) {
                    case IMMEDIATE_VERIFICATION_REQUIRED -> {
                        complianceActionService.setImmediateVerificationRequired(document.getUserId());
                        complianceActionService.suspendUserIfNotVerified(document.getUserId(), 24); // 24 hours
                    }
                    case URGENT_VERIFICATION_REQUIRED -> {
                        complianceActionService.setUrgentVerificationRequired(document.getUserId());
                        complianceActionService.scheduleVerificationReminders(document.getUserId(), 3); // 3 days
                    }
                    case STANDARD_VERIFICATION_REQUIRED -> {
                        complianceActionService.setStandardVerificationRequired(document.getUserId());
                        complianceActionService.scheduleVerificationReminders(document.getUserId(), 7); // 1 week
                    }
                    case RESTRICT_HIGH_VALUE_TRANSACTIONS -> {
                        complianceActionService.restrictHighValueTransactions(document.getUserId());
                    }
                    case LIMIT_TRANSACTION_AMOUNTS -> {
                        complianceActionService.limitTransactionAmounts(document.getUserId(), 
                            impact.getDocumentType(), gracePeriodExpired);
                    }
                    case ENHANCED_MONITORING -> {
                        complianceActionService.enableEnhancedMonitoring(document.getUserId());
                    }
                    case REVIEW_ACTIVE_TRANSACTIONS -> {
                        complianceActionService.flagActiveTransactionsForReview(document.getUserId());
                    }
                    case BUSINESS_COMPLIANCE_REVIEW -> {
                        complianceActionService.initiateBusinessComplianceReview(document.getUserId());
                    }
                }
                
                log.info("Executed compliance action {} for user {}", action, document.getUserId());
                
            } catch (Exception e) {
                log.error("Failed to execute compliance action {} for user {}: {}", 
                    action, document.getUserId(), e.getMessage());
            }
        }
    }

    private void sendExpiryNotifications(KycDocument document, ComplianceImpactAssessment impact, 
                                       Boolean gracePeriodExpired) {
        
        try {
            String urgencyLevel = mapSeverityToUrgency(impact.getSeverity());
            
            // Send user notification
            notificationService.sendDocumentExpiryNotification(
                document.getUserId(),
                document.getDocumentType(),
                document.getExpiryDate(),
                impact.getGracePeriodDays(),
                gracePeriodExpired,
                urgencyLevel
            );
            
            // Send compliance team notification for high/critical severity
            if (List.of("HIGH", "CRITICAL").contains(impact.getSeverity())) {
                notificationService.sendComplianceExpiryAlert(
                    document.getUserId(),
                    document.getDocumentType(),
                    impact.getSeverity(),
                    impact.getRequiredActions()
                );
            }
            
            // Send admin notification for critical documents
            if ("CRITICAL".equals(impact.getSeverity())) {
                notificationService.sendAdminExpiryAlert(
                    document.getUserId(),
                    document.getDocumentType(),
                    impact.getAffectedTransactions(),
                    gracePeriodExpired
                );
            }
            
        } catch (Exception e) {
            log.error("Error sending expiry notifications for document: {}", document.getId(), e);
        }
    }

    private String mapSeverityToUrgency(String severity) {
        return switch (severity) {
            case "CRITICAL" -> "URGENT";
            case "HIGH" -> "HIGH";
            case "MEDIUM" -> "NORMAL";
            case "LOW" -> "LOW";
            default -> "NORMAL";
        };
    }

    private void updateUserVerificationStatus(KycDocument document, ComplianceImpactAssessment impact) {
        try {
            // Check if user's overall verification status needs to be downgraded
            List<KycDocument> allUserDocuments = documentRepository.findByUserId(document.getUserId());
            
            KycVerificationStatus newStatus = calculateUserVerificationStatus(allUserDocuments, impact);
            
            if (newStatus != null) {
                userServiceClient.updateVerificationStatus(document.getUserId(), newStatus.toString());
                log.info("Updated user verification status to {} for user {}", newStatus, document.getUserId());
            }
            
        } catch (Exception e) {
            log.error("Error updating user verification status for user: {}", document.getUserId(), e);
        }
    }

    private KycVerificationStatus calculateUserVerificationStatus(List<KycDocument> allDocuments, 
                                                                ComplianceImpactAssessment impact) {
        
        long expiredCriticalDocs = allDocuments.stream()
            .filter(doc -> doc.getStatus() == KycDocumentStatus.EXPIRED || 
                          doc.getStatus() == KycDocumentStatus.EXPIRED_GRACE_PERIOD_ENDED)
            .filter(doc -> List.of("GOVERNMENT_ID", "PASSPORT", "BUSINESS_LICENSE").contains(doc.getDocumentType()))
            .count();
        
        long expiredDocs = allDocuments.stream()
            .filter(doc -> doc.getStatus() == KycDocumentStatus.EXPIRED || 
                          doc.getStatus() == KycDocumentStatus.EXPIRED_GRACE_PERIOD_ENDED)
            .count();
        
        if (expiredCriticalDocs > 0) {
            return KycVerificationStatus.VERIFICATION_REQUIRED;
        }
        
        if (expiredDocs > 1 || "CRITICAL".equals(impact.getSeverity())) {
            return KycVerificationStatus.PARTIAL_VERIFICATION;
        }
        
        return null; // No status change needed
    }

    private void scheduleFollowUpActions(KycDocument document, ComplianceImpactAssessment impact) {
        try {
            // Schedule verification reminders
            if (impact.getGracePeriodDays() != null && impact.getGracePeriodDays() > 0) {
                LocalDateTime reminderDate = LocalDateTime.now().plusDays(impact.getGracePeriodDays() / 2);
                scheduleVerificationReminder(document.getUserId(), document.getDocumentType(), reminderDate);
            }
            
            // Schedule grace period expiry check
            if (impact.getGracePeriodDays() != null && impact.getGracePeriodDays() > 0) {
                LocalDateTime gracePeriodEnd = LocalDateTime.now().plusDays(impact.getGracePeriodDays());
                scheduleGracePeriodExpiry(document.getId(), gracePeriodEnd);
            }
            
            // Schedule compliance review for high-risk cases
            if (List.of("HIGH", "CRITICAL").contains(impact.getSeverity())) {
                LocalDateTime reviewDate = LocalDateTime.now().plusDays(7);
                scheduleComplianceReview(document.getUserId(), document.getDocumentType(), reviewDate);
            }
            
        } catch (Exception e) {
            log.error("Error scheduling follow-up actions for document: {}", document.getId(), e);
        }
    }

    private void generateRegulatoryReports(KycDocument document, ComplianceImpactAssessment impact) {
        try {
            // Generate reports for critical compliance violations
            if ("CRITICAL".equals(impact.getSeverity()) && impact.isBusinessAccount()) {
                regulatoryReportingService.generateComplianceViolationReport(
                    document.getUserId(),
                    "KYC_DOCUMENT_EXPIRED",
                    document.getDocumentType(),
                    impact
                );
            }
            
            // Update regulatory metrics
            regulatoryReportingService.updateKycComplianceMetrics(
                document.getDocumentType(),
                impact.getSeverity(),
                impact.isBusinessAccount()
            );
            
        } catch (Exception e) {
            log.error("Error generating regulatory reports for document: {}", document.getId(), e);
        }
    }

    // Helper methods for external service calls
    private String getUserTier(String userId) {
        try {
            return userServiceClient.getUserTier(userId);
        } catch (Exception e) {
            log.warn("Failed to get user tier for {}, defaulting to STANDARD", userId);
            return "STANDARD";
        }
    }

    private boolean isBusinessAccount(String userId) {
        try {
            return userServiceClient.isBusinessAccount(userId);
        } catch (Exception e) {
            log.warn("Failed to check business account status for {}, defaulting to false", userId);
            return false;
        }
    }

    private List<String> getActiveTransactions(String userId) {
        try {
            return userServiceClient.getActiveTransactionIds(userId);
        } catch (Exception e) {
            log.warn("Failed to get active transactions for {}, defaulting to empty list", userId);
            return List.of();
        }
    }

    // Scheduling helper methods
    private void scheduleVerificationReminder(String userId, String documentType, LocalDateTime reminderDate) {
        Map<String, Object> event = Map.of(
            "eventType", "verification-reminder-scheduled",
            "userId", userId,
            "documentType", documentType,
            "scheduledAt", reminderDate.toString()
        );
        kafkaTemplate.send("verification-reminder-scheduled", userId, event);
    }

    private void scheduleGracePeriodExpiry(UUID documentId, LocalDateTime expiryDate) {
        Map<String, Object> event = Map.of(
            "eventType", "grace-period-expiry-scheduled",
            "documentId", documentId.toString(),
            "scheduledAt", expiryDate.toString()
        );
        kafkaTemplate.send("grace-period-expiry-scheduled", documentId.toString(), event);
    }

    private void scheduleComplianceReview(String userId, String documentType, LocalDateTime reviewDate) {
        Map<String, Object> event = Map.of(
            "eventType", "compliance-review-scheduled",
            "userId", userId,
            "documentType", documentType,
            "scheduledAt", reviewDate.toString()
        );
        kafkaTemplate.send("compliance-review-scheduled", userId, event);
    }

    // Event publishing methods
    private void publishExpiryProcessedEvent(KycDocument document, DocumentExpiryRecord record, 
                                           ComplianceImpactAssessment impact) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "kyc-document-expiry-processed",
                "documentId", document.getId().toString(),
                "userId", document.getUserId(),
                "documentType", document.getDocumentType(),
                "severity", impact.getSeverity(),
                "requiredActions", impact.getRequiredActions().stream().map(Enum::toString).toList(),
                "gracePeriodDays", impact.getGracePeriodDays() != null ? impact.getGracePeriodDays() : 0,
                "affectedTransactions", impact.getAffectedTransactions(),
                "processedAt", record.getProcessedAt().toString(),
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send("kyc-document-expiry-processed", document.getUserId(), event);
            log.debug("Published KYC document expiry processed event: {}", document.getId());
            
        } catch (Exception e) {
            log.error("Failed to publish KYC document expiry processed event", e);
        }
    }

    private void publishExpiryProcessingFailedEvent(String documentId, String userId, 
                                                  String errorCode, String errorMessage) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "kyc-document-expiry-processing-failed",
                "documentId", documentId != null ? documentId : "unknown",
                "userId", userId != null ? userId : "unknown",
                "errorCode", errorCode,
                "errorMessage", errorMessage,
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send("kyc-document-expiry-processing-failed", 
                documentId != null ? documentId : userId, event);
            log.debug("Published KYC document expiry processing failed event: documentId={}, error={}", 
                documentId, errorCode);
            
        } catch (Exception e) {
            log.error("Failed to publish KYC document expiry processing failed event", e);
        }
    }

    // Helper method to convert exception stack trace to string
    private String getStackTraceAsString(Exception e) {
        return org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(e);
    }

    // Inner class for compliance impact assessment
    @lombok.Data
    @lombok.Builder
    private static class ComplianceImpactAssessment {
        private UUID documentId;
        private String userId;
        private String documentType;
        private String severity;
        private List<ComplianceAction> requiredActions;
        private Integer gracePeriodDays;
        private int affectedTransactions;
        private boolean businessAccount;
        private String userTier;
        private LocalDateTime assessedAt;
    }
}