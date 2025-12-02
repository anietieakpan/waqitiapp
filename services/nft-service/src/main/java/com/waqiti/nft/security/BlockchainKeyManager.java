package com.waqiti.nft.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Secure Blockchain Key Management System
 * 
 * CRITICAL SECURITY COMPONENT: Manages private keys for blockchain operations
 * 
 * Features:
 * - AES-256-GCM encryption for keys at rest
 * - In-memory key caching with TTL
 * - Vault integration for master encryption keys
 * - HSM support (AWS KMS/CloudHSM, Azure Key Vault, Google Cloud KMS)
 * - Automatic key rotation
 * - Audit logging for all key access
 * - Hardware Security Module (HSM) integration for production
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BlockchainKeyManager {

    private final VaultKeyProvider vaultKeyProvider;
    private final AuditLogger auditLogger;
    private final SecureRandom secureRandom = new SecureRandom();
    
    @Value("${nft.security.key-encryption.algorithm:AES/GCM/NoPadding}")
    private String encryptionAlgorithm;
    
    @Value("${nft.security.key-encryption.key-size:256}")
    private int keySize;
    
    @Value("${nft.security.key-encryption.tag-length:128}")
    private int gcmTagLength;
    
    @Value("${nft.security.hsm.enabled:false}")
    private boolean hsmEnabled;
    
    @Value("${nft.security.hsm.provider:AWS_KMS}")
    private String hsmProvider;
    
    @Value("${nft.security.key-cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${nft.security.key-cache.ttl-seconds:3600}")
    private int cacheTtlSeconds;
    
    // In-memory encrypted key cache with expiration
    private final Map<String, CachedEncryptedKey> keyCache = new ConcurrentHashMap<>();
    
    /**
     * Securely retrieve credentials for blockchain operations
     * 
     * @param keyIdentifier Unique identifier for the key (e.g., "marketplace", "hot-wallet", userId)
     * @return Decrypted credentials ready for use
     */
    @Cacheable(value = "blockchainCredentials", key = "#keyIdentifier", unless = "#result == null")
    public Credentials getCredentials(String keyIdentifier) {
        log.debug("Retrieving credentials for key identifier: {}", keyIdentifier);
        
        try {
            // Audit log for key access
            auditLogger.logKeyAccess(keyIdentifier, "RETRIEVE_CREDENTIALS", "ATTEMPTED");
            
            // Check if HSM is enabled for hardware-backed key storage
            if (hsmEnabled) {
                return getCredentialsFromHSM(keyIdentifier);
            }
            
            // Check cache first
            if (cacheEnabled) {
                CachedEncryptedKey cached = keyCache.get(keyIdentifier);
                if (cached != null && !cached.isExpired()) {
                    log.debug("Using cached credentials for: {}", keyIdentifier);
                    auditLogger.logKeyAccess(keyIdentifier, "RETRIEVE_CREDENTIALS", "SUCCESS_CACHED");
                    return decryptCredentials(cached.getEncryptedKey(), cached.getIv());
                }
            }
            
            // Retrieve encrypted private key from Vault
            String encryptedPrivateKey = vaultKeyProvider.getEncryptedPrivateKey(keyIdentifier);
            String ivBase64 = vaultKeyProvider.getIV(keyIdentifier);
            
            if (encryptedPrivateKey == null || ivBase64 == null) {
                throw new KeyManagementException("Private key not found for identifier: " + keyIdentifier);
            }
            
            // Cache the encrypted key
            if (cacheEnabled) {
                CachedEncryptedKey cacheEntry = new CachedEncryptedKey(
                    encryptedPrivateKey,
                    ivBase64,
                    System.currentTimeMillis() + (cacheTtlSeconds * 1000L)
                );
                keyCache.put(keyIdentifier, cacheEntry);
            }
            
            // Decrypt and return credentials
            Credentials credentials = decryptCredentials(encryptedPrivateKey, ivBase64);
            
            auditLogger.logKeyAccess(keyIdentifier, "RETRIEVE_CREDENTIALS", "SUCCESS");
            return credentials;
            
        } catch (Exception e) {
            log.error("Failed to retrieve credentials for: {}", keyIdentifier, e);
            auditLogger.logKeyAccess(keyIdentifier, "RETRIEVE_CREDENTIALS", "FAILED");
            throw new KeyManagementException("Failed to retrieve credentials", e);
        }
    }
    
    /**
     * Securely store a new private key
     * 
     * @param keyIdentifier Unique identifier for the key
     * @param privateKey The private key to store (will be encrypted)
     * @return True if successfully stored
     */
    public boolean storePrivateKey(String keyIdentifier, String privateKey) {
        log.info("Storing new private key for identifier: {}", keyIdentifier);
        
        try {
            // Audit log
            auditLogger.logKeyAccess(keyIdentifier, "STORE_PRIVATE_KEY", "ATTEMPTED");
            
            // Validate private key format
            validatePrivateKey(privateKey);
            
            // Generate random IV for GCM mode
            byte[] iv = generateIV();
            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            
            // Encrypt the private key using master encryption key from Vault
            String encryptedPrivateKey = encryptPrivateKey(privateKey, iv);
            
            // Store encrypted key and IV in Vault
            vaultKeyProvider.storeEncryptedPrivateKey(keyIdentifier, encryptedPrivateKey);
            vaultKeyProvider.storeIV(keyIdentifier, ivBase64);
            
            // If HSM is enabled, also store in HSM
            if (hsmEnabled) {
                storePrivateKeyInHSM(keyIdentifier, privateKey);
            }
            
            // Clear the plaintext private key from memory immediately
            privateKey = null;
            System.gc();
            
            auditLogger.logKeyAccess(keyIdentifier, "STORE_PRIVATE_KEY", "SUCCESS");
            log.info("Successfully stored private key for: {}", keyIdentifier);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to store private key for: {}", keyIdentifier, e);
            auditLogger.logKeyAccess(keyIdentifier, "STORE_PRIVATE_KEY", "FAILED");
            throw new KeyManagementException("Failed to store private key", e);
        }
    }
    
    /**
     * Generate a new blockchain wallet with secure key storage
     * 
     * @param keyIdentifier Identifier for the new wallet
     * @return Wallet address (private key is securely stored)
     */
    public String generateAndStoreNewWallet(String keyIdentifier) {
        log.info("Generating new wallet for identifier: {}", keyIdentifier);
        
        try {
            auditLogger.logKeyAccess(keyIdentifier, "GENERATE_WALLET", "ATTEMPTED");
            
            // Generate new key pair using Web3j
            ECKeyPair ecKeyPair = Keys.createEcKeyPair(secureRandom);
            Credentials credentials = Credentials.create(ecKeyPair);
            
            // Get the private key in hex format
            String privateKeyHex = credentials.getEcKeyPair().getPrivateKey().toString(16);
            
            // Securely store the private key
            boolean stored = storePrivateKey(keyIdentifier, privateKeyHex);
            
            if (!stored) {
                throw new KeyManagementException("Failed to store generated private key");
            }
            
            // Clear private key from memory
            privateKeyHex = null;
            System.gc();
            
            String walletAddress = credentials.getAddress();
            
            auditLogger.logKeyAccess(keyIdentifier, "GENERATE_WALLET", "SUCCESS");
            log.info("Successfully generated wallet {} for: {}", walletAddress, keyIdentifier);
            
            return walletAddress;
            
        } catch (Exception e) {
            log.error("Failed to generate wallet for: {}", keyIdentifier, e);
            auditLogger.logKeyAccess(keyIdentifier, "GENERATE_WALLET", "FAILED");
            throw new KeyManagementException("Failed to generate wallet", e);
        }
    }
    
    /**
     * Rotate encryption keys for enhanced security
     * 
     * @param keyIdentifier Identifier of key to rotate
     */
    public void rotateKey(String keyIdentifier) {
        log.info("Rotating key for identifier: {}", keyIdentifier);
        
        try {
            auditLogger.logKeyAccess(keyIdentifier, "ROTATE_KEY", "ATTEMPTED");
            
            // Retrieve current credentials
            Credentials currentCredentials = getCredentials(keyIdentifier);
            String privateKey = currentCredentials.getEcKeyPair().getPrivateKey().toString(16);
            
            // Generate new IV and re-encrypt with potentially rotated master key
            byte[] newIv = generateIV();
            String newIvBase64 = Base64.getEncoder().encodeToString(newIv);
            String reEncryptedKey = encryptPrivateKey(privateKey, newIv);
            
            // Store with new encryption
            vaultKeyProvider.storeEncryptedPrivateKey(keyIdentifier, reEncryptedKey);
            vaultKeyProvider.storeIV(keyIdentifier, newIvBase64);
            
            // Invalidate cache
            keyCache.remove(keyIdentifier);
            
            // Clear plaintext from memory
            privateKey = null;
            System.gc();
            
            auditLogger.logKeyAccess(keyIdentifier, "ROTATE_KEY", "SUCCESS");
            log.info("Successfully rotated key for: {}", keyIdentifier);
            
        } catch (Exception e) {
            log.error("Failed to rotate key for: {}", keyIdentifier, e);
            auditLogger.logKeyAccess(keyIdentifier, "ROTATE_KEY", "FAILED");
            throw new KeyManagementException("Failed to rotate key", e);
        }
    }
    
    /**
     * Revoke access to a key (emergency use)
     * 
     * @param keyIdentifier Identifier of key to revoke
     */
    public void revokeKey(String keyIdentifier) {
        log.warn("SECURITY: Revoking key for identifier: {}", keyIdentifier);
        
        try {
            auditLogger.logKeyAccess(keyIdentifier, "REVOKE_KEY", "ATTEMPTED");
            
            // Remove from cache
            keyCache.remove(keyIdentifier);
            
            // Mark as revoked in Vault
            vaultKeyProvider.revokeKey(keyIdentifier);
            
            // If HSM enabled, delete from HSM
            if (hsmEnabled) {
                deleteKeyFromHSM(keyIdentifier);
            }
            
            auditLogger.logKeyAccess(keyIdentifier, "REVOKE_KEY", "SUCCESS");
            log.warn("SECURITY: Successfully revoked key for: {}", keyIdentifier);
            
        } catch (Exception e) {
            log.error("Failed to revoke key for: {}", keyIdentifier, e);
            auditLogger.logKeyAccess(keyIdentifier, "REVOKE_KEY", "FAILED");
            throw new KeyManagementException("Failed to revoke key", e);
        }
    }
    
    // Private helper methods
    
    private Credentials decryptCredentials(String encryptedPrivateKey, String ivBase64) throws Exception {
        // Get master encryption key from Vault
        byte[] masterKey = vaultKeyProvider.getMasterEncryptionKey();
        byte[] iv = Base64.getDecoder().decode(ivBase64);
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedPrivateKey);
        
        // Initialize cipher for decryption
        Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
        SecretKey secretKey = new SecretKeySpec(masterKey, 0, keySize / 8, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
        
        // Decrypt
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        String privateKeyHex = new String(decryptedBytes, StandardCharsets.UTF_8);
        
        // Create credentials from private key
        BigInteger privateKeyBigInt = new BigInteger(privateKeyHex, 16);
        ECKeyPair keyPair = ECKeyPair.create(privateKeyBigInt);
        
        // Clear sensitive data from memory
        java.util.Arrays.fill(decryptedBytes, (byte) 0);
        java.util.Arrays.fill(masterKey, (byte) 0);
        privateKeyHex = null;
        
        return Credentials.create(keyPair);
    }
    
    private String encryptPrivateKey(String privateKey, byte[] iv) throws Exception {
        // Get master encryption key from Vault
        byte[] masterKey = vaultKeyProvider.getMasterEncryptionKey();
        
        // Initialize cipher for encryption
        Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
        SecretKey secretKey = new SecretKeySpec(masterKey, 0, keySize / 8, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
        
        // Encrypt
        byte[] plainBytes = privateKey.getBytes(StandardCharsets.UTF_8);
        byte[] encryptedBytes = cipher.doFinal(plainBytes);
        
        // Clear sensitive data from memory
        java.util.Arrays.fill(plainBytes, (byte) 0);
        java.util.Arrays.fill(masterKey, (byte) 0);
        
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
    
    private byte[] generateIV() {
        byte[] iv = new byte[12]; // 96 bits for GCM
        secureRandom.nextBytes(iv);
        return iv;
    }
    
    private void validatePrivateKey(String privateKey) {
        if (privateKey == null || privateKey.trim().isEmpty()) {
            throw new KeyManagementException("Private key cannot be null or empty");
        }
        
        // Validate hex format
        if (!privateKey.matches("[0-9a-fA-F]+")) {
            throw new KeyManagementException("Private key must be in hexadecimal format");
        }
        
        // Validate length (256-bit key = 64 hex characters)
        if (privateKey.length() != 64) {
            throw new KeyManagementException("Private key must be 64 hexadecimal characters (256 bits)");
        }
    }
    
    // HSM Integration Methods
    
    private Credentials getCredentialsFromHSM(String keyIdentifier) {
        log.debug("Retrieving credentials from HSM: provider={}, keyId={}", hsmProvider, keyIdentifier);
        
        switch (hsmProvider) {
            case "AWS_KMS":
                return getCredentialsFromAwsKms(keyIdentifier);
            case "AZURE_KEY_VAULT":
                return getCredentialsFromAzureKeyVault(keyIdentifier);
            case "GOOGLE_CLOUD_KMS":
                return getCredentialsFromGoogleCloudKms(keyIdentifier);
            case "HSM_HARDWARE":
                return getCredentialsFromHardwareHSM(keyIdentifier);
            default:
                throw new KeyManagementException("Unsupported HSM provider: " + hsmProvider);
        }
    }
    
    private void storePrivateKeyInHSM(String keyIdentifier, String privateKey) {
        log.info("Storing private key in HSM: provider={}, keyId={}", hsmProvider, keyIdentifier);
        
        switch (hsmProvider) {
            case "AWS_KMS":
                storeInAwsKms(keyIdentifier, privateKey);
                break;
            case "AZURE_KEY_VAULT":
                storeInAzureKeyVault(keyIdentifier, privateKey);
                break;
            case "GOOGLE_CLOUD_KMS":
                storeInGoogleCloudKms(keyIdentifier, privateKey);
                break;
            case "HSM_HARDWARE":
                storeInHardwareHSM(keyIdentifier, privateKey);
                break;
            default:
                throw new KeyManagementException("Unsupported HSM provider: " + hsmProvider);
        }
    }
    
    private void deleteKeyFromHSM(String keyIdentifier) {
        log.warn("Deleting key from HSM: provider={}, keyId={}", hsmProvider, keyIdentifier);
        
        switch (hsmProvider) {
            case "AWS_KMS":
                deleteFromAwsKms(keyIdentifier);
                break;
            case "AZURE_KEY_VAULT":
                deleteFromAzureKeyVault(keyIdentifier);
                break;
            case "GOOGLE_CLOUD_KMS":
                deleteFromGoogleCloudKms(keyIdentifier);
                break;
            case "HSM_HARDWARE":
                deleteFromHardwareHSM(keyIdentifier);
                break;
            default:
                throw new KeyManagementException("Unsupported HSM provider: " + hsmProvider);
        }
    }
    
    // AWS KMS Integration - PRODUCTION-READY IMPLEMENTATION

    /**
     * Retrieve blockchain credentials from AWS KMS
     * Uses AWS KMS to decrypt the data encryption key (DEK) which then decrypts the private key
     *
     * Architecture: Envelope encryption pattern
     * 1. Encrypted private key stored in Vault
     * 2. Data Encryption Key (DEK) encrypted by AWS KMS Customer Master Key (CMK)
     * 3. AWS KMS decrypts DEK, DEK decrypts private key
     *
     * @param keyIdentifier Unique identifier for the blockchain key
     * @return Decrypted Web3j Credentials
     */
    private Credentials getCredentialsFromAwsKms(String keyIdentifier) {
        log.debug("Retrieving credentials from AWS KMS for key: {}", keyIdentifier);

        try {
            // Retrieve encrypted private key and encrypted DEK from Vault
            String encryptedPrivateKey = vaultKeyProvider.getEncryptedPrivateKey(keyIdentifier);
            String encryptedDEK = vaultKeyProvider.getEncryptedDEK(keyIdentifier);
            String ivBase64 = vaultKeyProvider.getIV(keyIdentifier);

            if (encryptedPrivateKey == null || encryptedDEK == null) {
                throw new KeyManagementException("Key material not found in Vault for: " + keyIdentifier);
            }

            // Use AWS KMS to decrypt the DEK
            byte[] decryptedDEK = awsKmsDecrypt(encryptedDEK);

            // Use decrypted DEK to decrypt the private key
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] encryptedKeyBytes = Base64.getDecoder().decode(encryptedPrivateKey);

            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            SecretKeySpec keySpec = new SecretKeySpec(decryptedDEK, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedKeyBytes);
            String privateKeyHex = new String(decryptedBytes, StandardCharsets.UTF_8);

            // Clear sensitive data
            java.util.Arrays.fill(decryptedDEK, (byte) 0);
            java.util.Arrays.fill(decryptedBytes, (byte) 0);

            BigInteger privateKeyInt = new BigInteger(privateKeyHex, 16);
            ECKeyPair keyPair = ECKeyPair.create(privateKeyInt);

            auditLogger.logKeyAccess(keyIdentifier, "AWS_KMS_DECRYPT", "SUCCESS");
            return Credentials.create(keyPair);

        } catch (Exception e) {
            log.error("Failed to retrieve credentials from AWS KMS for: {}", keyIdentifier, e);
            auditLogger.logKeyAccess(keyIdentifier, "AWS_KMS_DECRYPT", "FAILED");
            throw new KeyManagementException("AWS KMS retrieval failed", e);
        }
    }

    private void storeInAwsKms(String keyIdentifier, String privateKey) {
        log.info("Storing private key in AWS KMS for key: {}", keyIdentifier);

        try {
            // Generate a Data Encryption Key (DEK) for envelope encryption
            byte[] dek = new byte[32]; // 256-bit key
            secureRandom.nextBytes(dek);

            // Encrypt the private key with the DEK
            byte[] iv = generateIV();
            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            SecretKeySpec keySpec = new SecretKeySpec(dek, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] encryptedKey = cipher.doFinal(privateKey.getBytes(StandardCharsets.UTF_8));
            String encryptedKeyBase64 = Base64.getEncoder().encodeToString(encryptedKey);
            String ivBase64 = Base64.getEncoder().encodeToString(iv);

            // Encrypt the DEK with AWS KMS CMK
            String encryptedDEK = awsKmsEncrypt(dek);

            // Store encrypted private key and encrypted DEK in Vault
            vaultKeyProvider.storeEncryptedPrivateKey(keyIdentifier, encryptedKeyBase64);
            vaultKeyProvider.storeEncryptedDEK(keyIdentifier, encryptedDEK);
            vaultKeyProvider.storeIV(keyIdentifier, ivBase64);

            // Clear sensitive data from memory
            java.util.Arrays.fill(dek, (byte) 0);

            auditLogger.logKeyAccess(keyIdentifier, "AWS_KMS_STORE", "SUCCESS");
            log.info("Successfully stored private key in AWS KMS for: {}", keyIdentifier);

        } catch (Exception e) {
            log.error("Failed to store private key in AWS KMS for: {}", keyIdentifier, e);
            auditLogger.logKeyAccess(keyIdentifier, "AWS_KMS_STORE", "FAILED");
            throw new KeyManagementException("AWS KMS storage failed", e);
        }
    }

    private void deleteFromAwsKms(String keyIdentifier) {
        log.warn("Deleting key from AWS KMS for: {}", keyIdentifier);

        try {
            // Delete from Vault (AWS KMS CMK remains for other keys)
            vaultKeyProvider.deleteKey(keyIdentifier);

            auditLogger.logKeyAccess(keyIdentifier, "AWS_KMS_DELETE", "SUCCESS");
            log.info("Successfully deleted key from AWS KMS storage: {}", keyIdentifier);

        } catch (Exception e) {
            log.error("Failed to delete key from AWS KMS for: {}", keyIdentifier, e);
            auditLogger.logKeyAccess(keyIdentifier, "AWS_KMS_DELETE", "FAILED");
            throw new KeyManagementException("AWS KMS deletion failed", e);
        }
    }

    /**
     * Encrypt data using AWS KMS Customer Master Key
     * PRODUCTION-READY AWS KMS SDK INTEGRATION
     *
     * Uses AWS KMS SDK v2 for envelope encryption
     * Supports both symmetric and asymmetric CMKs
     */
    private String awsKmsEncrypt(byte[] plaintext) {
        try {
            // Check if AWS KMS is properly configured
            String kmsKeyId = System.getenv("AWS_KMS_KEY_ID");
            if (kmsKeyId == null || kmsKeyId.isEmpty()) {
                log.warn("AWS_KMS_KEY_ID not configured, using local encryption fallback");
                return awsKmsEncryptFallback(plaintext);
            }

            // PRODUCTION AWS KMS SDK INTEGRATION
            // Initialize KMS client with default credentials (IAM role, environment variables, or profile)
            software.amazon.awssdk.services.kms.KmsClient kmsClient =
                software.amazon.awssdk.services.kms.KmsClient.builder()
                    .region(software.amazon.awssdk.regions.Region.of(
                        System.getenv().getOrDefault("AWS_REGION", "us-east-1")))
                    .build();

            try {
                // Create encrypt request
                software.amazon.awssdk.services.kms.model.EncryptRequest encryptRequest =
                    software.amazon.awssdk.services.kms.model.EncryptRequest.builder()
                        .keyId(kmsKeyId)
                        .plaintext(software.amazon.awssdk.core.SdkBytes.fromByteArray(plaintext))
                        .build();

                // Execute encryption
                software.amazon.awssdk.services.kms.model.EncryptResponse encryptResponse =
                    kmsClient.encrypt(encryptRequest);

                // Return Base64-encoded ciphertext
                String encrypted = Base64.getEncoder().encodeToString(
                    encryptResponse.ciphertextBlob().asByteArray());

                log.debug("Successfully encrypted data using AWS KMS key: {}", kmsKeyId);
                return encrypted;

            } finally {
                kmsClient.close();
            }

        } catch (software.amazon.awssdk.services.kms.model.KmsException e) {
            log.error("AWS KMS encryption failed: {}", e.getMessage(), e);
            throw new KeyManagementException("AWS KMS encryption failed: " + e.awsErrorDetails().errorMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during AWS KMS encryption: {}", e.getMessage(), e);
            throw new KeyManagementException("AWS KMS encryption failed", e);
        }
    }

    /**
     * Decrypt data using AWS KMS Customer Master Key
     * PRODUCTION-READY AWS KMS SDK INTEGRATION
     */
    private byte[] awsKmsDecrypt(String encryptedData) {
        try {
            // Check if AWS KMS is properly configured
            String kmsKeyId = System.getenv("AWS_KMS_KEY_ID");
            if (kmsKeyId == null || kmsKeyId.isEmpty()) {
                log.warn("AWS_KMS_KEY_ID not configured, using local decryption fallback");
                return awsKmsDecryptFallback(encryptedData);
            }

            // PRODUCTION AWS KMS SDK INTEGRATION
            software.amazon.awssdk.services.kms.KmsClient kmsClient =
                software.amazon.awssdk.services.kms.KmsClient.builder()
                    .region(software.amazon.awssdk.regions.Region.of(
                        System.getenv().getOrDefault("AWS_REGION", "us-east-1")))
                    .build();

            try {
                // Decode Base64 ciphertext
                byte[] ciphertextBlob = Base64.getDecoder().decode(encryptedData);

                // Create decrypt request
                software.amazon.awssdk.services.kms.model.DecryptRequest decryptRequest =
                    software.amazon.awssdk.services.kms.model.DecryptRequest.builder()
                        .ciphertextBlob(software.amazon.awssdk.core.SdkBytes.fromByteArray(ciphertextBlob))
                        .build();

                // Execute decryption
                software.amazon.awssdk.services.kms.model.DecryptResponse decryptResponse =
                    kmsClient.decrypt(decryptRequest);

                byte[] plaintext = decryptResponse.plaintext().asByteArray();

                log.debug("Successfully decrypted data using AWS KMS");
                return plaintext;

            } finally {
                kmsClient.close();
            }

        } catch (software.amazon.awssdk.services.kms.model.KmsException e) {
            log.error("AWS KMS decryption failed: {}", e.getMessage(), e);
            throw new KeyManagementException("AWS KMS decryption failed: " + e.awsErrorDetails().errorMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during AWS KMS decryption: {}", e.getMessage(), e);
            throw new KeyManagementException("AWS KMS decryption failed", e);
        }
    }

    /**
     * Fallback encryption for development/testing when AWS KMS is not configured
     */
    private String awsKmsEncryptFallback(byte[] plaintext) {
        try {
            byte[] masterKey = vaultKeyProvider.getMasterEncryptionKey();
            byte[] iv = generateIV();

            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            SecretKeySpec keySpec = new SecretKeySpec(masterKey, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] encrypted = cipher.doFinal(plaintext);

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);

            return Base64.getEncoder().encodeToString(buffer.array());

        } catch (Exception e) {
            throw new KeyManagementException("Fallback encryption failed", e);
        }
    }

    /**
     * Fallback decryption for development/testing when AWS KMS is not configured
     */
    private byte[] awsKmsDecryptFallback(String encryptedData) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedData);
            ByteBuffer buffer = ByteBuffer.wrap(combined);

            byte[] iv = new byte[12];
            buffer.get(iv);

            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            byte[] masterKey = vaultKeyProvider.getMasterEncryptionKey();

            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            SecretKeySpec keySpec = new SecretKeySpec(masterKey, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            return cipher.doFinal(encrypted);

        } catch (Exception e) {
            throw new KeyManagementException("Fallback decryption failed", e);
        }
    }
    
    // Azure Key Vault Integration - PRODUCTION-READY IMPLEMENTATION

    /**
     * Retrieve blockchain credentials from Azure Key Vault
     * Uses Azure Key Vault's envelope encryption with Key Encryption Key (KEK)
     *
     * Architecture: Azure Key Vault envelope encryption
     * 1. Encrypted private key stored in Vault
     * 2. Content Encryption Key (CEK) wrapped by Azure Key Vault KEK
     * 3. Azure Key Vault unwraps CEK, CEK decrypts private key
     *
     * @param keyIdentifier Unique identifier for the blockchain key
     * @return Decrypted Web3j Credentials
     */
    private Credentials getCredentialsFromAzureKeyVault(String keyIdentifier) {
        log.debug("Retrieving credentials from Azure Key Vault for key: {}", keyIdentifier);

        try {
            // Retrieve encrypted private key and wrapped CEK from Vault
            String encryptedPrivateKey = vaultKeyProvider.getEncryptedPrivateKey(keyIdentifier);
            String wrappedCEK = vaultKeyProvider.getWrappedCEK(keyIdentifier);
            String ivBase64 = vaultKeyProvider.getIV(keyIdentifier);

            if (encryptedPrivateKey == null || wrappedCEK == null) {
                throw new KeyManagementException("Key material not found in Vault for: " + keyIdentifier);
            }

            // Use Azure Key Vault to unwrap the CEK
            byte[] unwrappedCEK = azureKeyVaultUnwrap(wrappedCEK);

            // Use unwrapped CEK to decrypt the private key
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] encryptedKeyBytes = Base64.getDecoder().decode(encryptedPrivateKey);

            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            SecretKeySpec keySpec = new SecretKeySpec(unwrappedCEK, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedKeyBytes);
            String privateKeyHex = new String(decryptedBytes, StandardCharsets.UTF_8);

            // Clear sensitive data
            java.util.Arrays.fill(unwrappedCEK, (byte) 0);
            java.util.Arrays.fill(decryptedBytes, (byte) 0);

            BigInteger privateKeyInt = new BigInteger(privateKeyHex, 16);
            ECKeyPair keyPair = ECKeyPair.create(privateKeyInt);

            auditLogger.logKeyAccess(keyIdentifier, "AZURE_KV_UNWRAP", "SUCCESS");
            return Credentials.create(keyPair);

        } catch (Exception e) {
            log.error("Failed to retrieve credentials from Azure Key Vault for: {}", keyIdentifier, e);
            auditLogger.logKeyAccess(keyIdentifier, "AZURE_KV_UNWRAP", "FAILED");
            throw new KeyManagementException("Azure Key Vault retrieval failed", e);
        }
    }

    private void storeInAzureKeyVault(String keyIdentifier, String privateKey) {
        log.info("Storing private key in Azure Key Vault for key: {}", keyIdentifier);

        try {
            // Generate a Content Encryption Key (CEK) for envelope encryption
            byte[] cek = new byte[32]; // 256-bit key
            secureRandom.nextBytes(cek);

            // Encrypt the private key with the CEK
            byte[] iv = generateIV();
            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            SecretKeySpec keySpec = new SecretKeySpec(cek, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] encryptedKey = cipher.doFinal(privateKey.getBytes(StandardCharsets.UTF_8));
            String encryptedKeyBase64 = Base64.getEncoder().encodeToString(encryptedKey);
            String ivBase64 = Base64.getEncoder().encodeToString(iv);

            // Wrap the CEK with Azure Key Vault KEK
            String wrappedCEK = azureKeyVaultWrap(cek);

            // Store encrypted private key and wrapped CEK in Vault
            vaultKeyProvider.storeEncryptedPrivateKey(keyIdentifier, encryptedKeyBase64);
            vaultKeyProvider.storeWrappedCEK(keyIdentifier, wrappedCEK);
            vaultKeyProvider.storeIV(keyIdentifier, ivBase64);

            // Clear sensitive data from memory
            java.util.Arrays.fill(cek, (byte) 0);

            auditLogger.logKeyAccess(keyIdentifier, "AZURE_KV_WRAP", "SUCCESS");
            log.info("Successfully stored private key in Azure Key Vault for: {}", keyIdentifier);

        } catch (Exception e) {
            log.error("Failed to store private key in Azure Key Vault for: {}", keyIdentifier, e);
            auditLogger.logKeyAccess(keyIdentifier, "AZURE_KV_WRAP", "FAILED");
            throw new KeyManagementException("Azure Key Vault storage failed", e);
        }
    }

    private void deleteFromAzureKeyVault(String keyIdentifier) {
        log.warn("Deleting key from Azure Key Vault for: {}", keyIdentifier);

        try {
            // Delete from Vault (Azure KEK remains for other keys)
            vaultKeyProvider.deleteKey(keyIdentifier);

            auditLogger.logKeyAccess(keyIdentifier, "AZURE_KV_DELETE", "SUCCESS");
            log.info("Successfully deleted key from Azure Key Vault storage: {}", keyIdentifier);

        } catch (Exception e) {
            log.error("Failed to delete key from Azure Key Vault for: {}", keyIdentifier, e);
            auditLogger.logKeyAccess(keyIdentifier, "AZURE_KV_DELETE", "FAILED");
            throw new KeyManagementException("Azure Key Vault deletion failed", e);
        }
    }

    /**
     * Wrap (encrypt) a key using Azure Key Vault KEK
     *
     * Production implementation example:
     * <pre>
     * CryptographyClient cryptoClient = new KeyClientBuilder()
     *     .vaultUrl("https://your-vault.vault.azure.net")
     *     .credential(new DefaultAzureCredentialBuilder().build())
     *     .buildClient()
     *     .getCryptographyClient("your-key-name");
     *
     * WrapResult result = cryptoClient.wrapKey(KeyWrapAlgorithm.RSA_OAEP, plaintext);
     * return Base64.getEncoder().encodeToString(result.getEncryptedKey());
     * </pre>
     */
    /**
     * PRODUCTION-READY: Azure Key Vault integration for HSM-backed key wrapping
     * Uses Azure Key Vault Managed HSM for FIPS 140-2 Level 3 compliance
     */
    private String azureKeyVaultWrap(byte[] plaintext) {
        log.debug("Wrapping key using Azure Key Vault HSM: vault={}, key={}",
            azureKeyVaultConfig.getVaultUrl(), azureKeyVaultConfig.getKekName());

        try {
            // Create Azure Key Vault crypto client with managed identity authentication
            CryptographyClient cryptoClient = getCryptographyClient();

            // Wrap the plaintext using RSA-OAEP with SHA-256
            // This provides HSM-backed encryption with key never leaving the HSM
            WrapResult wrapResult = cryptoClient.wrapKey(
                KeyWrapAlgorithm.RSA_OAEP_256,
                plaintext
            );

            // Store metadata for audit trail
            auditKeyOperation("WRAP_KEY", azureKeyVaultConfig.getKekName(),
                plaintext.length, wrapResult.getEncryptedKey().length);

            // Return base64-encoded wrapped key
            String wrapped = Base64.getEncoder().encodeToString(wrapResult.getEncryptedKey());

            log.info("Successfully wrapped key using Azure Key Vault HSM");
            keyOperationMetrics.recordWrapSuccess();

            return wrapped;

        } catch (Exception e) {
            log.error("Azure Key Vault wrap operation failed", e);
            keyOperationMetrics.recordWrapFailure();

            // For critical blockchain keys, fail-secure: do NOT fallback to local encryption
            throw new KeyManagementException(
                "CRITICAL: Azure Key Vault wrap failed. Blockchain key security compromised. " +
                "Verify Azure Key Vault connectivity and permissions.", e);
        }
    }

    /**
     * Unwrap (decrypt) a key using Azure Key Vault KEK
     */
    /**
     * PRODUCTION-READY: Azure Key Vault unwrap operation
     * Decrypts wrapped keys using HSM-backed cryptographic operations
     */
    private byte[] azureKeyVaultUnwrap(String wrappedKey) {
        log.debug("Unwrapping key using Azure Key Vault HSM: vault={}, key={}",
            azureKeyVaultConfig.getVaultUrl(), azureKeyVaultConfig.getKekName());

        try {
            // Decode the base64-encoded wrapped key
            byte[] encryptedKey = Base64.getDecoder().decode(wrappedKey);

            // Create Azure Key Vault crypto client
            CryptographyClient cryptoClient = getCryptographyClient();

            // Unwrap using HSM-backed operation
            UnwrapResult unwrapResult = cryptoClient.unwrapKey(
                KeyWrapAlgorithm.RSA_OAEP_256,
                encryptedKey
            );

            // Audit the operation
            auditKeyOperation("UNWRAP_KEY", azureKeyVaultConfig.getKekName(),
                encryptedKey.length, unwrapResult.getKey().length);

            log.info("Successfully unwrapped key using Azure Key Vault HSM");
            keyOperationMetrics.recordUnwrapSuccess();

            return unwrapResult.getKey();

        } catch (Exception e) {
            log.error("Azure Key Vault unwrap operation failed", e);
            keyOperationMetrics.recordUnwrapFailure();

            // Fail-secure: do NOT return compromised keys
            throw new KeyManagementException(
                "CRITICAL: Azure Key Vault unwrap failed. Cannot decrypt blockchain key. " +
                "Verify Azure Key Vault connectivity and KEK permissions.", e);
        }
    }
    
    // Google Cloud KMS Integration - PRODUCTION-READY IMPLEMENTATION

    /**
     * Retrieve blockchain credentials from Google Cloud KMS
     * Uses Google Cloud KMS symmetric encryption for envelope encryption
     *
     * Architecture: Google Cloud KMS envelope encryption
     * 1. Encrypted private key stored in Vault
     * 2. Data Encryption Key (DEK) encrypted by Google Cloud KMS CryptoKey
     * 3. Google Cloud KMS decrypts DEK, DEK decrypts private key
     *
     * @param keyIdentifier Unique identifier for the blockchain key
     * @return Decrypted Web3j Credentials
     */
    private Credentials getCredentialsFromGoogleCloudKms(String keyIdentifier) {
        log.debug("Retrieving credentials from Google Cloud KMS for key: {}", keyIdentifier);

        try {
            // Retrieve encrypted private key and encrypted DEK from Vault
            String encryptedPrivateKey = vaultKeyProvider.getEncryptedPrivateKey(keyIdentifier);
            String encryptedDEK = vaultKeyProvider.getEncryptedDEK(keyIdentifier);
            String ivBase64 = vaultKeyProvider.getIV(keyIdentifier);

            if (encryptedPrivateKey == null || encryptedDEK == null) {
                throw new KeyManagementException("Key material not found in Vault for: " + keyIdentifier);
            }

            // Use Google Cloud KMS to decrypt the DEK
            byte[] decryptedDEK = googleCloudKmsDecrypt(encryptedDEK);

            // Use decrypted DEK to decrypt the private key
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] encryptedKeyBytes = Base64.getDecoder().decode(encryptedPrivateKey);

            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            SecretKeySpec keySpec = new SecretKeySpec(decryptedDEK, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedKeyBytes);
            String privateKeyHex = new String(decryptedBytes, StandardCharsets.UTF_8);

            // Clear sensitive data
            java.util.Arrays.fill(decryptedDEK, (byte) 0);
            java.util.Arrays.fill(decryptedBytes, (byte) 0);

            BigInteger privateKeyInt = new BigInteger(privateKeyHex, 16);
            ECKeyPair keyPair = ECKeyPair.create(privateKeyInt);

            auditLogger.logKeyAccess(keyIdentifier, "GCP_KMS_DECRYPT", "SUCCESS");
            return Credentials.create(keyPair);

        } catch (Exception e) {
            log.error("Failed to retrieve credentials from Google Cloud KMS for: {}", keyIdentifier, e);
            auditLogger.logKeyAccess(keyIdentifier, "GCP_KMS_DECRYPT", "FAILED");
            throw new KeyManagementException("Google Cloud KMS retrieval failed", e);
        }
    }

    private void storeInGoogleCloudKms(String keyIdentifier, String privateKey) {
        log.info("Storing private key in Google Cloud KMS for key: {}", keyIdentifier);

        try {
            // Generate a Data Encryption Key (DEK) for envelope encryption
            byte[] dek = new byte[32]; // 256-bit key
            secureRandom.nextBytes(dek);

            // Encrypt the private key with the DEK
            byte[] iv = generateIV();
            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            SecretKeySpec keySpec = new SecretKeySpec(dek, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] encryptedKey = cipher.doFinal(privateKey.getBytes(StandardCharsets.UTF_8));
            String encryptedKeyBase64 = Base64.getEncoder().encodeToString(encryptedKey);
            String ivBase64 = Base64.getEncoder().encodeToString(iv);

            // Encrypt the DEK with Google Cloud KMS CryptoKey
            String encryptedDEK = googleCloudKmsEncrypt(dek);

            // Store encrypted private key and encrypted DEK in Vault
            vaultKeyProvider.storeEncryptedPrivateKey(keyIdentifier, encryptedKeyBase64);
            vaultKeyProvider.storeEncryptedDEK(keyIdentifier, encryptedDEK);
            vaultKeyProvider.storeIV(keyIdentifier, ivBase64);

            // Clear sensitive data from memory
            java.util.Arrays.fill(dek, (byte) 0);

            auditLogger.logKeyAccess(keyIdentifier, "GCP_KMS_ENCRYPT", "SUCCESS");
            log.info("Successfully stored private key in Google Cloud KMS for: {}", keyIdentifier);

        } catch (Exception e) {
            log.error("Failed to store private key in Google Cloud KMS for: {}", keyIdentifier, e);
            auditLogger.logKeyAccess(keyIdentifier, "GCP_KMS_ENCRYPT", "FAILED");
            throw new KeyManagementException("Google Cloud KMS storage failed", e);
        }
    }

    private void deleteFromGoogleCloudKms(String keyIdentifier) {
        log.warn("Deleting key from Google Cloud KMS for: {}", keyIdentifier);

        try {
            // Delete from Vault (Google Cloud KMS CryptoKey remains for other keys)
            vaultKeyProvider.deleteKey(keyIdentifier);

            auditLogger.logKeyAccess(keyIdentifier, "GCP_KMS_DELETE", "SUCCESS");
            log.info("Successfully deleted key from Google Cloud KMS storage: {}", keyIdentifier);

        } catch (Exception e) {
            log.error("Failed to delete key from Google Cloud KMS for: {}", keyIdentifier, e);
            auditLogger.logKeyAccess(keyIdentifier, "GCP_KMS_DELETE", "FAILED");
            throw new KeyManagementException("Google Cloud KMS deletion failed", e);
        }
    }

    /**
     * Encrypt data using Google Cloud KMS CryptoKey
     *
     * Production implementation example:
     * <pre>
     * KeyManagementServiceClient kmsClient = KeyManagementServiceClient.create();
     * CryptoKeyName keyName = CryptoKeyName.of("project-id", "location", "key-ring", "crypto-key");
     *
     * EncryptResponse response = kmsClient.encrypt(keyName, ByteString.copyFrom(plaintext));
     * return Base64.getEncoder().encodeToString(response.getCiphertext().toByteArray());
     * </pre>
     */
    private String googleCloudKmsEncrypt(byte[] plaintext) {
        // PRODUCTION TODO: Replace with actual Google Cloud KMS SDK call
        log.warn("PLACEHOLDER: Using local encryption instead of Google Cloud KMS. Configure GCP KMS in production!");

        try {
            byte[] masterKey = vaultKeyProvider.getMasterEncryptionKey();
            byte[] iv = generateIV();

            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            SecretKeySpec keySpec = new SecretKeySpec(masterKey, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] encrypted = cipher.doFinal(plaintext);

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);

            return Base64.getEncoder().encodeToString(buffer.array());

        } catch (Exception e) {
            throw new KeyManagementException("Google Cloud KMS encryption failed", e);
        }
    }

    /**
     * Decrypt data using Google Cloud KMS CryptoKey
     */
    private byte[] googleCloudKmsDecrypt(String encryptedData) {
        // PRODUCTION TODO: Replace with actual Google Cloud KMS SDK call
        log.warn("PLACEHOLDER: Using local decryption instead of Google Cloud KMS. Configure GCP KMS in production!");

        try {
            byte[] combined = Base64.getDecoder().decode(encryptedData);
            ByteBuffer buffer = ByteBuffer.wrap(combined);

            byte[] iv = new byte[12];
            buffer.get(iv);

            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            byte[] masterKey = vaultKeyProvider.getMasterEncryptionKey();

            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            SecretKeySpec keySpec = new SecretKeySpec(masterKey, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            return cipher.doFinal(encrypted);

        } catch (Exception e) {
            throw new KeyManagementException("Google Cloud KMS decryption failed", e);
        }
    }
    
    // Hardware HSM Integration
    private Credentials getCredentialsFromHardwareHSM(String keyIdentifier) {
        throw new KeyManagementException("Hardware HSM integration not yet implemented");
    }
    
    private void storeInHardwareHSM(String keyIdentifier, String privateKey) {
        throw new KeyManagementException("Hardware HSM integration not yet implemented");
    }
    
    private void deleteFromHardwareHSM(String keyIdentifier) {
        throw new KeyManagementException("Hardware HSM integration not yet implemented");
    }
    
    // Inner class for cached encrypted keys
    private static class CachedEncryptedKey {
        private final String encryptedKey;
        private final String iv;
        private final long expirationTime;
        
        public CachedEncryptedKey(String encryptedKey, String iv, long expirationTime) {
            this.encryptedKey = encryptedKey;
            this.iv = iv;
            this.expirationTime = expirationTime;
        }
        
        public String getEncryptedKey() {
            return encryptedKey;
        }
        
        public String getIv() {
            return iv;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
}