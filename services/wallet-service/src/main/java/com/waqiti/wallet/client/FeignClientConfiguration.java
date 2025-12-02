package com.waqiti.wallet.client;

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
 * Configures Feign clients with appropriate timeouts, retry logic,
 * and error handling for inter-service communication.
 * 
 * @author Waqiti Development Team
 * @since 1.0.0
 */
@Configuration
@Slf4j
public class FeignClientConfiguration {
    
    /**
     * Configure Feign request timeouts
     */
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
            5000, TimeUnit.MILLISECONDS,  // connect timeout
            10000, TimeUnit.MILLISECONDS  // read timeout
        );
    }
    
    /**
     * Configure retry logic
     */
    @Bean
    public Retryer retryer() {
        return new Retryer.Default(
            100,    // period (initial delay)
            1000,   // maxPeriod (max delay)
            3       // maxAttempts
        );
    }
    
    /**
     * Configure Feign logging level
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
    
    /**
     * Custom error decoder
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            log.error("Feign client error - method: {}, status: {}, reason: {}", 
                methodKey, response.status(), response.reason());
            
            return new ErrorDecoder.Default().decode(methodKey, response);
        };
    }
}