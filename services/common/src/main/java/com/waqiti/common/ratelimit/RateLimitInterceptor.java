package com.waqiti.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting interceptor using Token Bucket algorithm
 * Provides protection against API abuse and DDoS attacks
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {
    
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    
    @Value("${rate.limit.default.requests:100}")
    private int defaultRequests;
    
    @Value("${rate.limit.default.duration:60}")
    private int defaultDurationSeconds;
    
    @Value("${rate.limit.enabled:true}")
    private boolean rateLimitEnabled;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!rateLimitEnabled) {
            return true;
        }
        
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();
        
        // Check for RateLimit annotation
        RateLimit rateLimitAnnotation = method.getAnnotation(RateLimit.class);
        if (rateLimitAnnotation == null) {
            rateLimitAnnotation = handlerMethod.getBeanType().getAnnotation(RateLimit.class);
        }
        
        // Determine rate limits
        int requests = rateLimitAnnotation != null ? rateLimitAnnotation.requests() : defaultRequests;
        int durationSeconds = rateLimitAnnotation != null ? rateLimitAnnotation.durationSeconds() : defaultDurationSeconds;
        
        // Get bucket key based on client identification
        String bucketKey = getBucketKey(request, rateLimitAnnotation);
        
        // Get or create bucket for this key
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> createBucket(requests, durationSeconds));
        
        // Try to consume token
        if (bucket.tryConsume(1)) {
            // Add rate limit headers
            response.addHeader("X-RateLimit-Limit", String.valueOf(requests));
            response.addHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            response.addHeader("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() / 1000 + durationSeconds));
            return true;
        } else {
            // Rate limit exceeded
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.addHeader("X-RateLimit-Limit", String.valueOf(requests));
            response.addHeader("X-RateLimit-Remaining", "0");
            response.addHeader("X-RateLimit-Retry-After", String.valueOf(durationSeconds));
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Please try again later.\"}");
            
            log.warn("Rate limit exceeded for key: {} - endpoint: {}", bucketKey, request.getRequestURI());
            return false;
        }
    }
    
    /**
     * Generate bucket key based on rate limit strategy
     */
    private String getBucketKey(HttpServletRequest request, RateLimit rateLimitAnnotation) {
        RateLimitStrategy strategy = rateLimitAnnotation != null 
            ? rateLimitAnnotation.strategy() 
            : RateLimitStrategy.IP;
        
        StringBuilder keyBuilder = new StringBuilder();
        
        switch (strategy) {
            case IP:
                keyBuilder.append(getClientIp(request));
                break;
            case USER:
                String userId = extractUserId(request);
                keyBuilder.append(userId != null ? userId : getClientIp(request));
                break;
            case API_KEY:
                String apiKey = request.getHeader("X-API-Key");
                keyBuilder.append(apiKey != null ? apiKey : getClientIp(request));
                break;
            case ENDPOINT:
                keyBuilder.append(request.getRequestURI());
                break;
            case GLOBAL:
                keyBuilder.append("GLOBAL");
                break;
            case IP_AND_USER:
                String userIdForCombo = extractUserId(request);
                keyBuilder.append(getClientIp(request))
                          .append(":")
                          .append(userIdForCombo != null ? userIdForCombo : "anonymous");
                break;
            default:
                keyBuilder.append(getClientIp(request));
        }
        
        // Add endpoint for non-global strategies
        if (strategy != RateLimitStrategy.GLOBAL && strategy != RateLimitStrategy.ENDPOINT) {
            keyBuilder.append(":").append(request.getRequestURI());
        }
        
        return keyBuilder.toString();
    }
    
    /**
     * Create bucket with specified limits
     */
    private Bucket createBucket(int requests, int durationSeconds) {
        Bandwidth limit = Bandwidth.classic(requests, 
            Refill.intervally(requests, Duration.ofSeconds(durationSeconds)));
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }
    
    /**
     * Extract client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Extract user ID from request
     */
    private String extractUserId(HttpServletRequest request) {
        // Try to extract from JWT token
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // In production, decode JWT and extract user ID
            // For now, use header
            return request.getHeader("X-User-Id");
        }
        return null;
    }
    
    /**
     * Clean up old buckets periodically
     */
    public void cleanupBuckets() {
        // Remove buckets that haven't been used recently
        // This prevents memory leaks from accumulating buckets
        log.info("Cleaning up {} rate limit buckets", buckets.size());
        
        // In production, implement more sophisticated cleanup
        // based on last access time
        if (buckets.size() > 10000) {
            buckets.clear();
            log.warn("Cleared all rate limit buckets due to size limit");
        }
    }
}