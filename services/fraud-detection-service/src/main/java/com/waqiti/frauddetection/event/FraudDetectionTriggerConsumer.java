package com.waqiti.frauddetection.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.frauddetection.dto.FraudDetectionTriggerEvent;
import com.waqiti.frauddetection.dto.FraudAnalysisResult;
import com.waqiti.frauddetection.service.FraudAnalysisService;
import com.waqiti.frauddetection.service.FraudAlertService;
import com.waqiti.common.exception.EventProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka consumer for fraud detection trigger events.
 * Processes real-time fraud detection requests, analyzes transactions,
 * and triggers appropriate responses based on risk levels.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FraudDetectionTriggerConsumer {

    private final FraudAnalysisService fraudAnalysisService;
    private final FraudAlertService fraudAlertService;
    private final ObjectMapper objectMapper;

    /**
     * Main consumer for fraud-detection-trigger events
     */
    @KafkaListener(
        topics = "fraud-detection-trigger",
        groupId = "fraud-detection-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 500, multiplier = 2),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {EventProcessingException.class}
    )
    @Transactional
    public void handleFraudDetectionTrigger(
            @Payload @Valid FraudDetectionTriggerEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {

        log.info("Triggering fraud analysis for transaction: {} (topic={}, partition={}, offset={})",
            event.getTransactionId(), topic, partition, offset);

        try {
            // Validate event
            validateFraudDetectionEvent(event);

            // Perform fraud analysis
            FraudAnalysisResult result = performFraudAnalysis(event);

            // Process result based on risk level
            processAnalysisResult(event, result);

            // Acknowledge successful processing
            acknowledgment.acknowledge();

            log.info("Completed fraud analysis for transaction {}: risk={}, decision={}",
                event.getTransactionId(), result.getRiskLevel(), result.getDecision());

        } catch (Exception e) {
            log.error("Error processing fraud detection trigger for transaction {}: {}",
                event.getTransactionId(), e.getMessage(), e);

            // Create fallback fraud check result
            createFallbackFraudResult(event, e);

            throw new EventProcessingException("Failed to process fraud detection trigger", e);
        }
    }

    /**
     * Consumer for high-priority fraud alerts
     */
    @KafkaListener(
        topics = "fraud-alert-high-priority",
        groupId = "fraud-alert-high-priority-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleHighPriorityFraudAlert(
            @Payload @Valid FraudDetectionTriggerEvent event,
            Acknowledgment acknowledgment) {

        log.warn("Processing HIGH PRIORITY fraud alert for transaction: {}", event.getTransactionId());

        try {
            // Immediately block transaction
            fraudAlertService.blockTransactionImmediate(event.getTransactionId());

            // Perform emergency fraud analysis
            FraudAnalysisResult result = fraudAnalysisService.performEmergencyAnalysis(event);

            // Send immediate alerts
            fraudAlertService.sendImmediateAlert(event, result);

            // Freeze related accounts if risk is critical
            if (result.getRiskLevel().equals("CRITICAL")) {
                fraudAlertService.freezeRelatedAccounts(event.getUserId());
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing high priority fraud alert: {}", e.getMessage(), e);
            throw new EventProcessingException("Failed to process high priority fraud alert", e);
        }
    }

    /**
     * Consumer for fraud detection model updates
     */
    @KafkaListener(
        topics = "fraud-model-update",
        groupId = "fraud-model-update-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleFraudModelUpdate(
            @Payload String modelUpdateData,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String modelVersion,
            Acknowledgment acknowledgment) {

        log.info("Processing fraud model update: version={}", modelVersion);

        try {
            // Update fraud detection models
            fraudAnalysisService.updateFraudModel(modelVersion, modelUpdateData);

            // Validate new model
            fraudAnalysisService.validateModelUpdate(modelVersion);

            acknowledgment.acknowledge();

            log.info("Successfully updated fraud detection model to version: {}", modelVersion);

        } catch (Exception e) {
            log.error("Error updating fraud detection model: {}", e.getMessage(), e);
            // Acknowledge to prevent infinite retry for model updates
            acknowledgment.acknowledge();
        }
    }

    /**
     * Consumer for user behavior anomaly events
     */
    @KafkaListener(
        topics = "user-behavior-anomaly",
        groupId = "behavior-anomaly-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleUserBehaviorAnomaly(
            @Payload String anomalyData,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String userId,
            Acknowledgment acknowledgment) {

        log.info("Processing user behavior anomaly for user: {}", userId);

        try {
            // Analyze behavior anomaly
            FraudAnalysisResult anomalyResult = fraudAnalysisService.analyzeBehaviorAnomaly(
                userId, anomalyData);

            // Update user risk profile
            fraudAnalysisService.updateUserRiskProfile(userId, anomalyResult);

            // Trigger additional verification if needed
            if (anomalyResult.isRequiresAdditionalVerification()) {
                fraudAlertService.requestAdditionalVerification(userId, anomalyResult.getReason());
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing behavior anomaly for user {}: {}", userId, e.getMessage(), e);
            throw new EventProcessingException("Failed to process behavior anomaly", e);
        }
    }

    /**
     * Consumer for failed fraud detection events (DLT)
     */
    @KafkaListener(
        topics = "fraud-detection-trigger-dlt",
        groupId = "fraud-detection-dlt-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleFailedFraudDetectionEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            Acknowledgment acknowledgment) {

        log.error("Processing failed fraud detection event from DLT: exception={}", exceptionMessage);

        try {
            FraudDetectionTriggerEvent event = objectMapper.readValue(
                eventJson, FraudDetectionTriggerEvent.class);

            // Apply default fraud rules as fallback
            FraudAnalysisResult fallbackResult = fraudAnalysisService.applyDefaultFraudRules(event);

            // Save fallback result
            fraudAnalysisService.saveFraudAnalysisResult(event.getTransactionId(), fallbackResult);

            // Alert operations team about ML system failure
            fraudAlertService.sendSystemFailureAlert(event, exceptionMessage);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process DLT fraud detection event: {}", e.getMessage());
            acknowledgment.acknowledge(); // Acknowledge to prevent infinite loop
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Validate fraud detection event
     */
    private void validateFraudDetectionEvent(FraudDetectionTriggerEvent event) {
        if (event.getTransactionId() == null || event.getTransactionId().isEmpty()) {
            throw new EventProcessingException("Transaction ID is required");
        }
        
        if (event.getUserId() == null || event.getUserId().isEmpty()) {
            throw new EventProcessingException("User ID is required");
        }
        
        if (event.getAmount() == null || event.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new EventProcessingException("Valid transaction amount is required");
        }
    }

    /**
     * Perform comprehensive fraud analysis
     */
    private FraudAnalysisResult performFraudAnalysis(FraudDetectionTriggerEvent event) {
        // Real-time fraud analysis with multiple factors
        return fraudAnalysisService.analyzeTransaction(
            event.getTransactionId(),
            event.getUserId(),
            event.getAmount(),
            event.getCurrency(),
            event.getPaymentMethod(),
            event.getRecipientInfo(),
            event.getTransactionContext(),
            event.getDeviceInformation(),
            event.getLocationData()
        );
    }

    /**
     * Process fraud analysis result and take appropriate actions
     */
    private void processAnalysisResult(FraudDetectionTriggerEvent event, FraudAnalysisResult result) {
        CompletableFuture.runAsync(() -> {
            try {
                switch (result.getDecision()) {
                    case "APPROVE":
                        // Log approval
                        fraudAnalysisService.logFraudAnalysisResult(event.getTransactionId(), result);
                        break;

                    case "REVIEW":
                        // Flag for manual review
                        fraudAlertService.flagForManualReview(event.getTransactionId(), result);
                        break;

                    case "BLOCK":
                        // Block transaction immediately
                        fraudAlertService.blockTransaction(event.getTransactionId(), result.getReason());
                        
                        // Send fraud alert
                        fraudAlertService.sendFraudAlert(event, result);
                        break;

                    case "CHALLENGE":
                        // Request additional authentication
                        fraudAlertService.requestAdditionalAuthentication(
                            event.getTransactionId(), 
                            event.getUserId(), 
                            result.getChallengeType()
                        );
                        break;

                    default:
                        log.warn("Unknown fraud decision: {} for transaction {}", 
                            result.getDecision(), event.getTransactionId());
                }

                // Update user risk score if significant change
                if (result.getRiskScore() > 0.7) {
                    fraudAnalysisService.updateUserRiskScore(event.getUserId(), result.getRiskScore());
                }

                // Store analysis result for future learning
                fraudAnalysisService.saveFraudAnalysisResult(event.getTransactionId(), result);

            } catch (Exception e) {
                log.error("Error processing fraud analysis result for transaction {}: {}", 
                    event.getTransactionId(), e.getMessage(), e);
            }
        });
    }

    /**
     * Create fallback fraud result when analysis fails
     */
    private void createFallbackFraudResult(FraudDetectionTriggerEvent event, Exception error) {
        CompletableFuture.runAsync(() -> {
            try {
                // Apply simple rule-based fallback
                FraudAnalysisResult fallbackResult = fraudAnalysisService.applyFallbackRules(
                    event.getAmount(),
                    event.getUserId(),
                    event.getTransactionId()
                );

                // Save fallback result
                fraudAnalysisService.saveFraudAnalysisResult(event.getTransactionId(), fallbackResult);

                // Alert about ML system failure
                fraudAlertService.sendMLSystemFailureAlert(event.getTransactionId(), error.getMessage());

            } catch (Exception e) {
                log.error("Failed to create fallback fraud result: {}", e.getMessage(), e);
            }
        });
    }
}