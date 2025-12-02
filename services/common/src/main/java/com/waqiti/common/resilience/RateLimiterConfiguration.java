package com.waqiti.common.resilience;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Rate limiter configuration for controlling API and service call rates
 * Protects against abuse and ensures fair resource allocation
 */
@Configuration
@Slf4j
public class RateLimiterConfiguration {

    /**
     * Rate limiter registry for managing rate limiter instances
     */
    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterRegistry registry = RateLimiterRegistry.ofDefaults();
        
        registry.getEventPublisher().onEntryAdded(event ->
            log.info("Rate limiter added: {}", event.getAddedEntry().getName()));
            
        return registry;
    }

    /**
     * Payment API rate limiter
     * Protects payment endpoints from abuse and ensures system stability
     */
    @Bean
    public RateLimiter paymentRateLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(10)  // 10 payment requests
            .limitRefreshPeriod(Duration.ofMinutes(1))  // per minute
            .timeoutDuration(Duration.ofSeconds(5))  // wait up to 5 seconds
            .build();
            
        RateLimiter rateLimiter = registry.rateLimiter("payment-api", config);
        
        rateLimiter.getEventPublisher()
            .onSuccess(event ->
                log.debug("Payment API permission acquired"));
                    
        rateLimiter.getEventPublisher()
            .onFailure(event ->
                log.warn("Payment API rate limit exceeded - request rejected"));
                
        return rateLimiter;
    }

    /**
     * External API rate limiter
     * Controls calls to external services to respect their rate limits
     */
    @Bean
    public RateLimiter externalApiRateLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(100)  // 100 external API calls
            .limitRefreshPeriod(Duration.ofMinutes(1))  // per minute
            .timeoutDuration(Duration.ofSeconds(3))  // wait up to 3 seconds
            .build();
            
        RateLimiter rateLimiter = registry.rateLimiter("external-api", config);
        
        rateLimiter.getEventPublisher()
            .onFailure(event ->
                log.info("External API rate limit reached - throttling requests"));
                
        return rateLimiter;
    }

    /**
     * Database rate limiter
     * Prevents database overload from excessive queries
     */
    @Bean
    public RateLimiter databaseRateLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(1000)  // 1000 database calls
            .limitRefreshPeriod(Duration.ofSeconds(10))  // per 10 seconds
            .timeoutDuration(Duration.ofMilliseconds(500))  // wait up to 500ms
            .build();
            
        RateLimiter rateLimiter = registry.rateLimiter("database", config);
        
        rateLimiter.getEventPublisher()
            .onFailure(event ->
                log.error("Database rate limit exceeded - potential performance issue"));
                
        return rateLimiter;
    }

    /**
     * Authentication rate limiter
     * Protects against brute force attacks
     */
    @Bean
    public RateLimiter authenticationRateLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(5)  // 5 authentication attempts
            .limitRefreshPeriod(Duration.ofMinutes(5))  // per 5 minutes
            .timeoutDuration(Duration.ofSeconds(1))  // immediate rejection after limit
            .build();
            
        RateLimiter rateLimiter = registry.rateLimiter("authentication", config);
        
        rateLimiter.getEventPublisher()
            .onFailure(event ->
                log.warn("Authentication rate limit exceeded - potential brute force attack"));
                
        return rateLimiter;
    }

    /**
     * Notification rate limiter
     * Prevents spam and controls notification costs
     */
    @Bean
    public RateLimiter notificationRateLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(50)  // 50 notifications
            .limitRefreshPeriod(Duration.ofMinutes(1))  // per minute
            .timeoutDuration(Duration.ofSeconds(2))
            .build();
            
        return registry.rateLimiter("notification", config);
    }

    /**
     * File upload rate limiter
     * Controls file upload frequency to prevent abuse
     */
    @Bean
    public RateLimiter fileUploadRateLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(20)  // 20 file uploads
            .limitRefreshPeriod(Duration.ofMinutes(1))  // per minute
            .timeoutDuration(Duration.ofSeconds(10))  // allow longer wait for uploads
            .build();
            
        return registry.rateLimiter("file-upload", config);
    }

    /**
     * Crypto transaction rate limiter
     * Controls cryptocurrency transaction frequency
     */
    @Bean
    public RateLimiter cryptoTransactionRateLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(5)  // 5 crypto transactions
            .limitRefreshPeriod(Duration.ofMinutes(1))  // per minute
            .timeoutDuration(Duration.ofSeconds(30))  // longer wait for crypto operations
            .build();
            
        RateLimiter rateLimiter = registry.rateLimiter("crypto-transaction", config);
        
        rateLimiter.getEventPublisher()
            .onFailure(event ->
                log.warn("Crypto transaction rate limit exceeded - possible automated trading"));
                
        return rateLimiter;
    }

    /**
     * Admin API rate limiter
     * Higher limits for administrative operations
     */
    @Bean
    public RateLimiter adminApiRateLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(200)  // 200 admin requests
            .limitRefreshPeriod(Duration.ofMinutes(1))  // per minute
            .timeoutDuration(Duration.ofSeconds(2))
            .build();
            
        return registry.rateLimiter("admin-api", config);
    }
}