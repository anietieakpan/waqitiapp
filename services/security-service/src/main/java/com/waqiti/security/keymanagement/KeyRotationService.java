package com.waqiti.security.keymanagement;

import com.waqiti.security.audit.AuditService;
import com.waqiti.security.encryption.EncryptionService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.*;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Automated Key Rotation Service
 * Implements cryptographic key lifecycle management with automated rotation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeyRotationService {
    
    private final KmsClient kmsClient;
    private final AuditService auditService;
    private final EncryptionService encryptionService;
    
    // Key stores
    private final Map<String, CryptographicKey> activeKeys = new ConcurrentHashMap<>();
    private final Map<String, List<CryptographicKey>> keyHistory = new ConcurrentHashMap<>();
    private final ReadWriteLock keyLock = new ReentrantReadWriteLock();
    
    // Configuration
    @Value("${security.key.rotation.enabled:true}")
    private boolean rotationEnabled;
    
    @Value("${security.key.rotation.interval.days:90}")
    private int rotationIntervalDays;
    
    @Value("${security.key.rotation.warning.days:14}")
    private int rotationWarningDays;
    
    @Value("${security.key.algorithm:AES}")
    private String defaultAlgorithm;
    
    @Value("${security.key.size:256}")
    private int defaultKeySize;
    
    /**
     * Initialize key rotation service
     */
    public void initialize() {
        log.info("Initializing key rotation service");
        
        // Load existing keys from KMS
        loadKeysFromKMS();
        
        // Validate key configuration
        validateKeyConfiguration();
        
        // Schedule initial rotation check
        checkKeyRotation();
    }
    
    /**
     * Create a new cryptographic key
     */
    public CryptographicKey createKey(KeyCreationRequest request) {
        log.info("Creating new cryptographic key: {}", request.getKeyId());
        
        try {
            CryptographicKey key;
            
            switch (request.getKeyType()) {
                case SYMMETRIC:
                    key = createSymmetricKey(request);
                    break;
                case ASYMMETRIC:
                    key = createAsymmetricKey(request);
                    break;
                case SIGNING:
                    key = createSigningKey(request);
                    break;
                default:
                    throw new KeyManagementException("Unsupported key type: " + request.getKeyType());
            }
            
            // Store key in KMS
            storeKeyInKMS(key);
            
            // Add to active keys
            keyLock.writeLock().lock();
            try {
                activeKeys.put(key.getKeyId(), key);
                addToKeyHistory(key);
            } finally {
                keyLock.writeLock().unlock();
            }
            
            // Audit key creation
            auditService.logKeyManagementEvent("KEY_CREATED", Map.of(
                "keyId", key.getKeyId(),
                "keyType", key.getKeyType(),
                "algorithm", key.getAlgorithm(),
                "purpose", key.getPurpose()
            ));
            
            return key;
            
        } catch (Exception e) {
            log.error("Failed to create key", e);
            throw new KeyManagementException("Key creation failed", e);
        }
    }
    
    /**
     * Rotate a cryptographic key
     */
    public KeyRotationResult rotateKey(String keyId) {
        log.info("Rotating key: {}", keyId);
        
        keyLock.writeLock().lock();
        try {
            // Get current key
            CryptographicKey currentKey = activeKeys.get(keyId);
            if (currentKey == null) {
                throw new KeyManagementException("Key not found: " + keyId);
            }
            
            // Check if rotation is allowed
            if (!canRotateKey(currentKey)) {
                return KeyRotationResult.builder()
                    .keyId(keyId)
                    .success(false)
                    .reason("Key rotation not allowed at this time")
                    .build();
            }
            
            // Create new key version
            CryptographicKey newKey = createNewKeyVersion(currentKey);
            
            // Transition period - both keys active
            currentKey.setStatus(KeyStatus.ROTATING);
            currentKey.setRotationStarted(Instant.now());
            currentKey.setSuccessorKeyId(newKey.getKeyId());
            
            // Store new key
            storeKeyInKMS(newKey);
            activeKeys.put(newKey.getKeyId(), newKey);
            addToKeyHistory(newKey);
            
            // Re-encrypt data with new key (async)
            scheduleDataReEncryption(currentKey, newKey);
            
            // Schedule old key deactivation
            scheduleKeyDeactivation(currentKey);
            
            // Audit rotation
            auditService.logKeyManagementEvent("KEY_ROTATED", Map.of(
                "oldKeyId", keyId,
                "newKeyId", newKey.getKeyId(),
                "rotationReason", "Scheduled rotation"
            ));
            
            return KeyRotationResult.builder()
                .keyId(keyId)
                .newKeyId(newKey.getKeyId())
                .success(true)
                .rotationTimestamp(Instant.now())
                .build();
            
        } catch (Exception e) {
            log.error("Key rotation failed for {}", keyId, e);
            throw new KeyManagementException("Key rotation failed", e);
        } finally {
            keyLock.writeLock().unlock();
        }
    }
    
    /**
     * Scheduled key rotation check
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void checkKeyRotation() {
        if (!rotationEnabled) {
            log.debug("Key rotation is disabled");
            return;
        }
        
        log.info("Checking keys for rotation");
        
        keyLock.readLock().lock();
        try {
            Instant rotationThreshold = Instant.now().minus(rotationIntervalDays, ChronoUnit.DAYS);
            Instant warningThreshold = Instant.now().minus(
                rotationIntervalDays - rotationWarningDays, ChronoUnit.DAYS
            );
            
            for (CryptographicKey key : activeKeys.values()) {
                if (key.getStatus() != KeyStatus.ACTIVE) {
                    continue;
                }
                
                // Check if key needs rotation
                if (key.getCreatedAt().isBefore(rotationThreshold)) {
                    log.warn("Key {} is due for rotation", key.getKeyId());
                    try {
                        rotateKey(key.getKeyId());
                    } catch (Exception e) {
                        log.error("Auto-rotation failed for key {}", key.getKeyId(), e);
                        notifyRotationFailure(key, e);
                    }
                }
                // Check if key rotation warning needed
                else if (key.getCreatedAt().isBefore(warningThreshold)) {
                    log.info("Key {} will need rotation soon", key.getKeyId());
                    notifyUpcomingRotation(key);
                }
            }
            
        } finally {
            keyLock.readLock().unlock();
        }
    }
    
    /**
     * Emergency key rotation
     */
    public void emergencyRotateKey(String keyId, String reason) {
        log.warn("Emergency rotation requested for key {} - Reason: {}", keyId, reason);
        
        keyLock.writeLock().lock();
        try {
            CryptographicKey key = activeKeys.get(keyId);
            if (key == null) {
                throw new KeyManagementException("Key not found: " + keyId);
            }
            
            // Mark key as compromised
            key.setStatus(KeyStatus.COMPROMISED);
            key.setCompromisedAt(Instant.now());
            
            // Immediate rotation
            KeyRotationResult result = rotateKey(keyId);
            
            // Immediate deactivation of compromised key
            deactivateKey(keyId);
            
            // Audit emergency rotation
            auditService.logSecurityIncident("EMERGENCY_KEY_ROTATION", Map.of(
                "keyId", keyId,
                "reason", reason,
                "newKeyId", result.getNewKeyId()
            ));
            
            // Notify security team
            notifySecurityTeam(keyId, reason);
            
        } finally {
            keyLock.writeLock().unlock();
        }
    }
    
    /**
     * Deactivate a key
     */
    public void deactivateKey(String keyId) {
        log.info("Deactivating key: {}", keyId);
        
        keyLock.writeLock().lock();
        try {
            CryptographicKey key = activeKeys.get(keyId);
            if (key == null) {
                return;
            }
            
            // Update status
            key.setStatus(KeyStatus.DEACTIVATED);
            key.setDeactivatedAt(Instant.now());
            
            // Remove from active keys
            activeKeys.remove(keyId);
            
            // Archive key
            archiveKey(key);
            
            // Audit deactivation
            auditService.logKeyManagementEvent("KEY_DEACTIVATED", Map.of(
                "keyId", keyId,
                "deactivationTime", key.getDeactivatedAt()
            ));
            
        } finally {
            keyLock.writeLock().unlock();
        }
    }
    
    /**
     * Get active key for encryption
     */
    public CryptographicKey getActiveKeyForPurpose(String purpose) {
        keyLock.readLock().lock();
        try {
            return activeKeys.values().stream()
                .filter(key -> key.getStatus() == KeyStatus.ACTIVE)
                .filter(key -> key.getPurpose().equals(purpose))
                .max(Comparator.comparing(CryptographicKey::getCreatedAt))
                .orElseThrow(() -> new KeyManagementException(
                    "No active key found for purpose: " + purpose
                ));
        } finally {
            keyLock.readLock().unlock();
        }
    }
    
    /**
     * Get key by ID (including historical keys)
     */
    public CryptographicKey getKey(String keyId) {
        keyLock.readLock().lock();
        try {
            // Check active keys first
            CryptographicKey key = activeKeys.get(keyId);
            if (key != null) {
                return key;
            }
            
            // Check historical keys
            for (List<CryptographicKey> history : keyHistory.values()) {
                for (CryptographicKey historicalKey : history) {
                    if (historicalKey.getKeyId().equals(keyId)) {
                        return historicalKey;
                    }
                }
            }
            
            throw new KeyManagementException("Key not found: " + keyId);
            
        } finally {
            keyLock.readLock().unlock();
        }
    }
    
    /**
     * Validate key usage
     */
    public void validateKeyUsage(String keyId, KeyUsage usage) {
        CryptographicKey key = getKey(keyId);
        
        // Check key status
        if (key.getStatus() == KeyStatus.DEACTIVATED || 
            key.getStatus() == KeyStatus.COMPROMISED) {
            throw new KeyManagementException("Key is not active: " + keyId);
        }
        
        // Check key usage
        if (!key.getAllowedUsages().contains(usage)) {
            throw new KeyManagementException(
                "Key usage not allowed: " + usage + " for key " + keyId
            );
        }
        
        // Check expiration
        if (key.getExpiresAt() != null && Instant.now().isAfter(key.getExpiresAt())) {
            throw new KeyManagementException("Key has expired: " + keyId);
        }
        
        // Update usage statistics
        key.incrementUsageCount();
        key.setLastUsedAt(Instant.now());
    }
    
    /**
     * Generate key rotation report
     */
    public KeyRotationReport generateRotationReport() {
        keyLock.readLock().lock();
        try {
            List<KeyInfo> activeKeyInfo = new ArrayList<>();
            List<KeyInfo> upcomingRotations = new ArrayList<>();
            List<KeyInfo> recentRotations = new ArrayList<>();
            
            Instant rotationThreshold = Instant.now().minus(rotationIntervalDays, ChronoUnit.DAYS);
            Instant recentThreshold = Instant.now().minus(30, ChronoUnit.DAYS);
            
            for (CryptographicKey key : activeKeys.values()) {
                KeyInfo info = KeyInfo.builder()
                    .keyId(key.getKeyId())
                    .purpose(key.getPurpose())
                    .algorithm(key.getAlgorithm())
                    .createdAt(key.getCreatedAt())
                    .status(key.getStatus())
                    .build();
                
                activeKeyInfo.add(info);
                
                if (key.getCreatedAt().isBefore(rotationThreshold)) {
                    upcomingRotations.add(info);
                }
            }
            
            // Check recent rotations from history
            for (List<CryptographicKey> history : keyHistory.values()) {
                for (CryptographicKey key : history) {
                    if (key.getRotationStarted() != null && 
                        key.getRotationStarted().isAfter(recentThreshold)) {
                        recentRotations.add(KeyInfo.builder()
                            .keyId(key.getKeyId())
                            .purpose(key.getPurpose())
                            .algorithm(key.getAlgorithm())
                            .createdAt(key.getCreatedAt())
                            .status(key.getStatus())
                            .rotatedAt(key.getRotationStarted())
                            .build());
                    }
                }
            }
            
            return KeyRotationReport.builder()
                .reportTimestamp(Instant.now())
                .totalActiveKeys(activeKeys.size())
                .activeKeys(activeKeyInfo)
                .upcomingRotations(upcomingRotations)
                .recentRotations(recentRotations)
                .rotationIntervalDays(rotationIntervalDays)
                .nextScheduledCheck(getNextScheduledCheck())
                .build();
                
        } finally {
            keyLock.readLock().unlock();
        }
    }
    
    // Helper methods
    
    private void loadKeysFromKMS() {
        try {
            ListKeysRequest request = ListKeysRequest.builder()
                .limit(100)
                .build();
            
            ListKeysResponse response = kmsClient.listKeys(request);
            
            for (KeyListEntry entry : response.keys()) {
                try {
                    DescribeKeyRequest describeRequest = DescribeKeyRequest.builder()
                        .keyId(entry.keyId())
                        .build();
                    
                    DescribeKeyResponse describeResponse = kmsClient.describeKey(describeRequest);
                    KeyMetadata metadata = describeResponse.keyMetadata();
                    
                    // Load key metadata into local cache
                    CryptographicKey key = CryptographicKey.builder()
                        .keyId(metadata.keyId())
                        .keyType(mapKeyType(metadata.keySpec()))
                        .algorithm(metadata.keySpec().toString())
                        .createdAt(metadata.creationDate())
                        .status(mapKeyStatus(metadata.keyState()))
                        .build();
                    
                    if (key.getStatus() == KeyStatus.ACTIVE) {
                        activeKeys.put(key.getKeyId(), key);
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to load key {}", entry.keyId(), e);
                }
            }
            
            log.info("Loaded {} active keys from KMS", activeKeys.size());
            
        } catch (Exception e) {
            log.error("Failed to load keys from KMS", e);
        }
    }
    
    private void validateKeyConfiguration() {
        if (activeKeys.isEmpty()) {
            log.warn("No active keys found - creating default keys");
            createDefaultKeys();
        }
        
        // Ensure all required key purposes have active keys
        Set<String> requiredPurposes = Set.of(
            "DATA_ENCRYPTION",
            "API_SIGNING",
            "TOKEN_ENCRYPTION"
        );
        
        for (String purpose : requiredPurposes) {
            boolean hasKey = activeKeys.values().stream()
                .anyMatch(key -> key.getPurpose().equals(purpose) && 
                               key.getStatus() == KeyStatus.ACTIVE);
            
            if (!hasKey) {
                log.warn("No active key for purpose: {} - creating new key", purpose);
                createKeyForPurpose(purpose);
            }
        }
    }
    
    private void createDefaultKeys() {
        createKeyForPurpose("DATA_ENCRYPTION");
        createKeyForPurpose("API_SIGNING");
        createKeyForPurpose("TOKEN_ENCRYPTION");
    }
    
    private void createKeyForPurpose(String purpose) {
        KeyCreationRequest request = KeyCreationRequest.builder()
            .keyId(generateKeyId(purpose))
            .keyType(KeyType.SYMMETRIC)
            .algorithm(defaultAlgorithm)
            .keySize(defaultKeySize)
            .purpose(purpose)
            .build();
        
        createKey(request);
    }
    
    private String generateKeyId(String purpose) {
        return String.format("%s-%s-%s", 
            purpose.toLowerCase().replace("_", "-"),
            UUID.randomUUID().toString().substring(0, 8),
            System.currentTimeMillis()
        );
    }
    
    private CryptographicKey createSymmetricKey(KeyCreationRequest request) throws Exception {
        // Generate AES key
        KeyGenerator keyGen = KeyGenerator.getInstance(request.getAlgorithm());
        keyGen.init(request.getKeySize());
        SecretKey secretKey = keyGen.generateKey();
        
        return CryptographicKey.builder()
            .keyId(request.getKeyId())
            .keyType(KeyType.SYMMETRIC)
            .algorithm(request.getAlgorithm())
            .keySize(request.getKeySize())
            .keyMaterial(Base64.getEncoder().encodeToString(secretKey.getEncoded()))
            .purpose(request.getPurpose())
            .status(KeyStatus.ACTIVE)
            .createdAt(Instant.now())
            .allowedUsages(Set.of(KeyUsage.ENCRYPT, KeyUsage.DECRYPT))
            .usageCount(0L)
            .build();
    }
    
    private CryptographicKey createAsymmetricKey(KeyCreationRequest request) throws Exception {
        // Generate RSA key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(request.getKeySize());
        KeyPair keyPair = keyGen.generateKeyPair();
        
        return CryptographicKey.builder()
            .keyId(request.getKeyId())
            .keyType(KeyType.ASYMMETRIC)
            .algorithm("RSA")
            .keySize(request.getKeySize())
            .publicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()))
            .privateKey(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()))
            .purpose(request.getPurpose())
            .status(KeyStatus.ACTIVE)
            .createdAt(Instant.now())
            .allowedUsages(Set.of(KeyUsage.ENCRYPT, KeyUsage.DECRYPT, KeyUsage.SIGN, KeyUsage.VERIFY))
            .usageCount(0L)
            .build();
    }
    
    private CryptographicKey createSigningKey(KeyCreationRequest request) throws Exception {
        // Generate ECDSA key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = keyGen.generateKeyPair();
        
        return CryptographicKey.builder()
            .keyId(request.getKeyId())
            .keyType(KeyType.SIGNING)
            .algorithm("ECDSA")
            .keySize(256)
            .publicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()))
            .privateKey(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()))
            .purpose(request.getPurpose())
            .status(KeyStatus.ACTIVE)
            .createdAt(Instant.now())
            .allowedUsages(Set.of(KeyUsage.SIGN, KeyUsage.VERIFY))
            .usageCount(0L)
            .build();
    }
    
    private void storeKeyInKMS(CryptographicKey key) {
        // Store key in AWS KMS
        // Implementation would use KMS API to store key
        log.info("Storing key {} in KMS", key.getKeyId());
    }
    
    private void addToKeyHistory(CryptographicKey key) {
        keyHistory.computeIfAbsent(key.getPurpose(), k -> new ArrayList<>()).add(key);
    }
    
    private boolean canRotateKey(CryptographicKey key) {
        // Check if key is eligible for rotation
        if (key.getStatus() != KeyStatus.ACTIVE) {
            return false;
        }
        
        // Check if minimum age reached
        long daysSinceCreation = ChronoUnit.DAYS.between(key.getCreatedAt(), Instant.now());
        return daysSinceCreation >= 30; // Minimum 30 days before rotation
    }
    
    private CryptographicKey createNewKeyVersion(CryptographicKey currentKey) throws Exception {
        KeyCreationRequest request = KeyCreationRequest.builder()
            .keyId(generateKeyId(currentKey.getPurpose()))
            .keyType(currentKey.getKeyType())
            .algorithm(currentKey.getAlgorithm())
            .keySize(currentKey.getKeySize())
            .purpose(currentKey.getPurpose())
            .build();
        
        CryptographicKey newKey = createKey(request);
        newKey.setPreviousKeyId(currentKey.getKeyId());
        
        return newKey;
    }
    
    private void scheduleDataReEncryption(CryptographicKey oldKey, CryptographicKey newKey) {
        // Schedule background job to re-encrypt data
        log.info("Scheduling data re-encryption from key {} to {}", 
            oldKey.getKeyId(), newKey.getKeyId());
    }
    
    private void scheduleKeyDeactivation(CryptographicKey key) {
        // Schedule key deactivation after grace period
        log.info("Scheduling deactivation of key {} in 30 days", key.getKeyId());
    }
    
    private void archiveKey(CryptographicKey key) {
        // Archive key for compliance and recovery
        log.info("Archiving key {}", key.getKeyId());
    }
    
    private void notifyRotationFailure(CryptographicKey key, Exception error) {
        // Notify ops team about rotation failure
        log.error("Key rotation failed for {} - notifying operations", key.getKeyId());
    }
    
    private void notifyUpcomingRotation(CryptographicKey key) {
        // Notify about upcoming rotation
        log.info("Notifying about upcoming rotation for key {}", key.getKeyId());
    }
    
    private void notifySecurityTeam(String keyId, String reason) {
        // Notify security team about emergency rotation
        log.warn("Notifying security team about emergency rotation of key {}", keyId);
    }
    
    private KeyType mapKeyType(KeySpec keySpec) {
        if (keySpec.toString().startsWith("AES")) {
            return KeyType.SYMMETRIC;
        } else if (keySpec.toString().startsWith("RSA") || keySpec.toString().startsWith("ECC")) {
            return KeyType.ASYMMETRIC;
        } else {
            return KeyType.SIGNING;
        }
    }
    
    private KeyStatus mapKeyStatus(KeyState keyState) {
        switch (keyState) {
            case ENABLED:
                return KeyStatus.ACTIVE;
            case DISABLED:
                return KeyStatus.DEACTIVATED;
            case PENDING_DELETION:
            case PENDING_IMPORT:
                return KeyStatus.ROTATING;
            default:
                return KeyStatus.DEACTIVATED;
        }
    }
    
    private Instant getNextScheduledCheck() {
        // Calculate next 2 AM
        return Instant.now().truncatedTo(ChronoUnit.DAYS).plus(26, ChronoUnit.HOURS);
    }
    
    // Inner classes
    
    @Data
    @Builder
    public static class CryptographicKey {
        private String keyId;
        private KeyType keyType;
        private String algorithm;
        private int keySize;
        private String keyMaterial; // For symmetric keys
        private String publicKey;   // For asymmetric keys
        private String privateKey;  // For asymmetric keys
        private String purpose;
        private KeyStatus status;
        private Instant createdAt;
        private Instant expiresAt;
        private Instant deactivatedAt;
        private Instant rotationStarted;
        private String previousKeyId;
        private String successorKeyId;
        private Set<KeyUsage> allowedUsages;
        private long usageCount;
        private Instant lastUsedAt;
        private Instant compromisedAt;
        
        public void incrementUsageCount() {
            this.usageCount++;
        }
    }
    
    @Data
    @Builder
    public static class KeyCreationRequest {
        private String keyId;
        private KeyType keyType;
        private String algorithm;
        private int keySize;
        private String purpose;
        private Set<KeyUsage> allowedUsages;
    }
    
    @Data
    @Builder
    public static class KeyRotationResult {
        private String keyId;
        private String newKeyId;
        private boolean success;
        private String reason;
        private Instant rotationTimestamp;
    }
    
    @Data
    @Builder
    public static class KeyRotationReport {
        private Instant reportTimestamp;
        private int totalActiveKeys;
        private List<KeyInfo> activeKeys;
        private List<KeyInfo> upcomingRotations;
        private List<KeyInfo> recentRotations;
        private int rotationIntervalDays;
        private Instant nextScheduledCheck;
    }
    
    @Data
    @Builder
    public static class KeyInfo {
        private String keyId;
        private String purpose;
        private String algorithm;
        private Instant createdAt;
        private KeyStatus status;
        private Instant rotatedAt;
    }
    
    public enum KeyType {
        SYMMETRIC, ASYMMETRIC, SIGNING
    }
    
    public enum KeyStatus {
        ACTIVE, ROTATING, DEACTIVATED, COMPROMISED
    }
    
    public enum KeyUsage {
        ENCRYPT, DECRYPT, SIGN, VERIFY, WRAP, UNWRAP
    }
    
    public static class KeyManagementException extends RuntimeException {
        public KeyManagementException(String message) {
            super(message);
        }
        
        public KeyManagementException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}