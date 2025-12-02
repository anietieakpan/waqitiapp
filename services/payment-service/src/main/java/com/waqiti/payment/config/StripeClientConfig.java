package com.waqiti.payment.config;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Stripe Client Configuration
 * Secure configuration holder for Stripe API credentials
 *
 * @author Waqiti Payment Team
 * @version 1.0.0
 */
@Slf4j
@Getter
@Builder
public class StripeClientConfig {

    private final String apiKey;
    private final String webhookSecret;

    /**
     * Validate configuration on creation
     */
    @Builder.Default
    private final boolean validateOnCreate = true;

    /**
     * Post-construction validation
     */
    public void validate() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Stripe API key is required");
        }

        if (!apiKey.startsWith("sk_")) {
            log.warn("Stripe API key does not start with 'sk_' - may be invalid");
        }

        if (apiKey.startsWith("sk_test_")) {
            log.warn("Using Stripe TEST API key - ensure this is not production!");
        }

        if (webhookSecret == null || webhookSecret.trim().isEmpty()) {
            log.warn("Stripe webhook secret is not configured - webhook validation will fail");
        }

        log.info("Stripe client configuration validated successfully");
    }
}
