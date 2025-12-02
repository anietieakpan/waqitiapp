package com.waqiti.payment.client;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class RTPNetworkClientConfiguration {

    @Bean
    public Logger.Level rtpNetworkFeignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public Request.Options rtpNetworkRequestOptions() {
        return new Request.Options(
            10, TimeUnit.SECONDS,  // Connection timeout
            30, TimeUnit.SECONDS,  // Read timeout
            true                   // Follow redirects
        );
    }

    @Bean
    public CircuitBreaker rtpNetworkCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slowCallRateThreshold(50)
            .slowCallDurationThreshold(Duration.ofSeconds(3))
            .permittedNumberOfCallsInHalfOpenState(5)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .build();

        return CircuitBreaker.of("rtp-network-service", config);
    }

    @Bean
    public Retry rtpNetworkRetry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(2)
            .waitDuration(Duration.ofMillis(500))
            .exponentialBackoffMultiplier(2)
            .retryExceptions(RuntimeException.class, feign.RetryableException.class)
            .build();

        return Retry.of("rtp-network-service", config);
    }
}