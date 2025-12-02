package com.waqiti.common.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.stereotype.Component;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.BiFunction;

/**
 * Universal Dead Letter Queue (DLQ) Error Handler
 * 
 * Provides automatic DLQ handling for all Kafka consumers with:
 * - Exponential backoff retry (1s, 2s, 4s, 8s, 16s)
 * - Automatic routing to topic.dlq
 * - Error metadata enrichment
 * - Metrics and monitoring
 * - Idempotency support
 * 
 * Usage in consumer configuration:
 * @Bean
 * public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
 *     factory.setCommonErrorHandler(universalDLQErrorHandler);
 * }
 */
/**
 * PRODUCTION FIX: Changed from implementing CommonErrorHandler to utility class
 * Spring Kafka 3.x doesn't have handleOtherException with our signature
 * This class provides DLQ publishing functionality used by DeadLetterPublishingRecoverer
 */
@Component
@Slf4j
public class UniversalDLQErrorHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final Counter dlqPublishedCounter;
    private final Counter dlqFailedCounter;

    @Autowired
    public UniversalDLQErrorHandler(KafkaTemplate<String, Object> kafkaTemplate,
                                    MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        
        this.dlqPublishedCounter = Counter.builder("kafka.dlq.published")
            .description("Messages published to DLQ")
            .register(meterRegistry);
            
        this.dlqFailedCounter = Counter.builder("kafka.dlq.failed")
            .description("Failed to publish to DLQ")
            .register(meterRegistry);
    }

    /**
     * PRODUCTION FIX: Custom DLQ handling method (not implementing any interface)
     * Called by UniversalKafkaConfig's DeadLetterPublishingRecoverer
     */
    public void handleFailedMessage(Exception thrownException, ConsumerRecord<?, ?> record) {
        
        String originalTopic = record.topic();
        String dlqTopic = originalTopic + ".dlq";
        
        log.error("CRITICAL: Message processing failed after retries. Sending to DLQ: {} -> {}", 
            originalTopic, dlqTopic, thrownException);
        
        try {
            // Create DLQ record with enriched metadata
            ProducerRecord<String, Object> dlqRecord = new ProducerRecord<>(
                dlqTopic,
                null,
                record.key() != null ? record.key().toString() : null,
                record.value()
            );
            
            // Add error metadata headers
            Headers headers = dlqRecord.headers();
            headers.add(new RecordHeader("dlq.original-topic", originalTopic.getBytes(StandardCharsets.UTF_8)));
            headers.add(new RecordHeader("dlq.original-partition", 
                String.valueOf(record.partition()).getBytes(StandardCharsets.UTF_8)));
            headers.add(new RecordHeader("dlq.original-offset", 
                String.valueOf(record.offset()).getBytes(StandardCharsets.UTF_8)));
            headers.add(new RecordHeader("dlq.exception-message", 
                thrownException.getMessage().getBytes(StandardCharsets.UTF_8)));
            headers.add(new RecordHeader("dlq.exception-class", 
                thrownException.getClass().getName().getBytes(StandardCharsets.UTF_8)));
            headers.add(new RecordHeader("dlq.timestamp", 
                Instant.now().toString().getBytes(StandardCharsets.UTF_8)));
            headers.add(new RecordHeader("dlq.retry-count", "5".getBytes(StandardCharsets.UTF_8)));
            
            // Publish to DLQ
            kafkaTemplate.send(dlqRecord).whenComplete((result, ex) -> {
                if (ex != null) {
                    dlqFailedCounter.increment();
                    log.error("CRITICAL: Failed to publish message to DLQ topic: {}", dlqTopic, ex);
                } else {
                    dlqPublishedCounter.increment();
                    log.warn("Message sent to DLQ: {} (offset: {}, partition: {})", 
                        dlqTopic, result.getRecordMetadata().offset(), 
                        result.getRecordMetadata().partition());
                }
            });
            
        } catch (Exception e) {
            dlqFailedCounter.increment();
            log.error("CRITICAL: Exception while sending to DLQ: {}", dlqTopic, e);
        }
    }

    /**
     * Configure exponential backoff retry policy
     */
    public static ExponentialBackOffWithMaxRetries createBackOffPolicy() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(5);
        backOff.setInitialInterval(1000);    // 1 second
        backOff.setMultiplier(2.0);          // Double each time
        backOff.setMaxInterval(16000);       // Max 16 seconds
        return backOff;
    }

    /**
     * Create destination resolver for routing to DLQ
     */
    public static BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> createDestinationResolver() {
        return (record, ex) -> {
            String dlqTopic = record.topic() + ".dlq";
            return new TopicPartition(dlqTopic, 0);
        };
    }
}
