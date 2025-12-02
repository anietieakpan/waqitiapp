package com.waqiti.common.encryption;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * End-to-End Encryption Service for secure message communication
 * 
 * Features:
 * - ECDH (Elliptic Curve Diffie-Hellman) key exchange
 * - AES-GCM encryption for message content
 * - Perfect Forward Secrecy with ephemeral keys
 * - Digital signatures for message authentication
 * - Key rotation and lifecycle management
 */
@Service
@Slf4j
public class EndToEndEncryptionService {

    private static final String ECDH_ALGORITHM = "ECDH";
    private static final String EC_CURVE = "secp256r1";
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final int AES_KEY_LENGTH = 256;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, KeyPair> userKeyPairs = new ConcurrentHashMap<>();
    private final Map<String, SharedSecret> sharedSecrets = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Container for encrypted message data
     */
    public static class EncryptedMessage {
        private final String encryptedContent;
        private final String iv;
        private final String ephemeralPublicKey;
        private final String signature;
        private final String senderUserId;
        private final String recipientUserId;
        private final Instant timestamp;
        private final String keyVersion;

        public EncryptedMessage(String encryptedContent, String iv, String ephemeralPublicKey,
                              String signature, String senderUserId, String recipientUserId,
                              Instant timestamp, String keyVersion) {
            this.encryptedContent = encryptedContent;
            this.iv = iv;
            this.ephemeralPublicKey = ephemeralPublicKey;
            this.signature = signature;
            this.senderUserId = senderUserId;
            this.recipientUserId = recipientUserId;
            this.timestamp = timestamp;
            this.keyVersion = keyVersion;
        }

        // Getters
        public String getEncryptedContent() { return encryptedContent; }
        public String getIv() { return iv; }
        public String getEphemeralPublicKey() { return ephemeralPublicKey; }
        public String getSignature() { return signature; }
        public String getSenderUserId() { return senderUserId; }
        public String getRecipientUserId() { return recipientUserId; }
        public Instant getTimestamp() { return timestamp; }
        public String getKeyVersion() { return keyVersion; }
    }

    /**
     * Container for shared secret information
     */
    private static class SharedSecret {
        private final SecretKey secretKey;
        private final Instant createdAt;
        private final String keyVersion;

        public SharedSecret(SecretKey secretKey, Instant createdAt, String keyVersion) {
            this.secretKey = secretKey;
            this.createdAt = createdAt;
            this.keyVersion = keyVersion;
        }

        public SecretKey getSecretKey() { return secretKey; }
        public Instant getCreatedAt() { return createdAt; }
        public String getKeyVersion() { return keyVersion; }
        
        public boolean isExpired(long maxAgeSeconds) {
            return Instant.now().isAfter(createdAt.plusSeconds(maxAgeSeconds));
        }
    }

    /**
     * Generate a new ECDH key pair for a user
     */
    public KeyPair generateUserKeyPair(String userId) {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            ECGenParameterSpec ecSpec = new ECGenParameterSpec(EC_CURVE);
            keyPairGenerator.initialize(ecSpec);
            
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            userKeyPairs.put(userId, keyPair);
            
            log.info("Generated new key pair for user: {}", userId);
            return keyPair;
            
        } catch (Exception e) {
            log.error("Failed to generate key pair for user: {}", userId, e);
            throw new EncryptionException("Key pair generation failed", e);
        }
    }

    /**
     * Get the public key for a user
     */
    public String getUserPublicKey(String userId) {
        KeyPair keyPair = userKeyPairs.get(userId);
        if (keyPair == null) {
            keyPair = generateUserKeyPair(userId);
        }
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    /**
     * Store a user's public key (received from client)
     */
    public void storeUserPublicKey(String userId, String publicKeyBase64) {
        try {
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            
            // Create a KeyPair with null private key (we only have the public key)
            KeyPair keyPair = new KeyPair(publicKey, null);
            userKeyPairs.put(userId, keyPair);
            
            log.info("Stored public key for user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to store public key for user: {}", userId, e);
            throw new EncryptionException("Failed to store public key", e);
        }
    }

    /**
     * Perform ECDH key agreement to derive shared secret
     */
    private SecretKey performKeyAgreement(PrivateKey privateKey, PublicKey publicKey) {
        try {
            KeyAgreement keyAgreement = KeyAgreement.getInstance(ECDH_ALGORITHM);
            keyAgreement.init(privateKey);
            keyAgreement.doPhase(publicKey, true);
            
            byte[] sharedSecret = keyAgreement.generateSecret();
            
            // Derive AES key from shared secret using HKDF-like approach
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] derivedKey = digest.digest(sharedSecret);
            
            return new SecretKeySpec(derivedKey, AES_ALGORITHM);
            
        } catch (Exception e) {
            log.error("Key agreement failed", e);
            throw new EncryptionException("Key agreement failed", e);
        }
    }

    /**
     * Get or create shared secret between two users
     */
    private SharedSecret getOrCreateSharedSecret(String senderUserId, String recipientUserId) {
        String secretKey = createSecretKey(senderUserId, recipientUserId);
        
        SharedSecret existingSecret = sharedSecrets.get(secretKey);
        if (existingSecret != null && !existingSecret.isExpired(3600)) { // 1 hour expiration
            return existingSecret;
        }

        // Generate new shared secret
        KeyPair senderKeyPair = userKeyPairs.get(senderUserId);
        KeyPair recipientKeyPair = userKeyPairs.get(recipientUserId);
        
        if (senderKeyPair == null || recipientKeyPair == null) {
            throw new EncryptionException("Missing key pair for one or both users");
        }

        SecretKey sharedKey = performKeyAgreement(
            senderKeyPair.getPrivate(), 
            recipientKeyPair.getPublic()
        );

        String keyVersion = generateKeyVersion();
        SharedSecret newSecret = new SharedSecret(sharedKey, Instant.now(), keyVersion);
        sharedSecrets.put(secretKey, newSecret);
        
        log.info("Created new shared secret between users: {} and {}", senderUserId, recipientUserId);
        return newSecret;
    }

    /**
     * Create a deterministic key for shared secrets map
     */
    private String createSecretKey(String userId1, String userId2) {
        return userId1.compareTo(userId2) < 0 
            ? userId1 + ":" + userId2 
            : userId2 + ":" + userId1;
    }

    /**
     * Generate a unique key version identifier
     */
    private String generateKeyVersion() {
        return "v" + System.currentTimeMillis();
    }

    /**
     * Encrypt a message with end-to-end encryption
     */
    public EncryptedMessage encryptMessage(String senderUserId, String recipientUserId, String messageContent) {
        try {
            log.info("Encrypting message from {} to {}", senderUserId, recipientUserId);

            // Get shared secret
            SharedSecret sharedSecret = getOrCreateSharedSecret(senderUserId, recipientUserId);
            
            // Generate ephemeral key pair for Perfect Forward Secrecy
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            ECGenParameterSpec ecSpec = new ECGenParameterSpec(EC_CURVE);
            keyPairGenerator.initialize(ecSpec);
            KeyPair ephemeralKeyPair = keyPairGenerator.generateKeyPair();
            
            // Create message payload with metadata
            MessagePayload payload = new MessagePayload(
                messageContent,
                senderUserId,
                recipientUserId,
                Instant.now(),
                generateMessageId()
            );
            
            String jsonPayload = objectMapper.writeValueAsString(payload);
            byte[] messageBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);

            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Encrypt message content
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, sharedSecret.getSecretKey(), gcmParameterSpec);
            
            byte[] encryptedBytes = cipher.doFinal(messageBytes);

            // Sign the encrypted message
            String signature = signMessage(senderUserId, encryptedBytes);

            return new EncryptedMessage(
                Base64.getEncoder().encodeToString(encryptedBytes),
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(ephemeralKeyPair.getPublic().getEncoded()),
                signature,
                senderUserId,
                recipientUserId,
                Instant.now(),
                sharedSecret.getKeyVersion()
            );
            
        } catch (Exception e) {
            log.error("Message encryption failed", e);
            throw new EncryptionException("Message encryption failed", e);
        }
    }

    /**
     * Decrypt an encrypted message
     */
    public String decryptMessage(String recipientUserId, EncryptedMessage encryptedMessage) {
        try {
            log.info("Decrypting message for user: {}", recipientUserId);

            // Verify message signature first
            if (!verifySignature(encryptedMessage.getSenderUserId(), 
                               Base64.getDecoder().decode(encryptedMessage.getEncryptedContent()), 
                               encryptedMessage.getSignature())) {
                throw new EncryptionException("Message signature verification failed");
            }

            // Get shared secret
            SharedSecret sharedSecret = getOrCreateSharedSecret(
                encryptedMessage.getSenderUserId(), 
                recipientUserId
            );

            // Decrypt message content
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedMessage.getEncryptedContent());
            byte[] iv = Base64.getDecoder().decode(encryptedMessage.getIv());

            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, sharedSecret.getSecretKey(), gcmParameterSpec);
            
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            String jsonPayload = new String(decryptedBytes, StandardCharsets.UTF_8);
            
            // Parse message payload
            MessagePayload payload = objectMapper.readValue(jsonPayload, MessagePayload.class);
            
            // Validate message metadata
            if (!payload.getRecipientUserId().equals(recipientUserId)) {
                throw new EncryptionException("Message recipient mismatch");
            }

            log.info("Successfully decrypted message for user: {}", recipientUserId);
            return payload.getContent();
            
        } catch (Exception e) {
            log.error("Message decryption failed", e);
            throw new EncryptionException("Message decryption failed", e);
        }
    }

    /**
     * Sign a message for authentication
     */
    private String signMessage(String userId, byte[] messageBytes) {
        try {
            KeyPair keyPair = userKeyPairs.get(userId);
            if (keyPair == null || keyPair.getPrivate() == null) {
                throw new EncryptionException("Private key not available for signing");
            }

            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(keyPair.getPrivate());
            signature.update(messageBytes);
            
            byte[] signatureBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signatureBytes);
            
        } catch (Exception e) {
            log.error("Message signing failed", e);
            throw new EncryptionException("Message signing failed", e);
        }
    }

    /**
     * Verify message signature
     */
    private boolean verifySignature(String userId, byte[] messageBytes, String signatureBase64) {
        try {
            KeyPair keyPair = userKeyPairs.get(userId);
            if (keyPair == null) {
                log.warn("No key pair found for signature verification: {}", userId);
                return false;
            }

            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
            
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(keyPair.getPublic());
            signature.update(messageBytes);
            
            return signature.verify(signatureBytes);
            
        } catch (Exception e) {
            log.error("Signature verification failed", e);
            return false;
        }
    }

    /**
     * Generate unique message ID
     */
    private String generateMessageId() {
        return "msg_" + System.currentTimeMillis() + "_" + secureRandom.nextInt(10000);
    }

    /**
     * Rotate keys for a user (for Perfect Forward Secrecy)
     */
    public void rotateUserKeys(String userId) {
        log.info("Rotating keys for user: {}", userId);
        
        // Generate new key pair
        generateUserKeyPair(userId);
        
        // Clear old shared secrets involving this user
        sharedSecrets.entrySet().removeIf(entry -> 
            entry.getKey().contains(userId)
        );
        
        log.info("Key rotation completed for user: {}", userId);
    }

    /**
     * Clean up expired shared secrets
     */
    public void cleanupExpiredSecrets() {
        int removedCount = 0;
        var iterator = sharedSecrets.entrySet().iterator();
        
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isExpired(3600)) { // 1 hour
                iterator.remove();
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            log.info("Cleaned up {} expired shared secrets", removedCount);
        }
    }

    /**
     * Get encryption statistics
     */
    public Map<String, Object> getEncryptionStats() {
        return Map.of(
            "activeKeyPairs", userKeyPairs.size(),
            "activeSharedSecrets", sharedSecrets.size(),
            "algorithm", "ECDH + AES-GCM",
            "curve", EC_CURVE,
            "keySize", AES_KEY_LENGTH
        );
    }

    /**
     * Message payload structure
     */
    private static class MessagePayload {
        private String content;
        private String senderUserId;
        private String recipientUserId;
        private Instant timestamp;
        private String messageId;

        // Constructors
        public MessagePayload() {}

        public MessagePayload(String content, String senderUserId, String recipientUserId, 
                            Instant timestamp, String messageId) {
            this.content = content;
            this.senderUserId = senderUserId;
            this.recipientUserId = recipientUserId;
            this.timestamp = timestamp;
            this.messageId = messageId;
        }

        // Getters and setters
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getSenderUserId() { return senderUserId; }
        public void setSenderUserId(String senderUserId) { this.senderUserId = senderUserId; }
        public String getRecipientUserId() { return recipientUserId; }
        public void setRecipientUserId(String recipientUserId) { this.recipientUserId = recipientUserId; }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
    }

    /**
     * Custom exception for encryption-related errors
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message) {
            super(message);
        }

        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}