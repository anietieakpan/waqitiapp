package com.waqiti.common.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;

/**
 * AOP aspect for automatic metrics collection on REST endpoints
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsAspect {
    
    private final MetricsService metricsService;
    
    /**
     * Automatically capture metrics for all REST controller methods
     */
    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object captureEndpointMetrics(ProceedingJoinPoint joinPoint) throws Throwable {
        Instant start = Instant.now();
        String endpoint = getEndpointPath();
        String method = getHttpMethod();
        int statusCode = 200; // Default success
        
        try {
            Object result = joinPoint.proceed();
            
            // Extract status code from ResponseEntity if available
            if (result instanceof ResponseEntity<?>) {
                ResponseEntity<?> response = (ResponseEntity<?>) result;
                statusCode = response.getStatusCode().value();
            }
            
            return result;
            
        } catch (Exception e) {
            statusCode = 500; // Server error
            throw e;
            
        } finally {
            Duration duration = Duration.between(start, Instant.now());
            
            // Record endpoint metrics
            metricsService.recordEndpointMetrics(endpoint, method, statusCode, duration);
            
            // Record operation success/failure
            if (statusCode < 400) {
                metricsService.recordSuccessfulOperation(
                    joinPoint.getSignature().getName(), 
                    duration
                );
            } else {
                metricsService.recordFailedOperation(
                    joinPoint.getSignature().getName(),
                    "HTTP_" + statusCode
                );
            }
        }
    }
    
    private String getEndpointPath() {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getRequestURI();
            }
            
        } catch (Exception e) {
            log.debug("Could not extract endpoint path", e);
        }
        
        return "unknown";
    }
    
    private String getHttpMethod() {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getMethod();
            }
            
        } catch (Exception e) {
            log.debug("Could not extract HTTP method", e);
        }
        
        return "unknown";
    }
}