package com.waqiti.payment.security;

import com.waqiti.payment.cash.CashDepositNetwork;
import com.waqiti.common.vault.VaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages webhook secrets with automatic rotation support
 * 
 * Features:
 * - Vault integration for secure secret storage
 * - Automatic secret rotation
 * - Previous secret retention for graceful rotation
 * - Cache management for performance
 * - Audit logging for all secret operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookSecretManager {
    
    private final VaultService vaultService;
    private final Map<CashDepositNetwork, SecretPair> secrets = new ConcurrentHashMap<>();
    
    private static final String VAULT_PATH_PREFIX = "secret/webhook/";
    private static final int SECRET_ROTATION_DAYS = 30;
    
    @PostConstruct
    public void initialize() {
        loadSecretsFromVault();
        log.info("Webhook secret manager initialized with {} provider secrets", secrets.size());
    }
    
    /**
     * Gets the current secret for a provider
     */
    @Cacheable(value = "webhook-secrets", key = "#network")
    public String getCurrentSecret(CashDepositNetwork network) {
        SecretPair pair = secrets.get(network);
        if (pair == null) {
            throw new SecurityException("No secret configured for provider: " + network);
        }
        return pair.current;
    }
    
    /**
     * Gets the previous secret for rotation support
     */
    public String getPreviousSecret(CashDepositNetwork network) {
        SecretPair pair = secrets.get(network);
        return pair != null ? pair.previous : null;
    }
    
    /**
     * Checks if previous secret exists
     */
    public boolean hasPreviousSecret(CashDepositNetwork network) {
        SecretPair pair = secrets.get(network);
        return pair != null && pair.previous != null;
    }
    
    /**
     * Rotates secret for a specific provider
     */
    @CacheEvict(value = "webhook-secrets", key = "#network")
    public void rotateSecret(CashDepositNetwork network) {
        log.info("Rotating webhook secret for provider: {}", network);
        
        SecretPair current = secrets.get(network);
        if (current == null) {
            log.error("Cannot rotate secret - no existing secret for provider: {}", network);
            return;
        }
        
        // Generate new secret
        String newSecret = generateSecureSecret();
        
        // Update with rotation
        SecretPair rotated = new SecretPair(newSecret, current.current, LocalDateTime.now());
        secrets.put(network, rotated);
        
        // Persist to vault
        persistToVault(network, rotated);
        
        log.info("Secret rotated successfully for provider: {}", network);
    }
    
    /**
     * Scheduled task for automatic secret rotation
     */
    @Scheduled(cron = "0 0 2 * * ?") // Run daily at 2 AM
    public void checkAndRotateSecrets() {
        log.info("Starting scheduled secret rotation check");
        
        for (Map.Entry<CashDepositNetwork, SecretPair> entry : secrets.entrySet()) {
            if (shouldRotate(entry.getValue())) {
                rotateSecret(entry.getKey());
            }
        }
    }
    
    /**
     * Loads secrets from Vault
     */
    private void loadSecretsFromVault() {
        for (CashDepositNetwork network : CashDepositNetwork.values()) {
            try {
                String vaultPath = VAULT_PATH_PREFIX + network.toString().toLowerCase();
                Map<String, Object> secretData = vaultService.readSecret(vaultPath);
                
                if (secretData != null && secretData.containsKey("current")) {
                    String current = (String) secretData.get("current");
                    String previous = (String) secretData.get("previous");
                    LocalDateTime lastRotated = secretData.containsKey("lastRotated") 
                        ? LocalDateTime.parse((String) secretData.get("lastRotated"))
                        : LocalDateTime.now();
                    
                    secrets.put(network, new SecretPair(current, previous, lastRotated));
                    log.debug("Loaded secret for provider: {}", network);
                } else {
                    // Generate initial secret if not exists
                    String initialSecret = generateSecureSecret();
                    SecretPair initial = new SecretPair(initialSecret, null, LocalDateTime.now());
                    secrets.put(network, initial);
                    persistToVault(network, initial);
                    log.info("Generated initial secret for provider: {}", network);
                }
            } catch (Exception e) {
                log.error("Error loading secret for provider: {}", network, e);
            }
        }
    }
    
    /**
     * Persists secret to Vault
     */
    private void persistToVault(CashDepositNetwork network, SecretPair secretPair) {
        try {
            String vaultPath = VAULT_PATH_PREFIX + network.toString().toLowerCase();
            Map<String, Object> secretData = new HashMap<>();
            secretData.put("current", secretPair.current);
            secretData.put("previous", secretPair.previous);
            secretData.put("lastRotated", secretPair.lastRotated.toString());
            
            vaultService.writeSecret(vaultPath, secretData);
            log.debug("Persisted secret to vault for provider: {}", network);
        } catch (Exception e) {
            log.error("Error persisting secret to vault for provider: {}", network, e);
            throw new SecurityException("Failed to persist secret to vault");
        }
    }
    
    /**
     * Generates a cryptographically secure secret
     */
    private String generateSecureSecret() {
        // Generate 64-byte random secret
        byte[] randomBytes = new byte[64];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
    
    /**
     * Determines if secret should be rotated
     */
    private boolean shouldRotate(SecretPair secretPair) {
        return secretPair.lastRotated.isBefore(
            LocalDateTime.now().minusDays(SECRET_ROTATION_DAYS)
        );
    }
    
    /**
     * Secret pair for rotation support
     */
    private static class SecretPair {
        final String current;
        final String previous;
        final LocalDateTime lastRotated;
        
        SecretPair(String current, String previous, LocalDateTime lastRotated) {
            this.current = current;
            this.previous = previous;
            this.lastRotated = lastRotated;
        }
    }
}