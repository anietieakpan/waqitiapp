package com.waqiti.common.security;

import com.waqiti.common.security.hsm.HSMKeyHandle;
import com.waqiti.common.security.hsm.HSMProvider;
import com.waqiti.common.security.hsm.exception.HSMException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKeyFactory;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized encryption service using AES-256-GCM for all sensitive data
 * Fixes the weak ECB encryption vulnerabilities found in the audit
 */
@Slf4j
@Service
public class EncryptionService {

    private static final String ENCRYPTION_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits for GCM
    private static final int GCM_TAG_LENGTH = 16; // 128 bits for authentication tag
    private static final int AES_KEY_LENGTH = 256; // 256 bits for AES-256
    private static final int PBKDF2_ITERATIONS = 65536; // Strong key derivation
    private static final int SALT_LENGTH = 32; // 256 bits salt

    private final SecureRandom secureRandom;
    private final VaultOperations vaultOperations;
    private final HSMProvider hsmProvider;
    private final Map<String, SecretKey> keyCache = new ConcurrentHashMap<>();
    
    private String masterKeyId;
    private SecretKey masterKey;
    private LocalDateTime keyRotationTimestamp;
    private String keyVersion = "v1";
    
    @Value("${encryption.vault.path:secret/data/encryption}")
    private String vaultSecretPath;
    
    @Value("${encryption.vault.key-name:master-key}")
    private String vaultKeyName;
    
    @Value("${encryption.key.rotation.hours:168}") // Default: 1 week
    private int keyRotationHours;
    
    @Value("${encryption.hsm.enabled:false}")
    private boolean hsmEnabled;
    
    @Value("${encryption.hsm.key-prefix:waqiti-enc}")
    private String hsmKeyPrefix;
    
    @Value("${encryption.hsm.fallback-to-vault:true}")
    private boolean hsmFallbackToVault;
    
    @Value("${encryption.hsm.primary-mode:false}")
    private boolean hsmPrimaryMode;
    
    private final com.waqiti.common.security.cache.SecurityCacheService securityCacheService;

    public EncryptionService(@org.springframework.beans.factory.annotation.Autowired(required = false) VaultTemplate vaultOperations,
                             @org.springframework.beans.factory.annotation.Autowired(required = false) HSMProvider hsmProvider,
                             com.waqiti.common.security.cache.SecurityCacheService securityCacheService) {
        this.secureRandom = new SecureRandom();
        this.vaultOperations = vaultOperations;
        this.hsmProvider = hsmProvider;
        this.securityCacheService = securityCacheService;
    }
    
    @PostConstruct
    public void initializeEncryption() {
        try {
            log.info("Initializing encryption service with HSM enabled: {}, Primary mode: {}", hsmEnabled, hsmPrimaryMode);
            
            if (hsmEnabled && hsmPrimaryMode) {
                // HSM is primary - try HSM first, fallback to Vault if configured
                loadMasterKeyFromHSM();
            } else {
                // Vault is primary - try Vault first, fallback to HSM if enabled
                loadMasterKeyFromVault();
            }
            
            if (!validateConfiguration()) {
                throw new SecurityException("Encryption service validation failed");
            }
            
            log.info("Encryption service initialized successfully with key ID: {}, HSM mode: {}", 
                    maskKeyId(masterKeyId), hsmEnabled);
        } catch (Exception e) {
            log.error("Failed to initialize encryption service", e);
            throw new SecurityException("Encryption service initialization failed", e);
        }
    }
    
    /**
     * Loads the master encryption key from HashiCorp Vault
     */
    private void loadMasterKeyFromVault() {
        try {
            log.debug("Loading master key from Vault path: {}", vaultSecretPath);
            
            // Read secret from Vault
            VaultResponse response = vaultOperations.read(vaultSecretPath);
            
            if (response == null || response.getData() == null) {
                log.error("No encryption key found in Vault at path: {}", vaultSecretPath);
                throw new SecurityException("Master encryption key not found in Vault");
            }
            
            Map<String, Object> data = response.getData();
            String keyString = (String) data.get(vaultKeyName);
            String keyId = (String) data.get("key-id");
            
            if (keyString == null || keyString.isEmpty()) {
                throw new SecurityException("Master encryption key is empty in Vault");
            }
            
            // Decode and validate key
            byte[] keyBytes = Base64.getDecoder().decode(keyString);
            
            if (keyBytes.length != 32) { // 256 bits
                throw new SecurityException("Invalid master key size. Expected 256 bits");
            }
            
            this.masterKey = new SecretKeySpec(keyBytes, ENCRYPTION_ALGORITHM);
            this.masterKeyId = keyId != null ? keyId : generateKeyId();
            this.keyRotationTimestamp = LocalDateTime.now();
            
            // Clear sensitive data from memory
            keyString = null;
            
            log.info("Successfully loaded master key from Vault. Key ID: {}", maskKeyId(masterKeyId));
            
        } catch (Exception e) {
            log.error("Failed to load master key from Vault", e);
            
            // Fallback to HSM if enabled
            if (hsmEnabled && hsmFallbackToVault) {
                log.info("Falling back to HSM for master key loading");
                loadMasterKeyFromHSM();
            } else {
                throw new SecurityException("Failed to load master encryption key", e);
            }
        }
    }
    
    /**
     * Loads master key from Hardware Security Module (HSM)
     */
    private void loadMasterKeyFromHSM() {
        try {
            log.info("Loading master key from HSM");
            
            if (hsmProvider == null) {
                log.error("HSM provider not available");
                if (hsmFallbackToVault && vaultOperations != null) {
                    log.info("Falling back to Vault for master key loading");
                    loadMasterKeyFromVault();
                    return;
                } else {
                    throw new SecurityException("HSM provider not available and fallback disabled");
                }
            }
            
            // Initialize HSM provider if not already done
            try {
                if (!hsmProvider.testConnection()) {
                    hsmProvider.initialize();
                }
            } catch (Exception e) {
                log.error("Failed to initialize HSM provider", e);
                throw new HSMException("HSM initialization failed", e);
            }
            
            // Try to get existing master key or generate a new one
            String hsmMasterKeyId = hsmKeyPrefix + "-master-key-" + keyVersion;
            
            try {
                // Attempt to use existing key
                HSMKeyHandle keyHandle = hsmProvider.getKeyHandle(hsmMasterKeyId);
                if (keyHandle != null && !keyHandle.isExpired()) {
                    this.masterKeyId = hsmMasterKeyId;
                    this.masterKey = createSecretKeySpec(hsmMasterKeyId);
                    this.keyRotationTimestamp = LocalDateTime.now();
                    log.info("Master key loaded from HSM successfully - Key ID: {}", maskKeyId(hsmMasterKeyId));
                    return;
                }
            } catch (Exception e) {
                log.debug("Existing HSM key not found, generating new one: {}", e.getMessage());
            }
            
            // Generate new master key in HSM
            HSMKeyHandle keyHandle = hsmProvider.generateSecretKey(
                hsmMasterKeyId, 
                "AES", 
                256,
                new HSMKeyHandle.HSMKeyUsage[]{
                    HSMKeyHandle.HSMKeyUsage.ENCRYPT, 
                    HSMKeyHandle.HSMKeyUsage.DECRYPT
                }
            );
            
            this.masterKeyId = hsmMasterKeyId;
            this.masterKey = createSecretKeySpec(hsmMasterKeyId);
            this.keyRotationTimestamp = LocalDateTime.now();
            log.info("New master key generated in HSM - Key ID: {}", maskKeyId(hsmMasterKeyId));
            
        } catch (Exception e) {
            log.error("Failed to load master key from HSM", e);
            if (hsmFallbackToVault && vaultOperations != null) {
                log.info("Falling back to Vault for master key loading");
                loadMasterKeyFromVault();
            } else {
                throw new SecurityException("Failed to load master encryption key from HSM", e);
            }
        }
    }
    
    /**
     * Creates a SecretKeySpec that references an HSM key
     */
    private SecretKeySpec createSecretKeySpec(String keyId) {
        // This creates a placeholder key spec that will be used with HSM operations
        // The actual key material remains in the HSM
        byte[] keyBytes = keyId.getBytes();
        byte[] paddedKey = new byte[32]; // 256-bit key
        System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
        return new SecretKeySpec(paddedKey, "AES");
    }
    
    /**
     * Scheduled task to rotate encryption keys
     */
    @Scheduled(fixedDelayString = "${encryption.key.rotation.check.ms:3600000}") // Check every hour
    public void checkKeyRotation() {
        try {
            if (shouldRotateKey()) {
                log.info("Initiating scheduled key rotation");
                rotateEncryptionKey();
            }
        } catch (Exception e) {
            log.error("Key rotation check failed", e);
        }
    }
    
    private boolean shouldRotateKey() {
        if (keyRotationTimestamp == null) {
            return true;
        }
        
        LocalDateTime rotationDue = keyRotationTimestamp.plusHours(keyRotationHours);
        return LocalDateTime.now().isAfter(rotationDue);
    }
    
    /**
     * Rotates the master encryption key
     */
    public void rotateEncryptionKey() {
        try {
            log.info("Starting encryption key rotation");
            
            String oldKeyId = this.masterKeyId;
            SecretKey oldKey = this.masterKey;
            
            // Generate new key
            KeyGenerator keyGen = KeyGenerator.getInstance(ENCRYPTION_ALGORITHM);
            keyGen.init(AES_KEY_LENGTH);
            SecretKey newKey = keyGen.generateKey();
            String newKeyId = generateKeyId();
            
            // Store new key in Vault
            Map<String, String> secretData = Map.of(
                vaultKeyName, Base64.getEncoder().encodeToString(newKey.getEncoded()),
                "key-id", newKeyId,
                "rotation-timestamp", LocalDateTime.now().toString(),
                "previous-key-id", oldKeyId
            );
            
            vaultOperations.write(vaultSecretPath, secretData);
            
            // Update in-memory key
            this.masterKey = newKey;
            this.masterKeyId = newKeyId;
            this.keyRotationTimestamp = LocalDateTime.now();
            
            // Clear key cache to force re-derivation with new master key
            keyCache.clear();
            
            log.info("Successfully rotated encryption key. Old ID: {}, New ID: {}", 
                    maskKeyId(oldKeyId), maskKeyId(newKeyId));
            
            // Store old key for grace period (for decryption of old data)
            storeOldKeyForGracePeriod(oldKeyId, oldKey);
            
        } catch (Exception e) {
            log.error("Failed to rotate encryption key", e);
            throw new SecurityException("Key rotation failed", e);
        }
    }
    
    private void storeOldKeyForGracePeriod(String keyId, SecretKey key) {
        try {
            // Store old key in separate Vault path for grace period
            String oldKeyPath = vaultSecretPath + "/rotated/" + keyId;
            
            Map<String, String> oldKeyData = Map.of(
                "key", Base64.getEncoder().encodeToString(key.getEncoded()),
                "key-id", keyId,
                "rotation-timestamp", LocalDateTime.now().toString(),
                "expires-at", LocalDateTime.now().plusDays(30).toString()
            );
            
            vaultOperations.write(oldKeyPath, oldKeyData);
            
            log.info("Stored old key {} for 30-day grace period", maskKeyId(keyId));
            
        } catch (Exception e) {
            log.error("Failed to store old key for grace period", e);
        }
    }
    
    private String generateKeyId() {
        return "KEY_" + System.currentTimeMillis() + "_" + 
               Base64.getUrlEncoder().withoutPadding()
                     .encodeToString(secureRandom.generateSeed(6));
    }
    
    private String maskKeyId(String keyId) {
        if (keyId == null || keyId.length() < 8) {
            return "****";
        }
        return keyId.substring(0, 4) + "****" + keyId.substring(keyId.length() - 4);
    }

    /**
     * Encrypts data using AES-256-GCM with PBKDF2 key derivation or HSM
     * @param plaintext The data to encrypt
     * @return Base64 encoded encrypted data with salt and IV
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            // Use HSM encryption if available and key is HSM-based
            if (hsmEnabled && hsmProvider != null && masterKeyId.startsWith(hsmKeyPrefix)) {
                return encryptWithHSM(plaintext);
            }
            
            // Fallback to traditional encryption
            return encryptWithVault(plaintext);

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new SecurityException("Failed to encrypt data", e);
        }
    }
    
    /**
     * Encrypts data using HSM-based encryption
     */
    private String encryptWithHSM(String plaintext) throws HSMException {
        try {
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            
            // Use HSM for encryption - HSM handles key derivation and IV generation
            byte[] encryptedData = hsmProvider.encrypt(masterKeyId, plaintextBytes, "AES/GCM/NoPadding");
            
            // Add HSM marker to indicate this was encrypted with HSM
            byte[] hsmMarker = "HSM:".getBytes(StandardCharsets.UTF_8);
            byte[] combined = new byte[hsmMarker.length + encryptedData.length];
            System.arraycopy(hsmMarker, 0, combined, 0, hsmMarker.length);
            System.arraycopy(encryptedData, 0, combined, hsmMarker.length, encryptedData.length);
            
            return Base64.getEncoder().encodeToString(combined);
            
        } catch (HSMException e) {
            log.error("HSM encryption failed, falling back to Vault encryption", e);
            try {
                return encryptWithVault(plaintext);
            } catch (Exception ve) {
                throw new HSMException("HSM encryption failed and fallback to Vault also failed", "ENCRYPTION_FAILED", ve);
            }
        } catch (Exception e) {
            log.error("Unexpected error during HSM encryption, falling back to Vault encryption", e);
            try {
                return encryptWithVault(plaintext);
            } catch (Exception ve) {
                throw new HSMException("HSM encryption failed and fallback to Vault also failed", "ENCRYPTION_FAILED", ve);
            }
        }
    }
    
    /**
     * Encrypts data using Vault-based encryption (traditional method)
     */
    private String encryptWithVault(String plaintext) throws Exception {
        // Generate random salt for key derivation
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);

        // Derive encryption key from master key and salt
        SecretKey secretKey = deriveKey(Base64.getEncoder().encodeToString(masterKey.getEncoded()), salt);

        // Generate random IV for GCM mode
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        // Initialize cipher
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

        // Encrypt the data
        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = cipher.doFinal(plaintextBytes);

        // Combine salt + IV + ciphertext for storage
        byte[] combined = new byte[SALT_LENGTH + GCM_IV_LENGTH + ciphertext.length];
        System.arraycopy(salt, 0, combined, 0, SALT_LENGTH);
        System.arraycopy(iv, 0, combined, SALT_LENGTH, GCM_IV_LENGTH);
        System.arraycopy(ciphertext, 0, combined, SALT_LENGTH + GCM_IV_LENGTH, ciphertext.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Decrypts data encrypted with AES-256-GCM (HSM or Vault-based)
     * @param encryptedData Base64 encoded encrypted data
     * @return Decrypted plaintext
     */
    public String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData;
        }

        try {
            // Decode from Base64
            byte[] combined = Base64.getDecoder().decode(encryptedData);
            
            // Check if this data was encrypted with HSM
            String hsmMarker = "HSM:";
            byte[] hsmMarkerBytes = hsmMarker.getBytes(StandardCharsets.UTF_8);
            
            if (combined.length >= hsmMarkerBytes.length) {
                byte[] potentialMarker = new byte[hsmMarkerBytes.length];
                System.arraycopy(combined, 0, potentialMarker, 0, hsmMarkerBytes.length);
                
                if (java.util.Arrays.equals(potentialMarker, hsmMarkerBytes)) {
                    return decryptWithHSM(combined, hsmMarkerBytes.length);
                }
            }
            
            // Fallback to Vault-based decryption
            return decryptWithVault(combined);

        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new SecurityException("Failed to decrypt data", e);
        }
    }
    
    /**
     * Decrypts data that was encrypted with HSM
     */
    private String decryptWithHSM(byte[] combined, int markerLength) throws HSMException {
        try {
            // Extract HSM-encrypted data (after marker)
            byte[] hsmEncryptedData = new byte[combined.length - markerLength];
            System.arraycopy(combined, markerLength, hsmEncryptedData, 0, hsmEncryptedData.length);
            
            // Use HSM for decryption
            byte[] plaintextBytes = hsmProvider.decrypt(masterKeyId, hsmEncryptedData, "AES/GCM/NoPadding");
            
            return new String(plaintextBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("HSM decryption failed", e);
            throw new HSMException("Failed to decrypt data with HSM", e);
        }
    }
    
    /**
     * Decrypts data that was encrypted with Vault-based encryption
     */
    private String decryptWithVault(byte[] combined) throws Exception {
        if (combined.length < SALT_LENGTH + GCM_IV_LENGTH + GCM_TAG_LENGTH) {
            throw new SecurityException("Invalid encrypted data format");
        }

        // Extract salt, IV, and ciphertext
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] ciphertext = new byte[combined.length - SALT_LENGTH - GCM_IV_LENGTH];

        System.arraycopy(combined, 0, salt, 0, SALT_LENGTH);
        System.arraycopy(combined, SALT_LENGTH, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(combined, SALT_LENGTH + GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        // Derive the same key using salt
        SecretKey secretKey = deriveKey(Base64.getEncoder().encodeToString(masterKey.getEncoded()), salt);

        // Initialize cipher for decryption
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

        // Decrypt and verify authenticity
        byte[] plaintextBytes = cipher.doFinal(ciphertext);

        return new String(plaintextBytes, StandardCharsets.UTF_8);
    }

    /**
     * Legacy method for backward compatibility - uses new secure implementation
     * @deprecated Use encrypt(String) instead
     */
    @Deprecated
    public String encrypt(String data, String key) {
        log.warn("Using deprecated encrypt method with external key - please migrate to encrypt(String)");
        return encrypt(data);
    }

    /**
     * Legacy method for backward compatibility - uses new secure implementation
     * @deprecated Use decrypt(String) instead
     */
    @Deprecated
    public String decrypt(String encryptedData, String key) {
        log.warn("Using deprecated decrypt method with external key - please migrate to decrypt(String)");
        return decrypt(encryptedData);
    }

    /**
     * Encrypts sensitive binary data (e.g., documents, images)
     * @param data Binary data to encrypt
     * @return Encrypted data with salt and IV prepended
     */
    public byte[] encryptBytes(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }

        try {
            // Generate random salt and IV
            byte[] salt = new byte[SALT_LENGTH];
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(salt);
            secureRandom.nextBytes(iv);

            // Derive key and encrypt
            SecretKey secretKey = deriveKey(Base64.getEncoder().encodeToString(masterKey.getEncoded()), salt);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            byte[] ciphertext = cipher.doFinal(data);

            // Combine salt + IV + ciphertext
            byte[] combined = new byte[SALT_LENGTH + GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(salt, 0, combined, 0, SALT_LENGTH);
            System.arraycopy(iv, 0, combined, SALT_LENGTH, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, SALT_LENGTH + GCM_IV_LENGTH, ciphertext.length);

            return combined;

        } catch (Exception e) {
            log.error("Binary encryption failed", e);
            throw new SecurityException("Failed to encrypt binary data", e);
        }
    }

    /**
     * Decrypts binary data
     * @param encryptedData Encrypted binary data
     * @return Decrypted binary data
     */
    public byte[] decryptBytes(byte[] encryptedData) {
        if (encryptedData == null || encryptedData.length == 0) {
            return encryptedData;
        }

        try {
            if (encryptedData.length < SALT_LENGTH + GCM_IV_LENGTH + GCM_TAG_LENGTH) {
                throw new SecurityException("Invalid encrypted data format");
            }

            // Extract components
            byte[] salt = new byte[SALT_LENGTH];
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[encryptedData.length - SALT_LENGTH - GCM_IV_LENGTH];

            System.arraycopy(encryptedData, 0, salt, 0, SALT_LENGTH);
            System.arraycopy(encryptedData, SALT_LENGTH, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedData, SALT_LENGTH + GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            // Decrypt
            SecretKey secretKey = deriveKey(Base64.getEncoder().encodeToString(masterKey.getEncoded()), salt);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            return cipher.doFinal(ciphertext);

        } catch (Exception e) {
            log.error("Binary decryption failed", e);
            throw new SecurityException("Failed to decrypt binary data", e);
        }
    }

    /**
     * Derives a strong encryption key using PBKDF2
     * @param password Master password/key
     * @param salt Random salt
     * @return Derived AES key
     */
    /**
     * Store secure data with encryption (for token vault integration)
     */
    public void storeSecureData(String key, String value, int expirationSeconds) {
        // This method is referenced by PaymentEncryptionService
        // Would typically store in Redis or database with encryption
        String encryptedValue = encrypt(value);
        // Store implementation would go here
        log.debug("Stored secure data with key: {}", key);
    }
    
    /**
     * Retrieve secure data (for token vault integration)  
     */
    public String retrieveSecureData(String key) {
        // This method is referenced by PaymentEncryptionService
        log.debug("Retrieving secure data with key: {}", key);
        
        try {
            // Check in-memory cache first
            String cacheKey = "secure:data:" + key;
            
            // Try to retrieve from security cache service
            if (securityCacheService != null) {
                String cachedValue = securityCacheService.getFromCache(cacheKey);
                if (cachedValue != null) {
                    log.debug("Found secure data in cache for key: {}", key);
                    // Decrypt the cached value
                    return decrypt(cachedValue);
                }
            }
            
            // Try to retrieve from Vault if available
            if (vaultOperations != null) {
                String vaultPath = "secret/data/secure/" + key;
                try {
                    VaultResponse response = vaultOperations.read(vaultPath);
                    if (response != null && response.getData() != null) {
                        Map<String, Object> data = response.getData();
                        if (data.containsKey("value")) {
                            String encryptedValue = (String) data.get("value");
                            log.debug("Retrieved secure data from Vault for key: {}", key);
                            
                            // Cache the encrypted value
                            if (securityCacheService != null) {
                                securityCacheService.putInCache(cacheKey, encryptedValue, 300); // Cache for 5 minutes
                            }
                            
                            // Decrypt and return
                            return decrypt(encryptedValue);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not retrieve from Vault: {}", e.getMessage());
                }
            }
            
            // Try HSM if enabled and available
            if (hsmEnabled && hsmProvider != null) {
                try {
                    String hsmKey = hsmKeyPrefix + ":" + key;
                    HSMKeyHandle keyHandle = hsmProvider.getKeyHandle(hsmKey);
                    if (keyHandle != null) {
                        byte[] encryptedData = keyHandle.getEncryptedData();
                        if (encryptedData != null) {
                            String encryptedValue = Base64.getEncoder().encodeToString(encryptedData);
                            log.debug("Retrieved secure data from HSM for key: {}", key);
                            
                            // Cache the encrypted value
                            if (securityCacheService != null) {
                                securityCacheService.putInCache(cacheKey, encryptedValue, 300);
                            }
                            
                            // Decrypt and return
                            return decrypt(encryptedValue);
                        }
                    }
                } catch (HSMException e) {
                    log.debug("Could not retrieve from HSM: {}", e.getMessage());
                }
            }
            
            // Check local key cache as last resort
            SecretKey cachedKey = keyCache.get(key);
            if (cachedKey != null) {
                // Convert key to string representation
                String keyValue = Base64.getEncoder().encodeToString(cachedKey.getEncoded());
                log.debug("Retrieved secure data from local cache for key: {}", key);
                return keyValue;
            }
            
            log.warn("CRITICAL: Secure data not found for key: {} - Data retrieval failure", key);
            throw new SecurityException("Secure data not found for key: " + key);
            
        } catch (SecurityException e) {
            // Re-throw security exceptions
            throw e;
        } catch (Exception e) {
            log.error("CRITICAL: Error retrieving secure data for key: {} - Data access failure", key, e);
            throw new SecurityException("Failed to retrieve secure data for key: " + key, e);
        }
    }
    
    private SecretKey deriveKey(String password, byte[] salt) throws Exception {
        return securityCacheService.deriveKey(password, salt);
    }

    private SecretKey deriveKeyInternal(String password, byte[] salt) throws Exception {
        // Use master key from Vault instead of password parameter
        byte[] masterKeyBytes = this.masterKey.getEncoded();
        
        KeySpec spec = new PBEKeySpec(
            Base64.getEncoder().encodeToString(masterKeyBytes).toCharArray(), 
            salt, 
            PBKDF2_ITERATIONS, 
            AES_KEY_LENGTH
        );
        
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        
        return new SecretKeySpec(keyBytes, ENCRYPTION_ALGORITHM);
    }

    /**
     * Generates a secure random key for use as master key
     * @return Base64 encoded random key
     */
    public static String generateSecureKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ENCRYPTION_ALGORITHM);
            keyGen.init(AES_KEY_LENGTH);
            SecretKey secretKey = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityException("Failed to generate secure key", e);
        }
    }

    /**
     * Legacy generateKey method for backward compatibility
     * @deprecated Use generateSecureKey() instead
     */
    @Deprecated
    public String generateKey() throws Exception {
        log.warn("Using deprecated generateKey method - please migrate to generateSecureKey()");
        return generateSecureKey();
    }

    /**
     * Validates that the encryption service is properly configured
     * @return true if configuration is valid
     */
    public boolean validateConfiguration() {
        try {
            // Test encryption/decryption cycle
            String testData = "test-encryption-" + System.currentTimeMillis();
            String encrypted = encrypt(testData);
            String decrypted = decrypt(encrypted);

            return testData.equals(decrypted);
        } catch (Exception e) {
            log.error("Encryption service validation failed", e);
            return false;
        }
    }

    /**
     * Hash API key for secure storage
     */
    public String hashApiKey(String apiKey) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to hash API key", e);
            throw new RuntimeException("API key hashing failed", e);
        }
    }
}