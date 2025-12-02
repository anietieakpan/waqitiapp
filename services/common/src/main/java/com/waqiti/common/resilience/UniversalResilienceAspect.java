package com.waqiti.common.resilience;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Universal Resilience Aspect - Automatically applies resilience patterns to:
 * 1. All external service calls (Feign, RestTemplate, WebClient)
 * 2. All database operations
 * 3. All Kafka producers
 * 4. All async operations
 * 5. All annotated methods
 *
 * This aspect provides defense-in-depth by applying multiple resilience patterns:
 * - Circuit Breaker: Prevents cascading failures
 * - Retry: Handles transient failures
 * - Rate Limiter: Prevents overload
 * - Bulkhead: Isolates resources
 * - Time Limiter: Prevents hanging operations
 *
 * Priority-based configuration:
 * - CRITICAL: payment, settlement, ledger operations
 * - HIGH: compliance, fraud, KYC operations
 * - MEDIUM: notifications, reporting
 * - LOW: background tasks
 *
 * @author Waqiti Platform Team
 * @since P0 Production Readiness Phase
 */
@Slf4j
@Aspect
@Component
public class UniversalResilienceAspect {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final MeterRegistry meterRegistry;

    // Metrics
    private final Counter resilienceSuccessCounter;
    private final Counter resilienceFailureCounter;
    private final Counter circuitBreakerOpenCounter;
    private final Counter retryAttemptCounter;
    private final Timer resilienceExecutionTimer;

    @Autowired
    public UniversalResilienceAspect(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            BulkheadRegistry bulkheadRegistry,
            TimeLimiterRegistry timeLimiterRegistry,
            MeterRegistry meterRegistry) {

        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.timeLimiterRegistry = timeLimiterRegistry;
        this.meterRegistry = meterRegistry;

        // Initialize metrics
        this.resilienceSuccessCounter = Counter.builder("resilience.execution.success")
                .description("Successful executions with resilience patterns")
                .register(meterRegistry);

        this.resilienceFailureCounter = Counter.builder("resilience.execution.failure")
                .description("Failed executions despite resilience patterns")
                .register(meterRegistry);

        this.circuitBreakerOpenCounter = Counter.builder("resilience.circuit_breaker.open")
                .description("Circuit breaker open events")
                .register(meterRegistry);

        this.retryAttemptCounter = Counter.builder("resilience.retry.attempt")
                .description("Retry attempt events")
                .register(meterRegistry);

        this.resilienceExecutionTimer = Timer.builder("resilience.execution.duration")
                .description("Execution duration with resilience patterns")
                .register(meterRegistry);

        log.info("UniversalResilienceAspect initialized with comprehensive resilience patterns");
    }

    // ==================== POINTCUTS ====================

    /**
     * All Feign client calls (external APIs)
     */
    @Pointcut("within(@org.springframework.cloud.openfeign.FeignClient *)")
    public void feignClientCalls() {}

    /**
     * All Spring Data Repository methods (database operations)
     */
    @Pointcut("within(org.springframework.data.repository.Repository+)")
    public void repositoryMethods() {}

    /**
     * All methods annotated with @KafkaListener (Kafka consumers)
     */
    @Pointcut("@annotation(org.springframework.kafka.annotation.KafkaListener)")
    public void kafkaListeners() {}

    /**
     * All Kafka template send operations (Kafka producers)
     */
    @Pointcut("execution(* org.springframework.kafka.core.KafkaTemplate.send*(..))")
    public void kafkaProducers() {}

    /**
     * All methods annotated with @Async (async operations)
     */
    @Pointcut("@annotation(org.springframework.scheduling.annotation.Async)")
    public void asyncMethods() {}

    /**
     * All service layer methods (business logic)
     */
    @Pointcut("within(@org.springframework.stereotype.Service *) && execution(public * *(..))")
    public void serviceMethods() {}

    /**
     * All methods annotated with custom @Resilient
     */
    @Pointcut("@annotation(com.waqiti.common.resilience.annotation.Resilient)")
    public void resilientAnnotated() {}

    /**
     * All methods annotated with @Transactional (database transactions)
     */
    @Pointcut("@annotation(org.springframework.transaction.annotation.Transactional)")
    public void transactionalMethods() {}

    /**
     * Payment processing methods (CRITICAL priority)
     */
    @Pointcut("execution(* com.waqiti.payment.service..*(..))")
    public void paymentOperations() {}

    /**
     * Compliance and fraud detection (HIGH priority)
     */
    @Pointcut("execution(* com.waqiti.compliance.service..*(..))" +
              " || execution(* com.waqiti.fraud*.service..*(..))")
    public void complianceOperations() {}

    // ==================== ADVICES ====================

    /**
     * Apply resilience to ALL external API calls (Feign clients)
     * Priority: Based on service name
     */
    @Around("feignClientCalls()")
    public Object applyResilienceToExternalApis(ProceedingJoinPoint joinPoint) throws Throwable {
        String serviceName = determineServiceName(joinPoint);
        ResiliencePriority priority = determineServicePriority(serviceName);

        log.debug("Applying {} resilience to external API call: {}", priority, serviceName);

        return executeWithResilience(
                joinPoint,
                serviceName,
                priority,
                true,  // Circuit breaker
                true,  // Retry
                true,  // Rate limiter
                true,  // Bulkhead
                true   // Time limiter
        );
    }

    /**
     * Apply resilience to database operations
     * Priority: MEDIUM (performance-critical but less catastrophic than payments)
     */
    @Around("repositoryMethods() || transactionalMethods()")
    public Object applyResilienceToDatabase(ProceedingJoinPoint joinPoint) throws Throwable {
        String operationName = extractOperationName(joinPoint);
        boolean isReadOnly = isReadOnlyOperation(joinPoint);

        log.debug("Applying resilience to database operation: {} (readOnly: {})",
                operationName, isReadOnly);

        return executeWithResilience(
                joinPoint,
                "database-" + operationName,
                ResiliencePriority.MEDIUM,
                true,   // Circuit breaker
                !isReadOnly, // Retry only for write operations
                false,  // No rate limiting for DB
                true,   // Bulkhead for resource isolation
                true    // Time limiter
        );
    }

    /**
     * Apply resilience to Kafka producers
     * Priority: HIGH (messaging failures can cause data loss)
     */
    @Around("kafkaProducers()")
    public Object applyResilienceToKafkaProducers(ProceedingJoinPoint joinPoint) throws Throwable {
        String topic = extractKafkaTopic(joinPoint);

        log.debug("Applying resilience to Kafka producer: topic={}", topic);

        return executeWithResilience(
                joinPoint,
                "kafka-producer-" + topic,
                ResiliencePriority.HIGH,
                true,  // Circuit breaker
                true,  // Retry with exponential backoff
                false, // No rate limiting (Kafka has built-in)
                true,  // Bulkhead
                true   // Time limiter
        );
    }

    /**
     * Apply resilience to async operations
     * Priority: MEDIUM (background tasks, less critical)
     */
    @Around("asyncMethods()")
    public Object applyResilienceToAsyncOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();

        log.debug("Applying resilience to async operation: {}", methodName);

        return executeWithResilience(
                joinPoint,
                "async-" + methodName,
                ResiliencePriority.MEDIUM,
                true,  // Circuit breaker
                true,  // Retry
                false, // No rate limiting
                true,  // Bulkhead
                true   // Time limiter
        );
    }

    /**
     * Apply CRITICAL resilience to payment operations
     */
    @Around("paymentOperations()")
    public Object applyResilienceToPayments(ProceedingJoinPoint joinPoint) throws Throwable {
        String operationName = extractOperationName(joinPoint);

        log.debug("Applying CRITICAL resilience to payment operation: {}", operationName);

        return executeWithResilience(
                joinPoint,
                "payment-" + operationName,
                ResiliencePriority.CRITICAL,
                true,  // Circuit breaker (strict)
                true,  // Retry (with care)
                true,  // Rate limiter
                true,  // Bulkhead
                true   // Time limiter
        );
    }

    /**
     * Apply HIGH resilience to compliance operations
     */
    @Around("complianceOperations()")
    public Object applyResilienceToCompliance(ProceedingJoinPoint joinPoint) throws Throwable {
        String operationName = extractOperationName(joinPoint);

        log.debug("Applying HIGH resilience to compliance operation: {}", operationName);

        return executeWithResilience(
                joinPoint,
                "compliance-" + operationName,
                ResiliencePriority.HIGH,
                true,  // Circuit breaker
                true,  // Retry
                true,  // Rate limiter (external APIs)
                true,  // Bulkhead
                true   // Time limiter
        );
    }

    /**
     * Apply resilience to explicitly annotated methods
     */
    @Around("resilientAnnotated()")
    public Object applyResilienceToAnnotated(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        com.waqiti.common.resilience.annotation.Resilient annotation =
                AnnotationUtils.findAnnotation(method, com.waqiti.common.resilience.annotation.Resilient.class);

        if (annotation != null) {
            String name = annotation.name().isEmpty() ?
                    extractOperationName(joinPoint) : annotation.name();

            log.debug("Applying custom resilience to annotated method: {} (priority: {})",
                    name, annotation.priority());

            return executeWithResilience(
                    joinPoint,
                    name,
                    annotation.priority(),
                    annotation.circuitBreaker(),
                    annotation.retry(),
                    annotation.rateLimiter(),
                    annotation.bulkhead(),
                    annotation.timeLimiter()
            );
        }

        return joinPoint.proceed();
    }

    // ==================== CORE EXECUTION LOGIC ====================

    /**
     * Execute operation with specified resilience patterns
     */
    private Object executeWithResilience(
            ProceedingJoinPoint joinPoint,
            String operationName,
            ResiliencePriority priority,
            boolean useCircuitBreaker,
            boolean useRetry,
            boolean useRateLimiter,
            boolean useBulkhead,
            boolean useTimeLimiter) throws Throwable {

        Timer.Sample timerSample = Timer.start(meterRegistry);

        try {
            Supplier<Object> supplier = () -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            };

            // Apply rate limiter first (prevents overload)
            if (useRateLimiter) {
                RateLimiter rateLimiter = getRateLimiter(operationName, priority);
                supplier = RateLimiter.decorateSupplier(rateLimiter, supplier);
            }

            // Apply bulkhead (resource isolation)
            if (useBulkhead) {
                Bulkhead bulkhead = getBulkhead(operationName, priority);
                supplier = Bulkhead.decorateSupplier(bulkhead, supplier);
            }

            // Apply retry (handle transient failures)
            if (useRetry) {
                Retry retry = getRetry(operationName, priority);
                supplier = Retry.decorateSupplier(retry, supplier);

                // Add retry listener
                retry.getEventPublisher().onRetry(event -> {
                    retryAttemptCounter.increment();
                    log.warn("Retry attempt {} for operation: {} - Reason: {}",
                            event.getNumberOfRetryAttempts(),
                            operationName,
                            event.getLastThrowable().getMessage());
                });
            }

            // Apply circuit breaker (prevent cascading failures)
            if (useCircuitBreaker) {
                CircuitBreaker circuitBreaker = getCircuitBreaker(operationName, priority);
                supplier = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);

                // Add circuit breaker listeners
                circuitBreaker.getEventPublisher()
                        .onStateTransition(event -> {
                            log.warn("Circuit breaker state transition for {}: {} -> {}",
                                    operationName,
                                    event.getStateTransition().getFromState(),
                                    event.getStateTransition().getToState());

                            if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                                circuitBreakerOpenCounter.increment();
                            }
                        })
                        .onError(event -> {
                            log.error("Circuit breaker error for {}: {}",
                                    operationName,
                                    event.getThrowable().getMessage());
                        });
            }

            // Apply time limiter (prevent hanging)
            if (useTimeLimiter) {
                TimeLimiter timeLimiter = getTimeLimiter(operationName, priority);
                CompletableFuture<Object> future = CompletableFuture.supplyAsync(supplier);
                Object result = timeLimiter.executeFutureSupplier(() -> future);

                resilienceSuccessCounter.increment();
                timerSample.stop(resilienceExecutionTimer);

                return result;
            } else {
                Object result = supplier.get();

                resilienceSuccessCounter.increment();
                timerSample.stop(resilienceExecutionTimer);

                return result;
            }

        } catch (Throwable throwable) {
            resilienceFailureCounter.increment();
            timerSample.stop(resilienceExecutionTimer);

            log.error("Resilience-protected operation failed: {} - Error: {}",
                    operationName, throwable.getMessage());

            throw unwrapException(throwable);
        }
    }

    // ==================== HELPER METHODS ====================

    private CircuitBreaker getCircuitBreaker(String name, ResiliencePriority priority) {
        String configName = priority == ResiliencePriority.CRITICAL ? "payment-processing" :
                           priority == ResiliencePriority.HIGH ? "compliance-check" :
                           "notification-service";

        return circuitBreakerRegistry.circuitBreaker(name, configName);
    }

    private Retry getRetry(String name, ResiliencePriority priority) {
        String configName = priority == ResiliencePriority.CRITICAL ? "critical-operations" :
                           "transientFailure";

        return retryRegistry.retry(name, configName);
    }

    private RateLimiter getRateLimiter(String name, ResiliencePriority priority) {
        String configName = name.contains("compliance") || name.contains("sanctions") ?
                           "compliance-api" : "external-api";

        return rateLimiterRegistry.rateLimiter(name, configName);
    }

    private Bulkhead getBulkhead(String name, ResiliencePriority priority) {
        String configName = name.contains("database") ? "database-operations" :
                           name.contains("kafka") ? "kafka-consumers" :
                           "external-services";

        return bulkheadRegistry.bulkhead(name, configName);
    }

    private TimeLimiter getTimeLimiter(String name, ResiliencePriority priority) {
        String configName = name.contains("database") ? "database-operations" : "external-calls";

        return timeLimiterRegistry.timeLimiter(name, configName);
    }

    private String determineServiceName(ProceedingJoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        return className + "." + methodName;
    }

    private ResiliencePriority determineServicePriority(String serviceName) {
        String lower = serviceName.toLowerCase();

        if (lower.contains("payment") || lower.contains("settlement") ||
            lower.contains("ledger") || lower.contains("accounting")) {
            return ResiliencePriority.CRITICAL;
        }

        if (lower.contains("compliance") || lower.contains("fraud") ||
            lower.contains("kyc") || lower.contains("aml")) {
            return ResiliencePriority.HIGH;
        }

        if (lower.contains("notification") || lower.contains("report")) {
            return ResiliencePriority.MEDIUM;
        }

        return ResiliencePriority.LOW;
    }

    private String extractOperationName(ProceedingJoinPoint joinPoint) {
        return joinPoint.getSignature().getDeclaringType().getSimpleName() +
               "." + joinPoint.getSignature().getName();
    }

    private boolean isReadOnlyOperation(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        org.springframework.transaction.annotation.Transactional txAnnotation =
                AnnotationUtils.findAnnotation(method, org.springframework.transaction.annotation.Transactional.class);

        if (txAnnotation != null) {
            return txAnnotation.readOnly();
        }

        String methodName = method.getName().toLowerCase();
        return methodName.startsWith("get") || methodName.startsWith("find") ||
               methodName.startsWith("search") || methodName.startsWith("list") ||
               methodName.startsWith("count") || methodName.startsWith("exists");
    }

    private String extractKafkaTopic(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof String) {
            return (String) args[0];
        }
        return "unknown";
    }

    private Throwable unwrapException(Throwable throwable) {
        if (throwable instanceof RuntimeException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    /**
     * Priority levels for resilience configuration
     */
    public enum ResiliencePriority {
        CRITICAL,  // Financial operations
        HIGH,      // Compliance, fraud, KYC
        MEDIUM,    // Notifications, reporting
        LOW        // Background tasks
    }
}
