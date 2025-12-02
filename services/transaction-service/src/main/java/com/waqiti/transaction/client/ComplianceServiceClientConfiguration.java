package com.waqiti.transaction.client;

import feign.Request;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for ComplianceServiceClient Feign client.
 *
 * This configuration provides custom timeout and retry settings for compliance
 * service calls, which perform KYC/AML checks and risk assessments.
 *
 * Timeout Strategy:
 * - Connect Timeout: 5 seconds (compliance service may be under heavy load)
 * - Read Timeout: 15 seconds (risk assessment/ML models can take time)
 *
 * Retry Strategy:
 * - Initial Interval: 1000ms (1 second)
 * - Max Interval: 5000ms (5 seconds)
 * - Max Attempts: 2 (original + 1 retry)
 * - Rationale: Compliance checks are expensive; avoid excessive retries
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Configuration
public class ComplianceServiceClientConfiguration {

    /**
     * Custom request options for compliance service calls.
     *
     * Compliance operations involve:
     * - Database lookups for KYC/AML status
     * - External API calls to sanctions lists
     * - ML model inference for risk scoring
     * - Fraud pattern matching
     *
     * These operations can take 5-10 seconds, so we allow 15 seconds read timeout.
     *
     * @return Request options with extended timeouts
     */
    @Bean
    public Request.Options complianceServiceRequestOptions() {
        return new Request.Options(
            5000, TimeUnit.MILLISECONDS,  // Connect timeout: 5 seconds
            15000, TimeUnit.MILLISECONDS, // Read timeout: 15 seconds
            true                          // Follow redirects
        );
    }

    /**
     * Custom retry configuration for compliance service calls.
     *
     * Compliance checks are computationally expensive and may not be idempotent
     * (e.g., external API calls to sanctions lists may have rate limits).
     * We retry only once to avoid overwhelming the compliance service.
     *
     * Retry Schedule:
     * - Attempt 1: Immediate
     * - Attempt 2: After 1 second
     * - Total: 2 attempts over ~1 second
     *
     * @return Retryer with conservative retry policy
     */
    @Bean
    public Retryer complianceServiceRetryer() {
        return new Retryer.Default(
            1000L,    // Initial backoff interval (1 second)
            5000L,    // Max backoff interval (5 seconds)
            2         // Max attempts (original + 1 retry)
        );
    }
}
