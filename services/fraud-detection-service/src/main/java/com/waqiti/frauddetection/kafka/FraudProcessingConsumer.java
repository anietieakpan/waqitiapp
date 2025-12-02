package com.waqiti.frauddetection.kafka;

import com.waqiti.frauddetection.event.FraudEvent;
import com.waqiti.frauddetection.service.FraudDetectionService;
import com.waqiti.frauddetection.service.FraudInvestigationService;
import com.waqiti.frauddetection.service.FraudPreventionService;
import com.waqiti.frauddetection.service.MachineLearningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Production-grade Kafka consumer for fraud processing events
 * Handles: fraud-detection-trigger, fraud-processing-errors, fraud-detection-results,
 * fraud-response-events, fraud-alerts, fraud-alerts-dlq, fraud-team-alerts,
 * fraud-activity-logs, fraud-processed, fraud-analysis-completed, ml-fraud-processed,
 * fraud-detection-events, fraud-user-not-found, crypto-fraud-alert, model-feedback
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FraudProcessingConsumer {

    private final FraudDetectionService detectionService;
    private final FraudInvestigationService investigationService;
    private final FraudPreventionService preventionService;
    private final MachineLearningService mlService;

    @KafkaListener(topics = {"fraud-detection-trigger", "fraud-processing-errors", "fraud-detection-results",
                             "fraud-response-events", "fraud-alerts", "fraud-alerts-dlq", "fraud-team-alerts",
                             "fraud-activity-logs", "fraud-processed", "fraud-analysis-completed",
                             "ml-fraud-processed", "fraud-detection-events", "fraud-user-not-found",
                             "crypto-fraud-alert", "model-feedback"}, 
                   groupId = "fraud-processing-group")
    @Transactional
    public void processFraudEvent(@Payload FraudEvent event,
                                @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                @Header(KafkaHeaders.OFFSET) long offset,
                                Acknowledgment acknowledgment) {
        try {
            log.info("Processing fraud event: {} - Type: {} - Entity: {} - Score: {}", 
                    event.getFraudId(), event.getFraudType(), event.getEntityId(), event.getFraudScore());
            
            // Process based on topic
            switch (topic) {
                case "fraud-detection-trigger" -> triggerFraudDetection(event);
                case "fraud-processing-errors" -> handleProcessingError(event);
                case "fraud-detection-results" -> handleDetectionResults(event);
                case "fraud-response-events" -> handleFraudResponse(event);
                case "fraud-alerts", "fraud-alerts-dlq" -> processFraudAlert(event);
                case "fraud-team-alerts" -> alertFraudTeam(event);
                case "fraud-activity-logs" -> logFraudActivity(event);
                case "fraud-processed" -> handleProcessedFraud(event);
                case "fraud-analysis-completed" -> handleAnalysisCompleted(event);
                case "ml-fraud-processed" -> handleMlProcessed(event);
                case "fraud-detection-events" -> handleDetectionEvent(event);
                case "fraud-user-not-found" -> handleUserNotFound(event);
                case "crypto-fraud-alert" -> handleCryptoFraud(event);
                case "model-feedback" -> processModelFeedback(event);
            }
            
            // Update fraud metrics
            updateFraudMetrics(event);
            
            // Acknowledge
            acknowledgment.acknowledge();
            
            log.info("Successfully processed fraud event: {}", event.getFraudId());
            
        } catch (Exception e) {
            log.error("Failed to process fraud event {}: {}", 
                    event.getFraudId(), e.getMessage(), e);
            throw new RuntimeException("Fraud processing failed", e);
        }
    }

    private void triggerFraudDetection(FraudEvent event) {
        // Run fraud detection
        Map<String, Object> detectionResult = detectionService.detectFraud(
            event.getEntityId(),
            event.getTransactionId(),
            event.getAmount(),
            event.getTransactionData()
        );
        
        double fraudScore = (Double) detectionResult.get("score");
        event.setFraudScore(fraudScore);
        
        // Take action based on score
        if (fraudScore > 90) {
            // Confirmed fraud
            handleConfirmedFraud(event);
        } else if (fraudScore > 70) {
            // Suspicious - needs review
            escalateForReview(event);
        } else if (fraudScore > 50) {
            // Monitor closely
            enableEnhancedMonitoring(event);
        }
        
        // Store detection result
        detectionService.storeDetectionResult(
            event.getFraudId(),
            detectionResult,
            LocalDateTime.now()
        );
    }

    private void handleProcessingError(FraudEvent event) {
        // Log processing error
        detectionService.logProcessingError(
            event.getFraudId(),
            event.getErrorType(),
            event.getErrorMessage(),
            event.getStackTrace()
        );
        
        // Attempt recovery
        if (event.isRecoverable() && event.getRetryCount() < 3) {
            detectionService.scheduleRetry(
                event.getFraudId(),
                event.getRetryCount() + 1,
                LocalDateTime.now().plusMinutes(5)
            );
        } else {
            // Escalate to manual review
            investigationService.createManualReviewCase(
                event.getFraudId(),
                "PROCESSING_ERROR",
                event.getErrorDetails()
            );
        }
    }

    private void handleDetectionResults(FraudEvent event) {
        // Process detection results
        String decision = event.getDetectionDecision();
        
        switch (decision) {
            case "BLOCK" -> {
                preventionService.blockEntity(event.getEntityId(), event.getBlockReason());
                preventionService.blockTransaction(event.getTransactionId());
            }
            case "ALLOW" -> {
                detectionService.markAsLegitimate(event.getTransactionId());
            }
            case "REVIEW" -> {
                investigationService.createReviewCase(event.getFraudId(), event.getReviewPriority());
            }
            case "MONITOR" -> {
                detectionService.addToWatchlist(event.getEntityId(), event.getMonitoringLevel());
            }
        }
        
        // Update fraud model
        mlService.updateModel(
            event.getFraudType(),
            event.getFeatureVector(),
            decision
        );
    }

    private void handleFraudResponse(FraudEvent event) {
        // Execute fraud response
        String responseType = event.getResponseType();
        
        switch (responseType) {
            case "IMMEDIATE_BLOCK" -> {
                preventionService.immediateBlock(
                    event.getEntityId(),
                    event.getAffectedAccounts(),
                    event.getBlockDuration()
                );
            }
            case "FUNDS_RECOVERY" -> {
                investigationService.initiateFundsRecovery(
                    event.getTransactionId(),
                    event.getAmount(),
                    event.getRecoveryMethod()
                );
            }
            case "LAW_ENFORCEMENT" -> {
                investigationService.fileLawEnforcementReport(
                    event.getFraudId(),
                    event.getCrimeType(),
                    event.getEvidencePackage()
                );
            }
            case "CUSTOMER_NOTIFICATION" -> {
                preventionService.notifyAffectedCustomers(
                    event.getAffectedUsers(),
                    event.getFraudType(),
                    event.getRecommendedActions()
                );
            }
        }
    }

    private void processFraudAlert(FraudEvent event) {
        // Create fraud alert
        String alertId = detectionService.createAlert(
            event.getFraudType(),
            event.getSeverity(),
            event.getEntityId(),
            event.getAlertDetails()
        );
        
        // Determine alert routing
        if ("CRITICAL".equals(event.getSeverity())) {
            // Immediate escalation
            investigationService.createUrgentCase(alertId, event);
            preventionService.applyEmergencyMeasures(event.getEntityId());
        } else if ("HIGH".equals(event.getSeverity())) {
            // Standard investigation
            investigationService.createInvestigation(alertId, event);
        }
        
        // Send notifications
        detectionService.sendAlertNotifications(
            alertId,
            event.getNotificationTargets(),
            event.getSeverity()
        );
    }

    private void alertFraudTeam(FraudEvent event) {
        // Alert fraud team
        investigationService.alertTeam(
            event.getTeamId(),
            event.getAlertType(),
            event.getPriority(),
            event.getCaseDetails()
        );
        
        // Assign to investigator
        String investigatorId = investigationService.assignInvestigator(
            event.getFraudId(),
            event.getExpertiseRequired(),
            event.getPriority()
        );
        
        // Set SLA
        investigationService.setSla(
            event.getFraudId(),
            investigatorId,
            event.getSlaHours()
        );
    }

    private void logFraudActivity(FraudEvent event) {
        // Log fraud activity
        detectionService.logActivity(
            event.getActivityId(),
            event.getActivityType(),
            event.getEntityId(),
            event.getActivityDetails(),
            event.getTimestamp()
        );
        
        // Update behavior profile
        mlService.updateBehaviorProfile(
            event.getEntityId(),
            event.getActivityType(),
            event.getBehaviorMetrics()
        );
        
        // Check for patterns
        if (detectionService.detectsSuspiciousPattern(
                event.getEntityId(),
                event.getActivityHistory())) {
            
            triggerPatternInvestigation(event);
        }
    }

    private void handleProcessedFraud(FraudEvent event) {
        // Mark as processed
        detectionService.markAsProcessed(
            event.getFraudId(),
            event.getProcessingResult(),
            event.getProcessedAt()
        );
        
        // Update statistics
        detectionService.updateStatistics(
            event.getFraudType(),
            event.getProcessingResult(),
            event.getProcessingTime()
        );
        
        // Close case if resolved
        if ("RESOLVED".equals(event.getProcessingResult())) {
            investigationService.closeCase(
                event.getFraudId(),
                event.getResolutionDetails(),
                event.getRecoveredAmount()
            );
        }
    }

    private void handleAnalysisCompleted(FraudEvent event) {
        // Process analysis results
        Map<String, Object> analysis = event.getAnalysisResults();
        
        // Update fraud indicators
        detectionService.updateFraudIndicators(
            event.getEntityId(),
            (Map<String, Double>) analysis.get("indicators")
        );
        
        // Apply recommendations
        for (String recommendation : event.getRecommendations()) {
            preventionService.applyRecommendation(
                event.getEntityId(),
                recommendation
            );
        }
        
        // Schedule follow-up
        if (event.isFollowUpRequired()) {
            investigationService.scheduleFollowUp(
                event.getFraudId(),
                event.getFollowUpDate(),
                event.getFollowUpActions()
            );
        }
    }

    private void handleMlProcessed(FraudEvent event) {
        // ML model processing complete
        mlService.recordPrediction(
            event.getModelId(),
            event.getPredictionId(),
            event.getPredictedScore(),
            event.getConfidence(),
            event.getFeatures()
        );
        
        // Update model performance
        mlService.updateModelPerformance(
            event.getModelId(),
            event.getActualOutcome(),
            event.getPredictedScore()
        );
        
        // Retrain if needed
        if (event.isRetrainingRequired()) {
            mlService.scheduleRetraining(
                event.getModelId(),
                event.getRetrainingData(),
                event.getRetrainingPriority()
            );
        }
    }

    private void handleDetectionEvent(FraudEvent event) {
        // Generic fraud detection event
        detectionService.processDetectionEvent(
            event.getEventId(),
            event.getEventType(),
            event.getEventData()
        );
    }

    private void handleUserNotFound(FraudEvent event) {
        // User not found during fraud check
        log.warn("User not found for fraud check: {}", event.getEntityId());
        
        // Create investigation
        investigationService.investigateUnknownUser(
            event.getEntityId(),
            event.getTransactionId(),
            event.getSuspiciousIndicators()
        );
        
        // Block transaction as precaution
        preventionService.blockTransaction(
            event.getTransactionId(),
            "USER_NOT_FOUND"
        );
    }

    private void handleCryptoFraud(FraudEvent event) {
        // Crypto-specific fraud
        String walletAddress = event.getWalletAddress();
        
        // Check blockchain
        Map<String, Object> blockchainData = detectionService.analyzeBlockchain(
            walletAddress,
            event.getTransactionHash()
        );
        
        // Check for mixing services
        if ((Boolean) blockchainData.get("usesMixer")) {
            preventionService.flagHighRiskCrypto(
                walletAddress,
                "MIXER_DETECTED"
            );
        }
        
        // Check sanctions list
        if (detectionService.isOnCryptoSanctionsList(walletAddress)) {
            preventionService.blockCryptoAddress(
                walletAddress,
                "SANCTIONS_HIT"
            );
            
            // File SAR
            investigationService.fileCryptoSar(
                event.getFraudId(),
                walletAddress,
                event.getAmount()
            );
        }
    }

    private void processModelFeedback(FraudEvent event) {
        // Process model feedback
        mlService.processFeedback(
            event.getModelId(),
            event.getPredictionId(),
            event.getFeedbackType(),
            event.getActualOutcome(),
            event.getFeedbackDetails()
        );
        
        // Update model accuracy metrics
        mlService.updateAccuracyMetrics(
            event.getModelId(),
            event.getFeedbackType(),
            event.getActualOutcome()
        );
        
        // Add to training dataset
        if (event.isUsefulForTraining()) {
            mlService.addToTrainingData(
                event.getFeatureVector(),
                event.getActualOutcome(),
                event.getImportance()
            );
        }
    }

    private void handleConfirmedFraud(FraudEvent event) {
        // Immediate actions for confirmed fraud
        preventionService.executeEmergencyProtocol(
            event.getEntityId(),
            event.getFraudType()
        );
        
        // Notify all systems
        detectionService.broadcastFraudAlert(
            event.getFraudId(),
            event.getEntityId(),
            event.getFraudType()
        );
        
        // Create high-priority investigation
        investigationService.createHighPriorityInvestigation(
            event.getFraudId(),
            event.getFraudType(),
            event.getEstimatedLoss()
        );
    }

    private void escalateForReview(FraudEvent event) {
        // Create review case
        String caseId = investigationService.createReviewCase(
            event.getFraudId(),
            event.getReviewPriority()
        );
        
        // Apply temporary restrictions
        preventionService.applyTemporaryRestrictions(
            event.getEntityId(),
            event.getRestrictionLevel()
        );
        
        // Set review deadline
        investigationService.setDeadline(
            caseId,
            LocalDateTime.now().plusHours(event.getReviewSla())
        );
    }

    private void enableEnhancedMonitoring(FraudEvent event) {
        // Enable enhanced monitoring
        detectionService.enableEnhancedMonitoring(
            event.getEntityId(),
            event.getMonitoringLevel(),
            event.getMonitoringDuration()
        );
        
        // Adjust risk thresholds
        detectionService.adjustRiskThresholds(
            event.getEntityId(),
            event.getNewThresholds()
        );
    }

    private void triggerPatternInvestigation(FraudEvent event) {
        // Investigate suspicious patterns
        investigationService.investigatePattern(
            event.getEntityId(),
            event.getPatternType(),
            event.getPatternDetails()
        );
    }

    private void updateFraudMetrics(FraudEvent event) {
        // Update fraud metrics
        detectionService.updateMetrics(
            event.getFraudType(),
            event.getFraudScore(),
            event.getDetectionTime(),
            event.getPreventedLoss()
        );
    }
}