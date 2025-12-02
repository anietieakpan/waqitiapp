package com.waqiti.security.sox;

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
import java.util.Arrays;
import java.util.UUID;

/**
 * AOP Aspect for Automatic Segregation of Duties Enforcement
 *
 * CRITICAL COMPLIANCE:
 * - Automatically enforces SoD rules before method execution
 * - Uses @RequireSegregationOfDuties annotation
 * - Extracts user ID from JWT authentication context
 * - Prevents SoD violations at runtime
 *
 * EXECUTION FLOW:
 * 1. Method with @RequireSegregationOfDuties is called
 * 2. Aspect intercepts call BEFORE execution
 * 3. Extract authenticated user ID from SecurityContext
 * 4. Extract transaction ID from method parameters
 * 5. Validate SoD rules
 * 6. If valid → proceed with method execution
 * 7. If invalid → throw SegregationOfDutiesViolationException (403 Forbidden)
 * 8. Record transaction action for audit trail
 *
 * @author Waqiti Compliance Team
 * @version 3.0.0
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class SegregationOfDutiesAspect {

    private final SegregationOfDutiesValidator sodValidator;
    private final SegregationOfDutiesRepository sodRepository;
    private final SegregationOfDutiesAuditLogger sodAuditLogger;

    /**
     * Intercept methods annotated with @RequireSegregationOfDuties
     */
    @Around("@annotation(requireSoD)")
    public Object enforceSegregationOfDuties(
            ProceedingJoinPoint joinPoint,
            RequireSegregationOfDuties requireSoD) throws Throwable {

        log.debug("Enforcing SoD for method: {}, Action: {}",
            joinPoint.getSignature().getName(), requireSoD.action());

        try {
            // 1. Extract authenticated user ID
            UUID authenticatedUserId = extractAuthenticatedUserId();

            if (authenticatedUserId == null) {
                log.error("SOX: No authenticated user found in security context");
                throw new SegregationOfDutiesValidator.SegregationOfDutiesViolationException(
                    "Authentication required for SoD enforcement"
                );
            }

            // 2. Extract transaction ID from method parameters
            UUID transactionId = extractTransactionId(joinPoint, requireSoD.transactionIdParam());

            if (transactionId == null) {
                log.error("SOX: Could not extract transaction ID from parameter: {}",
                    requireSoD.transactionIdParam());
                throw new SegregationOfDutiesValidator.SegregationOfDutiesViolationException(
                    "Invalid transaction ID for SoD validation"
                );
            }

            // 3. Admin bypass (if allowed)
            if (requireSoD.allowAdminBypass() && isAdmin(authenticatedUserId)) {
                log.info("SOX: Admin bypass for user {} on action {}", authenticatedUserId, requireSoD.action());
                sodAuditLogger.logActionValidation(authenticatedUserId, transactionId,
                    requireSoD.action() + "_ADMIN_BYPASS", true);
                return joinPoint.proceed();
            }

            // 4. Validate incompatible actions
            if (requireSoD.incompatibleActions().length > 0) {
                sodValidator.validateTransactionAction(
                    authenticatedUserId, transactionId, requireSoD.action()
                );
            }

            // 5. Validate dual authorization
            if (requireSoD.requireDualAuth()) {
                UUID approverId = authenticatedUserId;
                UUID initiatorId = extractInitiatorFromTransaction(transactionId);

                if (initiatorId != null) {
                    sodValidator.validateDualAuthorization(
                        initiatorId, approverId, requireSoD.action(), transactionId
                    );
                } else {
                    log.warn("SOX: Could not determine initiator for dual auth validation");
                }
            }

            // 6. Validate maker-checker
            if (requireSoD.requireMakerChecker()) {
                UUID checkerId = authenticatedUserId;

                // Try to extract checker ID from parameters if specified
                if (!requireSoD.checkerIdParam().isEmpty()) {
                    UUID paramCheckerId = extractParameterValue(joinPoint, requireSoD.checkerIdParam());
                    if (paramCheckerId != null) {
                        checkerId = paramCheckerId;
                    }
                }

                UUID makerId = extractMakerFromTransaction(transactionId);

                if (makerId != null) {
                    sodValidator.validateMakerChecker(makerId, checkerId, transactionId);
                } else {
                    log.warn("SOX: Could not determine maker for maker-checker validation");
                }
            }

            // 7. SoD validation passed - record action and proceed
            sodRepository.recordTransactionAction(transactionId, authenticatedUserId, requireSoD.action());

            sodAuditLogger.logActionValidation(authenticatedUserId, transactionId,
                requireSoD.action(), true);

            log.debug("SOX: SoD validation passed for user {} on transaction {}",
                authenticatedUserId, transactionId);

            return joinPoint.proceed();

        } catch (SegregationOfDutiesValidator.SegregationOfDutiesViolationException e) {
            log.error("SOX: SoD validation failed: {}", e.getMessage());
            throw e;

        } catch (Throwable e) {
            log.error("SOX: Unexpected error during SoD validation", e);
            throw new SegregationOfDutiesValidator.SegregationOfDutiesViolationException(
                "SoD validation failed: " + e.getMessage(), e
            );
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
     * Extract transaction ID from method parameters
     */
    private UUID extractTransactionId(ProceedingJoinPoint joinPoint, String paramName) {
        return extractParameterValue(joinPoint, paramName);
    }

    /**
     * Extract parameter value by name
     */
    private UUID extractParameterValue(ProceedingJoinPoint joinPoint, String paramName) {
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
                    try {
                        return UUID.fromString((String) arg);
                    } catch (IllegalArgumentException e) {
                        log.error("SOX: Invalid UUID format for parameter {}: {}", paramName, arg);
                        return null;
                    }
                } else {
                    log.error("SOX: Parameter {} is not UUID or String type", paramName);
                    return null;
                }
            }
        }

        log.error("SOX: Parameter {} not found in method signature", paramName);
        return null;
    }

    /**
     * Extract initiator user ID from transaction history
     */
    private UUID extractInitiatorFromTransaction(UUID transactionId) {
        // Get first action (initiator)
        var actions = sodRepository.getTransactionActions(transactionId);

        if (actions.isEmpty()) {
            return null;
        }

        // First action is typically the initiation
        return actions.get(0).getUserId();
    }

    /**
     * Extract maker user ID from transaction history
     */
    private UUID extractMakerFromTransaction(UUID transactionId) {
        // Similar to initiator - first action is the "maker"
        return extractInitiatorFromTransaction(transactionId);
    }

    /**
     * Check if user is an admin
     */
    private boolean isAdmin(UUID userId) {
        var roles = sodRepository.getUserRoles(userId);
        return roles.stream().anyMatch(role ->
            role.equals("ROLE_ADMIN") ||
            role.equals("ROLE_SUPER_ADMIN") ||
            role.equals("ROLE_SYSTEM")
        );
    }
}
