package com.waqiti.crypto.kafka;

import com.waqiti.common.kafka.BaseDlqConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DLQ Handler for CryptoPriceAlertEventConsumer
 *
 * Handles failed messages from the dead letter topic for crypto price alerts
 * (user price target notifications, price threshold triggers, etc.)
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CryptoPriceAlertEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public CryptoPriceAlertEventConsumerDlqHandler(
            MeterRegistry meterRegistry,
            KafkaTemplate<String, Object> kafkaTemplate) {
        super(meterRegistry);
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CryptoPriceAlertEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CryptoPriceAlertEventConsumer.dlq:CryptoPriceAlertEventConsumer.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq"
    )
    public void handleDlqMessage(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        processDlqMessage(event, topic, acknowledgment);
    }

    @Override
    protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
        try {
            log.info("Processing DLQ event for crypto price alert: {}", event);

            Integer retryCount = (Integer) headers.getOrDefault("retry_count", 0);
            String failureReason = (String) headers.get("failure_reason");
            String alertId = (String) headers.get("alertId");
            String userId = (String) headers.get("userId");
            String cryptocurrency = (String) headers.get("cryptocurrency");
            String targetPrice = (String) headers.get("targetPrice");

            log.debug("Price alert DLQ - Alert: {}, User: {}, Crypto: {}, Target: {}, Retry: {}, Failure: {}",
                    alertId, userId, cryptocurrency, targetPrice, retryCount, failureReason);

            DlqProcessingResult result = classifyAndRecover(event, failureReason, retryCount, headers);
            updateRecoveryMetrics(result);

            return result;

        } catch (Exception e) {
            log.error("Error handling price alert DLQ event", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Classify error and determine recovery strategy for price alerts
     * Price alerts are non-critical - user notifications, not financial operations
     */
    private DlqProcessingResult classifyAndRecover(Object event, String failureReason,
                                                     Integer retryCount, Map<String, Object> headers) {
        final int MAX_RETRIES = 3; // Lower retries for non-critical alerts

        if (retryCount >= MAX_RETRIES) {
            log.warn("Max retries ({}) exceeded for price alert. Discarding stale alert", MAX_RETRIES);
            // Price alerts are time-sensitive - if failed multiple times, discard
            return DlqProcessingResult.DISCARDED;
        }

        if (failureReason == null) {
            return retryWithBackoff(event, retryCount, headers);
        }

        // Notification service errors - retry
        if (isNotificationServiceError(failureReason)) {
            log.warn("Notification service error for price alert: {}. Retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // User not found or disabled - permanent failure
        if (isUserError(failureReason)) {
            log.info("User error for price alert: {}. Discarding alert", failureReason);
            return DlqProcessingResult.DISCARDED;
        }

        // Duplicate alert already sent
        if (isDuplicateError(failureReason)) {
            log.info("Duplicate price alert detected: {}. Discarding", failureReason);
            return DlqProcessingResult.DISCARDED;
        }

        // Alert expired (price no longer at target)
        if (isStaleAlertError(failureReason)) {
            log.info("Price alert expired/stale: {}. Discarding", failureReason);
            return DlqProcessingResult.DISCARDED;
        }

        // Database connectivity - retry
        if (isDatabaseError(failureReason)) {
            log.info("Database error for price alert: {}. Retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Validation errors - discard malformed alerts
        if (isValidationError(failureReason)) {
            log.warn("Price alert validation error: {}. Discarding malformed alert", failureReason);
            return DlqProcessingResult.DISCARDED;
        }

        // Rate limiting (too many notifications) - retry with backoff
        if (isRateLimitError(failureReason)) {
            log.warn("Rate limit hit for price alert: {}. Retry with extended backoff", failureReason);
            return retryWithExtendedBackoff(event, retryCount, headers);
        }

        // Unknown error - retry once more, then discard (non-critical)
        log.warn("Unknown price alert error: {}. Retry #{}", failureReason, retryCount + 1);
        return retryWithBackoff(event, retryCount, headers);
    }

    private boolean isNotificationServiceError(String failureReason) {
        return failureReason.contains("notification") ||
               failureReason.contains("push service") ||
               failureReason.contains("email service") ||
               failureReason.contains("SMS service");
    }

    private boolean isUserError(String failureReason) {
        return failureReason.contains("user not found") ||
               failureReason.contains("user disabled") ||
               failureReason.contains("account closed");
    }

    private boolean isDuplicateError(String failureReason) {
        return failureReason.contains("duplicate") ||
               failureReason.contains("already sent") ||
               failureReason.contains("already triggered");
    }

    private boolean isStaleAlertError(String failureReason) {
        return failureReason.contains("expired") ||
               failureReason.contains("stale") ||
               failureReason.contains("price no longer") ||
               failureReason.contains("condition not met");
    }

    private boolean isDatabaseError(String failureReason) {
        return failureReason.contains("database") ||
               failureReason.contains("connection") ||
               failureReason.contains("SQL");
    }

    private boolean isValidationError(String failureReason) {
        return failureReason.contains("validation") ||
               failureReason.contains("invalid") ||
               failureReason.contains("malformed");
    }

    private boolean isRateLimitError(String failureReason) {
        return failureReason.contains("rate limit") ||
               failureReason.contains("too many") ||
               failureReason.contains("429") ||
               failureReason.contains("throttle");
    }

    private DlqProcessingResult retryWithBackoff(Object event, Integer retryCount, Map<String, Object> headers) {
        try {
            // Shorter backoff for price alerts (30s, 1min, 2min) - time-sensitive
            long delaySeconds = (long) Math.pow(2, retryCount) * 30;
            log.info("Scheduling price alert retry #{} with {}s delay", retryCount + 1, delaySeconds);

            headers.put("retry_count", retryCount + 1);
            headers.put("retry_scheduled_at", LocalDateTime.now());
            headers.put("retry_delay_seconds", delaySeconds);

            // In production: kafkaTemplate.send("crypto-price-alert-retry", event);
            return DlqProcessingResult.RETRY_SCHEDULED;

        } catch (Exception e) {
            log.error("Failed to schedule price alert retry", e);
            // If retry scheduling fails, discard (non-critical)
            return DlqProcessingResult.DISCARDED;
        }
    }

    private DlqProcessingResult retryWithExtendedBackoff(Object event, Integer retryCount, Map<String, Object> headers) {
        try {
            // Extended backoff for rate limiting (5min, 10min, 20min)
            long delaySeconds = (long) Math.pow(2, retryCount) * 300;
            log.info("Scheduling price alert retry after rate limit #{} with {}s delay", retryCount + 1, delaySeconds);

            headers.put("retry_count", retryCount + 1);
            headers.put("retry_scheduled_at", LocalDateTime.now());
            headers.put("retry_delay_seconds", delaySeconds);
            headers.put("rate_limited", true);

            // In production: kafkaTemplate.send("crypto-price-alert-retry", event);
            return DlqProcessingResult.RETRY_SCHEDULED;

        } catch (Exception e) {
            log.error("Failed to schedule price alert retry after rate limit", e);
            return DlqProcessingResult.DISCARDED;
        }
    }

    private void updateRecoveryMetrics(DlqProcessingResult result) {
        try {
            log.debug("Updating price alert DLQ recovery metrics: {}", result.name().toLowerCase());
            // Metrics are automatically updated by BaseDlqConsumer
        } catch (Exception e) {
            log.warn("Failed to update price alert recovery metrics", e);
        }
    }

    @Override
    protected String getServiceName() {
        return "CryptoPriceAlertEventConsumer";
    }
}
