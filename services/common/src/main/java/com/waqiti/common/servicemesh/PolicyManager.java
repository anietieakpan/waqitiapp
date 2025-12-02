package com.waqiti.common.servicemesh;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Policy Manager for rate limiting and access control
 * Manages service mesh policies for security and traffic control
 */
@Slf4j
@Component
@Builder
public class PolicyManager {

    @Value("${policy.manager.name:default-policy-manager}")
    private String name;
    
    @Value("${policy.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;
    
    @Value("${policy.rate-limit.requests:100}")
    private int rateLimitRequests;
    
    @Value("${policy.rate-limit.period-seconds:60}")
    private long rateLimitPeriodSeconds;
    
    @Value("${policy.cors.enabled:true}")
    private boolean corsEnabled;
    
    @Value("${policy.cors.origins:}")
    private List<String> corsOrigins;
    
    @Value("${spring.profiles.active:development}")
    private String activeProfile;
    
    // Policy registry
    private final Map<String, Policy> policies = new ConcurrentHashMap<>();
    private final Map<String, RateLimitPolicy> rateLimitPolicies = new ConcurrentHashMap<>();
    private final Map<String, CorsPolicy> corsPolicies = new ConcurrentHashMap<>();
    
    // Metrics
    private final AtomicLong policyViolations = new AtomicLong(0);
    private final AtomicLong policiesApplied = new AtomicLong(0);
    
    // Thread pools
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2);
    
    // State management
    private volatile ManagerState state = ManagerState.INITIALIZING;

    @PostConstruct
    public void initialize() {
        log.info("Initializing Policy Manager - Rate Limit: {}, CORS: {}", rateLimitEnabled, corsEnabled);
        
        if (rateLimitEnabled) {
            initializeDefaultRateLimitPolicy();
        }
        
        if (corsEnabled) {
            initializeDefaultCorsPolicy();
        }
        
        // Start policy enforcement monitoring
        startPolicyMonitoring();
        
        state = ManagerState.RUNNING;
        log.info("Policy Manager initialized successfully");
    }

    /**
     * Apply rate limiting policy
     */
    public CompletableFuture<PolicyResult> applyRateLimitPolicy(RateLimitRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Applying rate limit policy: {}", request.getPolicyName());
            
            try {
                RateLimitPolicy policy = RateLimitPolicy.builder()
                        .name(request.getPolicyName())
                        .serviceName(request.getServiceName())
                        .requests(request.getRequests())
                        .period(Duration.ofSeconds(rateLimitPeriodSeconds))
                        .burstCapacity(request.getBurstCapacity())
                        .keyExtractor(request.getKeyExtractor())
                        .responseStatusCode(request.getResponseStatusCode())
                        .responseMessage(request.getResponseMessage())
                        .createdAt(LocalDateTime.now())
                        .build();
                
                rateLimitPolicies.put(request.getPolicyName(), policy);
                policiesApplied.incrementAndGet();
                
                return PolicyResult.builder()
                        .success(true)
                        .policyName(request.getPolicyName())
                        .type(PolicyType.RATE_LIMIT)
                        .appliedAt(LocalDateTime.now())
                        .build();
                
            } catch (Exception e) {
                log.error("Failed to apply rate limit policy", e);
                return PolicyResult.builder()
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build();
            }
        });
    }

    /**
     * Apply CORS policy
     */
    public CompletableFuture<PolicyResult> applyCorsPolicy(CorsRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Applying CORS policy: {}", request.getPolicyName());
            
            try {
                CorsPolicy policy = CorsPolicy.builder()
                        .name(request.getPolicyName())
                        .serviceName(request.getServiceName())
                        .allowedOrigins(request.getAllowedOrigins())
                        .allowedMethods(request.getAllowedMethods())
                        .allowedHeaders(request.getAllowedHeaders())
                        .exposedHeaders(request.getExposedHeaders())
                        .allowCredentials(request.isAllowCredentials())
                        .maxAge(request.getMaxAge())
                        .createdAt(LocalDateTime.now())
                        .build();
                
                corsPolicies.put(request.getPolicyName(), policy);
                policiesApplied.incrementAndGet();
                
                return PolicyResult.builder()
                        .success(true)
                        .policyName(request.getPolicyName())
                        .type(PolicyType.CORS)
                        .appliedAt(LocalDateTime.now())
                        .build();
                
            } catch (Exception e) {
                log.error("Failed to apply CORS policy", e);
                return PolicyResult.builder()
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build();
            }
        });
    }

    /**
     * Apply authorization policy
     */
    public CompletableFuture<PolicyResult> applyAuthorizationPolicy(AuthorizationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Applying authorization policy: {}", request.getPolicyName());
            
            try {
                AuthorizationPolicy policy = AuthorizationPolicy.builder()
                        .name(request.getPolicyName())
                        .serviceName(request.getServiceName())
                        .rules(request.getRules())
                        .action(request.getAction())
                        .createdAt(LocalDateTime.now())
                        .build();
                
                policies.put(request.getPolicyName(), policy);
                policiesApplied.incrementAndGet();
                
                return PolicyResult.builder()
                        .success(true)
                        .policyName(request.getPolicyName())
                        .type(PolicyType.AUTHORIZATION)
                        .appliedAt(LocalDateTime.now())
                        .build();
                
            } catch (Exception e) {
                log.error("Failed to apply authorization policy", e);
                return PolicyResult.builder()
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build();
            }
        });
    }

    /**
     * Check if request is rate limited
     */
    public boolean isRateLimited(String serviceName, String key) {
        RateLimitPolicy policy = findRateLimitPolicy(serviceName);
        if (policy == null) {
            return false;
        }
        
        // Simplified rate limit check
        // In production, would use Redis or distributed cache
        return false;
    }

    private void initializeDefaultRateLimitPolicy() {
        RateLimitPolicy defaultPolicy = RateLimitPolicy.builder()
                .name("default-rate-limit")
                .serviceName("*")
                .requests(rateLimitRequests)
                .period(Duration.ofSeconds(rateLimitPeriodSeconds))
                .burstCapacity(rateLimitRequests * 2)
                .keyExtractor("client-ip")
                .responseStatusCode(429)
                .responseMessage("Too Many Requests")
                .createdAt(LocalDateTime.now())
                .build();
        
        rateLimitPolicies.put("default-rate-limit", defaultPolicy);
    }

    private void initializeDefaultCorsPolicy() {
        // Validate CORS configuration for security
        List<String> allowedOrigins = validateAndGetCorsOrigins();
        
        // Validate allowed headers and methods (never use "*" in production)
        List<String> allowedMethods = Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
        List<String> allowedHeaders = Arrays.asList(
            "Content-Type", "Authorization", "X-Requested-With", 
            "X-CSRF-Token", "Accept", "Origin", "X-Request-ID"
        );
        List<String> exposedHeaders = Arrays.asList(
            "X-Total-Count", "X-Request-ID", "X-RateLimit-Remaining",
            "X-RateLimit-Limit", "X-RateLimit-Reset"
        );
        
        CorsPolicy defaultPolicy = CorsPolicy.builder()
                .name("default-cors")
                .serviceName("*")
                .allowedOrigins(allowedOrigins)
                .allowedMethods(allowedMethods)
                .allowedHeaders(allowedHeaders)
                .exposedHeaders(exposedHeaders)
                .allowCredentials(true)
                .maxAge(3600L)
                .createdAt(LocalDateTime.now())
                .build();
        
        corsPolicies.put("default-cors", defaultPolicy);
        
        log.info("CORS Policy initialized - Allowed origins: {}", 
            allowedOrigins.size() <= 5 ? allowedOrigins : allowedOrigins.size() + " origins");
    }
    
    /**
     * Validate and get CORS origins with security enforcement
     * NEVER allows wildcard "*" in production environments
     */
    private List<String> validateAndGetCorsOrigins() {
        // Check if CORS origins are explicitly configured
        if (corsOrigins == null || corsOrigins.isEmpty()) {
            // No origins configured - determine safe defaults based on environment
            if (isProductionEnvironment()) {
                throw new IllegalStateException(
                    "CRITICAL SECURITY ERROR: CORS origins not configured for production environment! " +
                    "Set policy.cors.origins property with specific allowed domains. " +
                    "Example: policy.cors.origins=https://api.example.com,https://api.example.com"
                );
            } else {
                log.warn("⚠️  WARNING: Using permissive CORS policy for development. " +
                        "Configure policy.cors.origins for production!");
                return Arrays.asList("http://localhost:3000", "http://localhost:4200", "http://localhost:8080");
            }
        }
        
        // Check for dangerous wildcard patterns
        for (String origin : corsOrigins) {
            if ("*".equals(origin)) {
                if (isProductionEnvironment()) {
                    throw new IllegalStateException(
                        "CRITICAL SECURITY ERROR: Wildcard '*' CORS origin detected in production! " +
                        "This exposes your API to cross-site request forgery attacks. " +
                        "Configure specific domains in policy.cors.origins property."
                    );
                } else {
                    log.warn("⚠️  SECURITY WARNING: Wildcard '*' CORS origin detected in {} environment. " +
                            "This should NEVER be used in production!", activeProfile);
                }
            }
            
            // Validate origin format
            if (!origin.equals("*") && !origin.startsWith("http://") && !origin.startsWith("https://")) {
                log.warn("⚠️  WARNING: CORS origin '{}' does not start with http:// or https://. " +
                        "This may cause CORS failures.", origin);
            }
        }
        
        return corsOrigins;
    }
    
    private boolean isProductionEnvironment() {
        return activeProfile != null && 
               (activeProfile.contains("prod") || 
                activeProfile.contains("production"));
    }

    private RateLimitPolicy findRateLimitPolicy(String serviceName) {
        // First look for service-specific policy
        RateLimitPolicy policy = rateLimitPolicies.values().stream()
                .filter(p -> p.getServiceName().equals(serviceName))
                .findFirst()
                .orElse(null);
        
        // Fall back to default policy
        if (policy == null) {
            policy = rateLimitPolicies.get("default-rate-limit");
        }
        
        return policy;
    }

    private void startPolicyMonitoring() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                // Monitor policy violations and effectiveness
                log.debug("Policy violations: {}, Policies applied: {}", 
                        policyViolations.get(), policiesApplied.get());
            } catch (Exception e) {
                log.error("Policy monitoring error", e);
            }
        }, 30, 60, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Policy Manager");
        state = ManagerState.SHUTTING_DOWN;
        
        scheduledExecutor.shutdown();
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        state = ManagerState.TERMINATED;
        log.info("Policy Manager shutdown complete");
    }

    // Getters
    public String getName() {
        return name;
    }

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public int getRateLimitRequests() {
        return rateLimitRequests;
    }

    public long getRateLimitPeriodSeconds() {
        return rateLimitPeriodSeconds;
    }

    public boolean isCorsEnabled() {
        return corsEnabled;
    }

    public List<String> getCorsOrigins() {
        return corsOrigins;
    }

    public Map<String, Policy> getPolicies() {
        return new HashMap<>(policies);
    }

    public Map<String, RateLimitPolicy> getRateLimitPolicies() {
        return new HashMap<>(rateLimitPolicies);
    }

    public Map<String, CorsPolicy> getCorsPolicies() {
        return new HashMap<>(corsPolicies);
    }

    public long getPolicyViolations() {
        return policyViolations.get();
    }

    public long getPoliciesApplied() {
        return policiesApplied.get();
    }

    public ManagerState getState() {
        return state;
    }

    // Inner classes

    public enum ManagerState {
        INITIALIZING, RUNNING, SHUTTING_DOWN, TERMINATED
    }

    /**
     * Base Policy interface
     */
    public interface Policy {
        String getName();
        String getServiceName();
        LocalDateTime getCreatedAt();
        PolicyType getType();
    }

    /**
     * Policy types enumeration
     */
    public enum PolicyType {
        RATE_LIMIT, CORS, AUTHORIZATION, SECURITY, CIRCUIT_BREAKER
    }

    /**
     * Rate limiting policy configuration
     */
    @lombok.Data
    @Builder
    public static class RateLimitPolicy implements Policy {
        private final String name;
        private final String serviceName;
        private final int requests;
        private final Duration period;
        private final int burstCapacity;
        private final String keyExtractor;
        private final int responseStatusCode;
        private final String responseMessage;
        private final LocalDateTime createdAt;
        
        @Override
        public PolicyType getType() {
            return PolicyType.RATE_LIMIT;
        }
    }

    /**
     * CORS policy configuration
     */
    @lombok.Data
    @Builder
    public static class CorsPolicy implements Policy {
        private final String name;
        private final String serviceName;
        private final List<String> allowedOrigins;
        private final List<String> allowedMethods;
        private final List<String> allowedHeaders;
        private final List<String> exposedHeaders;
        private final boolean allowCredentials;
        private final Long maxAge;
        private final LocalDateTime createdAt;
        
        @Override
        public PolicyType getType() {
            return PolicyType.CORS;
        }
    }

    /**
     * Authorization policy configuration
     */
    @lombok.Data
    @Builder
    public static class AuthorizationPolicy implements Policy {
        private final String name;
        private final String serviceName;
        private final List<String> rules;
        private final String action;
        private final LocalDateTime createdAt;
        
        @Override
        public PolicyType getType() {
            return PolicyType.AUTHORIZATION;
        }
    }

    /**
     * Policy application result
     */
    @lombok.Data
    @Builder
    public static class PolicyResult {
        private final boolean success;
        private final String policyName;
        private final PolicyType type;
        private final LocalDateTime appliedAt;
        private final String errorMessage;
        private final Map<String, Object> metadata;
    }

    /**
     * Rate limit policy request
     */
    @lombok.Data
    @Builder
    public static class RateLimitRequest {
        private final String policyName;
        private final String serviceName;
        private final int requests;
        private final int burstCapacity;
        private final String keyExtractor;
        private final int responseStatusCode;
        private final String responseMessage;
        private final Map<String, Object> metadata;
    }

    /**
     * CORS policy request
     */
    @lombok.Data
    @Builder
    public static class CorsRequest {
        private final String policyName;
        private final String serviceName;
        private final List<String> allowedOrigins;
        private final List<String> allowedMethods;
        private final List<String> allowedHeaders;
        private final List<String> exposedHeaders;
        private final boolean allowCredentials;
        private final Long maxAge;
        private final Map<String, Object> metadata;
    }

    /**
     * Authorization policy request
     */
    @lombok.Data
    @Builder
    public static class AuthorizationRequest {
        private final String policyName;
        private final String serviceName;
        private final List<String> rules;
        private final String action;
        private final Map<String, Object> metadata;
    }

}