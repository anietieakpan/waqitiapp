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
 * Configuration for User Service Feign Client
 * Handles authentication, timeouts, logging, and error handling for user service calls
 */
@Configuration
@Slf4j
public class UserServiceClientConfig {

    @Value("${services.user.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${services.user.read-timeout:10000}")
    private int readTimeout;

    @Value("${services.user.follow-redirects:false}")
    private boolean followRedirects;

    /**
     * Configure request timeout options
     */
    @Bean
    public Request.Options userServiceRequestOptions() {
        return new Request.Options(
                connectTimeout, TimeUnit.MILLISECONDS,
                readTimeout, TimeUnit.MILLISECONDS,
                followRedirects
        );
    }

    /**
     * Request interceptor to add authentication headers
     */
    @Bean
    public RequestInterceptor userServiceRequestInterceptor() {
        return requestTemplate -> {
            // Add authentication from security context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getCredentials() != null) {
                requestTemplate.header("Authorization", "Bearer " + authentication.getCredentials().toString());
            }

            // Add service identification headers
            requestTemplate.header("X-Service-Name", "rewards-service");
            requestTemplate.header("X-Service-Version", "1.0");
            
            // Add content type headers
            requestTemplate.header("Content-Type", "application/json");
            requestTemplate.header("Accept", "application/json");
            
            log.debug("User service request: {} {}", requestTemplate.method(), requestTemplate.path());
        };
    }

    /**
     * Custom error decoder for user service errors
     */
    @Bean
    public ErrorDecoder userServiceErrorDecoder() {
        return new UserServiceErrorDecoder();
    }

    /**
     * Feign logger level configuration
     */
    @Bean
    public Logger.Level userServiceFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * Custom error decoder implementation
     */
    @Slf4j
    public static class UserServiceErrorDecoder implements ErrorDecoder {
        
        private final ErrorDecoder defaultErrorDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            log.error("User service error - Method: {}, Status: {}", methodKey, response.status());

            switch (response.status()) {
                case 400:
                    return new UserServiceException("Invalid request to user service: " + response.reason());
                case 401:
                    return new UserServiceException("Authentication failed for user service");
                case 403:
                    return new UserServiceException("Insufficient permissions for user service operation");
                case 404:
                    return new UserServiceException("User not found: " + response.reason());
                case 500:
                case 502:
                case 503:
                case 504:
                    return new UserServiceException("User service temporarily unavailable: " + response.reason());
                default:
                    return defaultErrorDecoder.decode(methodKey, response);
            }
        }
    }

    /**
     * Custom exception for user service errors
     */
    public static class UserServiceException extends RuntimeException {
        public UserServiceException(String message) {
            super(message);
        }
    }
}