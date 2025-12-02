package com.waqiti.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aspect that automatically applies circuit breakers to external service calls
 * 
 * Automatically detects and wraps:
 * - RestTemplate calls
 * - WebClient calls  
 * - FeignClient calls
 * - Any method annotated with @ExternalServiceCall
 * - Methods containing "client", "external", "remote" in class/method names
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "resilience4j.circuitbreaker.aspect.enabled", havingValue = "true", matchIfMissing = true)
public class ExternalServiceCallAspect {
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Cache for method-to-service-name mapping
    private final Map<String, String> methodServiceMapping = new ConcurrentHashMap<>();
    
    /**
     * Intercept RestTemplate calls
     */
    @Around("execution(* org.springframework.web.client.RestTemplate.*(..))")
    public Object aroundRestTemplateCall(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithCircuitBreaker(joinPoint, "rest-template-call");
    }
    
    /**
     * Intercept WebClient calls
     */
    @Around("execution(* org.springframework.web.reactive.function.client.WebClient.*(..))")
    public Object aroundWebClientCall(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithCircuitBreaker(joinPoint, "web-client-call");
    }
    
    /**
     * Intercept Feign client calls
     */
    @Around("@within(org.springframework.cloud.openfeign.FeignClient)")
    public Object aroundFeignClientCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String serviceName = extractServiceNameFromFeignClient(joinPoint);
        return executeWithCircuitBreaker(joinPoint, serviceName);
    }
    
    /**
     * Intercept methods annotated with @ExternalServiceCall
     */
    @Around("@annotation(externalServiceCall)")
    public Object aroundExternalServiceCall(ProceedingJoinPoint joinPoint, ExternalServiceCall externalServiceCall) throws Throwable {
        return executeWithCircuitBreaker(joinPoint, externalServiceCall.serviceName());
    }
    
    /**
     * Intercept methods in client classes (by naming convention)
     */
    @Around("execution(* *..client..*(..) || execution(* *..*Client.*(..) || execution(* *..*ServiceClient.*(..)))")
    public Object aroundClientCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String serviceName = extractServiceNameFromMethod(joinPoint);
        return executeWithCircuitBreaker(joinPoint, serviceName);
    }
    
    /**
     * Execute method with circuit breaker protection
     */
    private Object executeWithCircuitBreaker(ProceedingJoinPoint joinPoint, String serviceName) throws Throwable {
        if (serviceName == null || serviceName.isEmpty()) {
            // If we can't determine service name, proceed without circuit breaker
            return joinPoint.proceed();
        }
        
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceName);
        String methodSignature = joinPoint.getSignature().toShortString();
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Apply circuit breaker
            Object result = circuitBreaker.executeSupplier(() -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable throwable) {
                    if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    }
                    throw new RuntimeException("External service call failed", throwable);
                }
            });
            
            // Record success metrics
            long duration = System.currentTimeMillis() - startTime;
            recordSuccessMetrics(serviceName, methodSignature, duration);
            
            return result;
            
        } catch (Exception e) {
            // Record failure metrics
            long duration = System.currentTimeMillis() - startTime;
            recordFailureMetrics(serviceName, methodSignature, e, duration);
            
            // Check if we should use fallback
            if (shouldUseFallback(serviceName, e)) {
                Object fallbackResult = executeFallback(joinPoint, serviceName, e);
                if (fallbackResult != null) {
                    log.info("Used fallback for service: {} method: {}", serviceName, methodSignature);
                    return fallbackResult;
                }
            }
            
            throw e;
        }
    }
    
    /**
     * Extract service name from Feign client annotation
     */
    private String extractServiceNameFromFeignClient(ProceedingJoinPoint joinPoint) {
        Class<?> targetClass = joinPoint.getTarget().getClass();
        
        // Check for @FeignClient annotation
        if (targetClass.isAnnotationPresent(org.springframework.cloud.openfeign.FeignClient.class)) {
            org.springframework.cloud.openfeign.FeignClient feignClient = 
                targetClass.getAnnotation(org.springframework.cloud.openfeign.FeignClient.class);
            
            String name = feignClient.name();
            if (name.isEmpty()) {
                name = feignClient.value();
            }
            if (!name.isEmpty()) {
                return name;
            }
        }
        
        // Fallback to class name
        return extractServiceNameFromClassName(targetClass.getSimpleName());
    }
    
    /**
     * Extract service name from method signature
     */
    private String extractServiceNameFromMethod(ProceedingJoinPoint joinPoint) {
        String methodKey = joinPoint.getSignature().toShortString();
        
        return methodServiceMapping.computeIfAbsent(methodKey, key -> {
            Class<?> targetClass = joinPoint.getTarget().getClass();
            String className = targetClass.getSimpleName();
            String methodName = joinPoint.getSignature().getName();
            
            // Try to extract from class name first
            String serviceName = extractServiceNameFromClassName(className);
            if (serviceName != null) {
                return serviceName;
            }
            
            // Try to extract from method name
            serviceName = extractServiceNameFromMethodName(methodName);
            if (serviceName != null) {
                return serviceName;
            }
            
            // Fallback to generic name
            return "external-service";
        });
    }
    
    /**
     * Extract service name from class name
     */
    private String extractServiceNameFromClassName(String className) {
        className = className.toLowerCase();
        
        if (className.contains("wallet")) return "wallet-service";
        if (className.contains("payment")) return "payment-service";
        if (className.contains("kyc")) return "kyc-service";
        if (className.contains("fraud")) return "fraud-detection";
        if (className.contains("notification")) return "notification-service";
        if (className.contains("corebanking") || className.contains("banking")) return "bank-integration";
        if (className.contains("compliance")) return "compliance-service";
        if (className.contains("analytics")) return "analytics-service";
        if (className.contains("user")) return "user-service";
        if (className.contains("ledger")) return "ledger-service";
        
        // Remove common suffixes and return
        String serviceName = className
            .replaceAll("client$", "")
            .replaceAll("serviceclient$", "")
            .replaceAll("service$", "");
        
        return serviceName.isEmpty() ? null : serviceName;
    }
    
    /**
     * Extract service name from method name
     */
    private String extractServiceNameFromMethodName(String methodName) {
        methodName = methodName.toLowerCase();
        
        if (methodName.contains("wallet")) return "wallet-service";
        if (methodName.contains("payment")) return "payment-service";
        if (methodName.contains("kyc")) return "kyc-service";
        if (methodName.contains("fraud")) return "fraud-detection";
        if (methodName.contains("notify")) return "notification-service";
        if (methodName.contains("bank")) return "bank-integration";
        if (methodName.contains("compliance")) return "compliance-service";
        
        return null;
    }
    
    /**
     * Check if fallback should be used
     */
    private boolean shouldUseFallback(String serviceName, Exception e) {
        // Use fallback for non-critical services or specific error types
        return isNonCriticalService(serviceName) || 
               e instanceof java.net.ConnectException ||
               e instanceof java.net.SocketTimeoutException ||
               e.getMessage().toLowerCase().contains("timeout");
    }
    
    /**
     * Check if service is non-critical
     */
    private boolean isNonCriticalService(String serviceName) {
        return serviceName.contains("notification") ||
               serviceName.contains("analytics") ||
               serviceName.contains("reporting");
    }
    
    /**
     * Execute fallback logic
     */
    private Object executeFallback(ProceedingJoinPoint joinPoint, String serviceName, Exception e) {
        Class<?> returnType = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getReturnType();
        
        // Return appropriate fallback based on return type
        if (returnType.equals(Boolean.class) || returnType.equals(boolean.class)) {
            return false;
        } else if (returnType.equals(String.class)) {
            return "SERVICE_UNAVAILABLE";
        } else if (java.util.Collection.class.isAssignableFrom(returnType)) {
            return java.util.Collections.emptyList();
        } else if (java.util.Map.class.isAssignableFrom(returnType)) {
            return java.util.Collections.emptyMap();
        } else if (java.util.concurrent.CompletableFuture.class.isAssignableFrom(returnType)) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        
        // For complex objects, return null (calling code should handle)
        return null;
    }
    
    /**
     * Record success metrics
     */
    private void recordSuccessMetrics(String serviceName, String methodSignature, long duration) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("service", serviceName);
        metrics.put("method", methodSignature);
        metrics.put("status", "SUCCESS");
        metrics.put("duration", duration);
        metrics.put("timestamp", LocalDateTime.now());
        
        kafkaTemplate.send("circuit-breaker-metrics", serviceName, metrics);
    }
    
    /**
     * Record failure metrics
     */
    private void recordFailureMetrics(String serviceName, String methodSignature, Exception e, long duration) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("service", serviceName);
        metrics.put("method", methodSignature);
        metrics.put("status", "FAILURE");
        metrics.put("error", e.getClass().getSimpleName());
        metrics.put("errorMessage", e.getMessage());
        metrics.put("duration", duration);
        metrics.put("timestamp", LocalDateTime.now());
        
        kafkaTemplate.send("circuit-breaker-metrics", serviceName, metrics);
        
        // Send alert for critical services
        if (!isNonCriticalService(serviceName)) {
            sendCriticalServiceAlert(serviceName, methodSignature, e);
        }
    }
    
    /**
     * Send alert for critical service failures
     */
    private void sendCriticalServiceAlert(String serviceName, String methodSignature, Exception e) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "CRITICAL_SERVICE_CIRCUIT_BREAKER");
        alert.put("service", serviceName);
        alert.put("method", methodSignature);
        alert.put("error", e.getMessage());
        alert.put("timestamp", LocalDateTime.now());
        alert.put("severity", "HIGH");
        
        kafkaTemplate.send("critical-alerts", alert);
    }
}

/**
 * Annotation to mark methods as external service calls
 */
@java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD})
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@interface ExternalServiceCall {
    String serviceName();
    boolean enableFallback() default true;
}