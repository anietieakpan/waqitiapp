package com.waqiti.common.kafka;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.JsonMessageConverter;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Comprehensive Kafka Consumer Configuration
 * Production-ready configuration with error handling, retry logic, and monitoring
 */
@Configuration
@EnableKafka
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerConfiguration {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${kafka.consumer.group-id:waqiti-consumer-group}")
    private String defaultGroupId;

    @Value("${kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    @Value("${kafka.consumer.enable-auto-commit:false}")
    private boolean enableAutoCommit;

    @Value("${kafka.consumer.session-timeout-ms:30000}")
    private int sessionTimeoutMs;

    @Value("${kafka.consumer.heartbeat-interval-ms:3000}")
    private int heartbeatIntervalMs;

    @Value("${kafka.consumer.max-poll-records:100}")
    private int maxPollRecords;

    @Value("${kafka.consumer.max-poll-interval-ms:300000}")
    private int maxPollIntervalMs;

    @Value("${kafka.consumer.request-timeout-ms:40000}")
    private int requestTimeoutMs;

    @Value("${kafka.consumer.retry-attempts:3}")
    private int retryAttempts;

    @Value("${kafka.consumer.retry-delay-ms:1000}")
    private long retryDelayMs;

    @Value("${kafka.consumer.concurrency:3}")
    private int concurrency;

    @Value("${kafka.dlq.enabled:true}")
    private boolean dlqEnabled;

    @Value("${kafka.dlq.suffix:.DLQ}")
    private String dlqSuffix;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaEventTrackingService eventTrackingService;

    /**
     * Primary consumer factory with optimized configuration
     */
    @Bean
    @Primary
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        
        // Basic configuration
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, defaultGroupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit);
        
        // Serialization
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.waqiti.*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.waqiti.common.kafka.GenericKafkaEvent");
        
        // Performance tuning
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeoutMs);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, heartbeatIntervalMs);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        
        // Connection pooling
        props.put(ConsumerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 600000);
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, 1000);
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, 10000);
        
        // Security (if enabled)
        if (isSecurityEnabled()) {
            configureKafkaSecurity(props);
        }

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * High-performance listener container factory
     */
    @Bean
    @Primary
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(concurrency);
        
        // Manual acknowledgment for better control
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        // Enable batch processing for high throughput
        factory.setBatchListener(false); // Individual message processing for precise control
        
        // Error handling with DLQ support
        factory.setCommonErrorHandler(createErrorHandler());

        // PRODUCTION FIX: setValueDeserializer() removed in Spring Kafka 3.x
        // Value deserializer is already configured in consumerFactory() via ConsumerConfig
        // No action needed here - deserializer is set via VALUE_DESERIALIZER_CLASS_CONFIG

        // Consumer record interceptor for monitoring - configure in consumer properties
        Map<String, Object> props = factory.getConsumerFactory().getConfigurationProperties();
        props.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, KafkaConsumerInterceptor.class.getName());
        
        log.info("Kafka listener container factory configured with concurrency: {}, DLQ enabled: {}", 
                concurrency, dlqEnabled);
        
        return factory;
    }

    /**
     * Dedicated consumer factory for DLQ processing
     */
    @Bean
    public ConsumerFactory<String, Object> dlqConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.putAll(consumerFactory().getConfigurationProperties());
        
        // DLQ specific configurations
        props.put(ConsumerConfig.GROUP_ID_CONFIG, defaultGroupId + "-dlq");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10); // Smaller batches for DLQ
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * DLQ listener container factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> dlqListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(dlqConsumerFactory());
        factory.setConcurrency(1); // Single threaded for DLQ processing
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        // More conservative error handling for DLQ
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            (record, exception) -> {
                log.error("DLQ processing failed for record: {}", record, exception);
                eventTrackingService.logDlqProcessingError((ConsumerRecord<String, Object>) record, exception);
            },
            new FixedBackOff(5000L, 2L) // 5 second delay, max 2 retries
        );
        
        factory.setCommonErrorHandler(errorHandler);
        
        return factory;
    }

    /**
     * Batch processing consumer factory for high-volume events
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> batchListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        factory.setBatchListener(true);
        factory.setConcurrency(concurrency * 2); // Higher concurrency for batch processing
        
        // Batch-specific configurations
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.getContainerProperties().setPollTimeout(10000);
        
        return factory;
    }

    /**
     * Circuit breaker registry for consumer resilience
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .slowCallRateThreshold(50.0f)
            .slowCallDurationThreshold(Duration.ofSeconds(5))
            .build();
        
        return CircuitBreakerRegistry.of(config);
    }

    /**
     * Circuit breaker for Kafka consumers
     */
    @Bean
    public CircuitBreaker kafkaConsumerCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("kafka-consumer");
    }

    /**
     * Retry template for consumer operations
     */
    @Bean
    public RetryTemplate kafkaRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Retry policy
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(retryAttempts);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        // Backoff policy
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(retryDelayMs);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        return retryTemplate;
    }

    /**
     * Create comprehensive error handler with DLQ support
     */
    private DefaultErrorHandler createErrorHandler() {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            (record, exception) -> {
                handleDlqMessage((ConsumerRecord<String, Object>) record, exception);
            },
            new FixedBackOff(retryDelayMs, retryAttempts)
        );
        
        // Configure retryable exceptions
        errorHandler.addRetryableExceptions(
            org.springframework.kafka.KafkaException.class,
            org.springframework.dao.TransientDataAccessException.class,
            java.util.concurrent.TimeoutException.class
        );
        
        // Configure non-retryable exceptions
        errorHandler.addNotRetryableExceptions(
            org.springframework.kafka.support.serializer.DeserializationException.class,
            com.fasterxml.jackson.core.JsonParseException.class,
            IllegalArgumentException.class
        );
        
        return errorHandler;
    }

    /**
     * Handle DLQ message processing
     */
    private void handleDlqMessage(ConsumerRecord<String, Object> record, Exception exception) {
        try {
            if (dlqEnabled) {
                String dlqTopic = record.topic() + dlqSuffix;
                
                // Create DLQ event with error context
                DlqEvent dlqEvent = DlqEvent.builder()
                    .originalTopic(record.topic())
                    .dlqTopic(dlqTopic)
                    .partition(record.partition())
                    .offset(record.offset())
                    .key(record.key())
                    .value(record.value())
                    .headers(convertHeaders(record.headers()))
                    .errorMessage(exception.getMessage())
                    .errorType(exception.getClass().getSimpleName())
                    .timestamp(System.currentTimeMillis())
                    .retryCount(getRetryCount(record))
                    .build();
                
                // Send to DLQ topic
                kafkaTemplate.send(dlqTopic, dlqEvent);
                
                // Track in database
                eventTrackingService.logDlqEvent(dlqEvent);
                
                log.error("Message sent to DLQ topic '{}' due to processing failure: {}", 
                         dlqTopic, exception.getMessage());
            } else {
                log.error("DLQ disabled - dropping failed message from topic '{}': {}", 
                         record.topic(), exception.getMessage());
            }
            
            // Track processing error
            eventTrackingService.logProcessingError(record, exception);
            
        } catch (Exception dlqException) {
            log.error("Failed to send message to DLQ for topic '{}': {}", 
                     record.topic(), dlqException.getMessage(), dlqException);
        }
    }

    /**
     * Extract retry count from headers
     */
    private int getRetryCount(ConsumerRecord<String, Object> record) {
        try {
            org.apache.kafka.common.header.Header retryHeader = 
                record.headers().lastHeader("retry-count");
            if (retryHeader != null) {
                return Integer.parseInt(new String(retryHeader.value()));
            }
        } catch (Exception e) {
            log.debug("Could not extract retry count from headers", e);
        }
        return 0;
    }

    /**
     * Convert Kafka Headers to Map<String, String>
     */
    private Map<String, String> convertHeaders(org.apache.kafka.common.header.Headers headers) {
        Map<String, String> headerMap = new HashMap<>();
        if (headers != null) {
            headers.forEach(header -> {
                if (header.value() != null) {
                    headerMap.put(header.key(), new String(header.value()));
                }
            });
        }
        return headerMap;
    }

    /**
     * Check if Kafka security is enabled
     */
    private boolean isSecurityEnabled() {
        // Check for security-related environment variables or properties
        return System.getProperty("kafka.security.protocol") != null ||
               System.getenv("KAFKA_SECURITY_PROTOCOL") != null;
    }

    /**
     * Configure Kafka security settings
     */
    private void configureKafkaSecurity(Map<String, Object> props) {
        String securityProtocol = System.getProperty("kafka.security.protocol", "SASL_SSL");
        props.put("security.protocol", securityProtocol);
        
        if ("SASL_SSL".equals(securityProtocol) || "SASL_PLAINTEXT".equals(securityProtocol)) {
            String saslMechanism = System.getProperty("kafka.sasl.mechanism", "PLAIN");
            props.put("sasl.mechanism", saslMechanism);
            
            String jaasConfig = System.getProperty("kafka.sasl.jaas.config");
            if (jaasConfig != null) {
                props.put("sasl.jaas.config", jaasConfig);
            }
        }
        
        if ("SSL".equals(securityProtocol) || "SASL_SSL".equals(securityProtocol)) {
            String truststoreLocation = System.getProperty("kafka.ssl.truststore.location");
            String truststorePassword = System.getProperty("kafka.ssl.truststore.password");
            
            if (truststoreLocation != null && truststorePassword != null) {
                props.put("ssl.truststore.location", truststoreLocation);
                props.put("ssl.truststore.password", truststorePassword);
            }
        }
        
        log.info("Kafka security configured with protocol: {}", securityProtocol);
    }
}