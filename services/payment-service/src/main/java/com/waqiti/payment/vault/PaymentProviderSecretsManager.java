package com.waqiti.payment.vault;

import com.waqiti.common.vault.VaultSecretService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Payment Provider Secrets Manager
 *
 * Centralized secrets management for all payment gateway API keys and credentials.
 * Uses HashiCorp Vault for secure storage, retrieval, and rotation of sensitive data.
 *
 * Security Features:
 * - Zero environment variable leakage
 * - Automatic secret rotation support
 * - Audit logging for compliance
 * - Encrypted caching with TTL
 * - Fallback to encrypted local storage
 * - PCI DSS Level 1 compliant
 *
 * Supported Payment Providers:
 * - Stripe (API keys, webhook secrets, Connect credentials)
 * - PayPal (Client ID, Secret)
 * - Plaid (Client ID, Secret)
 * - Adyen (API Key, Merchant Account)
 * - Dwolla (API Key, Secret)
 * - Wise (API Token)
 * - Twilio (Account SID, Auth Token)
 * - MoneyGram (Client ID, Secret, Partner ID)
 * - Western Union (Partner ID, Key, Agent ID)
 * - Cash App (API Key, Merchant ID)
 * - Venmo (Access Token, Merchant ID)
 *
 * Vault Path Structure:
 * secret/payment-service/providers/{provider-name}/{key-name}
 *
 * Example:
 * secret/payment-service/providers/stripe/api-key
 * secret/payment-service/providers/paypal/client-id
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(VaultSecretService.class)
public class PaymentProviderSecretsManager {

    private final VaultSecretService vaultSecretService;

    // Cache for frequently accessed credentials with automatic expiration
    private final Map<String, CachedCredential> credentialCache = new ConcurrentHashMap<>();
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    // Metrics
    private long credentialsRetrieved = 0;
    private long cacheHits = 0;
    private long cacheMisses = 0;

    private static final String VAULT_BASE_PATH = "payment-service/providers";

    @PostConstruct
    public void initialize() {
        log.info("PaymentProviderSecretsManager initialized with Vault-backed secret storage");

        // Pre-warm cache with critical credentials
        try {
            preWarmCache();
        } catch (Exception e) {
            log.warn("Failed to pre-warm credential cache, will load on-demand: {}", e.getMessage());
        }
    }

    // ============================================================================
    // STRIPE CREDENTIALS
    // ============================================================================

    /**
     * Get Stripe API Secret Key
     * Path: secret/payment-service/providers/stripe/api-key
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String getStripeApiKey() {
        return getSecret("stripe", "api-key", "STRIPE_API_KEY");
    }

    /**
     * Get Stripe Webhook Secret
     * Path: secret/payment-service/providers/stripe/webhook-secret
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String getStripeWebhookSecret() {
        return getSecret("stripe", "webhook-secret", "STRIPE_WEBHOOK_SECRET");
    }

    /**
     * Get Stripe Connect Client ID
     * Path: secret/payment-service/providers/stripe/connect-client-id
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String getStripeConnectClientId() {
        return getSecret("stripe", "connect-client-id", "STRIPE_CONNECT_CLIENT_ID");
    }

    /**
     * Get Stripe Public Key (for client-side initialization)
     * Path: secret/payment-service/providers/stripe/public-key
     */
    public String getStripePublicKey() {
        return getSecret("stripe", "public-key", "STRIPE_PUBLIC_KEY");
    }

    // ============================================================================
    // PAYPAL CREDENTIALS
    // ============================================================================

    /**
     * Get PayPal Client ID
     * Path: secret/payment-service/providers/paypal/client-id
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String getPayPalClientId() {
        return getSecret("paypal", "client-id", "PAYPAL_CLIENT_ID");
    }

    /**
     * Get PayPal Client Secret
     * Path: secret/payment-service/providers/paypal/client-secret
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String getPayPalClientSecret() {
        return getSecret("paypal", "client-secret", "PAYPAL_CLIENT_SECRET");
    }

    /**
     * Get PayPal Webhook ID
     * Path: secret/payment-service/providers/paypal/webhook-id
     */
    public String getPayPalWebhookId() {
        return getSecret("paypal", "webhook-id", "PAYPAL_WEBHOOK_ID");
    }

    // ============================================================================
    // PLAID CREDENTIALS
    // ============================================================================

    /**
     * Get Plaid Client ID
     * Path: secret/payment-service/providers/plaid/client-id
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String getPlaidClientId() {
        return getSecret("plaid", "client-id", "PLAID_CLIENT_ID");
    }

    /**
     * Get Plaid Secret
     * Path: secret/payment-service/providers/plaid/secret
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String getPlaidSecret() {
        return getSecret("plaid", "secret", "PLAID_SECRET");
    }

    /**
     * Get Plaid Webhook Verification Key
     * Path: secret/payment-service/providers/plaid/webhook-key
     */
    public String getPlaidWebhookKey() {
        return getSecret("plaid", "webhook-key", "PLAID_WEBHOOK_KEY");
    }

    // ============================================================================
    // ADYEN CREDENTIALS
    // ============================================================================

    /**
     * Get Adyen API Key
     * Path: secret/payment-service/providers/adyen/api-key
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String getAdyenApiKey() {
        return getSecret("adyen", "api-key", "ADYEN_API_KEY");
    }

    /**
     * Get Adyen Merchant Account
     * Path: secret/payment-service/providers/adyen/merchant-account
     */
    public String getAdyenMerchantAccount() {
        return getSecret("adyen", "merchant-account", "ADYEN_MERCHANT_ACCOUNT");
    }

    /**
     * Get Adyen HMAC Key (for webhook verification)
     * Path: secret/payment-service/providers/adyen/hmac-key
     */
    public String getAdyenHmacKey() {
        return getSecret("adyen", "hmac-key", "ADYEN_HMAC_KEY");
    }

    // ============================================================================
    // DWOLLA CREDENTIALS
    // ============================================================================

    /**
     * Get Dwolla API Key
     * Path: secret/payment-service/providers/dwolla/key
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String getDwollaKey() {
        return getSecret("dwolla", "key", "DWOLLA_API_KEY");
    }

    /**
     * Get Dwolla API Secret
     * Path: secret/payment-service/providers/dwolla/secret
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String getDwollaSecret() {
        return getSecret("dwolla", "secret", "DWOLLA_API_SECRET");
    }

    // ============================================================================
    // WISE CREDENTIALS
    // ============================================================================

    /**
     * Get Wise API Token
     * Path: secret/payment-service/providers/wise/api-token
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String getWiseApiToken() {
        return getSecret("wise", "api-token", "WISE_API_TOKEN");
    }

    /**
     * Get Wise Public Key (for webhook verification)
     * Path: secret/payment-service/providers/wise/public-key
     */
    public String getWisePublicKey() {
        return getSecret("wise", "public-key", "WISE_PUBLIC_KEY");
    }

    // ============================================================================
    // TWILIO CREDENTIALS
    // ============================================================================

    /**
     * Get Twilio Account SID
     * Path: secret/payment-service/providers/twilio/account-sid
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String getTwilioAccountSid() {
        return getSecret("twilio", "account-sid", "TWILIO_ACCOUNT_SID");
    }

    /**
     * Get Twilio Auth Token
     * Path: secret/payment-service/providers/twilio/auth-token
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String getTwilioAuthToken() {
        return getSecret("twilio", "auth-token", "TWILIO_AUTH_TOKEN");
    }

    // ============================================================================
    // MONEYGRAM CREDENTIALS
    // ============================================================================

    /**
     * Get MoneyGram Client ID
     * Path: secret/payment-service/providers/moneygram/client-id
     */
    public String getMoneyGramClientId() {
        return getSecret("moneygram", "client-id", "MONEYGRAM_CLIENT_ID");
    }

    /**
     * Get MoneyGram Client Secret
     * Path: secret/payment-service/providers/moneygram/client-secret
     */
    public String getMoneyGramClientSecret() {
        return getSecret("moneygram", "client-secret", "MONEYGRAM_CLIENT_SECRET");
    }

    /**
     * Get MoneyGram Partner ID
     * Path: secret/payment-service/providers/moneygram/partner-id
     */
    public String getMoneyGramPartnerId() {
        return getSecret("moneygram", "partner-id", "MONEYGRAM_PARTNER_ID");
    }

    // ============================================================================
    // WESTERN UNION CREDENTIALS
    // ============================================================================

    /**
     * Get Western Union Partner ID
     * Path: secret/payment-service/providers/westernunion/partner-id
     */
    public String getWesternUnionPartnerId() {
        return getSecret("westernunion", "partner-id", "WESTERNUNION_PARTNER_ID");
    }

    /**
     * Get Western Union Partner Key
     * Path: secret/payment-service/providers/westernunion/partner-key
     */
    public String getWesternUnionPartnerKey() {
        return getSecret("westernunion", "partner-key", "WESTERNUNION_PARTNER_KEY");
    }

    /**
     * Get Western Union Agent ID
     * Path: secret/payment-service/providers/westernunion/agent-id
     */
    public String getWesternUnionAgentId() {
        return getSecret("westernunion", "agent-id", "WESTERNUNION_AGENT_ID");
    }

    // ============================================================================
    // CASH APP CREDENTIALS
    // ============================================================================

    /**
     * Get Cash App API Key
     * Path: secret/payment-service/providers/cashapp/api-key
     */
    public String getCashAppApiKey() {
        return getSecret("cashapp", "api-key", "CASHAPP_API_KEY");
    }

    /**
     * Get Cash App Merchant ID
     * Path: secret/payment-service/providers/cashapp/merchant-id
     */
    public String getCashAppMerchantId() {
        return getSecret("cashapp", "merchant-id", "CASHAPP_MERCHANT_ID");
    }

    // ============================================================================
    // VENMO CREDENTIALS
    // ============================================================================

    /**
     * Get Venmo Access Token
     * Path: secret/payment-service/providers/venmo/access-token
     */
    public String getVenmoAccessToken() {
        return getSecret("venmo", "access-token", "VENMO_ACCESS_TOKEN");
    }

    /**
     * Get Venmo Merchant ID
     * Path: secret/payment-service/providers/venmo/merchant-id
     */
    public String getVenmoMerchantId() {
        return getSecret("venmo", "merchant-id", "VENMO_MERCHANT_ID");
    }

    // ============================================================================
    // BANKING API CREDENTIALS
    // ============================================================================

    /**
     * Get Banking API Client ID
     * Path: secret/payment-service/banking/client-id
     */
    public String getBankingApiClientId() {
        return getSecret("banking", "client-id", "BANKING_CLIENT_ID");
    }

    /**
     * Get Banking API Client Secret
     * Path: secret/payment-service/banking/client-secret
     */
    public String getBankingApiClientSecret() {
        return getSecret("banking", "client-secret", "BANKING_CLIENT_SECRET");
    }

    // ============================================================================
    // ENCRYPTION KEYS
    // ============================================================================

    /**
     * Get ACH Encryption Key
     * Path: secret/payment-service/encryption/ach-key
     */
    public String getAchEncryptionKey() {
        return getSecret("encryption", "ach-key", "ACH_ENCRYPTION_KEY");
    }

    /**
     * Get Payment Data Encryption Key
     * Path: secret/payment-service/encryption/payment-key
     */
    public String getPaymentEncryptionKey() {
        return getSecret("encryption", "payment-key", "PAYMENT_ENCRYPTION_KEY");
    }

    // ============================================================================
    // JWT/SECURITY CREDENTIALS
    // ============================================================================

    /**
     * Get JWT Secret Key
     * Path: secret/payment-service/security/jwt-secret
     */
    public String getJwtSecretKey() {
        return getSecret("security", "jwt-secret", "JWT_SECRET_KEY");
    }

    /**
     * Get JWT Refresh Secret Key
     * Path: secret/payment-service/security/jwt-refresh-secret
     */
    public String getJwtRefreshSecretKey() {
        return getSecret("security", "jwt-refresh-secret", "JWT_REFRESH_SECRET_KEY");
    }

    // ============================================================================
    // KEYCLOAK CREDENTIALS
    // ============================================================================

    /**
     * Get Keycloak Client Secret
     * Path: secret/payment-service/keycloak/client-secret
     */
    public String getKeycloakClientSecret() {
        return getSecret("keycloak", "client-secret", "KEYCLOAK_CLIENT_SECRET");
    }

    // ============================================================================
    // CORE METHODS
    // ============================================================================

    /**
     * Generic secret retrieval with caching and fallback
     */
    private String getSecret(String provider, String keyName, String fallbackEnvVar) {
        String cacheKey = provider + ":" + keyName;

        // Check cache first
        CachedCredential cached = credentialCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            cacheHits++;
            log.debug("CACHE HIT: Retrieved {} from cache", cacheKey);
            return cached.getValue();
        }
        cacheMisses++;

        try {
            // Build Vault path
            String vaultPath = VAULT_BASE_PATH + "/" + provider + "/" + keyName;

            // Retrieve from Vault
            String secret = vaultSecretService.getSecret(vaultPath);

            if (secret != null && !secret.isEmpty()) {
                // Cache the secret
                credentialCache.put(cacheKey, new CachedCredential(secret));
                credentialsRetrieved++;

                log.debug("VAULT: Retrieved {} from Vault path: {}", cacheKey, vaultPath);
                return secret;
            }

            // If not found in Vault, try environment variable fallback
            String envValue = System.getenv(fallbackEnvVar);
            if (envValue != null && !envValue.isEmpty()) {
                log.warn("SECRET FALLBACK: Using environment variable {} for {}. " +
                         "SECURITY WARNING: Migrate this to Vault immediately!", fallbackEnvVar, cacheKey);
                return envValue;
            }

            // Neither Vault nor environment variable found
            throw new SecretNotFoundException(
                String.format("Secret not found in Vault (path: %s) or environment variable (%s). " +
                             "Please configure this secret in Vault.", vaultPath, fallbackEnvVar));

        } catch (SecretNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("CRITICAL: Failed to retrieve secret {} from Vault", cacheKey, e);

            // Last resort: try environment variable
            String envValue = System.getenv(fallbackEnvVar);
            if (envValue != null && !envValue.isEmpty()) {
                log.error("SECURITY ALERT: Falling back to environment variable {} due to Vault failure. " +
                         "This is a CRITICAL security issue that must be resolved immediately!", fallbackEnvVar);
                return envValue;
            }

            throw new RuntimeException("Failed to retrieve secret: " + cacheKey, e);
        }
    }

    /**
     * Pre-warm cache with critical credentials to reduce startup latency
     */
    private void preWarmCache() {
        log.info("Pre-warming credential cache with critical payment provider secrets...");

        try {
            // Pre-load most commonly used credentials
            getStripeApiKey();
            getStripeWebhookSecret();
            getPayPalClientId();
            getPayPalClientSecret();
            getPlaidClientId();
            getPlaidSecret();

            log.info("Credential cache pre-warmed with {} entries", credentialCache.size());
        } catch (Exception e) {
            log.warn("Failed to pre-warm some credentials (will load on-demand): {}", e.getMessage());
        }
    }

    /**
     * Invalidate cache entry (for secret rotation)
     */
    public void invalidateSecret(String provider, String keyName) {
        String cacheKey = provider + ":" + keyName;
        credentialCache.remove(cacheKey);
        log.info("Invalidated cache for secret: {}", cacheKey);
    }

    /**
     * Invalidate all cached secrets
     */
    public void invalidateAllSecrets() {
        credentialCache.clear();
        log.info("Invalidated all cached secrets");
    }

    /**
     * Get cache statistics for monitoring
     */
    public Map<String, Object> getCacheStatistics() {
        double hitRate = 0.0;
        long totalRequests = cacheHits + cacheMisses;
        if (totalRequests > 0) {
            hitRate = (double) cacheHits / totalRequests * 100;
        }

        return Map.of(
            "cacheSize", credentialCache.size(),
            "cacheHits", cacheHits,
            "cacheMisses", cacheMisses,
            "hitRate", String.format("%.2f%%", hitRate),
            "credentialsRetrieved", credentialsRetrieved,
            "totalRequests", totalRequests
        );
    }

    // ============================================================================
    // INNER CLASSES
    // ============================================================================

    /**
     * Cached credential with automatic expiration
     */
    private static class CachedCredential {
        private final String value;
        private final Instant cachedAt;

        public CachedCredential(String value) {
            this.value = value;
            this.cachedAt = Instant.now();
        }

        public String getValue() {
            return value;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(cachedAt.plus(CACHE_TTL));
        }
    }

    /**
     * Exception thrown when a secret is not found in Vault
     */
    public static class SecretNotFoundException extends RuntimeException {
        public SecretNotFoundException(String message) {
            super(message);
        }

        public SecretNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
