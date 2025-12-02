package com.waqiti.expense.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Production-ready Kafka error handling with DLQ support
 */
@Configuration
@EnableKafka
@Slf4j
public class KafkaErrorHandlingConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.dlq.enabled:true}")
    private boolean dlqEnabled;

    @Value("${kafka.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${kafka.retry.backoff-interval:1000}")
    private long backoffInterval;

    /**
     * Kafka template for DLQ publishing
     */
    @Bean
    public KafkaTemplate<String, Object> dlqKafkaTemplate() {
        return new KafkaTemplate<>(dlqProducerFactory());
    }

    /**
     * Producer factory for DLQ
     */
    @Bean
    public ProducerFactory<String, Object> dlqProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);

        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Error handler with DLQ support
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> dlqKafkaTemplate) {
        log.info("Configuring Kafka error handler with DLQ support, maxAttempts: {}", maxRetryAttempts);

        // Dead letter publishing recoverer
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dlqKafkaTemplate,
                (record, ex) -> {
                    // Determine DLQ topic name
                    String originalTopic = record.topic();
                    String dlqTopic = originalTopic + ".dlq";

                    log.error("Message processing failed after {} attempts, sending to DLQ: {}. " +
                                    "Original topic: {}, partition: {}, offset: {}, key: {}, error: {}",
                            maxRetryAttempts, dlqTopic, originalTopic,
                            record.partition(), record.offset(), record.key(), ex.getMessage());

                    return new org.apache.kafka.common.TopicPartition(dlqTopic, record.partition());
                });

        // Configure fixed backoff
        FixedBackOff fixedBackOff = new FixedBackOff(backoffInterval, maxRetryAttempts - 1);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, fixedBackOff);

        // Add retryable exceptions
        errorHandler.addRetryableExceptions(
                org.springframework.kafka.KafkaException.class,
                org.springframework.dao.DataAccessException.class
        );

        // Add non-retryable exceptions (send directly to DLQ)
        errorHandler.addNotRetryableExceptions(
                com.waqiti.expense.exception.InvalidExpenseException.class,
                IllegalArgumentException.class,
                com.fasterxml.jackson.core.JsonProcessingException.class
        );

        // Log retry attempts
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("Retry attempt {} for record: topic={}, partition={}, offset={}, key={}, error={}",
                    deliveryAttempt, record.topic(), record.partition(), record.offset(),
                    record.key(), ex.getMessage());
        });

        return errorHandler;
    }

    /**
     * Custom listener container factory with error handling
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            DefaultErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setCommonErrorHandler(errorHandler);

        // Enable batch listening
        factory.setBatchListener(false);

        // Set concurrency
        factory.setConcurrency(3);

        log.info("Kafka listener container factory configured with error handling");

        return factory;
    }

    /**
     * DLQ consumer for monitoring and reprocessing
     */
    // Can add @KafkaListener for DLQ monitoring here
}
