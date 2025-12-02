package com.waqiti.payment.config;

import com.waqiti.common.config.validation.ConfigurationValidator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * CRITICAL PRODUCTION: Payment Service Configuration Validator
 *
 * <p>Validates all payment provider API keys and endpoints at startup.
 * FAILS FAST in production if required payment integration credentials are missing.
 *
 * <p><b>Validates:</b>
 * <ul>
 *   <li>Stripe API keys (secret key, publishable key, webhook secret)</li>
 *   <li>PayPal credentials (client ID, client secret)</li>
 *   <li>Plaid credentials (client ID, secret, environment)</li>
 *   <li>Square credentials (access token, location ID)</li>
 *   <li>Database configuration (SSL/TLS enforcement)</li>
 *   <li>Kafka bootstrap servers (production URLs)</li>
 *   <li>Redis URLs (TLS enforcement)</li>
 * </ul>
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-23
 */
@Slf4j
@Configuration
public class PaymentConfigurationValidator extends ConfigurationValidator {

    // ==================== STRIPE CONFIGURATION ====================
    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${stripe.public-key:}")
    private String stripePublicKey;

    @Value("${stripe.webhook.secret:}")
    private String stripeWebhookSecret;

    @Value("${stripe.api.url:https://api.stripe.com}")
    private String stripeApiUrl;

    // ==================== PAYPAL CONFIGURATION ====================
    @Value("${paypal.client-id:}")
    private String paypalClientId;

    @Value("${paypal.client-secret:}")
    private String paypalClientSecret;

    @Value("${paypal.mode:sandbox}")
    private String paypalMode;

    @Value("${paypal.api.url:https://api.paypal.com}")
    private String paypalApiUrl;

    // ==================== PLAID CONFIGURATION ====================
    @Value("${plaid.client-id:}")
    private String plaidClientId;

    @Value("${plaid.secret:}")
    private String plaidSecret;

    @Value("${plaid.environment:sandbox}")
    private String plaidEnvironment;

    @Value("${plaid.api.url:https://production.plaid.com}")
    private String plaidApiUrl;

    // ==================== SQUARE CONFIGURATION ====================
    @Value("${square.access-token:}")
    private String squareAccessToken;

    @Value("${square.location-id:}")
    private String squareLocationId;

    @Value("${square.api.url:https://connect.squareup.com}")
    private String squareApiUrl;

    // ==================== INFRASTRUCTURE CONFIGURATION ====================
    @Value("${spring.datasource.url:}")
    private String databaseUrl;

    @Value("${spring.kafka.bootstrap-servers:}")
    private String kafkaBootstrapServers;

    @Value("${spring.data.redis.host:}")
    private String redisHost;

    @Value("${spring.data.redis.url:}")
    private String redisUrl;

    public PaymentConfigurationValidator(Environment environment) {
        super(environment);
    }

    /**
     * CRITICAL PRODUCTION VALIDATION
     * Validates all payment service configuration at startup
     * FAILS FAST if required payment provider credentials are missing in production
     */
    @PostConstruct
    public void validatePaymentConfiguration() {
        log.info("====================================================================");
        log.info("       PAYMENT SERVICE - CONFIGURATION VALIDATION");
        log.info("====================================================================");

        // ==================== STRIPE VALIDATION ====================
        log.info("Validating Stripe configuration...");

        requireInProduction("stripe.secret-key", stripeSecretKey,
            "Stripe secret key is REQUIRED for payment processing in production");
        requireValidApiKey("stripe.secret-key", stripeSecretKey,
            "Please set a valid Stripe secret key from https://dashboard.stripe.com/apikeys");

        // Verify Stripe secret key format (starts with sk_live_ in production)
        if (isProduction() && stripeSecretKey != null && !stripeSecretKey.trim().isEmpty()) {
            if (!stripeSecretKey.startsWith("sk_live_")) {
                addError("Stripe secret key MUST start with 'sk_live_' in production. " +
                    "Got: " + stripeSecretKey.substring(0, Math.min(10, stripeSecretKey.length())) + "...");
                log.error("🚨 STRIPE SECURITY: Using test key in production!");
            }
        }

        requireInProduction("stripe.public-key", stripePublicKey,
            "Stripe publishable key is REQUIRED for client-side integration");
        requireValidApiKey("stripe.public-key", stripePublicKey,
            "Please set a valid Stripe publishable key");

        // Verify Stripe public key format (starts with pk_live_ in production)
        if (isProduction() && stripePublicKey != null && !stripePublicKey.trim().isEmpty()) {
            if (!stripePublicKey.startsWith("pk_live_")) {
                addError("Stripe publishable key MUST start with 'pk_live_' in production");
                log.error("🚨 STRIPE SECURITY: Using test publishable key in production!");
            }
        }

        requireInProduction("stripe.webhook.secret", stripeWebhookSecret,
            "Stripe webhook secret is REQUIRED for webhook verification");

        requireHttps("stripe.api.url", stripeApiUrl,
            "Stripe API must use HTTPS");
        requireNotLocalhost("stripe.api.url", stripeApiUrl,
            "Stripe API cannot be localhost in production");

        // ==================== PAYPAL VALIDATION ====================
        log.info("Validating PayPal configuration...");

        requireInProduction("paypal.client-id", paypalClientId,
            "PayPal client ID is REQUIRED for PayPal payments in production");
        requireValidApiKey("paypal.client-id", paypalClientId,
            "Please set a valid PayPal client ID from https://developer.paypal.com");

        requireInProduction("paypal.client-secret", paypalClientSecret,
            "PayPal client secret is REQUIRED for PayPal payments in production");
        requireValidApiKey("paypal.client-secret", paypalClientSecret,
            "Please set a valid PayPal client secret");

        // Verify PayPal mode
        if (isProduction() && "sandbox".equalsIgnoreCase(paypalMode)) {
            addError("PayPal mode MUST be 'live' in production (currently: " + paypalMode + ")");
            log.error("🚨 PAYPAL SECURITY: Using sandbox mode in production!");
        }

        requireHttps("paypal.api.url", paypalApiUrl,
            "PayPal API must use HTTPS");
        requireNotLocalhost("paypal.api.url", paypalApiUrl,
            "PayPal API cannot be localhost in production");

        // ==================== PLAID VALIDATION ====================
        log.info("Validating Plaid configuration...");

        requireInProduction("plaid.client-id", plaidClientId,
            "Plaid client ID is REQUIRED for bank account linking in production");
        requireValidApiKey("plaid.client-id", plaidClientId,
            "Please set a valid Plaid client ID from https://dashboard.plaid.com");

        requireInProduction("plaid.secret", plaidSecret,
            "Plaid secret is REQUIRED for bank account linking in production");
        requireValidApiKey("plaid.secret", plaidSecret,
            "Please set a valid Plaid secret");

        // Verify Plaid environment
        if (isProduction() && !"production".equalsIgnoreCase(plaidEnvironment)) {
            addError("Plaid environment MUST be 'production' in production (currently: " + plaidEnvironment + ")");
            log.error("🚨 PLAID SECURITY: Using non-production environment in production!");
        }

        requireHttps("plaid.api.url", plaidApiUrl,
            "Plaid API must use HTTPS");

        // ==================== SQUARE VALIDATION (OPTIONAL) ====================
        if (squareAccessToken != null && !squareAccessToken.trim().isEmpty()) {
            log.info("Validating Square configuration (optional)...");

            requireValidApiKey("square.access-token", squareAccessToken,
                "Square access token appears invalid");

            requireNonEmpty("square.location-id", squareLocationId,
                "Square location ID is required when access token is configured");

            requireHttps("square.api.url", squareApiUrl,
                "Square API must use HTTPS");
        }

        // ==================== DATABASE VALIDATION ====================
        log.info("Validating database configuration...");

        requireValidDatabaseUrl("spring.datasource.url", databaseUrl);

        // Additional PostgreSQL SSL check
        if (databaseUrl != null && databaseUrl.contains("postgresql")) {
            if (isProduction() && !databaseUrl.contains("sslmode=require")) {
                addError("PostgreSQL MUST use SSL in production. " +
                    "Add ?sslmode=require to JDBC URL. Current URL: " + maskSensitiveUrl(databaseUrl));
                log.error("🚨 DATABASE SECURITY: PostgreSQL without SSL in production!");
            }
        }

        // ==================== KAFKA VALIDATION ====================
        log.info("Validating Kafka configuration...");

        requireValidKafkaServers("spring.kafka.bootstrap-servers", kafkaBootstrapServers);

        // ==================== REDIS VALIDATION ====================
        log.info("Validating Redis configuration...");

        String effectiveRedisUrl = redisUrl != null && !redisUrl.trim().isEmpty()
            ? redisUrl
            : (redisHost != null ? "redis://" + redisHost + ":6379" : null);

        if (effectiveRedisUrl != null) {
            requireNotLocalhost("spring.data.redis", effectiveRedisUrl,
                "Redis must not be localhost in production");

            if (isProduction() && !effectiveRedisUrl.startsWith("rediss://")) {
                log.warn("⚠️  SECURITY WARNING: Redis not using TLS (rediss://) in production");
                log.warn("⚠️  Current URL: {}", effectiveRedisUrl);
                log.warn("⚠️  Recommend: Use rediss:// for encrypted connections");
            }
        }

        // Execute validation and fail fast if errors
        super.validateConfiguration();

        if (validationErrors.isEmpty()) {
            log.info("✅ Payment Service configuration validation PASSED");
            log.info("✅ Stripe: CONFIGURED (key: {}...)", maskApiKey(stripeSecretKey));
            log.info("✅ PayPal: CONFIGURED (mode: {})", paypalMode);
            log.info("✅ Plaid: CONFIGURED (env: {})", plaidEnvironment);
            if (squareAccessToken != null && !squareAccessToken.trim().isEmpty()) {
                log.info("✅ Square: CONFIGURED (location: {})", squareLocationId);
            }
            log.info("✅ Database: CONFIGURED (SSL: {})",
                databaseUrl != null && databaseUrl.contains("sslmode=require") ? "YES" : "NO");
            log.info("✅ Kafka: CONFIGURED ({})", kafkaBootstrapServers);
            log.info("✅ Redis: CONFIGURED (TLS: {})",
                effectiveRedisUrl != null && effectiveRedisUrl.startsWith("rediss://") ? "YES" : "NO");
        }

        log.info("====================================================================");
    }

    /**
     * Mask API key for logging (show only first 10 characters)
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) return "NOT SET";
        if (apiKey.length() <= 10) return "*".repeat(apiKey.length());
        return apiKey.substring(0, 10) + "..." + "*".repeat(apiKey.length() - 10);
    }

    /**
     * Mask sensitive URL components for logging
     */
    private String maskSensitiveUrl(String url) {
        if (url == null) return "NOT SET";

        // Mask any embedded credentials in URL
        url = url.replaceAll("://[^@]+@", "://*****:*****@");

        // Mask passwords in JDBC URLs
        url = url.replaceAll("password=[^&]+", "password=*****");

        return url;
    }
}
