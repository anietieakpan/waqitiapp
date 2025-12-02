package com.waqiti.virtualcard.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * Security Context Helper
 *
 * Provides access to current user information and request context
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityContext {

    /**
     * Get current authenticated user ID
     *
     * @return User identifier
     */
    public String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof String) {
                    return (String) principal;
                }
                // If using custom UserDetails, extract user ID
                // Assuming principal has a method to get user ID
                return authentication.getName();
            }
            log.warn("No authenticated user found in security context");
            throw new SecurityException("User not authenticated");
        } catch (Exception e) {
            log.error("Error retrieving current user ID", e);
            throw new SecurityException("Unable to determine current user");
        }
    }

    /**
     * Get current user's username
     *
     * @return Username
     */
    public String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) {
                return authentication.getName();
            }
            return "anonymous";
        } catch (Exception e) {
            log.error("Error retrieving current username", e);
            return "unknown";
        }
    }

    /**
     * Get client IP address from current HTTP request
     *
     * @return IP address
     */
    public String getClientIpAddress() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request == null) {
                return "unknown";
            }

            // Check for IP address in headers (for proxy/load balancer scenarios)
            String ipAddress = request.getHeader("X-Forwarded-For");
            if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getHeader("Proxy-Client-IP");
            }
            if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getHeader("HTTP_X_FORWARDED_FOR");
            }
            if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getHeader("HTTP_CLIENT_IP");
            }
            if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getRemoteAddr();
            }

            // Handle multiple IPs (take first one)
            if (ipAddress != null && ipAddress.contains(",")) {
                ipAddress = ipAddress.split(",")[0].trim();
            }

            return ipAddress != null ? ipAddress : "unknown";

        } catch (Exception e) {
            log.warn("Unable to determine client IP address", e);
            return "unknown";
        }
    }

    /**
     * Get User-Agent from current HTTP request
     *
     * @return User agent string
     */
    public String getUserAgent() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request == null) {
                return "unknown";
            }

            String userAgent = request.getHeader("User-Agent");
            return userAgent != null ? userAgent : "unknown";

        } catch (Exception e) {
            log.warn("Unable to determine user agent", e);
            return "unknown";
        }
    }

    /**
     * Get current HTTP request
     *
     * @return HttpServletRequest or null if not in request context
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            log.debug("Not in request context", e);
            return null;
        }
    }

    /**
     * Check if user is authenticated
     *
     * @return true if authenticated
     */
    public boolean isAuthenticated() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null && authentication.isAuthenticated() &&
                   !"anonymousUser".equals(authentication.getPrincipal());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get current user's authorities/roles
     *
     * @return Set of authority strings
     */
    public java.util.Set<String> getCurrentUserAuthorities() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) {
                return authentication.getAuthorities().stream()
                    .map(grantedAuthority -> grantedAuthority.getAuthority())
                    .collect(java.util.stream.Collectors.toSet());
            }
            return java.util.Collections.emptySet();
        } catch (Exception e) {
            log.error("Error retrieving user authorities", e);
            return java.util.Collections.emptySet();
        }
    }

    /**
     * Check if current user has specific authority
     *
     * @param authority Authority to check
     * @return true if user has the authority
     */
    public boolean hasAuthority(String authority) {
        return getCurrentUserAuthorities().contains(authority);
    }
}
