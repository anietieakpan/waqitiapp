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

@Configuration
public class FraudDetectionClientConfiguration {

    @Bean
    public Logger.Level fraudDetectionFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Request.Options fraudDetectionRequestOptions() {
        return new Request.Options(
            5, TimeUnit.SECONDS,   // Connection timeout
            15, TimeUnit.SECONDS,  // Read timeout
            true                   // Follow redirects
        );
    }

    @Bean
    public CircuitBreaker fraudDetectionCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(30)
            .slowCallRateThreshold(30)
            .slowCallDurationThreshold(Duration.ofSeconds(5))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slidingWindowSize(20)
            .minimumNumberOfCalls(10)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .build();

        return CircuitBreaker.of("fraud-detection-service", config);
    }

    @Bean
    public Retry fraudDetectionRetry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(2))
            .exponentialBackoffMultiplier(2)
            .retryExceptions(RuntimeException.class, feign.RetryableException.class)
            .build();

        return Retry.of("fraud-detection-service", config);
    }
}