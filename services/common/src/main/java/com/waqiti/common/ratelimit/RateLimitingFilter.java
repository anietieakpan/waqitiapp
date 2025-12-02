package com.waqiti.common.ratelimit;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enterprise-grade rate limiting filter for HTTP requests
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RateLimitingFilter implements Filter {

    private final RateLimiterConfiguration rateLimiterConfig;
    private final RateLimiterRegistry rateLimiterRegistry;
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("Initializing rate limiting filter");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        if (!rateLimiterConfig.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String endpoint = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();
        String clientIp = getClientIp(httpRequest);
        String userId = getUserId(httpRequest);
        
        // Check if endpoint is rate limited
        if (!rateLimiterConfig.isEndpointRateLimited(endpoint)) {
            chain.doFilter(request, response);
            return;
        }
        
        try {
            // Apply rate limiting
            boolean allowed = applyRateLimiting(endpoint, method, clientIp, userId);
            
            if (!allowed) {
                handleRateLimitExceeded(httpRequest, httpResponse);
                return;
            }
            
            // Add rate limit headers
            addRateLimitHeaders(httpResponse, endpoint, clientIp, userId);
            
            // Continue with request
            chain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("Error in rate limiting filter", e);
            chain.doFilter(request, response);
        }
    }

    /**
     * Apply rate limiting logic
     */
    private boolean applyRateLimiting(String endpoint, String method, String clientIp, String userId) {
        boolean allowed = true;
        
        // Check per-endpoint rate limit
        if (rateLimiterConfig.getGlobal().isEnablePerEndpoint()) {
            String endpointKey = endpoint + ":" + method;
            int limit = rateLimiterConfig.getLimitForEndpoint(endpoint);
            allowed = allowed && checkRateLimit(endpointKey, limit);
        }
        
        // Check per-IP rate limit
        if (allowed && rateLimiterConfig.getGlobal().isEnablePerIp() && clientIp != null) {
            String ipKey = rateLimiterConfig.getIpRateLimitKey(clientIp, endpoint);
            int limit = rateLimiterConfig.getGlobal().getDefaultLimit();
            allowed = allowed && checkRateLimit(ipKey, limit);
        }
        
        // Check per-user rate limit
        if (allowed && rateLimiterConfig.getGlobal().isEnablePerUser() && userId != null) {
            String userKey = rateLimiterConfig.getUserRateLimitKey(userId, endpoint);
            int limit = rateLimiterConfig.getGlobal().getDefaultLimit();
            allowed = allowed && checkRateLimit(userKey, limit);
        }
        
        return allowed;
    }

    /**
     * Check rate limit for a specific key
     */
    private boolean checkRateLimit(String key, int limit) {
        try {
            // Try to get or create rate limiter for this key
            RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(key, () -> 
                rateLimiterConfig.createCustomRateLimiter(key, limit, Duration.ofSeconds(60))
            );
            
            // Try to acquire permission
            return rateLimiter.acquirePermission();
            
        } catch (Exception e) {
            log.error("Error checking rate limit for key: {}", key, e);
            // Fallback to in-memory rate limiting
            return rateLimiterConfig.isRequestAllowed(key, limit, 60000);
        }
    }

    /**
     * Handle rate limit exceeded
     */
    private void handleRateLimitExceeded(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        log.warn("Rate limit exceeded for endpoint: {} from IP: {}", 
                request.getRequestURI(), getClientIp(request));
        
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.getWriter().write(
            "{\"error\":\"Rate limit exceeded\",\"message\":\"Too many requests. Please try again later.\"}"
        );
    }

    /**
     * Add rate limit headers to response
     */
    private void addRateLimitHeaders(HttpServletResponse response, String endpoint, 
                                     String clientIp, String userId) {
        try {
            int limit = rateLimiterConfig.getLimitForEndpoint(endpoint);
            String key = endpoint;
            
            if (userId != null) {
                key = rateLimiterConfig.getUserRateLimitKey(userId, endpoint);
            } else if (clientIp != null) {
                key = rateLimiterConfig.getIpRateLimitKey(clientIp, endpoint);
            }
            
            long remaining = rateLimiterConfig.getRemainingRequests(key, limit);
            long resetTime = rateLimiterConfig.getWindowResetTime(key, 60000);
            
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("X-RateLimit-Reset", String.valueOf(resetTime / 1000));
            
        } catch (Exception e) {
            log.debug("Error adding rate limit headers", e);
        }
    }

    /**
     * Get client IP address
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
     * Get user ID from request
     */
    private String getUserId(HttpServletRequest request) {
        // Try to get from JWT token
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                // Extract user ID from JWT (simplified - would use proper JWT parsing)
                return extractUserIdFromJwt(authHeader.substring(7));
            } catch (Exception e) {
                log.debug("Could not extract user ID from JWT", e);
            }
        }
        
        // Try to get from session
        if (request.getSession(false) != null) {
            Object userId = request.getSession().getAttribute("userId");
            if (userId != null) {
                return userId.toString();
            }
        }
        
        // Try to get from request parameter
        String userId = request.getParameter("userId");
        if (userId != null) {
            return userId;
        }
        
        return null;
    }

    /**
     * Extract user ID from JWT token
     */
    private String extractUserIdFromJwt(String token) {
        // Simplified implementation - would use proper JWT library
        // This is just a placeholder
        return "user-from-jwt";
    }

    @Override
    public void destroy() {
        log.info("Destroying rate limiting filter");
    }
}