package com.waqiti.common.config;

import feign.Client;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * P1 FIX: Feign Client Global Configuration
 *
 * ISSUE FIXED: Missing default timeout configuration caused some Feign clients to use
 * infinite timeouts, leading to thread pool exhaustion and cascading failures.
 *
 * PROBLEM:
 * - Some Feign clients had no explicit timeout configuration
 * - Default Feign timeout is effectively infinite (10 seconds connect, 60 seconds read)
 * - Under load, slow external services caused thread pool exhaustion
 * - Cascading failures across microservices
 *
 * SOLUTION:
 * - Set conservative default timeouts for all Feign clients
 * - Connect timeout: 2 seconds (time to establish connection)
 * - Read timeout: 5 seconds (time to receive response)
 * - Follow timeout: false (don't follow redirects by default)
 *
 * TIMEOUT STRATEGY:
 * - Fast operations (user, wallet): 2s connect, 3s read (override per client)
 * - Standard operations: 2s connect, 5s read (default)
 * - Financial operations (payment, fraud): 3s connect, 10s read (override per client)
 * - ACH/Wire transfers: 5s connect, 30s read (override per client)
 * - External APIs: 3s connect, 60s read (override per client)
 *
 * OVERRIDE EXAMPLE:
 * ```yaml
 * feign:
 *   client:
 *     config:
 *       fraud-detection-service:
 *         connectTimeout: 3000
 *         readTimeout: 10000
 * ```
 *
 * @author Waqiti Platform Team
 * @since 1.0 (P1 FIX)
 */
@Configuration
@Slf4j
public class FeignClientConfiguration {

    // Default timeout values (conservative for safety)
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 2000;  // 2 seconds
    private static final int DEFAULT_READ_TIMEOUT_MS = 5000;     // 5 seconds

    /**
     * Configures default request options for all Feign clients
     * These apply to ALL Feign clients unless overridden in application.yml
     */
    @Bean
    public Request.Options feignRequestOptions() {
        log.info("FEIGN CONFIG: Setting default timeouts - Connect: {}ms, Read: {}ms",
            DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);

        return new Request.Options(
            DEFAULT_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS,  // Connect timeout
            DEFAULT_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS,     // Read timeout
            false                                                // Follow redirects
        );
    }

    /**
     * Configures retry behavior for Feign clients
     * Default: No automatic retries (use Resilience4j @Retry instead for better control)
     */
    @Bean
    public Retryer feignRetryer() {
        log.info("FEIGN CONFIG: Disabling automatic retries (use Resilience4j @Retry for controlled retries)");
        return Retryer.NEVER_RETRY;
    }

    /**
     * Custom error decoder for better error handling
     */
    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return new CustomFeignErrorDecoder();
    }

    /**
     * Custom error decoder to properly handle HTTP errors
     */
    private static class CustomFeignErrorDecoder implements ErrorDecoder {
        private final ErrorDecoder defaultDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            int status = response.status();

            // Handle specific HTTP status codes
            switch (status) {
                case 408: // Request Timeout
                    return new java.net.SocketTimeoutException(
                        String.format("Request timeout for %s (HTTP %d)", methodKey, status)
                    );

                case 429: // Too Many Requests
                    return new RuntimeException(
                        String.format("Rate limit exceeded for %s (HTTP %d)", methodKey, status)
                    );

                case 503: // Service Unavailable
                case 504: // Gateway Timeout
                    return new RuntimeException(
                        String.format("Service unavailable for %s (HTTP %d)", methodKey, status)
                    );

                default:
                    return defaultDecoder.decode(methodKey, response);
            }
        }
    }
}
