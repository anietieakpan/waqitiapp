package com.waqiti.common.security.authorization;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AUTHORIZATION ENFORCEMENT ASPECT
 *
 * AOP aspect that enforces authorization checks across all secured endpoints.
 * Works in conjunction with @PreAuthorize and custom authorization annotations.
 *
 * SECURITY CRITICAL: This aspect provides defense-in-depth by adding an
 * additional layer of authorization enforcement.
 *
 * FEATURES:
 * - Role-based access control (RBAC) enforcement
 * - Permission-based access control enforcement
 * - Resource ownership validation
 * - Audit logging of authorization decisions
 * - Rate limiting integration
 * - Suspicious activity detection
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-01-01
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuthorizationAspect {

    private final AuditService auditService;
    private final ResourceOwnershipValidator ownershipValidator;

    /**
     * Enforce @RequiresPermission annotations
     */
    @Around("@annotation(com.waqiti.common.security.rbac.RequiresPermission)")
    public Object enforcePermission(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        com.waqiti.common.security.rbac.RequiresPermission requiresPermission =
            method.getAnnotation(com.waqiti.common.security.rbac.RequiresPermission.class);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            logAuthorizationFailure("NOT_AUTHENTICATED", method, null, "No authentication");
            throw new UnauthorizedException("Authentication required");
        }

        String userId = extractUserId(authentication);
        Set<String> userRoles = extractRoles(authentication);

        // Check if user has required permission
        AuthorizationMatrix.Permission requiredPermission = requiresPermission.value();
        boolean hasPermission = hasPermission(userRoles, requiredPermission);

        if (!hasPermission) {
            String message = String.format(
                "User %s does not have permission: %s (Required for: %s.%s)",
                userId, requiredPermission.getCode(),
                method.getDeclaringClass().getSimpleName(), method.getName()
            );

            logAuthorizationFailure("PERMISSION_DENIED", method, userId, message);

            // AUDIT: Log unauthorized access attempt
            auditService.logSecurityEvent(
                "AUTHORIZATION_DENIED",
                userId,
                Map.of(
                    "permission", requiredPermission.getCode(),
                    "method", method.getName(),
                    "class", method.getDeclaringClass().getName(),
                    "roles", String.join(",", userRoles)
                ),
                "MEDIUM"
            );

            throw new UnauthorizedException(message);
        }

        // AUDIT: Log successful authorization
        auditService.logSecurityEvent(
            "AUTHORIZATION_GRANTED",
            userId,
            Map.of(
                "permission", requiredPermission.getCode(),
                "method", method.getName()
            ),
            "LOW"
        );

        return joinPoint.proceed();
    }

    /**
     * Enforce @ValidateOwnership annotations
     */
    @Around("@annotation(com.waqiti.security.idor.ValidateOwnership)")
    public Object enforceOwnership(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        com.waqiti.security.idor.ValidateOwnership validateOwnership =
            method.getAnnotation(com.waqiti.security.idor.ValidateOwnership.class);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            logAuthorizationFailure("NOT_AUTHENTICATED", method, null, "No authentication");
            throw new UnauthorizedException("Authentication required");
        }

        String userId = extractUserId(authentication);
        String resourceType = validateOwnership.resourceType();
        String resourceIdParam = validateOwnership.resourceIdParam();

        // Extract resource ID from method parameters
        Object resourceId = extractResourceId(joinPoint, signature, resourceIdParam);

        // Validate ownership
        boolean isOwner = ownershipValidator.validateOwnership(
            userId, resourceType, resourceId.toString()
        );

        if (!isOwner) {
            String message = String.format(
                "User %s does not own %s resource: %s (IDOR attempt detected)",
                userId, resourceType, resourceId
            );

            logAuthorizationFailure("IDOR_ATTEMPT", method, userId, message);

            // AUDIT: Log IDOR attempt (HIGH PRIORITY)
            auditService.logSecurityEvent(
                "IDOR_ATTEMPT_BLOCKED",
                userId,
                Map.of(
                    "resourceType", resourceType,
                    "resourceId", resourceId.toString(),
                    "method", method.getName(),
                    "class", method.getDeclaringClass().getName()
                ),
                "HIGH"  // HIGH priority for security team
            );

            throw new UnauthorizedException(
                String.format("Access denied to %s resource", resourceType)
            );
        }

        // AUDIT: Log successful ownership validation
        auditService.logSecurityEvent(
            "OWNERSHIP_VALIDATED",
            userId,
            Map.of(
                "resourceType", resourceType,
                "resourceId", resourceId.toString()
            ),
            "LOW"
        );

        return joinPoint.proceed();
    }

    /**
     * Check if user has permission based on roles
     */
    private boolean hasPermission(Set<String> userRoles, AuthorizationMatrix.Permission permission) {
        Set<AuthorizationMatrix.Role> roles = userRoles.stream()
            .map(roleStr -> {
                try {
                    return AuthorizationMatrix.Role.valueOf(roleStr.replace("ROLE_", ""));
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown role: {}", roleStr);
                    return null;
                }
            })
            .filter(role -> role != null)
            .collect(Collectors.toSet());

        return AuthorizationMatrix.hasPermission(roles, permission);
    }

    /**
     * Extract user ID from authentication
     */
    private String extractUserId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        } else if (authentication.getPrincipal() instanceof String) {
            return (String) authentication.getPrincipal();
        } else {
            return authentication.getName();
        }
    }

    /**
     * Extract roles from authentication
     */
    private Set<String> extractRoles(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
    }

    /**
     * Extract resource ID from method parameters
     */
    private Object extractResourceId(
        ProceedingJoinPoint joinPoint,
        MethodSignature signature,
        String paramName) {

        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameterNames.length; i++) {
            if (parameterNames[i].equals(paramName)) {
                return args[i];
            }
        }

        throw new IllegalArgumentException(
            "Parameter '" + paramName + "' not found in method signature"
        );
    }

    /**
     * Log authorization failure
     */
    private void logAuthorizationFailure(
        String reason,
        Method method,
        String userId,
        String message) {

        log.error("AUTHORIZATION_FAILED: {} - Method: {}.{}, User: {}, Reason: {}",
            reason,
            method.getDeclaringClass().getSimpleName(),
            method.getName(),
            userId != null ? userId : "UNKNOWN",
            message);
    }
}
