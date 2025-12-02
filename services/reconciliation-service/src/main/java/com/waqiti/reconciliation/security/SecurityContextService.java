package com.waqiti.reconciliation.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * Service for accessing security context information
 */
@Service
@Slf4j
public class SecurityContextService {
    
    /**
     * Get current authenticated user ID
     */
    public String getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception e) {
            log.debug("Could not get authenticated user", e);
        }
        return "anonymous";
    }
    
    /**
     * Get current session ID
     */
    public String getCurrentSessionId() {
        try {
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attr.getRequest();
            return request.getSession().getId();
        } catch (Exception e) {
            log.debug("Could not get session ID", e);
            return UUID.randomUUID().toString();
        }
    }
    
    /**
     * Get client IP address
     */
    public String getClientIpAddress() {
        try {
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attr.getRequest();
            
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }
            
            return request.getRemoteAddr();
        } catch (Exception e) {
            log.debug("Could not get client IP address", e);
            return "unknown";
        }
    }
    
    /**
     * Get user agent
     */
    public String getUserAgent() {
        try {
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attr.getRequest();
            return request.getHeader("User-Agent");
        } catch (Exception e) {
            log.debug("Could not get user agent", e);
            return "unknown";
        }
    }
}