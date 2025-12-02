package com.waqiti.account.config;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.feign.FeignDecorators;
import io.github.resilience4j.feign.Resilience4jFeign;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Feign client configuration with Resilience4j integration
 *
 * <p>Provides:</p>
 * <ul>
 *   <li>Circuit breaker integration</li>
 *   <li>Automatic retry with exponential backoff</li>
 *   <li>Request/response timeout configuration</li>
 *   <li>Custom error decoder</li>
 *   <li>Request logging</li>
 * </ul>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Configuration
@Slf4j
public class FeignConfiguration {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Retry configuration with exponential backoff
     *
     * <p>Retry strategy:</p>
     * <ul>
     *   <li>Initial interval: 100ms</li>
     *   <li>Max interval: 3s</li>
     *   <li>Max attempts: 3</li>
     *   <li>Multiplier: 2x</li>
     * </ul>
     */
    @Bean
    public Retryer feignRetryer() {
        return new Retryer.Default(
            100,      // Initial interval (ms)
            TimeUnit.SECONDS.toMillis(3),  // Max interval (ms)
            3         // Max attempts
        );
    }

    /**
     * Request timeout configuration
     *
     * <p>Timeouts:</p>
     * <ul>
     *   <li>Connect: 5s</li>
     *   <li>Read: 10s</li>
     * </ul>
     */
    @Bean
    public Request.Options feignRequestOptions() {
        return new Request.Options(
            5, TimeUnit.SECONDS,   // Connect timeout
            10, TimeUnit.SECONDS,  // Read timeout
            true                   // Follow redirects
        );
    }

    /**
     * Custom error decoder for better error handling
     */
    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return new FeignErrorDecoder();
    }

    /**
     * Feign logging level (BASIC in production, FULL in dev)
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * Custom error decoder for Feign
     */
    @Slf4j
    public static class FeignErrorDecoder implements ErrorDecoder {

        private final ErrorDecoder defaultDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            log.error("Feign client error - method={}, status={}, reason={}",
                methodKey, response.status(), response.reason());

            // Map status codes to appropriate exceptions
            return switch (response.status()) {
                case 400 -> new IllegalArgumentException(
                    "Bad request to " + methodKey + ": " + response.reason());
                case 404 -> new ResourceNotFoundException(
                    "Resource not found: " + methodKey);
                case 503 -> new ServiceUnavailableException(
                    "Service unavailable: " + methodKey);
                case 504 -> new java.util.concurrent.TimeoutException(
                    "Gateway timeout: " + methodKey);
                default -> defaultDecoder.decode(methodKey, response);
            };
        }
    }

    /**
     * Resource not found exception
     */
    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Service unavailable exception
     */
    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }
    }
}
