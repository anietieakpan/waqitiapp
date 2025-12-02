package com.waqiti.common.kafka.schema;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.subject.TopicNameStrategy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Centralized Kafka Schema Registry Configuration
 * 
 * Provides:
 * - Avro schema serialization/deserialization
 * - Schema evolution support
 * - Schema versioning
 * - Compatibility checking
 * - Schema caching
 * 
 * This configuration supports both Avro and JSON serialization strategies
 * with automatic schema registration and validation.
 */
@Slf4j
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "kafka.schema-registry.enabled", havingValue = "true", matchIfMissing = false)
public class SchemaRegistryConfig {

    @Value("${kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${kafka.schema-registry.url:http://localhost:8081}")
    private String schemaRegistryUrl;

    @Value("${kafka.schema-registry.cache-size:100}")
    private int schemaCacheSize;

    @Value("${kafka.consumer.group-id:${spring.application.name}}")
    private String consumerGroupId;

    @Value("${kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    @Value("${kafka.producer.compression-type:snappy}")
    private String compressionType;

    @Value("${kafka.producer.batch-size:16384}")
    private int batchSize;

    @Value("${kafka.producer.linger-ms:5}")
    private int lingerMs;

    @Value("${kafka.security.protocol:PLAINTEXT}")
    private String securityProtocol;

    @Value("${kafka.ssl.truststore.location:}")
    private String truststoreLocation;

    @Value("${kafka.ssl.truststore.password:}")
    private String truststorePassword;

    @Value("${kafka.ssl.keystore.location:}")
    private String keystoreLocation;

    @Value("${kafka.ssl.keystore.password:}")
    private String keystorePassword;

    @Value("${kafka.sasl.mechanism:}")
    private String saslMechanism;

    @Value("${kafka.sasl.jaas.config:}")
    private String saslJaasConfig;

    /**
     * Schema Registry Client Bean
     * Provides centralized schema management
     */
    @Bean
    public SchemaRegistryClient schemaRegistryClient() {
        log.info("Initializing Schema Registry Client with URL: {}", schemaRegistryUrl);
        return new CachedSchemaRegistryClient(schemaRegistryUrl, schemaCacheSize);
    }

    /**
     * Avro Producer Configuration
     * For producing messages with Avro schema
     */
    @Bean
    public ProducerFactory<String, Object> avroProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        
        // Schema Registry configuration
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        props.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, true);
        props.put(AbstractKafkaSchemaSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY, TopicNameStrategy.class.getName());
        
        // Performance tuning
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        props.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // Add security configuration if enabled
        addSecurityConfiguration(props);
        
        log.info("Created Avro producer factory with schema registry at: {}", schemaRegistryUrl);
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Avro Kafka Template
     * For sending messages with Avro schema
     */
    @Bean(name = "avroKafkaTemplate")
    public KafkaTemplate<String, Object> avroKafkaTemplate() {
        return new KafkaTemplate<>(avroProducerFactory());
    }

    /**
     * Avro Consumer Configuration
     * For consuming messages with Avro schema
     */
    @Bean
    public ConsumerFactory<String, Object> avroConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaAvroDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        
        // Schema Registry configuration
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        // PRODUCTION FIX: Use string constant directly (SPECIFIC_AVRO_READER_CONFIG moved/renamed in newer versions)
        props.put("specific.avro.reader", true);
        
        // Performance and reliability
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // Add security configuration if enabled
        addSecurityConfiguration(props);
        
        log.info("Created Avro consumer factory with schema registry at: {}", schemaRegistryUrl);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Avro Listener Container Factory
     * For @KafkaListener with Avro messages
     */
    @Bean(name = "avroKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> avroKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(avroConsumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(new SchemaRegistryErrorHandler());
        return factory;
    }

    /**
     * JSON Producer Configuration (backward compatibility)
     * For producing messages with JSON schema
     */
    @Bean
    public ProducerFactory<String, Object> jsonProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // JSON Schema Registry configuration
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        
        // Performance tuning
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        props.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // Add security configuration if enabled
        addSecurityConfiguration(props);
        
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * JSON Kafka Template (backward compatibility)
     */
    @Bean(name = "jsonKafkaTemplate")
    public KafkaTemplate<String, Object> jsonKafkaTemplate() {
        return new KafkaTemplate<>(jsonProducerFactory());
    }

    /**
     * JSON Consumer Configuration (backward compatibility)
     * For consuming messages with JSON schema
     */
    @Bean
    public ConsumerFactory<String, Object> jsonConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.waqiti.*");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        
        // Performance and reliability
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // Add security configuration if enabled
        addSecurityConfiguration(props);
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * JSON Listener Container Factory (backward compatibility)
     */
    @Bean(name = "jsonKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> jsonKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(jsonConsumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    /**
     * Add security configuration to Kafka properties
     */
    private void addSecurityConfiguration(Map<String, Object> props) {
        if (!"PLAINTEXT".equals(securityProtocol)) {
            props.put("security.protocol", securityProtocol);
            
            // SSL Configuration
            if (securityProtocol.contains("SSL")) {
                if (!truststoreLocation.isEmpty()) {
                    props.put("ssl.truststore.location", truststoreLocation);
                    props.put("ssl.truststore.password", truststorePassword);
                }
                if (!keystoreLocation.isEmpty()) {
                    props.put("ssl.keystore.location", keystoreLocation);
                    props.put("ssl.keystore.password", keystorePassword);
                }
            }
            
            // SASL Configuration
            if (securityProtocol.contains("SASL")) {
                props.put("sasl.mechanism", saslMechanism);
                props.put("sasl.jaas.config", saslJaasConfig);
            }
        }
    }

    /**
     * Schema Registry Health Indicator
     */
    @Bean
    public SchemaRegistryHealthIndicator schemaRegistryHealthIndicator(SchemaRegistryClient client) {
        return new SchemaRegistryHealthIndicator(client, schemaRegistryUrl);
    }

    /**
     * Schema Migration Service
     * Handles schema evolution and compatibility
     */
    @Bean
    public SchemaMigrationService schemaMigrationService(SchemaRegistryClient client) {
        return new SchemaMigrationService(client);
    }

    /**
     * Schema Validation Service
     * Validates messages against registered schemas
     */
    @Bean
    public SchemaValidationService schemaValidationService(SchemaRegistryClient client) {
        return new SchemaValidationService(client);
    }
}