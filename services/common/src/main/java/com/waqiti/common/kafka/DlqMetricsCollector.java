package com.waqiti.common.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Metrics collector for Dead Letter Queue operations.
 *
 * Tracks:
 * - DLQ message count per topic
 * - Retry attempts distribution
 * - Poison pill detection
 * - DLQ send failures
 * - Processing latency
 *
 * Metrics are exposed to Prometheus for monitoring and alerting.
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class DlqMetricsCollector {

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Counter> dlqCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> poisonPillCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> dlqFailureCounters = new ConcurrentHashMap<>();

    public DlqMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Record a message sent to DLQ.
     */
    public void recordDlqMessage(String topic, String consumerGroup, int retryCount) {
        String key = topic + ":" + consumerGroup;

        Counter counter = dlqCounters.computeIfAbsent(key, k ->
                Counter.builder("kafka.dlq.messages")
                        .tag("topic", topic)
                        .tag("consumer_group", consumerGroup)
                        .description("Number of messages sent to DLQ")
                        .register(meterRegistry)
        );

        counter.increment();

        // Record retry count distribution
        meterRegistry.counter("kafka.dlq.retry_count",
                "topic", topic,
                "consumer_group", consumerGroup,
                "retry_count", String.valueOf(retryCount)
        ).increment();

        log.debug("Recorded DLQ message | Topic: {} | Consumer Group: {} | Retry Count: {}",
                topic, consumerGroup, retryCount);
    }

    /**
     * Record a poison pill detection.
     */
    public void recordPoisonPill(String topic, String consumerGroup) {
        String key = topic + ":" + consumerGroup;

        Counter counter = poisonPillCounters.computeIfAbsent(key, k ->
                Counter.builder("kafka.dlq.poison_pills")
                        .tag("topic", topic)
                        .tag("consumer_group", consumerGroup)
                        .description("Number of poison pill messages detected")
                        .register(meterRegistry)
        );

        counter.increment();

        log.warn("Recorded poison pill | Topic: {} | Consumer Group: {}", topic, consumerGroup);
    }

    /**
     * Record a DLQ send failure.
     */
    public void recordDlqSendFailure(String topic, String consumerGroup) {
        String key = topic + ":" + consumerGroup;

        Counter counter = dlqFailureCounters.computeIfAbsent(key, k ->
                Counter.builder("kafka.dlq.send_failures")
                        .tag("topic", topic)
                        .tag("consumer_group", consumerGroup)
                        .description("Number of DLQ send failures")
                        .register(meterRegistry)
        );

        counter.increment();

        log.error("Recorded DLQ send failure | Topic: {} | Consumer Group: {}", topic, consumerGroup);
    }

    /**
     * Record processing time for a consumer.
     */
    public void recordProcessingTime(String topic, String consumerGroup, long milliseconds) {
        Timer.builder("kafka.consumer.processing_time")
                .tag("topic", topic)
                .tag("consumer_group", consumerGroup)
                .description("Consumer message processing time")
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(milliseconds));
    }

    /**
     * Record successful message processing.
     */
    public void recordSuccessfulProcessing(String topic, String consumerGroup) {
        meterRegistry.counter("kafka.consumer.messages_processed",
                "topic", topic,
                "consumer_group", consumerGroup,
                "status", "success"
        ).increment();
    }

    /**
     * Record failed message processing.
     */
    public void recordFailedProcessing(String topic, String consumerGroup, String exceptionType) {
        meterRegistry.counter("kafka.consumer.messages_processed",
                "topic", topic,
                "consumer_group", consumerGroup,
                "status", "failure",
                "exception_type", exceptionType
        ).increment();
    }
}
