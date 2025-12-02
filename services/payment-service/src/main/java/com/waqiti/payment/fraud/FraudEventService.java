package com.waqiti.payment.fraud;

import com.waqiti.payment.fraud.dto.FraudValidationResult;
import com.waqiti.payment.fraud.model.FraudReviewCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fraud Event Publishing Service
 *
 * Publishes fraud-related events to Kafka for real-time monitoring,
 * analytics, and downstream processing.
 *
 * Events published:
 * - FRAUD_BLOCKED: When a transaction is blocked due to high fraud risk
 * - FRAUD_REVIEW_QUEUED: When a transaction is queued for manual review
 * - FRAUD_APPROVED: When a transaction passes fraud checks
 * - FRAUD_ESCALATED: When a case is escalated to senior analyst
 *
 * @author Waqiti Fraud Detection Team
 * @version 2.0.0
 * @since 2025-10-16
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudEventService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String FRAUD_ALERTS_TOPIC = "fraud-alerts";
    private static final String FRAUD_REVIEW_EVENTS_TOPIC = "fraud-review-events";

    /**
     * Publish fraud blocked event
     *
     * @param paymentId Payment ID that was blocked
     * @param userId User ID associated with payment
     * @param fraudResult Fraud validation result with details
     */
    @Async
    public void publishFraudBlocked(UUID paymentId, String userId, FraudValidationResult fraudResult) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "FRAUD_BLOCKED");
            event.put("paymentId", paymentId.toString());
            event.put("userId", userId);
            event.put("riskScore", fraudResult.getRiskScore());
            event.put("riskLevel", fraudResult.getRiskLevel());
            event.put("reason", fraudResult.getReason());
            event.put("triggeredRules", fraudResult.getTriggeredRules());
            event.put("modelVersion", fraudResult.getModelVersion());
            event.put("confidence", fraudResult.getConfidence());
            event.put("timestamp", LocalDateTime.now().toString());

            kafkaTemplate.send(FRAUD_ALERTS_TOPIC, paymentId.toString(), event);

            log.info("FRAUD EVENT: Published fraud blocked event: paymentId={}, userId={}, riskScore={}",
                paymentId, userId, fraudResult.getRiskScore());

        } catch (Exception e) {
            log.error("FRAUD EVENT: Failed to publish fraud blocked event: paymentId={}, userId={}",
                paymentId, userId, e);
            // Don't throw - event publishing failure shouldn't block fraud detection
        }
    }

    /**
     * Publish fraud review queued event
     *
     * @param paymentId Payment ID queued for review
     * @param userId User ID associated with payment
     * @param reviewCase Fraud review case details
     */
    @Async
    public void publishFraudReviewQueued(UUID paymentId, String userId, FraudReviewCase reviewCase) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "FRAUD_REVIEW_QUEUED");
            event.put("reviewId", reviewCase.getReviewId());
            event.put("paymentId", paymentId.toString());
            event.put("userId", userId);
            event.put("priority", reviewCase.getPriority());
            event.put("riskScore", reviewCase.getRiskScore());
            event.put("riskLevel", reviewCase.getRiskLevel());
            event.put("amount", reviewCase.getAmount());
            event.put("currency", reviewCase.getCurrency());
            event.put("slaDeadline", reviewCase.getSlaDeadline().toString());
            event.put("queuedAt", reviewCase.getQueuedAt().toString());
            event.put("timestamp", LocalDateTime.now().toString());

            kafkaTemplate.send(FRAUD_REVIEW_EVENTS_TOPIC, reviewCase.getReviewId(), event);

            log.info("FRAUD EVENT: Published fraud review queued event: reviewId={}, paymentId={}, priority={}",
                reviewCase.getReviewId(), paymentId, reviewCase.getPriority());

        } catch (Exception e) {
            log.error("FRAUD EVENT: Failed to publish fraud review queued event: reviewId={}, paymentId={}",
                reviewCase != null ? reviewCase.getReviewId() : "null", paymentId, e);
        }
    }

    /**
     * Publish fraud approved event
     *
     * @param paymentId Payment ID that was approved
     * @param userId User ID associated with payment
     * @param riskScore Fraud risk score
     * @param riskLevel Risk level
     */
    @Async
    public void publishFraudApproved(UUID paymentId, String userId, Double riskScore, String riskLevel) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "FRAUD_APPROVED");
            event.put("paymentId", paymentId.toString());
            event.put("userId", userId);
            event.put("riskScore", riskScore);
            event.put("riskLevel", riskLevel);
            event.put("timestamp", LocalDateTime.now().toString());

            kafkaTemplate.send(FRAUD_ALERTS_TOPIC, paymentId.toString(), event);

            log.info("FRAUD EVENT: Published fraud approved event: paymentId={}, userId={}, riskScore={}",
                paymentId, userId, riskScore);

        } catch (Exception e) {
            log.error("FRAUD EVENT: Failed to publish fraud approved event: paymentId={}, userId={}",
                paymentId, userId, e);
        }
    }
}
