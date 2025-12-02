package com.waqiti.payment.ratelimit;

import com.waqiti.common.ratelimiting.RateLimited;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.payment.ratelimit.service.DistributedRateLimitService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Production-ready distributed rate limiting filter
 * 
 * Provides comprehensive rate limiting with:
 * - Per-user, per-IP, and per-API-key limits
 * - Redis-backed distributed counters
 * - Token bucket algorithm implementation
 * - Sliding window rate limiting
 * - Graceful degradation on Redis failure
 * - Comprehensive metrics and monitoring
 * 
 * @author Waqiti Payment Team
 * @version 2.0.0
 * @since 2025-01-16
 */
@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter implements HandlerInterceptor {

    private final DistributedRateLimitService rateLimitService;
    private final SecurityContext securityContext;
    private final RateLimitMetricsCollector metricsCollector;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) 
            throws Exception {

        // Only process if handler has @RateLimited annotation
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        Method method = handlerMethod.getMethod();
        RateLimited rateLimited = method.getAnnotation(RateLimited.class);
        
        if (rateLimited == null) {
            rateLimited = method.getDeclaringClass().getAnnotation(RateLimited.class);
        }

        if (rateLimited == null) {
            return true;
        }

        // Extract rate limit key
        String rateLimitKey = extractRateLimitKey(request, rateLimited);
        
        log.debug("Checking rate limit - key: {}, capacity: {}, refillRate: {}/{}min", 
            rateLimitKey, rateLimited.capacity(), rateLimited.refillTokens(), rateLimited.refillPeriodMinutes());

        try {
            // Check rate limit
            RateLimitResult result = rateLimitService.checkRateLimit(
                rateLimitKey,
                rateLimited.capacity(),
                rateLimited.refillTokens(),
                rateLimited.refillPeriodMinutes()
            );

            // Add rate limit headers
            addRateLimitHeaders(response, result, rateLimited);

            // Record metrics
            metricsCollector.recordRateLimitCheck(rateLimitKey, rateLimited.keyType().toString(), result);

            if (!result.isAllowed()) {
                handleRateLimitExceeded(request, response, rateLimited, result);
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("Rate limit check failed for key: {} - falling back to allow", rateLimitKey, e);
            metricsCollector.recordRateLimitError(rateLimitKey, e);
            
            // Graceful degradation - allow request if rate limit service fails
            return true;
        }
    }

    /**
     * Extract rate limit key based on key type
     */
    private String extractRateLimitKey(HttpServletRequest request, RateLimited rateLimited) {
        String baseKey = buildBaseKey(request, rateLimited);
        
        return switch (rateLimited.keyType()) {
            case USER -> {
                String userId = securityContext.getCurrentUserId();
                yield baseKey + ":user:" + (userId != null ? userId : "anonymous");
            }
            case IP -> {
                String clientIp = getClientIpAddress(request);
                yield baseKey + ":ip:" + clientIp;
            }
            case API_KEY -> {
                String apiKey = extractApiKey(request);
                yield baseKey + ":api_key:" + (apiKey != null ? apiKey : "none");
            }
            case COMBINED -> {
                String userId = securityContext.getCurrentUserId();
                String clientIp = getClientIpAddress(request);
                yield baseKey + ":combined:" + (userId != null ? userId : "anon") + ":" + clientIp;
            }
            case CUSTOM -> {
                String customKey = extractCustomKey(request, rateLimited);
                yield baseKey + ":custom:" + customKey;
            }
        };
    }

    /**
     * Build base key from request path and method
     */
    private String buildBaseKey(HttpServletRequest request, RateLimited rateLimited) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // Use custom key prefix if provided
        if (!rateLimited.keyPrefix().isEmpty()) {
            return "rate_limit:" + rateLimited.keyPrefix();
        }
        
        // Normalize path for consistent keying
        String normalizedPath = normalizePath(path);
        return "rate_limit:" + method + ":" + normalizedPath;
    }

    /**
     * Normalize path for consistent rate limiting
     */
    private String normalizePath(String path) {
        // Remove trailing slash
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        
        // Replace path variables with placeholders
        // e.g., /api/v1/payments/123/status -> /api/v1/payments/{id}/status
        return path.replaceAll("/\\d+", "/{id}")
                  .replaceAll("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "/{uuid}")
                  .replaceAll("/[a-zA-Z0-9_-]{20,}", "/{token}");
    }

    /**
     * Get client IP address with proxy support
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP", 
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };

        for (String headerName : headerNames) {
            String value = request.getHeader(headerName);
            if (value != null && !value.isEmpty() && !"unknown".equalsIgnoreCase(value)) {
                // Take the first IP if comma-separated
                if (value.contains(",")) {
                    value = value.split(",")[0].trim();
                }
                return value;
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * Extract API key from request
     */
    private String extractApiKey(HttpServletRequest request) {
        // Check Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("ApiKey ")) {
            return authHeader.substring(7);
        }
        
        // Check X-API-Key header
        String apiKeyHeader = request.getHeader("X-API-Key");
        if (apiKeyHeader != null) {
            return apiKeyHeader;
        }
        
        // Check query parameter
        return request.getParameter("api_key");
    }

    /**
     * Extract custom key based on annotation configuration
     */
    private String extractCustomKey(HttpServletRequest request, RateLimited rateLimited) {
        if (!rateLimited.customKeyExtractor().isEmpty()) {
            // Use custom key extractor if specified
            return extractFromCustomExtractor(request, rateLimited.customKeyExtractor());
        }
        
        // Default to user ID if available, otherwise IP
        String userId = securityContext.getCurrentUserId();
        if (userId != null) {
            return userId;
        }
        
        return getClientIpAddress(request);
    }

    /**
     * Extract key using custom extractor
     */
    private String extractFromCustomExtractor(HttpServletRequest request, String extractorConfig) {
        // Parse extractor configuration
        // Format: "header:X-Custom-Header" or "param:customParam" or "path:0" (path segment)
        String[] parts = extractorConfig.split(":", 2);
        if (parts.length != 2) {
            return "unknown";
        }
        
        String type = parts[0];
        String key = parts[1];
        
        return switch (type) {
            case "header" -> request.getHeader(key);
            case "param" -> request.getParameter(key);
            case "path" -> {
                String[] pathSegments = request.getRequestURI().split("/");
                try {
                    int index = Integer.parseInt(key);
                    if (index >= 0 && index < pathSegments.length) {
                        yield pathSegments[index];
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid path index in custom extractor: {}", key);
                }
                yield "unknown";
            }
            default -> "unknown";
        };
    }

    /**
     * Add rate limit headers to response
     */
    private void addRateLimitHeaders(HttpServletResponse response, RateLimitResult result, RateLimited rateLimited) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimited.capacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemainingTokens()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.getResetTime()));
        
        if (result.getRetryAfterSeconds() > 0) {
            response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
        }
    }

    /**
     * Handle rate limit exceeded
     */
    private void handleRateLimitExceeded(HttpServletRequest request, HttpServletResponse response, 
                                       RateLimited rateLimited, RateLimitResult result) throws IOException {
        
        String rateLimitKey = extractRateLimitKey(request, rateLimited);
        String clientIp = getClientIpAddress(request);
        String userId = securityContext.getCurrentUserId();
        
        log.warn("Rate limit exceeded - key: {}, ip: {}, user: {}, limit: {}, window: {}min", 
            rateLimitKey, clientIp, userId, rateLimited.capacity(), rateLimited.refillPeriodMinutes());

        // Record security event for potential abuse
        if (result.getRemainingTokens() <= -10) { // Significant over-limit
            securityEventService.recordRateLimitAbuse(rateLimitKey, clientIp, userId, result);
        }

        // Set response status and headers
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        
        // Create error response
        RateLimitErrorResponse errorResponse = RateLimitErrorResponse.builder()
            .error("RATE_LIMIT_EXCEEDED")
            .message("Too many requests. Rate limit exceeded.")
            .retryAfterSeconds(result.getRetryAfterSeconds())
            .limit(rateLimited.capacity())
            .remainingTokens(Math.max(0, result.getRemainingTokens()))
            .resetTime(result.getResetTime())
            .build();

        // Write JSON response
        objectMapper.writeValue(response.getOutputStream(), errorResponse);

        // Record metrics
        metricsCollector.recordRateLimitExceeded(rateLimitKey, rateLimited.keyType().toString());
    }

    /**
     * Rate limit error response
     */
    @lombok.Data
    @lombok.Builder
    private static class RateLimitErrorResponse {
        private String error;
        private String message;
        private int retryAfterSeconds;
        private int limit;
        private int remainingTokens;
        private long resetTime;
    }
}