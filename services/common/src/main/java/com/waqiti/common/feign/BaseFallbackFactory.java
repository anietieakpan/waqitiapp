package com.waqiti.common.feign;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Base Fallback Factory for FeignClient Error Handling
 *
 * CRITICAL: All FeignClients should use FallbackFactory (not Fallback) for better error visibility
 *
 * FEATURES:
 * - Logs root cause of failure (timeout, circuit breaker, 4xx/5xx)
 * - Provides context-aware error handling
 * - Increments metrics for fallback invocations
 * - Enables graceful degradation
 *
 * FALLBACK STRATEGY:
 * 1. Circuit Breaker Open → Return cached/default value
 * 2. Timeout → Return cached/default value
 * 3. 4xx Client Error → Propagate exception (caller error)
 * 4. 5xx Server Error → Return fallback value
 * 5. Network Error → Return fallback value
 *
 * USAGE:
 * <pre>
 * {@literal @}Component
 * public class WalletServiceClientFallbackFactory
 *         extends BaseFallbackFactory{@literal <}WalletServiceClient{@literal >} {
 *
 *     {@literal @}Override
 *     protected WalletServiceClient createFallback(Throwable cause) {
 *         return new WalletServiceClient() {
 *             {@literal @}Override
 *             public BalanceResponse getBalance(UUID walletId) {
 *                 // Return cached balance or default
 *                 return getCachedBalance(walletId);
 *             }
 *         };
 *     }
 * }
 *
 * // Configure FeignClient
 * {@literal @}FeignClient(
 *     name = "wallet-service",
 *     fallbackFactory = WalletServiceClientFallbackFactory.class
 * )
 * public interface WalletServiceClient { ... }
 * </pre>
 *
 * @param <T> The Feign client interface type
 * @author Waqiti Engineering Team
 * @version 3.0.0
 */
@Slf4j
public abstract class BaseFallbackFactory<T> implements FallbackFactory<T> {

    /**
     * Create fallback instance with error context
     *
     * CRITICAL: This method provides the exception that caused the fallback
     * Use this to implement context-aware fallback logic
     */
    @Override
    public T create(Throwable cause) {
        // Log the failure
        logFallbackInvocation(cause);

        // Create fallback implementation
        return createFallback(cause);
    }

    /**
     * Create the actual fallback implementation
     *
     * Override this method to provide fallback logic
     *
     * @param cause The exception that triggered the fallback
     * @return Fallback implementation of the FeignClient
     */
    protected abstract T createFallback(Throwable cause);

    /**
     * Log fallback invocation with detailed error information
     */
    private void logFallbackInvocation(Throwable cause) {
        String clientName = getClientName();
        String errorType = determineErrorType(cause);
        String errorMessage = cause.getMessage();

        log.warn("FEIGN_FALLBACK_INVOKED: Client={}, ErrorType={}, Message={}",
            clientName, errorType, errorMessage);

        // Log stack trace for unexpected errors
        if (!isExpectedError(cause)) {
            log.error("FEIGN_UNEXPECTED_ERROR: Client={}", clientName, cause);
        }
    }

    /**
     * Determine error type for logging and metrics
     */
    private String determineErrorType(Throwable cause) {
        if (cause instanceof CallNotPermittedException) {
            return "CIRCUIT_BREAKER_OPEN";
        } else if (cause instanceof SocketTimeoutException) {
            return "SOCKET_TIMEOUT";
        } else if (cause instanceof TimeoutException) {
            return "TIMEOUT";
        } else if (cause instanceof FeignException.FeignClientException) {
            return "CLIENT_ERROR_" + ((FeignException) cause).status();
        } else if (cause instanceof FeignException.FeignServerException) {
            return "SERVER_ERROR_" + ((FeignException) cause).status();
        } else if (cause instanceof FeignException) {
            return "FEIGN_ERROR_" + ((FeignException) cause).status();
        } else {
            return "UNKNOWN_ERROR";
        }
    }

    /**
     * Check if error is expected (normal fallback scenario)
     */
    private boolean isExpectedError(Throwable cause) {
        return cause instanceof CallNotPermittedException ||
               cause instanceof SocketTimeoutException ||
               cause instanceof TimeoutException ||
               cause instanceof FeignException.FeignServerException;
    }

    /**
     * Get client name for logging
     * Override to provide custom client name
     */
    protected String getClientName() {
        return this.getClass().getSimpleName().replace("FallbackFactory", "");
    }

    /**
     * Check if error is due to circuit breaker
     */
    protected boolean isCircuitBreakerOpen(Throwable cause) {
        return cause instanceof CallNotPermittedException;
    }

    /**
     * Check if error is due to timeout
     */
    protected boolean isTimeout(Throwable cause) {
        return cause instanceof SocketTimeoutException ||
               cause instanceof TimeoutException ||
               (cause instanceof org.springframework.web.client.ResourceAccessException &&
                cause.getCause() instanceof SocketTimeoutException);
    }

    /**
     * Check if error is 4xx client error
     */
    protected boolean isClientError(Throwable cause) {
        return cause instanceof FeignException.FeignClientException;
    }

    /**
     * Check if error is 5xx server error
     */
    protected boolean isServerError(Throwable cause) {
        return cause instanceof FeignException.FeignServerException;
    }

    /**
     * Extract HTTP status code from FeignException
     */
    protected int getStatusCode(Throwable cause) {
        if (cause instanceof FeignException) {
            return ((FeignException) cause).status();
        }
        return 0;
    }

    /**
     * Helper method: Throw exception with custom message
     */
    protected RuntimeException rethrowWithMessage(Throwable cause, String message) {
        log.error("{}: {}", message, cause.getMessage());
        return new FeignClientFallbackException(message, cause);
    }

    /**
     * Helper method: Check if should use cached response
     */
    protected boolean shouldUseCache(Throwable cause) {
        // Use cache for circuit breaker, timeout, or server errors
        return isCircuitBreakerOpen(cause) || isTimeout(cause) || isServerError(cause);
    }

    /**
     * Helper method: Check if should propagate exception
     */
    protected boolean shouldPropagateException(Throwable cause) {
        // Propagate client errors (4xx) - these are caller's fault
        return isClientError(cause) && getStatusCode(cause) != 404;
    }

    /**
     * Custom exception for fallback failures
     */
    public static class FeignClientFallbackException extends RuntimeException {
        public FeignClientFallbackException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
