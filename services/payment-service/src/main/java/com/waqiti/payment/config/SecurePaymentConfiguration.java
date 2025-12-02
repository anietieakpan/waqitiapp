package com.waqiti.payment.config;

import com.waqiti.common.security.secrets.SecureSecretsManager;
import com.waqiti.common.security.secrets.SecureString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.annotation.PostConstruct;

/**
 * Secure configuration for Payment Service
 * Demonstrates proper usage of SecureSecretsManager instead of hardcoded secrets
 *
 * BEFORE (Insecure):
 * <pre>
 * {@code
 * @Value("${stripe.api.key}")
 * private String stripeApiKey = "sk_live_51HxYzAB..."; // NEVER DO THIS!
 * }
 * </pre>
 *
 * AFTER (Secure):
 * <pre>
 * {@code
 * @Autowired
 * private SecureSecretsManager secretsManager;
 *
 * public String getStripeApiKey() {
 *     return secretsManager.getSecret("stripe.api.key").getValue();
 * }
 * }
 * </pre>
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurePaymentConfiguration {

    private final SecureSecretsManager secretsManager;

    // Cache secrets on startup for performance
    private SecureString stripeApiKey;
    private SecureString stripeWebhookSecret;
    private SecureString paypalClientId;
    private SecureString paypalClientSecret;

    @PostConstruct
    public void initializeSecrets() {
        log.info("Loading payment service secrets from SecureSecretsManager...");

        try {
            // Load all secrets at startup
            stripeApiKey = secretsManager.getSecret("payment.stripe.api.key");
            stripeWebhookSecret = secretsManager.getSecret("payment.stripe.webhook.secret");
            paypalClientId = secretsManager.getSecret("payment.paypal.client.id");
            paypalClientSecret = secretsManager.getSecret("payment.paypal.client.secret");

            log.info("Payment service secrets loaded successfully");

        } catch (Exception e) {
            log.error("Failed to load payment service secrets", e);
            throw new IllegalStateException("Cannot start payment service without required secrets", e);
        }
    }

    /**
     * Get Stripe API key securely
     * This method should be used instead of @Value injection
     */
    public String getStripeApiKey() {
        if (stripeApiKey == null) {
            stripeApiKey = secretsManager.getSecret("payment.stripe.api.key");
        }
        return stripeApiKey.getValue();
    }

    /**
     * Get Stripe webhook secret securely
     */
    public String getStripeWebhookSecret() {
        if (stripeWebhookSecret == null) {
            stripeWebhookSecret = secretsManager.getSecret("payment.stripe.webhook.secret");
        }
        return stripeWebhookSecret.getValue();
    }

    /**
     * Get PayPal client ID securely
     */
    public String getPaypalClientId() {
        if (paypalClientId == null) {
            paypalClientId = secretsManager.getSecret("payment.paypal.client.id");
        }
        return paypalClientId.getValue();
    }

    /**
     * Get PayPal client secret securely
     */
    public String getPaypalClientSecret() {
        if (paypalClientSecret == null) {
            paypalClientSecret = secretsManager.getSecret("payment.paypal.client.secret");
        }
        return paypalClientSecret.getValue();
    }

    /**
     * Refresh secrets (call this when secrets are rotated)
     */
    public void refreshSecrets() {
        log.info("Refreshing payment service secrets...");
        stripeApiKey = null;
        stripeWebhookSecret = null;
        paypalClientId = null;
        paypalClientSecret = null;
        initializeSecrets();
    }

    /**
     * Bean for Stripe client configuration
     */
    @Bean
    @Primary
    public StripeClientConfig stripeClientConfig() {
        return StripeClientConfig.builder()
                .apiKey(getStripeApiKey())
                .webhookSecret(getStripeWebhookSecret())
                .build();
    }

    /**
     * Bean for PayPal client configuration
     */
    @Bean
    @Primary
    public PayPalClientConfig paypalClientConfig() {
        return PayPalClientConfig.builder()
                .clientId(getPaypalClientId())
                .clientSecret(getPaypalClientSecret())
                .build();
    }
}
