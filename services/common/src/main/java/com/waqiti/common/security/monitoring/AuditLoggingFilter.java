package com.waqiti.common.security.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Security audit logging filter for tracking all security-related events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLoggingFilter implements Filter {
    
    private final ObjectMapper objectMapper;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String requestId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        
        // Add request ID to response headers for tracing
        httpResponse.setHeader("X-Request-ID", requestId);
        
        try {
            // Log request
            logSecurityEvent("REQUEST_RECEIVED", httpRequest, requestId, null);
            
            chain.doFilter(request, response);
            
            // Log response
            long duration = System.currentTimeMillis() - startTime;
            logSecurityEvent("REQUEST_COMPLETED", httpRequest, requestId, 
                Map.of("status", httpResponse.getStatus(), "duration_ms", duration));
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logSecurityEvent("REQUEST_FAILED", httpRequest, requestId,
                Map.of("error", e.getMessage(), "duration_ms", duration));
            throw e;
        }
    }
    
    private void logSecurityEvent(String eventType, HttpServletRequest request, String requestId, Map<String, Object> additionalData) {
        try {
            Map<String, Object> auditEvent = createAuditEvent(eventType, request, requestId, additionalData);
            
            // Log as structured JSON for security monitoring systems
            log.info("SECURITY_AUDIT: {}", objectMapper.writeValueAsString(auditEvent));
            
        } catch (Exception e) {
            log.error("Failed to log security audit event", e);
        }
    }
    
    private Map<String, Object> createAuditEvent(String eventType, HttpServletRequest request, 
                                               String requestId, Map<String, Object> additionalData) {
        Map<String, Object> auditEvent = new HashMap<>();
        
        // Basic event information
        auditEvent.put("event_type", eventType);
        auditEvent.put("timestamp", LocalDateTime.now().toString());
        auditEvent.put("request_id", requestId);
        
        // Request information
        auditEvent.put("method", request.getMethod());
        auditEvent.put("uri", request.getRequestURI());
        auditEvent.put("query_string", request.getQueryString());
        auditEvent.put("remote_addr", getClientIpAddress(request));
        auditEvent.put("user_agent", request.getHeader("User-Agent"));
        auditEvent.put("referer", request.getHeader("Referer"));
        
        // Authentication information
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null) {
            auditEvent.put("auth_type", authHeader.startsWith("Bearer") ? "JWT" : "OTHER");
            auditEvent.put("has_auth", true);
        } else {
            auditEvent.put("has_auth", false);
        }
        
        // Session information
        if (request.getSession(false) != null) {
            auditEvent.put("session_id", request.getSession().getId());
        }
        
        // Security headers
        Map<String, String> securityHeaders = new HashMap<>();
        securityHeaders.put("X-Forwarded-For", request.getHeader("X-Forwarded-For"));
        securityHeaders.put("X-Real-IP", request.getHeader("X-Real-IP"));
        securityHeaders.put("X-Forwarded-Proto", request.getHeader("X-Forwarded-Proto"));
        auditEvent.put("security_headers", securityHeaders);
        
        // Additional data
        if (additionalData != null) {
            auditEvent.putAll(additionalData);
        }
        
        return auditEvent;
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
}