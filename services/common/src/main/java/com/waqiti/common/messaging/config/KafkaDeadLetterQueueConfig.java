package com.waqiti.common.messaging.config;

import com.waqiti.common.messaging.dlq.DeadLetterQueueManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * CRITICAL PRODUCTION FIX - KafkaDeadLetterQueueConfig
 * Comprehensive Kafka configuration with Dead Letter Queue support
 * Implements retry policies and error handling to prevent message loss
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaDeadLetterQueueConfig {
    
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;
    
    @Value("${dlq.retry.attempts:3}")
    private int defaultRetryAttempts;
    
    @Value("${dlq.retry.interval:1000}")
    private long defaultRetryInterval;
    
    @Value("${dlq.retry.multiplier:2.0}")
    private double retryMultiplier;
    
    @Value("${dlq.retry.max-interval:30000}")
    private long maxRetryInterval;
    
    @Value("${kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;
    
    @Value("${kafka.consumer.enable-auto-commit:false}")
    private boolean enableAutoCommit;
    
    @Value("${kafka.consumer.max-poll-records:500}")
    private int maxPollRecords;
    
    @Value("${kafka.consumer.session-timeout:30000}")
    private int sessionTimeout;
    
    @Value("${kafka.consumer.heartbeat-interval:3000}")
    private int heartbeatInterval;
    
    /**
     * Producer configuration for DLQ
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Reliability settings
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        
        // Performance settings
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        
        // Compression
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");
        
        // Idempotence
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    /**
     * Consumer configuration for DLQ
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // Consumer settings
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit);
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeout);
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, heartbeatInterval);
        
        // Deserialization settings
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.waqiti.*");
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Object.class);
        configProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        
        // Reliability settings
        configProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        configProps.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 1048576);
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }
    
    /**
     * Kafka template with DLQ support
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        
        // Set default topic if needed
        template.setDefaultTopic("default-topic");
        
        // Enable observations for metrics
        template.setObservationEnabled(true);
        
        return template;
    }
    
    /**
     * Dead Letter Publishing Recoverer
     */
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<String, Object> kafkaTemplate,
            DeadLetterQueueManager dlqManager) {
        
        return new DeadLetterPublishingRecoverer(kafkaTemplate, 
            (record, exception) -> {
                String originalTopic = record.topic();
                String dlqTopic = originalTopic + ".dlq";
                
                log.error("Sending message to DLQ: originalTopic={}, dlqTopic={}, error={}", 
                    originalTopic, dlqTopic, exception.getMessage());
                
                return new org.apache.kafka.common.TopicPartition(dlqTopic, 0);
            });
    }
    
    /**
     * Error handler with retry and DLQ support
     */
    @Bean
    public DefaultErrorHandler errorHandler(DeadLetterPublishingRecoverer deadLetterPublishingRecoverer) {
        
        // Configure exponential backoff for Spring Kafka 3.x
        ExponentialBackOff exponentialBackOff = new ExponentialBackOff(defaultRetryInterval, retryMultiplier);
        exponentialBackOff.setMaxInterval(maxRetryInterval);
        exponentialBackOff.setMaxElapsedTime(defaultRetryAttempts * maxRetryInterval);
        
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(deadLetterPublishingRecoverer, exponentialBackOff);
        
        // Configure retry behavior
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("Retry attempt {} for topic {} partition {} offset {}: {}", 
                deliveryAttempt, record.topic(), record.partition(), record.offset(), ex.getMessage());
        });
        
        // Configure which exceptions should not be retried
        errorHandler.addNotRetryableExceptions(
            IllegalArgumentException.class,
            IllegalStateException.class,
            org.springframework.kafka.support.serializer.DeserializationException.class
        );
        
        return errorHandler;
    }
    
    /**
     * Kafka listener container factory with DLQ support
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler errorHandler) {
        
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        
        // Container properties
        ContainerProperties containerProperties = factory.getContainerProperties();
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProperties.setPollTimeout(3000L);
        containerProperties.setIdleBetweenPolls(1000L);
        
        // Enable observation for metrics
        factory.getContainerProperties().setObservationEnabled(true);
        
        // Set concurrency
        factory.setConcurrency(3);
        
        return factory;
    }
    
    /**
     * DLQ specific listener container factory
     */
    @Bean("dlqKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> dlqKafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory);
        
        // DLQ specific error handler (no retries for DLQ processing)
        FixedBackOff noRetryBackOff = new FixedBackOff(0L, 0L);
        DefaultErrorHandler dlqErrorHandler = new DefaultErrorHandler(noRetryBackOff);
        
        dlqErrorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.error("CRITICAL: DLQ processing failed for topic {} partition {} offset {}: {}", 
                record.topic(), record.partition(), record.offset(), ex.getMessage(), ex);
        });
        
        factory.setCommonErrorHandler(dlqErrorHandler);
        
        // Container properties for DLQ
        ContainerProperties containerProperties = factory.getContainerProperties();
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProperties.setPollTimeout(5000L);
        
        // Lower concurrency for DLQ processing
        factory.setConcurrency(1);
        
        return factory;
    }
}