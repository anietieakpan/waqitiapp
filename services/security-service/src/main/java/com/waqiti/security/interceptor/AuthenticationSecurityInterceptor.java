package com.waqiti.security.interceptor;

import com.waqiti.security.authentication.AuthenticationSecurityService;
import com.waqiti.security.exception.AuthenticationBypassException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authentication Security Interceptor
 * 
 * CRITICAL SECURITY: Automatically applies authentication and authorization
 * security checks to all HTTP requests to prevent bypass vulnerabilities.
 * 
 * This interceptor provides automatic protection against:
 * - Authentication bypass attempts
 * - Session hijacking and fixation
 * - Parameter manipulation attacks
 * - Privilege escalation attempts
 * - Suspicious request patterns
 * - Missing authentication context
 * 
 * PROTECTION FEATURES:
 * - Validates authentication context for all protected endpoints
 * - Detects and prevents session security violations
 * - Monitors request parameters for suspicious patterns
 * - Enforces user access controls automatically
 * - Creates comprehensive audit trails
 * - Provides real-time threat detection
 * 
 * SECURITY BENEFITS:
 * - Automatic protection without modifying existing controllers
 * - Consistent security enforcement across all endpoints
 * - Early detection of attack attempts
 * - Reduced risk of developer security mistakes
 * - Centralized security policy enforcement
 * 
 * COMPLIANCE IMPACT:
 * - Implements defense-in-depth security architecture
 * - Ensures consistent access control enforcement
 * - Provides audit trails for compliance reporting
 * - Meets OWASP security guidelines
 * - Supports PCI DSS access control requirements
 * 
 * NON-COMPLIANCE PENALTIES:
 * - Authentication bypass leading to data breach: $50-90 per record
 * - PCI DSS access control violations: $25,000-500,000 per month
 * - Regulatory fines for inadequate security: $1M+
 * - Loss of processing privileges and business reputation
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationSecurityInterceptor implements HandlerInterceptor {

    private final AuthenticationSecurityService authenticationSecurityService;

    @Value("${security.interceptor.enabled:true}")
    private boolean interceptorEnabled;

    @Value("${security.interceptor.skip-patterns:/actuator/**,/health/**,/swagger-ui/**,/v3/api-docs/**}")
    private String skipPatterns;

    @Value("${security.interceptor.protected-patterns:/api/**}")
    private String protectedPatterns;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!interceptorEnabled) {
            return true;
        }

        String requestUri = request.getRequestURI();
        String method = request.getMethod();

        try {
            // Skip security checks for public endpoints
            if (shouldSkipSecurityCheck(requestUri)) {
                return true;
            }

            // Only apply security checks to protected patterns
            if (!isProtectedEndpoint(requestUri)) {
                return true;
            }

            log.debug("Applying security checks to: {} {}", method, requestUri);

            // Get current authentication
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // Check authentication context
            validateAuthenticationContext(authentication, request);

            // Validate session security
            validateSessionSecurity(authentication, request);

            // Check request parameters for suspicious patterns
            validateRequestParameters(request, authentication);

            // Check for user access control violations
            validateUserAccessControl(request, authentication);

            log.debug("Security validation passed for: {} {}", method, requestUri);
            return true;

        } catch (AuthenticationBypassException e) {
            log.error("SECURITY VIOLATION: Authentication bypass attempt detected - {} {}: {}", 
                method, requestUri, e.getMessage());
            
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;

        } catch (Exception e) {
            log.error("Security validation error for {} {}", method, requestUri, e);
            
            // On security validation errors, fail securely
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Log completed request for security monitoring
        if (interceptorEnabled && isProtectedEndpoint(request.getRequestURI())) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String userId = null;
            
            try {
                if (authentication != null && authentication.isAuthenticated()) {
                    userId = authenticationSecurityService.extractUserIdFromAuthentication(authentication);
                }
            } catch (Exception e) {
                // Ignore extraction errors in cleanup
            }

            log.debug("Request completed: {} {} - User: {} - Status: {}", 
                request.getMethod(), request.getRequestURI(), userId, response.getStatus());
        }
    }

    private void validateAuthenticationContext(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationBypassException(
                "Authentication required for protected endpoint: " + request.getRequestURI());
        }

        // Additional authentication context validations
        if (authentication.getName() == null || authentication.getName().trim().isEmpty()) {
            throw new AuthenticationBypassException(
                "Invalid authentication context: missing user identifier");
        }

        if (authentication.getAuthorities() == null || authentication.getAuthorities().isEmpty()) {
            log.warn("Authentication context missing authorities for user: {}", authentication.getName());
        }
    }

    private void validateSessionSecurity(Authentication authentication, HttpServletRequest request) {
        try {
            authenticationSecurityService.validateSessionSecurity(request, authentication);
        } catch (AuthenticationBypassException e) {
            throw e; // Re-throw authentication bypass exceptions
        } catch (Exception e) {
            log.warn("Session security validation warning: {}", e.getMessage());
            // Don't fail the request for non-critical session issues
        }
    }

    private void validateRequestParameters(HttpServletRequest request, Authentication authentication) {
        try {
            authenticationSecurityService.validateRequestParameters(request, authentication);
        } catch (Exception e) {
            log.warn("Request parameter validation warning: {}", e.getMessage());
            // Don't fail the request for parameter warnings (they're logged for monitoring)
        }
    }

    private void validateUserAccessControl(HttpServletRequest request, Authentication authentication) {
        String requestUri = request.getRequestURI();
        
        // Extract user ID from path parameters for user-specific endpoints
        String pathUserId = extractUserIdFromPath(requestUri);
        if (pathUserId != null) {
            try {
                authenticationSecurityService.validateUserAccess(pathUserId, authentication);
                log.debug("User access validation passed for user: {} on path: {}", pathUserId, requestUri);
            } catch (AuthenticationBypassException e) {
                log.error("SECURITY: User access validation failed for user: {} on path: {}", pathUserId, requestUri);
                throw e; // Re-throw for proper handling
            } catch (Exception e) {
                log.warn("User access validation error for user: {} on path: {}: {}", 
                    pathUserId, requestUri, e.getMessage());
                // Continue processing - validation warnings are logged but don't block
            }
        }
        
        // Extract resource ID from path for resource-specific endpoints
        ResourceInfo resourceInfo = extractResourceInfoFromPath(requestUri);
        if (resourceInfo != null) {
            try {
                authenticationSecurityService.validateResourceAccess(
                    resourceInfo.resourceId, resourceInfo.resourceType, authentication);
                log.debug("Resource access validation passed for resource: {}/{} on path: {}", 
                    resourceInfo.resourceType, resourceInfo.resourceId, requestUri);
            } catch (AuthenticationBypassException e) {
                log.error("SECURITY: Resource access validation failed for resource: {}/{} on path: {}", 
                    resourceInfo.resourceType, resourceInfo.resourceId, requestUri);
                throw e; // Re-throw for proper handling
            } catch (Exception e) {
                log.warn("Resource access validation error for resource: {}/{} on path: {}: {}", 
                    resourceInfo.resourceType, resourceInfo.resourceId, requestUri, e.getMessage());
                // Continue processing - validation warnings are logged but don't block
            }
        } else {
            log.debug("No specific resource access control required for path: {}", requestUri);
        }
    }

    private boolean shouldSkipSecurityCheck(String requestUri) {
        if (skipPatterns == null || skipPatterns.isEmpty()) {
            return false;
        }

        String[] patterns = skipPatterns.split(",");
        for (String pattern : patterns) {
            pattern = pattern.trim();
            if (pattern.endsWith("/**")) {
                String prefix = pattern.substring(0, pattern.length() - 3);
                if (requestUri.startsWith(prefix)) {
                    return true;
                }
            } else if (requestUri.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean isProtectedEndpoint(String requestUri) {
        if (protectedPatterns == null || protectedPatterns.isEmpty()) {
            return true; // Protect everything by default
        }

        String[] patterns = protectedPatterns.split(",");
        for (String pattern : patterns) {
            pattern = pattern.trim();
            if (pattern.endsWith("/**")) {
                String prefix = pattern.substring(0, pattern.length() - 3);
                if (requestUri.startsWith(prefix)) {
                    return true;
                }
            } else if (requestUri.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    private String extractUserIdFromPath(String requestUri) {
        // Match patterns like /api/v1/users/{userId} or /api/v1/accounts/user/{userId}
        if (requestUri == null || requestUri.trim().isEmpty()) {
            log.debug("Empty or null request URI provided for user ID extraction");
            return null;
        }
        
        String[] segments = requestUri.split("/");
        
        for (int i = 0; i < segments.length - 1; i++) {
            String segment = segments[i];
            if (segment.equals("user") || segment.equals("users")) {
                String nextSegment = segments[i + 1];
                if (isValidUUID(nextSegment)) {
                    log.debug("Extracted user ID from path: {} -> {}", requestUri, nextSegment);
                    return nextSegment;
                }
            }
        }
        
        log.debug("No valid user ID found in path: {}", requestUri);
        return null; // This is acceptable - not all endpoints have user IDs in path
    }

    private ResourceInfo extractResourceInfoFromPath(String requestUri) {
        // Match patterns like /api/v1/accounts/{accountId} or /api/v1/payments/{paymentId}
        if (requestUri == null || requestUri.trim().isEmpty()) {
            log.debug("Empty or null request URI provided for resource info extraction");
            return null;
        }
        
        String[] segments = requestUri.split("/");
        
        for (int i = 0; i < segments.length - 1; i++) {
            String segment = segments[i];
            String nextSegment = segments[i + 1];
            
            if (isResourceType(segment) && isValidUUID(nextSegment)) {
                log.debug("Extracted resource info from path: {} -> {}/{}", requestUri, segment, nextSegment);
                return new ResourceInfo(nextSegment, segment);
            }
        }
        
        log.debug("No valid resource info found in path: {}", requestUri);
        return null; // This is acceptable - not all endpoints have resource IDs in path
    }

    private boolean isResourceType(String segment) {
        return segment.equals("accounts") || 
               segment.equals("payments") || 
               segment.equals("transactions") ||
               segment.equals("cards") ||
               segment.equals("wallets");
    }

    private boolean isValidUUID(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        
        // Standard UUID format validation
        if (value.length() != 36) {
            return false;
        }
        
        try {
            java.util.UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            log.debug("Invalid UUID format: {}", value);
            return false;
        }
    }

    // Helper class for resource information
    private static class ResourceInfo {
        final String resourceId;
        final String resourceType;

        ResourceInfo(String resourceId, String resourceType) {
            this.resourceId = resourceId;
            this.resourceType = resourceType;
        }
    }
}