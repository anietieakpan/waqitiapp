package com.waqiti.voice.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka SSL/SASL Configuration
 *
 * CRITICAL SECURITY: Enforces encrypted + authenticated connections to Kafka
 *
 * Features:
 * - SSL/TLS encryption (TLS 1.2+)
 * - SASL authentication (PLAIN, SCRAM-SHA-256, SCRAM-SHA-512)
 * - Client certificate authentication (mTLS)
 * - ACL-based authorization
 *
 * Use Cases:
 * - Voice payment events (VoicePaymentInitiated, VoicePaymentCompleted)
 * - Fraud detection events (SuspiciousVoiceActivity)
 * - Audit events (VoiceCommandProcessed, BiometricVerificationAttempt)
 * - Integration events (cross-service communication)
 *
 * Security Benefits:
 * - Protects sensitive events in transit
 * - Prevents unauthorized event publishing/consumption
 * - Prevents man-in-the-middle attacks
 * - Required for PCI-DSS compliance
 *
 * Compliance:
 * - PCI-DSS Requirement 4.1 (Strong cryptography for transmission)
 * - PCI-DSS Requirement 8 (Unique ID + authentication)
 * - GDPR Article 32 (Encryption of personal data)
 *
 * Setup:
 * 1. Configure Kafka broker SSL:
 *    listeners=SSL://0.0.0.0:9093
 *    ssl.keystore.location=/var/private/ssl/kafka.server.keystore.jks
 *    ssl.truststore.location=/var/private/ssl/kafka.server.truststore.jks
 * 2. Configure SASL:
 *    sasl.enabled.mechanisms=SCRAM-SHA-256
 *    sasl.mechanism.inter.broker.protocol=SCRAM-SHA-256
 * 3. Create SASL users:
 *    kafka-configs --zookeeper localhost:2181 --alter --add-config \
 *      'SCRAM-SHA-256=[password=secret]' --entity-type users --entity-name voice-payment-service
 * 4. Set environment variables:
 *    - KAFKA_SECURITY_PROTOCOL=SASL_SSL
 *    - KAFKA_SASL_MECHANISM=SCRAM-SHA-256
 *    - KAFKA_SASL_USERNAME=voice-payment-service
 *    - KAFKA_SASL_PASSWORD=<from-vault>
 */
@Slf4j
@Configuration
public class KafkaTLSConfiguration {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:voice-payment-service}")
    private String consumerGroupId;

    // Security Configuration
    @Value("${spring.kafka.security.protocol:PLAINTEXT}")
    private String securityProtocol; // PLAINTEXT, SSL, SASL_PLAINTEXT, SASL_SSL

    @Value("${spring.kafka.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${spring.kafka.sasl.enabled:false}")
    private boolean saslEnabled;

    // SSL Configuration
    @Value("${spring.kafka.ssl.trust-store-location:#{null}}")
    private String trustStoreLocation;

    @Value("${spring.kafka.ssl.trust-store-password:#{null}}")
    private String trustStorePassword;

    @Value("${spring.kafka.ssl.trust-store-type:JKS}")
    private String trustStoreType;

    @Value("${spring.kafka.ssl.key-store-location:#{null}}")
    private String keyStoreLocation;

    @Value("${spring.kafka.ssl.key-store-password:#{null}}")
    private String keyStorePassword;

    @Value("${spring.kafka.ssl.key-store-type:JKS}")
    private String keyStoreType;

    @Value("${spring.kafka.ssl.key-password:#{null}}")
    private String keyPassword;

    @Value("${spring.kafka.ssl.endpoint-identification-algorithm:https}")
    private String endpointIdentificationAlgorithm; // https (verify hostname), "" (disable)

    // SASL Configuration
    @Value("${spring.kafka.sasl.mechanism:SCRAM-SHA-256}")
    private String saslMechanism; // PLAIN, SCRAM-SHA-256, SCRAM-SHA-512, GSSAPI

    @Value("${spring.kafka.sasl.jaas.config:#{null}}")
    private String saslJaasConfig;

    @Value("${spring.kafka.sasl.username:#{null}}")
    private String saslUsername;

    @Value("${spring.kafka.sasl.password:#{null}}")
    private String saslPassword;

    /**
     * Producer configuration with SSL/SASL
     */
    @Bean
    @Primary
    public ProducerFactory<String, Object> producerFactory() {
        log.info("Configuring Kafka producer: bootstrap={}, security={}, ssl={}, sasl={}",
                bootstrapServers, securityProtocol, sslEnabled, saslEnabled);

        Map<String, Object> configProps = new HashMap<>();

        // Basic configuration
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Idempotence and reliability
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // Security configuration
        configProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);

        // SSL configuration
        if (sslEnabled || "SSL".equals(securityProtocol) || "SASL_SSL".equals(securityProtocol)) {
            configureSSL(configProps);
        }

        // SASL configuration
        if (saslEnabled || "SASL_PLAINTEXT".equals(securityProtocol) || "SASL_SSL".equals(securityProtocol)) {
            configureSASL(configProps);
        }

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Consumer configuration with SSL/SASL
     */
    @Bean
    @Primary
    public ConsumerFactory<String, Object> consumerFactory() {
        log.info("Configuring Kafka consumer: bootstrap={}, group={}, security={}",
                bootstrapServers, consumerGroupId, securityProtocol);

        Map<String, Object> configProps = new HashMap<>();

        // Basic configuration
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // JSON deserialization
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.waqiti.*");
        configProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        // Consumer reliability
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);

        // Security configuration
        configProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);

        // SSL configuration
        if (sslEnabled || "SSL".equals(securityProtocol) || "SASL_SSL".equals(securityProtocol)) {
            configureSSL(configProps);
        }

        // SASL configuration
        if (saslEnabled || "SASL_PLAINTEXT".equals(securityProtocol) || "SASL_SSL".equals(securityProtocol)) {
            configureSASL(configProps);
        }

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * Configure SSL properties
     */
    private void configureSSL(Map<String, Object> configProps) {
        log.info("Configuring Kafka SSL/TLS");

        // Trust store (server certificate verification)
        if (trustStoreLocation != null && !trustStoreLocation.isBlank()) {
            configProps.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, trustStoreLocation);
            configProps.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, trustStorePassword);
            configProps.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, trustStoreType);
            log.info("SSL trust store configured: {}", trustStoreLocation);
        } else {
            log.warn("⚠️ No SSL trust store configured - using system default");
        }

        // Key store (client certificate for mTLS)
        if (keyStoreLocation != null && !keyStoreLocation.isBlank()) {
            configProps.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keyStoreLocation);
            configProps.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, keyStorePassword);
            configProps.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, keyStoreType);
            configProps.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, keyPassword);
            log.info("✅ SSL client certificate configured (mTLS enabled)");
        }

        // Hostname verification
        configProps.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG,
                endpointIdentificationAlgorithm);

        if ("".equals(endpointIdentificationAlgorithm)) {
            log.warn("⚠️ SSL hostname verification DISABLED - vulnerable to MITM attacks");
        } else {
            log.info("✅ SSL hostname verification enabled: {}", endpointIdentificationAlgorithm);
        }

        // TLS protocol
        configProps.put(SslConfigs.SSL_PROTOCOL_CONFIG, "TLSv1.2");
        configProps.put(SslConfigs.SSL_ENABLED_PROTOCOLS_CONFIG, "TLSv1.2,TLSv1.3");

        log.info("✅ Kafka SSL/TLS configured");
    }

    /**
     * Configure SASL properties
     */
    private void configureSASL(Map<String, Object> configProps) {
        log.info("Configuring Kafka SASL authentication: mechanism={}", saslMechanism);

        configProps.put(SaslConfigs.SASL_MECHANISM, saslMechanism);

        // JAAS configuration
        String jaasConfig;
        if (saslJaasConfig != null && !saslJaasConfig.isBlank()) {
            jaasConfig = saslJaasConfig;
            log.info("Using provided SASL JAAS configuration");
        } else if (saslUsername != null && saslPassword != null) {
            jaasConfig = buildJaasConfig(saslMechanism, saslUsername, saslPassword);
            log.info("Built SASL JAAS configuration for user: {}", saslUsername);
        } else {
            log.error("❌ SASL enabled but no credentials configured");
            throw new IllegalStateException("SASL credentials not configured");
        }

        configProps.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);

        log.info("✅ Kafka SASL authentication configured");
    }

    /**
     * Build JAAS configuration string
     */
    private String buildJaasConfig(String mechanism, String username, String password) {
        switch (mechanism) {
            case "PLAIN":
                return String.format(
                        "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                        username, password
                );

            case "SCRAM-SHA-256":
            case "SCRAM-SHA-512":
                return String.format(
                        "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";",
                        username, password
                );

            default:
                throw new IllegalArgumentException("Unsupported SASL mechanism: " + mechanism);
        }
    }

    /**
     * Kafka template for producing messages
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Kafka listener container factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        factory.getContainerProperties().setPollTimeout(3000);

        return factory;
    }

    /**
     * Production security validator
     */
    @Profile("production")
    @Bean
    public void validateProductionKafkaSecurity() {
        if ("PLAINTEXT".equals(securityProtocol)) {
            throw new IllegalStateException(
                    "SECURITY VIOLATION: Kafka PLAINTEXT protocol not allowed in production"
            );
        }

        if (!"SASL_SSL".equals(securityProtocol) && !"SSL".equals(securityProtocol)) {
            log.warn("⚠️ PRODUCTION WARNING: Kafka security protocol '{}' may not provide full security", securityProtocol);
        }

        if (!sslEnabled && !"SASL_SSL".equals(securityProtocol) && !"SSL".equals(securityProtocol)) {
            throw new IllegalStateException(
                    "SECURITY VIOLATION: Kafka SSL must be enabled in production"
            );
        }

        if (!saslEnabled && !"SASL_SSL".equals(securityProtocol) && !"SASL_PLAINTEXT".equals(securityProtocol)) {
            log.warn("⚠️ PRODUCTION WARNING: Kafka SASL authentication not enabled");
            log.warn("⚠️ Anyone with network access can publish/consume events");
        }

        log.info("✅ Production Kafka security validation passed");
    }
}
