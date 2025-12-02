package com.waqiti.common.kafka.config;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Schema Registry Configuration
 *
 * Configures Kafka producers and consumers to use Avro serialization with Schema Registry.
 * This ensures type safety and prevents breaking changes to event contracts.
 *
 * Features:
 * - Automatic schema validation on produce
 * - Schema evolution with compatibility checking
 * - Type-safe event handling
 * - Centralized schema management
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-18
 */
@Configuration
public class SchemaRegistryConfiguration {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.schema.registry.url:http://localhost:8081}")
    private String schemaRegistryUrl;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroupId;

    /**
     * Producer factory with Avro serialization
     */
    @Bean
    public ProducerFactory<String, Object> avroProducerFactory() {
        Map<String, Object> config = new HashMap<>();

        // Kafka Configuration
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);

        // Schema Registry Configuration
        config.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        config.put(KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS, false); // Require explicit registration
        config.put(KafkaAvroSerializerConfig.USE_LATEST_VERSION, true);
        config.put(KafkaAvroSerializerConfig.VALUE_SUBJECT_NAME_STRATEGY,
            "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");

        // Producer Reliability
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Performance
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        config.put(ProducerConfig.LINGER_MS_CONFIG, 10);

        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * Kafka template for sending Avro messages
     */
    @Bean
    public KafkaTemplate<String, Object> avroKafkaTemplate() {
        return new KafkaTemplate<>(avroProducerFactory());
    }

    /**
     * Consumer factory with Avro deserialization
     */
    @Bean
    public ConsumerFactory<String, Object> avroConsumerFactory() {
        Map<String, Object> config = new HashMap<>();

        // Kafka Configuration
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);

        // Schema Registry Configuration
        config.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        config.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        config.put(KafkaAvroDeserializerConfig.USE_LATEST_VERSION, true);

        // Consumer Configuration
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);

        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Listener container factory for Avro consumers
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> avroKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(avroConsumerFactory());
        factory.setConcurrency(3); // 3 threads per listener
        factory.getContainerProperties().setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL);

        return factory;
    }
}
