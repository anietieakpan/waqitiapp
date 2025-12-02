package com.waqiti.common.security.ratelimit;

import com.waqiti.common.security.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limiting filter using token bucket algorithm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements Filter {
    
    private final RateLimitProperties rateLimitProperties;
    private final RedisTemplate<String, String> redisTemplate;
    private final ConcurrentMap<String, Bucket> localBuckets = new ConcurrentHashMap<>();
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        if (!rateLimitProperties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        
        String clientKey = getClientKey(httpRequest);
        String endpoint = httpRequest.getRequestURI();
        
        try {
            if (!isRequestAllowed(clientKey, endpoint)) {
                log.warn("Rate limit exceeded for client: {} on endpoint: {}", clientKey, endpoint);
                sendRateLimitExceededResponse(httpResponse);
                return;
            }
            
            chain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("Error in rate limiting filter", e);
            // Allow request to proceed on error
            chain.doFilter(request, response);
        }
    }
    
    private String getClientKey(HttpServletRequest request) {
        // Try to get user ID from authentication
        String userId = getUserIdFromRequest(request);
        if (userId != null) {
            return "user:" + userId;
        }
        
        // Fall back to IP address
        String clientIp = getClientIpAddress(request);
        return "ip:" + clientIp;
    }
    
    private String getUserIdFromRequest(HttpServletRequest request) {
        // Extract user ID from JWT token or session
        // This would typically be done through Spring Security context
        return null; // Simplified for now
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
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
    
    private boolean isRequestAllowed(String clientKey, String endpoint) {
        // Get endpoint-specific limits
        RateLimitProperties.EndpointLimit endpointLimit = getEndpointLimit(endpoint);
        
        Bucket bucket = getBucket(clientKey, endpointLimit);
        return bucket.tryConsume(1);
    }
    
    private RateLimitProperties.EndpointLimit getEndpointLimit(String endpoint) {
        // Check for exact match first
        for (String pattern : rateLimitProperties.getEndpoints().keySet()) {
            if (endpoint.matches(pattern.replace("**", ".*"))) {
                return rateLimitProperties.getEndpoints().get(pattern);
            }
        }
        
        // Use global limits as fallback
        RateLimitProperties.Global global = rateLimitProperties.getGlobal();
        return new RateLimitProperties.EndpointLimit(
            global.getRequestsPerMinute(),
            global.getWindowSizeSeconds(),
            global.getBurstCapacity()
        );
    }
    
    private Bucket getBucket(String clientKey, RateLimitProperties.EndpointLimit limit) {
        if (rateLimitProperties.getDistributed().isEnabled()) {
            return getDistributedBucket(clientKey, limit);
        } else {
            return getLocalBucket(clientKey, limit);
        }
    }
    
    private Bucket getLocalBucket(String clientKey, RateLimitProperties.EndpointLimit limit) {
        return localBuckets.computeIfAbsent(clientKey, key -> {
            Bandwidth bandwidth = Bandwidth.classic(
                limit.getRequests(),
                Refill.intervally(limit.getRequests(), Duration.ofSeconds(limit.getWindowSizeSeconds()))
            );
            return Bucket.builder()
                .addLimit(bandwidth)
                .build();
        });
    }
    
    private Bucket getDistributedBucket(String clientKey, RateLimitProperties.EndpointLimit limit) {
        // In a full implementation, this would use Redis-based bucket
        // For now, fall back to local bucket
        return getLocalBucket(clientKey, limit);
    }
    
    private void sendRateLimitExceededResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setHeader("X-RateLimit-Limit", "100");
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setHeader("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() + 60000));
        
        String jsonResponse = """
            {
                "error": "rate_limit_exceeded",
                "message": "Too many requests. Please try again later.",
                "status": 429
            }
            """;
        
        response.getWriter().write(jsonResponse);
    }
}