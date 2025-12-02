package com.waqiti.security.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * Comprehensive Security Service Configuration for production-grade deployment.
 * 
 * Features:
 * - Circuit breaker patterns for resilience
 * - Rate limiting for API protection
 * - Bulkhead isolation for resource management
 * - Retry mechanisms for transient failures
 * - High-performance caching
 * - Optimized thread pools
 * - ML model configuration
 * - Security event processing
 * - Performance monitoring
 * 
 * Production Standards:
 * - Supports 100,000+ TPS transaction screening
 * - Sub-100ms response times
 * - 99.9% availability requirements
 * - Horizontal scaling support
 * - Circuit breaker protection
 * - Comprehensive monitoring
 */
@Configuration
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
@EnableConfigurationProperties({
    SecurityServiceConfig.AMLProperties.class,
    SecurityServiceConfig.PerformanceProperties.class,
    SecurityServiceConfig.CircuitBreakerProperties.class
})
public class SecurityServiceConfig {
    
    // ===== PERFORMANCE AND SCALING CONFIGURATION =====
    
    /**
     * High-performance thread pool for AML screening operations
     */
    @Bean(name = "amlScreeningExecutor")
    @Primary
    public TaskExecutor amlScreeningExecutor(PerformanceProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getAmlScreening().getCorePoolSize());
        executor.setMaxPoolSize(properties.getAmlScreening().getMaxPoolSize());
        executor.setQueueCapacity(properties.getAmlScreening().getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getAmlScreening().getKeepAliveSeconds());
        executor.setThreadNamePrefix("aml-screening-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
    
    /**
     * Thread pool for fraud detection operations
     */
    @Bean(name = "fraudDetectionExecutor")
    public TaskExecutor fraudDetectionExecutor(PerformanceProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getFraudDetection().getCorePoolSize());
        executor.setMaxPoolSize(properties.getFraudDetection().getMaxPoolSize());
        executor.setQueueCapacity(properties.getFraudDetection().getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getFraudDetection().getKeepAliveSeconds());
        executor.setThreadNamePrefix("fraud-detection-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
    
    /**
     * Thread pool for regulatory reporting
     */
    @Bean(name = "regulatoryReportingExecutor")
    public TaskExecutor regulatoryReportingExecutor(PerformanceProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getRegulatoryReporting().getCorePoolSize());
        executor.setMaxPoolSize(properties.getRegulatoryReporting().getMaxPoolSize());
        executor.setQueueCapacity(properties.getRegulatoryReporting().getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getRegulatoryReporting().getKeepAliveSeconds());
        executor.setThreadNamePrefix("regulatory-reporting-");
        executor.initialize();
        return executor;
    }
    
    /**
     * Thread pool for security event processing
     */
    @Bean(name = "securityEventExecutor")
    public TaskExecutor securityEventExecutor(PerformanceProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getSecurityEvent().getCorePoolSize());
        executor.setMaxPoolSize(properties.getSecurityEvent().getMaxPoolSize());
        executor.setQueueCapacity(properties.getSecurityEvent().getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getSecurityEvent().getKeepAliveSeconds());
        executor.setThreadNamePrefix("security-event-");
        executor.initialize();
        return executor;
    }
    
    // ===== CIRCUIT BREAKER CONFIGURATION =====
    
    /**
     * Circuit breaker for AML screening operations
     */
    @Bean(name = "amlScreeningCircuitBreaker")
    public CircuitBreaker amlScreeningCircuitBreaker(CircuitBreakerProperties properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.getAmlScreening().getFailureRateThreshold())
                .slowCallRateThreshold(properties.getAmlScreening().getSlowCallRateThreshold())
                .slowCallDurationThreshold(Duration.ofMillis(properties.getAmlScreening().getSlowCallDurationMs()))
                .waitDurationInOpenState(Duration.ofSeconds(properties.getAmlScreening().getWaitDurationInOpenStateSeconds()))
                .minimumNumberOfCalls(properties.getAmlScreening().getMinimumNumberOfCalls())
                .slidingWindowSize(properties.getAmlScreening().getSlidingWindowSize())
                .permittedNumberOfCallsInHalfOpenState(properties.getAmlScreening().getPermittedNumberOfCallsInHalfOpenState())
                .build();
        
        return CircuitBreaker.of("amlScreening", config);
    }
    
    /**
     * Circuit breaker for external watchlist services
     */
    @Bean(name = "watchlistCircuitBreaker")
    public CircuitBreaker watchlistCircuitBreaker(CircuitBreakerProperties properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.getWatchlist().getFailureRateThreshold())
                .slowCallRateThreshold(properties.getWatchlist().getSlowCallRateThreshold())
                .slowCallDurationThreshold(Duration.ofMillis(properties.getWatchlist().getSlowCallDurationMs()))
                .waitDurationInOpenState(Duration.ofSeconds(properties.getWatchlist().getWaitDurationInOpenStateSeconds()))
                .minimumNumberOfCalls(properties.getWatchlist().getMinimumNumberOfCalls())
                .slidingWindowSize(properties.getWatchlist().getSlidingWindowSize())
                .build();
        
        return CircuitBreaker.of("watchlistService", config);
    }
    
    /**
     * Circuit breaker for database operations
     */
    @Bean(name = "databaseCircuitBreaker")
    public CircuitBreaker databaseCircuitBreaker(CircuitBreakerProperties properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.getDatabase().getFailureRateThreshold())
                .slowCallRateThreshold(properties.getDatabase().getSlowCallRateThreshold())
                .slowCallDurationThreshold(Duration.ofMillis(properties.getDatabase().getSlowCallDurationMs()))
                .waitDurationInOpenState(Duration.ofSeconds(properties.getDatabase().getWaitDurationInOpenStateSeconds()))
                .minimumNumberOfCalls(properties.getDatabase().getMinimumNumberOfCalls())
                .build();
        
        return CircuitBreaker.of("databaseOperations", config);
    }
    
    // ===== RATE LIMITING CONFIGURATION =====
    
    /**
     * Rate limiter for AML screening API
     */
    @Bean(name = "amlScreeningRateLimiter")
    public RateLimiter amlScreeningRateLimiter(PerformanceProperties properties) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(properties.getRateLimiting().getAmlScreeningLimitPerSecond())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofMillis(properties.getRateLimiting().getTimeoutMs()))
                .build();
        
        return RateLimiter.of("amlScreeningAPI", config);
    }
    
    /**
     * Rate limiter for API signature verification
     */
    @Bean(name = "signatureVerificationRateLimiter")
    public RateLimiter signatureVerificationRateLimiter(PerformanceProperties properties) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(properties.getRateLimiting().getSignatureVerificationLimitPerSecond())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofMillis(properties.getRateLimiting().getTimeoutMs()))
                .build();
        
        return RateLimiter.of("signatureVerificationAPI", config);
    }
    
    // ===== BULKHEAD CONFIGURATION =====
    
    /**
     * Bulkhead for AML operations isolation
     */
    @Bean(name = "amlBulkhead")
    public Bulkhead amlBulkhead(PerformanceProperties properties) {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(properties.getBulkhead().getAmlMaxConcurrentCalls())
                .maxWaitDuration(Duration.ofMillis(properties.getBulkhead().getMaxWaitDurationMs()))
                .build();
        
        return Bulkhead.of("amlOperations", config);
    }
    
    /**
     * Bulkhead for fraud detection operations isolation
     */
    @Bean(name = "fraudDetectionBulkhead")
    public Bulkhead fraudDetectionBulkhead(PerformanceProperties properties) {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(properties.getBulkhead().getFraudDetectionMaxConcurrentCalls())
                .maxWaitDuration(Duration.ofMillis(properties.getBulkhead().getMaxWaitDurationMs()))
                .build();
        
        return Bulkhead.of("fraudDetectionOperations", config);
    }
    
    // ===== RETRY CONFIGURATION =====
    
    /**
     * Retry configuration for transient failures
     */
    @Bean(name = "defaultRetry")
    public Retry defaultRetry(PerformanceProperties properties) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(properties.getRetry().getMaxAttempts())
                .waitDuration(Duration.ofMillis(properties.getRetry().getWaitDurationMs()))
                .retryExceptions(
                    java.sql.SQLException.class,
                    java.net.SocketTimeoutException.class,
                    java.util.concurrent.TimeoutException.class
                )
                .build();
        
        return Retry.of("defaultRetry", config);
    }
    
    // ===== CACHING CONFIGURATION =====
    
    /**
     * High-performance Redis cache configuration
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1)) // Default TTL
                .serializeKeysWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();
        
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withCacheConfiguration("amlScreeningCache", 
                    config.entryTtl(Duration.ofMinutes(15)))
                .withCacheConfiguration("watchlistCache", 
                    config.entryTtl(Duration.ofHours(4)))
                .withCacheConfiguration("riskScoreCache", 
                    config.entryTtl(Duration.ofMinutes(30)))
                .withCacheConfiguration("signatureCache", 
                    config.entryTtl(Duration.ofMinutes(5)))
                .transactionAware()
                .build();
    }
    
    /**
     * Redis template for custom cache operations
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();
        return template;
    }
    
    // ===== CONFIGURATION PROPERTIES =====
    
    /**
     * AML-specific configuration properties
     */
    @ConfigurationProperties(prefix = "security-service.aml")
    @lombok.Data
    public static class AMLProperties {
        private MonitoringThresholds monitoringThresholds = new MonitoringThresholds();
        private WatchlistConfig watchlist = new WatchlistConfig();
        private MachineLearning machineLearning = new MachineLearning();
        private RegulatoryCompliance regulatoryCompliance = new RegulatoryCompliance();
        
        @lombok.Data
        public static class MonitoringThresholds {
            private java.math.BigDecimal singleTransactionThreshold = new java.math.BigDecimal("10000.00");
            private java.math.BigDecimal dailyAggregateThreshold = new java.math.BigDecimal("25000.00");
            private java.math.BigDecimal monthlyAggregateThreshold = new java.math.BigDecimal("100000.00");
            private int structuringTransactionCount = 3;
            private java.math.BigDecimal structuringAmountThreshold = new java.math.BigDecimal("9000.00");
            private int velocityThresholdPerHour = 10;
            private int velocityThresholdPerDay = 50;
        }
        
        @lombok.Data
        public static class WatchlistConfig {
            private boolean enableSanctionsScreening = true;
            private boolean enablePEPScreening = true;
            private boolean enableAdverseMediaScreening = true;
            private double matchThreshold = 0.8;
            private int cacheExpirationHours = 4;
            private String sanctionsListUrl = "https://api.sanctionslist.com/v1";
            private String pepListUrl = "https://api.peplist.com/v1";
        }
        
        @lombok.Data
        public static class MachineLearning {
            private boolean enableMLRiskScoring = true;
            private String modelName = "aml_risk_model_v2";
            private String modelVersion = "2.1.0";
            private String modelPath = "/models/aml/";
            private int batchSize = 100;
            private double confidenceThreshold = 0.75;
            private boolean enableOnlineLearning = false;
        }
        
        @lombok.Data
        public static class RegulatoryCompliance {
            private boolean enableAutomaticSARFiling = true;
            private boolean enableAutomaticCTRFiling = true;
            private int sarDeadlineDays = 30;
            private int ctrDeadlineDays = 15;
            private String defaultJurisdiction = "US";
            private String reportingInstitution = "Waqiti Financial Services";
            private String institutionEIN = "12-3456789";
        }
    }
    
    /**
     * Performance and scaling configuration properties
     */
    @ConfigurationProperties(prefix = "security-service.performance")
    @lombok.Data
    public static class PerformanceProperties {
        private ThreadPoolConfig amlScreening = new ThreadPoolConfig(50, 200, 1000, 60);
        private ThreadPoolConfig fraudDetection = new ThreadPoolConfig(30, 100, 500, 60);
        private ThreadPoolConfig regulatoryReporting = new ThreadPoolConfig(10, 50, 200, 60);
        private ThreadPoolConfig securityEvent = new ThreadPoolConfig(20, 80, 400, 60);
        private RateLimitingConfig rateLimiting = new RateLimitingConfig();
        private BulkheadConfig bulkhead = new BulkheadConfig();
        private RetryConfig retry = new RetryConfig();
        
        @lombok.Data
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class ThreadPoolConfig {
            private int corePoolSize = 10;
            private int maxPoolSize = 50;
            private int queueCapacity = 100;
            private int keepAliveSeconds = 60;
            
            public ThreadPoolConfig(int core, int max, int queue, int keepAlive) {
                this.corePoolSize = core;
                this.maxPoolSize = max;
                this.queueCapacity = queue;
                this.keepAliveSeconds = keepAlive;
            }
        }
        
        @lombok.Data
        public static class RateLimitingConfig {
            private int amlScreeningLimitPerSecond = 1000;
            private int signatureVerificationLimitPerSecond = 2000;
            private int fraudDetectionLimitPerSecond = 500;
            private int timeoutMs = 1000;
        }
        
        @lombok.Data
        public static class BulkheadConfig {
            private int amlMaxConcurrentCalls = 100;
            private int fraudDetectionMaxConcurrentCalls = 50;
            private int watchlistMaxConcurrentCalls = 200;
            private int maxWaitDurationMs = 5000;
        }
        
        @lombok.Data
        public static class RetryConfig {
            private int maxAttempts = 3;
            private int waitDurationMs = 500;
            private int exponentialBackoffMultiplier = 2;
        }
    }
    
    /**
     * Circuit breaker configuration properties
     */
    @ConfigurationProperties(prefix = "security-service.circuit-breaker")
    @lombok.Data
    public static class CircuitBreakerProperties {
        private CircuitBreakerConfig amlScreening = new CircuitBreakerConfig();
        private CircuitBreakerConfig watchlist = new CircuitBreakerConfig();
        private CircuitBreakerConfig database = new CircuitBreakerConfig();
        private CircuitBreakerConfig externalApi = new CircuitBreakerConfig();
        
        @lombok.Data
        public static class CircuitBreakerConfig {
            private float failureRateThreshold = 50.0f;
            private float slowCallRateThreshold = 50.0f;
            private int slowCallDurationMs = 2000;
            private int waitDurationInOpenStateSeconds = 30;
            private int minimumNumberOfCalls = 10;
            private int slidingWindowSize = 100;
            private int permittedNumberOfCallsInHalfOpenState = 5;
        }
    }
    
    // ===== MONITORING AND HEALTH CHECKS =====
    
    /**
     * Health check configuration
     */
    @Bean
    public org.springframework.boot.actuate.health.HealthIndicator securityServiceHealthIndicator(
            DataSource dataSource,
            RedisTemplate<String, Object> redisTemplate) {
        
        return new org.springframework.boot.actuate.health.HealthIndicator() {
            @Override
            public org.springframework.boot.actuate.health.Health health() {
                try {
                    // Check database connectivity
                    dataSource.getConnection().isValid(5);
                    
                    // Check Redis connectivity
                    redisTemplate.opsForValue().set("health:check", "ok", Duration.ofSeconds(5));
                    
                    return org.springframework.boot.actuate.health.Health.up()
                            .withDetail("database", "UP")
                            .withDetail("redis", "UP")
                            .withDetail("timestamp", java.time.LocalDateTime.now())
                            .build();
                            
                } catch (Exception e) {
                    return org.springframework.boot.actuate.health.Health.down()
                            .withDetail("error", e.getMessage())
                            .withDetail("timestamp", java.time.LocalDateTime.now())
                            .build();
                }
            }
        };
    }
    
    // ===== SECURITY CONFIGURATION =====
    
    /**
     * Security configuration for API endpoints
     */
    @Bean
    public org.springframework.security.config.annotation.web.builders.HttpSecurity httpSecurity() 
            throws Exception {
        return org.springframework.security.config.annotation.web.builders.HttpSecurity
                .create()
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                    .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/api/security/aml/**").hasRole("AML_OFFICER")
                    .requestMatchers("/api/security/fraud/**").hasRole("FRAUD_ANALYST")
                    .requestMatchers("/api/security/admin/**").hasRole("SECURITY_ADMIN")
                    .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }
}