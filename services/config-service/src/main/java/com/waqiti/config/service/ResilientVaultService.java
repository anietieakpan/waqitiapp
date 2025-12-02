package com.waqiti.config.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

/**
 * Resilient wrapper for Vault operations with circuit breaker and retry
 * Prevents cascading failures when Vault is unavailable
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientVaultService {

    private final VaultTemplate vaultTemplate;

    /**
     * Read secret from Vault with circuit breaker and retry
     *
     * @param path Secret path in Vault
     * @return Secret value
     */
    @CircuitBreaker(name = "vaultService", fallbackMethod = "readFallback")
    @Retry(name = "vaultService")
    public String read(String path) {
        log.debug("Reading secret from Vault: {}", path);

        try {
            VaultResponse response = vaultTemplate.read(path);
            if (response != null && response.getData() != null) {
                Object value = response.getData().get("value");
                return value != null ? value.toString() : null;
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to read from Vault: {}", path, e);
            throw e;
        }
    }

    /**
     * Write secret to Vault with circuit breaker and retry
     *
     * @param path Secret path
     * @param value Secret value
     */
    @CircuitBreaker(name = "vaultService", fallbackMethod = "writeFallback")
    @Retry(name = "vaultService")
    public void write(String path, String value) {
        log.debug("Writing secret to Vault: {}", path);

        try {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("value", value);
            vaultTemplate.write(path, data);
            log.debug("Successfully wrote secret to Vault: {}", path);
        } catch (Exception e) {
            log.error("Failed to write to Vault: {}", path, e);
            throw e;
        }
    }

    /**
     * Delete secret from Vault with circuit breaker
     *
     * @param path Secret path
     */
    @CircuitBreaker(name = "vaultService", fallbackMethod = "deleteFallback")
    public void delete(String path) {
        log.debug("Deleting secret from Vault: {}", path);

        try {
            vaultTemplate.delete(path);
            log.debug("Successfully deleted secret from Vault: {}", path);
        } catch (Exception e) {
            log.error("Failed to delete from Vault: {}", path, e);
            throw e;
        }
    }

    /**
     * Check if Vault is healthy
     *
     * @return true if Vault is accessible
     */
    @CircuitBreaker(name = "vaultService", fallbackMethod = "healthFallback")
    public boolean isHealthy() {
        try {
            vaultTemplate.opsForSys().health();
            return true;
        } catch (Exception e) {
            log.warn("Vault health check failed", e);
            return false;
        }
    }

    // Fallback methods

    private String readFallback(String path, Exception e) {
        log.error("Vault circuit breaker activated for read operation. Path: {}, Error: {}",
                path, e.getMessage());
        // Return null - caller should handle missing value
        return null;
    }

    private void writeFallback(String path, String value, Exception e) {
        log.error("Vault circuit breaker activated for write operation. Path: {}, Error: {}",
                path, e.getMessage());
        // Log critical alert - secrets cannot be stored
        log.error("CRITICAL: Unable to write secret to Vault. Manual intervention required.");
        throw new RuntimeException("Vault service unavailable", e);
    }

    private void deleteFallback(String path, Exception e) {
        log.error("Vault circuit breaker activated for delete operation. Path: {}, Error: {}",
                path, e.getMessage());
        // Log warning - deletion can be retried later
        log.warn("Unable to delete secret from Vault. Will retry later.");
    }

    private boolean healthFallback(Exception e) {
        log.error("Vault circuit breaker activated for health check. Error: {}", e.getMessage());
        return false;
    }
}
