package com.waqiti.common.bulkhead;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * AOP Aspect for automatic bulkhead protection via @BulkheadProtected annotation
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class BulkheadAspect {
    
    private final BulkheadService bulkheadService;
    
    @Around("@annotation(bulkheadProtected)")
    public Object applyBulkhead(ProceedingJoinPoint joinPoint, BulkheadProtected bulkheadProtected) throws Throwable {
        
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        String operationId = bulkheadProtected.operationId().isEmpty() 
            ? className + "." + methodName 
            : bulkheadProtected.operationId();
            
        ResourceType resourceType = bulkheadProtected.resourceType();
        int timeoutSeconds = bulkheadProtected.timeoutSeconds();
        
        log.debug("Applying bulkhead protection to {}.{} with resource type {} and timeout {}s", 
                 className, methodName, resourceType, timeoutSeconds);
        
        Supplier<Object> operation = () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable throwable) {
                if (throwable instanceof RuntimeException) {
                    throw (RuntimeException) throwable;
                }
                throw new RuntimeException("Bulkhead operation failed", throwable);
            }
        };
        
        try {
            CompletableFuture<BulkheadResult<Object>> future = executeWithBulkhead(
                operation, operationId, resourceType, timeoutSeconds);
            
            BulkheadResult<Object> result = future.get(5, TimeUnit.SECONDS);
            
            if (result.isSuccess()) {
                return result.getResult();
            } else if (result.isTimeout()) {
                throw new BulkheadTimeoutException(
                    "Operation timed out: " + operationId + " (resource: " + resourceType + ")");
            } else if (result.isRejected()) {
                throw new BulkheadRejectedException(
                    "Operation rejected due to resource exhaustion: " + operationId + " (resource: " + resourceType + ")");
            } else {
                throw new BulkheadException(
                    "Bulkhead operation failed: " + result.getError(), result.getException());
            }
            
        } catch (Exception e) {
            log.error("Bulkhead aspect failed for operation {}", operationId, e);
            throw new BulkheadException("Bulkhead protection failed", e);
        }
    }
    
    private CompletableFuture<BulkheadResult<Object>> executeWithBulkhead(
            Supplier<Object> operation, String operationId, ResourceType resourceType, int timeoutSeconds) {
        
        return switch (resourceType) {
            case PAYMENT_PROCESSING -> bulkheadService.executePaymentProcessing(operation, operationId);
            case KYC_VERIFICATION -> bulkheadService.executeKycVerification(operation, operationId);
            case FRAUD_DETECTION -> bulkheadService.executeFraudDetection(operation, operationId);
            case NOTIFICATION -> bulkheadService.executeNotification(operation, operationId);
            case ANALYTICS -> bulkheadService.executeAnalytics(operation, operationId);
            case CORE_BANKING -> bulkheadService.executeCoreBanking(operation, operationId);
        };
    }
}
