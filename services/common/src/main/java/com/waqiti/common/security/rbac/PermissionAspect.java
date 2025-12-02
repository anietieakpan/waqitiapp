package com.waqiti.common.security.rbac;

import com.waqiti.common.exceptions.AccessDeniedException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * AOP Aspect for @RequiresPermission Annotation
 *
 * Automatically enforces permission checks on methods annotated with @RequiresPermission.
 * This provides declarative, centralized authorization control across the platform.
 *
 * Example:
 * <pre>
 * {@code
 * @RequiresPermission(Permission.PAYMENT_WRITE)
 * public PaymentResponse createPayment(PaymentRequest request) {
 *     // Method automatically protected by aspect
 * }
 * }
 * </pre>
 *
 * @author Waqiti Platform Engineering
 */
@Aspect
@Component
public class PermissionAspect {
    private static final Logger logger = LoggerFactory.getLogger(PermissionAspect.class);

    @Autowired
    private AuthorizationService authorizationService;

    /**
     * Intercept methods annotated with @RequiresPermission
     * Checks permission before allowing method execution
     *
     * @param joinPoint The method being intercepted
     * @return The result of the method execution
     * @throws Throwable if method execution fails or permission is denied
     */
    @Around("@annotation(com.waqiti.common.security.rbac.RequiresPermission)")
    public Object enforcePermission(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Get the annotation
        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);

        if (annotation == null) {
            // Shouldn't happen, but handle gracefully
            logger.warn("@RequiresPermission annotation not found on method: {}",
                method.getName());
            return joinPoint.proceed();
        }

        Permission requiredPermission = annotation.value();
        String customMessage = annotation.message();

        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();
        String userId = authorizationService.getCurrentUserId();

        logger.debug("Checking permission for {}.{} - User: {}, Required: {}",
            className, methodName, userId, requiredPermission.getCode());

        try {
            // Check if user has required permission
            if (!authorizationService.hasPermission(requiredPermission)) {
                String errorMessage = String.format(
                    "%s | Method: %s.%s() | User: %s | Required: %s",
                    customMessage,
                    className,
                    methodName,
                    userId,
                    requiredPermission.getCode()
                );

                logger.error("Permission denied - {}", errorMessage);

                throw new AccessDeniedException(errorMessage);
            }

            // Permission granted, proceed with method execution
            logger.debug("Permission granted for {}.{} - User: {}",
                className, methodName, userId);

            return joinPoint.proceed();

        } catch (AccessDeniedException e) {
            // Re-throw access denied exceptions
            throw e;
        } catch (Throwable e) {
            // Log unexpected errors but still throw them
            logger.error("Error in permission aspect for {}.{}: {}",
                className, methodName, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Intercept class-level @RequiresPermission annotations
     * Applies to all public methods in the class
     *
     * @param joinPoint The method being intercepted
     * @return The result of the method execution
     * @throws Throwable if method execution fails or permission is denied
     */
    @Around("@within(com.waqiti.common.security.rbac.RequiresPermission) && execution(public * *(..))")
    public Object enforceClassLevelPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        Class<?> targetClass = joinPoint.getTarget().getClass();

        // Get class-level annotation
        RequiresPermission annotation = targetClass.getAnnotation(RequiresPermission.class);

        if (annotation == null) {
            // Check if method has its own annotation (takes precedence)
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();

            if (method.isAnnotationPresent(RequiresPermission.class)) {
                // Method-level annotation will be handled by the other aspect
                return joinPoint.proceed();
            }

            return joinPoint.proceed();
        }

        Permission requiredPermission = annotation.value();
        String customMessage = annotation.message();

        String className = targetClass.getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String userId = authorizationService.getCurrentUserId();

        logger.debug("Checking class-level permission for {}.{} - User: {}, Required: {}",
            className, methodName, userId, requiredPermission.getCode());

        try {
            if (!authorizationService.hasPermission(requiredPermission)) {
                String errorMessage = String.format(
                    "%s | Class: %s | Method: %s() | User: %s | Required: %s",
                    customMessage,
                    className,
                    methodName,
                    userId,
                    requiredPermission.getCode()
                );

                logger.error("Class-level permission denied - {}", errorMessage);

                throw new AccessDeniedException(errorMessage);
            }

            logger.debug("Class-level permission granted for {}.{} - User: {}",
                className, methodName, userId);

            return joinPoint.proceed();

        } catch (AccessDeniedException e) {
            throw e;
        } catch (Throwable e) {
            logger.error("Error in class-level permission aspect for {}.{}: {}",
                className, methodName, e.getMessage(), e);
            throw e;
        }
    }
}
