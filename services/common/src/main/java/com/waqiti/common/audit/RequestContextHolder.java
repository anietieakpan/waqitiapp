package com.waqiti.common.audit;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Helper class for extracting request context information
 */
public class RequestContextHolder {
    
    /**
     * Get IP address from current request
     */
    public static String getIpAddress() {
        ServletRequestAttributes attrs = 
            (ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            
            // Check X-Forwarded-For header first (for proxies/load balancers)
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                // Take the first IP if there are multiple
                return xForwardedFor.split(",")[0].trim();
            }
            
            // Check X-Real-IP header
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }
            
            // Fall back to remote address
            return request.getRemoteAddr();
        }
        
        return "unknown";
    }
    
    /**
     * Get User-Agent from current request
     */
    public static String getUserAgent() {
        ServletRequestAttributes attrs = 
            (ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String userAgent = request.getHeader("User-Agent");
            return userAgent != null ? userAgent : "unknown";
        }
        
        return "unknown";
    }
    
    /**
     * Get session ID from current request
     */
    public static String getSessionId() {
        ServletRequestAttributes attrs = 
            (ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            if (request.getSession(false) != null) {
                return request.getSession().getId();
            }
        }
        
        return null;
    }
}