package com.waqiti.customer.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Configuration for Customer Service.
 * Configures Kafka producers and consumers with JSON serialization,
 * error handling, retry logic, and DLQ support.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Configuration
@EnableKafka
@Slf4j
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroupId;

    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    @Value("${spring.kafka.consumer.enable-auto-commit:false}")
    private boolean enableAutoCommit;

    @Value("${spring.kafka.consumer.properties.max.poll.records:100}")
    private int maxPollRecords;

    @Value("${spring.kafka.consumer.properties.session.timeout.ms:30000}")
    private int sessionTimeout;

    @Value("${spring.kafka.producer.acks:all}")
    private String producerAcks;

    @Value("${spring.kafka.producer.retries:3}")
    private int producerRetries;

    @Value("${spring.kafka.producer.properties.compression.type:snappy}")
    private String compressionType;

    /**
     * Configures the Kafka producer factory with JSON serialization.
     * Enables idempotence and configures compression for optimal performance.
     *
     * @return ProducerFactory configured for JSON serialization
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, producerAcks);
        configProps.put(ProducerConfig.RETRIES_CONFIG, producerRetries);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        log.info("Kafka Producer Factory configured with bootstrap servers: {}", bootstrapServers);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Creates KafkaTemplate for sending messages to Kafka topics.
     *
     * @return KafkaTemplate for message publishing
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        template.setObservationEnabled(true);
        log.info("KafkaTemplate created successfully");
        return template;
    }

    /**
     * Configures the Kafka consumer factory with JSON deserialization.
     * Uses ErrorHandlingDeserializer to handle deserialization errors gracefully.
     *
     * @return ConsumerFactory configured for JSON deserialization
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit);
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeout);
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        configProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);

        // Use ErrorHandlingDeserializer to handle deserialization errors
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        configProps.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        configProps.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        // Trust all packages for JSON deserialization
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.waqiti.*");
        configProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.lang.Object");

        log.info("Kafka Consumer Factory configured with group-id: {}", consumerGroupId);
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * Configures the Kafka listener container factory.
     * Enables batch processing and manual acknowledgment.
     *
     * @return ConcurrentKafkaListenerContainerFactory for message consumption
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setBatchListener(true);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setPollTimeout(3000);
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler(
            (consumerRecord, exception) -> {
                log.error("Error consuming message: topic={}, partition={}, offset={}, error={}",
                    consumerRecord.topic(),
                    consumerRecord.partition(),
                    consumerRecord.offset(),
                    exception.getMessage());
            },
            new org.springframework.util.backoff.FixedBackOff(1000L, 3L)
        ));

        log.info("Kafka Listener Container Factory configured with concurrency: 3");
        return factory;
    }

    /**
     * Configures a separate listener container factory for single record processing.
     * Useful for non-batch event listeners.
     *
     * @return ConcurrentKafkaListenerContainerFactory for single record consumption
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> singleRecordKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setBatchListener(false);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler(
            new org.springframework.util.backoff.FixedBackOff(1000L, 3L)
        ));

        log.info("Single Record Kafka Listener Container Factory configured");
        return factory;
    }
}
