package com.waqiti.virtualcard.client;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Feign Client Configuration
 *
 * Configures:
 * - Request/response timeouts
 * - Retry policy
 * - Error handling
 * - Logging level
 */
@Slf4j
@Configuration
public class FeignClientConfiguration {

    /**
     * Configure request options (timeouts)
     */
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
            5000, TimeUnit.MILLISECONDS,  // Connect timeout: 5 seconds
            10000, TimeUnit.MILLISECONDS, // Read timeout: 10 seconds
            true                           // Follow redirects
        );
    }

    /**
     * Configure retry policy
     * - Max 3 attempts
     * - 1 second between retries
     * - 2 second max period
     */
    @Bean
    public Retryer retryer() {
        return new Retryer.Default(
            1000,  // Period between retries (1 second)
            2000,  // Max period between retries (2 seconds)
            3      // Max attempts
        );
    }

    /**
     * Configure error decoder for better error handling
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return new CustomFeignErrorDecoder();
    }

    /**
     * Configure logging level
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC; // Log request method, URL, response status, and execution time
    }

    /**
     * Custom error decoder to handle specific HTTP status codes
     */
    @Slf4j
    static class CustomFeignErrorDecoder implements ErrorDecoder {
        private final ErrorDecoder defaultErrorDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            int status = response.status();

            log.error("Feign client error - method: {}, status: {}, reason: {}",
                methodKey, status, response.reason());

            // Handle specific status codes
            switch (status) {
                case 400:
                    return new IllegalArgumentException("Bad request to " + methodKey);
                case 401:
                    return new SecurityException("Unauthorized access to " + methodKey);
                case 403:
                    return new SecurityException("Forbidden access to " + methodKey);
                case 404:
                    return new ResourceNotFoundException("Resource not found: " + methodKey);
                case 408:
                    return new RequestTimeoutException("Request timeout: " + methodKey);
                case 429:
                    return new RateLimitExceededException("Rate limit exceeded: " + methodKey);
                case 500:
                case 502:
                case 503:
                case 504:
                    return new ServiceUnavailableException("Service unavailable: " + methodKey);
                default:
                    return defaultErrorDecoder.decode(methodKey, response);
            }
        }
    }

    // Custom exceptions
    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    public static class RequestTimeoutException extends RuntimeException {
        public RequestTimeoutException(String message) {
            super(message);
        }
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }

    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }
    }
}
