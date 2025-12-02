package com.waqiti.messaging.service;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.AeadKeyTemplates;
import com.waqiti.messaging.domain.UserKeyBundle;
import com.waqiti.messaging.dto.EncryptedMessage;
import com.waqiti.messaging.dto.KeyExchangeData;
import com.waqiti.messaging.exception.EncryptionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;
import org.whispersystems.curve25519.Curve25519;
import org.whispersystems.curve25519.Curve25519KeyPair;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class EncryptionService {
    
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int AES_KEY_SIZE = 256;
    private static final int IV_SIZE = 12;
    private static final int TAG_SIZE = 128;
    
    private final KeyManagementService keyManagementService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentMap<String, SessionKeys> sessionCache = new ConcurrentHashMap<>();
    
    static {
        Security.addProvider(new BouncyCastleProvider());
    }
    
    @PostConstruct
    public void init() {
        try {
            AeadConfig.register();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize encryption", e);
        }
    }
    
    public EncryptedMessage encryptMessage(String senderId, String recipientId, String content, MessageType messageType) {
        try {
            // Get or establish session
            SessionKeys sessionKeys = getOrEstablishSession(senderId, recipientId);
            
            // Generate message key
            byte[] messageKey = generateMessageKey();
            
            // Encrypt content
            byte[] encryptedContent = encryptContent(content.getBytes(StandardCharsets.UTF_8), messageKey);
            
            // Encrypt message key with session key
            byte[] encryptedMessageKey = encryptKey(messageKey, sessionKeys.getEncryptionKey());
            
            // Generate signature
            String signature = generateSignature(encryptedContent, senderId);
            
            // Update session state
            sessionKeys.incrementMessageCount();
            
            return EncryptedMessage.builder()
                .encryptedContent(Base64.getEncoder().encodeToString(encryptedContent))
                .encryptedKey(Base64.getEncoder().encodeToString(encryptedMessageKey))
                .signature(signature)
                .messageType(messageType)
                .sessionId(sessionKeys.getSessionId())
                .messageNumber(sessionKeys.getMessageCount())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to encrypt message", e);
            throw new EncryptionException("Failed to encrypt message", e);
        }
    }
    
    public String decryptMessage(String recipientId, EncryptedMessage encryptedMessage) {
        try {
            // Get session
            SessionKeys sessionKeys = getSession(encryptedMessage.getSessionId());
            if (sessionKeys == null) {
                throw new EncryptionException("Session not found");
            }
            
            // Verify signature
            byte[] encryptedContent = Base64.getDecoder().decode(encryptedMessage.getEncryptedContent());
            if (!verifySignature(encryptedContent, encryptedMessage.getSignature(), encryptedMessage.getSenderId())) {
                throw new EncryptionException("Invalid message signature");
            }
            
            // Decrypt message key
            byte[] encryptedMessageKey = Base64.getDecoder().decode(encryptedMessage.getEncryptedKey());
            byte[] messageKey = decryptKey(encryptedMessageKey, sessionKeys.getDecryptionKey());
            
            // Decrypt content
            byte[] decryptedContent = decryptContent(encryptedContent, messageKey);
            
            // Verify message ordering
            if (encryptedMessage.getMessageNumber() <= sessionKeys.getLastReceivedMessageNumber()) {
                log.warn("Potential replay attack detected - message number out of order");
            }
            sessionKeys.updateLastReceivedMessageNumber(encryptedMessage.getMessageNumber());
            
            return new String(decryptedContent, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Failed to decrypt message", e);
            throw new EncryptionException("Failed to decrypt message", e);
        }
    }
    
    public KeyExchangeData initiateKeyExchange(String senderId, String recipientId) {
        try {
            // Generate ephemeral key pair
            Curve25519 curve = Curve25519.getInstance(Curve25519.BEST);
            Curve25519KeyPair ephemeralKeyPair = curve.generateKeyPair();
            
            // Get recipient's key bundle
            UserKeyBundle recipientBundle = keyManagementService.getUserKeyBundle(recipientId);
            
            // Perform X3DH key agreement
            byte[] sharedSecret = performX3DHKeyAgreement(
                senderId,
                ephemeralKeyPair,
                recipientBundle
            );
            
            // Derive session keys
            SessionKeys sessionKeys = deriveSessionKeys(sharedSecret, senderId, recipientId);
            
            // Store session
            String sessionId = generateSessionId(senderId, recipientId);
            sessionKeys.setSessionId(sessionId);
            sessionCache.put(sessionId, sessionKeys);
            
            return KeyExchangeData.builder()
                .sessionId(sessionId)
                .ephemeralPublicKey(Base64.getEncoder().encodeToString(ephemeralKeyPair.getPublicKey()))
                .preKeyId(recipientBundle.getOneTimePreKey().getKeyId())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to initiate key exchange", e);
            throw new EncryptionException("Failed to initiate key exchange", e);
        }
    }
    
    public void completeKeyExchange(String recipientId, KeyExchangeData keyExchangeData) {
        try {
            // Get own key bundle
            UserKeyBundle ownBundle = keyManagementService.getUserKeyBundle(recipientId);
            
            // Retrieve pre-key
            byte[] ephemeralPublicKey = Base64.getDecoder().decode(keyExchangeData.getEphemeralPublicKey());
            
            // Perform X3DH key agreement (recipient side)
            byte[] sharedSecret = performX3DHKeyAgreementRecipient(
                recipientId,
                ephemeralPublicKey,
                keyExchangeData.getPreKeyId(),
                ownBundle
            );
            
            // Derive session keys (reverse order for recipient)
            SessionKeys sessionKeys = deriveSessionKeys(sharedSecret, keyExchangeData.getSenderId(), recipientId);
            sessionKeys.setSessionId(keyExchangeData.getSessionId());
            
            // Store session
            sessionCache.put(keyExchangeData.getSessionId(), sessionKeys);
            
            // Mark pre-key as used
            keyManagementService.markPreKeyAsUsed(recipientId, keyExchangeData.getPreKeyId());
            
        } catch (Exception e) {
            log.error("Failed to complete key exchange", e);
            throw new EncryptionException("Failed to complete key exchange", e);
        }
    }
    
    private SessionKeys getOrEstablishSession(String senderId, String recipientId) {
        String sessionId = generateSessionId(senderId, recipientId);
        SessionKeys session = sessionCache.get(sessionId);
        
        if (session == null || session.isExpired()) {
            // Initiate new key exchange
            KeyExchangeData keyExchange = initiateKeyExchange(senderId, recipientId);
            session = sessionCache.get(keyExchange.getSessionId());
        }
        
        return session;
    }
    
    private SessionKeys getSession(String sessionId) {
        return sessionCache.get(sessionId);
    }
    
    private byte[] performX3DHKeyAgreement(String senderId, Curve25519KeyPair ephemeralKeyPair, 
                                          UserKeyBundle recipientBundle) throws Exception {
        // Simplified X3DH - in production, implement full protocol
        Curve25519 curve = Curve25519.getInstance(Curve25519.BEST);
        
        // DH1: IK_A -> SPK_B
        byte[] identityKey = keyManagementService.getIdentityKey(senderId);
        byte[] signedPreKey = Base64.getDecoder().decode(recipientBundle.getSignedPreKey());
        byte[] dh1 = curve.calculateAgreement(signedPreKey, identityKey);
        
        // DH2: EK_A -> IK_B
        byte[] recipientIdentityKey = Base64.getDecoder().decode(recipientBundle.getIdentityKey());
        byte[] dh2 = curve.calculateAgreement(recipientIdentityKey, ephemeralKeyPair.getPrivateKey());
        
        // DH3: EK_A -> SPK_B
        byte[] dh3 = curve.calculateAgreement(signedPreKey, ephemeralKeyPair.getPrivateKey());
        
        // DH4: EK_A -> OPK_B (if available)
        byte[] dh4 = new byte[32];
        if (recipientBundle.getOneTimePreKey() != null) {
            byte[] oneTimePreKey = Base64.getDecoder().decode(recipientBundle.getOneTimePreKey().getPublicKey());
            dh4 = curve.calculateAgreement(oneTimePreKey, ephemeralKeyPair.getPrivateKey());
        }
        
        // Combine DH outputs
        return combineSecrets(dh1, dh2, dh3, dh4);
    }
    
    private byte[] performX3DHKeyAgreementRecipient(String recipientId, byte[] ephemeralPublicKey,
                                                   Integer preKeyId, UserKeyBundle ownBundle) throws Exception {
        // Implement recipient side of X3DH
        // This is simplified - implement full protocol in production
        Curve25519 curve = Curve25519.getInstance(Curve25519.BEST);
        
        byte[] identityPrivateKey = keyManagementService.getIdentityPrivateKey(recipientId);
        byte[] signedPreKeyPrivate = keyManagementService.getSignedPreKeyPrivate(recipientId);
        
        // Perform DH calculations in reverse
        byte[] dh1 = curve.calculateAgreement(ephemeralPublicKey, signedPreKeyPrivate);
        byte[] dh2 = curve.calculateAgreement(ephemeralPublicKey, identityPrivateKey);
        byte[] dh3 = curve.calculateAgreement(ephemeralPublicKey, signedPreKeyPrivate);
        
        byte[] dh4 = new byte[32];
        if (preKeyId != null) {
            byte[] oneTimePreKeyPrivate = keyManagementService.getPreKeyPrivate(recipientId, preKeyId);
            dh4 = curve.calculateAgreement(ephemeralPublicKey, oneTimePreKeyPrivate);
        }
        
        return combineSecrets(dh1, dh2, dh3, dh4);
    }
    
    private byte[] combineSecrets(byte[]... secrets) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(new byte[32], HMAC_ALGORITHM));
        
        for (byte[] secret : secrets) {
            mac.update(secret);
        }
        
        return mac.doFinal();
    }
    
    private SessionKeys deriveSessionKeys(byte[] sharedSecret, String senderId, String recipientId) throws Exception {
        // Derive encryption and authentication keys using HKDF
        byte[] info = (senderId + "|" + recipientId).getBytes(StandardCharsets.UTF_8);
        
        byte[] encryptionKey = hkdfExpand(sharedSecret, "encryption".getBytes(), 32);
        byte[] decryptionKey = hkdfExpand(sharedSecret, "decryption".getBytes(), 32);
        byte[] authenticationKey = hkdfExpand(sharedSecret, "authentication".getBytes(), 32);
        
        return SessionKeys.builder()
            .encryptionKey(encryptionKey)
            .decryptionKey(decryptionKey)
            .authenticationKey(authenticationKey)
            .createdAt(System.currentTimeMillis())
            .build();
    }
    
    private byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(prk, HMAC_ALGORITHM));
        
        byte[] result = new byte[length];
        byte[] block = new byte[0];
        
        for (int i = 0; i < (length + 31) / 32; i++) {
            mac.update(block);
            mac.update(info);
            mac.update((byte) (i + 1));
            block = mac.doFinal();
            System.arraycopy(block, 0, result, i * 32, Math.min(32, length - i * 32));
        }
        
        return result;
    }
    
    private byte[] generateMessageKey() {
        byte[] key = new byte[32];
        secureRandom.nextBytes(key);
        return key;
    }
    
    private byte[] encryptContent(byte[] content, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        
        byte[] iv = new byte[IV_SIZE];
        secureRandom.nextBytes(iv);
        
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] ciphertext = cipher.doFinal(content);
        
        // Prepend IV to ciphertext
        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
        
        return result;
    }
    
    private byte[] decryptContent(byte[] encryptedContent, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        
        // Extract IV
        byte[] iv = Arrays.copyOfRange(encryptedContent, 0, IV_SIZE);
        byte[] ciphertext = Arrays.copyOfRange(encryptedContent, IV_SIZE, encryptedContent.length);
        
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(ciphertext);
    }
    
    private byte[] encryptKey(byte[] key, byte[] encryptionKey) throws Exception {
        return encryptContent(key, encryptionKey);
    }
    
    private byte[] decryptKey(byte[] encryptedKey, byte[] decryptionKey) throws Exception {
        return decryptContent(encryptedKey, decryptionKey);
    }
    
    private String generateSignature(byte[] content, String senderId) throws Exception {
        // Get signing key
        PrivateKey signingKey = keyManagementService.getSigningKey(senderId);
        
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(signingKey);
        signature.update(content);
        
        return Base64.getEncoder().encodeToString(signature.sign());
    }
    
    private boolean verifySignature(byte[] content, String signatureStr, String senderId) throws Exception {
        // Get verification key
        PublicKey verificationKey = keyManagementService.getVerificationKey(senderId);
        
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(verificationKey);
        signature.update(content);
        
        byte[] signatureBytes = Base64.getDecoder().decode(signatureStr);
        return signature.verify(signatureBytes);
    }
    
    private String generateSessionId(String userId1, String userId2) {
        // Ensure consistent session ID regardless of order
        String combined = userId1.compareTo(userId2) < 0 ? 
            userId1 + "|" + userId2 : userId2 + "|" + userId1;
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    public void rotateSessionKeys(String sessionId) {
        SessionKeys session = sessionCache.get(sessionId);
        if (session != null && session.needsRotation()) {
            // Implement key rotation logic
            log.info("Rotating keys for session: {}", sessionId);
        }
    }
    
    public void cleanupExpiredSessions() {
        sessionCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}

@Data
@Builder
class SessionKeys {
    private String sessionId;
    private byte[] encryptionKey;
    private byte[] decryptionKey;
    private byte[] authenticationKey;
    private long createdAt;
    private int messageCount;
    private int lastReceivedMessageNumber;
    
    private static final long SESSION_LIFETIME = 7 * 24 * 60 * 60 * 1000; // 7 days
    private static final int ROTATION_THRESHOLD = 10000; // Rotate after 10k messages
    
    public boolean isExpired() {
        return System.currentTimeMillis() - createdAt > SESSION_LIFETIME;
    }
    
    public boolean needsRotation() {
        return messageCount > ROTATION_THRESHOLD;
    }
    
    public void incrementMessageCount() {
        this.messageCount++;
    }
    
    public void updateLastReceivedMessageNumber(int messageNumber) {
        this.lastReceivedMessageNumber = messageNumber;
    }
}

enum MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    FILE,
    LOCATION,
    CONTACT,
    PAYMENT
}