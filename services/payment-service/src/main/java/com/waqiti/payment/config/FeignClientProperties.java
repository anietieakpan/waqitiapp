package com.waqiti.payment.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive Feign client configuration properties
 * Provides complete configuration for all Feign clients with resilience patterns
 */
@Slf4j
@Data
@Validated
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "feign")
public class FeignClientProperties {
    
    @Valid
    @NotNull
    private ClientConfig client = new ClientConfig();
    
    @Valid
    @NotNull
    private CircuitBreakerConfig circuitbreaker = new CircuitBreakerConfig();
    
    @Valid
    @NotNull
    private CompressionConfig compression = new CompressionConfig();
    
    @Valid
    @NotNull
    private HystrixConfig hystrix = new HystrixConfig();
    
    @Valid
    @NotNull
    private OkHttpConfig okhttp = new OkHttpConfig();
    
    @Valid
    @NotNull
    private HttpClientConfig httpclient = new HttpClientConfig();
    
    /**
     * Main Feign client configuration
     */
    @Data
    public static class ClientConfig {
        @Valid
        @NotNull
        private DefaultConfig defaultConfig = new DefaultConfig();
        
        @Valid
        private Map<String, ServiceConfig> config = new HashMap<>();
        
        @NotNull
        private Boolean defaultToProperties = true;
        
        @NotNull
        private Boolean decodeSlash = true;
        
        @NotNull
        private String defaultRequestHeaders = "";
        
        @NotNull
        private String defaultQueryParameters = "";
        
        /**
         * Default configuration for all Feign clients
         */
        @Data
        public static class DefaultConfig {
            @Min(100)
            @Max(120000)
            private Integer connectTimeout = 10000;
            
            @Min(100)
            @Max(300000)
            private Integer readTimeout = 30000;
            
            @NotNull
            private LoggerLevel loggerLevel = LoggerLevel.BASIC;
            
            @NotNull
            private List<String> requestInterceptors = new ArrayList<>();
            
            @NotNull
            private String errorDecoder = "com.waqiti.payment.client.decoder.CustomErrorDecoder";
            
            @NotNull
            private String retryer = "feign.Retryer.Default";
            
            @NotNull
            private Boolean decode404 = false;
            
            @NotNull
            private String encoder = "feign.jackson.JacksonEncoder";
            
            @NotNull
            private String decoder = "feign.jackson.JacksonDecoder";
            
            @NotNull
            private String contract = "org.springframework.cloud.openfeign.support.SpringMvcContract";
            
            @NotNull
            private List<String> capabilities = Arrays.asList(
                "metrics", "circuitbreaker", "micrometer"
            );
            
            @NotNull
            private Boolean followRedirects = false;
            
            @NotNull
            private Boolean dismissSslValidation = false;
            
            public enum LoggerLevel {
                NONE, BASIC, HEADERS, FULL
            }
            
            public Duration getConnectTimeoutDuration() {
                return Duration.ofMillis(connectTimeout);
            }
            
            public Duration getReadTimeoutDuration() {
                return Duration.ofMillis(readTimeout);
            }
        }
        
        /**
         * Service-specific configuration
         */
        @Data
        public static class ServiceConfig {
            @Min(100)
            @Max(120000)
            private Integer connectTimeout;
            
            @Min(100)
            @Max(300000)
            private Integer readTimeout;
            
            private DefaultConfig.LoggerLevel loggerLevel;
            
            private List<String> requestInterceptors;
            
            private String errorDecoder;
            
            private String retryer;
            
            private Boolean decode404;
            
            private String url;
            
            private String path;
            
            private Boolean primary;
            
            private Map<String, Collection<String>> defaultRequestHeaders;
            
            private Map<String, Collection<String>> defaultQueryParameters;
            
            /**
             * Merge with default configuration
             */
            public void mergeWithDefault(DefaultConfig defaultConfig) {
                if (connectTimeout == null) connectTimeout = defaultConfig.getConnectTimeout();
                if (readTimeout == null) readTimeout = defaultConfig.getReadTimeout();
                if (loggerLevel == null) loggerLevel = defaultConfig.getLoggerLevel();
                if (requestInterceptors == null) requestInterceptors = defaultConfig.getRequestInterceptors();
                if (errorDecoder == null) errorDecoder = defaultConfig.getErrorDecoder();
                if (retryer == null) retryer = defaultConfig.getRetryer();
                if (decode404 == null) decode404 = defaultConfig.getDecode404();
            }
        }
        
        /**
         * Initialize wallet-service configuration
         */
        @PostConstruct
        public void initializeServiceConfigs() {
            // Wallet Service Configuration
            config.computeIfAbsent("wallet-service", k -> {
                ServiceConfig walletConfig = new ServiceConfig();
                walletConfig.setConnectTimeout(5000);
                walletConfig.setReadTimeout(15000);
                walletConfig.setLoggerLevel(DefaultConfig.LoggerLevel.FULL);
                walletConfig.setRequestInterceptors(Arrays.asList(
                    "com.waqiti.payment.client.interceptor.AuthInterceptor",
                    "com.waqiti.payment.client.interceptor.TracingInterceptor"
                ));
                walletConfig.setErrorDecoder("com.waqiti.payment.client.decoder.WalletServiceErrorDecoder");
                return walletConfig;
            });
            
            // Compliance Service Configuration
            config.computeIfAbsent("compliance-service", k -> {
                ServiceConfig complianceConfig = new ServiceConfig();
                complianceConfig.setConnectTimeout(10000);
                complianceConfig.setReadTimeout(30000);
                complianceConfig.setLoggerLevel(DefaultConfig.LoggerLevel.HEADERS);
                complianceConfig.setRequestInterceptors(Arrays.asList(
                    "com.waqiti.payment.client.interceptor.AuthInterceptor",
                    "com.waqiti.payment.client.interceptor.ComplianceInterceptor"
                ));
                complianceConfig.setErrorDecoder("com.waqiti.payment.client.decoder.ComplianceServiceErrorDecoder");
                return complianceConfig;
            });
        }
    }
    
    /**
     * Circuit breaker configuration for Feign clients
     */
    @Data
    public static class CircuitBreakerConfig {
        @NotNull
        private Boolean enabled = true;
        
        @NotNull
        private String alphanumericIds = "true";
        
        @NotNull
        private String groupEnabled = "false";
        
        @Min(10)
        @Max(100)
        private Integer slidingWindowSize = 50;
        
        @Min(1)
        @Max(100)
        private Integer minimumNumberOfCalls = 20;
        
        @Min(1)
        @Max(100)
        private Float failureRateThreshold = 50.0f;
        
        @Min(1)
        @Max(100)
        private Float slowCallRateThreshold = 100.0f;
        
        @Min(100)
        @Max(60000)
        private Integer slowCallDurationThreshold = 1000;
        
        @Min(1000)
        @Max(300000)
        private Integer waitDurationInOpenState = 60000;
        
        @Min(1)
        @Max(100)
        private Integer permittedNumberOfCallsInHalfOpenState = 10;
        
        @NotNull
        private Boolean automaticTransitionFromOpenToHalfOpenEnabled = true;
        
        @NotNull
        private SlidingWindowType slidingWindowType = SlidingWindowType.COUNT_BASED;
        
        @NotNull
        private Boolean recordExceptions = true;
        
        @NotNull
        private List<Class<? extends Throwable>> recordExceptionsList = Arrays.asList(
            Exception.class
        );
        
        @NotNull
        private List<Class<? extends Throwable>> ignoreExceptionsList = new ArrayList<>();
        
        @NotNull
        private Boolean writableStackTraceEnabled = true;
        
        public enum SlidingWindowType {
            COUNT_BASED, TIME_BASED
        }
        
        public Duration getWaitDuration() {
            return Duration.ofMillis(waitDurationInOpenState);
        }
        
        public Duration getSlowCallDuration() {
            return Duration.ofMillis(slowCallDurationThreshold);
        }
    }
    
    /**
     * Compression configuration for Feign clients
     */
    @Data
    public static class CompressionConfig {
        @Valid
        @NotNull
        private RequestCompression request = new RequestCompression();
        
        @Valid
        @NotNull
        private ResponseCompression response = new ResponseCompression();
        
        @Data
        public static class RequestCompression {
            @NotNull
            private Boolean enabled = true;
            
            @NotNull
            private List<String> mimeTypes = Arrays.asList(
                "text/xml", "application/xml", "application/json"
            );
            
            @Min(256)
            @Max(10485760)
            private Integer minRequestSize = 2048;
        }
        
        @Data
        public static class ResponseCompression {
            @NotNull
            private Boolean enabled = true;
            
            @NotNull
            private Boolean useGzipDecoder = true;
        }
    }
    
    /**
     * Hystrix configuration for fallback support
     */
    @Data
    public static class HystrixConfig {
        @NotNull
        private Boolean enabled = false;
        
        @Valid
        private CommandConfig command = new CommandConfig();
        
        @Valid
        private ThreadPoolConfig threadpool = new ThreadPoolConfig();
        
        @Data
        public static class CommandConfig {
            @Valid
            private DefaultCommandConfig defaultConfig = new DefaultCommandConfig();
            
            @Data
            public static class DefaultCommandConfig {
                @Valid
                private CircuitBreakerSettings circuitBreaker = new CircuitBreakerSettings();
                
                @Valid
                private ExecutionSettings execution = new ExecutionSettings();
                
                @Valid
                private FallbackSettings fallback = new FallbackSettings();
                
                @Valid
                private MetricsSettings metrics = new MetricsSettings();
                
                @Valid
                private RequestSettings requestCache = new RequestSettings();
                
                @Data
                public static class CircuitBreakerSettings {
                    @NotNull
                    private Boolean enabled = true;
                    
                    @Min(1)
                    @Max(100)
                    private Integer requestVolumeThreshold = 20;
                    
                    @Min(1000)
                    @Max(60000)
                    private Integer sleepWindowInMilliseconds = 5000;
                    
                    @Min(1)
                    @Max(100)
                    private Integer errorThresholdPercentage = 50;
                    
                    @NotNull
                    private Boolean forceOpen = false;
                    
                    @NotNull
                    private Boolean forceClosed = false;
                }
                
                @Data
                public static class ExecutionSettings {
                    @Valid
                    private IsolationSettings isolation = new IsolationSettings();
                    
                    @Valid
                    private TimeoutSettings timeout = new TimeoutSettings();
                    
                    @Data
                    public static class IsolationSettings {
                        @NotNull
                        private String strategy = "THREAD";
                        
                        @Min(1)
                        @Max(100)
                        private Integer semaphoreMaxConcurrentRequests = 10;
                        
                        @NotNull
                        private Boolean interruptOnTimeout = true;
                        
                        @NotNull
                        private Boolean interruptOnCancel = false;
                    }
                    
                    @Data
                    public static class TimeoutSettings {
                        @NotNull
                        private Boolean enabled = true;
                        
                        @Min(100)
                        @Max(60000)
                        private Integer inMilliseconds = 1000;
                    }
                }
                
                @Data
                public static class FallbackSettings {
                    @NotNull
                    private Boolean enabled = true;
                    
                    @Min(1)
                    @Max(100)
                    private Integer maxConcurrentRequests = 10;
                }
                
                @Data
                public static class MetricsSettings {
                    @Valid
                    private RollingStatsSettings rollingStats = new RollingStatsSettings();
                    
                    @Valid
                    private HealthSnapshotSettings healthSnapshot = new HealthSnapshotSettings();
                    
                    @Data
                    public static class RollingStatsSettings {
                        @Min(1000)
                        @Max(60000)
                        private Integer timeInMilliseconds = 10000;
                        
                        @Min(1)
                        @Max(100)
                        private Integer numBuckets = 10;
                    }
                    
                    @Data
                    public static class HealthSnapshotSettings {
                        @Min(100)
                        @Max(10000)
                        private Integer intervalInMilliseconds = 500;
                    }
                }
                
                @Data
                public static class RequestSettings {
                    @NotNull
                    private Boolean enabled = true;
                }
            }
        }
        
        @Data
        public static class ThreadPoolConfig {
            @Valid
            private DefaultThreadPoolConfig defaultConfig = new DefaultThreadPoolConfig();
            
            @Data
            public static class DefaultThreadPoolConfig {
                @Min(1)
                @Max(100)
                private Integer coreSize = 10;
                
                @Min(1)
                @Max(100)
                private Integer maximumSize = 10;
                
                @Min(-1)
                @Max(100)
                private Integer maxQueueSize = -1;
                
                @Min(1)
                @Max(100)
                private Integer queueSizeRejectionThreshold = 5;
                
                @Min(1)
                @Max(60000)
                private Integer keepAliveTimeMinutes = 1;
                
                @NotNull
                private Boolean allowMaximumSizeToDivergeFromCoreSize = false;
            }
        }
    }
    
    /**
     * OkHttp client configuration
     */
    @Data
    public static class OkHttpConfig {
        @NotNull
        private Boolean enabled = false;
        
        @NotNull
        private Boolean disableSslValidation = false;
        
        @Min(1)
        @Max(1000)
        private Integer maxConnections = 200;
        
        @Min(1)
        @Max(1000)
        private Integer maxConnectionsPerRoute = 50;
        
        @Min(1000)
        @Max(300000)
        private Long timeToLive = 900000L;
        
        @NotNull
        private TimeUnit timeToLiveUnit = TimeUnit.MILLISECONDS;
        
        @NotNull
        private Boolean followRedirects = true;
        
        @NotNull
        private Boolean retryOnConnectionFailure = true;
        
        @Min(1000)
        @Max(60000)
        private Integer connectionTimeout = 10000;
        
        @Min(1000)
        @Max(60000)
        private Integer readTimeout = 30000;
        
        @Min(1000)
        @Max(60000)
        private Integer writeTimeout = 30000;
        
        @NotNull
        private ProtocolVersion protocolVersion = ProtocolVersion.HTTP_2;
        
        public enum ProtocolVersion {
            HTTP_1_0, HTTP_1_1, HTTP_2, HTTP_3
        }
    }
    
    /**
     * Apache HttpClient configuration
     */
    @Data
    public static class HttpClientConfig {
        @NotNull
        private Boolean enabled = true;
        
        @NotNull
        private Boolean disableSslValidation = false;
        
        @Min(1)
        @Max(1000)
        private Integer maxConnections = 200;
        
        @Min(1)
        @Max(1000)
        private Integer maxConnectionsPerRoute = 50;
        
        @Min(1000)
        @Max(300000)
        private Long timeToLive = 900000L;
        
        @NotNull
        private TimeUnit timeToLiveUnit = TimeUnit.MILLISECONDS;
        
        @NotNull
        private Boolean followRedirects = true;
        
        @Min(1000)
        @Max(60000)
        private Integer connectionTimeout = 10000;
        
        @NotNull
        private Boolean connectionTimerRepeat = false;
        
        @Min(1000)
        @Max(60000)
        private Integer socketTimeout = 30000;
        
        @NotNull
        private ConnectionReuseStrategy connectionReuseStrategy = ConnectionReuseStrategy.DEFAULT;
        
        @NotNull
        private Boolean hc5Enabled = false;
        
        public enum ConnectionReuseStrategy {
            DEFAULT, ALWAYS, NEVER
        }
    }
    
    /**
     * Validate configuration on startup
     */
    @PostConstruct
    public void validateConfiguration() {
        log.info("Validating Feign client configuration...");
        
        // Initialize service-specific configurations
        client.initializeServiceConfigs();
        
        // Merge service configs with defaults
        client.getConfig().values().forEach(config -> 
            config.mergeWithDefault(client.getDefaultConfig())
        );
        
        // Validate circuit breaker configuration
        if (circuitbreaker.isEnabled()) {
            if (circuitbreaker.getFailureRateThreshold() < 0 || circuitbreaker.getFailureRateThreshold() > 100) {
                throw new IllegalArgumentException("Failure rate threshold must be between 0 and 100");
            }
            
            if (circuitbreaker.getSlowCallRateThreshold() < 0 || circuitbreaker.getSlowCallRateThreshold() > 100) {
                throw new IllegalArgumentException("Slow call rate threshold must be between 0 and 100");
            }
        }
        
        // Validate compression settings
        if (compression.getRequest().isEnabled() && compression.getRequest().getMinRequestSize() < 256) {
            log.warn("Request compression minimum size is very low: {} bytes", compression.getRequest().getMinRequestSize());
        }
        
        // Validate client configurations
        if (okhttp.isEnabled() && httpclient.isEnabled()) {
            log.warn("Both OkHttp and Apache HttpClient are enabled. Apache HttpClient will be used.");
        }
        
        log.info("Feign client configuration validation completed successfully");
        logConfigurationSummary();
    }
    
    private void logConfigurationSummary() {
        log.info("=== Feign Client Configuration Summary ===");
        log.info("Default: connectTimeout={}ms, readTimeout={}ms, logLevel={}", 
            client.getDefaultConfig().getConnectTimeout(),
            client.getDefaultConfig().getReadTimeout(),
            client.getDefaultConfig().getLoggerLevel());
        log.info("Circuit Breaker: enabled={}, failureRate={}%, slidingWindow={}", 
            circuitbreaker.isEnabled(),
            circuitbreaker.getFailureRateThreshold(),
            circuitbreaker.getSlidingWindowSize());
        log.info("Compression: request={}, response={}, minSize={}", 
            compression.getRequest().isEnabled(),
            compression.getResponse().isEnabled(),
            compression.getRequest().getMinRequestSize());
        log.info("Hystrix: enabled={}", hystrix.isEnabled());
        log.info("HTTP Clients: okhttp={}, httpclient={}", 
            okhttp.isEnabled(), httpclient.isEnabled());
        log.info("Service Configurations: {}", client.getConfig().keySet());
        log.info("==========================================");
    }
}