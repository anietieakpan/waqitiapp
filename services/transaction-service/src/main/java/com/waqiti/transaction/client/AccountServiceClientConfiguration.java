package com.waqiti.transaction.client;

import feign.Request;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for AccountServiceClient Feign client.
 *
 * This configuration provides custom timeout and retry settings for account service
 * calls, which are critical for financial operations.
 *
 * Timeout Strategy:
 * - Connect Timeout: 3 seconds (account service should be fast)
 * - Read Timeout: 8 seconds (account balance/reserve operations may need DB queries)
 *
 * Retry Strategy:
 * - Initial Interval: 500ms
 * - Max Interval: 2000ms (2 seconds)
 * - Max Attempts: 3 (original + 2 retries)
 * - Total max time: ~5.5 seconds across all retries
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Configuration
public class AccountServiceClientConfiguration {

    /**
     * Custom request options for account service calls.
     *
     * Account operations are time-sensitive but may require database queries.
     * Balance checks and fund reservations typically complete in <500ms,
     * but we allow up to 8 seconds for complex scenarios.
     *
     * @return Request options with custom timeouts
     */
    @Bean
    public Request.Options accountServiceRequestOptions() {
        return new Request.Options(
            3000, TimeUnit.MILLISECONDS,  // Connect timeout: 3 seconds
            8000, TimeUnit.MILLISECONDS,  // Read timeout: 8 seconds
            true                          // Follow redirects
        );
    }

    /**
     * Custom retry configuration for account service calls.
     *
     * Account operations are idempotent (using idempotency keys), so retries are safe.
     * We retry up to 3 times with exponential backoff to handle transient failures.
     *
     * Retry Schedule:
     * - Attempt 1: Immediate
     * - Attempt 2: After 500ms
     * - Attempt 3: After 1500ms (500ms + 1000ms backoff)
     * - Total: 3 attempts over ~2 seconds
     *
     * @return Retryer with exponential backoff
     */
    @Bean
    public Retryer accountServiceRetryer() {
        return new Retryer.Default(
            500L,     // Initial backoff interval (500ms)
            2000L,    // Max backoff interval (2 seconds)
            3         // Max attempts (original + 2 retries)
        );
    }
}
