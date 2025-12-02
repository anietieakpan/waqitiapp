package com.waqiti.compliance.kafka;

import com.waqiti.common.events.SanctionsScreeningResultEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.compliance.service.ComplianceWorkflowService;
import com.waqiti.compliance.service.SanctionsReviewService;
import com.waqiti.compliance.audit.ComplianceAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for sanctions screening result events
 * 
 * Handles sanctions screening results by:
 * - Creating compliance review cases for matches
 * - Triggering enhanced due diligence workflows  
 * - Updating compliance audit trail
 * - Escalating high-risk matches to compliance officers
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SanctionsScreeningResultConsumer {

    private final SanctionsReviewService sanctionsReviewService;
    private final ComplianceWorkflowService complianceWorkflowService;
    private final UniversalDLQHandler universalDLQHandler;
    private final ComplianceAuditService auditService;

    @KafkaListener(
        topics = "sanctions-screening-results",
        groupId = "compliance-sanctions-review-group",
        containerFactory = "complianceKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleSanctionsScreeningResult(
            @Payload SanctionsScreeningResultEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.OFFSET) Long offset,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("Processing sanctions screening result event: {} for entity: {} (result: {})",
                    event.getEventId(), event.getEntityName(), event.getResultStatus());
            
            // Audit the screening result
            auditService.auditSanctionsScreeningResult(event);
            
            // Handle based on screening result
            switch (event.getScreeningResult()) {
                case CLEAR:
                    handleClearResult(event);
                    break;
                
                case POTENTIAL_MATCH:
                    handlePotentialMatch(event);
                    break;
                
                case CONFIRMED_MATCH:
                    handleConfirmedMatch(event);
                    break;
                
                case ERROR:
                    handleScreeningError(event);
                    break;
                
                case UNDER_REVIEW:
                    handleUnderReview(event);
                    break;
                
                case FALSE_POSITIVE:
                    handleFalsePositive(event);
                    break;
                
                default:
                    log.warn("Unknown screening result type: {} for event: {}", 
                            event.getScreeningResult(), event.getEventId());
            }
            
            // Update compliance metrics
            updateComplianceMetrics(event);
            
            acknowledgment.acknowledge();
            log.debug("Successfully processed sanctions screening result: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Error processing sanctions screening result event: {} - Error: {}",
                    event.getEventId(), e.getMessage(), e);

            // Send to DLQ for retry/parking
            try {
                org.apache.kafka.clients.consumer.ConsumerRecord<String, SanctionsScreeningResultEvent> consumerRecord =
                    new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                        topic, partition, offset, event.getEventId(), event);
                universalDLQHandler.handleFailedMessage(consumerRecord, e);
            } catch (Exception dlqEx) {
                log.error("CRITICAL: Failed to send sanctions screening result to DLQ: {}", event.getEventId(), dlqEx);
            }

            // Don't acknowledge to trigger retry
            throw new RuntimeException("Failed to process sanctions screening result", e);
        }
    }

    private void handleClearResult(SanctionsScreeningResultEvent event) {
        log.debug("Handling clear sanctions screening result: {}", event.getEventId());
        
        // For clear results, just record in audit trail
        auditService.recordClearScreening(event.getScreeningId(), event.getUserId(), 
                event.getEntityName(), event.getScreeningType());
        
        // Update compliance status if this was for onboarding
        if ("ONBOARDING".equals(event.getScreeningSource())) {
            complianceWorkflowService.updateOnboardingComplianceStatus(
                    event.getUserId(), "SANCTIONS_CLEAR");
        }
    }

    private void handlePotentialMatch(SanctionsScreeningResultEvent event) {
        log.info("Handling potential sanctions match: {} for entity: {}", 
                event.getEventId(), event.getEntityName());
        
        try {
            // Create review case for manual assessment
            String caseId = sanctionsReviewService.createReviewCase(
                    event.getScreeningId(),
                    event.getUserId(),
                    event.getEntityName(),
                    event.getMatches(),
                    event.getScreeningType(),
                    "POTENTIAL_MATCH"
            );
            
            // Assign to compliance officer if specified
            if (event.getAssignedTo() != null) {
                sanctionsReviewService.assignReviewCase(caseId, event.getAssignedTo());
            } else {
                sanctionsReviewService.assignToAvailableOfficer(caseId, event.getRiskLevel());
            }
            
            // Set review deadline
            sanctionsReviewService.setReviewDeadline(caseId, event.getReviewDeadline());
            
            // Trigger enhanced due diligence if required
            if (event.isRequiresEnhancedDueDiligence()) {
                complianceWorkflowService.initiateEnhancedDueDiligence(
                        event.getUserId(), event.getEntityName(), caseId);
            }
            
            // Freeze account for high-risk matches pending review
            if ("HIGH".equals(event.getRiskLevel())) {
                complianceWorkflowService.freezeAccountPendingReview(
                        event.getUserId(), caseId, "Potential sanctions match - high risk");
            }
            
        } catch (Exception e) {
            log.error("Error handling potential sanctions match: {}", event.getEventId(), e);
            throw e;
        }
    }

    private void handleConfirmedMatch(SanctionsScreeningResultEvent event) {
        log.warn("Handling CONFIRMED sanctions match: {} for entity: {}", 
                event.getEventId(), event.getEntityName());
        
        try {
            // Immediately freeze account/block transaction
            complianceWorkflowService.freezeAccountImmediate(
                    event.getUserId(), 
                    "CONFIRMED_SANCTIONS_MATCH", 
                    "Confirmed sanctions match: " + event.getEntityName()
            );
            
            // Create high-priority review case
            String caseId = sanctionsReviewService.createHighPriorityCase(
                    event.getScreeningId(),
                    event.getUserId(),
                    event.getEntityName(),
                    event.getMatches(),
                    "CONFIRMED_MATCH"
            );
            
            // Immediately assign to senior compliance officer
            sanctionsReviewService.assignToSeniorOfficer(caseId);
            
            // Block any pending transactions
            if (event.getTransactionId() != null) {
                complianceWorkflowService.blockTransaction(
                        event.getTransactionId(), caseId, "Confirmed sanctions match");
            }
            
            // Generate suspicious activity report if applicable
            if (event.getTransactionId() != null) {
                complianceWorkflowService.generateSAR(
                        event.getUserId(), event.getTransactionId(), 
                        "SANCTIONS_VIOLATION", event.getMatches());
            }
            
            // Notify regulatory authorities if required
            complianceWorkflowService.notifyRegulatoryAuthorities(
                    event.getUserId(), event.getEntityName(), event.getMatches());
            
        } catch (Exception e) {
            log.error("Error handling confirmed sanctions match: {}", event.getEventId(), e);
            throw e;
        }
    }

    private void handleScreeningError(SanctionsScreeningResultEvent event) {
        log.error("Sanctions screening error for entity: {} - Event: {}", 
                event.getEntityName(), event.getEventId());
        
        // Create technical review case
        sanctionsReviewService.createTechnicalReviewCase(
                event.getScreeningId(),
                event.getUserId(),
                event.getEntityName(),
                "SCREENING_ERROR"
        );
        
        // Alert technical team
        complianceWorkflowService.alertTechnicalTeam(
                "Sanctions screening error", event.getScreeningId(), event.getAdditionalData());
        
        // Schedule retry screening
        complianceWorkflowService.scheduleRetryScreening(
                event.getUserId(), event.getEntityName(), event.getScreeningType());
    }

    private void handleUnderReview(SanctionsScreeningResultEvent event) {
        log.debug("Updating screening status to under review: {}", event.getEventId());
        
        // Update existing review case status
        sanctionsReviewService.updateReviewStatus(
                event.getScreeningId(), "UNDER_REVIEW");
        
        // Set compliance officer if not already assigned
        if (event.getComplianceOfficer() != null) {
            sanctionsReviewService.updateAssignedOfficer(
                    event.getScreeningId(), event.getComplianceOfficer());
        }
    }

    private void handleFalsePositive(SanctionsScreeningResultEvent event) {
        log.debug("Handling false positive screening result: {}", event.getEventId());
        
        // Close any existing review cases
        sanctionsReviewService.closeFalsePositiveCase(
                event.getScreeningId(), event.getComplianceOfficer());
        
        // Unfreeze account if it was frozen
        if (event.getUserId() != null) {
            complianceWorkflowService.unfreezeAccount(
                    event.getUserId(), "False positive - cleared by compliance");
        }
        
        // Update false positive tracking
        sanctionsReviewService.trackFalsePositive(
                event.getEntityName(), event.getMatches());
        
        // Update screening algorithms if needed
        complianceWorkflowService.improveFalsePositiveDetection(event);
    }

    private void updateComplianceMetrics(SanctionsScreeningResultEvent event) {
        try {
            auditService.updateSanctionsScreeningMetrics(
                    event.getScreeningResult().toString(),
                    event.getScreeningType(),
                    event.getProcessingTime()
            );
        } catch (Exception e) {
            log.warn("Failed to update compliance metrics for event: {}", event.getEventId(), e);
            // Don't fail the entire processing for metrics
        }
    }
}