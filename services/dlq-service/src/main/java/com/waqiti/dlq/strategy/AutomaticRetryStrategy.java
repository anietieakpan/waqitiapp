package com.waqiti.dlq.strategy;

import com.waqiti.dlq.model.DLQMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Automatic retry strategy with exponential backoff.
 *
 * This strategy retries messages automatically with increasing delays.
 * Suitable for transient errors (network, timeout, etc.).
 *
 * Backoff schedule:
 * - Retry 1: 30 seconds
 * - Retry 2: 2 minutes
 * - Retry 3: 5 minutes
 * - Retry 4: 15 minutes
 * - Retry 5: 1 hour
 * - Retry 6+: 4 hours
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AutomaticRetryStrategy implements RecoveryStrategyHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int[] BACKOFF_SECONDS = {30, 120, 300, 900, 3600, 14400};
    private static final int MAX_RETRIES = 10;

    @Override
    public RecoveryResult recover(DLQMessage message) {
        log.info("Attempting automatic retry for message: id={}, topic={}, retryCount={}",
                message.getId(), message.getOriginalTopic(), message.getRetryCount());

        try {
            // Check if max retries exceeded
            if (message.getRetryCount() >= MAX_RETRIES) {
                log.warn("Max retries exceeded for message: id={}, retryCount={}",
                        message.getId(), message.getRetryCount());
                return RecoveryResult.permanentFailure(
                    "Max retries (" + MAX_RETRIES + ") exceeded");
            }

            // Retry by republishing to original topic
            kafkaTemplate.send(
                message.getOriginalTopic(),
                message.getMessageKey(),
                message.getMessagePayload()
            ).get(); // Wait for confirmation

            log.info("✅ Message successfully retried: id={}, topic={}",
                    message.getId(), message.getOriginalTopic());

            return RecoveryResult.success("Message successfully republished to original topic");

        } catch (Exception e) {
            log.error("❌ Retry failed for message: id={}, error={}",
                    message.getId(), e.getMessage(), e);

            // Calculate next retry delay
            int retryIndex = Math.min(message.getRetryCount(), BACKOFF_SECONDS.length - 1);
            int nextDelay = BACKOFF_SECONDS[retryIndex];

            return RecoveryResult.retryLater(
                "Retry failed: " + e.getMessage(),
                nextDelay
            );
        }
    }

    @Override
    public String getStrategyName() {
        return "AUTOMATIC_RETRY";
    }

    @Override
    public boolean canHandle(DLQMessage message) {
        // Can handle any transient error
        return message.getRetryCount() < MAX_RETRIES;
    }
}
