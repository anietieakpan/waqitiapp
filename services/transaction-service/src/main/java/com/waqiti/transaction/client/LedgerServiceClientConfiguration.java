package com.waqiti.transaction.client;

import feign.Request;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for LedgerServiceClient Feign client.
 *
 * This configuration provides custom timeout and retry settings for ledger
 * service calls, which are CRITICAL for double-entry bookkeeping integrity.
 *
 * Timeout Strategy:
 * - Connect Timeout: 2 seconds (ledger service must be highly available)
 * - Read Timeout: 10 seconds (ledger entries involve complex DB transactions)
 *
 * Retry Strategy:
 * - Initial Interval: 500ms
 * - Max Interval: 2000ms (2 seconds)
 * - Max Attempts: 4 (original + 3 retries)
 * - Rationale: Ledger operations MUST succeed; retry aggressively
 *
 * CRITICAL: Ledger operations use idempotency keys to prevent duplicate entries.
 * Multiple retries are safe and necessary to ensure entries are not lost.
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Configuration
public class LedgerServiceClientConfiguration {

    /**
     * Custom request options for ledger service calls.
     *
     * Ledger operations involve:
     * - Double-entry bookkeeping validation
     * - Database transactions with ACID guarantees
     * - Balance calculations
     * - Audit trail creation
     *
     * These operations typically complete in <1 second, but we allow 10 seconds
     * for database contention scenarios.
     *
     * @return Request options with balanced timeouts
     */
    @Bean
    public Request.Options ledgerServiceRequestOptions() {
        return new Request.Options(
            2000, TimeUnit.MILLISECONDS,  // Connect timeout: 2 seconds
            10000, TimeUnit.MILLISECONDS, // Read timeout: 10 seconds
            true                          // Follow redirects
        );
    }

    /**
     * Custom retry configuration for ledger service calls.
     *
     * CRITICAL: Ledger entries MUST be created to maintain financial integrity.
     * We retry aggressively (4 attempts) because:
     * 1. Ledger operations use idempotency keys (safe to retry)
     * 2. Transient failures should not result in lost entries
     * 3. Double-entry bookkeeping requires both debit and credit
     *
     * Retry Schedule:
     * - Attempt 1: Immediate
     * - Attempt 2: After 500ms
     * - Attempt 3: After 1500ms (500ms + 1000ms backoff)
     * - Attempt 4: After 3500ms (1500ms + 2000ms backoff)
     * - Total: 4 attempts over ~5.5 seconds
     *
     * @return Retryer with aggressive retry policy
     */
    @Bean
    public Retryer ledgerServiceRetryer() {
        return new Retryer.Default(
            500L,     // Initial backoff interval (500ms)
            2000L,    // Max backoff interval (2 seconds)
            4         // Max attempts (original + 3 retries) - AGGRESSIVE
        );
    }
}
