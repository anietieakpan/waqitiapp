package com.waqiti.corebanking.client;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import java.util.concurrent.TimeUnit;

/**
 * Feign client configuration for all service integrations
 * Provides common configuration for timeouts, retries, and error handling
 */
@Configuration
public class FeignClientConfiguration {

    @Value("${feign.client.config.default.connectTimeout:5000}")
    private int connectTimeout;

    @Value("${feign.client.config.default.readTimeout:10000}")
    private int readTimeout;

    @Value("${feign.client.config.default.loggerLevel:BASIC}")
    private String loggerLevel;

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.valueOf(loggerLevel);
    }

    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
            connectTimeout, TimeUnit.MILLISECONDS,
            readTimeout, TimeUnit.MILLISECONDS,
            true
        );
    }

    @Bean
    public Retryer retryer() {
        // Retry with exponential backoff: 100ms initial, 1 second max, 3 attempts
        return new Retryer.Default(100, TimeUnit.SECONDS.toMillis(1), 3);
    }

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            // Add correlation ID for distributed tracing
            requestTemplate.header("X-Correlation-Id", getCorrelationId());
            requestTemplate.header("X-Service-Name", "core-banking-service");
            requestTemplate.header("Accept", "application/json");
            requestTemplate.header("Content-Type", "application/json");
        };
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new CustomErrorDecoder();
    }

    private String getCorrelationId() {
        // In production, get from MDC or generate new one
        return java.util.UUID.randomUUID().toString();
    }

    /**
     * Custom error decoder for Feign clients
     */
    public static class CustomErrorDecoder implements ErrorDecoder {
        
        private final ErrorDecoder defaultErrorDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            HttpStatus statusCode = HttpStatus.valueOf(response.status());

            if (statusCode.is4xxClientError()) {
                switch (statusCode) {
                    case BAD_REQUEST:
                        return new ServiceClientException.BadRequestException(
                            "Bad request to " + methodKey);
                    case UNAUTHORIZED:
                        return new ServiceClientException.UnauthorizedException(
                            "Unauthorized access to " + methodKey);
                    case FORBIDDEN:
                        return new ServiceClientException.ForbiddenException(
                            "Forbidden access to " + methodKey);
                    case NOT_FOUND:
                        return new ServiceClientException.NotFoundException(
                            "Resource not found for " + methodKey);
                    default:
                        return new ServiceClientException.ClientException(
                            "Client error for " + methodKey + ": " + response.status());
                }
            }

            if (statusCode.is5xxServerError()) {
                return new ServiceClientException.ServerException(
                    "Server error for " + methodKey + ": " + response.status());
            }

            return defaultErrorDecoder.decode(methodKey, response);
        }
    }

    /**
     * Custom exceptions for service client errors
     */
    public static class ServiceClientException extends RuntimeException {
        
        public ServiceClientException(String message) {
            super(message);
        }

        public ServiceClientException(String message, Throwable cause) {
            super(message, cause);
        }

        public static class BadRequestException extends ServiceClientException {
            public BadRequestException(String message) {
                super(message);
            }
        }

        public static class UnauthorizedException extends ServiceClientException {
            public UnauthorizedException(String message) {
                super(message);
            }
        }

        public static class ForbiddenException extends ServiceClientException {
            public ForbiddenException(String message) {
                super(message);
            }
        }

        public static class NotFoundException extends ServiceClientException {
            public NotFoundException(String message) {
                super(message);
            }
        }

        public static class ClientException extends ServiceClientException {
            public ClientException(String message) {
                super(message);
            }
        }

        public static class ServerException extends ServiceClientException {
            public ServerException(String message) {
                super(message);
            }
        }
    }
}