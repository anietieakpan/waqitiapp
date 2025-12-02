package com.waqiti.transaction.client;

import feign.Request;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for ExternalSystemClient Feign client.
 *
 * This configuration provides custom timeout and retry settings for external
 * system integration (bank transfers, payment gateways, card networks).
 *
 * Timeout Strategy:
 * - Connect Timeout: 10 seconds (external systems may be slow to connect)
 * - Read Timeout: 30 seconds (bank transfers can take 20-30 seconds)
 *
 * Retry Strategy:
 * - Initial Interval: 2000ms (2 seconds)
 * - Max Interval: 10000ms (10 seconds)
 * - Max Attempts: 2 (original + 1 retry)
 * - Rationale: External systems may charge per request; limit retries
 *
 * WARNING: External operations may NOT be idempotent. The external system
 * must implement idempotency checks using transaction reference IDs.
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Configuration
public class ExternalSystemClientConfiguration {

    /**
     * Custom request options for external system calls.
     *
     * External operations involve:
     * - Bank API calls (ACH, wire transfers, SEPA)
     * - Payment gateway processing (Stripe, PayPal, etc.)
     * - Card network authorization (Visa, Mastercard)
     * - Third-party account validation
     *
     * These operations can take 10-30 seconds due to:
     * - Network latency to external systems
     * - Real-time fraud checks
     * - Card authorization requests
     * - Bank processing time
     *
     * @return Request options with extended timeouts for external calls
     */
    @Bean
    public Request.Options externalSystemRequestOptions() {
        return new Request.Options(
            10000, TimeUnit.MILLISECONDS,  // Connect timeout: 10 seconds
            30000, TimeUnit.MILLISECONDS,  // Read timeout: 30 seconds
            true                           // Follow redirects
        );
    }

    /**
     * Custom retry configuration for external system calls.
     *
     * CAUTION: External systems may NOT be idempotent and may charge per request.
     * We retry only ONCE to minimize:
     * 1. Duplicate charges/transfers
     * 2. Transaction fees from external providers
     * 3. Rate limit exhaustion
     *
     * The external system MUST implement idempotency using:
     * - Transaction reference IDs
     * - Idempotency keys
     * - Deduplication logic
     *
     * Retry Schedule:
     * - Attempt 1: Immediate
     * - Attempt 2: After 2 seconds
     * - Total: 2 attempts over ~2 seconds
     *
     * @return Retryer with conservative retry policy
     */
    @Bean
    public Retryer externalSystemRetryer() {
        return new Retryer.Default(
            2000L,     // Initial backoff interval (2 seconds)
            10000L,    // Max backoff interval (10 seconds)
            2          // Max attempts (original + 1 retry) - CONSERVATIVE
        );
    }
}
