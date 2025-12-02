package com.waqiti.vault.controller;

import com.waqiti.vault.service.VaultDatabaseSecretsManager;
import com.waqiti.vault.service.VaultHealthService;
import com.waqiti.vault.service.VaultSecretsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Vault Management Controller
 * 
 * Provides REST endpoints for Vault operations, health monitoring,
 * and secrets management. Secured with role-based access control.
 */
@RestController
@RequestMapping("/api/v1/vault")
public class VaultController {

    private final VaultSecretsService vaultSecretsService;
    private final VaultHealthService vaultHealthService;
    private final VaultDatabaseSecretsManager databaseSecretsManager;

    public VaultController(VaultSecretsService vaultSecretsService,
                          VaultHealthService vaultHealthService,
                          VaultDatabaseSecretsManager databaseSecretsManager) {
        this.vaultSecretsService = vaultSecretsService;
        this.vaultHealthService = vaultHealthService;
        this.databaseSecretsManager = databaseSecretsManager;
    }

    // Health and Status Endpoints

    @GetMapping("/health")
    public ResponseEntity<VaultHealthService.VaultHealthStatus> getHealth() {
        VaultHealthService.VaultHealthStatus status = vaultHealthService.getDetailedHealthStatus();
        
        if (status.isHealthy()) {
            return ResponseEntity.ok(status);
        } else {
            return ResponseEntity.status(503).body(status);
        }
    }

    @PostMapping("/health/check")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VaultHealthService.VaultHealthStatus> forceHealthCheck() {
        VaultHealthService.VaultHealthStatus status = vaultHealthService.forceHealthCheck();
        return ResponseEntity.ok(status);
    }

    @GetMapping("/health/simple")
    public ResponseEntity<Map<String, Object>> getSimpleHealth() {
        boolean healthy = vaultHealthService.isVaultHealthy();
        String lastError = vaultHealthService.getLastError();
        
        Map<String, Object> response = Map.of(
            "healthy", healthy,
            "status", healthy ? "UP" : "DOWN",
            "lastCheck", vaultHealthService.getLastHealthCheck(),
            "error", lastError != null ? lastError : ""
        );
        
        return healthy ? ResponseEntity.ok(response) : ResponseEntity.status(503).body(response);
    }

    // Secrets Management Endpoints

    @GetMapping("/secrets/{path}/{key}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<Map<String, String>> getSecret(@PathVariable String path, @PathVariable String key) {
        try {
            String secret = vaultSecretsService.getSecret(path, key);
            
            if (secret != null) {
                return ResponseEntity.ok(Map.of("value", secret));
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/secrets/{path}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<Map<String, Object>> getAllSecrets(@PathVariable String path) {
        try {
            Map<String, Object> secrets = vaultSecretsService.getAllSecrets(path);
            return ResponseEntity.ok(secrets);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/secrets/{path}/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> storeSecret(@PathVariable String path, 
                                                          @PathVariable String key,
                                                          @RequestBody Map<String, String> request) {
        try {
            String value = request.get("value");
            if (value == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing 'value' in request body"));
            }
            
            vaultSecretsService.storeSecret(path, key, value);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Secret stored successfully"));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/secrets/{path}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> storeSecrets(@PathVariable String path,
                                                           @RequestBody Map<String, Object> secrets) {
        try {
            vaultSecretsService.storeSecrets(path, secrets);
            return ResponseEntity.ok(Map.of("status", "success", 
                "message", "Stored " + secrets.size() + " secrets successfully"));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/secrets/{path}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteSecret(@PathVariable String path) {
        try {
            vaultSecretsService.deleteSecret(path);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Secret deleted successfully"));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Database Credentials Endpoints

    @GetMapping("/database/credentials/{role}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<?> getDatabaseCredentials(@PathVariable String role) {
        try {
            VaultSecretsService.DatabaseCredentials credentials = databaseSecretsManager.getCredentials(role);
            
            if (credentials != null) {
                // Return credentials without exposing password in logs
                Map<String, Object> response = Map.of(
                    "username", credentials.getUsername(),
                    "expiresAt", credentials.getExpiresAt(),
                    "hasPassword", credentials.getPassword() != null && !credentials.getPassword().isEmpty()
                );
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/database/credentials/{role}/renew")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<Map<String, Object>> renewDatabaseCredentials(@PathVariable String role) {
        try {
            boolean renewed = databaseSecretsManager.renewCredentials(role);
            
            if (renewed) {
                return ResponseEntity.ok(Map.of(
                    "status", "success", 
                    "message", "Credentials renewed successfully",
                    "role", role
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "status", "error", 
                    "message", "Failed to renew credentials",
                    "role", role
                ));
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/database/credentials/{role}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<VaultDatabaseSecretsManager.CredentialsStatus> getCredentialsStatus(@PathVariable String role) {
        VaultDatabaseSecretsManager.CredentialsStatus status = databaseSecretsManager.getCredentialsStatus(role);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/database/credentials/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, VaultDatabaseSecretsManager.CredentialsStatus>> getAllCredentialsStatus() {
        Map<String, VaultDatabaseSecretsManager.CredentialsStatus> statusMap = databaseSecretsManager.getAllCredentialsStatus();
        return ResponseEntity.ok(statusMap);
    }

    @DeleteMapping("/database/credentials/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> invalidateCredentials(@PathVariable String role) {
        try {
            databaseSecretsManager.invalidateCredentials(role);
            return ResponseEntity.ok(Map.of(
                "status", "success", 
                "message", "Credentials invalidated successfully",
                "role", role
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Encryption Endpoints

    @PostMapping("/encrypt/{keyName}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<Map<String, String>> encryptData(@PathVariable String keyName,
                                                          @RequestBody Map<String, String> request) {
        try {
            String plaintext = request.get("plaintext");
            if (plaintext == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing 'plaintext' in request body"));
            }
            
            String ciphertext = vaultSecretsService.encryptData(keyName, plaintext);
            
            if (ciphertext != null) {
                return ResponseEntity.ok(Map.of("ciphertext", ciphertext));
            } else {
                return ResponseEntity.status(500).body(Map.of("error", "Encryption failed"));
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/decrypt/{keyName}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<Map<String, String>> decryptData(@PathVariable String keyName,
                                                          @RequestBody Map<String, String> request) {
        try {
            String ciphertext = request.get("ciphertext");
            if (ciphertext == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing 'ciphertext' in request body"));
            }
            
            String plaintext = vaultSecretsService.decryptData(keyName, ciphertext);
            
            if (plaintext != null) {
                return ResponseEntity.ok(Map.of("plaintext", plaintext));
            } else {
                return ResponseEntity.status(500).body(Map.of("error", "Decryption failed"));
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Cache Management Endpoints

    @PostMapping("/cache/clear")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> clearCache() {
        try {
            vaultSecretsService.clearExpiredCache();
            return ResponseEntity.ok(Map.of("status", "success", "message", "Cache cleared successfully"));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/cache/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VaultSecretsService.CacheStatistics> getCacheStatistics() {
        VaultSecretsService.CacheStatistics stats = vaultSecretsService.getCacheStatistics();
        return ResponseEntity.ok(stats);
    }

    // Administrative Endpoints

    @PostMapping("/admin/database/clear-all-credentials")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> clearAllDatabaseCredentials() {
        try {
            databaseSecretsManager.clearAllCredentials();
            return ResponseEntity.ok(Map.of("status", "success", "message", "All database credentials cleared"));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/admin/database/cached-roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.Set<String>> getCachedRoles() {
        java.util.Set<String> roles = databaseSecretsManager.getCachedRoles();
        return ResponseEntity.ok(roles);
    }
}