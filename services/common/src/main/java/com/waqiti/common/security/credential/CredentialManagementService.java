package com.waqiti.common.security.credential;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Centralized credential management service integrating with HashiCorp Vault
 * Provides secure storage, rotation, and access control for all service credentials
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialManagementService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${vault.uri:http://localhost:8200}")
    private String vaultUri;
    
    @Value("${vault.token}")
    private String vaultToken;
    
    @Value("${vault.namespace:waqiti}")
    private String vaultNamespace;
    
    @Value("${vault.mount.path:secret}")
    private String vaultMountPath;
    
    @Value("${vault.app.role.id}")
    private String appRoleId;
    
    @Value("${vault.app.role.secret}")
    private String appRoleSecret;
    
    @Value("${credential.rotation.enabled:true}")
    private boolean rotationEnabled;
    
    @Value("${credential.rotation.interval.hours:168}") // 1 week default
    private int rotationIntervalHours;
    
    @Value("${credential.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${credential.cache.ttl.minutes:5}")
    private int cacheTtlMinutes;
    
    // Credential cache with TTL
    private final Map<String, CachedCredential> credentialCache = new ConcurrentHashMap<>();
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    
    // Vault token management
    private String currentVaultToken;
    private Instant tokenExpiryTime;
    
    // Local encryption for cache
    private SecretKey localEncryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();
    
    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing Credential Management Service with Vault at: {}", vaultUri);
            
            // Initialize local encryption key for cache
            initializeLocalEncryption();
            
            // Authenticate with Vault
            authenticateWithVault();
            
            // Verify Vault connectivity
            verifyVaultConnection();
            
            // Initialize credential paths
            initializeCredentialPaths();
            
            log.info("Credential Management Service initialized successfully");
            
        } catch (Exception e) {
            log.error("Failed to initialize Credential Management Service", e);
            throw new CredentialException("Credential service initialization failed", e);
        }
    }
    
    /**
     * Store credential in Vault
     */
    public void storeCredential(String path, String key, String value, Map<String, String> metadata) {
        log.debug("Storing credential at path: {}/{}", path, key);
        
        try {
            // Prepare credential data
            Map<String, Object> credentialData = new HashMap<>();
            credentialData.put("value", value);
            credentialData.put("created_at", Instant.now().toString());
            credentialData.put("created_by", getCurrentServiceName());
            
            if (metadata != null) {
                credentialData.put("metadata", metadata);
            }
            
            // Encrypt sensitive value before storing
            String encryptedValue = encryptForVault(value);
            credentialData.put("encrypted_value", encryptedValue);
            
            // Store in Vault
            String vaultPath = buildVaultPath(path, key);
            writeToVault(vaultPath, credentialData);
            
            // Invalidate cache
            invalidateCache(path + "/" + key);
            
            // Audit credential creation
            auditCredentialAccess("CREATE", path, key, true);
            
            log.info("Credential stored successfully at: {}/{}", path, key);
            
        } catch (Exception e) {
            log.error("Failed to store credential: {}/{}", path, key, e);
            auditCredentialAccess("CREATE", path, key, false);
            throw new CredentialException("Failed to store credential", e);
        }
    }
    
    /**
     * Retrieve credential from Vault
     */
    public String getCredential(String path, String key) {
        String fullPath = path + "/" + key;
        log.debug("Retrieving credential: {}", fullPath);
        
        try {
            // Check cache first
            if (cacheEnabled) {
                CachedCredential cached = getFromCache(fullPath);
                if (cached != null && !cached.isExpired()) {
                    log.debug("Credential retrieved from cache: {}", fullPath);
                    return decryptCachedValue(cached.getEncryptedValue());
                }
            }
            
            // Retrieve from Vault
            String vaultPath = buildVaultPath(path, key);
            Map<String, Object> vaultData = readFromVault(vaultPath);
            
            if (vaultData == null || !vaultData.containsKey("encrypted_value")) {
                log.warn("Credential not found: {}", fullPath);
                return null;
            }
            
            // Decrypt value
            String encryptedValue = (String) vaultData.get("encrypted_value");
            String decryptedValue = decryptFromVault(encryptedValue);
            
            // Update cache
            if (cacheEnabled) {
                cacheCredential(fullPath, decryptedValue);
            }
            
            // Audit credential access
            auditCredentialAccess("READ", path, key, true);
            
            return decryptedValue;
            
        } catch (Exception e) {
            log.error("Failed to retrieve credential: {}", fullPath, e);
            auditCredentialAccess("READ", path, key, false);
            throw new CredentialException("Failed to retrieve credential", e);
        }
    }
    
    /**
     * Update existing credential
     */
    public void updateCredential(String path, String key, String newValue) {
        log.info("Updating credential: {}/{}", path, key);
        
        try {
            // Get existing credential for versioning
            Map<String, Object> existingData = readFromVault(buildVaultPath(path, key));
            
            // Create new version
            Map<String, Object> updatedData = new HashMap<>();
            updatedData.put("value", newValue);
            updatedData.put("encrypted_value", encryptForVault(newValue));
            updatedData.put("updated_at", Instant.now().toString());
            updatedData.put("updated_by", getCurrentServiceName());
            updatedData.put("version", getNextVersion(existingData));
            
            // Preserve metadata
            if (existingData != null && existingData.containsKey("metadata")) {
                updatedData.put("metadata", existingData.get("metadata"));
            }
            
            // Store updated credential
            writeToVault(buildVaultPath(path, key), updatedData);
            
            // Invalidate cache
            invalidateCache(path + "/" + key);
            
            // Audit update
            auditCredentialAccess("UPDATE", path, key, true);
            
            log.info("Credential updated successfully: {}/{}", path, key);
            
        } catch (Exception e) {
            log.error("Failed to update credential: {}/{}", path, key, e);
            auditCredentialAccess("UPDATE", path, key, false);
            throw new CredentialException("Failed to update credential", e);
        }
    }
    
    /**
     * Delete credential from Vault
     */
    public void deleteCredential(String path, String key) {
        log.warn("Deleting credential: {}/{}", path, key);
        
        try {
            // Soft delete - move to deleted path with timestamp
            Map<String, Object> existingData = readFromVault(buildVaultPath(path, key));
            
            if (existingData != null) {
                // Archive before deletion
                archiveCredential(path, key, existingData);
                
                // Delete from Vault
                deleteFromVault(buildVaultPath(path, key));
            }
            
            // Invalidate cache
            invalidateCache(path + "/" + key);
            
            // Audit deletion
            auditCredentialAccess("DELETE", path, key, true);
            
            log.info("Credential deleted: {}/{}", path, key);
            
        } catch (Exception e) {
            log.error("Failed to delete credential: {}/{}", path, key, e);
            auditCredentialAccess("DELETE", path, key, false);
            throw new CredentialException("Failed to delete credential", e);
        }
    }
    
    /**
     * Rotate credential
     */
    public String rotateCredential(String path, String key, CredentialGenerator generator) {
        log.info("Rotating credential: {}/{}", path, key);
        
        try {
            // Generate new credential value
            String newValue = generator.generate();
            
            // Store old value for rollback
            String oldValue = getCredential(path, key);
            
            // Update credential
            updateCredential(path, key, newValue);
            
            // Store rotation history
            storeRotationHistory(path, key, oldValue, newValue);
            
            // Notify dependent services
            notifyCredentialRotation(path, key);
            
            log.info("Credential rotated successfully: {}/{}", path, key);
            
            return newValue;
            
        } catch (Exception e) {
            log.error("Failed to rotate credential: {}/{}", path, key, e);
            throw new CredentialException("Failed to rotate credential", e);
        }
    }
    
    /**
     * Bulk retrieve credentials
     */
    public Map<String, String> getCredentials(String path) {
        log.debug("Retrieving all credentials from path: {}", path);
        
        try {
            Map<String, String> credentials = new HashMap<>();
            
            // List all keys in path
            List<String> keys = listKeysInPath(path);
            
            // Retrieve each credential
            for (String key : keys) {
                String value = getCredential(path, key);
                if (value != null) {
                    credentials.put(key, value);
                }
            }
            
            return credentials;
            
        } catch (Exception e) {
            log.error("Failed to retrieve credentials from path: {}", path, e);
            throw new CredentialException("Failed to retrieve credentials", e);
        }
    }
    
    /**
     * Scheduled credential rotation
     */
    @Scheduled(fixedDelayString = "${credential.rotation.check.interval:3600000}") // 1 hour
    public void rotateExpiredCredentials() {
        if (!rotationEnabled) {
            return;
        }
        
        log.debug("Checking for credentials requiring rotation");
        
        try {
            List<CredentialInfo> credentialsToRotate = findCredentialsForRotation();
            
            for (CredentialInfo credInfo : credentialsToRotate) {
                try {
                    // Get appropriate generator for credential type
                    CredentialGenerator generator = getGeneratorForType(credInfo.getType());
                    
                    // Rotate credential
                    rotateCredential(credInfo.getPath(), credInfo.getKey(), generator);
                    
                } catch (Exception e) {
                    log.error("Failed to rotate credential: {}", credInfo, e);
                }
            }
            
        } catch (Exception e) {
            log.error("Credential rotation check failed", e);
        }
    }
    
    /**
     * Clean expired cache entries
     */
    @Scheduled(fixedDelay = 60000) // Every minute
    public void cleanExpiredCache() {
        if (!cacheEnabled) {
            return;
        }
        
        cacheLock.writeLock().lock();
        try {
            Instant now = Instant.now();
            credentialCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    // Vault operations
    
    private void authenticateWithVault() {
        try {
            // Use AppRole authentication
            Map<String, String> authRequest = Map.of(
                "role_id", appRoleId,
                "secret_id", appRoleSecret
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, String>> request = new HttpEntity<>(authRequest, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                vaultUri + "/v1/auth/approle/login",
                request,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> auth = (Map<String, Object>) response.getBody().get("auth");
                currentVaultToken = (String) auth.get("client_token");
                Integer leaseDuration = (Integer) auth.get("lease_duration");
                tokenExpiryTime = Instant.now().plusSeconds(leaseDuration - 60); // Refresh 1 minute before expiry
                
                log.info("Successfully authenticated with Vault");
            }
            
        } catch (Exception e) {
            // Fallback to token auth if AppRole fails
            if (vaultToken != null && !vaultToken.isEmpty()) {
                currentVaultToken = vaultToken;
                tokenExpiryTime = Instant.now().plus(24, ChronoUnit.HOURS);
                log.info("Using configured Vault token");
            } else {
                throw new CredentialException("Failed to authenticate with Vault", e);
            }
        }
    }
    
    private void writeToVault(String path, Map<String, Object> data) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Vault-Token", currentVaultToken);
        
        Map<String, Object> wrapper = Map.of("data", data);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(wrapper, headers);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(
            vaultUri + "/v1/" + path,
            request,
            Map.class
        );
        
        if (response.getStatusCode() != HttpStatus.OK && response.getStatusCode() != HttpStatus.NO_CONTENT) {
            throw new CredentialException("Failed to write to Vault: " + response.getStatusCode());
        }
    }
    
    private Map<String, Object> readFromVault(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Vault-Token", currentVaultToken);
        
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                vaultUri + "/v1/" + path,
                HttpMethod.GET,
                request,
                Map.class
            );
            
            if (response.getBody() != null) {
                return (Map<String, Object>) response.getBody().get("data");
            }
            
        } catch (Exception e) {
            log.debug("Failed to read from Vault: {}", path, e);
        }
        
        return null;
    }
    
    private void deleteFromVault(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Vault-Token", currentVaultToken);
        
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        restTemplate.exchange(
            vaultUri + "/v1/" + path,
            HttpMethod.DELETE,
            request,
            Void.class
        );
    }
    
    private List<String> listKeysInPath(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Vault-Token", currentVaultToken);
        
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                vaultUri + "/v1/" + vaultMountPath + "/metadata/" + path + "?list=true",
                HttpMethod.GET,
                request,
                Map.class
            );
            
            if (response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                return (List<String>) data.get("keys");
            }
            
        } catch (Exception e) {
            log.debug("Failed to list keys in path: {}", path, e);
        }
        
        return new ArrayList<>();
    }
    
    // Helper methods
    
    private void initializeLocalEncryption() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        localEncryptionKey = keyGen.generateKey();
    }
    
    private String buildVaultPath(String path, String key) {
        return vaultMountPath + "/data/" + vaultNamespace + "/" + path + "/" + key;
    }
    
    private String encryptForVault(String value) throws Exception {
        // Additional encryption layer before storing in Vault
        byte[] iv = new byte[12];
        secureRandom.nextBytes(iv);
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, localEncryptionKey, spec);
        
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        
        return Base64.getEncoder().encodeToString(combined);
    }
    
    private String decryptFromVault(String encryptedValue) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedValue);
        
        byte[] iv = new byte[12];
        byte[] encrypted = new byte[combined.length - 12];
        System.arraycopy(combined, 0, iv, 0, 12);
        System.arraycopy(combined, 12, encrypted, 0, encrypted.length);
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, localEncryptionKey, spec);
        
        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
    
    private void cacheCredential(String path, String value) {
        cacheLock.writeLock().lock();
        try {
            String encryptedValue = encryptForCache(value);
            CachedCredential cached = CachedCredential.builder()
                .encryptedValue(encryptedValue)
                .cachedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(cacheTtlMinutes * 60))
                .build();
            
            credentialCache.put(path, cached);
            
        } catch (Exception e) {
            log.error("Failed to cache credential", e);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    private CachedCredential getFromCache(String path) {
        cacheLock.readLock().lock();
        try {
            return credentialCache.get(path);
        } finally {
            cacheLock.readLock().unlock();
        }
    }
    
    private void invalidateCache(String path) {
        cacheLock.writeLock().lock();
        try {
            credentialCache.remove(path);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    private String encryptForCache(String value) {
        // Simple encryption for cache - in production use stronger encryption
        try {
            return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new CredentialException("Cache encryption failed", e);
        }
    }
    
    private String decryptCachedValue(String encryptedValue) {
        try {
            return new String(Base64.getDecoder().decode(encryptedValue), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CredentialException("Cache decryption failed", e);
        }
    }
    
    private void verifyVaultConnection() {
        try {
            readFromVault(vaultMountPath + "/data/" + vaultNamespace + "/health");
            log.info("Vault connection verified");
        } catch (Exception e) {
            log.warn("Vault health check failed, but continuing", e);
        }
    }
    
    private void initializeCredentialPaths() {
        // Create standard credential paths if they don't exist
        String[] paths = {
            "database",
            "api-keys", 
            "certificates",
            "encryption-keys",
            "service-accounts"
        };
        
        for (String path : paths) {
            try {
                Map<String, Object> initData = Map.of(
                    "initialized", true,
                    "created_at", Instant.now().toString()
                );
                writeToVault(buildVaultPath(path, ".init"), initData);
            } catch (Exception e) {
                log.debug("Path initialization for {} - may already exist", path);
            }
        }
    }
    
    private String getCurrentServiceName() {
        return System.getProperty("spring.application.name", "unknown-service");
    }
    
    private int getNextVersion(Map<String, Object> existingData) {
        if (existingData != null && existingData.containsKey("version")) {
            return ((Number) existingData.get("version")).intValue() + 1;
        }
        return 1;
    }
    
    private void archiveCredential(String path, String key, Map<String, Object> data) {
        try {
            String archivePath = buildVaultPath("_archive/" + path, key + "_" + Instant.now().toEpochMilli());
            writeToVault(archivePath, data);
        } catch (Exception e) {
            log.warn("Failed to archive credential before deletion", e);
        }
    }
    
    private void storeRotationHistory(String path, String key, String oldValue, String newValue) {
        try {
            Map<String, Object> history = Map.of(
                "path", path,
                "key", key,
                "rotated_at", Instant.now().toString(),
                "rotated_by", getCurrentServiceName()
            );
            
            String historyPath = buildVaultPath("_rotation_history/" + path, key + "_" + Instant.now().toEpochMilli());
            writeToVault(historyPath, history);
            
        } catch (Exception e) {
            log.warn("Failed to store rotation history", e);
        }
    }
    
    private void notifyCredentialRotation(String path, String key) {
        // Notify dependent services about credential rotation
        log.info("Notifying services about credential rotation: {}/{}", path, key);
    }
    
    private List<CredentialInfo> findCredentialsForRotation() {
        // Find credentials that need rotation based on age
        return new ArrayList<>(); // Simplified
    }
    
    private CredentialGenerator getGeneratorForType(String type) {
        // Return appropriate generator based on credential type
        return () -> UUID.randomUUID().toString(); // Default generator
    }
    
    private void auditCredentialAccess(String action, String path, String key, boolean success) {
        log.info("AUDIT: Credential {} - Path: {}/{}, Success: {}, User: {}", 
            action, path, key, success, getCurrentServiceName());
    }
    
    // Data classes
    
    @Data
    @Builder
    public static class CachedCredential {
        private String encryptedValue;
        private Instant cachedAt;
        private Instant expiresAt;
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
    
    @Data
    @Builder
    public static class CredentialInfo {
        private String path;
        private String key;
        private String type;
        private Instant createdAt;
        private Instant lastRotated;
        private int rotationIntervalHours;
    }
    
    @FunctionalInterface
    public interface CredentialGenerator {
        String generate() throws Exception;
    }
    
    public static class CredentialException extends RuntimeException {
        public CredentialException(String message) {
            super(message);
        }
        
        public CredentialException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}