package com.waqiti.common.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * Service to extract security context information for audit trails
 * Critical for compliance and forensic analysis
 */
@Service
@Slf4j
public class SecurityContextService {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String SESSION_ID_HEADER = "X-Session-ID";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String X_REAL_IP_HEADER = "X-Real-IP";

    /**
     * Get current authenticated user ID
     */
    public String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && 
                !"anonymousUser".equals(authentication.getPrincipal())) {
                
                // Handle different authentication types
                Object principal = authentication.getPrincipal();
                if (principal instanceof String) {
                    return (String) principal;
                } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                    return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
                } else {
                    return principal.toString();
                }
            }
        } catch (Exception e) {
            log.debug("No authenticated user found: {}", e.getMessage());
        }
        return "anonymous";
    }

    /**
     * Get current session ID from request context
     */
    public String getCurrentSessionId() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                // Try custom header first
                String sessionId = request.getHeader(SESSION_ID_HEADER);
                if (sessionId != null && !sessionId.isEmpty()) {
                    return sessionId;
                }
                
                // Fall back to HTTP session
                if (request.getSession(false) != null) {
                    return request.getSession().getId();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract session ID: {}", e.getMessage());
        }
        return generateFallbackId("session");
    }

    /**
     * Get correlation ID for request tracing
     */
    public String getCorrelationId() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                String correlationId = request.getHeader(CORRELATION_ID_HEADER);
                if (correlationId != null && !correlationId.isEmpty()) {
                    return correlationId;
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract correlation ID: {}", e.getMessage());
        }
        return generateFallbackId("corr");
    }

    /**
     * Get client IP address with proxy support
     */
    public String getClientIpAddress() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                // Check for forwarded IP (proxy/load balancer)
                String forwardedFor = request.getHeader(X_FORWARDED_FOR_HEADER);
                if (forwardedFor != null && !forwardedFor.isEmpty()) {
                    // X-Forwarded-For can contain multiple IPs, take the first (original client)
                    return forwardedFor.split(",")[0].trim();
                }
                
                // Check for real IP header
                String realIp = request.getHeader(X_REAL_IP_HEADER);
                if (realIp != null && !realIp.isEmpty()) {
                    return realIp;
                }
                
                // Fall back to direct remote address
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Could not extract client IP: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Get user agent string
     */
    public String getUserAgent() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                String userAgent = request.getHeader(USER_AGENT_HEADER);
                if (userAgent != null && !userAgent.isEmpty()) {
                    // Truncate if too long to prevent database issues
                    return userAgent.length() > 512 ? userAgent.substring(0, 512) : userAgent;
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract user agent: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Get device fingerprint if available
     */
    public String getDeviceFingerprint() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                // Custom device fingerprint header
                String fingerprint = request.getHeader("X-Device-Fingerprint");
                if (fingerprint != null && !fingerprint.isEmpty()) {
                    return fingerprint;
                }
                
                // Generate basic fingerprint from available headers
                return generateBasicFingerprint(request);
            }
        } catch (Exception e) {
            log.debug("Could not extract device fingerprint: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Get complete audit context for comprehensive logging
     */
    public AuditContext getAuditContext() {
        return AuditContext.builder()
                .userId(getCurrentUserId())
                .sessionId(getCurrentSessionId())
                .correlationId(getCorrelationId())
                .clientIpAddress(getClientIpAddress())
                .userAgent(getUserAgent())
                .deviceFingerprint(getDeviceFingerprint())
                .timestamp(java.time.Instant.now())
                .build();
    }

    /**
     * Check if current context has administrative privileges
     */
    public boolean hasAdminPrivileges() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getAuthorities() != null) {
                return authentication.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().startsWith("ROLE_ADMIN") || 
                                             authority.getAuthority().equals("ADMIN"));
            }
        } catch (Exception e) {
            log.debug("Could not check admin privileges: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Check if current user has specific role
     */
    public boolean hasRole(String role) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getAuthorities() != null) {
                String fullRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                return authentication.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals(fullRole));
            }
        } catch (Exception e) {
            log.debug("Could not check role {}: {}", role, e.getMessage());
        }
        return false;
    }

    // Private helper methods

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private String generateFallbackId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String generateBasicFingerprint(HttpServletRequest request) {
        StringBuilder fingerprint = new StringBuilder();
        
        // Append various headers to create a basic fingerprint
        appendIfPresent(fingerprint, request.getHeader("Accept"));
        appendIfPresent(fingerprint, request.getHeader("Accept-Language"));
        appendIfPresent(fingerprint, request.getHeader("Accept-Encoding"));
        appendIfPresent(fingerprint, request.getRemoteAddr());
        
        if (fingerprint.length() > 0) {
            // Create hash of the fingerprint data
            return "basic-" + Math.abs(fingerprint.toString().hashCode());
        }
        
        return "unknown";
    }

    private void appendIfPresent(StringBuilder sb, String value) {
        if (value != null && !value.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("|");
            }
            sb.append(value);
        }
    }

    /**
     * Audit context data structure
     */
    @lombok.Builder
    @lombok.Data
    public static class AuditContext {
        private String userId;
        private String sessionId;
        private String correlationId;
        private String clientIpAddress;
        private String userAgent;
        private String deviceFingerprint;
        private java.time.Instant timestamp;
    }
}