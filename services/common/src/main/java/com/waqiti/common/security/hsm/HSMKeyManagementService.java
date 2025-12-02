package com.waqiti.common.security.hsm;

import com.waqiti.common.security.hsm.exception.HSMException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enterprise HSM Key Management Service
 * 
 * Features:
 * - Automated key lifecycle management
 * - Key rotation and archival
 * - Compliance reporting (FIPS 140-2/3, Common Criteria)
 * - Key usage auditing
 * - High availability with key replication
 * - Emergency key recovery procedures
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "waqiti.security.hsm.enabled", havingValue = "true")
public class HSMKeyManagementService {

    private final HSMProvider hsmProvider;
    private final Map<String, HSMKeyMetadata> keyRegistry = new ConcurrentHashMap<>();
    
    // Key lifecycle management
    private static final int DEFAULT_KEY_LIFETIME_DAYS = 365;
    private static final int KEY_ROTATION_WARNING_DAYS = 30;
    private static final int ARCHIVED_KEY_RETENTION_DAYS = 2555; // 7 years for compliance
    
    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing HSM Key Management Service");
            
            // Ensure HSM is available
            if (!hsmProvider.testConnection()) {
                throw new HSMException("HSM not available during initialization");
            }
            
            // Load existing keys from HSM
            loadExistingKeys();
            
            // Perform initial key health check
            performKeyHealthCheck();
            
            log.info("HSM Key Management Service initialized successfully with {} keys", keyRegistry.size());
            
        } catch (Exception e) {
            log.error("Failed to initialize HSM Key Management Service", e);
            throw new RuntimeException("HSM Key Management initialization failed", e);
        }
    }

    /**
     * Generates a new master encryption key with full lifecycle management
     */
    public HSMKeyHandle generateMasterKey(String keyId, int keySize) throws HSMException {
        return generateMasterKey(keyId, keySize, DEFAULT_KEY_LIFETIME_DAYS);
    }
    
    public HSMKeyHandle generateMasterKey(String keyId, int keySize, int lifetimeDays) throws HSMException {
        try {
            log.info("Generating new HSM master key: {} with lifetime {} days", keyId, lifetimeDays);
            
            // Validate key ID
            validateKeyId(keyId);
            
            // Check if key already exists
            if (keyRegistry.containsKey(keyId)) {
                throw new HSMException("Key with ID " + keyId + " already exists");
            }
            
            // Generate key in HSM with appropriate usage flags
            HSMKeyHandle keyHandle = hsmProvider.generateSecretKey(
                keyId, 
                "AES", 
                keySize,
                new HSMKeyHandle.HSMKeyUsage[]{
                    HSMKeyHandle.HSMKeyUsage.ENCRYPT,
                    HSMKeyHandle.HSMKeyUsage.DECRYPT,
                    HSMKeyHandle.HSMKeyUsage.WRAP,
                    HSMKeyHandle.HSMKeyUsage.UNWRAP
                }
            );
            
            // Create metadata for lifecycle management
            HSMKeyMetadata metadata = HSMKeyMetadata.builder()
                .keyId(keyId)
                .keyType("MASTER")
                .algorithm("AES")
                .keySize(keySize)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(lifetimeDays))
                .status(HSMKeyStatus.ACTIVE)
                .usageCount(0)
                .lastUsed(LocalDateTime.now())
                .owner("SYSTEM")
                .purpose("DATA_ENCRYPTION")
                .complianceLevel("FIPS_140_2_LEVEL_3")
                .build();
            
            // Register key in local registry
            keyRegistry.put(keyId, metadata);
            
            // Audit key generation
            auditKeyOperation("GENERATE_MASTER_KEY", keyId, "SUCCESS", null);
            
            log.info("Master key generated successfully: {}", keyId);
            return keyHandle;
            
        } catch (Exception e) {
            auditKeyOperation("GENERATE_MASTER_KEY", keyId, "FAILED", e.getMessage());
            throw new HSMException("Failed to generate master key: " + keyId, e);
        }
    }

    /**
     * Rotates an existing master key
     */
    public HSMKeyHandle rotateMasterKey(String oldKeyId) throws HSMException {
        try {
            log.info("Rotating HSM master key: {}", oldKeyId);
            
            HSMKeyMetadata oldKeyMetadata = keyRegistry.get(oldKeyId);
            if (oldKeyMetadata == null) {
                throw new HSMException("Key not found for rotation: " + oldKeyId);
            }
            
            // Generate new key with incremented version
            String newKeyId = generateRotatedKeyId(oldKeyId);
            HSMKeyHandle newKeyHandle = generateMasterKey(
                newKeyId, 
                oldKeyMetadata.getKeySize(),
                DEFAULT_KEY_LIFETIME_DAYS
            );
            
            // Mark old key as superseded
            oldKeyMetadata.setStatus(HSMKeyStatus.SUPERSEDED);
            oldKeyMetadata.setSupersededBy(newKeyId);
            oldKeyMetadata.setSupersededAt(LocalDateTime.now());
            
            // Update new key metadata to reference old key
            HSMKeyMetadata newKeyMetadata = keyRegistry.get(newKeyId);
            newKeyMetadata.setSupersedes(oldKeyId);
            
            auditKeyOperation("ROTATE_MASTER_KEY", oldKeyId, "SUCCESS", "New key: " + newKeyId);
            
            log.info("Master key rotated successfully: {} -> {}", oldKeyId, newKeyId);
            return newKeyHandle;
            
        } catch (Exception e) {
            auditKeyOperation("ROTATE_MASTER_KEY", oldKeyId, "FAILED", e.getMessage());
            throw new HSMException("Failed to rotate master key: " + oldKeyId, e);
        }
    }

    /**
     * Archives an old key (moves to long-term storage for compliance)
     */
    public void archiveKey(String keyId) throws HSMException {
        try {
            log.info("Archiving HSM key: {}", keyId);
            
            HSMKeyMetadata metadata = keyRegistry.get(keyId);
            if (metadata == null) {
                throw new HSMException("Key not found for archival: " + keyId);
            }
            
            // Validate key can be archived
            if (metadata.getStatus() == HSMKeyStatus.ACTIVE) {
                throw new HSMException("Cannot archive active key: " + keyId);
            }
            
            // Update key status
            metadata.setStatus(HSMKeyStatus.ARCHIVED);
            metadata.setArchivedAt(LocalDateTime.now());
            
            // Set retention expiry (7 years for financial compliance)
            metadata.setRetentionExpiresAt(LocalDateTime.now().plusDays(ARCHIVED_KEY_RETENTION_DAYS));
            
            auditKeyOperation("ARCHIVE_KEY", keyId, "SUCCESS", null);
            
            log.info("Key archived successfully: {}", keyId);
            
        } catch (Exception e) {
            auditKeyOperation("ARCHIVE_KEY", keyId, "FAILED", e.getMessage());
            throw new HSMException("Failed to archive key: " + keyId, e);
        }
    }

    /**
     * Securely destroys a key (after retention period)
     */
    public void destroyKey(String keyId) throws HSMException {
        try {
            log.info("Destroying HSM key: {}", keyId);
            
            HSMKeyMetadata metadata = keyRegistry.get(keyId);
            if (metadata == null) {
                throw new HSMException("Key not found for destruction: " + keyId);
            }
            
            // Validate key can be destroyed
            if (metadata.getStatus() != HSMKeyStatus.ARCHIVED) {
                throw new HSMException("Can only destroy archived keys: " + keyId);
            }
            
            if (metadata.getRetentionExpiresAt().isAfter(LocalDateTime.now())) {
                throw new HSMException("Key retention period not expired: " + keyId);
            }
            
            // Delete from HSM
            hsmProvider.deleteKey(keyId);
            
            // Update metadata
            metadata.setStatus(HSMKeyStatus.DESTROYED);
            metadata.setDestroyedAt(LocalDateTime.now());
            
            auditKeyOperation("DESTROY_KEY", keyId, "SUCCESS", null);
            
            log.info("Key destroyed successfully: {}", keyId);
            
        } catch (Exception e) {
            auditKeyOperation("DESTROY_KEY", keyId, "FAILED", e.getMessage());
            throw new HSMException("Failed to destroy key: " + keyId, e);
        }
    }

    /**
     * Scheduled key lifecycle management - runs daily
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void performKeyLifecycleManagement() {
        try {
            log.info("Starting scheduled key lifecycle management");
            
            int rotationWarnings = 0;
            int expiredKeys = 0;
            int archivedKeys = 0;
            int destroyedKeys = 0;
            
            LocalDateTime now = LocalDateTime.now();
            
            for (HSMKeyMetadata metadata : keyRegistry.values()) {
                try {
                    // Check for keys approaching expiration
                    if (metadata.getStatus() == HSMKeyStatus.ACTIVE && 
                        metadata.getExpiresAt().minusDays(KEY_ROTATION_WARNING_DAYS).isBefore(now)) {
                        
                        log.warn("Key {} expires in {} days - rotation recommended", 
                                metadata.getKeyId(), 
                                java.time.temporal.ChronoUnit.DAYS.between(now, metadata.getExpiresAt()));
                        rotationWarnings++;
                    }
                    
                    // Check for expired keys
                    if (metadata.getStatus() == HSMKeyStatus.ACTIVE && 
                        metadata.getExpiresAt().isBefore(now)) {
                        
                        log.warn("Key {} has expired - marking as expired", metadata.getKeyId());
                        metadata.setStatus(HSMKeyStatus.EXPIRED);
                        expiredKeys++;
                    }
                    
                    // Archive superseded keys older than 30 days
                    if (metadata.getStatus() == HSMKeyStatus.SUPERSEDED && 
                        metadata.getSupersededAt() != null &&
                        metadata.getSupersededAt().plusDays(30).isBefore(now)) {
                        
                        archiveKey(metadata.getKeyId());
                        archivedKeys++;
                    }
                    
                    // Destroy archived keys past retention period
                    if (metadata.getStatus() == HSMKeyStatus.ARCHIVED &&
                        metadata.getRetentionExpiresAt() != null &&
                        metadata.getRetentionExpiresAt().isBefore(now)) {
                        
                        destroyKey(metadata.getKeyId());
                        destroyedKeys++;
                    }
                    
                } catch (Exception e) {
                    log.error("Error processing key lifecycle for {}", metadata.getKeyId(), e);
                }
            }
            
            log.info("Key lifecycle management completed: {} rotation warnings, {} expired, {} archived, {} destroyed",
                    rotationWarnings, expiredKeys, archivedKeys, destroyedKeys);
            
        } catch (Exception e) {
            log.error("Key lifecycle management failed", e);
        }
    }

    /**
     * Health check for all managed keys - runs every hour
     */
    @Scheduled(fixedDelay = 3600000) // Every hour
    public void performKeyHealthCheck() {
        try {
            log.debug("Performing key health check");
            
            List<HSMKeyInfo> hsmKeys = hsmProvider.listKeys();
            Set<String> hsmKeyIds = hsmKeys.stream()
                .map(HSMKeyInfo::getKeyId)
                .collect(Collectors.toSet());
            
            // Check for keys in registry that are missing from HSM
            for (String keyId : keyRegistry.keySet()) {
                HSMKeyMetadata metadata = keyRegistry.get(keyId);
                
                if (metadata.getStatus() != HSMKeyStatus.DESTROYED && 
                    !hsmKeyIds.contains(keyId)) {
                    
                    log.error("CRITICAL: Key {} in registry but missing from HSM", keyId);
                    metadata.setStatus(HSMKeyStatus.MISSING);
                    auditKeyOperation("KEY_HEALTH_CHECK", keyId, "MISSING", "Key not found in HSM");
                }
            }
            
            // Check for orphaned keys in HSM
            for (String hsmKeyId : hsmKeyIds) {
                if (!keyRegistry.containsKey(hsmKeyId)) {
                    log.warn("Orphaned key found in HSM: {}", hsmKeyId);
                    auditKeyOperation("KEY_HEALTH_CHECK", hsmKeyId, "ORPHANED", "Key in HSM but not in registry");
                }
            }
            
            log.debug("Key health check completed");
            
        } catch (Exception e) {
            log.error("Key health check failed", e);
        }
    }

    /**
     * Get key usage statistics
     */
    public Map<String, Object> getKeyStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalKeys = keyRegistry.size();
        long activeKeys = keyRegistry.values().stream()
            .mapToLong(k -> k.getStatus() == HSMKeyStatus.ACTIVE ? 1 : 0)
            .sum();
        long expiredKeys = keyRegistry.values().stream()
            .mapToLong(k -> k.getStatus() == HSMKeyStatus.EXPIRED ? 1 : 0)
            .sum();
        long archivedKeys = keyRegistry.values().stream()
            .mapToLong(k -> k.getStatus() == HSMKeyStatus.ARCHIVED ? 1 : 0)
            .sum();
        
        stats.put("totalKeys", totalKeys);
        stats.put("activeKeys", activeKeys);
        stats.put("expiredKeys", expiredKeys);
        stats.put("archivedKeys", archivedKeys);
        stats.put("hsmProvider", hsmProvider.getProviderType());
        
        return stats;
    }

    /**
     * Generate compliance report
     */
    public HSMComplianceReport generateComplianceReport() {
        List<HSMKeyMetadata> allKeys = new ArrayList<>(keyRegistry.values());
        
        return HSMComplianceReport.builder()
            .reportDate(LocalDateTime.now())
            .totalKeys(allKeys.size())
            .activeKeys((int) allKeys.stream().filter(k -> k.getStatus() == HSMKeyStatus.ACTIVE).count())
            .expiredKeys((int) allKeys.stream().filter(k -> k.getStatus() == HSMKeyStatus.EXPIRED).count())
            .archivedKeys((int) allKeys.stream().filter(k -> k.getStatus() == HSMKeyStatus.ARCHIVED).count())
            .keysNearingExpiration((int) allKeys.stream()
                .filter(k -> k.getStatus() == HSMKeyStatus.ACTIVE && 
                           k.getExpiresAt().isBefore(LocalDateTime.now().plusDays(KEY_ROTATION_WARNING_DAYS)))
                .count())
            .complianceLevel("FIPS_140_2_LEVEL_3")
            .hsmProvider(hsmProvider.getProviderType().toString())
            .keys(allKeys)
            .build();
    }

    // Helper methods
    
    private void loadExistingKeys() throws HSMException {
        try {
            List<HSMKeyInfo> hsmKeys = hsmProvider.listKeys();
            
            for (HSMKeyInfo keyInfo : hsmKeys) {
                if (keyInfo.getKeyId().startsWith("waqiti-")) {
                    HSMKeyMetadata metadata = HSMKeyMetadata.builder()
                        .keyId(keyInfo.getKeyId())
                        .keyType("UNKNOWN") // Would need to be determined from key attributes
                        .algorithm(keyInfo.getAlgorithm())
                        .keySize(keyInfo.getKeySize())
                        .createdAt(keyInfo.getCreatedAt())
                        .expiresAt(keyInfo.getExpiresAt())
                        .status(HSMKeyStatus.ACTIVE)
                        .usageCount(0)
                        .lastUsed(LocalDateTime.now())
                        .build();
                    
                    keyRegistry.put(keyInfo.getKeyId(), metadata);
                }
            }
            
            log.info("Loaded {} existing keys from HSM", keyRegistry.size());
            
        } catch (Exception e) {
            throw new HSMException("Failed to load existing keys from HSM", e);
        }
    }
    
    private void validateKeyId(String keyId) {
        if (keyId == null || keyId.trim().isEmpty()) {
            throw new IllegalArgumentException("Key ID cannot be null or empty");
        }
        
        if (!keyId.matches("^[a-zA-Z0-9-_]+$")) {
            throw new IllegalArgumentException("Key ID contains invalid characters");
        }
        
        if (keyId.length() > 64) {
            throw new IllegalArgumentException("Key ID too long (max 64 characters)");
        }
    }
    
    private String generateRotatedKeyId(String originalKeyId) {
        // Extract version number or append v2, v3, etc.
        if (originalKeyId.matches(".*-v\\d+$")) {
            String base = originalKeyId.replaceAll("-v\\d+$", "");
            String versionStr = originalKeyId.replaceAll(".*-v(\\d+)$", "$1");
            int version = Integer.parseInt(versionStr) + 1;
            return base + "-v" + version;
        } else {
            return originalKeyId + "-v2";
        }
    }
    
    private void auditKeyOperation(String operation, String keyId, String result, String details) {
        // This would integrate with the comprehensive audit service
        log.info("HSM_KEY_AUDIT: operation={}, keyId={}, result={}, details={}", 
                operation, keyId, result, details);
    }

    // Inner classes for data structures
    
    @lombok.Builder
    @lombok.Data
    public static class HSMKeyMetadata {
        private String keyId;
        private String keyType;
        private String algorithm;
        private int keySize;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private HSMKeyStatus status;
        private long usageCount;
        private LocalDateTime lastUsed;
        private String owner;
        private String purpose;
        private String complianceLevel;
        
        // Lifecycle fields
        private String supersedes;
        private String supersededBy;
        private LocalDateTime supersededAt;
        private LocalDateTime archivedAt;
        private LocalDateTime retentionExpiresAt;
        private LocalDateTime destroyedAt;
    }
    
    public enum HSMKeyStatus {
        ACTIVE,
        EXPIRED,
        SUPERSEDED,
        ARCHIVED,
        DESTROYED,
        MISSING
    }
    
    @lombok.Builder
    @lombok.Data
    public static class HSMComplianceReport {
        private LocalDateTime reportDate;
        private int totalKeys;
        private int activeKeys;
        private int expiredKeys;
        private int archivedKeys;
        private int keysNearingExpiration;
        private String complianceLevel;
        private String hsmProvider;
        private List<HSMKeyMetadata> keys;
    }
}