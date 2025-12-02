/**
 * SECURITY ENHANCEMENT: Admin Operation Validator
 * Provides additional authorization checks for sensitive administrative operations
 */
package com.waqiti.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SECURITY-FOCUSED validator for administrative operations
 * Implements additional security layers beyond standard Spring Security authorization
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminOperationValidator {
    
    private final SecurityAuditService securityAuditService;
    
    // Track admin operations to detect suspicious patterns
    private final Map<UUID, List<AdminOperationRecord>> userOperationHistory = new ConcurrentHashMap<>();
    
    // Rate limiting for admin operations
    private final Map<UUID, LocalDateTime> lastOperationTime = new ConcurrentHashMap<>();
    
    private static final int MAX_ADMIN_OPERATIONS_PER_MINUTE = 10;
    private static final int MAX_ADMIN_OPERATIONS_PER_HOUR = 50;
    
    /**
     * SECURITY FIX: Validate admin operations with enhanced security checks
     */
    public void validateAdminOperation(UUID userId, String operationType, String userRole, String resourceId) {
        log.info("SECURITY: Validating admin operation - User: {}, Operation: {}, Role: {}, Resource: {}", 
                userId, operationType, userRole, resourceId);
        
        // SECURITY CHECK 1: Verify user role permissions
        validateRolePermissions(userId, operationType, userRole);
        
        // SECURITY CHECK 2: Rate limiting for admin operations
        validateRateLimiting(userId, operationType);
        
        // SECURITY CHECK 3: Check for suspicious operation patterns
        validateOperationPattern(userId, operationType);
        
        // SECURITY CHECK 4: Validate business hours (if required)
        validateBusinessHours(userId, operationType);
        
        // SECURITY CHECK 5: Check for concurrent admin sessions
        validateConcurrentSessions(userId);
        
        // Record the operation for audit trail
        recordAdminOperation(userId, operationType, userRole, resourceId);
        
        log.info("SECURITY: Admin operation validation passed for user {} operation {}", userId, operationType);
    }
    
    /**
     * SECURITY FIX: Validate export operations with enhanced checks
     */
    public void validateExportOperation(UUID userId, UUID resourceId, String operationType, String userRole) {
        log.info("SECURITY: Validating export operation - User: {}, Resource: {}, Operation: {}, Role: {}", 
                userId, resourceId, operationType, userRole);
        
        // Enhanced validation for export operations
        validateAdminOperation(userId, operationType, userRole, resourceId.toString());
        
        // Additional checks specific to data export
        validateDataExportPermissions(userId, resourceId, userRole);
        
        log.info("SECURITY: Export operation validation passed for user {} resource {}", userId, resourceId);
    }
    
    /**
     * SECURITY CHECK 1: Validate role permissions
     */
    private void validateRolePermissions(UUID userId, String operationType, String userRole) {
        if (!hasRequiredRole(userRole, operationType)) {
            log.warn("SECURITY: Insufficient role permissions - User: {}, Role: {}, Operation: {}", 
                    userId, userRole, operationType);
            throw new SecurityException("Insufficient permissions for operation: " + operationType);
        }
        
        // Additional check for super-admin operations
        if (isSuperAdminOperation(operationType) && !userRole.equals("SUPER_ADMIN")) {
            log.warn("SECURITY: Super admin operation attempted by non-super-admin user: {}", userId);
            throw new SecurityException("Super admin privileges required for this operation");
        }
    }
    
    /**
     * SECURITY CHECK 2: Rate limiting validation
     */
    private void validateRateLimiting(UUID userId, String operationType) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastOperation = lastOperationTime.get(userId);
        
        if (lastOperation != null) {
            long secondsSinceLastOperation = ChronoUnit.SECONDS.between(lastOperation, now);
            
            // Minimum 6 seconds between admin operations (prevents automation abuse)
            if (secondsSinceLastOperation < 6) {
                log.warn("SECURITY: Rate limit exceeded - User: {}, Operation: {}, Time since last: {}s", 
                        userId, operationType, secondsSinceLastOperation);
                throw new SecurityException("Rate limit exceeded. Please wait before performing another admin operation.");
            }
        }
        
        // Check operations per minute and hour
        List<AdminOperationRecord> userOperations = userOperationHistory.get(userId);
        if (userOperations != null) {
            long operationsLastMinute = userOperations.stream()
                .filter(op -> ChronoUnit.MINUTES.between(op.timestamp, now) < 1)
                .count();
            
            long operationsLastHour = userOperations.stream()
                .filter(op -> ChronoUnit.HOURS.between(op.timestamp, now) < 1)
                .count();
            
            if (operationsLastMinute >= MAX_ADMIN_OPERATIONS_PER_MINUTE) {
                log.warn("SECURITY: Too many admin operations per minute - User: {}, Count: {}", 
                        userId, operationsLastMinute);
                throw new SecurityException("Too many admin operations. Please wait before continuing.");
            }
            
            if (operationsLastHour >= MAX_ADMIN_OPERATIONS_PER_HOUR) {
                log.warn("SECURITY: Too many admin operations per hour - User: {}, Count: {}", 
                        userId, operationsLastHour);
                throw new SecurityException("Hourly admin operation limit exceeded. Please contact support.");
            }
        }
        
        lastOperationTime.put(userId, now);
    }
    
    /**
     * SECURITY CHECK 3: Validate operation patterns for suspicious activity
     */
    private void validateOperationPattern(UUID userId, String operationType) {
        List<AdminOperationRecord> userOperations = userOperationHistory.get(userId);
        if (userOperations != null && userOperations.size() > 5) {
            LocalDateTime now = LocalDateTime.now();
            
            // Check for repeated identical operations (potential automation)
            long recentIdenticalOperations = userOperations.stream()
                .filter(op -> ChronoUnit.MINUTES.between(op.timestamp, now) < 5)
                .filter(op -> op.operationType.equals(operationType))
                .count();
            
            if (recentIdenticalOperations > 3) {
                log.warn("SECURITY: Suspicious operation pattern detected - User: {}, Operation: {}, Count: {}", 
                        userId, operationType, recentIdenticalOperations);
                throw new SecurityException("Suspicious operation pattern detected. Operation blocked for security.");
            }
        }
    }
    
    /**
     * SECURITY CHECK 4: Validate business hours (for highly sensitive operations)
     */
    private void validateBusinessHours(UUID userId, String operationType) {
        if (isHighlySecureOperation(operationType)) {
            LocalDateTime now = LocalDateTime.now();
            int hour = now.getHour();
            int dayOfWeek = now.getDayOfWeek().getValue();
            
            // Allow only business hours (9 AM - 6 PM, Monday-Friday) for critical operations
            if (dayOfWeek > 5 || hour < 9 || hour > 18) {
                log.warn("SECURITY: Highly secure operation attempted outside business hours - User: {}, Operation: {}", 
                        userId, operationType);
                throw new SecurityException("This operation is only allowed during business hours (9 AM - 6 PM, Monday-Friday)");
            }
        }
    }
    
    /**
     * SECURITY CHECK 5: Validate concurrent admin sessions
     */
    private void validateConcurrentSessions(UUID userId) {
        int activeSessions = SecurityContextUtil.getActiveSessionCount(userId);
        if (activeSessions > 2) {
            log.warn("SECURITY: Too many concurrent admin sessions - User: {}, Sessions: {}", 
                    userId, activeSessions);
            throw new SecurityException("Too many concurrent admin sessions. Please close other sessions.");
        }
    }
    
    /**
     * Additional validation for data export operations
     */
    private void validateDataExportPermissions(UUID userId, UUID resourceId, String userRole) {
        // Check if user has specific permission to export this type of data
        if (!hasDataExportPermission(userId, resourceId, userRole)) {
            log.warn("SECURITY: Data export permission denied - User: {}, Resource: {}, Role: {}", 
                    userId, resourceId, userRole);
            throw new SecurityException("Data export permission denied for this resource");
        }
        
        // Additional check for sensitive data export
        if (isSensitiveDataExport(resourceId)) {
            log.info("SECURITY: Sensitive data export attempted - User: {}, Resource: {}", userId, resourceId);
            // Could add additional approval workflow here
        }
    }
    
    /**
     * Record admin operation for audit trail
     */
    private void recordAdminOperation(UUID userId, String operationType, String userRole, String resourceId) {
        AdminOperationRecord record = new AdminOperationRecord(
            LocalDateTime.now(),
            operationType,
            userRole,
            resourceId,
            SecurityContextUtil.getClientIp(),
            SecurityContextUtil.getUserAgent()
        );
        
        userOperationHistory.computeIfAbsent(userId, k -> new java.util.ArrayList<>()).add(record);
        
        // Keep only recent operations (last 24 hours)
        userOperationHistory.get(userId).removeIf(op -> 
            ChronoUnit.HOURS.between(op.timestamp, LocalDateTime.now()) > 24);
        
        // Send to audit service
        securityAuditService.recordAdminOperation(userId, record);
    }
    
    /**
     * Helper methods for security checks
     */
    private boolean hasRequiredRole(String userRole, String operationType) {
        return switch (operationType) {
            case "RECONCILIATION_RULE_CREATE", "RECONCILIATION_RULE_UPDATE" -> 
                userRole.equals("ADMIN") || userRole.equals("SUPER_ADMIN");
            case "RECONCILIATION_EXPORT" -> 
                userRole.equals("ADMIN") || userRole.equals("AUDITOR") || userRole.equals("RECONCILER");
            default -> userRole.equals("ADMIN") || userRole.equals("SUPER_ADMIN");
        };
    }
    
    private boolean isSuperAdminOperation(String operationType) {
        return operationType.contains("DELETE") || operationType.contains("PURGE") || operationType.equals("SYSTEM_CONFIG");
    }
    
    private boolean isHighlySecureOperation(String operationType) {
        return operationType.contains("DELETE") || operationType.contains("RULE_CREATE") || operationType.contains("CONFIG");
    }
    
    private boolean hasDataExportPermission(UUID userId, UUID resourceId, String userRole) {
        // In production, this would check against a permissions database
        return userRole.equals("ADMIN") || userRole.equals("AUDITOR") || userRole.equals("RECONCILER");
    }
    
    private boolean isSensitiveDataExport(UUID resourceId) {
        // In production, this would check the data classification of the resource
        return true; // All reconciliation data is considered sensitive
    }
    
    /**
     * Record structure for admin operations
     */
    private static class AdminOperationRecord {
        public final LocalDateTime timestamp;
        public final String operationType;
        public final String userRole;
        public final String resourceId;
        public final String clientIp;
        public final String userAgent;
        
        public AdminOperationRecord(LocalDateTime timestamp, String operationType, String userRole, 
                                  String resourceId, String clientIp, String userAgent) {
            this.timestamp = timestamp;
            this.operationType = operationType;
            this.userRole = userRole;
            this.resourceId = resourceId;
            this.clientIp = clientIp;
            this.userAgent = userAgent;
        }
    }
}