package com.waqiti.common.config;

import com.waqiti.common.exceptions.SecretsConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Centralized secrets management configuration
 * Provides secure access to API keys, passwords, and sensitive configuration
 */
@Slf4j
@Configuration
public class SecretsConfig {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final int ITERATION_COUNT = 65536;
    private static final int KEY_LENGTH = 256;

    @Value("${secrets.encryption.key:#{null}}")
    private String masterKey;

    @Value("${secrets.vault.enabled:false}")
    private boolean vaultEnabled;

    @Value("${secrets.vault.address:http://localhost:8200}")
    private String vaultAddress;

    @Value("${secrets.vault.token:#{null}}")
    private String vaultToken;

    @Value("${secrets.aws.enabled:false}")
    private boolean awsSecretsEnabled;

    @Value("${secrets.aws.region:us-east-1}")
    private String awsRegion;

    private final Environment environment;
    private final Map<String, String> secretsCache = new HashMap<>();

    public SecretsConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    @Primary
    public SecretsManager secretsManager() {
        if (vaultEnabled) {
            log.info("Using HashiCorp Vault for secrets management");
            return new VaultSecretsManager(vaultAddress, vaultToken);
        } else if (awsSecretsEnabled) {
            log.info("Using AWS Secrets Manager for secrets management");
            return new AWSSecretsManager(awsRegion);
        } else {
            log.info("Using environment-based secrets management");
            return new EnvironmentSecretsManager(environment, masterKey);
        }
    }

    /**
     * Interface for secrets management implementations
     */
    public interface SecretsManager {
        String getSecret(String key);
        void putSecret(String key, String value);
        boolean hasSecret(String key);
        void refreshSecrets();
    }

    /**
     * Environment-based secrets manager with encryption
     */
    public static class EnvironmentSecretsManager implements SecretsManager {
        private final Environment environment;
        private final SecretKey encryptionKey;
        private final SecureRandom secureRandom = new SecureRandom();
        private final Map<String, String> cache = new HashMap<>();

        public EnvironmentSecretsManager(Environment environment, String masterKey) {
            this.environment = environment;
            this.encryptionKey = deriveKey(masterKey != null ? masterKey : getDefaultMasterKey());
        }

        @Override
        public String getSecret(String key) {
            // Check cache first
            if (cache.containsKey(key)) {
                return cache.get(key);
            }

            // Try to get from environment
            String envKey = "SECRET_" + key.toUpperCase().replace(".", "_");
            String value = environment.getProperty(envKey);
            
            if (value == null) {
                // Try with original key
                value = environment.getProperty(key);
            }

            if (value != null && value.startsWith("ENC:")) {
                // Decrypt the value
                value = decrypt(value.substring(4));
            }

            if (value != null) {
                cache.put(key, value);
            }

            return value;
        }

        @Override
        public void putSecret(String key, String value) {
            cache.put(key, value);
        }

        @Override
        public boolean hasSecret(String key) {
            return getSecret(key) != null;
        }

        @Override
        public void refreshSecrets() {
            cache.clear();
        }

        private SecretKey deriveKey(String password) {
            try {
                byte[] salt = "waqiti-salt-v1".getBytes(StandardCharsets.UTF_8);
                KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                byte[] keyBytes = factory.generateSecret(spec).getEncoded();
                return new SecretKeySpec(keyBytes, "AES");
            } catch (Exception e) {
                throw new SecretsConfigurationException("Failed to derive encryption key", e);
            }
        }

        private String encrypt(String plaintext) {
            try {
                byte[] iv = new byte[GCM_IV_LENGTH];
                secureRandom.nextBytes(iv);

                Cipher cipher = Cipher.getInstance(ALGORITHM);
                GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
                cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);

                byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
                byte[] combined = new byte[iv.length + cipherText.length];
                System.arraycopy(iv, 0, combined, 0, iv.length);
                System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

                return Base64.getEncoder().encodeToString(combined);
            } catch (Exception e) {
                throw new SecretsConfigurationException("Failed to encrypt secret", e);
            }
        }

        private String decrypt(String encryptedText) {
            try {
                byte[] combined = Base64.getDecoder().decode(encryptedText);
                byte[] iv = new byte[GCM_IV_LENGTH];
                byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
                
                System.arraycopy(combined, 0, iv, 0, iv.length);
                System.arraycopy(combined, iv.length, cipherText, 0, cipherText.length);

                Cipher cipher = Cipher.getInstance(ALGORITHM);
                GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
                cipher.init(Cipher.DECRYPT_MODE, encryptionKey, parameterSpec);

                byte[] plainText = cipher.doFinal(cipherText);
                return new String(plainText, StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new SecretsConfigurationException("Failed to decrypt secret", e);
            }
        }

        private String getDefaultMasterKey() {
            // In production, this should come from a secure source
            String key = System.getenv("WAQITI_MASTER_KEY");
            if (key == null) {
                log.warn("Using default master key - NOT SECURE FOR PRODUCTION");
                key = "default-master-key-change-in-production";
            }
            return key;
        }
    }

    /**
     * HashiCorp Vault implementation
     */
    public static class VaultSecretsManager implements SecretsManager {
        private final String vaultAddress;
        private final String vaultToken;
        private final Map<String, String> cache = new HashMap<>();

        public VaultSecretsManager(String vaultAddress, String vaultToken) {
            this.vaultAddress = vaultAddress;
            this.vaultToken = vaultToken;
            validateVaultConnection();
        }

        @Override
        public String getSecret(String key) {
            if (cache.containsKey(key)) {
                return cache.get(key);
            }

            // Implementation would use Vault Java Driver
            // For now, returning placeholder
            log.debug("Fetching secret {} from Vault", key);
            String value = fetchFromVault(key);
            if (value != null) {
                cache.put(key, value);
            }
            return value;
        }

        @Override
        public void putSecret(String key, String value) {
            // Implementation would use Vault Java Driver
            log.debug("Storing secret {} in Vault", key);
            storeInVault(key, value);
            cache.put(key, value);
        }

        @Override
        public boolean hasSecret(String key) {
            return getSecret(key) != null;
        }

        @Override
        public void refreshSecrets() {
            cache.clear();
        }

        private void validateVaultConnection() {
            // Validate connection to Vault
            if (vaultToken == null || vaultToken.isEmpty()) {
                throw new SecretsConfigurationException("Vault token is required when Vault is enabled");
            }
        }

        private String fetchFromVault(String key) {
            // Placeholder for Vault implementation
            return null;
        }

        private void storeInVault(String key, String value) {
            // Placeholder for Vault implementation
        }
    }

    /**
     * AWS Secrets Manager implementation
     */
    public static class AWSSecretsManager implements SecretsManager {
        private final String region;
        private final Map<String, String> cache = new HashMap<>();

        public AWSSecretsManager(String region) {
            this.region = region;
        }

        @Override
        public String getSecret(String key) {
            if (cache.containsKey(key)) {
                return cache.get(key);
            }

            // Implementation would use AWS SDK
            log.debug("Fetching secret {} from AWS Secrets Manager", key);
            String value = fetchFromAWS(key);
            if (value != null) {
                cache.put(key, value);
            }
            return value;
        }

        @Override
        public void putSecret(String key, String value) {
            // Implementation would use AWS SDK
            log.debug("Storing secret {} in AWS Secrets Manager", key);
            storeInAWS(key, value);
            cache.put(key, value);
        }

        @Override
        public boolean hasSecret(String key) {
            return getSecret(key) != null;
        }

        @Override
        public void refreshSecrets() {
            cache.clear();
        }

        private String fetchFromAWS(String key) {
            // Placeholder for AWS implementation
            return null;
        }

        private void storeInAWS(String key, String value) {
            // Placeholder for AWS implementation
        }
    }

    /**
     * Helper class to provide typed access to common secrets
     */
    @Bean
    public ApiSecrets apiSecrets(SecretsManager secretsManager) {
        return new ApiSecrets(secretsManager);
    }

    public static class ApiSecrets {
        private final SecretsManager secretsManager;

        public ApiSecrets(SecretsManager secretsManager) {
            this.secretsManager = secretsManager;
        }

        public String getTwilioApiKey() {
            return getRequiredSecret("twilio.api.key");
        }

        public String getTwilioApiSecret() {
            return getRequiredSecret("twilio.api.secret");
        }

        public String getTwilioAccountSid() {
            return getRequiredSecret("twilio.account.sid");
        }

        public String getSendGridApiKey() {
            return getRequiredSecret("sendgrid.api.key");
        }

        public String getStripeApiKey() {
            return getRequiredSecret("stripe.api.key");
        }

        public String getStripeWebhookSecret() {
            return getRequiredSecret("stripe.webhook.secret");
        }

        public String getOnfidoApiToken() {
            return getRequiredSecret("onfido.api.token");
        }

        public String getJumioApiToken() {
            return getRequiredSecret("jumio.api.token");
        }

        public String getJumioApiSecret() {
            return getRequiredSecret("jumio.api.secret");
        }

        public String getTrueLayerClientId() {
            return getRequiredSecret("truelayer.client.id");
        }

        public String getTrueLayerClientSecret() {
            return getRequiredSecret("truelayer.client.secret");
        }

        public String getPlaidClientId() {
            return getRequiredSecret("plaid.client.id");
        }

        public String getPlaidSecret() {
            return getRequiredSecret("plaid.secret");
        }

        public String getFirebaseServerKey() {
            return getRequiredSecret("firebase.server.key");
        }

        public String getApnsCertificate() {
            return getRequiredSecret("apns.certificate");
        }

        public String getApnsCertificatePassword() {
            return getRequiredSecret("apns.certificate.password");
        }

        public String getGoogleMapsApiKey() {
            return getRequiredSecret("google.maps.api.key");
        }

        public String getCoinbaseApiKey() {
            return getRequiredSecret("coinbase.api.key");
        }

        public String getCoinbaseApiSecret() {
            return getRequiredSecret("coinbase.api.secret");
        }

        public String getKrakenApiKey() {
            return getRequiredSecret("kraken.api.key");
        }

        public String getKrakenPrivateKey() {
            return getRequiredSecret("kraken.private.key");
        }

        public String getDbEncryptionKey() {
            return getRequiredSecret("db.encryption.key");
        }

        public String getJwtSecret() {
            return getRequiredSecret("jwt.secret");
        }

        public Optional<String> getOptionalSecret(String key) {
            return Optional.ofNullable(secretsManager.getSecret(key));
        }

        private String getRequiredSecret(String key) {
            String secret = secretsManager.getSecret(key);
            if (secret == null) {
                throw new SecretsConfigurationException("Required secret not found: " + key);
            }
            return secret;
        }
    }
}