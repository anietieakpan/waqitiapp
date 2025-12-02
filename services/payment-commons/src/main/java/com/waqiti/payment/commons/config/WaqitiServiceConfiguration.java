package com.waqiti.payment.commons.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuator.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Shared configuration template for all Waqiti microservices
 * Provides standardized configurations for:
 * - Database connections and JPA
 * - Redis caching
 * - Kafka messaging
 * - Resilience patterns (Circuit Breaker, Retry, Rate Limiting)
 * - Security settings
 * - Monitoring and metrics
 * - JSON serialization
 * - HTTP clients
 * - Async processing
 */
@Configuration
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class WaqitiServiceConfiguration implements WebMvcConfigurer {

    /**
     * Standardized ObjectMapper with common settings for all services
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Register JavaTimeModule for Java 8 time support
        mapper.registerModule(new JavaTimeModule());
        
        // Configure serialization settings
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        // Configure deserialization settings
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        mapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
        
        return mapper;
    }

    /**
     * Standardized RestTemplate with resilience and monitoring
     */
    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder, ObjectMapper objectMapper) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .additionalMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    /**
     * Standardized Circuit Breaker configuration
     */
    @Bean
    @ConfigurationProperties(prefix = "waqiti.resilience.circuit-breaker")
    public CircuitBreakerConfig circuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slowCallRateThreshold(50.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Standardized Retry configuration
     */
    @Bean
    @ConfigurationProperties(prefix = "waqiti.resilience.retry")
    public RetryConfig retryConfig() {
        return RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .exponentialBackoffMultiplier(2.0)
                .retryExceptions(
                    RuntimeException.class,
                    org.springframework.web.client.ResourceAccessException.class,
                    org.springframework.dao.TransientDataAccessException.class
                )
                .build();
    }

    /**
     * Standardized Time Limiter configuration
     */
    @Bean
    @ConfigurationProperties(prefix = "waqiti.resilience.time-limiter")
    public TimeLimiterConfig timeLimiterConfig() {
        return TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))
                .cancelRunningFuture(true)
                .build();
    }

    /**
     * Standardized CORS configuration
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("https://*.waqiti.com", "https://localhost:*", "https://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * Standardized async executor configuration
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("waqiti-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Standardized metrics configuration
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags(
                    "application", "waqiti-fintech",
                    "environment", System.getProperty("spring.profiles.active", "development"),
                    "version", getClass().getPackage().getImplementationVersion() != null 
                        ? getClass().getPackage().getImplementationVersion() : "unknown"
                );
    }

    /**
     * Database connection pool configuration
     */
    @Bean
    @ConfigurationProperties(prefix = "waqiti.datasource.hikari")
    public DatabasePoolConfiguration databasePoolConfiguration() {
        return new DatabasePoolConfiguration();
    }

    /**
     * Redis configuration
     */
    @Bean
    @ConfigurationProperties(prefix = "waqiti.redis")
    public RedisConfiguration redisConfiguration() {
        return new RedisConfiguration();
    }

    /**
     * Kafka configuration
     */
    @Bean
    @ConfigurationProperties(prefix = "waqiti.kafka")
    public KafkaConfiguration kafkaConfiguration() {
        return new KafkaConfiguration();
    }

    /**
     * Security configuration
     */
    @Bean
    @ConfigurationProperties(prefix = "waqiti.security")
    public SecurityConfiguration securityConfiguration() {
        return new SecurityConfiguration();
    }

    /**
     * Database pool configuration properties
     */
    public static class DatabasePoolConfiguration {
        private int maximumPoolSize = 20;
        private int minimumIdle = 5;
        private long connectionTimeout = 30000;
        private long idleTimeout = 600000;
        private long maxLifetime = 1800000;
        private long leakDetectionThreshold = 60000;
        private String poolName = "WaqitiCP";
        private boolean autoCommit = false;

        // Getters and setters
        public int getMaximumPoolSize() { return maximumPoolSize; }
        public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }
        
        public int getMinimumIdle() { return minimumIdle; }
        public void setMinimumIdle(int minimumIdle) { this.minimumIdle = minimumIdle; }
        
        public long getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(long connectionTimeout) { this.connectionTimeout = connectionTimeout; }
        
        public long getIdleTimeout() { return idleTimeout; }
        public void setIdleTimeout(long idleTimeout) { this.idleTimeout = idleTimeout; }
        
        public long getMaxLifetime() { return maxLifetime; }
        public void setMaxLifetime(long maxLifetime) { this.maxLifetime = maxLifetime; }
        
        public long getLeakDetectionThreshold() { return leakDetectionThreshold; }
        public void setLeakDetectionThreshold(long leakDetectionThreshold) { this.leakDetectionThreshold = leakDetectionThreshold; }
        
        public String getPoolName() { return poolName; }
        public void setPoolName(String poolName) { this.poolName = poolName; }
        
        public boolean isAutoCommit() { return autoCommit; }
        public void setAutoCommit(boolean autoCommit) { this.autoCommit = autoCommit; }
    }

    /**
     * Redis configuration properties
     */
    public static class RedisConfiguration {
        private String host = "localhost";
        private int port = 6379;
        private String password;
        private int database = 0;
        private Duration timeout = Duration.ofSeconds(5);
        private int maxActive = 20;
        private int maxIdle = 10;
        private int minIdle = 2;
        private Duration maxWait = Duration.ofSeconds(5);

        // Getters and setters
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        
        public int getDatabase() { return database; }
        public void setDatabase(int database) { this.database = database; }
        
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        
        public int getMaxActive() { return maxActive; }
        public void setMaxActive(int maxActive) { this.maxActive = maxActive; }
        
        public int getMaxIdle() { return maxIdle; }
        public void setMaxIdle(int maxIdle) { this.maxIdle = maxIdle; }
        
        public int getMinIdle() { return minIdle; }
        public void setMinIdle(int minIdle) { this.minIdle = minIdle; }
        
        public Duration getMaxWait() { return maxWait; }
        public void setMaxWait(Duration maxWait) { this.maxWait = maxWait; }
    }

    /**
     * Kafka configuration properties
     */
    public static class KafkaConfiguration {
        private String bootstrapServers = "localhost:9092";
        private String acks = "all";
        private int retries = 3;
        private int batchSize = 16384;
        private int lingerMs = 5;
        private long bufferMemory = 33554432;
        private String keySerializer = "org.apache.kafka.common.serialization.StringSerializer";
        private String valueSerializer = "org.springframework.kafka.support.serializer.JsonSerializer";
        private String keyDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";
        private String valueDeserializer = "org.springframework.kafka.support.serializer.JsonDeserializer";
        private String groupId = "waqiti-fintech";
        private String autoOffsetReset = "earliest";
        private boolean enableAutoCommit = false;

        // Getters and setters
        public String getBootstrapServers() { return bootstrapServers; }
        public void setBootstrapServers(String bootstrapServers) { this.bootstrapServers = bootstrapServers; }
        
        public String getAcks() { return acks; }
        public void setAcks(String acks) { this.acks = acks; }
        
        public int getRetries() { return retries; }
        public void setRetries(int retries) { this.retries = retries; }
        
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        
        public int getLingerMs() { return lingerMs; }
        public void setLingerMs(int lingerMs) { this.lingerMs = lingerMs; }
        
        public long getBufferMemory() { return bufferMemory; }
        public void setBufferMemory(long bufferMemory) { this.bufferMemory = bufferMemory; }
        
        public String getKeySerializer() { return keySerializer; }
        public void setKeySerializer(String keySerializer) { this.keySerializer = keySerializer; }
        
        public String getValueSerializer() { return valueSerializer; }
        public void setValueSerializer(String valueSerializer) { this.valueSerializer = valueSerializer; }
        
        public String getKeyDeserializer() { return keyDeserializer; }
        public void setKeyDeserializer(String keyDeserializer) { this.keyDeserializer = keyDeserializer; }
        
        public String getValueDeserializer() { return valueDeserializer; }
        public void setValueDeserializer(String valueDeserializer) { this.valueDeserializer = valueDeserializer; }
        
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
        
        public String getAutoOffsetReset() { return autoOffsetReset; }
        public void setAutoOffsetReset(String autoOffsetReset) { this.autoOffsetReset = autoOffsetReset; }
        
        public boolean isEnableAutoCommit() { return enableAutoCommit; }
        public void setEnableAutoCommit(boolean enableAutoCommit) { this.enableAutoCommit = enableAutoCommit; }
    }

    /**
     * Security configuration properties
     */
    public static class SecurityConfiguration {
        private boolean enableSecurity = true;
        private String jwtSecret = "${JWT_SECRET:?JWT secret key must be provided via JWT_SECRET environment variable}";
        private Duration jwtExpiration = Duration.ofHours(24);
        private boolean enableCsrf = false;
        private boolean enableCors = true;
        private String[] permitAllPatterns = {
            "/actuator/health/**",
            "/actuator/info",
            "/api/v1/auth/**",
            "/swagger-ui/**",
            "/v3/api-docs/**"
        };

        // Getters and setters
        public boolean isEnableSecurity() { return enableSecurity; }
        public void setEnableSecurity(boolean enableSecurity) { this.enableSecurity = enableSecurity; }
        
        public String getJwtSecret() { return jwtSecret; }
        public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
        
        public Duration getJwtExpiration() { return jwtExpiration; }
        public void setJwtExpiration(Duration jwtExpiration) { this.jwtExpiration = jwtExpiration; }
        
        public boolean isEnableCsrf() { return enableCsrf; }
        public void setEnableCsrf(boolean enableCsrf) { this.enableCsrf = enableCsrf; }
        
        public boolean isEnableCors() { return enableCors; }
        public void setEnableCors(boolean enableCors) { this.enableCors = enableCors; }
        
        public String[] getPermitAllPatterns() { return permitAllPatterns; }
        public void setPermitAllPatterns(String[] permitAllPatterns) { this.permitAllPatterns = permitAllPatterns; }
    }
}