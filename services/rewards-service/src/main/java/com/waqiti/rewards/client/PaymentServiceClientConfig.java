package com.waqiti.rewards.client;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for Payment Service Feign Client
 * Handles authentication, timeouts, logging, and error handling for payment service calls
 */
@Configuration
@Slf4j
public class PaymentServiceClientConfig {

    @Value("${services.payment.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${services.payment.read-timeout:20000}")
    private int readTimeout;

    @Value("${services.payment.follow-redirects:false}")
    private boolean followRedirects;

    /**
     * Configure request timeout options for payment operations
     * Payment operations may require longer timeouts for processing
     */
    @Bean
    public Request.Options paymentServiceRequestOptions() {
        return new Request.Options(
                connectTimeout, TimeUnit.MILLISECONDS,
                readTimeout, TimeUnit.MILLISECONDS,
                followRedirects
        );
    }

    /**
     * Request interceptor to add authentication and tracking headers
     */
    @Bean
    public RequestInterceptor paymentServiceRequestInterceptor() {
        return requestTemplate -> {
            // Add authentication from security context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getCredentials() != null) {
                requestTemplate.header("Authorization", "Bearer " + authentication.getCredentials().toString());
            }

            // Add service identification headers
            requestTemplate.header("X-Service-Name", "rewards-service");
            requestTemplate.header("X-Service-Version", "1.0");
            requestTemplate.header("X-Request-Source", "rewards-cashback");
            
            // Add content type headers
            requestTemplate.header("Content-Type", "application/json");
            requestTemplate.header("Accept", "application/json");
            
            // Add request timestamp for audit trails
            requestTemplate.header("X-Request-Timestamp", String.valueOf(System.currentTimeMillis()));
            
            log.debug("Payment service request: {} {}", requestTemplate.method(), requestTemplate.path());
        };
    }

    /**
     * Custom error decoder for payment service errors
     */
    @Bean
    public ErrorDecoder paymentServiceErrorDecoder() {
        return new PaymentServiceErrorDecoder();
    }

    /**
     * Feign logger level configuration
     */
    @Bean
    public Logger.Level paymentServiceFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * Custom error decoder implementation for payment-specific errors
     */
    @Slf4j
    public static class PaymentServiceErrorDecoder implements ErrorDecoder {
        
        private final ErrorDecoder defaultErrorDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            log.error("Payment service error - Method: {}, Status: {}", methodKey, response.status());

            switch (response.status()) {
                case 400:
                    return new PaymentServiceException("Invalid payment request: " + response.reason());
                case 401:
                    return new PaymentServiceException("Authentication failed for payment service");
                case 403:
                    return new PaymentServiceException("Insufficient permissions for payment operation");
                case 404:
                    return new PaymentServiceException("Payment or transaction not found: " + response.reason());
                case 409:
                    return new PaymentServiceException("Payment conflict - possible duplicate: " + response.reason());
                case 422:
                    return new PaymentServiceException("Payment processing failed: " + response.reason());
                case 429:
                    return new PaymentServiceException("Rate limit exceeded for payment service");
                case 500:
                case 502:
                case 503:
                case 504:
                    return new PaymentServiceException("Payment service temporarily unavailable: " + response.reason());
                default:
                    return defaultErrorDecoder.decode(methodKey, response);
            }
        }
    }

    /**
     * Custom exception for payment service errors
     */
    public static class PaymentServiceException extends RuntimeException {
        public PaymentServiceException(String message) {
            super(message);
        }
    }
}