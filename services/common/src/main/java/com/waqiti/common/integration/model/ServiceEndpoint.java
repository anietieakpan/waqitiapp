package com.waqiti.common.integration.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive service endpoint configuration with advanced features
 * Includes health monitoring, circuit breaker integration, and dynamic configuration
 */
@Data
@Builder(toBuilder = true)
@Jacksonized
public class ServiceEndpoint {
    
    // Basic endpoint information
    private String serviceName;
    private String baseUrl;
    private String url; // Feign client URL
    private String version;
    private String protocol;
    private int port;
    private String contextPath;

    // Client references
    private Object clientBean; // Feign client bean instance
    private Class<?> clientInterface; // Feign client interface class
    
    // Connection configuration
    private int connectTimeoutMs;
    private int readTimeoutMs;
    private int writeTimeoutMs;
    private int maxConnections;
    private int maxConnectionsPerRoute;
    private boolean keepAlive;
    private Duration keepAliveDuration;
    
    // Retry and resilience configuration
    private int maxRetryAttempts;
    private Duration retryDelay;
    private Duration maxRetryDelay;
    private double retryBackoffMultiplier;
    private List<Integer> retryableStatusCodes;
    private List<Class<? extends Exception>> retryableExceptions;
    
    // Circuit breaker configuration
    private boolean circuitBreakerEnabled;
    private int circuitBreakerFailureThreshold;
    private Duration circuitBreakerTimeout;
    private int circuitBreakerMinRequestsThreshold;
    private double circuitBreakerErrorPercentageThreshold;
    
    // Authentication and security
    private boolean requiresAuth;
    private AuthenticationType authType;
    private Map<String, Object> authConfig;
    private Map<String, String> defaultHeaders;
    private boolean tlsEnabled;
    private String tlsVersion;
    private boolean certificateValidationEnabled;
    private String clientCertificatePath;
    private String clientCertificatePassword;
    
    // Health monitoring
    private boolean healthCheckEnabled;
    private String healthCheckPath;
    private Duration healthCheckInterval;
    private Duration healthCheckTimeout;
    private int healthCheckFailureThreshold;
    private boolean isHealthy;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant lastHealthCheck;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant lastSuccessfulHealthCheck;
    
    private String lastHealthCheckError;
    private int consecutiveHealthCheckFailures;
    
    // Load balancing and discovery
    private LoadBalancingStrategy loadBalancingStrategy;
    private List<String> alternativeEndpoints;
    private boolean serviceDiscoveryEnabled;
    private String serviceDiscoveryNamespace;
    private Map<String, String> serviceDiscoveryTags;
    
    // Rate limiting and throttling
    private boolean rateLimitingEnabled;
    private int requestsPerSecond;
    private int burstCapacity;
    private Duration rateLimitWindow;
    
    // Monitoring and observability
    private boolean metricsEnabled;
    private boolean tracingEnabled;
    private boolean loggingEnabled;
    private String logLevel;
    private List<String> sensitiveHeaders;
    private List<String> sensitiveQueryParams;
    
    // Caching configuration
    private boolean responseCachingEnabled;
    private Duration cacheTtl;
    private List<String> cacheableOperations;
    private Map<String, Duration> operationCacheTtl;
    
    // Service metadata
    private String description;
    private String owner;
    private String team;
    private String environment;
    private Priority priority;
    private ServiceType serviceType;
    private Map<String, Object> customMetadata;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant createdAt;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant updatedAt;
    
    private String createdBy;
    private String updatedBy;
    
    // Runtime statistics
    private final Map<String, Object> runtimeStats = new ConcurrentHashMap<>();
    
    /**
     * Authentication types supported
     */
    public enum AuthenticationType {
        NONE,
        BASIC_AUTH,
        BEARER_TOKEN,
        API_KEY,
        OAUTH2,
        JWT,
        MUTUAL_TLS,
        CUSTOM
    }
    
    /**
     * Load balancing strategies
     */
    public enum LoadBalancingStrategy {
        ROUND_ROBIN,
        WEIGHTED_ROUND_ROBIN,
        LEAST_CONNECTIONS,
        LEAST_RESPONSE_TIME,
        RANDOM,
        STICKY_SESSION,
        HEALTH_AWARE
    }
    
    /**
     * Service priority levels
     */
    public enum Priority {
        LOW(1),
        MEDIUM(2),
        HIGH(3),
        CRITICAL(4);
        
        private final int level;
        
        Priority(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    /**
     * Service types
     */
    public enum ServiceType {
        REST_API,
        SOAP_WEB_SERVICE,
        GRAPHQL,
        GRPC,
        MESSAGE_QUEUE,
        DATABASE,
        CACHE,
        FILE_STORAGE,
        AUTHENTICATION,
        PAYMENT_GATEWAY,
        NOTIFICATION,
        ANALYTICS,
        EXTERNAL_PARTNER
    }
    
    /**
     * Get the complete service URL for a specific path
     */
    public String getCompleteUrl(String path) {
        StringBuilder url = new StringBuilder();
        url.append(protocol).append("://").append(baseUrl);
        
        if (port > 0 && !isDefaultPort()) {
            url.append(":").append(port);
        }
        
        if (contextPath != null && !contextPath.isEmpty()) {
            if (!contextPath.startsWith("/")) {
                url.append("/");
            }
            url.append(contextPath);
        }
        
        if (path != null && !path.isEmpty()) {
            if (!path.startsWith("/") && !url.toString().endsWith("/")) {
                url.append("/");
            }
            url.append(path);
        }
        
        return url.toString();
    }
    
    /**
     * Check if using default port for protocol
     */
    public boolean isDefaultPort() {
        return (protocol.equalsIgnoreCase("http") && port == 80) ||
               (protocol.equalsIgnoreCase("https") && port == 443);
    }
    
    /**
     * Get health check URL
     */
    public String getHealthCheckUrl() {
        return getCompleteUrl(healthCheckPath != null ? healthCheckPath : "/health");
    }
    
    /**
     * Check if endpoint is currently available
     */
    public boolean isAvailable() {
        return isHealthy && !isCircuitBreakerOpen() && !isRateLimited();
    }
    
    /**
     * Check if circuit breaker is open
     */
    public boolean isCircuitBreakerOpen() {
        if (!circuitBreakerEnabled) {
            return false;
        }
        
        // Check if consecutive failures exceed threshold
        return consecutiveHealthCheckFailures >= circuitBreakerFailureThreshold;
    }
    
    /**
     * Check if service is currently rate limited
     */
    public boolean isRateLimited() {
        if (!rateLimitingEnabled) {
            return false;
        }
        
        // Implementation would check current request rate
        Long currentRate = (Long) runtimeStats.get("current_requests_per_second");
        return currentRate != null && currentRate >= requestsPerSecond;
    }
    
    /**
     * Update health status
     */
    public void updateHealthStatus(boolean healthy, String error) {
        this.isHealthy = healthy;
        this.lastHealthCheck = Instant.now();
        
        if (healthy) {
            this.lastSuccessfulHealthCheck = Instant.now();
            this.consecutiveHealthCheckFailures = 0;
            this.lastHealthCheckError = null;
        } else {
            this.consecutiveHealthCheckFailures++;
            this.lastHealthCheckError = error;
        }
        
        this.updatedAt = Instant.now();
    }
    
    /**
     * Record runtime statistics
     */
    public void recordRuntimeStat(String key, Object value) {
        runtimeStats.put(key, value);
    }
    
    /**
     * Get runtime statistic
     */
    public Object getRuntimeStat(String key) {
        return runtimeStats.get(key);
    }
    
    /**
     * Check if service supports operation caching
     */
    public boolean isCacheableOperation(String operation) {
        return responseCachingEnabled && 
               cacheableOperations != null && 
               cacheableOperations.contains(operation);
    }
    
    /**
     * Get cache TTL for specific operation
     */
    public Duration getCacheTtlForOperation(String operation) {
        if (operationCacheTtl != null && operationCacheTtl.containsKey(operation)) {
            return operationCacheTtl.get(operation);
        }
        return cacheTtl;
    }
    
    /**
     * Check if endpoint requires authentication
     */
    public boolean requiresAuthentication() {
        return requiresAuth && authType != AuthenticationType.NONE;
    }
    
    /**
     * Get service weight for load balancing
     */
    public int getServiceWeight() {
        if (!isAvailable()) {
            return 0;
        }
        
        // Base weight on priority and health
        int weight = priority.getLevel() * 25;
        
        // Adjust based on response time
        Double avgResponseTime = (Double) runtimeStats.get("average_response_time_ms");
        if (avgResponseTime != null) {
            // Lower response time = higher weight
            weight += Math.max(0, 100 - avgResponseTime.intValue());
        }
        
        return Math.max(1, weight);
    }
    
    /**
     * Validate endpoint configuration
     */
    public boolean isValidConfiguration() {
        return serviceName != null && !serviceName.trim().isEmpty() &&
               baseUrl != null && !baseUrl.trim().isEmpty() &&
               protocol != null && (protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https")) &&
               port > 0 && port <= 65535 &&
               connectTimeoutMs > 0 && readTimeoutMs > 0;
    }
    
    /**
     * Get endpoint description for logging
     */
    public String getEndpointDescription() {
        return String.format("%s (%s) - %s", serviceName, version, getCompleteUrl(""));
    }
    
    /**
     * Clone endpoint with different base URL (for failover)
     */
    public ServiceEndpoint withAlternativeUrl(String alternativeUrl) {
        return this.toBuilder()
                .baseUrl(alternativeUrl)
                .updatedAt(Instant.now())
                .build();
    }
}