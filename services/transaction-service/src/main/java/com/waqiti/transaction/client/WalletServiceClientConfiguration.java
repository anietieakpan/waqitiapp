package com.waqiti.transaction.client;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for WalletServiceClient Feign client.
 *
 * Provides:
 * - Custom timeout configuration
 * - Retry policy with exponential backoff
 * - Error decoder for proper exception handling
 * - Request/Response logging
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Configuration
@Slf4j
public class WalletServiceClientConfiguration {

    @Value("${feign.client.config.wallet-service.connect-timeout:3000}")
    private int connectTimeout;

    @Value("${feign.client.config.wallet-service.read-timeout:5000}")
    private int readTimeout;

    /**
     * Configures request timeouts for wallet service calls.
     *
     * Wallet service is critical for authorization, so we use strict timeouts:
     * - Connect timeout: 3 seconds (service should respond quickly)
     * - Read timeout: 5 seconds (ownership checks are simple queries)
     *
     * @return Request.Options with configured timeouts
     */
    @Bean
    public Request.Options walletServiceRequestOptions() {
        return new Request.Options(
            connectTimeout, TimeUnit.MILLISECONDS,
            readTimeout, TimeUnit.MILLISECONDS,
            true // Follow redirects
        );
    }

    /**
     * Configures retry policy for wallet service calls.
     *
     * Retry configuration:
     * - Max attempts: 3
     * - Initial interval: 500ms
     * - Max interval: 2000ms
     * - Multiplier: 1.5 (exponential backoff)
     *
     * Only retries on network errors and 503 Service Unavailable.
     *
     * @return Retryer with exponential backoff
     */
    @Bean
    public Retryer walletServiceRetryer() {
        return new Retryer.Default(
            500L,    // Initial interval (ms)
            2000L,   // Max interval (ms)
            3        // Max attempts
        );
    }

    /**
     * Custom error decoder for wallet service responses.
     *
     * Converts HTTP errors to appropriate exceptions:
     * - 404 → ResourceNotFoundException
     * - 403 → AccessDeniedException
     * - 503 → ServiceUnavailableException
     * - Others → FeignException
     *
     * @return ErrorDecoder implementation
     */
    @Bean
    public ErrorDecoder walletServiceErrorDecoder() {
        return (methodKey, response) -> {
            HttpStatus status = HttpStatus.valueOf(response.status());

            String message = String.format("Wallet service error: %s %s",
                                          response.status(),
                                          response.reason());

            log.error("Wallet service returned error: status={}, method={}, reason={}",
                     status, methodKey, response.reason());

            switch (status) {
                case NOT_FOUND:
                    return new com.waqiti.common.exception.ResourceNotFoundException(
                        "Wallet not found",
                        "WALLET_NOT_FOUND"
                    );

                case FORBIDDEN:
                    return new org.springframework.security.access.AccessDeniedException(
                        "Access denied to wallet"
                    );

                case SERVICE_UNAVAILABLE:
                    return new com.waqiti.common.exception.ServiceUnavailableException(
                        "Wallet service is temporarily unavailable",
                        "WALLET_SERVICE_UNAVAILABLE"
                    );

                case TOO_MANY_REQUESTS:
                    return new com.waqiti.common.exception.RateLimitExceededException(
                        "Rate limit exceeded for wallet service",
                        "WALLET_SERVICE_RATE_LIMIT"
                    );

                case BAD_REQUEST:
                    return new IllegalArgumentException("Invalid request to wallet service: " + message);

                default:
                    return new feign.FeignException.FeignClientException(
                        response.status(),
                        message,
                        response.request(),
                        response.body() != null ? response.body().array() : null,
                        response.headers()
                    );
            }
        };
    }

    /**
     * Configures Feign logging level.
     *
     * - NONE: No logging (production default)
     * - BASIC: Log request method, URL, response status, execution time
     * - HEADERS: Log basic + request/response headers
     * - FULL: Log everything (use only for debugging)
     *
     * @return Logger.Level
     */
    @Bean
    public Logger.Level walletServiceLoggerLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * Custom request interceptor to add tracing headers.
     *
     * Adds correlation ID and other headers for distributed tracing.
     *
     * @return RequestInterceptor
     */
    @Bean
    public feign.RequestInterceptor walletServiceRequestInterceptor() {
        return requestTemplate -> {
            // Add correlation ID for tracing
            String correlationId = org.slf4j.MDC.get("correlationId");
            if (correlationId != null) {
                requestTemplate.header("X-Correlation-ID", correlationId);
            }

            // Add service identifier
            requestTemplate.header("X-Client-Service", "transaction-service");

            // Add request timestamp
            requestTemplate.header("X-Request-Time",
                                 String.valueOf(System.currentTimeMillis()));
        };
    }
}
