package com.waqiti.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.waqiti.common.security.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import com.waqiti.common.service.GeoLocationService;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for audit logging framework
 * Enables comprehensive compliance audit logging
 */
@Slf4j
@Configuration
@EnableAsync
@EnableAspectJAutoProxy
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true", matchIfMissing = true)
public class AuditConfiguration implements WebMvcConfigurer {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String kafkaBootstrapServers;

    @Value("${audit.mongodb.uri:mongodb://localhost:27017/audit}")
    private String mongoUri;

    @Value("${audit.mongodb.database:audit_db}")
    private String mongoDatabase;

    /**
     * MongoDB template for audit event storage
     */
    @Bean
    public MongoTemplate auditMongoTemplate(MongoClient mongoClient) {
        return new MongoTemplate(mongoClient, mongoDatabase);
    }

    /**
     * Kafka producer factory for audit events
     */
    @Bean
    public ProducerFactory<String, AuditEvent> auditProducerFactory() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configs.put(ProducerConfig.ACKS_CONFIG, "all"); // Ensure durability
        configs.put(ProducerConfig.RETRIES_CONFIG, 3);
        configs.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configs.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configs.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        configs.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        
        return new DefaultKafkaProducerFactory<>(configs);
    }

    /**
     * Kafka template for audit events
     */
    @Bean
    public KafkaTemplate<String, AuditEvent> auditKafkaTemplate() {
        return new KafkaTemplate<>(auditProducerFactory());
    }

    /**
     * Audit service bean
     */
    @Bean
    public AuditService auditService(MongoTemplate mongoTemplate,
                                   @SuppressWarnings("rawtypes") KafkaTemplate kafkaTemplate,
                                   SecurityContext securityContext,
                                   ObjectMapper objectMapper,
                                   GeoLocationService geoLocationService,
                                   com.waqiti.common.metrics.MetricsService metricsService,
                                   io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        log.info("Initializing audit service with MongoDB, Kafka, and Metrics");
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> objectKafkaTemplate = kafkaTemplate;
        return new AuditService(mongoTemplate, objectKafkaTemplate, securityContext,
                              objectMapper, geoLocationService, metricsService, meterRegistry);
    }

    /**
     * Audit interceptor for HTTP requests
     */
    @Bean
    public AuditInterceptor auditInterceptor(AuditService auditService, 
                                           ObjectMapper objectMapper) {
        return new AuditInterceptor(auditService, objectMapper);
    }

    /**
     * Audit aspect for method-level auditing
     */
    @Bean
    public AuditAspect auditAspect(AuditService auditService, 
                                  ObjectMapper objectMapper) {
        return new AuditAspect(auditService, objectMapper);
    }

    /**
     * Geo-location service for IP enrichment
     */
    @Bean
    public GeoLocationService geoLocationService() {
        return new GeoLocationService();
    }

    /**
     * Register audit interceptor
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditInterceptor(null, null))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health/**", "/api/actuator/**");
    }

    /**
     * Audit report service for compliance reporting
     * Note: AuditEventRepository is automatically created by Spring Data MongoDB
     */
    @Bean
    public AuditReportService auditReportService(AuditEventRepository repository) {
        return new AuditReportService(repository);
    }

    /**
     * Scheduled audit cleanup service
     */
    @Bean
    @ConditionalOnProperty(name = "audit.cleanup.enabled", havingValue = "true")
    public AuditCleanupService auditCleanupService(AuditEventRepository repository) {
        return new AuditCleanupService(repository);
    }
}