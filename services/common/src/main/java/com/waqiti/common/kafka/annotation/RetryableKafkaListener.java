package com.waqiti.common.kafka.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;

import java.lang.annotation.*;

/**
 * Enhanced Kafka Listener annotation with built-in retry and DLQ support.
 *
 * This is a composite annotation that combines @KafkaListener with automatic
 * retry logic and Dead Letter Queue (DLQ) configuration.
 *
 * Features:
 * - Automatic exponential backoff retry
 * - DLQ topic creation (.dlt suffix)
 * - Error metadata capture
 * - Configurable retry attempts
 * - Non-retryable exception handling
 *
 * Usage Example:
 * <pre>
 * &#64;RetryableKafkaListener(
 *     topics = "payment-events",
 *     groupId = "payment-processor-group",
 *     attempts = 5,
 *     backoffDelay = 1000,
 *     backoffMultiplier = 2.0
 * )
 * public void processPaymentEvent(PaymentEvent event) {
 *     // Process event
 * }
 *
 * &#64;DltHandler
 * public void handleFailedPaymentEvent(PaymentEvent event, Exception exception) {
 *     // Handle permanently failed events
 * }
 * </pre>
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @see org.springframework.kafka.annotation.KafkaListener
 * @see org.springframework.kafka.annotation.DltHandler
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@KafkaListener
public @interface RetryableKafkaListener {

    /**
     * Topics to listen to.
     */
    @AliasFor(annotation = KafkaListener.class, attribute = "topics")
    String[] topics() default {};

    /**
     * Topic pattern to listen to.
     */
    @AliasFor(annotation = KafkaListener.class, attribute = "topicPattern")
    String topicPattern() default "";

    /**
     * Consumer group ID.
     */
    @AliasFor(annotation = KafkaListener.class, attribute = "groupId")
    String groupId() default "";

    /**
     * Unique ID for this listener.
     */
    @AliasFor(annotation = KafkaListener.class, attribute = "id")
    String id() default "";

    /**
     * Container factory bean name.
     */
    @AliasFor(annotation = KafkaListener.class, attribute = "containerFactory")
    String containerFactory() default "kafkaListenerContainerFactory";

    /**
     * Number of retry attempts before sending to DLQ.
     * Default: 5 attempts (initial + 4 retries)
     */
    int attempts() default 5;

    /**
     * Initial backoff delay in milliseconds.
     * Default: 1000ms (1 second)
     */
    long backoffDelay() default 1000L;

    /**
     * Backoff multiplier for exponential backoff.
     * Default: 2.0 (doubles each time)
     *
     * Retry schedule with defaults:
     * - Attempt 1: Immediate
     * - Attempt 2: 1s delay
     * - Attempt 3: 2s delay
     * - Attempt 4: 4s delay
     * - Attempt 5: 8s delay
     * - After 5 attempts: Send to DLQ
     */
    double backoffMultiplier() default 2.0;

    /**
     * Maximum backoff delay in milliseconds.
     * Default: 30000ms (30 seconds)
     */
    long maxBackoffDelay() default 30000L;

    /**
     * DLQ topic suffix.
     * Default: ".dlt" (Dead Letter Topic)
     *
     * Example: If main topic is "payment-events",
     * DLQ topic will be "payment-events.dlt"
     */
    String dlqSuffix() default ".dlt";

    /**
     * Whether to include exception in DLQ message headers.
     * Default: true
     */
    boolean includeExceptionInHeaders() default true;

    /**
     * Auto-create DLQ topic if it doesn't exist.
     * Default: true
     */
    boolean autoCreateDlqTopic() default true;

    /**
     * Concurrency level (number of concurrent consumers).
     * Default: 3
     */
    @AliasFor(annotation = KafkaListener.class, attribute = "concurrency")
    String concurrency() default "3";

    /**
     * Exception types that should NOT be retried (poison pills).
     * These will be sent directly to DLQ on first failure.
     */
    Class<? extends Throwable>[] nonRetryableExceptions() default {
        org.springframework.kafka.support.serializer.DeserializationException.class,
        org.springframework.messaging.converter.MessageConversionException.class,
        IllegalArgumentException.class
    };

    /**
     * Whether to auto-start this listener.
     * Default: true
     */
    @AliasFor(annotation = KafkaListener.class, attribute = "autoStartup")
    String autoStartup() default "true";
}
