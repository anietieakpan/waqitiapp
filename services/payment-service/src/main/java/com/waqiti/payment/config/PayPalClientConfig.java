package com.waqiti.payment.config;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * PayPal Client Configuration
 * Secure configuration holder for PayPal API credentials
 *
 * @author Waqiti Payment Team
 * @version 1.0.0
 */
@Slf4j
@Getter
@Builder
public class PayPalClientConfig {

    private final String clientId;
    private final String clientSecret;

    @Builder.Default
    private final String mode = "live"; // "sandbox" or "live"

    @Builder.Default
    private final String baseUrl = "https://api.paypal.com";

    /**
     * Validate configuration
     */
    public void validate() {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalStateException("PayPal client ID is required");
        }

        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            throw new IllegalStateException("PayPal client secret is required");
        }

        if ("sandbox".equalsIgnoreCase(mode)) {
            log.warn("PayPal configured in SANDBOX mode - ensure this is not production!");
        }

        log.info("PayPal client configuration validated successfully (mode: {})", mode);
    }

    /**
     * Check if running in sandbox mode
     */
    public boolean isSandbox() {
        return "sandbox".equalsIgnoreCase(mode);
    }

    /**
     * Get appropriate base URL for current mode
     */
    public String getEffectiveBaseUrl() {
        if (isSandbox()) {
            return "https://api.sandbox.paypal.com";
        }
        return baseUrl;
    }
}
