package com.waqiti.transaction.client;

import feign.Client;
import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import feign.okhttp.OkHttpClient;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Production-ready Feign client configuration for Fraud Detection Service.
 * Implements comprehensive error handling, retry logic, and observability.
 */
@Slf4j
@Configuration
public class FraudDetectionClientConfiguration {

    @Value("${services.fraud-detection.timeout.connect:5000}")
    private int connectTimeout;

    @Value("${services.fraud-detection.timeout.read:10000}")
    private int readTimeout;

    @Value("${services.fraud-detection.retry.maxAttempts:3}")
    private int maxAttempts;

    @Value("${services.fraud-detection.retry.period:1000}")
    private long retryPeriod;

    @Value("${services.fraud-detection.retry.maxPeriod:5000}")
    private long maxRetryPeriod;

    /**
     * Configure request options with appropriate timeouts
     */
    @Bean("fraudDetectionRequestOptions")
    public Request.Options requestOptions() {
        return new Request.Options(
            connectTimeout, TimeUnit.MILLISECONDS,
            readTimeout, TimeUnit.MILLISECONDS,
            true // followRedirects
        );
    }

    /**
     * Configure retry strategy with exponential backoff
     */
    @Bean("fraudDetectionRetryer")
    public Retryer retryer() {
        return new Retryer.Default(retryPeriod, maxRetryPeriod, maxAttempts);
    }

    /**
     * Configure HTTP client with connection pooling and SSL
     */
    @Bean("fraudDetectionHttpClient")
    public Client feignClient() {
        okhttp3.OkHttpClient.Builder builder = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
            .writeTimeout(readTimeout, TimeUnit.MILLISECONDS)
            .connectionPool(new okhttp3.ConnectionPool(50, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true);

        // Add interceptors for authentication, logging, etc.
        builder.addInterceptor(chain -> {
            okhttp3.Request original = chain.request();
            okhttp3.Request.Builder requestBuilder = original.newBuilder()
                .header("User-Agent", "Waqiti-Transaction-Service/1.0")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
                
            // Add correlation ID for tracing
            String correlationId = getCorrelationId();
            if (correlationId != null) {
                requestBuilder.header("X-Correlation-ID", correlationId);
            }

            okhttp3.Request request = requestBuilder.build();
            return chain.proceed(request);
        });

        return new OkHttpClient(builder.build());
    }

    /**
     * Configure custom error decoder for proper exception handling
     */
    @Bean("fraudDetectionErrorDecoder")
    public ErrorDecoder errorDecoder() {
        return new FraudDetectionErrorDecoder();
    }

    /**
     * Configure logging level for debugging
     */
    @Bean("fraudDetectionLoggerLevel")
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC; // Use FULL for debugging, BASIC for production
    }

    /**
     * Custom error decoder for Fraud Detection Service
     */
    private static class FraudDetectionErrorDecoder implements ErrorDecoder {
        private final Default defaultDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            switch (response.status()) {
                case 400:
                    return new FraudDetectionBadRequestException("Invalid fraud check request");
                case 401:
                    return new FraudDetectionUnauthorizedException("Unauthorized access to fraud detection service");
                case 403:
                    return new FraudDetectionForbiddenException("Access forbidden to fraud detection service");
                case 404:
                    return new FraudDetectionNotFoundException("Fraud detection resource not found");
                case 429:
                    return new FraudDetectionRateLimitException("Rate limit exceeded for fraud detection service");
                case 500:
                    return new FraudDetectionServiceException("Internal error in fraud detection service");
                case 503:
                    return new FraudDetectionServiceUnavailableException("Fraud detection service temporarily unavailable");
                default:
                    return defaultDecoder.decode(methodKey, response);
            }
        }
    }

    /**
     * Get correlation ID from current context (MDC, request headers, etc.)
     */
    private String getCorrelationId() {
        // Implementation depends on your tracing solution
        // This could come from MDC, Spring Cloud Sleuth, etc.
        return org.slf4j.MDC.get("correlationId");
    }

    // Custom exception classes for better error handling
    public static class FraudDetectionException extends RuntimeException {
        public FraudDetectionException(String message) {
            super(message);
        }
        public FraudDetectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class FraudDetectionBadRequestException extends FraudDetectionException {
        public FraudDetectionBadRequestException(String message) {
            super(message);
        }
    }

    public static class FraudDetectionUnauthorizedException extends FraudDetectionException {
        public FraudDetectionUnauthorizedException(String message) {
            super(message);
        }
    }

    public static class FraudDetectionForbiddenException extends FraudDetectionException {
        public FraudDetectionForbiddenException(String message) {
            super(message);
        }
    }

    public static class FraudDetectionNotFoundException extends FraudDetectionException {
        public FraudDetectionNotFoundException(String message) {
            super(message);
        }
    }

    public static class FraudDetectionRateLimitException extends FraudDetectionException {
        public FraudDetectionRateLimitException(String message) {
            super(message);
        }
    }

    public static class FraudDetectionServiceException extends FraudDetectionException {
        public FraudDetectionServiceException(String message) {
            super(message);
        }
    }

    public static class FraudDetectionServiceUnavailableException extends FraudDetectionException {
        public FraudDetectionServiceUnavailableException(String message) {
            super(message);
        }
    }
}