package com.waqiti.security.authentication;

import com.waqiti.security.audit.AuditService;
import com.waqiti.security.client.*;
import com.waqiti.security.exception.AuthenticationBypassException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Authentication Security Service
 * 
 * CRITICAL SECURITY: Prevents authentication bypass vulnerabilities
 * 
 * This service provides comprehensive security checks to prevent common
 * authentication bypass attacks and vulnerabilities:
 * 
 * SECURITY FEATURES:
 * - User impersonation prevention
 * - Role escalation prevention  
 * - Session hijacking protection
 * - Authentication context validation
 * - Resource access authorization
 * - Audit trail for security events
 * 
 * COMMON VULNERABILITIES PREVENTED:
 * - Horizontal privilege escalation (accessing other users' data)
 * - Vertical privilege escalation (gaining admin privileges)
 * - Session fixation attacks
 * - Authentication bypass through parameter manipulation
 * - Missing authorization checks
 * - Insecure direct object references
 * 
 * COMPLIANCE:
 * - OWASP Top 10: A01 Broken Access Control
 * - OWASP Top 10: A05 Security Misconfiguration
 * - PCI DSS: Requirement 7 (Restrict access by business need-to-know)
 * - PCI DSS: Requirement 8 (Identify and authenticate access)
 * 
 * SECURITY IMPACT:
 * - Prevents unauthorized access to sensitive financial data
 * - Protects against account takeover attacks
 * - Ensures proper segregation of duties
 * - Maintains audit trails for compliance
 * 
 * NON-COMPLIANCE PENALTIES:
 * - Data breach due to access control failure: $50-90 per record
 * - PCI DSS violations: $5,000-500,000 per month
 * - Regulatory fines for unauthorized access: $1M+
 * - Loss of customer trust and business reputation
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationSecurityService {

    private final AuditService auditService;
    private final AccountServiceClient accountServiceClient;
    private final WalletServiceClient walletServiceClient;
    private final CardServiceClient cardServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final UserServiceClient userServiceClient;

    @Value("${security.authentication.strict-mode:true}")
    private boolean strictMode;

    @Value("${security.authentication.audit.enabled:true}")
    private boolean auditEnabled;

    @Value("${security.session.timeout.minutes:30}")
    private int sessionTimeoutMinutes;

    // Session security tracking
    private final Map<String, SessionSecurityContext> activeSessions = new ConcurrentHashMap<>();
    
    // Security patterns
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern SUSPICIOUS_PARAMETER_PATTERN = Pattern.compile(".*(admin|root|system|debug|test).*", Pattern.CASE_INSENSITIVE);

    /**
     * Validates that the authenticated user can access the specified user's resources
     * Prevents horizontal privilege escalation
     * 
     * @param targetUserId The user ID being accessed
     * @param authentication Current authentication context
     * @throws AuthenticationBypassException if access is not allowed
     */
    public void validateUserAccess(String targetUserId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            auditSecurityViolation("USER_ACCESS_UNAUTHENTICATED", targetUserId, null);
            throw new AuthenticationBypassException("Authentication required");
        }

        String authenticatedUserId = extractUserIdFromAuthentication(authentication);
        
        // Admin users can access any user's data
        if (hasAdminRole(authentication)) {
            auditSecurityEvent("ADMIN_USER_ACCESS", authenticatedUserId, targetUserId);
            return;
        }
        
        // Regular users can only access their own data
        if (!authenticatedUserId.equals(targetUserId)) {
            auditSecurityViolation("HORIZONTAL_PRIVILEGE_ESCALATION_ATTEMPT", 
                authenticatedUserId, targetUserId);
            throw new AuthenticationBypassException(
                "Access denied: Cannot access resources of user " + targetUserId);
        }
        
        auditSecurityEvent("VALID_USER_ACCESS", authenticatedUserId, targetUserId);
    }

    /**
     * Validates that the authenticated user can access the specified user's resources by UUID
     * 
     * @param targetUserId UUID of the user being accessed
     * @param authentication Current authentication context
     */
    public void validateUserAccess(UUID targetUserId, Authentication authentication) {
        validateUserAccess(targetUserId.toString(), authentication);
    }

    /**
     * Validates that the authenticated user can access a specific resource
     * 
     * @param resourceId Resource identifier
     * @param resourceType Type of resource (account, payment, etc.)
     * @param authentication Current authentication context
     */
    public void validateResourceAccess(String resourceId, String resourceType, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            auditSecurityViolation("RESOURCE_ACCESS_UNAUTHENTICATED", resourceId, resourceType);
            throw new AuthenticationBypassException("Authentication required for resource access");
        }

        String authenticatedUserId = extractUserIdFromAuthentication(authentication);
        
        // Admin users can access any resource
        if (hasAdminRole(authentication)) {
            auditSecurityEvent("ADMIN_RESOURCE_ACCESS", authenticatedUserId, 
                String.format("%s:%s", resourceType, resourceId));
            return;
        }
        
        // Check if user owns the resource (this would typically query the database)
        if (!isResourceOwner(authenticatedUserId, resourceId, resourceType)) {
            auditSecurityViolation("UNAUTHORIZED_RESOURCE_ACCESS", authenticatedUserId, 
                String.format("%s:%s", resourceType, resourceId));
            throw new AuthenticationBypassException(
                "Access denied: Cannot access " + resourceType + " " + resourceId);
        }
        
        auditSecurityEvent("VALID_RESOURCE_ACCESS", authenticatedUserId, 
            String.format("%s:%s", resourceType, resourceId));
    }

    /**
     * Validates that the current user has the required role
     * Prevents vertical privilege escalation
     * 
     * @param requiredRole Required role for the operation
     * @param authentication Current authentication context
     */
    public void validateRoleAccess(String requiredRole, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            auditSecurityViolation("ROLE_ACCESS_UNAUTHENTICATED", requiredRole, null);
            throw new AuthenticationBypassException("Authentication required for role-based access");
        }

        String authenticatedUserId = extractUserIdFromAuthentication(authentication);
        
        if (!hasRole(authentication, requiredRole)) {
            auditSecurityViolation("VERTICAL_PRIVILEGE_ESCALATION_ATTEMPT", 
                authenticatedUserId, requiredRole);
            throw new AccessDeniedException("Access denied: Required role " + requiredRole + " not found");
        }
        
        auditSecurityEvent("VALID_ROLE_ACCESS", authenticatedUserId, requiredRole);
    }

    /**
     * Creates a secure session context with enhanced tracking
     * 
     * @param authentication User authentication
     * @param request HTTP request for session data
     * @return Session security context
     */
    public SessionSecurityContext createSecureSession(Authentication authentication, HttpServletRequest request) {
        String userId = extractUserIdFromAuthentication(authentication);
        String sessionId = request.getSession().getId();
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        
        SessionSecurityContext sessionContext = SessionSecurityContext.builder()
            .sessionId(sessionId)
            .userId(userId)
            .clientIp(clientIp)
            .userAgent(userAgent)
            .createdAt(LocalDateTime.now())
            .lastAccessedAt(LocalDateTime.now())
            .authorities(new HashSet<>(authentication.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .toList()))
            .build();
        
        activeSessions.put(sessionId, sessionContext);
        
        auditSecurityEvent("SECURE_SESSION_CREATED", userId, sessionId);
        
        return sessionContext;
    }

    /**
     * Validates session security and detects hijacking attempts
     * 
     * @param request Current HTTP request
     * @param authentication Current authentication
     */
    public void validateSessionSecurity(HttpServletRequest request, Authentication authentication) {
        String sessionId = request.getSession().getId();
        String currentIp = getClientIpAddress(request);
        String currentUserAgent = request.getHeader("User-Agent");
        
        SessionSecurityContext sessionContext = activeSessions.get(sessionId);
        
        if (sessionContext == null) {
            // Session not tracked - could be session fixation attempt
            auditSecurityViolation("UNTRACKED_SESSION_ACCESS", sessionId, currentIp);
            createSecureSession(authentication, request); // Create tracking
            return;
        }
        
        // Check for IP address change (potential session hijacking)
        if (!sessionContext.getClientIp().equals(currentIp)) {
            auditSecurityViolation("SESSION_IP_MISMATCH", sessionContext.getUserId(), 
                String.format("Original: %s, Current: %s", sessionContext.getClientIp(), currentIp));
            
            if (strictMode) {
                invalidateSession(sessionId);
                throw new AuthenticationBypassException("Session security violation: IP address mismatch");
            } else {
                log.warn("Session IP mismatch detected for user {}: {} -> {}", 
                    sessionContext.getUserId(), sessionContext.getClientIp(), currentIp);
            }
        }
        
        // Check for User-Agent change (potential session hijacking)
        if (sessionContext.getUserAgent() != null && !sessionContext.getUserAgent().equals(currentUserAgent)) {
            auditSecurityViolation("SESSION_USERAGENT_MISMATCH", sessionContext.getUserId(), 
                String.format("Session: %s", sessionId));
            
            if (strictMode) {
                log.warn("User-Agent mismatch detected for session {}", sessionId);
                // Don't invalidate for User-Agent changes as they can be legitimate
            }
        }
        
        // Check session timeout
        if (sessionContext.getLastAccessedAt().plusMinutes(sessionTimeoutMinutes).isBefore(LocalDateTime.now())) {
            auditSecurityEvent("SESSION_TIMEOUT", sessionContext.getUserId(), sessionId);
            invalidateSession(sessionId);
            throw new AuthenticationBypassException("Session expired");
        }
        
        // Update last accessed time
        sessionContext.setLastAccessedAt(LocalDateTime.now());
    }

    /**
     * Checks for suspicious request parameters that might indicate bypass attempts
     * 
     * @param request HTTP request to analyze
     * @param authentication Current authentication
     */
    public void validateRequestParameters(HttpServletRequest request, Authentication authentication) {
        String userId = authentication != null ? extractUserIdFromAuthentication(authentication) : "anonymous";
        
        // Check for suspicious parameter names and values
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String[] paramValues = request.getParameterValues(paramName);
            
            for (String paramValue : paramValues) {
                // Check for suspicious parameter names
                if (SUSPICIOUS_PARAMETER_PATTERN.matcher(paramName).matches()) {
                    auditSecurityViolation("SUSPICIOUS_PARAMETER_NAME", userId, 
                        String.format("Parameter: %s", paramName));
                }
                
                // Check for suspicious parameter values
                if (paramValue != null && SUSPICIOUS_PARAMETER_PATTERN.matcher(paramValue).matches()) {
                    auditSecurityViolation("SUSPICIOUS_PARAMETER_VALUE", userId, 
                        String.format("Parameter: %s, Value: %s", paramName, maskSensitiveValue(paramValue)));
                }
                
                // Check for potential injection attempts
                if (paramValue != null && containsPotentialInjection(paramValue)) {
                    auditSecurityViolation("POTENTIAL_INJECTION_ATTEMPT", userId, 
                        String.format("Parameter: %s", paramName));
                }
            }
        }
    }

    /**
     * Enforces secure user ID extraction from authentication
     * 
     * @param authentication Authentication context
     * @return Validated user ID
     */
    public String extractUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            auditSecurityViolation("INVALID_AUTHENTICATION_CONTEXT", null, null);
            throw new AuthenticationBypassException("Invalid authentication context");
        }
        
        String userId = authentication.getName();
        
        // Validate UUID format for user IDs
        if (!UUID_PATTERN.matcher(userId).matches()) {
            auditSecurityViolation("INVALID_USER_ID_FORMAT", userId, null);
            throw new AuthenticationBypassException("Invalid user ID format in authentication");
        }
        
        return userId;
    }

    /**
     * Invalidates a session and cleans up security context
     * 
     * @param sessionId Session to invalidate
     */
    public void invalidateSession(String sessionId) {
        SessionSecurityContext context = activeSessions.remove(sessionId);
        if (context != null) {
            auditSecurityEvent("SESSION_INVALIDATED", context.getUserId(), sessionId);
        }
    }

    /**
     * Performs cleanup of expired sessions
     */
    public void cleanupExpiredSessions() {
        LocalDateTime expireThreshold = LocalDateTime.now().minusMinutes(sessionTimeoutMinutes);
        
        Iterator<Map.Entry<String, SessionSecurityContext>> iterator = activeSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SessionSecurityContext> entry = iterator.next();
            SessionSecurityContext context = entry.getValue();
            
            if (context.getLastAccessedAt().isBefore(expireThreshold)) {
                auditSecurityEvent("SESSION_EXPIRED_CLEANUP", context.getUserId(), entry.getKey());
                iterator.remove();
            }
        }
    }

    // Private helper methods

    private boolean hasAdminRole(Authentication authentication) {
        return hasRole(authentication, "ADMIN") || hasRole(authentication, "ROLE_ADMIN");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals(role) || 
                             auth.getAuthority().equals("ROLE_" + role));
    }

    private boolean isResourceOwner(String userId, String resourceId, String resourceType) {
        // Implement resource ownership checks based on resource type
        try {
            switch (resourceType.toLowerCase()) {
                case "payment":
                case "transaction":
                    return checkPaymentOwnership(userId, resourceId);
                case "account":
                case "wallet":
                    return checkAccountOwnership(userId, resourceId);
                case "card":
                    return checkCardOwnership(userId, resourceId);
                case "beneficiary":
                    return checkBeneficiaryOwnership(userId, resourceId);
                default:
                    // For unknown resource types, log and deny access
                    log.warn("Unknown resource type for ownership check: {}", resourceType);
                    auditSecurityViolation("UNKNOWN_RESOURCE_TYPE_ACCESS", userId, 
                        String.format("ResourceType: %s, ResourceId: %s", resourceType, resourceId));
                    return false;
            }
        } catch (Exception e) {
            log.error("Error checking resource ownership for user {} and resource {}:{}", 
                userId, resourceType, resourceId, e);
            // Fail securely - deny access on errors
            return false;
        }
    }
    
    private boolean checkPaymentOwnership(String userId, String resourceId) {
        // Check if user is sender, receiver, or has admin privileges
        log.debug("Checking payment ownership: user={}, payment={}", userId, resourceId);
        
        try {
            // Query payment service to verify ownership
            PaymentDetailsResponse payment = paymentServiceClient.getPaymentDetails(resourceId);
            
            if (payment == null) {
                log.warn("Payment not found: {}", resourceId);
                return false;
            }
            
            // Check if user is sender or receiver
            boolean isSender = userId.equals(payment.getSenderId());
            boolean isReceiver = userId.equals(payment.getReceiverId());
            
            // Check if user has admin privileges
            boolean hasAdminRole = userHasRole(userId, "ADMIN") || userHasRole(userId, "SUPPORT");
            
            boolean hasAccess = isSender || isReceiver || hasAdminRole;
            
            if (!hasAccess) {
                log.warn("Payment ownership check failed: user={}, payment={}, sender={}, receiver={}", 
                        userId, resourceId, payment.getSenderId(), payment.getReceiverId());
            }
            
            return hasAccess;
            
        } catch (Exception e) {
            log.error("Error checking payment ownership: user={}, payment={}", userId, resourceId, e);
            // Fail secure - deny access on error
            return false;
        }
    }
    
    private boolean checkAccountOwnership(String userId, String resourceId) {
        log.debug("Checking account ownership: user={}, account={}", userId, resourceId);
        
        try {
            // First check wallet ownership
            Boolean isWalletOwner = walletServiceClient.checkWalletOwnership(userId, resourceId);
            if (Boolean.TRUE.equals(isWalletOwner)) {
                return true;
            }
            
            // Then check account ownership
            Boolean isAccountOwner = accountServiceClient.checkAccountOwnership(userId, resourceId);
            if (Boolean.TRUE.equals(isAccountOwner)) {
                return true;
            }
            
            // Check if user has admin/support role for override
            boolean hasAdminRole = userHasRole(userId, "ADMIN") || 
                                 userHasRole(userId, "SUPPORT") ||
                                 userHasRole(userId, "COMPLIANCE_OFFICER");
            
            if (!hasAdminRole) {
                log.warn("SECURITY: Account ownership check failed - user {} attempted to access account {}", 
                    userId, resourceId);
                auditSecurityViolation(userId, "UNAUTHORIZED_ACCOUNT_ACCESS", resourceId);
            }
            
            return hasAdminRole;
            
        } catch (Exception e) {
            log.error("SECURITY: Error checking account ownership for user {} and account {}", 
                userId, resourceId, e);
            auditSecurityViolation(userId, "ACCOUNT_OWNERSHIP_CHECK_ERROR", resourceId);
            return false;
        }
    }
    
    private boolean checkCardOwnership(String userId, String resourceId) {
        log.debug("Checking card ownership: user={}, card={}", userId, resourceId);
        
        try {
            // Check card ownership via card service
            Boolean isCardOwner = cardServiceClient.checkCardOwnership(userId, resourceId);
            if (Boolean.TRUE.equals(isCardOwner)) {
                return true;
            }
            
            // Check if user has admin/support role for override
            boolean hasAdminRole = userHasRole(userId, "ADMIN") || 
                                 userHasRole(userId, "SUPPORT") ||
                                 userHasRole(userId, "FRAUD_ANALYST");
            
            if (!hasAdminRole) {
                log.warn("SECURITY: Card ownership check failed - user {} attempted to access card {}", 
                    userId, resourceId);
                auditSecurityViolation(userId, "UNAUTHORIZED_CARD_ACCESS", resourceId);
            }
            
            return hasAdminRole;
            
        } catch (Exception e) {
            log.error("SECURITY: Error checking card ownership for user {} and card {}", 
                userId, resourceId, e);
            auditSecurityViolation(userId, "CARD_OWNERSHIP_CHECK_ERROR", resourceId);
            return false;
        }
    }
    
    private boolean checkBeneficiaryOwnership(String userId, String resourceId) {
        log.debug("Checking beneficiary ownership: user={}, beneficiary={}", userId, resourceId);
        
        try {
            // Beneficiaries are typically stored in payment or account service
            // Check via account service first
            AccountServiceClient.AccountDetailsResponse account = 
                accountServiceClient.getAccountDetails(resourceId);
                
            if (account != null && userId.equals(account.getUserId())) {
                return true;
            }
            
            // Check if user has admin/support role for override
            boolean hasAdminRole = userHasRole(userId, "ADMIN") || 
                                 userHasRole(userId, "SUPPORT") ||
                                 userHasRole(userId, "COMPLIANCE_OFFICER");
            
            if (!hasAdminRole) {
                log.warn("SECURITY: Beneficiary ownership check failed - user {} attempted to access beneficiary {}", 
                    userId, resourceId);
                auditSecurityViolation(userId, "UNAUTHORIZED_BENEFICIARY_ACCESS", resourceId);
            }
            
            return hasAdminRole;
            
        } catch (Exception e) {
            log.error("SECURITY: Error checking beneficiary ownership for user {} and beneficiary {}", 
                userId, resourceId, e);
            auditSecurityViolation(userId, "BENEFICIARY_OWNERSHIP_CHECK_ERROR", resourceId);
            return false;
        }
    }
    
    private boolean userHasRole(String userId, String role) {
        try {
            return userServiceClient.userHasRole(userId, role);
        } catch (Exception e) {
            log.error("Error checking user role: userId={}, role={}", userId, role, e);
            return false;
        }
    }
    
    private void auditSecurityViolation(String userId, String violationType, String resourceId) {
        if (auditEnabled) {
            try {
                auditService.auditSecurityEvent(
                    "SECURITY_VIOLATION",
                    userId,
                    violationType + " - User attempted to access resource they don't own",
                    Map.of(
                        "userId", userId,
                        "violationType", violationType,
                        "resourceId", resourceId,
                        "timestamp", LocalDateTime.now(),
                        "severity", "HIGH"
                    )
                );
            } catch (Exception e) {
                log.error("Failed to audit security violation", e);
            }
        }
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

    private boolean containsPotentialInjection(String value) {
        String lowerValue = value.toLowerCase();
        return lowerValue.contains("script") || 
               lowerValue.contains("javascript") ||
               lowerValue.contains("select") ||
               lowerValue.contains("union") ||
               lowerValue.contains("drop") ||
               lowerValue.contains("delete") ||
               lowerValue.contains("update") ||
               lowerValue.contains("insert") ||
               lowerValue.contains("exec") ||
               lowerValue.contains("xp_");
    }

    private String maskSensitiveValue(String value) {
        if (value == null || value.length() <= 4) {
            return "***";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }

    private void auditSecurityEvent(String eventType, String userId, String details) {
        if (auditEnabled && auditService != null) {
            try {
                auditService.logSecurityEvent(eventType, Map.of(
                    "userId", userId != null ? userId : "unknown",
                    "details", details != null ? details : "",
                    "timestamp", LocalDateTime.now(),
                    "severity", "INFO"
                ));
            } catch (Exception e) {
                log.error("Failed to audit security event", e);
            }
        }
    }

    private void auditSecurityViolation(String violationType, String userId, String details) {
        log.warn("SECURITY VIOLATION: {} - User: {} - Details: {}", violationType, userId, details);
        
        if (auditEnabled && auditService != null) {
            try {
                auditService.logSecurityEvent("SECURITY_VIOLATION", Map.of(
                    "violationType", violationType,
                    "userId", userId != null ? userId : "unknown",
                    "details", details != null ? details : "",
                    "timestamp", LocalDateTime.now(),
                    "severity", "CRITICAL"
                ));
            } catch (Exception e) {
                log.error("Failed to audit security violation", e);
            }
        }
    }

    // Data structures

    public static class SessionSecurityContext {
        private String sessionId;
        private String userId;
        private String clientIp;
        private String userAgent;
        private LocalDateTime createdAt;
        private LocalDateTime lastAccessedAt;
        private Set<String> authorities;

        private SessionSecurityContext(SessionSecurityContextBuilder builder) {
            this.sessionId = builder.sessionId;
            this.userId = builder.userId;
            this.clientIp = builder.clientIp;
            this.userAgent = builder.userAgent;
            this.createdAt = builder.createdAt;
            this.lastAccessedAt = builder.lastAccessedAt;
            this.authorities = builder.authorities;
        }

        public static SessionSecurityContextBuilder builder() {
            return new SessionSecurityContextBuilder();
        }

        // Getters and setters
        public String getSessionId() { return sessionId; }
        public String getUserId() { return userId; }
        public String getClientIp() { return clientIp; }
        public String getUserAgent() { return userAgent; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getLastAccessedAt() { return lastAccessedAt; }
        public void setLastAccessedAt(LocalDateTime lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }
        public Set<String> getAuthorities() { return authorities; }

        public static class SessionSecurityContextBuilder {
            private String sessionId;
            private String userId;
            private String clientIp;
            private String userAgent;
            private LocalDateTime createdAt;
            private LocalDateTime lastAccessedAt;
            private Set<String> authorities;

            public SessionSecurityContextBuilder sessionId(String sessionId) {
                this.sessionId = sessionId;
                return this;
            }

            public SessionSecurityContextBuilder userId(String userId) {
                this.userId = userId;
                return this;
            }

            public SessionSecurityContextBuilder clientIp(String clientIp) {
                this.clientIp = clientIp;
                return this;
            }

            public SessionSecurityContextBuilder userAgent(String userAgent) {
                this.userAgent = userAgent;
                return this;
            }

            public SessionSecurityContextBuilder createdAt(LocalDateTime createdAt) {
                this.createdAt = createdAt;
                return this;
            }

            public SessionSecurityContextBuilder lastAccessedAt(LocalDateTime lastAccessedAt) {
                this.lastAccessedAt = lastAccessedAt;
                return this;
            }

            public SessionSecurityContextBuilder authorities(Set<String> authorities) {
                this.authorities = authorities;
                return this;
            }

            public SessionSecurityContext build() {
                return new SessionSecurityContext(this);
            }
        }
    }
}