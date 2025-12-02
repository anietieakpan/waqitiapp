package com.waqiti.security.idor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * AOP Aspect for Automatic IDOR Protection
 *
 * CRITICAL SECURITY:
 * - Automatically validates resource ownership before method execution
 * - Uses @ValidateOwnership annotation to mark protected methods
 * - Extracts user ID from JWT authentication context
 * - Prevents unauthorized access to user resources
 *
 * USAGE:
 * <pre>
 * {@literal @}GetMapping("/wallets/{walletId}")
 * {@literal @}ValidateOwnership(resourceType = "WALLET", resourceIdParam = "walletId")
 * public WalletResponse getWallet(@PathVariable UUID walletId) {
 *     // This method will only execute if authenticated user owns the wallet
 * }
 * </pre>
 *
 * SECURITY FEATURES:
 * - Runs before method execution (fail-fast)
 * - Audit logging of all validation failures
 * - Support for admin role bypass
 * - Support for custom permission checks
 *
 * @author Waqiti Security Team
 * @version 3.0.0
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class OwnershipValidationAspect {

    private final OwnershipValidator ownershipValidator;

    /**
     * Intercept methods annotated with @ValidateOwnership
     */
    @Around("@annotation(validateOwnership)")
    public Object validateOwnershipBeforeExecution(
            ProceedingJoinPoint joinPoint,
            ValidateOwnership validateOwnership) throws Throwable {

        log.debug("SECURITY: Intercepting method for ownership validation: {}",
            joinPoint.getSignature().getName());

        try {
            // Extract authenticated user ID from JWT token
            UUID authenticatedUserId = extractAuthenticatedUserId();

            if (authenticatedUserId == null) {
                log.error("SECURITY: No authenticated user found in security context");
                throw new OwnershipValidationException("Authentication required");
            }

            // Check if user is admin (bypass ownership check)
            if (validateOwnership.allowAdmin() && ownershipValidator.isAdmin(authenticatedUserId)) {
                log.debug("SECURITY: Admin user {} bypassing ownership check", authenticatedUserId);
                return joinPoint.proceed();
            }

            // Extract resource ID from method parameters
            UUID resourceId = extractResourceId(joinPoint, validateOwnership.resourceIdParam());

            if (resourceId == null) {
                log.error("SECURITY: Could not extract resource ID from parameter: {}",
                    validateOwnership.resourceIdParam());
                throw new OwnershipValidationException("Invalid resource ID");
            }

            // Validate ownership
            if (validateOwnership.requiredPermission().isEmpty()) {
                // Simple ownership validation
                ownershipValidator.validateOwnership(
                    authenticatedUserId,
                    validateOwnership.resourceType(),
                    resourceId
                );
            } else {
                // Ownership + permission validation
                ownershipValidator.validateOwnershipWithPermission(
                    authenticatedUserId,
                    validateOwnership.resourceType(),
                    resourceId,
                    validateOwnership.requiredPermission()
                );
            }

            // Ownership validated - proceed with method execution
            return joinPoint.proceed();

        } catch (OwnershipValidationException e) {
            log.warn("SECURITY: Ownership validation failed: {}", e.getMessage());
            throw e;
        } catch (Throwable e) {
            log.error("SECURITY: Unexpected error during ownership validation", e);
            throw new OwnershipValidationException("Ownership validation failed", e);
        }
    }

    /**
     * Extract authenticated user ID from Spring Security context
     */
    private UUID extractAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        // Extract user ID from JWT claims
        Object principal = authentication.getPrincipal();

        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            org.springframework.security.core.userdetails.UserDetails userDetails =
                (org.springframework.security.core.userdetails.UserDetails) principal;
            return UUID.fromString(userDetails.getUsername());
        } else if (principal instanceof String) {
            return UUID.fromString((String) principal);
        }

        // Try to extract from JWT token claims
        if (authentication.getDetails() instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> details = (java.util.Map<String, Object>) authentication.getDetails();
            Object userId = details.get("userId");
            if (userId != null) {
                return UUID.fromString(userId.toString());
            }
        }

        return null;
    }

    /**
     * Extract resource ID from method parameters
     */
    private UUID extractResourceId(ProceedingJoinPoint joinPoint, String paramName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        // Find parameter by name
        for (int i = 0; i < parameterNames.length; i++) {
            if (parameterNames[i].equals(paramName)) {
                Object arg = args[i];

                if (arg instanceof UUID) {
                    return (UUID) arg;
                } else if (arg instanceof String) {
                    return UUID.fromString((String) arg);
                } else {
                    log.error("SECURITY: Parameter {} is not UUID or String type", paramName);
                    return null;
                }
            }
        }

        log.error("SECURITY: Parameter {} not found in method signature", paramName);
        return null;
    }
}
