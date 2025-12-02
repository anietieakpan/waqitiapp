package com.waqiti.common.audit.service;

import com.waqiti.common.correlation.CorrelationIdService;
import com.waqiti.common.tracing.CorrelationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * Service for extracting audit context information
 * 
 * Centralizes the extraction of context information for audit logging,
 * including user identity, session details, network information, and
 * device characteristics.
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditContextService {
    
    private final CorrelationIdService correlationIdService;
    
    /**
     * Extract current user ID from security context
     */
    public String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.debug("Failed to extract user ID from security context", e);
        }
        return "anonymous";
    }
    
    /**
     * Extract current username from security context
     */
    public String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof String) {
                    return (String) principal;
                }
                // Handle custom user details if needed
                return authentication.getName();
            }
        } catch (Exception e) {
            log.debug("Failed to extract username from security context", e);
        }
        return "anonymous";
    }
    
    /**
     * Extract session ID from request context
     */
    public String getSessionId() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                String sessionId = attributes.getSessionId();
                return sessionId != null ? sessionId : generateSessionId();
            }
        } catch (Exception e) {
            log.debug("Failed to extract session ID", e);
        }
        return generateSessionId();
    }
    
    /**
     * Extract client IP address from request
     */
    public String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                
                // Check for IP behind proxy
                String ip = request.getHeader("X-Forwarded-For");
                if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                    // X-Forwarded-For can contain multiple IPs, take the first one
                    return ip.split(",")[0].trim();
                }
                
                ip = request.getHeader("X-Real-IP");
                if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                    return ip;
                }
                
                ip = request.getHeader("Proxy-Client-IP");
                if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                    return ip;
                }
                
                ip = request.getHeader("WL-Proxy-Client-IP");
                if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                    return ip;
                }
                
                // Fall back to remote address
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Failed to extract client IP address", e);
        }
        return "unknown";
    }
    
    /**
     * Extract user agent from request
     */
    public String getUserAgent() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String userAgent = request.getHeader("User-Agent");
                return userAgent != null ? userAgent : "unknown";
            }
        } catch (Exception e) {
            log.debug("Failed to extract user agent", e);
        }
        return "unknown";
    }
    
    /**
     * Extract remote host from request
     */
    public String getRemoteHost() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String remoteHost = request.getRemoteHost();
                return remoteHost != null ? remoteHost : "unknown";
            }
        } catch (Exception e) {
            log.debug("Failed to extract remote host", e);
        }
        return "unknown";
    }
    
    /**
     * Extract request URI from request
     */
    public String getRequestUri() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getRequestURI();
            }
        } catch (Exception e) {
            log.debug("Failed to extract request URI", e);
        }
        return "unknown";
    }
    
    /**
     * Extract HTTP method from request
     */
    public String getHttpMethod() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getMethod();
            }
        } catch (Exception e) {
            log.debug("Failed to extract HTTP method", e);
        }
        return "unknown";
    }
    
    /**
     * Extract device fingerprint (simplified implementation)
     */
    public String getDeviceFingerprint() {
        try {
            String userAgent = getUserAgent();
            String acceptLanguage = getAcceptLanguage();
            String acceptEncoding = getAcceptEncoding();
            
            // Create a simple fingerprint based on headers
            int fingerprint = (userAgent + acceptLanguage + acceptEncoding).hashCode();
            return String.valueOf(Math.abs(fingerprint));
        } catch (Exception e) {
            log.debug("Failed to generate device fingerprint", e);
            return "unknown";
        }
    }
    
    /**
     * Extract Accept-Language header
     */
    public String getAcceptLanguage() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String acceptLanguage = request.getHeader("Accept-Language");
                return acceptLanguage != null ? acceptLanguage : "unknown";
            }
        } catch (Exception e) {
            log.debug("Failed to extract Accept-Language", e);
        }
        return "unknown";
    }
    
    /**
     * Extract Accept-Encoding header
     */
    public String getAcceptEncoding() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String acceptEncoding = request.getHeader("Accept-Encoding");
                return acceptEncoding != null ? acceptEncoding : "unknown";
            }
        } catch (Exception e) {
            log.debug("Failed to extract Accept-Encoding", e);
        }
        return "unknown";
    }
    
    /**
     * Extract correlation ID from request headers or MDC
     * Implements distributed tracing with MDC integration
     */
    public String getCorrelationId() {
        try {
            // Try to get from MDC first (populated by interceptors/filters)
            String mdcCorrelationId = MDC.get(CorrelationIdService.CORRELATION_ID_KEY);
            if (mdcCorrelationId != null && !mdcCorrelationId.isEmpty()) {
                return mdcCorrelationId;
            }
            
            // Try from CorrelationContext static utility
            String contextCorrelationId = CorrelationContext.getCorrelationId();
            if (contextCorrelationId != null && !contextCorrelationId.isEmpty()) {
                return contextCorrelationId;
            }
            
            // Try to get from CorrelationIdService
            String serviceCorrelationId = correlationIdService.getCorrelationId().orElse(null);
            if (serviceCorrelationId != null && !serviceCorrelationId.isEmpty()) {
                return serviceCorrelationId;
            }
            
            // Fall back to request headers
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String correlationId = request.getHeader("X-Correlation-ID");
                if (correlationId != null && !correlationId.isEmpty()) {
                    setCorrelationIdInMDC(correlationId);
                    return correlationId;
                }
                
                correlationId = request.getHeader("X-Request-ID");
                if (correlationId != null && !correlationId.isEmpty()) {
                    setCorrelationIdInMDC(correlationId);
                    return correlationId;
                }
            }
            
        } catch (Exception e) {
            log.debug("Failed to extract correlation ID", e);
        }
        
        // Generate new correlation ID if not found
        return generateCorrelationId();
    }
    
    /**
     * Get trace ID for distributed tracing
     */
    public String getTraceId() {
        try {
            String mdcTraceId = MDC.get(CorrelationIdService.TRACE_ID_KEY);
            if (mdcTraceId != null && !mdcTraceId.isEmpty()) {
                return mdcTraceId;
            }
            
            String contextTraceId = CorrelationContext.getTraceId();
            if (contextTraceId != null && !contextTraceId.isEmpty()) {
                return contextTraceId;
            }
            
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String traceId = request.getHeader("X-Trace-ID");
                if (traceId != null && !traceId.isEmpty()) {
                    setTraceIdInMDC(traceId);
                    return traceId;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract trace ID", e);
        }
        
        // Generate new trace ID if not found
        return correlationIdService.generateTraceId();
    }
    
    /**
     * Get span ID for distributed tracing
     */
    public String getSpanId() {
        try {
            String mdcSpanId = MDC.get(CorrelationIdService.SPAN_ID_KEY);
            if (mdcSpanId != null && !mdcSpanId.isEmpty()) {
                return mdcSpanId;
            }
            
            String contextSpanId = CorrelationContext.getSpanId();
            if (contextSpanId != null && !contextSpanId.isEmpty()) {
                return contextSpanId;
            }
            
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String spanId = request.getHeader("X-Span-ID");
                if (spanId != null && !spanId.isEmpty()) {
                    setSpanIdInMDC(spanId);
                    return spanId;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract span ID", e);
        }
        
        // Generate new span ID if not found
        return correlationIdService.generateSpanId();
    }
    
    /**
     * Set correlation ID in MDC for distributed tracing
     */
    public void setCorrelationIdInMDC(String correlationId) {
        if (correlationId != null && !correlationId.isEmpty()) {
            MDC.put(CorrelationIdService.CORRELATION_ID_KEY, correlationId);
            CorrelationContext.setCorrelationId(correlationId);
            correlationIdService.setCorrelationId(correlationId);
            log.debug("Set correlation ID in MDC: {}", correlationId);
        }
    }
    
    /**
     * Set trace ID in MDC for distributed tracing
     */
    public void setTraceIdInMDC(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put(CorrelationIdService.TRACE_ID_KEY, traceId);
            CorrelationContext.setTraceId(traceId);
            log.debug("Set trace ID in MDC: {}", traceId);
        }
    }
    
    /**
     * Set span ID in MDC for distributed tracing
     */
    public void setSpanIdInMDC(String spanId) {
        if (spanId != null && !spanId.isEmpty()) {
            MDC.put(CorrelationIdService.SPAN_ID_KEY, spanId);
            CorrelationContext.setSpanId(spanId);
            log.debug("Set span ID in MDC: {}", spanId);
        }
    }
    
    /**
     * Set user ID in MDC for distributed tracing
     */
    public void setUserIdInMDC(String userId) {
        if (userId != null && !userId.isEmpty()) {
            MDC.put("userId", userId);
            CorrelationContext.setUserId(userId);
            log.debug("Set user ID in MDC: {}", userId);
        }
    }
    
    /**
     * Set request information in MDC for distributed tracing
     */
    public void setRequestInfoInMDC(String method, String path) {
        if (method != null && !method.isEmpty()) {
            MDC.put("requestMethod", method);
        }
        if (path != null && !path.isEmpty()) {
            MDC.put("requestPath", path);
        }
        CorrelationContext.setRequestInfo(method, path);
        log.debug("Set request info in MDC: {} {}", method, path);
    }
    
    /**
     * Initialize MDC with complete audit context
     */
    public void initializeMDCContext() {
        try {
            String correlationId = getCorrelationId();
            String traceId = getTraceId();
            String spanId = getSpanId();
            String userId = getCurrentUserId();
            String method = getHttpMethod();
            String path = getRequestUri();
            
            setCorrelationIdInMDC(correlationId);
            setTraceIdInMDC(traceId);
            setSpanIdInMDC(spanId);
            setUserIdInMDC(userId);
            setRequestInfoInMDC(method, path);
            
            log.debug("Initialized MDC context - CorrelationId: {}, TraceId: {}, SpanId: {}, UserId: {}", 
                correlationId, traceId, spanId, userId);
        } catch (Exception e) {
            log.error("Failed to initialize MDC context", e);
        }
    }
    
    /**
     * Clear MDC context
     */
    public void clearMDCContext() {
        try {
            MDC.remove(CorrelationIdService.CORRELATION_ID_KEY);
            MDC.remove(CorrelationIdService.TRACE_ID_KEY);
            MDC.remove(CorrelationIdService.SPAN_ID_KEY);
            MDC.remove("userId");
            MDC.remove("requestMethod");
            MDC.remove("requestPath");
            CorrelationContext.clear();
            log.debug("Cleared MDC context");
        } catch (Exception e) {
            log.error("Failed to clear MDC context", e);
        }
    }
    
    /**
     * Check if request is from mobile device
     */
    public boolean isMobileDevice() {
        String userAgent = getUserAgent().toLowerCase();
        return userAgent.contains("mobile") || 
               userAgent.contains("android") || 
               userAgent.contains("iphone") || 
               userAgent.contains("ipad") ||
               userAgent.contains("windows phone");
    }
    
    /**
     * Check if request is from known bot/crawler
     */
    public boolean isBot() {
        String userAgent = getUserAgent().toLowerCase();
        return userAgent.contains("bot") || 
               userAgent.contains("crawler") || 
               userAgent.contains("spider") ||
               userAgent.contains("scraper");
    }
    
    /**
     * Check if connection is secure (HTTPS)
     */
    public boolean isSecureConnection() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.isSecure() || 
                       "https".equals(request.getHeader("X-Forwarded-Proto"));
            }
        } catch (Exception e) {
            log.debug("Failed to check if connection is secure", e);
        }
        return false;
    }
    
    /**
     * Extract referer header
     */
    public String getReferer() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String referer = request.getHeader("Referer");
                return referer != null ? referer : "unknown";
            }
        } catch (Exception e) {
            log.debug("Failed to extract referer", e);
        }
        return "unknown";
    }
    
    /**
     * Check if request is from trusted source
     */
    public boolean isTrustedSource() {
        String ipAddress = getClientIpAddress();
        
        // Check against whitelist of trusted IP ranges
        // This is a simplified implementation
        return ipAddress.startsWith("192.168.") || 
               ipAddress.startsWith("10.") || 
               ipAddress.equals("127.0.0.1") ||
               ipAddress.equals("::1");
    }
    
    /**
     * Get geographic location based on IP (placeholder)
     */
    public String getGeographicLocation() {
        // In production, this would integrate with a GeoIP service
        return "unknown";
    }
    
    /**
     * Get ISP information based on IP (placeholder)
     */
    public String getIspInformation() {
        // In production, this would integrate with an ISP lookup service
        return "unknown";
    }
    
    /**
     * Check if IP is on blacklist (placeholder)
     */
    public boolean isBlacklistedIp() {
        // In production, this would check against threat intelligence feeds
        return false;
    }
    
    /**
     * Generate unique session ID
     */
    private String generateSessionId() {
        return "session_" + UUID.randomUUID().toString();
    }
    
    /**
     * Generate unique correlation ID
     */
    private String generateCorrelationId() {
        return "corr_" + UUID.randomUUID().toString();
    }
}