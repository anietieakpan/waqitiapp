package com.waqiti.compliance.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.compliance.service.ComplianceReviewService;
import com.waqiti.compliance.service.ManualReviewService;
import com.waqiti.compliance.domain.ManualReviewCase;
import com.waqiti.compliance.domain.ReviewPriority;
import com.waqiti.compliance.domain.ReviewType;
import com.waqiti.compliance.domain.ReviewStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Critical Kafka consumer for processing manual review queue requests
 * Handles cases that require human intervention for compliance and risk assessment
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ManualReviewQueueConsumer {

    private final ManualReviewService manualReviewService;
    private final ComplianceReviewService complianceReviewService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000L, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = "manual-review-queue", groupId = "compliance-service-manual-review-group")
    public void processManualReviewRequest(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("Processing manual review request from topic: {}, partition: {}", topic, partition);
            
            // Parse manual review event
            ManualReviewEvent event = objectMapper.readValue(payload, ManualReviewEvent.class);
            
            // Validate event
            validateManualReviewEvent(event);
            
            // Process based on review type
            switch (event.getReviewType()) {
                case "TRANSACTION_REVIEW":
                    handleTransactionReview(event);
                    break;
                case "KYC_REVIEW":
                    handleKycReview(event);
                    break;
                case "DOCUMENT_REVIEW":
                    handleDocumentReview(event);
                    break;
                case "FRAUD_INVESTIGATION":
                    handleFraudInvestigation(event);
                    break;
                case "COMPLIANCE_ESCALATION":
                    handleComplianceEscalation(event);
                    break;
                case "AML_INVESTIGATION":
                    handleAmlInvestigation(event);
                    break;
                case "ACCOUNT_VERIFICATION":
                    handleAccountVerification(event);
                    break;
                case "DISPUTE_REVIEW":
                    handleDisputeReview(event);
                    break;
                default:
                    log.warn("Unknown review type: {}", event.getReviewType());
            }
            
            log.info("Successfully processed manual review request for case: {}, type: {}", 
                event.getCaseId(), event.getReviewType());
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing manual review request: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process manual review request", e);
        }
    }

    private void validateManualReviewEvent(ManualReviewEvent event) {
        if (event.getCaseId() == null || event.getCaseId().trim().isEmpty()) {
            throw new IllegalArgumentException("Case ID is required");
        }
        
        if (event.getReviewType() == null || event.getReviewType().trim().isEmpty()) {
            throw new IllegalArgumentException("Review type is required");
        }
        
        if (event.getPriority() == null || event.getPriority().trim().isEmpty()) {
            throw new IllegalArgumentException("Priority is required");
        }
        
        if (event.getSubmittedBy() == null || event.getSubmittedBy().trim().isEmpty()) {
            throw new IllegalArgumentException("Submitter is required");
        }
    }

    private void handleTransactionReview(ManualReviewEvent event) {
        log.info("Processing transaction review for case: {}, transaction: {}", 
            event.getCaseId(), event.getTransactionId());
        
        try {
            // Create manual review case
            ManualReviewCase reviewCase = createReviewCase(event, ReviewType.TRANSACTION_REVIEW);
            
            // Set transaction-specific data
            reviewCase.setEntityId(event.getTransactionId());
            reviewCase.setEntityType("TRANSACTION");
            reviewCase.setAmount(event.getAmount());
            reviewCase.setCurrency(event.getCurrency());
            
            // Determine priority based on amount and risk factors
            ReviewPriority priority = determineTransactionReviewPriority(event);
            reviewCase.setPriority(priority);
            
            // Set SLA based on priority
            reviewCase.setSlaDeadline(calculateSlaDeadline(priority));
            
            // Assign to appropriate reviewer
            String assignedReviewer = manualReviewService.assignTransactionReviewer(reviewCase);
            reviewCase.setAssignedTo(assignedReviewer);
            
            // Save review case
            manualReviewService.createReviewCase(reviewCase);
            
            // Notify assigned reviewer
            manualReviewService.notifyReviewer(reviewCase, "NEW_TRANSACTION_REVIEW");
            
            // Send alert if high priority
            if (priority == ReviewPriority.HIGH || priority == ReviewPriority.CRITICAL) {
                manualReviewService.sendHighPriorityAlert(reviewCase);
            }
            
            log.info("Transaction review case created: {}, assigned to: {}", 
                reviewCase.getId(), assignedReviewer);
            
        } catch (Exception e) {
            log.error("Error handling transaction review for case {}: {}", 
                event.getCaseId(), e.getMessage(), e);
            throw new RuntimeException("Failed to handle transaction review", e);
        }
    }

    private void handleKycReview(ManualReviewEvent event) {
        log.info("Processing KYC review for case: {}, user: {}", event.getCaseId(), event.getUserId());
        
        try {
            // Create KYC review case
            ManualReviewCase reviewCase = createReviewCase(event, ReviewType.KYC_REVIEW);
            
            // Set KYC-specific data
            reviewCase.setEntityId(event.getUserId());
            reviewCase.setEntityType("USER_KYC");
            
            // KYC reviews typically have standard priority unless escalated
            ReviewPriority priority = "ESCALATED".equals(event.getReason()) ? 
                ReviewPriority.HIGH : ReviewPriority.MEDIUM;
            reviewCase.setPriority(priority);
            
            // Set regulatory SLA (typically 24-48 hours)
            reviewCase.setSlaDeadline(LocalDateTime.now().plusHours(24));
            
            // Assign to KYC specialist
            String assignedReviewer = manualReviewService.assignKycReviewer(reviewCase);
            reviewCase.setAssignedTo(assignedReviewer);
            
            // Save review case
            manualReviewService.createReviewCase(reviewCase);
            
            // Notify KYC team
            manualReviewService.notifyReviewer(reviewCase, "NEW_KYC_REVIEW");
            
            // Update user KYC status to under review
            complianceReviewService.updateKycStatus(event.getUserId(), "UNDER_MANUAL_REVIEW");
            
            log.info("KYC review case created: {}, assigned to: {}", 
                reviewCase.getId(), assignedReviewer);
            
        } catch (Exception e) {
            log.error("Error handling KYC review for case {}: {}", 
                event.getCaseId(), e.getMessage(), e);
            throw new RuntimeException("Failed to handle KYC review", e);
        }
    }

    private void handleDocumentReview(ManualReviewEvent event) {
        log.info("Processing document review for case: {}, documents: {}", 
            event.getCaseId(), event.getDocumentIds());
        
        try {
            // Create document review case
            ManualReviewCase reviewCase = createReviewCase(event, ReviewType.DOCUMENT_REVIEW);
            
            // Set document-specific data
            reviewCase.setEntityType("DOCUMENTS");
            
            // Document reviews usually have medium priority
            reviewCase.setPriority(ReviewPriority.MEDIUM);
            
            // Set SLA based on document type (ID documents faster than financial docs)
            int slaHours = event.getDocumentType().equals("IDENTITY") ? 4 : 24;
            reviewCase.setSlaDeadline(LocalDateTime.now().plusHours(slaHours));
            
            // Assign to document specialist
            String assignedReviewer = manualReviewService.assignDocumentReviewer(reviewCase);
            reviewCase.setAssignedTo(assignedReviewer);
            
            // Save review case
            manualReviewService.createReviewCase(reviewCase);
            
            // Notify reviewer
            manualReviewService.notifyReviewer(reviewCase, "NEW_DOCUMENT_REVIEW");
            
            log.info("Document review case created: {}, assigned to: {}", 
                reviewCase.getId(), assignedReviewer);
            
        } catch (Exception e) {
            log.error("Error handling document review for case {}: {}", 
                event.getCaseId(), e.getMessage(), e);
            throw new RuntimeException("Failed to handle document review", e);
        }
    }

    private void handleFraudInvestigation(ManualReviewEvent event) {
        log.info("Processing fraud investigation for case: {}, risk score: {}", 
            event.getCaseId(), event.getRiskScore());
        
        try {
            // Create fraud investigation case
            ManualReviewCase reviewCase = createReviewCase(event, ReviewType.FRAUD_INVESTIGATION);
            
            // Set fraud-specific data
            reviewCase.setEntityId(event.getTransactionId() != null ? event.getTransactionId() : event.getUserId());
            reviewCase.setEntityType(event.getTransactionId() != null ? "TRANSACTION" : "USER");
            reviewCase.setRiskScore(event.getRiskScore());
            
            // Fraud investigations are typically high priority
            ReviewPriority priority = event.getRiskScore() >= 0.8 ? 
                ReviewPriority.CRITICAL : ReviewPriority.HIGH;
            reviewCase.setPriority(priority);
            
            // Set urgent SLA for fraud cases
            int slaHours = priority == ReviewPriority.CRITICAL ? 2 : 4;
            reviewCase.setSlaDeadline(LocalDateTime.now().plusHours(slaHours));
            
            // Assign to fraud specialist
            String assignedReviewer = manualReviewService.assignFraudInvestigator(reviewCase);
            reviewCase.setAssignedTo(assignedReviewer);
            
            // Save review case
            manualReviewService.createReviewCase(reviewCase);
            
            // Notify fraud team immediately
            manualReviewService.notifyReviewer(reviewCase, "NEW_FRAUD_INVESTIGATION");
            manualReviewService.sendHighPriorityAlert(reviewCase);
            
            // If critical, also notify security team
            if (priority == ReviewPriority.CRITICAL) {
                manualReviewService.notifySecurityTeam(reviewCase);
            }
            
            log.warn("Fraud investigation case created: {}, priority: {}, assigned to: {}", 
                reviewCase.getId(), priority, assignedReviewer);
            
        } catch (Exception e) {
            log.error("Error handling fraud investigation for case {}: {}", 
                event.getCaseId(), e.getMessage(), e);
            throw new RuntimeException("Failed to handle fraud investigation", e);
        }
    }

    private void handleComplianceEscalation(ManualReviewEvent event) {
        log.info("Processing compliance escalation for case: {}, regulation: {}", 
            event.getCaseId(), event.getRegulationType());
        
        try {
            // Create compliance escalation case
            ManualReviewCase reviewCase = createReviewCase(event, ReviewType.COMPLIANCE_ESCALATION);
            
            // Set compliance-specific data
            reviewCase.setEntityType("COMPLIANCE");
            reviewCase.setRegulationType(event.getRegulationType());
            
            // Compliance escalations are always high priority
            reviewCase.setPriority(ReviewPriority.HIGH);
            
            // Set regulatory SLA (varies by regulation type)
            int slaHours = getComplianceSlaHours(event.getRegulationType());
            reviewCase.setSlaDeadline(LocalDateTime.now().plusHours(slaHours));
            
            // Assign to compliance officer
            String assignedReviewer = manualReviewService.assignComplianceOfficer(reviewCase);
            reviewCase.setAssignedTo(assignedReviewer);
            
            // Save review case
            manualReviewService.createReviewCase(reviewCase);
            
            // Notify compliance team
            manualReviewService.notifyReviewer(reviewCase, "NEW_COMPLIANCE_ESCALATION");
            manualReviewService.sendHighPriorityAlert(reviewCase);
            
            // Notify management for regulatory matters
            manualReviewService.notifyManagement(reviewCase, "COMPLIANCE_ESCALATION");
            
            log.warn("Compliance escalation case created: {}, regulation: {}, assigned to: {}", 
                reviewCase.getId(), event.getRegulationType(), assignedReviewer);
            
        } catch (Exception e) {
            log.error("Error handling compliance escalation for case {}: {}", 
                event.getCaseId(), e.getMessage(), e);
            throw new RuntimeException("Failed to handle compliance escalation", e);
        }
    }

    private void handleAmlInvestigation(ManualReviewEvent event) {
        log.info("Processing AML investigation for case: {}, user: {}", 
            event.getCaseId(), event.getUserId());
        
        try {
            // Create AML investigation case
            ManualReviewCase reviewCase = createReviewCase(event, ReviewType.AML_INVESTIGATION);
            
            // Set AML-specific data
            reviewCase.setEntityId(event.getUserId());
            reviewCase.setEntityType("AML_REVIEW");
            reviewCase.setAmount(event.getAmount());
            
            // AML investigations are critical priority
            reviewCase.setPriority(ReviewPriority.CRITICAL);
            
            // Set strict regulatory SLA (24 hours for SAR determination)
            reviewCase.setSlaDeadline(LocalDateTime.now().plusHours(24));
            
            // Assign to AML specialist
            String assignedReviewer = manualReviewService.assignAmlInvestigator(reviewCase);
            reviewCase.setAssignedTo(assignedReviewer);
            
            // Save review case
            manualReviewService.createReviewCase(reviewCase);
            
            // Notify AML team immediately
            manualReviewService.notifyReviewer(reviewCase, "NEW_AML_INVESTIGATION");
            manualReviewService.sendHighPriorityAlert(reviewCase);
            
            // Notify compliance team
            manualReviewService.notifyComplianceTeam(reviewCase, "AML_INVESTIGATION_STARTED");
            
            // Freeze related transactions pending investigation
            complianceReviewService.freezeRelatedTransactions(event.getUserId(), 
                "AML investigation: " + event.getReason());
            
            log.warn("AML investigation case created: {}, assigned to: {}", 
                reviewCase.getId(), assignedReviewer);
            
        } catch (Exception e) {
            log.error("Error handling AML investigation for case {}: {}", 
                event.getCaseId(), e.getMessage(), e);
            throw new RuntimeException("Failed to handle AML investigation", e);
        }
    }

    private void handleAccountVerification(ManualReviewEvent event) {
        log.info("Processing account verification for case: {}, user: {}", 
            event.getCaseId(), event.getUserId());
        
        try {
            // Create account verification case
            ManualReviewCase reviewCase = createReviewCase(event, ReviewType.ACCOUNT_VERIFICATION);
            
            // Set verification-specific data
            reviewCase.setEntityId(event.getUserId());
            reviewCase.setEntityType("ACCOUNT");
            
            // Standard priority for account verification
            reviewCase.setPriority(ReviewPriority.MEDIUM);
            
            // Set SLA (typically 48 hours)
            reviewCase.setSlaDeadline(LocalDateTime.now().plusHours(48));
            
            // Assign to verification specialist
            String assignedReviewer = manualReviewService.assignVerificationSpecialist(reviewCase);
            reviewCase.setAssignedTo(assignedReviewer);
            
            // Save review case
            manualReviewService.createReviewCase(reviewCase);
            
            // Notify reviewer
            manualReviewService.notifyReviewer(reviewCase, "NEW_ACCOUNT_VERIFICATION");
            
            log.info("Account verification case created: {}, assigned to: {}", 
                reviewCase.getId(), assignedReviewer);
            
        } catch (Exception e) {
            log.error("Error handling account verification for case {}: {}", 
                event.getCaseId(), e.getMessage(), e);
            throw new RuntimeException("Failed to handle account verification", e);
        }
    }

    private void handleDisputeReview(ManualReviewEvent event) {
        log.info("Processing dispute review for case: {}, dispute: {}", 
            event.getCaseId(), event.getDisputeId());
        
        try {
            // Create dispute review case
            ManualReviewCase reviewCase = createReviewCase(event, ReviewType.DISPUTE_REVIEW);
            
            // Set dispute-specific data
            reviewCase.setEntityId(event.getDisputeId());
            reviewCase.setEntityType("DISPUTE");
            reviewCase.setAmount(event.getAmount());
            
            // Priority based on dispute amount and type
            ReviewPriority priority = determineDisputeReviewPriority(event);
            reviewCase.setPriority(priority);
            
            // Set SLA based on dispute type and regulations
            reviewCase.setSlaDeadline(calculateDisputeSlaDeadline(event));
            
            // Assign to dispute specialist
            String assignedReviewer = manualReviewService.assignDisputeReviewer(reviewCase);
            reviewCase.setAssignedTo(assignedReviewer);
            
            // Save review case
            manualReviewService.createReviewCase(reviewCase);
            
            // Notify reviewer
            manualReviewService.notifyReviewer(reviewCase, "NEW_DISPUTE_REVIEW");
            
            log.info("Dispute review case created: {}, assigned to: {}", 
                reviewCase.getId(), assignedReviewer);
            
        } catch (Exception e) {
            log.error("Error handling dispute review for case {}: {}", 
                event.getCaseId(), e.getMessage(), e);
            throw new RuntimeException("Failed to handle dispute review", e);
        }
    }

    private ManualReviewCase createReviewCase(ManualReviewEvent event, ReviewType reviewType) {
        ManualReviewCase reviewCase = new ManualReviewCase();
        reviewCase.setId(UUID.randomUUID());
        reviewCase.setCaseId(event.getCaseId());
        reviewCase.setReviewType(reviewType);
        reviewCase.setStatus(ReviewStatus.PENDING);
        reviewCase.setSubmittedBy(event.getSubmittedBy());
        reviewCase.setSubmittedAt(LocalDateTime.now());
        reviewCase.setDescription(event.getDescription());
        reviewCase.setReason(event.getReason());
        reviewCase.setMetadata(event.getMetadata());
        
        return reviewCase;
    }

    private ReviewPriority determineTransactionReviewPriority(ManualReviewEvent event) {
        if (event.getAmount() != null && event.getAmount().compareTo(BigDecimal.valueOf(10000)) > 0) {
            return ReviewPriority.HIGH;
        }
        if (event.getRiskScore() != null && event.getRiskScore() >= 0.7) {
            return ReviewPriority.HIGH;
        }
        return ReviewPriority.MEDIUM;
    }

    private ReviewPriority determineDisputeReviewPriority(ManualReviewEvent event) {
        if (event.getAmount() != null && event.getAmount().compareTo(BigDecimal.valueOf(5000)) > 0) {
            return ReviewPriority.HIGH;
        }
        if ("CHARGEBACK".equals(event.getDisputeType())) {
            return ReviewPriority.HIGH;
        }
        return ReviewPriority.MEDIUM;
    }

    private LocalDateTime calculateSlaDeadline(ReviewPriority priority) {
        return switch (priority) {
            case CRITICAL -> LocalDateTime.now().plusHours(2);
            case HIGH -> LocalDateTime.now().plusHours(8);
            case MEDIUM -> LocalDateTime.now().plusHours(24);
            case LOW -> LocalDateTime.now().plusHours(72);
        };
    }

    private LocalDateTime calculateDisputeSlaDeadline(ManualReviewEvent event) {
        // Regulatory requirements for dispute responses
        if ("CHARGEBACK".equals(event.getDisputeType())) {
            return LocalDateTime.now().plusDays(7); // 7 days for chargeback response
        }
        return LocalDateTime.now().plusDays(14); // 14 days for standard disputes
    }

    private int getComplianceSlaHours(String regulationType) {
        return switch (regulationType) {
            case "SAR", "CTR" -> 24; // Suspicious Activity Reports
            case "OFAC" -> 4; // OFAC sanctions screening
            case "BSA" -> 48; // Bank Secrecy Act
            default -> 24;
        };
    }

    // Manual review event data structure
    public static class ManualReviewEvent {
        private String caseId;
        private String reviewType;
        private String priority;
        private String submittedBy;
        private String userId;
        private String transactionId;
        private String disputeId;
        private String[] documentIds;
        private String documentType;
        private String reason;
        private String description;
        private String regulationType;
        private String disputeType;
        private BigDecimal amount;
        private String currency;
        private Double riskScore;
        private Map<String, Object> metadata;
        private LocalDateTime timestamp;
        
        // Getters and setters
        public String getCaseId() { return caseId; }
        public void setCaseId(String caseId) { this.caseId = caseId; }
        
        public String getReviewType() { return reviewType; }
        public void setReviewType(String reviewType) { this.reviewType = reviewType; }
        
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        
        public String getSubmittedBy() { return submittedBy; }
        public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        
        public String getDisputeId() { return disputeId; }
        public void setDisputeId(String disputeId) { this.disputeId = disputeId; }
        
        public String[] getDocumentIds() { return documentIds; }
        public void setDocumentIds(String[] documentIds) { this.documentIds = documentIds; }
        
        public String getDocumentType() { return documentType; }
        public void setDocumentType(String documentType) { this.documentType = documentType; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getRegulationType() { return regulationType; }
        public void setRegulationType(String regulationType) { this.regulationType = regulationType; }
        
        public String getDisputeType() { return disputeType; }
        public void setDisputeType(String disputeType) { this.disputeType = disputeType; }
        
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public Double getRiskScore() { return riskScore; }
        public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
}