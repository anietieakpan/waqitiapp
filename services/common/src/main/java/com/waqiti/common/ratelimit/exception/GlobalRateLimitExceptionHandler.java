package com.waqiti.common.ratelimit.exception;

import com.waqiti.common.api.ApiResponse;
import com.waqiti.common.events.SecurityEventPublisher;
import com.waqiti.common.events.SecurityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * CRITICAL SECURITY HANDLER - Rate Limit Exception Handler
 * 
 * Handles all rate limit exceptions across the application with:
 * - Proper HTTP 429 responses with security headers
 * - Comprehensive logging for security monitoring
 * - Automatic security event publishing for SIEM systems
 * - IP blocking recommendations for repeated violations
 * - Compliance with RFC 6585 standards
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalRateLimitExceptionHandler {

    private final SecurityEventPublisher securityEventPublisher;

    /**
     * Handle rate limit exceeded exceptions with comprehensive security logging
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleRateLimitExceeded(
            RateLimitExceededException ex, 
            WebRequest request,
            HttpServletRequest httpRequest) {
        
        // Extract request details for security logging
        String clientIp = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String requestUri = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();
        
        // Create comprehensive security log entry
        log.error("CRITICAL_RATE_LIMIT_VIOLATION: IP={} User={} Endpoint={} Method={} " +
                 "Limit={} Remaining={} RetryAfter={} UserAgent={} Key={} Type={} Blocked={} Reason={}", 
                clientIp, ex.getUserId(), requestUri, method,
                ex.getRequestsAllowed(), ex.getRequestsRemaining(), ex.getRetryAfterSeconds(),
                userAgent, ex.getRateLimitKey(), ex.getKeyType(), ex.isBlocked(), ex.getBlockReason());
        
        // Publish security event for SIEM and monitoring systems
        publishSecurityEvent(ex, clientIp, userAgent, requestUri, method);
        
        // Determine response status based on violation severity
        HttpStatus status = ex.isBlocked() ? HttpStatus.FORBIDDEN : HttpStatus.TOO_MANY_REQUESTS;
        
        // Build comprehensive response headers per RFC 6585
        HttpHeaders headers = buildRateLimitHeaders(ex);
        
        // Create user-friendly error response
        Map<String, Object> errorDetails = Map.of(
            "type", "RATE_LIMIT_EXCEEDED",
            "code", ex.isBlocked() ? "BLOCKED" : "THROTTLED",
            "limit", ex.getRequestsAllowed(),
            "remaining", Math.max(0, ex.getRequestsRemaining()),
            "retryAfterSeconds", ex.getRetryAfterSeconds(),
            "resetTime", ex.getResetTime() != null ? ex.getResetTime().toEpochSecond(ZoneOffset.UTC) : null,
            "blocked", ex.isBlocked(),
            "endpoint", requestUri,
            "documentation", "https://api.example.com/rate-limiting"
        );
        
        ApiResponse<Object> response = ApiResponse.builder()
            .success(false)
            .message(ex.getFormattedMessage())
            .error(errorDetails)
            .metadata(ApiResponse.ResponseMetadata.builder()
                .timestamp(Instant.now())
                .build())
            .build();
        
        return new ResponseEntity<>(response, headers, status);
    }

    /**
     * Handle generic rate limiting configuration errors
     */
    @ExceptionHandler(RateLimitConfigurationException.class)
    public ResponseEntity<ApiResponse<Object>> handleRateLimitConfiguration(
            RateLimitConfigurationException ex, 
            WebRequest request) {
        
        log.error("RATE_LIMIT_CONFIG_ERROR: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.builder()
            .success(false)
            .message("Rate limiting service configuration error")
            .error(Map.of(
                "type", "RATE_LIMIT_CONFIG_ERROR",
                "code", "SERVICE_MISCONFIGURED"
            ))
            .metadata(ApiResponse.ResponseMetadata.builder()
                .timestamp(Instant.now())
                .build())
            .build();
        
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Build comprehensive rate limit response headers per RFC 6585
     */
    private HttpHeaders buildRateLimitHeaders(RateLimitExceededException ex) {
        HttpHeaders headers = new HttpHeaders();
        
        // Standard rate limit headers (RFC 6585)
        headers.add("X-RateLimit-Limit", String.valueOf(ex.getRequestsAllowed()));
        headers.add("X-RateLimit-Remaining", String.valueOf(Math.max(0, ex.getRequestsRemaining())));
        headers.add("X-RateLimit-Reset", ex.getResetTime() != null ? 
            String.valueOf(ex.getResetTime().toEpochSecond(ZoneOffset.UTC)) : "0");
        headers.add("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        
        // Extended headers for security monitoring
        headers.add("X-RateLimit-Type", ex.getKeyType());
        headers.add("X-RateLimit-Window", String.valueOf(ex.getWindowSeconds()));
        headers.add("X-RateLimit-Blocked", String.valueOf(ex.isBlocked()));
        
        if (ex.isBlocked() && ex.getBlockReason() != null) {
            headers.add("X-RateLimit-Block-Reason", ex.getBlockReason());
        }
        
        // Security headers
        headers.add("X-Content-Type-Options", "nosniff");
        headers.add("X-Frame-Options", "DENY");
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        
        return headers;
    }

    /**
     * Publish comprehensive security event for monitoring systems
     */
    private void publishSecurityEvent(RateLimitExceededException ex, 
                                    String clientIp, 
                                    String userAgent, 
                                    String requestUri, 
                                    String method) {
        try {
            SecurityEvent.SecurityEventBuilder eventBuilder = SecurityEvent.builder()
                .eventType(ex.isBlocked() ? "RATE_LIMIT_BLOCK" : "RATE_LIMIT_VIOLATION")
                .severity(ex.isBlocked() ? "HIGH" : "MEDIUM")
                .clientIp(clientIp)
                .userId(ex.getUserId())
                .endpoint(requestUri)
                .method(method)
                .userAgent(userAgent)
                .timestamp(System.currentTimeMillis());
            
            // Build detailed event context
            String details = String.format(
                "{\"rateLimitKey\":\"%s\",\"keyType\":\"%s\",\"limit\":%d,\"remaining\":%d," +
                "\"windowSeconds\":%d,\"retryAfterSeconds\":%d,\"blocked\":%s,\"blockReason\":\"%s\"," +
                "\"endpoint\":\"%s\",\"method\":\"%s\"}",
                ex.getRateLimitKey(), ex.getKeyType(), ex.getRequestsAllowed(), 
                ex.getRequestsRemaining(), ex.getWindowSeconds(), ex.getRetryAfterSeconds(),
                ex.isBlocked(), ex.getBlockReason() != null ? ex.getBlockReason() : "",
                requestUri, method
            );
            
            SecurityEvent event = eventBuilder.details(details).build();
            securityEventPublisher.publishSecurityEvent(event);
            
            // Additional alerting for blocked IPs/users
            if (ex.isBlocked()) {
                SecurityEvent blockEvent = SecurityEvent.builder()
                    .eventType("IP_USER_BLOCKED")
                    .severity("CRITICAL")
                    .clientIp(clientIp)
                    .userId(ex.getUserId())
                    .details(String.format("{\"reason\":\"%s\",\"duration\":%d,\"keyType\":\"%s\"}", 
                            ex.getBlockReason(), ex.getRetryAfterSeconds(), ex.getKeyType()))
                    .timestamp(System.currentTimeMillis())
                    .build();
                securityEventPublisher.publishSecurityEvent(blockEvent);
            }
            
        } catch (Exception e) {
            log.error("Failed to publish rate limit security event", e);
        }
    }

    /**
     * Extract real client IP considering proxy headers and security
     */
    private String extractClientIp(HttpServletRequest request) {
        // Check common proxy headers in order of trust
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP", 
            "X-Cluster-Client-IP",
            "X-Forwarded",
            "Forwarded-For",
            "Forwarded"
        };
        
        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Take first IP from comma-separated list
                String firstIp = ip.split(",")[0].trim();
                if (isValidIpAddress(firstIp)) {
                    return firstIp;
                }
            }
        }
        
        // Fallback to remote address
        return request.getRemoteAddr();
    }

    /**
     * Basic IP address validation
     */
    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        // Basic validation - could be enhanced with proper regex
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}