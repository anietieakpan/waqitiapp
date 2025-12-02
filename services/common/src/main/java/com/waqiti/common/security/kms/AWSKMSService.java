package com.waqiti.common.security.kms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AWS KMS Service for cryptographic operations
 * 
 * Provides secure key management and signing capabilities using AWS KMS.
 * Used for blockchain transaction signing, JWT token signing, and data encryption.
 * 
 * Security Features:
 * - Hardware Security Module (HSM) backed keys
 * - FIPS 140-2 Level 2 validated
 * - Audit logging to CloudTrail
 * - Key rotation support
 * - Multi-region replication
 * 
 * Compliance:
 * - PCI DSS 3.2.1 (Key Management)
 * - SOC 2 Type II
 * - ISO 27001
 * - HIPAA
 * 
 * @author Waqiti Security Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class AWSKMSService {
    
    private final String region;
    private final String cryptoSigningKeyId;
    private final String jwtSigningKeyId;
    private final String dataEncryptionKeyId;
    private final boolean enabled;
    private final int cacheMaxSize;
    private final long cacheTtlMillis;
    
    private KmsClient kmsClient;
    private final Map<String, CachedKey> keyCache = new ConcurrentHashMap<>();
    
    public AWSKMSService(
            @Value("${aws.kms.region:us-east-1}") String region,
            @Value("${aws.kms.crypto-signing-key-id:alias/waqiti-crypto-signing}") String cryptoSigningKeyId,
            @Value("${aws.kms.jwt-signing-key-id:alias/waqiti-jwt-signing}") String jwtSigningKeyId,
            @Value("${aws.kms.data-encryption-key-id:alias/waqiti-data-encryption}") String dataEncryptionKeyId,
            @Value("${aws.kms.enabled:true}") boolean enabled,
            @Value("${aws.kms.cache.max-size:100}") int cacheMaxSize,
            @Value("${aws.kms.cache.ttl-millis:300000}") long cacheTtlMillis) {
        
        this.region = region;
        this.cryptoSigningKeyId = cryptoSigningKeyId;
        this.jwtSigningKeyId = jwtSigningKeyId;
        this.dataEncryptionKeyId = dataEncryptionKeyId;
        this.enabled = enabled;
        this.cacheMaxSize = cacheMaxSize;
        this.cacheTtlMillis = cacheTtlMillis;
    }
    
    @PostConstruct
    public void initialize() {
        if (!enabled) {
            log.warn("AWS KMS is DISABLED - using fallback local signing (NOT FOR PRODUCTION)");
            return;
        }
        
        try {
            log.info("Initializing AWS KMS Service - Region: {}", region);
            
            this.kmsClient = KmsClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
            
            // Verify connectivity and key access
            verifyKeyAccess(cryptoSigningKeyId, "Crypto Signing");
            verifyKeyAccess(jwtSigningKeyId, "JWT Signing");
            verifyKeyAccess(dataEncryptionKeyId, "Data Encryption");
            
            log.info("AWS KMS Service initialized successfully");
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to initialize AWS KMS Service", e);
            throw new KMSInitializationException("KMS initialization failed", e);
        }
    }
    
    /**
     * Sign a blockchain transaction using AWS KMS
     * 
     * Used for signing Ethereum/Polygon/BSC transactions with ECDSA secp256k1.
     * The transaction hash is signed by the KMS key, ensuring private keys
     * never leave the HSM.
     * 
     * @param keyAlias KMS key alias (e.g., "alias/waqiti-crypto-signing")
     * @param transactionHash SHA3-256 hash of the transaction (32 bytes)
     * @return DER-encoded ECDSA signature (r, s, v components)
     * @throws KMSOperationException if signing fails
     */
    public byte[] signTransaction(String keyAlias, byte[] transactionHash) {
        if (!enabled) {
            throw new KMSOperationException("KMS is disabled - cannot sign transaction");
        }
        
        if (transactionHash == null || transactionHash.length != 32) {
            throw new IllegalArgumentException("Transaction hash must be exactly 32 bytes");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("KMS: Signing blockchain transaction with key: {}", keyAlias);
            
            // Create signing request
            SignRequest signRequest = SignRequest.builder()
                    .keyId(keyAlias)
                    .message(SdkBytes.fromByteArray(transactionHash))
                    .messageType(MessageType.DIGEST) // Pre-hashed message
                    .signingAlgorithm(SigningAlgorithmSpec.ECDSA_SHA_256)
                    .build();
            
            // Sign with KMS
            SignResponse signResponse = kmsClient.sign(signRequest);
            byte[] signature = signResponse.signature().asByteArray();
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("KMS: Transaction signed successfully - Duration: {}ms, Signature length: {}", 
                    duration, signature.length);
            
            // Audit log
            auditLog("SIGN_TRANSACTION", keyAlias, transactionHash.length, signature.length, duration);
            
            return signature;
            
        } catch (KmsException e) {
            log.error("KMS: Transaction signing failed - Key: {}, Error: {}", 
                    keyAlias, e.awsErrorDetails().errorMessage(), e);
            throw new KMSOperationException("KMS signing failed: " + e.awsErrorDetails().errorMessage(), e);
            
        } catch (Exception e) {
            log.error("KMS: Unexpected error during transaction signing", e);
            throw new KMSOperationException("Unexpected signing error", e);
        }
    }
    
    /**
     * Sign JWT token with AWS KMS
     * 
     * Signs JWT tokens using RS256 (RSA-SHA256) algorithm.
     * Ensures JWT signing keys are managed securely in HSM.
     * 
     * @param jwtPayload JWT payload to sign
     * @return Base64-encoded signature
     */
    public String signJWT(String jwtPayload) {
        if (!enabled) {
            throw new KMSOperationException("KMS is disabled - cannot sign JWT");
        }
        
        try {
            // Hash the JWT payload with SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jwtPayload.getBytes(StandardCharsets.UTF_8));
            
            // Sign with KMS
            SignRequest signRequest = SignRequest.builder()
                    .keyId(jwtSigningKeyId)
                    .message(SdkBytes.fromByteArray(hash))
                    .messageType(MessageType.DIGEST)
                    .signingAlgorithm(SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256)
                    .build();
            
            SignResponse signResponse = kmsClient.sign(signRequest);
            byte[] signature = signResponse.signature().asByteArray();
            
            log.debug("KMS: JWT signed successfully - Payload length: {}", jwtPayload.length());
            
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
            
        } catch (Exception e) {
            log.error("KMS: JWT signing failed", e);
            throw new KMSOperationException("JWT signing failed", e);
        }
    }
    
    /**
     * Verify JWT signature with AWS KMS
     * 
     * @param jwtPayload Original JWT payload
     * @param signature Base64-encoded signature to verify
     * @return true if signature is valid
     */
    public boolean verifyJWT(String jwtPayload, String signature) {
        if (!enabled) {
            throw new KMSOperationException("KMS is disabled - cannot verify JWT");
        }
        
        try {
            // Hash the JWT payload
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jwtPayload.getBytes(StandardCharsets.UTF_8));
            
            // Decode signature
            byte[] signatureBytes = Base64.getUrlDecoder().decode(signature);
            
            // Verify with KMS
            VerifyRequest verifyRequest = VerifyRequest.builder()
                    .keyId(jwtSigningKeyId)
                    .message(SdkBytes.fromByteArray(hash))
                    .messageType(MessageType.DIGEST)
                    .signature(SdkBytes.fromByteArray(signatureBytes))
                    .signingAlgorithm(SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256)
                    .build();
            
            VerifyResponse verifyResponse = kmsClient.verify(verifyRequest);
            
            return verifyResponse.signatureValid();
            
        } catch (Exception e) {
            log.error("KMS: JWT verification failed", e);
            return false;
        }
    }
    
    /**
     * Encrypt data using AWS KMS
     * 
     * Uses envelope encryption:
     * 1. Generate data encryption key (DEK) from KMS
     * 2. Encrypt data with DEK locally
     * 3. Return encrypted DEK + encrypted data
     * 
     * @param plaintext Data to encrypt
     * @return Encrypted data bundle (encrypted DEK + ciphertext)
     */
    public EncryptedDataBundle encrypt(byte[] plaintext) {
        if (!enabled) {
            throw new KMSOperationException("KMS is disabled - cannot encrypt data");
        }
        
        try {
            // Generate data encryption key
            GenerateDataKeyRequest dataKeyRequest = GenerateDataKeyRequest.builder()
                    .keyId(dataEncryptionKeyId)
                    .keySpec(DataKeySpec.AES_256)
                    .build();
            
            GenerateDataKeyResponse dataKeyResponse = kmsClient.generateDataKey(dataKeyRequest);
            
            byte[] plaintextKey = dataKeyResponse.plaintext().asByteArray();
            byte[] encryptedKey = dataKeyResponse.ciphertextBlob().asByteArray();
            
            // Encrypt data locally with DEK (AES-256-GCM)
            byte[] ciphertext = encryptWithAES(plaintext, plaintextKey);
            
            // Clear plaintext key from memory
            java.util.Arrays.fill(plaintextKey, (byte) 0);
            
            log.debug("KMS: Data encrypted - Plaintext: {} bytes, Ciphertext: {} bytes", 
                    plaintext.length, ciphertext.length);
            
            return new EncryptedDataBundle(encryptedKey, ciphertext);
            
        } catch (Exception e) {
            log.error("KMS: Encryption failed", e);
            throw new KMSOperationException("Data encryption failed", e);
        }
    }
    
    /**
     * Decrypt data using AWS KMS
     * 
     * @param encryptedBundle Encrypted data bundle from encrypt()
     * @return Decrypted plaintext
     */
    public byte[] decrypt(EncryptedDataBundle encryptedBundle) {
        if (!enabled) {
            throw new KMSOperationException("KMS is disabled - cannot decrypt data");
        }
        
        try {
            // Decrypt the data encryption key with KMS
            DecryptRequest decryptRequest = DecryptRequest.builder()
                    .ciphertextBlob(SdkBytes.fromByteArray(encryptedBundle.getEncryptedKey()))
                    .keyId(dataEncryptionKeyId)
                    .build();
            
            DecryptResponse decryptResponse = kmsClient.decrypt(decryptRequest);
            byte[] plaintextKey = decryptResponse.plaintext().asByteArray();
            
            // Decrypt data locally with DEK
            byte[] plaintext = decryptWithAES(encryptedBundle.getCiphertext(), plaintextKey);
            
            // Clear plaintext key from memory
            java.util.Arrays.fill(plaintextKey, (byte) 0);
            
            log.debug("KMS: Data decrypted - Ciphertext: {} bytes, Plaintext: {} bytes", 
                    encryptedBundle.getCiphertext().length, plaintext.length);
            
            return plaintext;
            
        } catch (Exception e) {
            log.error("KMS: Decryption failed", e);
            throw new KMSOperationException("Data decryption failed", e);
        }
    }
    
    /**
     * Get public key for signature verification
     * 
     * @param keyAlias KMS key alias
     * @return DER-encoded public key
     */
    public byte[] getPublicKey(String keyAlias) {
        if (!enabled) {
            throw new KMSOperationException("KMS is disabled - cannot retrieve public key");
        }
        
        // Check cache first
        CachedKey cached = keyCache.get(keyAlias);
        if (cached != null && !cached.isExpired()) {
            log.debug("KMS: Public key retrieved from cache - Key: {}", keyAlias);
            return cached.getPublicKey();
        }
        
        try {
            GetPublicKeyRequest request = GetPublicKeyRequest.builder()
                    .keyId(keyAlias)
                    .build();
            
            GetPublicKeyResponse response = kmsClient.getPublicKey(request);
            byte[] publicKey = response.publicKey().asByteArray();
            
            // Cache the public key
            keyCache.put(keyAlias, new CachedKey(publicKey, System.currentTimeMillis()));
            
            // Evict old cache entries if needed
            if (keyCache.size() > cacheMaxSize) {
                evictOldestCacheEntry();
            }
            
            log.info("KMS: Public key retrieved - Key: {}, Size: {} bytes", keyAlias, publicKey.length);
            
            return publicKey;
            
        } catch (Exception e) {
            log.error("KMS: Failed to retrieve public key - Key: {}", keyAlias, e);
            throw new KMSOperationException("Public key retrieval failed", e);
        }
    }
    
    /**
     * Rotate KMS key
     * 
     * Enables automatic key rotation for the specified key.
     * AWS KMS rotates keys annually.
     * 
     * @param keyAlias KMS key alias
     */
    public void enableKeyRotation(String keyAlias) {
        if (!enabled) {
            log.warn("KMS is disabled - skipping key rotation");
            return;
        }
        
        try {
            EnableKeyRotationRequest request = EnableKeyRotationRequest.builder()
                    .keyId(keyAlias)
                    .build();
            
            kmsClient.enableKeyRotation(request);
            
            log.info("KMS: Key rotation enabled - Key: {}", keyAlias);
            
        } catch (Exception e) {
            log.error("KMS: Failed to enable key rotation - Key: {}", keyAlias, e);
            throw new KMSOperationException("Key rotation failed", e);
        }
    }
    
    /**
     * Create KMS key
     * 
     * @param alias Key alias (e.g., "alias/waqiti-new-key")
     * @param description Key description
     * @param keySpec Key specification (ECC_SECG_P256K1 for blockchain, RSA_2048 for JWT)
     * @return Key ID
     */
    public String createKey(String alias, String description, KeySpec keySpec) {
        if (!enabled) {
            throw new KMSOperationException("KMS is disabled - cannot create key");
        }
        
        try {
            // Create key
            CreateKeyRequest createRequest = CreateKeyRequest.builder()
                    .description(description)
                    .keyUsage(KeyUsageType.SIGN_VERIFY)
                    .keySpec(keySpec)
                    .origin(OriginType.AWS_KMS)
                    .build();
            
            CreateKeyResponse createResponse = kmsClient.createKey(createRequest);
            String keyId = createResponse.keyMetadata().keyId();
            
            // Create alias
            CreateAliasRequest aliasRequest = CreateAliasRequest.builder()
                    .aliasName(alias)
                    .targetKeyId(keyId)
                    .build();
            
            kmsClient.createAlias(aliasRequest);
            
            log.info("KMS: Key created - Alias: {}, ID: {}, Spec: {}", alias, keyId, keySpec);
            
            return keyId;
            
        } catch (Exception e) {
            log.error("KMS: Failed to create key - Alias: {}", alias, e);
            throw new KMSOperationException("Key creation failed", e);
        }
    }
    
    // Private helper methods
    
    private void verifyKeyAccess(String keyId, String keyPurpose) {
        try {
            DescribeKeyRequest request = DescribeKeyRequest.builder()
                    .keyId(keyId)
                    .build();
            
            DescribeKeyResponse response = kmsClient.describeKey(request);
            KeyMetadata metadata = response.keyMetadata();
            
            log.info("KMS: Verified access to {} key - ID: {}, State: {}, Spec: {}", 
                    keyPurpose, metadata.keyId(), metadata.keyState(), metadata.keySpec());
            
            if (metadata.keyState() != KeyState.ENABLED) {
                throw new KMSInitializationException("Key is not enabled: " + keyId);
            }
            
        } catch (NotFoundException e) {
            throw new KMSInitializationException("Key not found: " + keyId, e);
        }
    }
    
    private byte[] encryptWithAES(byte[] plaintext, byte[] key) throws Exception {
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(key, "AES");
        
        // Generate random IV
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);
        
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        byte[] ciphertext = cipher.doFinal(plaintext);
        
        // Prepend IV to ciphertext
        ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
        buffer.put(iv);
        buffer.put(ciphertext);
        return buffer.array();
    }
    
    private byte[] decryptWithAES(byte[] ciphertext, byte[] key) throws Exception {
        // Extract IV from ciphertext
        ByteBuffer buffer = ByteBuffer.wrap(ciphertext);
        byte[] iv = new byte[12];
        buffer.get(iv);
        byte[] actualCiphertext = new byte[buffer.remaining()];
        buffer.get(actualCiphertext);
        
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(key, "AES");
        javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);
        
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        return cipher.doFinal(actualCiphertext);
    }
    
    private void auditLog(String operation, String keyAlias, int inputSize, int outputSize, long durationMs) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("operation", operation);
        auditData.put("keyAlias", keyAlias);
        auditData.put("inputSize", inputSize);
        auditData.put("outputSize", outputSize);
        auditData.put("durationMs", durationMs);
        auditData.put("timestamp", java.time.Instant.now());
        
        log.info("KMS_AUDIT: {}", auditData);
    }
    
    private void evictOldestCacheEntry() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        
        for (Map.Entry<String, CachedKey> entry : keyCache.entrySet()) {
            if (entry.getValue().getCachedAt() < oldestTime) {
                oldestTime = entry.getValue().getCachedAt();
                oldestKey = entry.getKey();
            }
        }
        
        if (oldestKey != null) {
            keyCache.remove(oldestKey);
            log.debug("KMS: Evicted oldest cache entry - Key: {}", oldestKey);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        if (kmsClient != null) {
            log.info("Shutting down AWS KMS Service");
            kmsClient.close();
        }
    }
    
    // Inner classes
    
    private class CachedKey {
        private final byte[] publicKey;
        private final long cachedAt;
        
        public CachedKey(byte[] publicKey, long cachedAt) {
            this.publicKey = publicKey;
            this.cachedAt = cachedAt;
        }
        
        public byte[] getPublicKey() {
            return publicKey;
        }
        
        public long getCachedAt() {
            return cachedAt;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > cacheTtlMillis;
        }
    }
    
    public static class EncryptedDataBundle {
        private final byte[] encryptedKey;
        private final byte[] ciphertext;
        
        public EncryptedDataBundle(byte[] encryptedKey, byte[] ciphertext) {
            this.encryptedKey = encryptedKey;
            this.ciphertext = ciphertext;
        }
        
        public byte[] getEncryptedKey() {
            return encryptedKey;
        }
        
        public byte[] getCiphertext() {
            return ciphertext;
        }
    }
    
    public static class KMSInitializationException extends RuntimeException {
        public KMSInitializationException(String message) {
            super(message);
        }
        
        public KMSInitializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class KMSOperationException extends RuntimeException {
        public KMSOperationException(String message) {
            super(message);
        }
        
        public KMSOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}