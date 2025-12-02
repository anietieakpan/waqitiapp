package com.waqiti.security.ratelimit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.waqiti.common.audit.AuditService;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Financial API Rate Limiting Service
 * 
 * Implements comprehensive rate limiting for financial APIs including:
 * - Per-user rate limiting
 * - Per-IP rate limiting  
 * - Per-endpoint rate limiting
 * - Burst protection
 * - Suspicious activity detection
 * - Graduated penalties
 * 
 * SECURITY: Fixes missing rate limiting on critical financial endpoints
 */
@Service
@Slf4j
public class FinancialApiRateLimitingService {
    
    private final AuditService auditService;
    private final RateLimiterRegistry rateLimiterRegistry;
    
    // Rate limiting configuration
    @Value("${rate-limit.payment.requests-per-minute:30}")
    private int paymentRequestsPerMinute;
    
    @Value("${rate-limit.transfer.requests-per-minute:20}")
    private int transferRequestsPerMinute;
    
    @Value("${rate-limit.wallet.requests-per-minute:100}")
    private int walletRequestsPerMinute;
    
    @Value("${rate-limit.auth.requests-per-minute:10}")
    private int authRequestsPerMinute;
    
    @Value("${rate-limit.report.requests-per-hour:50}")
    private int reportRequestsPerHour;
    
    @Value("${rate-limit.global.requests-per-second:1000}")
    private int globalRequestsPerSecond;
    
    // Burst protection
    @Value("${rate-limit.burst.window-minutes:5}")
    private int burstWindowMinutes;
    
    @Value("${rate-limit.burst.threshold-multiplier:3}")
    private int burstThresholdMultiplier;
    
    // Suspicious activity detection
    private final Cache<String, Integer> suspiciousActivityCache;
    private final Cache<String, Long> penaltyCache;
    
    // Patterns for sensitive endpoints
    private static final Pattern PAYMENT_PATTERN = Pattern.compile(".*/payments/.*|.*/pay/.*|.*/transfer/.*");
    private static final Pattern AUTH_PATTERN = Pattern.compile(".*/auth/.*|.*/login.*|.*/token.*");
    private static final Pattern WALLET_PATTERN = Pattern.compile(".*/wallet/.*|.*/balance/.*");
    private static final Pattern REPORT_PATTERN = Pattern.compile(".*/reports/.*|.*/analytics/.*");
    
    public FinancialApiRateLimitingService(AuditService auditService) {
        this.auditService = auditService;
        this.rateLimiterRegistry = RateLimiterRegistry.ofDefaults();
        
        // Initialize suspicious activity tracking
        this.suspiciousActivityCache = CacheBuilder.newBuilder()
            .expireAfterWrite(burstWindowMinutes, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();
        
        // Initialize penalty tracking (longer duration)
        this.penaltyCache = CacheBuilder.newBuilder()
            .expireAfterWrite(60, TimeUnit.MINUTES) // 1 hour penalty
            .maximumSize(1000)
            .build();
    }
    
    @PostConstruct
    public void initializeRateLimiters() {
        log.info("Initializing Financial API Rate Limiters");
        
        // Payment endpoints - most restrictive
        createRateLimiter("payment-per-user", paymentRequestsPerMinute, Duration.ofMinutes(1));
        createRateLimiter("payment-per-ip", paymentRequestsPerMinute * 5, Duration.ofMinutes(1));
        
        // Transfer endpoints
        createRateLimiter("transfer-per-user", transferRequestsPerMinute, Duration.ofMinutes(1));
        createRateLimiter("transfer-per-ip", transferRequestsPerMinute * 5, Duration.ofMinutes(1));
        
        // Wallet endpoints
        createRateLimiter("wallet-per-user", walletRequestsPerMinute, Duration.ofMinutes(1));
        createRateLimiter("wallet-per-ip", walletRequestsPerMinute * 3, Duration.ofMinutes(1));
        
        // Authentication endpoints - prevent brute force
        createRateLimiter("auth-per-ip", authRequestsPerMinute, Duration.ofMinutes(1));
        createRateLimiter("auth-per-user", authRequestsPerMinute / 2, Duration.ofMinutes(1));
        
        // Reporting endpoints - per hour
        createRateLimiter("report-per-user", reportRequestsPerHour, Duration.ofHours(1));
        createRateLimiter("report-per-ip", reportRequestsPerHour * 2, Duration.ofHours(1));
        
        // Global rate limiter - prevent DDoS
        createRateLimiter("global", globalRequestsPerSecond, Duration.ofSeconds(1));
        
        log.info("Rate limiters initialized successfully");
    }
    
    /**
     * Check if request is allowed based on comprehensive rate limiting
     * 
     * @param userId User ID (if authenticated)
     * @param endpoint The API endpoint being accessed
     * @return RateLimitResult with decision and metadata
     */
    public RateLimitResult checkRateLimit(String userId, String endpoint) {
        String clientIp = getClientIpAddress();
        
        try {
            // 1. Check if client is in penalty box
            if (isInPenaltyBox(clientIp, userId)) {
                return RateLimitResult.denied("PENALTY_BOX", "Client in penalty box due to suspicious activity");
            }
            
            // 2. Check global rate limit first
            if (!checkGlobalRateLimit()) {
                return RateLimitResult.denied("GLOBAL_LIMIT", "Global rate limit exceeded");
            }
            
            // 3. Check endpoint-specific limits
            RateLimitResult endpointResult = checkEndpointRateLimit(userId, clientIp, endpoint);
            if (!endpointResult.isAllowed()) {
                return endpointResult;
            }
            
            // 4. Check for burst activity
            if (detectBurstActivity(clientIp, userId)) {
                return RateLimitResult.denied("BURST_DETECTED", "Burst activity detected");
            }
            
            // 5. Update activity counters
            updateActivityCounters(clientIp, userId, endpoint);
            
            // 6. Audit successful rate limit check
            auditService.auditRateLimit(userId, clientIp, endpoint, "ALLOWED");
            
            return RateLimitResult.allowed();
            
        } catch (Exception e) {
            log.error("Error checking rate limit", e);
            // Fail safe - allow request but audit the error
            auditService.auditRateLimit(userId, clientIp, endpoint, "ERROR: " + e.getMessage());
            return RateLimitResult.allowed();
        }
    }
    
    /**
     * Check endpoint-specific rate limits
     */
    private RateLimitResult checkEndpointRateLimit(String userId, String clientIp, String endpoint) {
        if (PAYMENT_PATTERN.matcher(endpoint).matches()) {
            return checkPaymentRateLimit(userId, clientIp);
        } else if (AUTH_PATTERN.matcher(endpoint).matches()) {
            return checkAuthRateLimit(userId, clientIp);
        } else if (WALLET_PATTERN.matcher(endpoint).matches()) {
            return checkWalletRateLimit(userId, clientIp);
        } else if (REPORT_PATTERN.matcher(endpoint).matches()) {
            return checkReportRateLimit(userId, clientIp);
        }
        
        // Default rate limiting for other endpoints
        return checkDefaultRateLimit(userId, clientIp);
    }
    
    /**
     * Check payment endpoint rate limits (most restrictive)
     */
    private RateLimitResult checkPaymentRateLimit(String userId, String clientIp) {
        // Per-user payment limit
        if (userId != null) {
            RateLimiter userLimiter = getRateLimiter("payment-per-user", userId);
            if (!userLimiter.acquirePermission()) {
                auditService.auditRateLimit(userId, clientIp, "payment", "USER_LIMIT_EXCEEDED");
                return RateLimitResult.denied("PAYMENT_USER_LIMIT", 
                    "Payment rate limit exceeded for user");
            }
        }
        
        // Per-IP payment limit
        RateLimiter ipLimiter = getRateLimiter("payment-per-ip", clientIp);
        if (!ipLimiter.acquirePermission()) {
            auditService.auditRateLimit(userId, clientIp, "payment", "IP_LIMIT_EXCEEDED");
            return RateLimitResult.denied("PAYMENT_IP_LIMIT", 
                "Payment rate limit exceeded for IP");
        }
        
        return RateLimitResult.allowed();
    }
    
    /**
     * Check authentication endpoint rate limits (prevent brute force)
     */
    private RateLimitResult checkAuthRateLimit(String userId, String clientIp) {
        // Per-IP auth limit (more restrictive)
        RateLimiter ipLimiter = getRateLimiter("auth-per-ip", clientIp);
        if (!ipLimiter.acquirePermission()) {
            // Escalate to penalty box for repeated auth failures
            addToPenaltyBox(clientIp, userId, "AUTH_BRUTE_FORCE");
            auditService.auditSecurityEvent("AUTH_BRUTE_FORCE_DETECTED", 
                String.format("IP: %s, User: %s", clientIp, userId));
            return RateLimitResult.denied("AUTH_IP_LIMIT", 
                "Authentication rate limit exceeded - potential brute force");
        }
        
        // Per-user auth limit
        if (userId != null) {
            RateLimiter userLimiter = getRateLimiter("auth-per-user", userId);
            if (!userLimiter.acquirePermission()) {
                return RateLimitResult.denied("AUTH_USER_LIMIT", 
                    "Authentication rate limit exceeded for user");
            }
        }
        
        return RateLimitResult.allowed();
    }
    
    /**
     * Check wallet endpoint rate limits
     */
    private RateLimitResult checkWalletRateLimit(String userId, String clientIp) {
        if (userId != null) {
            RateLimiter userLimiter = getRateLimiter("wallet-per-user", userId);
            if (!userLimiter.acquirePermission()) {
                return RateLimitResult.denied("WALLET_USER_LIMIT", 
                    "Wallet rate limit exceeded for user");
            }
        }
        
        RateLimiter ipLimiter = getRateLimiter("wallet-per-ip", clientIp);
        if (!ipLimiter.acquirePermission()) {
            return RateLimitResult.denied("WALLET_IP_LIMIT", 
                "Wallet rate limit exceeded for IP");
        }
        
        return RateLimitResult.allowed();
    }
    
    /**
     * Check reporting endpoint rate limits
     */
    private RateLimitResult checkReportRateLimit(String userId, String clientIp) {
        if (userId != null) {
            RateLimiter userLimiter = getRateLimiter("report-per-user", userId);
            if (!userLimiter.acquirePermission()) {
                return RateLimitResult.denied("REPORT_USER_LIMIT", 
                    "Report rate limit exceeded for user");
            }
        }
        
        RateLimiter ipLimiter = getRateLimiter("report-per-ip", clientIp);
        if (!ipLimiter.acquirePermission()) {
            return RateLimitResult.denied("REPORT_IP_LIMIT", 
                "Report rate limit exceeded for IP");
        }
        
        return RateLimitResult.allowed();
    }
    
    /**
     * Default rate limiting for other endpoints
     */
    private RateLimitResult checkDefaultRateLimit(String userId, String clientIp) {
        // Use wallet limits as default (moderate)
        return checkWalletRateLimit(userId, clientIp);
    }
    
    /**
     * Check global rate limit to prevent DDoS
     */
    private boolean checkGlobalRateLimit() {
        RateLimiter globalLimiter = getRateLimiter("global", "global");
        return globalLimiter.acquirePermission();
    }
    
    /**
     * Detect burst activity patterns
     */
    private boolean detectBurstActivity(String clientIp, String userId) {
        String key = clientIp + ":" + (userId != null ? userId : "anonymous");
        
        Integer currentCount = suspiciousActivityCache.getIfPresent(key);
        if (currentCount == null) {
            currentCount = 0;
        }
        
        currentCount++;
        suspiciousActivityCache.put(key, currentCount);
        
        // Check if burst threshold exceeded
        int threshold = paymentRequestsPerMinute * burstThresholdMultiplier;
        if (currentCount > threshold) {
            log.warn("Burst activity detected: {} requests from {}", currentCount, key);
            addToPenaltyBox(clientIp, userId, "BURST_ACTIVITY");
            return true;
        }
        
        return false;
    }
    
    /**
     * Add client to penalty box for escalated violations
     */
    private void addToPenaltyBox(String clientIp, String userId, String reason) {
        String key = clientIp + ":" + (userId != null ? userId : "anonymous");
        penaltyCache.put(key, System.currentTimeMillis());
        
        log.warn("Added to penalty box: {} for reason: {}", key, reason);
        auditService.auditSecurityEvent("PENALTY_BOX_ADDED", 
            String.format("Key: %s, Reason: %s", key, reason));
    }
    
    /**
     * Check if client is in penalty box
     */
    private boolean isInPenaltyBox(String clientIp, String userId) {
        String key = clientIp + ":" + (userId != null ? userId : "anonymous");
        return penaltyCache.getIfPresent(key) != null;
    }
    
    /**
     * Update activity counters for monitoring
     */
    private void updateActivityCounters(String clientIp, String userId, String endpoint) {
        // Implementation for updating monitoring metrics
        // This would integrate with your monitoring system
    }
    
    /**
     * Get or create rate limiter with specific key
     */
    private RateLimiter getRateLimiter(String name, String key) {
        String fullName = name + "-" + key;
        return rateLimiterRegistry.rateLimiter(fullName);
    }
    
    /**
     * Create rate limiter with specific configuration
     */
    private void createRateLimiter(String name, int requestsPerPeriod, Duration period) {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitRefreshPeriod(period)
            .limitForPeriod(requestsPerPeriod)
            .timeoutDuration(Duration.ofMillis(100))
            .build();
        
        rateLimiterRegistry.rateLimiter(name, config);
        log.debug("Created rate limiter: {} with {} requests per {}", 
            name, requestsPerPeriod, period);
    }
    
    /**
     * Get client IP address from request
     */
    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) 
                RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attrs.getRequest();
            
            // Check for X-Forwarded-For header (load balancer)
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            
            // Check for X-Real-IP header
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }
            
            // Fallback to remote address
            return request.getRemoteAddr();
            
        } catch (Exception e) {
            log.debug("Could not determine client IP", e);
            return "unknown";
        }
    }
    
    /**
     * Get rate limiting statistics
     */
    public RateLimitStatistics getStatistics() {
        return RateLimitStatistics.builder()
            .totalRateLimiters(rateLimiterRegistry.getAllRateLimiters().size())
            .penaltyBoxSize(penaltyCache.size())
            .suspiciousActivityTracked(suspiciousActivityCache.size())
            .build();
    }
    
    /**
     * Clear penalty box (admin operation)
     */
    public void clearPenaltyBox(String clientIp, String userId) {
        String key = clientIp + ":" + (userId != null ? userId : "anonymous");
        penaltyCache.invalidate(key);
        
        log.info("Cleared penalty box for: {}", key);
        auditService.auditSecurityEvent("PENALTY_BOX_CLEARED", "Key: " + key);
    }
    
    /**
     * Rate limit result
     */
    @lombok.Data
    @lombok.Builder
    public static class RateLimitResult {
        private boolean allowed;
        private String reason;
        private String details;
        
        public static RateLimitResult allowed() {
            return RateLimitResult.builder()
                .allowed(true)
                .reason("ALLOWED")
                .build();
        }
        
        public static RateLimitResult denied(String reason, String details) {
            return RateLimitResult.builder()
                .allowed(false)
                .reason(reason)
                .details(details)
                .build();
        }
    }
    
    /**
     * Rate limiting statistics
     */
    @lombok.Data
    @lombok.Builder
    public static class RateLimitStatistics {
        private int totalRateLimiters;
        private long penaltyBoxSize;
        private long suspiciousActivityTracked;
    }
}