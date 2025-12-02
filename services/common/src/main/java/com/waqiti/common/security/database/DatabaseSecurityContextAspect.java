package com.waqiti.common.security.database;

import com.waqiti.common.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.UUID;

/**
 * CRITICAL SECURITY: Database Security Context Aspect
 * 
 * Automatically sets database security context for all transactional methods
 * to ensure Row-Level Security (RLS) policies work correctly.
 * 
 * This aspect runs before all @Transactional methods to establish the
 * required session variables that RLS policies depend on.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1) // Execute before transaction management
public class DatabaseSecurityContextAspect {

    private final DatabaseSecurityContextService contextService;

    /**
     * CRITICAL: Intercept all @Transactional methods to set security context
     */
    @Around("@annotation(org.springframework.transaction.annotation.Transactional) || " +
            "@within(org.springframework.transaction.annotation.Transactional)")
    public Object setSecurityContextForTransactionalMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        
        try {
            // Check if we already have a security context set (avoid nested calls)
            DatabaseSecurityContextService.DatabaseSecurityContext existingContext = 
                    contextService.getCurrentSecurityContext();
                    
            boolean contextAlreadySet = existingContext.getUserId() != null || 
                                      existingContext.isSystemContext();
            
            if (!contextAlreadySet) {
                // Set security context based on current authentication
                setAppropriateSecurityContext(methodName);
            }
            
            // Proceed with the method execution
            return joinPoint.proceed();
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to set database security context for method: {}", methodName, e);
            
            // For security-critical methods, fail fast
            if (isSecurityCriticalMethod(methodName)) {
                throw new DatabaseSecurityContextService.SecurityContextNotSetException(
                    "Security context required for method: " + methodName);
            }
            
            // For non-critical methods, proceed but log warning
            log.warn("SECURITY: Proceeding without security context for non-critical method: {}", methodName);
            return joinPoint.proceed();
        }
    }

    /**
     * CRITICAL: Intercept financial transaction methods with enhanced context
     */
    @Around("@annotation(com.waqiti.common.security.database.RequiresFinancialContext)")
    public Object setFinancialSecurityContext(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        
        log.info("SECURITY: Setting enhanced financial context for method: {}", methodName);
        
        try {
            // Extract financial context from method parameters or context
            UUID userId = SecurityContextUtil.getCurrentUserId();
            String sessionId = SecurityContextUtil.getCurrentSessionId();
            
            if (userId != null) {
                // Generate transaction ID for this financial operation
                UUID transactionId = UUID.randomUUID();
                
                contextService.setFinancialOperationContext(
                    userId, 
                    extractOperationType(methodName), 
                    transactionId,
                    sessionId
                );
                
                log.info("SECURITY: Enhanced financial context set for user: {}, transaction: {}", 
                        userId, transactionId);
            }
            
            return joinPoint.proceed();
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to set financial security context for method: {}", methodName, e);
            throw e;
        }
    }

    /**
     * CRITICAL: Intercept admin methods with elevated context
     */
    @Around("@annotation(com.waqiti.common.security.database.RequiresAdminContext)")
    public Object setAdminSecurityContext(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        
        log.warn("SECURITY: Setting admin context for method: {}", methodName);
        
        try {
            UUID adminUserId = SecurityContextUtil.getCurrentUserId();
            String adminRole = SecurityContextUtil.getCurrentUserRole();
            String sessionId = SecurityContextUtil.getCurrentSessionId();
            
            if (adminUserId != null && adminRole != null) {
                contextService.setAdminSecurityContext(adminUserId, sessionId, adminRole);
                
                log.warn("SECURITY: Admin context set for user: {} with role: {}", adminUserId, adminRole);
            } else {
                throw new SecurityException("Admin context required but user not authenticated as admin");
            }
            
            return joinPoint.proceed();
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to set admin security context for method: {}", methodName, e);
            throw e;
        }
    }

    /**
     * Private helper methods
     */
    private void setAppropriateSecurityContext(String methodName) {
        try {
            UUID currentUserId = SecurityContextUtil.getCurrentUserId();
            String currentUserRole = SecurityContextUtil.getCurrentUserRole();
            String sessionId = SecurityContextUtil.getCurrentSessionId();
            Principal principal = SecurityContextUtil.getCurrentPrincipal();
            
            if (currentUserId != null) {
                // Regular user context
                contextService.setUserSecurityContext(currentUserId, sessionId, principal);
                
                log.debug("SECURITY: User context set for method: {}, user: {}", methodName, currentUserId);
                
            } else if (isSystemServiceContext()) {
                // System service context (inter-service communication)
                String serviceName = extractServiceName();
                UUID correlationId = UUID.randomUUID();
                
                contextService.setSystemServiceContext(serviceName, correlationId);
                
                log.debug("SECURITY: System service context set for method: {}, service: {}", 
                         methodName, serviceName);
                
            } else {
                log.warn("SECURITY: No valid authentication context found for method: {}", methodName);
            }
            
        } catch (Exception e) {
            log.error("SECURITY: Failed to determine appropriate security context for method: {}", 
                     methodName, e);
            throw e;
        }
    }

    private boolean isSecurityCriticalMethod(String methodName) {
        String lowerMethodName = methodName.toLowerCase();
        
        return lowerMethodName.contains("wallet") ||
               lowerMethodName.contains("transaction") ||
               lowerMethodName.contains("payment") ||
               lowerMethodName.contains("transfer") ||
               lowerMethodName.contains("deposit") ||
               lowerMethodName.contains("withdraw") ||
               lowerMethodName.contains("balance") ||
               lowerMethodName.contains("kyc") ||
               lowerMethodName.contains("compliance") ||
               lowerMethodName.contains("audit") ||
               lowerMethodName.contains("financial");
    }

    private String extractOperationType(String methodName) {
        String lowerMethodName = methodName.toLowerCase();
        
        if (lowerMethodName.contains("transfer")) return "TRANSFER";
        if (lowerMethodName.contains("deposit")) return "DEPOSIT";
        if (lowerMethodName.contains("withdraw")) return "WITHDRAWAL";
        if (lowerMethodName.contains("payment")) return "PAYMENT";
        if (lowerMethodName.contains("create")) return "CREATE";
        if (lowerMethodName.contains("update")) return "UPDATE";
        if (lowerMethodName.contains("delete")) return "DELETE";
        
        return "GENERAL_OPERATION";
    }

    private boolean isSystemServiceContext() {
        try {
            // Check if we're in a system service context (no user authentication)
            return SecurityContextUtil.getCurrentUserId() == null && 
                   SecurityContextUtil.getCurrentPrincipal() == null;
        } catch (Exception e) {
            return false;
        }
    }

    private String extractServiceName() {
        // Extract service name from application context or configuration
        String serviceName = System.getProperty("spring.application.name", "unknown-service");
        return serviceName.toLowerCase().replace(" ", "-");
    }
}