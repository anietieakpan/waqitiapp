package com.waqiti.security.rbac.aspect;

import com.waqiti.security.rbac.annotation.RequiresPermission;
import com.waqiti.security.rbac.service.RBACService;
import com.waqiti.security.rbac.exception.InsufficientPermissionException;
import com.waqiti.common.security.authorization.ResourceOwnershipValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

/**
 * AOP aspect for enforcing RBAC permissions on annotated methods.
 * Intercepts method calls and validates user permissions before execution.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionAspect {

    private final RBACService rbacService;
    private final ResourceOwnershipValidator ownershipValidator;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * Intercepts methods annotated with @RequiresPermission
     */
    @Around("@annotation(requiresPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequiresPermission requiresPermission) throws Throwable {
        log.debug("Checking permissions for method: {}", joinPoint.getSignature().getName());
        
        try {
            // Get current user
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                throw new InsufficientPermissionException("User not authenticated");
            }
            
            UUID userId = extractUserId(authentication);
            if (userId == null) {
                throw new InsufficientPermissionException("Could not extract user ID from authentication");
            }
            
            // Check permissions
            boolean hasPermission = checkUserPermissions(userId, requiresPermission, joinPoint);
            
            if (!hasPermission) {
                if (requiresPermission.failSilently()) {
                    log.warn("Permission denied (failing silently): userId={}, method={}", 
                            userId, joinPoint.getSignature().getName());
                    return getDefaultReturnValue(joinPoint);
                } else {
                    throw new InsufficientPermissionException(requiresPermission.message());
                }
            }
            
            log.debug("Permission check passed for user: {}", userId);
            return joinPoint.proceed();
            
        } catch (InsufficientPermissionException e) {
            log.warn("Permission denied: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error during permission check", e);
            throw new InsufficientPermissionException("Permission check failed: " + e.getMessage());
        }
    }

    /**
     * Checks if user has required permissions
     */
    private boolean checkUserPermissions(UUID userId, RequiresPermission requiresPermission, 
                                       ProceedingJoinPoint joinPoint) {
        
        // Check 'allOf' permissions (user must have all)
        if (requiresPermission.allOf().length > 0) {
            for (String permission : requiresPermission.allOf()) {
                String resolvedPermission = resolvePermission(permission, requiresPermission, joinPoint);
                if (!rbacService.hasPermission(userId, resolvedPermission)) {
                    log.debug("User {} missing required permission: {}", userId, resolvedPermission);
                    return false;
                }
            }
        }
        
        // Check 'value' permissions (user must have at least one)
        if (requiresPermission.value().length > 0) {
            boolean hasAnyPermission = Arrays.stream(requiresPermission.value())
                    .map(permission -> resolvePermission(permission, requiresPermission, joinPoint))
                    .anyMatch(permission -> rbacService.hasPermission(userId, permission));
            
            if (!hasAnyPermission) {
                log.debug("User {} missing any of required permissions: {}", userId, 
                        Arrays.toString(requiresPermission.value()));
                return false;
            }
        }
        
        // Check owner permission if enabled
        if (requiresPermission.allowOwner()) {
            return checkOwnerPermission(userId, requiresPermission, joinPoint);
        }
        
        return true;
    }

    /**
     * Resolves permission string with resource information
     */
    private String resolvePermission(String permission, RequiresPermission requiresPermission, 
                                   ProceedingJoinPoint joinPoint) {
        
        // If no resource specified, return permission as-is
        if (requiresPermission.resource().isEmpty()) {
            return permission;
        }
        
        // Extract resource ID if specified
        String resourceId = null;
        if (!requiresPermission.resourceId().isEmpty()) {
            resourceId = extractResourceId(requiresPermission.resourceId(), joinPoint);
        }
        
        // Build full permission string
        StringBuilder fullPermission = new StringBuilder();
        fullPermission.append(requiresPermission.resource()).append(":");
        fullPermission.append(permission);
        
        if (resourceId != null) {
            fullPermission.append(":").append(resourceId);
        }
        
        return fullPermission.toString();
    }

    /**
     * Extracts resource ID using SpEL expression
     */
    private String extractResourceId(String resourceIdExpression, ProceedingJoinPoint joinPoint) {
        try {
            EvaluationContext context = createEvaluationContext(joinPoint);
            Expression expression = expressionParser.parseExpression(resourceIdExpression);
            Object result = expression.getValue(context);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            log.warn("Failed to extract resource ID using expression: {}", resourceIdExpression, e);
            return null;
        }
    }

    /**
     * Creates SpEL evaluation context with method parameters
     */
    private EvaluationContext createEvaluationContext(ProceedingJoinPoint joinPoint) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // Add method parameters to context
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();
        
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            context.setVariable(parameters[i].getName(), args[i]);
        }
        
        // Add authentication context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            context.setVariable("currentUser", auth.getPrincipal());
            context.setVariable("userId", extractUserId(auth));
        }
        
        return context;
    }

    /**
     * Enhanced owner permission checking with proper resource ownership validation
     * Addresses Critical Security Vulnerability: Authorization Bypass
     */
    private boolean checkOwnerPermission(UUID userId, RequiresPermission requiresPermission, 
                                       ProceedingJoinPoint joinPoint) {
        
        String resourceType = requiresPermission.resource();
        String resourceId = extractResourceId(requiresPermission.resourceId(), joinPoint);
        
        if (resourceId == null) {
            // Try to find resource ID from common parameter names
            resourceId = extractResourceIdFromCommonParams(joinPoint);
        }
        
        if (resourceId == null) {
            log.warn("Could not extract resource ID for ownership check");
            return false;
        }
        
        // If no resource type specified, fall back to simple ownership check (legacy)
        if (resourceType == null || resourceType.isEmpty()) {
            try {
                UUID ownerUUID = UUID.fromString(resourceId);
                return userId.equals(ownerUUID);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID format for resource owner: {}", resourceId);
                return false;
            }
        }
        
        // Enhanced validation using actual database queries
        try {
            boolean hasOwnership;
            
            switch (resourceType.toUpperCase()) {
                case "WALLET":
                    hasOwnership = ownershipValidator.isWalletOwner(userId, UUID.fromString(resourceId));
                    break;
                case "PAYMENT":
                    hasOwnership = ownershipValidator.isPaymentParticipant(userId, UUID.fromString(resourceId));
                    break;
                case "TRANSACTION":
                    hasOwnership = ownershipValidator.isTransactionParticipant(userId, UUID.fromString(resourceId));
                    break;
                case "ACCOUNT":
                    hasOwnership = ownershipValidator.isAccountOwner(userId, resourceId);
                    break;
                case "NOTIFICATION":
                    hasOwnership = ownershipValidator.isNotificationRecipient(userId, UUID.fromString(resourceId));
                    break;
                case "LEDGER":
                case "LEDGER_ENTRY":
                    hasOwnership = ownershipValidator.hasLedgerAccess(userId, UUID.fromString(resourceId));
                    break;
                case "FAMILY_ACCOUNT":
                    hasOwnership = ownershipValidator.isFamilyAccountMember(userId, UUID.fromString(resourceId));
                    break;
                case "BUSINESS":
                    hasOwnership = ownershipValidator.hasBusinessAccountAccess(userId, UUID.fromString(resourceId), "VIEWER");
                    break;
                default:
                    // Check for delegated access as fallback
                    hasOwnership = ownershipValidator.hasDelegatedAccess(userId, resourceType, resourceId);
                    break;
            }
            
            if (!hasOwnership) {
                log.warn("SECURITY: User {} denied access to {} resource {} - ownership validation failed", 
                        userId, resourceType, resourceId);
            } else {
                log.debug("Ownership validated: userId={}, resourceType={}, resourceId={}", 
                        userId, resourceType, resourceId);
            }
            
            return hasOwnership;
            
        } catch (Exception e) {
            log.error("Error checking resource ownership: userId={}, resourceType={}, resourceId={}", 
                    userId, resourceType, resourceId, e);
            return false; // Fail securely
        }
    }
    
    /**
     * Extract resource ID from common parameter names when not explicitly specified
     */
    private String extractResourceIdFromCommonParams(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();
        
        // Common resource ID parameter names in priority order
        String[] commonParamNames = {
            "id", "resourceId", "walletId", "paymentId", "transactionId", 
            "accountId", "notificationId", "entryId", "familyAccountId", "businessId"
        };
        
        for (String paramName : commonParamNames) {
            for (int i = 0; i < parameters.length; i++) {
                if (parameters[i].getName().equals(paramName) && args[i] != null) {
                    return args[i].toString();
                }
            }
        }
        
        return null;
    }

    /**
     * Extracts user ID from authentication
     */
    private UUID extractUserId(Authentication authentication) {
        try {
            Object principal = authentication.getPrincipal();
            
            // Handle different authentication principal types
            if (principal instanceof UserPrincipal) {
                return ((UserPrincipal) principal).getId();
            } else if (principal instanceof String) {
                return UUID.fromString((String) principal);
            } else if (principal instanceof UUID) {
                return (UUID) principal;
            }
            
            // Try to extract from authentication name
            String name = authentication.getName();
            if (name != null) {
                try {
                    return UUID.fromString(name);
                } catch (IllegalArgumentException e) {
                    log.debug("Authentication name is not a valid UUID: {}", name);
                }
            }
            
        } catch (Exception e) {
            log.error("Error extracting user ID from authentication", e);
        }
        
        return null;
    }

    /**
     * Gets default return value for method when failing silently
     */
    private Object getDefaultReturnValue(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> returnType = signature.getReturnType();
        
        // Return appropriate default value based on return type
        if (returnType == void.class || returnType == Void.class) {
            return null;
        } else if (returnType == boolean.class || returnType == Boolean.class) {
            return false;
        } else if (returnType.isPrimitive()) {
            return 0; // Default primitive value
        } else if (java.util.Collection.class.isAssignableFrom(returnType)) {
            return java.util.Collections.emptyList();
        } else if (java.util.Map.class.isAssignableFrom(returnType)) {
            return java.util.Collections.emptyMap();
        } else if (returnType.isArray()) {
            return java.lang.reflect.Array.newInstance(returnType.getComponentType(), 0);
        }
        
        return null;
    }

    /**
     * User principal interface for extracting user information
     */
    public interface UserPrincipal {
        UUID getId();
        String getUsername();
        Set<String> getRoles();
    }
}