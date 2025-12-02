package com.waqiti.voice.security.access;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.UUID;

/**
 * Voice Data Access Security Aspect
 *
 * CRITICAL SECURITY: AOP-based row-level security enforcement
 *
 * Automatically validates that users can only access their own data using
 * Spring AOP to intercept method calls annotated with @ValidateUserAccess.
 *
 * Features:
 * - Automatic user ID validation
 * - Works on any method with UUID userId parameter
 * - Admin override support
 * - Audit logging of access attempts
 * - Prevents horizontal privilege escalation
 *
 * Usage:
 * @ValidateUserAccess
 * public VoiceProfile getVoiceProfile(UUID userId) {
 *     // This method will automatically validate userId matches authenticated user
 * }
 *
 * Security Model:
 * 1. Extract userId parameter from method arguments
 * 2. Compare with authenticated user from SecurityContext
 * 3. Throw UnauthorizedException if mismatch
 * 4. Allow admins to access any user's data (with audit log)
 *
 * Prevents:
 * - Horizontal privilege escalation (User A accessing User B's data)
 * - IDOR vulnerabilities (Insecure Direct Object Reference)
 * - Parameter tampering attacks
 *
 * Compliance:
 * - GDPR Article 32 (Access control measures)
 * - PCI-DSS Requirement 7 (Restrict access by business need-to-know)
 * - SOC 2 (Logical access controls)
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class VoiceDataAccessSecurityAspect {

    private final SecurityContextService securityContextService;

    /**
     * Intercept methods annotated with @ValidateUserAccess
     *
     * Validates that the userId parameter matches the authenticated user
     */
    @Before("@annotation(validateUserAccess)")
    public void validateUserAccess(JoinPoint joinPoint, ValidateUserAccess validateUserAccess) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Object[] args = joinPoint.getArgs();
        String[] parameterNames = signature.getParameterNames();

        // Find userId parameter
        UUID requestedUserId = null;
        String userIdParamName = validateUserAccess.userIdParam();

        for (int i = 0; i < parameterNames.length; i++) {
            if (parameterNames[i].equals(userIdParamName)) {
                Object arg = args[i];
                if (arg instanceof UUID) {
                    requestedUserId = (UUID) arg;
                    break;
                } else if (arg instanceof String) {
                    try {
                        requestedUserId = UUID.fromString((String) arg);
                        break;
                    } catch (IllegalArgumentException e) {
                        log.error("Invalid UUID format for parameter {}: {}", userIdParamName, arg);
                        throw new SecurityContextService.UnauthorizedException("Invalid user ID format");
                    }
                }
            }
        }

        if (requestedUserId == null) {
            log.error("SECURITY: @ValidateUserAccess used but no userId parameter found in method: {}",
                    signature.getName());
            throw new SecurityContextService.UnauthorizedException(
                    "Security validation failed: userId parameter not found"
            );
        }

        // Validate access
        if (validateUserAccess.allowAdmin()) {
            securityContextService.validateUserAccessOrAdmin(requestedUserId);
        } else {
            securityContextService.validateUserAccess(requestedUserId);
        }

        log.debug("Access validation passed for method: {} userId: {}",
                signature.getName(), requestedUserId);
    }

    /**
     * Intercept methods annotated with @ValidateVoiceProfileOwnership
     *
     * Validates that voice profile belongs to authenticated user
     */
    @Before("@annotation(validateOwnership)")
    public void validateVoiceProfileOwnership(JoinPoint joinPoint, ValidateVoiceProfileOwnership validateOwnership) {
        // Similar to validateUserAccess but specifically for voice profiles
        // Can add additional checks like profile existence, enrollment status, etc.
        validateUserAccess(joinPoint, new ValidateUserAccess() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return ValidateUserAccess.class;
            }

            @Override
            public String userIdParam() {
                return validateOwnership.userIdParam();
            }

            @Override
            public boolean allowAdmin() {
                return validateOwnership.allowAdmin();
            }
        });
    }

    /**
     * Annotation to validate user access on service methods
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidateUserAccess {
        /**
         * Name of the userId parameter to validate
         */
        String userIdParam() default "userId";

        /**
         * Allow admin users to access any user's data
         */
        boolean allowAdmin() default false;
    }

    /**
     * Annotation to validate voice profile ownership
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidateVoiceProfileOwnership {
        /**
         * Name of the userId parameter to validate
         */
        String userIdParam() default "userId";

        /**
         * Allow admin users to access any user's data
         */
        boolean allowAdmin() default false;
    }

    /**
     * Annotation to validate voice command ownership
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidateVoiceCommandOwnership {
        /**
         * Name of the userId parameter to validate
         */
        String userIdParam() default "userId";

        /**
         * Allow admin users to access any user's data
         */
        boolean allowAdmin() default false;
    }

    /**
     * Annotation to validate voice transaction ownership
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidateVoiceTransactionOwnership {
        /**
         * Name of the userId parameter to validate
         */
        String userIdParam() default "userId";

        /**
         * Allow admin users to access any user's data
         */
        boolean allowAdmin() default false;
    }
}
