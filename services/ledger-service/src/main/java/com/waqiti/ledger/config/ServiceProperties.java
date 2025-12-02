package com.waqiti.ledger.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive service integration configuration for microservices communication
 * Provides centralized configuration for all external service connections
 */
@Slf4j
@Data
@Validated
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "services")
public class ServiceProperties {
    
    @Valid
    @NotNull
    private PaymentService paymentService = new PaymentService();
    
    @Valid
    @NotNull
    private TransactionService transactionService = new TransactionService();
    
    @Valid
    @NotNull
    private AuthService authService = new AuthService();
    
    @Valid
    @NotNull
    private NotificationService notificationService = new NotificationService();
    
    @Valid
    @NotNull
    private UserService userService = new UserService();
    
    @Valid
    @NotNull
    private WalletService walletService = new WalletService();
    
    @Valid
    @NotNull
    private KycService kycService = new KycService();
    
    @Valid
    @NotNull
    private FraudService fraudService = new FraudService();
    
    @Valid
    @NotNull
    private CommonConfig commonConfig = new CommonConfig();
    
    /**
     * Base configuration class for all services
     */
    @Data
    public abstract static class BaseServiceConfig {
        @NotBlank
        @Pattern(regexp = "^(http|https)://.*")
        private String url;
        
        @NotBlank
        private String name;
        
        @Min(100)
        @Max(120000)
        private Integer timeout = 30000;
        
        @Min(100)
        @Max(120000)
        private Integer connectTimeout = 10000;
        
        @Min(100)
        @Max(120000)
        private Integer readTimeout = 30000;
        
        @NotNull
        private Boolean enabled = true;
        
        @NotNull
        private RetryConfig retry = new RetryConfig();
        
        @NotNull
        private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
        
        @NotNull
        private HealthCheckConfig healthCheck = new HealthCheckConfig();
        
        @NotNull
        private Map<String, String> defaultHeaders = new HashMap<>();
        
        @NotNull
        private Boolean useLoadBalancer = true;
        
        @NotNull
        private LoadBalancerStrategy loadBalancerStrategy = LoadBalancerStrategy.ROUND_ROBIN;
        
        @Min(1)
        @Max(100)
        private Integer maxConnections = 50;
        
        @Min(1)
        @Max(100)
        private Integer maxConnectionsPerRoute = 20;
        
        @NotNull
        private Boolean keepAlive = true;
        
        @Min(1000)
        @Max(300000)
        private Long keepAliveTimeout = 60000L;
        
        @NotNull
        private Boolean followRedirects = false;
        
        @Min(0)
        @Max(10)
        private Integer maxRedirects = 3;
        
        @NotNull
        private CompressionConfig compression = new CompressionConfig();
        
        @NotNull
        private SecurityConfig security = new SecurityConfig();
        
        @NotNull
        private MetricsConfig metrics = new MetricsConfig();
        
        public enum LoadBalancerStrategy {
            ROUND_ROBIN, RANDOM, WEIGHTED, LEAST_CONNECTIONS, IP_HASH
        }
        
        public URI getUri() {
            try {
                return new URI(url);
            } catch (URISyntaxException e) {
                log.error("Invalid service URL: {}", url, e);
                throw new IllegalArgumentException("Invalid service URL: " + url, e);
            }
        }
        
        public String getHealthCheckUrl() {
            return url + healthCheck.getPath();
        }
        
        public long getTimeoutMillis() {
            return timeout.longValue();
        }
        
        public Duration getTimeoutDuration() {
            return Duration.ofMillis(timeout);
        }
        
        public boolean isHealthy() {
            return enabled && healthCheck.getLastStatus() == HealthStatus.UP;
        }
    }
    
    /**
     * Retry configuration for resilient communication
     */
    @Data
    public static class RetryConfig {
        // SECURITY FIX: Use SecureRandom instead of Math.random()
        private static final SecureRandom secureRandom = new SecureRandom();

        @NotNull
        private Boolean enabled = true;
        
        @Min(0)
        @Max(10)
        private Integer maxAttempts = 3;
        
        @Min(100)
        @Max(60000)
        private Long initialBackoff = 1000L;
        
        @Min(1)
        @Max(10)
        private Double backoffMultiplier = 2.0;
        
        @Min(1000)
        @Max(300000)
        private Long maxBackoff = 30000L;
        
        @NotNull
        private RetryStrategy strategy = RetryStrategy.EXPONENTIAL;
        
        @NotNull
        private Set<Integer> retryableStatusCodes = new HashSet<>(Arrays.asList(
            408, 429, 500, 502, 503, 504
        ));
        
        @NotNull
        private Set<String> retryableExceptions = new HashSet<>(Arrays.asList(
            "java.net.SocketTimeoutException",
            "java.net.ConnectException",
            "java.io.IOException"
        ));
        
        public enum RetryStrategy {
            LINEAR, EXPONENTIAL, FIBONACCI, RANDOM_JITTER
        }
        
        public long calculateBackoff(int attemptNumber) {
            if (!enabled || attemptNumber <= 0) return 0;
            
            long backoff;
            switch (strategy) {
                case LINEAR:
                    backoff = initialBackoff * attemptNumber;
                    break;
                case EXPONENTIAL:
                    backoff = (long) (initialBackoff * Math.pow(backoffMultiplier, attemptNumber - 1));
                    break;
                case FIBONACCI:
                    backoff = calculateFibonacciBackoff(attemptNumber);
                    break;
                case RANDOM_JITTER:
                    backoff = (long) (initialBackoff * Math.pow(backoffMultiplier, attemptNumber - 1));
                    // SECURITY FIX: Use SecureRandom instead of Math.random()
                    backoff += (long) (secureRandom.nextDouble() * 1000);
                    break;
                default:
                    backoff = initialBackoff;
            }
            
            return Math.min(backoff, maxBackoff);
        }
        
        private long calculateFibonacciBackoff(int n) {
            if (n <= 1) return initialBackoff;
            long a = initialBackoff, b = initialBackoff;
            for (int i = 2; i <= n; i++) {
                long temp = a + b;
                a = b;
                b = temp;
            }
            return b;
        }
        
        public boolean shouldRetry(int statusCode) {
            return enabled && retryableStatusCodes.contains(statusCode);
        }
        
        public boolean shouldRetry(Exception exception) {
            if (!enabled) return false;
            return retryableExceptions.stream()
                .anyMatch(ex -> exception.getClass().getName().contains(ex));
        }
    }
    
    /**
     * Circuit breaker configuration for fault tolerance
     */
    @Data
    public static class CircuitBreakerConfig {
        @NotNull
        private Boolean enabled = true;
        
        @Min(1)
        @Max(100)
        private Integer failureThreshold = 5;
        
        @Min(0.1)
        @Max(1.0)
        private Double failureRateThreshold = 0.5;
        
        @Min(1000)
        @Max(300000)
        private Long openTimeout = 60000L;
        
        @Min(1000)
        @Max(60000)
        private Long halfOpenTimeout = 10000L;
        
        @Min(1)
        @Max(1000)
        private Integer slidingWindowSize = 100;
        
        @NotNull
        private WindowType windowType = WindowType.COUNT_BASED;
        
        @Min(1)
        @Max(100)
        private Integer permittedCallsInHalfOpen = 3;
        
        @NotNull
        private Boolean automaticTransition = true;
        
        @NotNull
        private CircuitState currentState = CircuitState.CLOSED;
        
        private Long lastStateTransition = System.currentTimeMillis();
        
        public enum WindowType {
            COUNT_BASED, TIME_BASED
        }
        
        public enum CircuitState {
            CLOSED, OPEN, HALF_OPEN
        }
        
        public boolean isOpen() {
            if (!enabled) return false;
            
            if (currentState == CircuitState.OPEN) {
                long elapsed = System.currentTimeMillis() - lastStateTransition;
                if (automaticTransition && elapsed > openTimeout) {
                    currentState = CircuitState.HALF_OPEN;
                    lastStateTransition = System.currentTimeMillis();
                    return false;
                }
                return true;
            }
            return false;
        }
        
        public void recordSuccess() {
            if (currentState == CircuitState.HALF_OPEN) {
                // Logic to transition back to CLOSED after successful calls
            }
        }
        
        public void recordFailure() {
            // Logic to track failures and potentially open the circuit
        }
    }
    
    /**
     * Health check configuration for service monitoring
     */
    @Data
    public static class HealthCheckConfig {
        @NotNull
        private Boolean enabled = true;
        
        @NotBlank
        private String path = "/actuator/health";
        
        @Min(5000)
        @Max(300000)
        private Long interval = 30000L;
        
        @Min(1000)
        @Max(60000)
        private Long timeout = 5000L;
        
        @Min(1)
        @Max(10)
        private Integer healthyThreshold = 2;
        
        @Min(1)
        @Max(10)
        private Integer unhealthyThreshold = 3;
        
        @NotNull
        private HealthStatus lastStatus = HealthStatus.UNKNOWN;
        
        private Long lastCheckTime;
        
        private String lastCheckMessage;
        
        @Min(0)
        private Integer consecutiveFailures = 0;
        
        @Min(0)
        private Integer consecutiveSuccesses = 0;
        
        public boolean shouldCheck() {
            if (!enabled) return false;
            if (lastCheckTime == null) return true;
            
            long elapsed = System.currentTimeMillis() - lastCheckTime;
            return elapsed >= interval;
        }
        
        public void recordSuccess() {
            consecutiveSuccesses++;
            consecutiveFailures = 0;
            lastCheckTime = System.currentTimeMillis();
            
            if (consecutiveSuccesses >= healthyThreshold) {
                lastStatus = HealthStatus.UP;
            }
        }
        
        public void recordFailure(String message) {
            consecutiveFailures++;
            consecutiveSuccesses = 0;
            lastCheckTime = System.currentTimeMillis();
            lastCheckMessage = message;
            
            if (consecutiveFailures >= unhealthyThreshold) {
                lastStatus = HealthStatus.DOWN;
            }
        }
    }
    
    public enum HealthStatus {
        UP, DOWN, DEGRADED, UNKNOWN
    }
    
    /**
     * Compression configuration for efficient data transfer
     */
    @Data
    public static class CompressionConfig {
        @NotNull
        private Boolean enabled = true;
        
        @NotNull
        private CompressionType requestCompression = CompressionType.GZIP;
        
        @NotNull
        private CompressionType responseCompression = CompressionType.GZIP;
        
        @Min(256)
        @Max(10485760)
        private Integer minCompressSize = 1024;
        
        @NotNull
        private Set<String> compressibleMimeTypes = new HashSet<>(Arrays.asList(
            "application/json", "application/xml", "text/plain", "text/html"
        ));
        
        public enum CompressionType {
            NONE, GZIP, DEFLATE, BROTLI, ZSTD
        }
        
        public boolean shouldCompress(String contentType, int contentLength) {
            if (!enabled) return false;
            if (contentLength < minCompressSize) return false;
            
            return compressibleMimeTypes.stream()
                .anyMatch(type -> contentType.contains(type));
        }
    }
    
    /**
     * Security configuration for service communication
     */
    @Data
    public static class SecurityConfig {
        @NotNull
        private Boolean useTls = true;
        
        @NotNull
        private Boolean verifyHostname = true;
        
        @NotNull
        private Boolean trustAllCertificates = false;
        
        @NotNull
        private AuthType authType = AuthType.OAUTH2;
        
        private String clientId;
        
        private String clientSecret;
        
        private String tokenEndpoint;
        
        private String apiKey;
        
        @NotNull
        private Map<String, String> customHeaders = new HashMap<>();
        
        @Min(60)
        @Max(86400)
        private Integer tokenCacheDuration = 3600;
        
        @NotNull
        private Boolean encryptPayload = false;
        
        @NotNull
        private Boolean signRequests = false;
        
        public enum AuthType {
            NONE, BASIC, BEARER, OAUTH2, API_KEY, MUTUAL_TLS, CUSTOM
        }
        
        public Map<String, String> getAuthHeaders() {
            Map<String, String> headers = new HashMap<>(customHeaders);
            
            switch (authType) {
                case API_KEY:
                    if (apiKey != null) {
                        headers.put("X-API-Key", apiKey);
                    }
                    break;
                case BASIC:
                    if (clientId != null && clientSecret != null) {
                        String auth = clientId + ":" + clientSecret;
                        String encoded = Base64.getEncoder().encodeToString(auth.getBytes());
                        headers.put("Authorization", "Basic " + encoded);
                    }
                    break;
                case BEARER:
                    // Token should be provided dynamically
                    break;
                case OAUTH2:
                    // OAuth2 token should be obtained and cached
                    break;
            }
            
            return headers;
        }
    }
    
    /**
     * Metrics configuration for monitoring
     */
    @Data
    public static class MetricsConfig {
        @NotNull
        private Boolean enabled = true;
        
        @NotNull
        private Boolean recordLatency = true;
        
        @NotNull
        private Boolean recordRequestSize = true;
        
        @NotNull
        private Boolean recordResponseSize = true;
        
        @NotNull
        private Boolean recordStatusCodes = true;
        
        @NotNull
        private Set<Double> latencyPercentiles = new HashSet<>(Arrays.asList(
            0.5, 0.75, 0.90, 0.95, 0.99
        ));
        
        @Min(100)
        @Max(10000)
        private Integer histogramBuckets = 1000;
        
        @NotNull
        private Boolean exportToPrometheus = true;
        
        @NotNull
        private Boolean exportToMicrometer = true;
        
        @NotNull
        private Map<String, String> customTags = new HashMap<>();
        
        public Map<String, String> getAllTags(String serviceName) {
            Map<String, String> tags = new HashMap<>(customTags);
            tags.put("service", serviceName);
            tags.put("enabled", String.valueOf(enabled));
            return tags;
        }
    }
    
    /**
     * Payment Service configuration
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PaymentService extends BaseServiceConfig {
        {
            setName("payment-service");
            setUrl("http://payment-service:8080");
        }
        
        @Min(1)
        @Max(1000000)
        private Integer maxTransactionAmount = 100000;
        
        @NotNull
        private Boolean validateIban = true;
        
        @NotNull
        private Boolean supportInternationalPayments = true;
        
        @NotNull
        private Set<String> supportedPaymentMethods = new HashSet<>(Arrays.asList(
            "CARD", "BANK_TRANSFER", "WALLET", "MOBILE_MONEY"
        ));
        
        @NotNull
        private Map<String, Double> paymentMethodFees = new HashMap<>();
    }
    
    /**
     * Transaction Service configuration
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TransactionService extends BaseServiceConfig {
        {
            setName("transaction-service");
            setUrl("http://transaction-service:8080");
        }
        
        @Min(1)
        @Max(10000)
        private Integer batchSize = 100;
        
        @NotNull
        private Boolean enableIdempotency = true;
        
        @Min(1)
        @Max(365)
        private Integer idempotencyKeyTtlDays = 7;
        
        @NotNull
        private Boolean asyncProcessing = true;
        
        @Min(1)
        @Max(100)
        private Integer maxConcurrentTransactions = 50;
    }
    
    /**
     * Auth Service configuration
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AuthService extends BaseServiceConfig {
        {
            setName("auth-service");
            setUrl("http://auth-service:8080");
        }
        
        @NotBlank
        private String realm = "waqiti";
        
        @NotNull
        private Boolean validateTokens = true;
        
        @Min(60)
        @Max(86400)
        private Integer tokenCacheTtl = 300;
        
        @NotNull
        private Boolean enableSso = true;
        
        @NotNull
        private Set<String> requiredScopes = new HashSet<>(Arrays.asList(
            "ledger:read", "ledger:write"
        ));
    }
    
    /**
     * Notification Service configuration
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class NotificationService extends BaseServiceConfig {
        {
            setName("notification-service");
            setUrl("http://notification-service:8080");
        }
        
        @NotNull
        private Set<String> enabledChannels = new HashSet<>(Arrays.asList(
            "EMAIL", "SMS", "PUSH", "IN_APP"
        ));
        
        @NotNull
        private Boolean enableBatching = true;
        
        @Min(1)
        @Max(1000)
        private Integer batchSize = 100;
        
        @Min(1000)
        @Max(60000)
        private Long batchInterval = 5000L;
        
        @NotNull
        private Map<String, Integer> channelPriorities = Map.of(
            "EMAIL", 1,
            "SMS", 2,
            "PUSH", 3,
            "IN_APP", 4
        );
    }
    
    /**
     * User Service configuration
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UserService extends BaseServiceConfig {
        {
            setName("user-service");
            setUrl("http://user-service:8080");
        }
        
        @NotNull
        private Boolean cacheUserData = true;
        
        @Min(60)
        @Max(3600)
        private Integer userCacheTtl = 600;
        
        @NotNull
        private Boolean validateUserStatus = true;
        
        @NotNull
        private Set<String> requiredUserFields = new HashSet<>(Arrays.asList(
            "id", "email", "status", "kycLevel"
        ));
    }
    
    /**
     * Wallet Service configuration
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class WalletService extends BaseServiceConfig {
        {
            setName("wallet-service");
            setUrl("http://wallet-service:8080");
        }
        
        @NotNull
        private Boolean enableMultiCurrency = true;
        
        @NotNull
        private Set<String> supportedCurrencies = new HashSet<>(Arrays.asList(
            "USD", "EUR", "GBP", "NGN"
        ));
        
        @NotNull
        private Boolean realTimeBalanceUpdate = true;
        
        @Min(0)
        @Max(100)
        private Integer maxWalletsPerUser = 10;
    }
    
    /**
     * KYC Service configuration
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class KycService extends BaseServiceConfig {
        {
            setName("kyc-service");
            setUrl("http://kyc-service:8080");
        }
        
        @NotNull
        private Set<String> requiredDocuments = new HashSet<>(Arrays.asList(
            "ID", "PROOF_OF_ADDRESS"
        ));
        
        @NotNull
        private Boolean autoApprove = false;
        
        @Min(1)
        @Max(90)
        private Integer documentExpiryDays = 30;
        
        @NotNull
        private Map<String, Integer> kycLevelLimits = Map.of(
            "LEVEL_1", 1000,
            "LEVEL_2", 10000,
            "LEVEL_3", 100000
        );
    }
    
    /**
     * Fraud Service configuration
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class FraudService extends BaseServiceConfig {
        {
            setName("fraud-service");
            setUrl("http://fraud-service:8080");
        }
        
        @NotNull
        private Boolean enableRealTimeScoring = true;
        
        @Min(0.0)
        @Max(1.0)
        private Double fraudThreshold = 0.7;
        
        @NotNull
        private Boolean blockHighRiskTransactions = true;
        
        @NotNull
        private Set<String> riskFactors = new HashSet<>(Arrays.asList(
            "IP_REPUTATION", "DEVICE_FINGERPRINT", "BEHAVIORAL_ANALYSIS", "TRANSACTION_PATTERN"
        ));
        
        @NotNull
        private Map<String, Double> riskFactorWeights = Map.of(
            "IP_REPUTATION", 0.25,
            "DEVICE_FINGERPRINT", 0.25,
            "BEHAVIORAL_ANALYSIS", 0.3,
            "TRANSACTION_PATTERN", 0.2
        );
    }
    
    /**
     * Common configuration applicable to all services
     */
    @Data
    public static class CommonConfig {
        @NotNull
        private Boolean useServiceDiscovery = true;
        
        @NotBlank
        private String discoveryServiceUrl = "http://eureka-server:8761";
        
        @NotNull
        private Boolean enableDistributedTracing = true;
        
        @NotBlank
        private String tracingEndpoint = "http://zipkin:9411";
        
        @Min(0.0)
        @Max(1.0)
        private Double tracingSampleRate = 0.1;
        
        @NotNull
        private Boolean enableCentralizedLogging = true;
        
        @NotBlank
        private String loggingEndpoint = "http://logstash:5000";
        
        @NotNull
        private LogLevel defaultLogLevel = LogLevel.INFO;
        
        @NotNull
        private Map<String, String> globalHeaders = Map.of(
            "X-Service-Name", "ledger-service",
            "X-Service-Version", "1.0.0"
        );
        
        public enum LogLevel {
            TRACE, DEBUG, INFO, WARN, ERROR
        }
    }
    
    /**
     * Validate all service configurations on startup
     */
    @PostConstruct
    public void validateServiceConfigurations() {
        log.info("Validating Service configurations...");
        
        List<BaseServiceConfig> services = Arrays.asList(
            paymentService, transactionService, authService,
            notificationService, userService, walletService,
            kycService, fraudService
        );
        
        for (BaseServiceConfig service : services) {
            validateServiceConfig(service);
        }
        
        log.info("Service configuration validation completed successfully");
        logServiceConfigurationSummary();
    }
    
    private void validateServiceConfig(BaseServiceConfig service) {
        // Validate URL format
        try {
            URI uri = new URI(service.getUrl());
            if (uri.getHost() == null) {
                throw new IllegalArgumentException("Invalid host in URL: " + service.getUrl());
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL for service " + service.getName() + ": " + service.getUrl(), e);
        }
        
        // Validate timeouts
        if (service.getConnectTimeout() > service.getTimeout()) {
            log.warn("Connect timeout is greater than total timeout for service: {}", service.getName());
        }
        
        // Validate retry configuration
        if (service.getRetry().getEnabled() && service.getRetry().getMaxAttempts() < 1) {
            throw new IllegalArgumentException("Max retry attempts must be at least 1 for service: " + service.getName());
        }
        
        // Validate circuit breaker
        if (service.getCircuitBreaker().getEnabled() && 
            service.getCircuitBreaker().getFailureThreshold() < 1) {
            throw new IllegalArgumentException("Circuit breaker failure threshold must be at least 1 for service: " + service.getName());
        }
        
        log.debug("Service configuration validated: {}", service.getName());
    }
    
    private void logServiceConfigurationSummary() {
        log.info("=== Service Configuration Summary ===");
        log.info("Payment Service: url={}, timeout={}ms, retry={}", 
            paymentService.getUrl(), paymentService.getTimeout(), paymentService.getRetry().getEnabled());
        log.info("Transaction Service: url={}, timeout={}ms, batch={}", 
            transactionService.getUrl(), transactionService.getTimeout(), transactionService.getBatchSize());
        log.info("Auth Service: url={}, realm={}, sso={}", 
            authService.getUrl(), authService.getRealm(), authService.getEnableSso());
        log.info("Notification Service: url={}, channels={}, batching={}", 
            notificationService.getUrl(), notificationService.getEnabledChannels(), notificationService.getEnableBatching());
        log.info("User Service: url={}, cache={}, ttl={}s", 
            userService.getUrl(), userService.getCacheUserData(), userService.getUserCacheTtl());
        log.info("Wallet Service: url={}, multiCurrency={}, currencies={}", 
            walletService.getUrl(), walletService.getEnableMultiCurrency(), walletService.getSupportedCurrencies().size());
        log.info("KYC Service: url={}, autoApprove={}, levels={}", 
            kycService.getUrl(), kycService.getAutoApprove(), kycService.getKycLevelLimits().size());
        log.info("Fraud Service: url={}, realTime={}, threshold={}", 
            fraudService.getUrl(), fraudService.getEnableRealTimeScoring(), fraudService.getFraudThreshold());
        log.info("Common: discovery={}, tracing={}, logging={}", 
            commonConfig.getUseServiceDiscovery(), commonConfig.getEnableDistributedTracing(), 
            commonConfig.getEnableCentralizedLogging());
        log.info("====================================");
    }
    
    /**
     * Get service configuration by name
     */
    public BaseServiceConfig getServiceByName(String serviceName) {
        switch (serviceName.toLowerCase()) {
            case "payment-service":
                return paymentService;
            case "transaction-service":
                return transactionService;
            case "auth-service":
                return authService;
            case "notification-service":
                return notificationService;
            case "user-service":
                return userService;
            case "wallet-service":
                return walletService;
            case "kyc-service":
                return kycService;
            case "fraud-service":
                return fraudService;
            default:
                throw new IllegalArgumentException("Unknown service: " + serviceName);
        }
    }
}