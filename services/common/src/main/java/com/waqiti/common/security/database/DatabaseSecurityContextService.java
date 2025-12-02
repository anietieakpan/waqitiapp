package com.waqiti.common.security.database;

import com.waqiti.common.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.UUID;

/**
 * CRITICAL SECURITY: Database Security Context Service for Row-Level Security (RLS)
 * 
 * This service manages the database session variables that RLS policies depend on.
 * It ensures that users can only access their own data at the database level,
 * providing defense-in-depth security even if application logic is compromised.
 * 
 * Required for PCI DSS, SOC 2, and financial services compliance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseSecurityContextService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * CRITICAL: Set complete database security context for authenticated user
     * This must be called at the start of every database transaction involving user data
     */
    @Transactional
    public void setUserSecurityContext(UUID userId, String sessionId, Principal principal) {
        log.debug("SECURITY: Setting database security context for user: {}", userId);
        
        try {
            // Set current user ID for RLS policies
            jdbcTemplate.queryForObject("SELECT set_config('app.current_user_id', ?, false)", String.class, userId.toString());
            
            // Set session ID for audit trail
            if (sessionId != null) {
                jdbcTemplate.queryForObject("SELECT set_config('app.session_id', ?, false)", String.class, sessionId);
            }
            
            // Extract and set user role from principal
            String userRole = extractUserRole(principal);
            if (userRole != null) {
                jdbcTemplate.queryForObject("SELECT set_config('app.user_role', ?, false)", String.class, userRole);
            }
            
            // Set client IP address for audit trail
            String clientIp = extractClientIpAddress();
            if (clientIp != null) {
                jdbcTemplate.queryForObject("SELECT set_config('app.client_ip', ?, false)", String.class, clientIp);
            }
            
            // Set user agent for audit trail
            String userAgent = extractUserAgent();
            if (userAgent != null) {
                jdbcTemplate.queryForObject("SELECT set_config('app.user_agent', ?, false)", String.class, sanitizeUserAgent(userAgent));
            }
            
            // Set transaction ID for correlation
            String transactionId = UUID.randomUUID().toString();
            jdbcTemplate.queryForObject("SELECT set_config('app.transaction_id', ?, false)", String.class, transactionId);
            
            // Set context timestamp
            jdbcTemplate.execute("SELECT set_config('app.context_set_at', NOW()::TEXT, false)");
            
            log.info("SECURITY: Database security context successfully set for user: {} with transaction: {}", 
                    userId, transactionId);
            
        } catch (DataAccessException e) {
            log.error("CRITICAL: Failed to set database security context for user: {}", userId, e);
            throw new DatabaseSecurityContextException("Failed to set security context", e);
        }
    }

    /**
     * CRITICAL: Set admin security context with elevated privileges
     * Only for authenticated admin users with verified admin roles
     */
    @Transactional
    public void setAdminSecurityContext(UUID adminUserId, String sessionId, String adminRole) {
        log.warn("SECURITY: Setting ADMIN database security context for user: {} with role: {}", 
                adminUserId, adminRole);
        
        try {
            // Verify admin role is legitimate
            if (!isValidAdminRole(adminRole)) {
                throw new IllegalArgumentException("Invalid admin role: " + adminRole);
            }
            
            // Set admin context
            jdbcTemplate.queryForObject("SELECT set_config('app.current_user_id', ?, false)", String.class, adminUserId.toString());
            jdbcTemplate.queryForObject("SELECT set_config('app.user_role', ?, false)", String.class, adminRole);
            jdbcTemplate.execute("SELECT set_config('app.admin_context', 'true', false)");
            
            if (sessionId != null) {
                jdbcTemplate.queryForObject("SELECT set_config('app.session_id', ?, false)", String.class, sessionId);
            }
            
            // Enhanced audit for admin access
            String clientIp = extractClientIpAddress();
            if (clientIp != null) {
                jdbcTemplate.queryForObject("SELECT set_config('app.client_ip', ?, false)", String.class, clientIp);
            }
            
            jdbcTemplate.execute("SELECT set_config('app.context_set_at', NOW()::TEXT, false)");
            
            log.warn("SECURITY: Admin database context set for user: {} with role: {}", adminUserId, adminRole);
            
        } catch (DataAccessException e) {
            log.error("CRITICAL: Failed to set admin security context for user: {}", adminUserId, e);
            throw new DatabaseSecurityContextException("Failed to set admin security context", e);
        }
    }

    /**
     * CRITICAL: Set system service context for inter-service communication
     */
    @Transactional
    public void setSystemServiceContext(String serviceName, UUID correlationId) {
        log.debug("SECURITY: Setting system service context for service: {}", serviceName);
        
        try {
            // Set service context
            jdbcTemplate.queryForObject("SELECT set_config('app.service_name', ?, false)", String.class, serviceName);
            jdbcTemplate.execute("SELECT set_config('app.user_role', 'SYSTEM_SERVICE', false)");
            jdbcTemplate.execute("SELECT set_config('app.system_context', 'true', false)");
            
            if (correlationId != null) {
                jdbcTemplate.queryForObject("SELECT set_config('app.correlation_id', ?, false)", String.class, correlationId.toString());
            }
            
            jdbcTemplate.execute("SELECT set_config('app.context_set_at', NOW()::TEXT, false)");
            
            log.debug("SECURITY: System service context set for: {}", serviceName);
            
        } catch (DataAccessException e) {
            log.error("CRITICAL: Failed to set system service context for: {}", serviceName, e);
            throw new DatabaseSecurityContextException("Failed to set service context", e);
        }
    }

    /**
     * CRITICAL: Clear database security context (should be called at transaction end)
     */
    @Transactional
    public void clearSecurityContext() {
        log.debug("SECURITY: Clearing database security context");
        
        try {
            // Clear all app-specific session variables
            jdbcTemplate.execute("SELECT set_config('app.current_user_id', '', false)");
            jdbcTemplate.execute("SELECT set_config('app.user_role', '', false)");
            jdbcTemplate.execute("SELECT set_config('app.session_id', '', false)");
            jdbcTemplate.execute("SELECT set_config('app.client_ip', '', false)");
            jdbcTemplate.execute("SELECT set_config('app.user_agent', '', false)");
            jdbcTemplate.execute("SELECT set_config('app.transaction_id', '', false)");
            jdbcTemplate.execute("SELECT set_config('app.admin_context', 'false', false)");
            jdbcTemplate.execute("SELECT set_config('app.system_context', 'false', false)");
            jdbcTemplate.execute("SELECT set_config('app.service_name', '', false)");
            jdbcTemplate.execute("SELECT set_config('app.correlation_id', '', false)");
            
            log.debug("SECURITY: Database security context cleared");
            
        } catch (DataAccessException e) {
            log.warn("SECURITY: Failed to clear database security context", e);
            // Don't throw exception as this is cleanup - log and continue
        }
    }

    /**
     * CRITICAL: Verify current security context is properly set
     */
    public DatabaseSecurityContext getCurrentSecurityContext() {
        try {
            String userId = jdbcTemplate.queryForObject("SELECT current_setting('app.current_user_id', true)", String.class);
            String userRole = jdbcTemplate.queryForObject("SELECT current_setting('app.user_role', true)", String.class);
            String sessionId = jdbcTemplate.queryForObject("SELECT current_setting('app.session_id', true)", String.class);
            String clientIp = jdbcTemplate.queryForObject("SELECT current_setting('app.client_ip', true)", String.class);
            String transactionId = jdbcTemplate.queryForObject("SELECT current_setting('app.transaction_id', true)", String.class);
            String isAdmin = jdbcTemplate.queryForObject("SELECT current_setting('app.admin_context', true)", String.class);
            String isSystem = jdbcTemplate.queryForObject("SELECT current_setting('app.system_context', true)", String.class);
            String contextSetAt = jdbcTemplate.queryForObject("SELECT current_setting('app.context_set_at', true)", String.class);
            
            return DatabaseSecurityContext.builder()
                    .userId(userId != null && !userId.isEmpty() ? UUID.fromString(userId) : null)
                    .userRole(userRole)
                    .sessionId(sessionId)
                    .clientIp(clientIp)
                    .transactionId(transactionId)
                    .isAdminContext("true".equals(isAdmin))
                    .isSystemContext("true".equals(isSystem))
                    .contextSetAt(contextSetAt)
                    .build();
                    
        } catch (Exception e) {
            log.warn("SECURITY: Failed to retrieve current security context", e);
            return DatabaseSecurityContext.builder().build();
        }
    }

    /**
     * CRITICAL: Validate that security context is properly set before sensitive operations
     */
    public void validateSecurityContextForOperation(String operationType) {
        DatabaseSecurityContext context = getCurrentSecurityContext();
        
        if (context.getUserId() == null && !context.isSystemContext()) {
            throw new SecurityContextNotSetException(
                "Security context not set for operation: " + operationType);
        }
        
        if (context.getUserRole() == null) {
            throw new SecurityContextNotSetException(
                "User role not set in security context for operation: " + operationType);
        }
        
        log.debug("SECURITY: Security context validated for operation: {} by user: {} with role: {}", 
                operationType, context.getUserId(), context.getUserRole());
    }

    /**
     * Enhanced security context for financial operations
     */
    @Transactional
    public void setFinancialOperationContext(UUID userId, String operationType, 
                                           UUID transactionId, String sessionId) {
        log.info("SECURITY: Setting enhanced financial operation context for user: {}, operation: {}", 
                userId, operationType);
        
        // Set standard context
        setUserSecurityContext(userId, sessionId, SecurityContextUtil.getCurrentPrincipal());
        
        try {
            // Add financial operation specific context
            jdbcTemplate.queryForObject("SELECT set_config('app.operation_type', ?, false)", String.class, operationType);
            jdbcTemplate.queryForObject("SELECT set_config('app.financial_transaction_id', ?, false)", String.class, transactionId.toString());
            jdbcTemplate.execute("SELECT set_config('app.financial_context', 'true', false)");
            jdbcTemplate.execute("SELECT set_config('app.enhanced_logging', 'true', false)");
            
            log.info("SECURITY: Enhanced financial context set for transaction: {}", transactionId);
            
        } catch (DataAccessException e) {
            log.error("CRITICAL: Failed to set financial operation context", e);
            throw new DatabaseSecurityContextException("Failed to set financial context", e);
        }
    }

    /**
     * Private helper methods
     */
    private String extractUserRole(Principal principal) {
        if (principal == null) return null;
        
        // Extract role from JWT token or authentication context
        try {
            return SecurityContextUtil.getCurrentUserRole();
        } catch (Exception e) {
            log.warn("SECURITY: Failed to extract user role from principal", e);
            return "USER"; // Default role
        }
    }

    private String extractClientIpAddress() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                
                // Check for forwarded IP first (load balancer/proxy)
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
        } catch (Exception e) {
            log.debug("SECURITY: Could not extract client IP address", e);
        }
        return null;
    }

    private String extractUserAgent() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getHeader("User-Agent");
            }
        } catch (Exception e) {
            log.debug("SECURITY: Could not extract user agent", e);
        }
        return null;
    }

    private String sanitizeUserAgent(String userAgent) {
        if (userAgent == null) return null;
        
        // Remove potential SQL injection characters and limit length
        return userAgent.replaceAll("[';\"\\\\]", "")
                      .substring(0, Math.min(userAgent.length(), 500));
    }

    private boolean isValidAdminRole(String role) {
        return role != null && (
            role.equals("ADMIN") ||
            role.equals("SUPER_ADMIN") ||
            role.equals("SYSTEM_ADMIN") ||
            role.equals("COMPLIANCE_OFFICER") ||
            role.equals("AUDIT_ADMIN")
        );
    }

    /**
     * DTOs and Exceptions
     */
    @lombok.Builder
    @lombok.Data
    public static class DatabaseSecurityContext {
        private UUID userId;
        private String userRole;
        private String sessionId;
        private String clientIp;
        private String transactionId;
        private boolean isAdminContext;
        private boolean isSystemContext;
        private String contextSetAt;
    }

    public static class DatabaseSecurityContextException extends RuntimeException {
        public DatabaseSecurityContextException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class SecurityContextNotSetException extends RuntimeException {
        public SecurityContextNotSetException(String message) {
            super(message);
        }
    }
}