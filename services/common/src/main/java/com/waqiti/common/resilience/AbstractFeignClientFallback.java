package com.waqiti.common.resilience;

import com.waqiti.common.audit.AuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * ABSTRACT FEIGN CLIENT FALLBACK
 *
 * Base class for all Feign client fallback implementations.
 * Provides common functionality for graceful degradation when services are unavailable.
 *
 * RESILIENCE PATTERN: Circuit Breaker + Fallback
 * When a service is unavailable, instead of failing, we:
 * 1. Log the failure for monitoring
 * 2. Audit the fallback execution
 * 3. Return cached data or safe defaults
 * 4. Queue operations for async processing
 *
 * PRODUCTION IMPACT:
 * - Prevents cascading failures
 * - Maintains system availability during outages
 * - Provides degraded but functional service
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-01-01
 */
@Slf4j
public abstract class AbstractFeignClientFallback {

    @Autowired(required = false)
    protected AuditService auditService;

    /**
     * Get the service name for logging/auditing
     */
    protected abstract String getServiceName();

    /**
     * Log fallback execution
     */
    protected void logFallback(String operation, Throwable cause) {
        log.warn("FALLBACK: {} service unavailable - Operation: {}, Cause: {}",
            getServiceName(), operation, cause != null ? cause.getMessage() : "Unknown");

        if (auditService != null) {
            auditService.logServiceFailure(
                getServiceName(),
                operation,
                cause != null ? cause.getMessage() : "Service unavailable",
                Map.of(
                    "fallbackTriggered", "true",
                    "timestamp", LocalDateTime.now().toString()
                )
            );
        }
    }

    /**
     * Log successful cache hit
     */
    protected void logCacheHit(String operation, Object cachedData) {
        log.info("FALLBACK: Using cached data for {} - Operation: {}",
            getServiceName(), operation);

        if (auditService != null) {
            auditService.logCacheEvent(
                "FALLBACK_CACHE_HIT",
                getServiceName(),
                operation,
                "Using cached data during service outage"
            );
        }
    }

    /**
     * Log queued operation for async processing
     */
    protected void logQueuedOperation(String operation, Object operationData) {
        log.info("FALLBACK: Queueing operation for async processing - Service: {}, Operation: {}",
            getServiceName(), operation);

        if (auditService != null) {
            auditService.logAsyncOperation(
                "OPERATION_QUEUED",
                getServiceName(),
                operation,
                "Operation queued for async processing during service outage"
            );
        }
    }

    /**
     * Create fallback response with error indicator
     */
    protected <T> T createFallbackResponse(Class<T> responseType, String message) {
        log.debug("FALLBACK: Creating fallback response for {} - Type: {}, Message: {}",
            getServiceName(), responseType.getSimpleName(), message);

        try {
            T response = responseType.getDeclaredConstructor().newInstance();
            // Set error flag if response has setError method
            try {
                responseType.getMethod("setError", Boolean.class).invoke(response, true);
                responseType.getMethod("setErrorMessage", String.class).invoke(response, message);
            } catch (NoSuchMethodException e) {
                // Response doesn't have error fields, that's OK
            }
            return response;
        } catch (Exception e) {
            log.error("FALLBACK: Failed to create fallback response for {}",
                getServiceName(), e);
            return null;
        }
    }
}
