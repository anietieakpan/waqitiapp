package com.waqiti.common.security;

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
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Aspect for automatically validating resource ownership on annotated methods
 * Provides declarative security for financial operations
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class ResourceOwnershipAspect {

    private final ResourceOwnershipValidator ownershipValidator;

    /**
     * Annotation to mark methods that require resource ownership validation
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidateOwnership {
        /**
         * Type of resource being accessed
         */
        ResourceType resourceType();
        
        /**
         * Name of the method parameter containing the resource ID
         */
        String resourceIdParam() default "id";
        
        /**
         * Operation being performed for audit logging
         */
        String operation() default "ACCESS";
        
        /**
         * Whether to validate bulk operations (when resourceIdParam contains a List)
         */
        boolean bulkOperation() default false;
    }

    /**
     * Annotation for administrative operations requiring special permissions
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RequireAdminAccess {
        /**
         * Operation description for audit logging
         */
        String operation();
        
        /**
         * Resource type being administered
         */
        String resourceType() default "SYSTEM";
    }

    /**
     * Annotation for compliance operations requiring special permissions
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RequireComplianceAccess {
        /**
         * Operation description for audit logging
         */
        String operation();
        
        /**
         * Resource type for compliance operation
         */
        String resourceType() default "COMPLIANCE_DATA";
    }

    /**
     * Intercept methods annotated with @ValidateOwnership
     */
    @Before("@annotation(validateOwnership)")
    public void validateResourceOwnership(JoinPoint joinPoint, ValidateOwnership validateOwnership) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Object[] args = joinPoint.getArgs();
            Parameter[] parameters = signature.getMethod().getParameters();

            // Find the resource ID parameter
            Object resourceIdValue = findParameterValue(parameters, args, validateOwnership.resourceIdParam());
            
            if (resourceIdValue == null) {
                log.warn("Resource ID parameter '{}' not found in method '{}'", 
                        validateOwnership.resourceIdParam(), signature.getName());
                return;
            }

            // Handle bulk operations
            if (validateOwnership.bulkOperation() && resourceIdValue instanceof List) {
                @SuppressWarnings("unchecked")
                List<UUID> resourceIds = (List<UUID>) resourceIdValue;
                ownershipValidator.validateBulkOperationAccess(
                        resourceIds, 
                        validateOwnership.resourceType().name(), 
                        validateOwnership.operation()
                );
            } else {
                // Single resource validation
                UUID resourceId = convertToUUID(resourceIdValue);
                validateSingleResource(resourceId, validateOwnership);
            }

            log.debug("Resource ownership validation passed for method: {}", signature.getName());

        } catch (Exception e) {
            log.error("Resource ownership validation failed for method: {}", 
                    joinPoint.getSignature().getName(), e);
            throw e; // Re-throw to prevent unauthorized access
        }
    }

    /**
     * Intercept methods requiring administrative access
     */
    @Before("@annotation(requireAdminAccess)")
    public void validateAdministrativeAccess(JoinPoint joinPoint, RequireAdminAccess requireAdminAccess) {
        try {
            ownershipValidator.validateAdministrativeAccess(
                    requireAdminAccess.operation(),
                    requireAdminAccess.resourceType()
            );

            log.debug("Administrative access validation passed for method: {}", 
                    joinPoint.getSignature().getName());

        } catch (Exception e) {
            log.error("Administrative access validation failed for method: {}", 
                    joinPoint.getSignature().getName(), e);
            throw e;
        }
    }

    /**
     * Intercept methods requiring compliance access
     */
    @Before("@annotation(requireComplianceAccess)")
    public void validateComplianceAccess(JoinPoint joinPoint, RequireComplianceAccess requireComplianceAccess) {
        try {
            ownershipValidator.validateComplianceAccess(
                    requireComplianceAccess.operation(),
                    requireComplianceAccess.resourceType()
            );

            log.debug("Compliance access validation passed for method: {}", 
                    joinPoint.getSignature().getName());

        } catch (Exception e) {
            log.error("Compliance access validation failed for method: {}", 
                    joinPoint.getSignature().getName(), e);
            throw e;
        }
    }

    // Private helper methods

    private Object findParameterValue(Parameter[] parameters, Object[] args, String paramName) {
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(paramName)) {
                return args[i];
            }
        }
        
        // Also check for common parameter patterns
        for (int i = 0; i < parameters.length; i++) {
            String parameterName = parameters[i].getName();
            if (parameterName.endsWith("Id") && paramName.equals("id")) {
                return args[i];
            }
            if (parameterName.equals("resourceId") && paramName.equals("id")) {
                return args[i];
            }
        }
        
        return null;
    }

    private UUID convertToUUID(Object value) {
        if (value instanceof UUID) {
            return (UUID) value;
        } else if (value instanceof String) {
            return UUID.fromString((String) value);
        } else {
            throw new IllegalArgumentException("Resource ID must be UUID or String: " + value);
        }
    }

    private void validateSingleResource(UUID resourceId, ValidateOwnership validateOwnership) {
        switch (validateOwnership.resourceType()) {
            case WALLET -> ownershipValidator.validateWalletOwnership(resourceId, validateOwnership.operation());
            case TRANSACTION -> ownershipValidator.validateTransactionAccess(resourceId, validateOwnership.operation());
            case ACCOUNT -> ownershipValidator.validateAccountAccess(resourceId, validateOwnership.operation());
            case PAYMENT_METHOD -> ownershipValidator.validatePaymentMethodAccess(resourceId, validateOwnership.operation());
            case KYC_DOCUMENT -> ownershipValidator.validateKycDocumentAccess(resourceId, validateOwnership.operation());
            case USER_PROFILE -> ownershipValidator.validateUserProfileAccess(resourceId, validateOwnership.operation());
            default -> throw new IllegalArgumentException("Unsupported resource type: " + validateOwnership.resourceType());
        }
    }

    /**
     * Supported resource types for ownership validation
     */
    public enum ResourceType {
        WALLET,
        TRANSACTION,
        ACCOUNT,
        PAYMENT_METHOD,
        KYC_DOCUMENT,
        USER_PROFILE,
        MERCHANT_ACCOUNT,
        COMPLIANCE_RECORD,
        AUDIT_LOG
    }
}