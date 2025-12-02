package com.waqiti.common.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aspect for handling the @Traced annotation and creating trace spans.
 * This integrates with Spring Cloud Sleuth/Micrometer Tracing for distributed tracing.
 */
@Aspect
@Component
@Slf4j
@ConditionalOnProperty(value = "tracing.enabled", havingValue = "true", matchIfMissing = true)
public class TracingAspect {
    
    private final Tracer tracer;
    private final TracingMetrics tracingMetrics;
    private final Map<String, OperationMetrics> operationMetricsCache = new ConcurrentHashMap<>();
    
    public TracingAspect(Tracer tracer, TracingMetrics tracingMetrics) {
        this.tracer = tracer;
        this.tracingMetrics = tracingMetrics;
    }
    
    @Around("@annotation(traced)")
    public Object trace(ProceedingJoinPoint joinPoint, Traced traced) throws Throwable {
        String operationName = getOperationName(joinPoint, traced);
        Instant startTime = Instant.now();
        
        // Get or create operation metrics
        OperationMetrics metrics = operationMetricsCache.computeIfAbsent(
            operationName, k -> new OperationMetrics(operationName));
        
        // Create a new span for this operation
        Span span = tracer.nextSpan()
                .name(operationName)
                .tag("business.operation", traced.businessOperation())
                .tag("priority", traced.priority().name())
                .tag("class", joinPoint.getTarget().getClass().getName())
                .tag("method", joinPoint.getSignature().getName())
                .tag("thread", Thread.currentThread().getName());
        
        // Add custom tags if specified
        for (String tag : traced.tags()) {
            String[] parts = tag.split("=", 2);
            if (parts.length == 2) {
                span.tag(parts[0].trim(), parts[1].trim());
            }
        }
        
        // Add parameters to span if configured
        if (traced.includeParameters()) {
            addParametersToSpan(span, joinPoint);
        }
        
        // Track concurrent executions
        metrics.incrementConcurrent();
        
        try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
            if (log.isDebugEnabled()) {
                log.debug("Starting traced operation: {} with priority: {} [traceId: {}, spanId: {}]", 
                         operationName, traced.priority(), 
                         span.context().traceId(), span.context().spanId());
            }
            
            // Execute the actual method
            Object result = joinPoint.proceed();
            
            // Calculate execution time
            Duration duration = Duration.between(startTime, Instant.now());
            
            // Add result to span if configured
            if (traced.includeResult() && result != null) {
                addResultToSpan(span, result);
            }
            
            // Add metrics to span
            span.tag("status", "success");
            span.tag("duration.ms", String.valueOf(duration.toMillis()));
            span.tag("concurrent.executions", String.valueOf(metrics.getConcurrentExecutions()));
            
            // Record metrics
            metrics.recordSuccess(duration);
            tracingMetrics.recordOperation(operationName, traced.priority(), true, duration);
            
            if (log.isDebugEnabled()) {
                log.debug("Completed traced operation: {} in {}ms", operationName, duration.toMillis());
            }
            
            return result;
            
        } catch (Exception e) {
            Duration duration = Duration.between(startTime, Instant.now());
            
            // Enhanced error handling
            span.tag("status", "error");
            span.tag("error.type", e.getClass().getName());
            span.tag("error.message", e.getMessage() != null ? e.getMessage() : "No message");
            span.tag("duration.ms", String.valueOf(duration.toMillis()));
            
            // Add stack trace for critical operations
            if (traced.priority() == Traced.TracingPriority.CRITICAL) {
                span.tag("error.stacktrace", getStackTraceString(e));
            }
            
            // Record error metrics
            metrics.recordError(duration, e.getClass().getSimpleName());
            tracingMetrics.recordOperation(operationName, traced.priority(), false, duration);
            
            log.error("Error in traced operation: {} after {}ms - {}", 
                     operationName, duration.toMillis(), e.getMessage(), e);
            throw e;
            
        } finally {
            metrics.decrementConcurrent();
            span.end();
        }
    }
    
    private void addParametersToSpan(Span span, ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] paramNames = signature.getParameterNames();
            
            if (args != null && paramNames != null) {
                Map<String, String> params = new HashMap<>();
                for (int i = 0; i < Math.min(args.length, paramNames.length); i++) {
                    if (args[i] != null) {
                        String value = sanitizeParameterValue(args[i]);
                        params.put(paramNames[i], value);
                    }
                }
                span.tag("parameters", params.toString());
            }
        } catch (Exception e) {
            log.warn("Failed to add parameters to span: {}", e.getMessage());
        }
    }
    
    private void addResultToSpan(Span span, Object result) {
        try {
            String resultString = sanitizeParameterValue(result);
            span.tag("result", resultString);
            span.tag("result.type", result.getClass().getSimpleName());
        } catch (Exception e) {
            log.warn("Failed to add result to span: {}", e.getMessage());
        }
    }
    
    private String sanitizeParameterValue(Object value) {
        if (value == null) {
            return "null";
        }
        
        String str = value.toString();
        // Truncate long strings to avoid span size issues
        if (str.length() > 500) {
            return str.substring(0, 497) + "...";
        }
        
        // Mask sensitive data
        return maskSensitiveData(str);
    }
    
    private String maskSensitiveData(String value) {
        // Mask potential sensitive data patterns
        return value
            .replaceAll("(?i)(password|secret|token|key|credential)=[^\\s]+", "$1=***")
            .replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b", "***@***.***")
            .replaceAll("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b", "****-****-****-****");
    }
    
    private String getStackTraceString(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");
        StackTraceElement[] stackTrace = e.getStackTrace();
        for (int i = 0; i < Math.min(stackTrace.length, 10); i++) {
            sb.append("\tat ").append(stackTrace[i].toString()).append("\n");
        }
        return sb.toString();
    }
    
    private String getOperationName(ProceedingJoinPoint joinPoint, Traced traced) {
        if (!traced.operationName().isEmpty()) {
            return traced.operationName();
        }
        
        // Default to class.method if no operation name specified
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }
    
    /**
     * Internal class to track operation metrics
     */
    private static class OperationMetrics {
        private final String operationName;
        private long totalExecutions = 0;
        private long successCount = 0;
        private long errorCount = 0;
        private long totalDurationMs = 0;
        private int concurrentExecutions = 0;
        private final Map<String, Long> errorTypes = new ConcurrentHashMap<>();
        
        public OperationMetrics(String operationName) {
            this.operationName = operationName;
        }
        
        public synchronized void recordSuccess(Duration duration) {
            totalExecutions++;
            successCount++;
            totalDurationMs += duration.toMillis();
        }
        
        public synchronized void recordError(Duration duration, String errorType) {
            totalExecutions++;
            errorCount++;
            totalDurationMs += duration.toMillis();
            errorTypes.merge(errorType, 1L, Long::sum);
        }
        
        public synchronized void incrementConcurrent() {
            concurrentExecutions++;
        }
        
        public synchronized void decrementConcurrent() {
            concurrentExecutions--;
        }
        
        public int getConcurrentExecutions() {
            return concurrentExecutions;
        }
    }
}