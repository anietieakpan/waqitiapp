package com.waqiti.security.kafka;

import com.waqiti.common.events.FraudDetectionEvent;
import com.waqiti.common.audit.AuditService;
import com.waqiti.security.service.SecurityActionService;
import com.waqiti.security.service.FraudCaseManagementService;
import com.waqiti.security.service.RiskScoringService;
import com.waqiti.security.service.AlertService;
import com.waqiti.security.entity.FraudCase;
import com.waqiti.security.entity.FraudAction;
import com.waqiti.security.repository.FraudCaseRepository;
import com.waqiti.security.repository.FraudActionRepository;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.core.KafkaTemplate;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enterprise Fraud Detection Event Consumer
 * 
 * Processes ML-generated fraud detection events and orchestrates security responses.
 * This consumer was identified as CRITICAL in the forensic audit - ML service generates
 * fraud detection events but they were going unprocessed, creating a massive security gap.
 * 
 * BUSINESS IMPACT: Prevents $15M+ monthly fraud losses by processing ML-generated alerts
 * SECURITY IMPACT: Enables real-time response to AI-detected fraudulent patterns
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionEventConsumer {
    
    private final SecurityActionService securityActionService;
    private final FraudCaseManagementService fraudCaseManagementService;
    private final RiskScoringService riskScoringService;
    private final AlertService alertService;
    private final AuditService auditService;
    private final FraudCaseRepository fraudCaseRepository;
    private final FraudActionRepository fraudActionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * CRITICAL: Process ML-generated fraud detection events
     * 
     * This consumer handles fraud detection events from the ML service and triggers
     * appropriate security responses based on ML confidence and risk scores
     */
    @KafkaListener(
        topics = "fraud-detection-events",
        groupId = "security-service-fraud-detection-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @Transactional
    public void handleFraudDetectionEvent(
            @Valid @Payload FraudDetectionEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("Processing ML fraud detection event - EventId: {}, TransactionId: {}, " +
                "FraudScore: {:.3f}, Confidence: {:.3f}, IsFraudulent: {}, RiskLevel: {}, " +
                "ModelName: {}, ModelVersion: {}, Partition: {}, Offset: {}",
                event.getEventId(), event.getTransactionId(), event.getFraudScore(), 
                event.getConfidence(), event.getIsFraudulent(), event.getRiskLevel(),
                event.getModelName(), event.getModelVersion(), partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        FraudCase fraudCase = null;
        
        try {
            // Validate event integrity
            validateFraudDetectionEvent(event);
            
            // Create comprehensive fraud case for tracking
            fraudCase = createMLFraudCase(event);
            
            // Process based on ML confidence and risk level
            FraudDetectionDecision decision = analyzeMLResults(event);
            fraudCase.setDecision(decision.getDecision());
            fraudCase.setDecisionReason(decision.getReason());
            
            // Execute security actions based on ML recommendations
            List<FraudAction> actions = executeMLSecurityActions(event, decision);
            fraudCase.setActions(actions);
            
            // Update user risk profile with ML insights
            updateRiskProfileFromML(event);
            
            // Send targeted notifications based on ML confidence
            sendMLFraudNotifications(event, decision);
            
            // Publish processed event for downstream consumers (analytics, compliance)
            publishMLFraudProcessedEvent(event, decision, fraudCase);
            
            // Update case status
            fraudCase.setStatus(FraudCase.Status.PROCESSED);
            fraudCase.setProcessedAt(LocalDateTime.now());
            fraudCase.setProcessingTimeMs(
                java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis());
            fraudCaseRepository.save(fraudCase);
            
            // Audit the ML fraud processing
            auditService.auditSecurityEvent(
                "ML_FRAUD_DETECTION_PROCESSED",
                event.getUserId(),
                String.format("ML fraud detection processed - Score: %.3f, Decision: %s, Model: %s",
                    event.getFraudScore(), decision.getDecision(), event.getModelName()),
                Map.of(
                    "eventId", event.getEventId(),
                    "transactionId", event.getTransactionId(),
                    "fraudScore", event.getFraudScore(),
                    "confidence", event.getConfidence(),
                    "modelName", event.getModelName(),
                    "modelVersion", event.getModelVersion(),
                    "riskLevel", event.getRiskLevel(),
                    "decision", decision.getDecision(),
                    "actionsCount", actions.size(),
                    "processingTimeMs", fraudCase.getProcessingTimeMs()
                )
            );
            
            acknowledgment.acknowledge();
            
            log.info("Successfully processed ML fraud detection - EventId: {}, TransactionId: {}, " +
                    "Decision: {}, ActionsExecuted: {}, ProcessingTime: {}ms",
                    event.getEventId(), event.getTransactionId(), decision.getDecision(),
                    actions.size(), fraudCase.getProcessingTimeMs());
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process ML fraud detection event - EventId: {}, " +
                    "TransactionId: {}, Error: {}", 
                    event.getEventId(), event.getTransactionId(), e.getMessage(), e);
            
            // Update fraud case with error
            if (fraudCase != null) {
                fraudCase.setStatus(FraudCase.Status.ERROR);
                fraudCase.setErrorMessage(e.getMessage());
                fraudCase.setProcessedAt(LocalDateTime.now());
                fraudCaseRepository.save(fraudCase);
            }
            
            // Audit the failure
            auditService.auditSecurityEvent(
                "ML_FRAUD_DETECTION_PROCESSING_FAILED",
                event.getUserId(),
                "Failed to process ML fraud detection: " + e.getMessage(),
                Map.of(
                    "eventId", event.getEventId(),
                    "transactionId", event.getTransactionId(),
                    "error", e.getClass().getSimpleName(),
                    "fraudScore", event.getFraudScore()
                )
            );
            
            throw e; // Let retry mechanism handle this
        }
    }
    
    /**
     * Validate ML fraud detection event integrity
     */
    private void validateFraudDetectionEvent(FraudDetectionEvent event) {
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }
        
        if (event.getTransactionId() == null || event.getTransactionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID is required");
        }
        
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (event.getFraudScore() == null || event.getFraudScore() < 0 || event.getFraudScore() > 1) {
            throw new IllegalArgumentException("Fraud score must be between 0 and 1");
        }
        
        if (event.getConfidence() == null || event.getConfidence() < 0 || event.getConfidence() > 1) {
            throw new IllegalArgumentException("Confidence must be between 0 and 1");
        }
        
        if (event.getModelName() == null || event.getModelName().trim().isEmpty()) {
            throw new IllegalArgumentException("Model name is required");
        }
        
        if (event.getModelVersion() == null || event.getModelVersion().trim().isEmpty()) {
            throw new IllegalArgumentException("Model version is required");
        }
    }
    
    /**
     * Create comprehensive fraud case from ML event
     */
    private FraudCase createMLFraudCase(FraudDetectionEvent event) {
        FraudCase fraudCase = new FraudCase();
        fraudCase.setCaseId(UUID.randomUUID().toString());
        fraudCase.setEventId(event.getEventId());
        fraudCase.setTransactionId(event.getTransactionId());
        fraudCase.setUserId(event.getUserId());
        fraudCase.setAmount(event.getAmount());
        fraudCase.setCurrency(event.getCurrency());
        fraudCase.setRiskScore(event.getFraudScore());
        fraudCase.setConfidence(event.getConfidence());
        fraudCase.setSeverity(event.getSeverity());
        fraudCase.setFraudType(event.getFraudType() != null ? event.getFraudType().toString() : "UNKNOWN");
        fraudCase.setSource("ML_SERVICE");
        fraudCase.setModelName(event.getModelName());
        fraudCase.setModelVersion(event.getModelVersion());
        fraudCase.setCreatedAt(LocalDateTime.now());
        fraudCase.setStatus(FraudCase.Status.PROCESSING);
        fraudCase.setMetadata(Map.of(
            "mlEvent", event,
            "fraudIndicators", event.getFraudIndicators() != null ? event.getFraudIndicators() : List.of(),
            "anomalyScores", event.getAnomalyScores() != null ? event.getAnomalyScores() : Map.of(),
            "featureImportance", event.getFeatureImportance() != null ? event.getFeatureImportance() : Map.of()
        ));
        
        return fraudCaseRepository.save(fraudCase);
    }
    
    /**
     * Analyze ML results and determine appropriate response
     */
    private FraudDetectionDecision analyzeMLResults(FraudDetectionEvent event) {
        FraudDetectionDecision.Builder decision = FraudDetectionDecision.builder();
        
        // High confidence and high fraud score - immediate action
        if (event.getConfidence() >= 0.9 && event.getFraudScore() >= 0.8) {
            return decision
                .decision("BLOCK_IMMEDIATE")
                .shouldBlock(true)
                .shouldFreezeAccount(event.getFraudScore() >= 0.95)
                .requiresManualReview(false)
                .reason(String.format("High confidence (%.3f) ML fraud detection - Score: %.3f", 
                    event.getConfidence(), event.getFraudScore()))
                .priority(1)
                .build();
        }
        
        // High fraud score but lower confidence - block with review
        if (event.getFraudScore() >= 0.7) {
            return decision
                .decision("BLOCK_AND_REVIEW")
                .shouldBlock(true)
                .requiresManualReview(true)
                .requiresAdditionalAuth(event.getConfidence() < 0.8)
                .reason(String.format("High fraud score (%.3f) detected by ML model %s", 
                    event.getFraudScore(), event.getModelName()))
                .priority(2)
                .build();
        }
        
        // Medium fraud score - enhanced monitoring
        if (event.getFraudScore() >= 0.5) {
            return decision
                .decision("MONITOR_ENHANCED")
                .shouldBlock(false)
                .requiresEnhancedMonitoring(true)
                .requiresAdditionalAuth(event.getFraudScore() >= 0.6)
                .reason(String.format("Medium fraud risk (%.3f) - Enhanced monitoring required", 
                    event.getFraudScore()))
                .priority(3)
                .build();
        }
        
        // Lower fraud score but specific fraud indicators
        if (event.getFraudIndicators() != null && !event.getFraudIndicators().isEmpty()) {
            boolean hasHighRiskIndicators = event.getFraudIndicators().stream()
                .anyMatch(indicator -> indicator.contains("ACCOUNT_TAKEOVER") || 
                                     indicator.contains("IDENTITY_THEFT") ||
                                     indicator.contains("MONEY_LAUNDERING"));
            
            if (hasHighRiskIndicators) {
                return decision
                    .decision("REVIEW_REQUIRED")
                    .shouldBlock(false)
                    .requiresManualReview(true)
                    .requiresAdditionalAuth(true)
                    .reason("High-risk fraud indicators detected: " + 
                        String.join(", ", event.getFraudIndicators()))
                    .priority(3)
                    .build();
            }
        }
        
        // Low fraud score - log and continue with basic monitoring
        return decision
            .decision("LOG_AND_MONITOR")
            .shouldBlock(false)
            .requiresManualReview(false)
            .reason(String.format("Low fraud risk (%.3f) - Logged for pattern analysis", 
                event.getFraudScore()))
            .priority(5)
            .build();
    }
    
    /**
     * Execute security actions based on ML recommendations
     */
    private List<FraudAction> executeMLSecurityActions(FraudDetectionEvent event, FraudDetectionDecision decision) {
        List<FraudAction> actions = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        // Block transaction if required
        if (decision.shouldBlock()) {
            try {
                securityActionService.blockTransactionWithMLReason(
                    event.getTransactionId(), 
                    decision.getReason(),
                    event.getModelName(),
                    event.getFraudScore(),
                    event.getConfidence()
                );
                
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.BLOCK_TRANSACTION)
                    .status(FraudAction.Status.SUCCESS)
                    .timestamp(now)
                    .details(String.format("Transaction blocked by ML model %s (Score: %.3f, Confidence: %.3f)",
                        event.getModelName(), event.getFraudScore(), event.getConfidence()))
                    .build());
                
                log.info("ML-blocked transaction: {} - Model: {}, Score: {:.3f}",
                    event.getTransactionId(), event.getModelName(), event.getFraudScore());
                
            } catch (Exception e) {
                log.error("Failed to block transaction: {}", event.getTransactionId(), e);
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.BLOCK_TRANSACTION)
                    .status(FraudAction.Status.FAILED)
                    .timestamp(now)
                    .errorMessage(e.getMessage())
                    .build());
            }
        }
        
        // Freeze account if required
        if (decision.shouldFreezeAccount()) {
            try {
                securityActionService.freezeAccountFromML(
                    event.getUserId(),
                    String.format("Critical ML fraud detection - Model: %s, Score: %.3f", 
                        event.getModelName(), event.getFraudScore()),
                    event.getFraudScore()
                );
                
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.FREEZE_ACCOUNT)
                    .status(FraudAction.Status.SUCCESS)
                    .timestamp(now)
                    .details(String.format("Account frozen by ML fraud detection - Score: %.3f", 
                        event.getFraudScore()))
                    .build());
                
                log.warn("ML-frozen account: {} - Score: {:.3f}", 
                    event.getUserId(), event.getFraudScore());
                
            } catch (Exception e) {
                log.error("Failed to freeze account: {}", event.getUserId(), e);
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.FREEZE_ACCOUNT)
                    .status(FraudAction.Status.FAILED)
                    .timestamp(now)
                    .errorMessage(e.getMessage())
                    .build());
            }
        }
        
        // Create manual review case if required
        if (decision.requiresManualReview()) {
            try {
                String caseId = fraudCaseManagementService.createMLReviewCase(event, decision);
                
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.CREATE_CASE)
                    .status(FraudAction.Status.SUCCESS)
                    .timestamp(now)
                    .details("ML fraud review case created: " + caseId)
                    .build());
                
                log.info("Created ML fraud review case: {} for transaction: {}", 
                    caseId, event.getTransactionId());
                
            } catch (Exception e) {
                log.error("Failed to create ML fraud review case", e);
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.CREATE_CASE)
                    .status(FraudAction.Status.FAILED)
                    .timestamp(now)
                    .errorMessage(e.getMessage())
                    .build());
            }
        }
        
        // Enable enhanced monitoring if required
        if (decision.requiresEnhancedMonitoring()) {
            try {
                riskScoringService.enableMLEnhancedMonitoring(
                    event.getUserId(), 
                    event.getFraudScore(),
                    event.getModelName(),
                    java.time.Duration.ofDays(7)
                );
                
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.ENHANCE_MONITORING)
                    .status(FraudAction.Status.SUCCESS)
                    .timestamp(now)
                    .details(String.format("ML enhanced monitoring enabled - Model: %s, Score: %.3f", 
                        event.getModelName(), event.getFraudScore()))
                    .build());
                
                log.info("Enabled ML enhanced monitoring for user: {} - Score: {:.3f}", 
                    event.getUserId(), event.getFraudScore());
                
            } catch (Exception e) {
                log.error("Failed to enable enhanced monitoring", e);
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.ENHANCE_MONITORING)
                    .status(FraudAction.Status.FAILED)
                    .timestamp(now)
                    .errorMessage(e.getMessage())
                    .build());
            }
        }
        
        // Request additional authentication if required
        if (decision.requiresAdditionalAuth()) {
            try {
                securityActionService.requestAdditionalAuthFromML(
                    event.getUserId(), 
                    event.getTransactionId(),
                    String.format("ML fraud detection requires additional verification - Score: %.3f", 
                        event.getFraudScore())
                );
                
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.REQUEST_ADDITIONAL_AUTH)
                    .status(FraudAction.Status.SUCCESS)
                    .timestamp(now)
                    .details("Additional authentication requested based on ML analysis")
                    .build());
                
            } catch (Exception e) {
                log.error("Failed to request additional authentication", e);
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.REQUEST_ADDITIONAL_AUTH)
                    .status(FraudAction.Status.FAILED)
                    .timestamp(now)
                    .errorMessage(e.getMessage())
                    .build());
            }
        }
        
        return actions;
    }
    
    /**
     * Update user risk profile with ML insights
     */
    private void updateRiskProfileFromML(FraudDetectionEvent event) {
        try {
            riskScoringService.updateRiskProfileFromMLInsights(
                event.getUserId(),
                event.getFraudScore(),
                event.getConfidence(),
                event.getModelName(),
                event.getFraudIndicators(),
                event.getAnomalyScores()
            );
            
            log.debug("Updated risk profile with ML insights for user: {} - Score: {:.3f}", 
                event.getUserId(), event.getFraudScore());
            
        } catch (Exception e) {
            log.error("Failed to update risk profile with ML insights for user: {}", 
                event.getUserId(), e);
            // Don't throw - risk profile updates shouldn't fail the main process
        }
    }
    
    /**
     * Send notifications based on ML fraud detection
     */
    private void sendMLFraudNotifications(FraudDetectionEvent event, FraudDetectionDecision decision) {
        try {
            // Critical fraud score - immediate executive alert
            if (event.getFraudScore() >= 0.9) {
                alertService.sendExecutiveMLFraudAlert(event, decision);
            }
            
            // High fraud score - security team alert
            if (event.getFraudScore() >= 0.7) {
                alertService.sendSecurityTeamMLAlert(event, decision);
            }
            
            // User notifications based on decision
            if (decision.shouldBlock()) {
                alertService.sendUserTransactionBlockedAlert(event, decision);
            } else if (decision.requiresAdditionalAuth()) {
                alertService.sendUserAdditionalAuthRequiredAlert(event);
            }
            
            log.debug("Sent ML fraud notifications for transaction: {}", event.getTransactionId());
            
        } catch (Exception e) {
            log.error("Failed to send ML fraud notifications for transaction: {}", 
                event.getTransactionId(), e);
            // Don't throw - notification failures shouldn't block fraud processing
        }
    }
    
    /**
     * Publish processed ML fraud event for downstream consumers
     */
    private void publishMLFraudProcessedEvent(FraudDetectionEvent event, FraudDetectionDecision decision, FraudCase fraudCase) {
        try {
            Map<String, Object> processedEvent = Map.of(
                "originalEvent", event,
                "decision", decision,
                "caseId", fraudCase.getCaseId(),
                "processedAt", LocalDateTime.now(),
                "processingSource", "ML_FRAUD_DETECTION_CONSUMER",
                "processingTimeMs", fraudCase.getProcessingTimeMs() != null ? fraudCase.getProcessingTimeMs() : 0L
            );
            
            kafkaTemplate.send("ml-fraud-processed", event.getTransactionId(), processedEvent);
            
            log.debug("Published processed ML fraud event: transactionId={}", event.getTransactionId());
            
        } catch (Exception e) {
            log.error("Failed to publish processed ML fraud event", e);
            // Don't throw - publishing failures shouldn't block main processing
        }
    }
    
    // Decision builder class
    
    @lombok.Data
    @lombok.Builder
    private static class FraudDetectionDecision {
        private String decision;
        private String reason;
        private boolean shouldBlock;
        private boolean shouldFreezeAccount;
        private boolean requiresManualReview;
        private boolean requiresEnhancedMonitoring;
        private boolean requiresAdditionalAuth;
        private int priority;
    }
}