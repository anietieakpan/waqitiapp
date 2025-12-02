package com.waqiti.payment.client;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.feign.FeignDecorators;
import io.github.resilience4j.feign.Resilience4jFeign;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for NotificationServiceClient with resilience patterns
 */
@Configuration
public class NotificationServiceClientConfiguration {

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
            10, TimeUnit.SECONDS,  // Connection timeout
            30, TimeUnit.SECONDS,  // Read timeout
            true                   // Follow redirects
        );
    }

    @Bean
    public Retryer retryer() {
        // Retry with exponential backoff
        return new Retryer.Default(100, TimeUnit.SECONDS.toMillis(1), 3);
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new NotificationServiceErrorDecoder();
    }

    @Bean
    public CircuitBreaker notificationServiceCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slowCallRateThreshold(50)
            .slowCallDurationThreshold(Duration.ofSeconds(2))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();

        return CircuitBreaker.of("notification-service", config);
    }

    @Bean
    public Retry notificationServiceRetry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .exponentialBackoffMultiplier(2)
            .retryExceptions(
                RuntimeException.class,
                feign.RetryableException.class
            )
            .build();

        return Retry.of("notification-service", config);
    }

    @Bean
    public Resilience4jFeign.Builder notificationServiceFeignBuilder(
            CircuitBreaker notificationServiceCircuitBreaker,
            Retry notificationServiceRetry) {
        
        FeignDecorators decorators = FeignDecorators.builder()
            .withCircuitBreaker(notificationServiceCircuitBreaker)
            .withRetry(notificationServiceRetry)
            .build();

        return Resilience4jFeign.builder(decorators);
    }

    /**
     * Custom error decoder for notification service
     */
    public static class NotificationServiceErrorDecoder implements ErrorDecoder {
        private final ErrorDecoder defaultErrorDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            if (response.status() >= 400 && response.status() < 500) {
                return new NotificationClientException(
                    String.format("Client error calling notification service: %s", response.reason())
                );
            } else if (response.status() >= 500) {
                return new NotificationServerException(
                    String.format("Server error from notification service: %s", response.reason())
                );
            }
            return defaultErrorDecoder.decode(methodKey, response);
        }
    }

    /**
     * Client error exception
     */
    public static class NotificationClientException extends RuntimeException {
        public NotificationClientException(String message) {
            super(message);
        }
    }

    /**
     * Server error exception
     */
    public static class NotificationServerException extends RuntimeException {
        public NotificationServerException(String message) {
            super(message);
        }
    }
}