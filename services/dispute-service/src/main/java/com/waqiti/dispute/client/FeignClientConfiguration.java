package com.waqiti.dispute.client;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Feign Client Configuration
 *
 * Configures timeouts, retry logic, logging, and error handling
 * for all Feign clients in the dispute service
 *
 * @author Waqiti Platform Team
 * @version 1.0
 */
@Configuration
@Slf4j
public class FeignClientConfiguration {

    @Value("${feign.client.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${feign.client.read-timeout:10000}")
    private int readTimeout;

    /**
     * Configure connection and read timeouts
     */
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
                connectTimeout, TimeUnit.MILLISECONDS,
                readTimeout, TimeUnit.MILLISECONDS,
                true  // Follow redirects
        );
    }

    /**
     * Configure retry logic with exponential backoff
     */
    @Bean
    public Retryer retryer() {
        return new Retryer.Default(
                100,     // Initial interval (ms)
                1000,    // Max interval (ms)
                3        // Max attempts
        );
    }

    /**
     * Configure logging level for Feign clients
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;  // Log request/response info
    }

    /**
     * Custom error decoder for Feign client errors
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            log.error("Feign client error - Method: {}, Status: {}, Reason: {}",
                    methodKey, response.status(), response.reason());

            // You can customize error handling based on status codes
            switch (response.status()) {
                case 404:
                    return new ResourceNotFoundException("Resource not found: " + methodKey);
                case 503:
                    return new ServiceUnavailableException("Service unavailable: " + methodKey);
                case 401:
                case 403:
                    return new UnauthorizedException("Unauthorized access: " + methodKey);
                default:
                    return new FeignClientException("Feign client error: " + response.reason());
            }
        };
    }

    // Custom exceptions
    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }

    public static class FeignClientException extends RuntimeException {
        public FeignClientException(String message) {
            super(message);
        }
    }
}
