package com.waqiti.common.kafka;

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
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Universal Kafka Configuration with DLQ Support
 * 
 * Automatically applies DLQ error handling to all Kafka consumers
 */
@Configuration
@Slf4j
public class UniversalKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:default-group}")
    private String groupId;

    @Value("${spring.kafka.dlq.enabled:true}")
    private boolean dlqEnabled;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        config.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 40000);
        
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            UniversalDLQErrorHandler dlqErrorHandler) {
        
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        if (dlqEnabled) {
            // Configure exponential backoff
            ExponentialBackOff backOff = new ExponentialBackOff();
            backOff.setInitialInterval(1000L);    // 1 second
            backOff.setMultiplier(2.0);            // Double each time
            backOff.setMaxInterval(16000L);        // Max 16 seconds
            backOff.setMaxElapsedTime(60000L);     // Give up after 1 minute
            
            DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (consumerRecord, exception) -> {
                    // PRODUCTION FIX: Call renamed method
                    dlqErrorHandler.handleFailedMessage(exception, consumerRecord);
                },
                backOff
            );
            
            factory.setCommonErrorHandler(errorHandler);
            
            log.info("Kafka DLQ error handling ENABLED with exponential backoff (1s -> 16s)");
        } else {
            log.warn("Kafka DLQ error handling DISABLED");
        }
        
        return factory;
    }

    /**
     * Factory for critical consumers requiring stricter guarantees
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> criticalKafkaListenerContainerFactory(
            UniversalDLQErrorHandler dlqErrorHandler) {
        
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(1); // Single thread for critical consumers
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // More aggressive retry for critical consumers
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(2000L);     // 2 seconds
        backOff.setMultiplier(3.0);             // Triple each time
        backOff.setMaxInterval(60000L);         // Max 1 minute
        backOff.setMaxElapsedTime(300000L);     // Give up after 5 minutes
        
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            (consumerRecord, exception) -> {
                // PRODUCTION FIX: Call renamed method
                dlqErrorHandler.handleFailedMessage(exception, consumerRecord);
            },
            backOff
        );
        
        factory.setCommonErrorHandler(errorHandler);
        
        log.info("CRITICAL Kafka consumer factory configured with extended retry (2s -> 60s, 5min max)");
        
        return factory;
    }
}
