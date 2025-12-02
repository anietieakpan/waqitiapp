package com.waqiti.messaging.service;

import com.waqiti.messaging.domain.PreKey;
import com.waqiti.messaging.domain.UserKeyBundle;
import com.waqiti.messaging.exception.KeyManagementException;
import com.waqiti.messaging.repository.PreKeyRepository;
import com.waqiti.messaging.repository.UserKeyBundleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.whispersystems.curve25519.Curve25519;
import org.whispersystems.curve25519.Curve25519KeyPair;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.SecretKeyFactory;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class KeyManagementService {
    
    private static final int PRE_KEY_BATCH_SIZE = 100;
    private static final int MIN_PRE_KEY_COUNT = 10;
    private static final int SIGNED_PRE_KEY_ROTATION_DAYS = 30;
    
    private final UserKeyBundleRepository keyBundleRepository;
    private final PreKeyRepository preKeyRepository;
    private final SecureKeyStorage secureKeyStorage;
    private final ConcurrentMap<String, KeyPair> signingKeyCache = new ConcurrentHashMap<>();
    
    private final Curve25519 curve25519 = Curve25519.getInstance(Curve25519.BEST);
    
    @Transactional
    public void initializeUserKeys(String userId, String deviceId) {
        try {
            // Check if keys already exist
            if (keyBundleRepository.findByUserId(userId).isPresent()) {
                log.info("Keys already exist for user: {}", userId);
                return;
            }
            
            // Generate identity key pair
            Curve25519KeyPair identityKeyPair = curve25519.generateKeyPair();
            
            // Generate signed pre-key
            Curve25519KeyPair signedPreKeyPair = curve25519.generateKeyPair();
            byte[] signedPreKeySignature = signPreKey(signedPreKeyPair.getPublicKey(), identityKeyPair.getPrivateKey());
            
            // Generate registration ID
            SecureRandom random = new SecureRandom();
            int registrationId = random.nextInt(Integer.MAX_VALUE);
            
            // Create key bundle
            UserKeyBundle keyBundle = UserKeyBundle.builder()
                .userId(userId)
                .deviceId(deviceId)
                .registrationId(registrationId)
                .identityKey(Base64.getEncoder().encodeToString(identityKeyPair.getPublicKey()))
                .signedPreKeyId(1)
                .signedPreKey(Base64.getEncoder().encodeToString(signedPreKeyPair.getPublicKey()))
                .signedPreKeySignature(Base64.getEncoder().encodeToString(signedPreKeySignature))
                .signedPreKeyCreatedAt(LocalDateTime.now())
                .build();
            
            // Store private keys securely (in production, use HSM or secure key storage)
            storePrivateKey(userId, "identity", identityKeyPair.getPrivateKey());
            storePrivateKey(userId, "signed_pre_key", signedPreKeyPair.getPrivateKey());
            
            // Generate initial batch of one-time pre-keys
            generatePreKeys(keyBundle, PRE_KEY_BATCH_SIZE);
            
            // Save key bundle
            keyBundleRepository.save(keyBundle);
            
            // Generate signing key pair for message signatures
            generateSigningKeyPair(userId);
            
            log.info("Successfully initialized keys for user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to initialize user keys", e);
            throw new KeyManagementException("Failed to initialize user keys", e);
        }
    }
    
    @Transactional
    public void generatePreKeys(UserKeyBundle keyBundle, int count) {
        try {
            List<PreKey> preKeys = new ArrayList<>();
            int startId = getNextPreKeyId(keyBundle);
            
            for (int i = 0; i < count; i++) {
                Curve25519KeyPair preKeyPair = curve25519.generateKeyPair();
                
                PreKey preKey = PreKey.builder()
                    .keyId(startId + i)
                    .publicKey(Base64.getEncoder().encodeToString(preKeyPair.getPublicKey()))
                    .privateKey(Base64.getEncoder().encodeToString(preKeyPair.getPrivateKey()))
                    .keyBundle(keyBundle)
                    .used(false)
                    .build();
                
                preKeys.add(preKey);
            }
            
            preKeyRepository.saveAll(preKeys);
            log.info("Generated {} pre-keys for user: {}", count, keyBundle.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to generate pre-keys", e);
            throw new KeyManagementException("Failed to generate pre-keys", e);
        }
    }
    
    @Cacheable(value = "userKeyBundles", key = "#userId")
    public UserKeyBundle getUserKeyBundle(String userId) {
        return keyBundleRepository.findByUserId(userId)
            .orElseThrow(() -> new KeyManagementException("Key bundle not found for user: " + userId));
    }
    
    @Transactional
    public void markPreKeyAsUsed(String userId, Integer preKeyId) {
        UserKeyBundle keyBundle = getUserKeyBundle(userId);
        PreKey preKey = preKeyRepository.findByKeyBundleAndKeyId(keyBundle, preKeyId)
            .orElseThrow(() -> new KeyManagementException("Pre-key not found"));
        
        preKey.markAsUsed();
        preKeyRepository.save(preKey);
        
        // Check if we need to refill pre-keys
        if (keyBundle.needsPreKeyRefill()) {
            generatePreKeys(keyBundle, PRE_KEY_BATCH_SIZE);
        }
    }
    
    @Transactional
    @Scheduled(cron = "0 0 0 * * ?") // Daily at midnight
    public void rotateSignedPreKeys() {
        List<UserKeyBundle> bundles = keyBundleRepository.findBySignedPreKeyCreatedAtBefore(
            LocalDateTime.now().minusDays(SIGNED_PRE_KEY_ROTATION_DAYS)
        );
        
        for (UserKeyBundle bundle : bundles) {
            try {
                rotateSignedPreKey(bundle);
            } catch (Exception e) {
                log.error("Failed to rotate signed pre-key for user: {}", bundle.getUserId(), e);
            }
        }
    }
    
    @CacheEvict(value = "userKeyBundles", key = "#keyBundle.userId")
    private void rotateSignedPreKey(UserKeyBundle keyBundle) {
        try {
            // Generate new signed pre-key
            Curve25519KeyPair signedPreKeyPair = curve25519.generateKeyPair();
            byte[] identityPrivateKey = getIdentityPrivateKey(keyBundle.getUserId());
            byte[] signedPreKeySignature = signPreKey(signedPreKeyPair.getPublicKey(), identityPrivateKey);
            
            // Update key bundle
            keyBundle.setSignedPreKeyId(keyBundle.getSignedPreKeyId() + 1);
            keyBundle.setSignedPreKey(Base64.getEncoder().encodeToString(signedPreKeyPair.getPublicKey()));
            keyBundle.setSignedPreKeySignature(Base64.getEncoder().encodeToString(signedPreKeySignature));
            keyBundle.setSignedPreKeyCreatedAt(LocalDateTime.now());
            
            // Store new private key
            storePrivateKey(keyBundle.getUserId(), "signed_pre_key", signedPreKeyPair.getPrivateKey());
            
            keyBundleRepository.save(keyBundle);
            log.info("Rotated signed pre-key for user: {}", keyBundle.getUserId());
            
        } catch (Exception e) {
            throw new KeyManagementException("Failed to rotate signed pre-key", e);
        }
    }
    
    @Scheduled(fixedDelay = 3600000) // Every hour
    public void cleanupUsedPreKeys() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        int deleted = preKeyRepository.deleteByUsedTrueAndUsedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} used pre-keys", deleted);
        }
    }
    
    private byte[] signPreKey(byte[] preKeyPublic, byte[] identityPrivateKey) throws Exception {
        // Simple signature using identity key
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(preKeyPublic);
        byte[] hash = digest.digest();
        
        // In production, use proper signature algorithm
        return curve25519.calculateSignature(identityPrivateKey, hash);
    }
    
    private void generateSigningKeyPair(String userId) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        
        signingKeyCache.put(userId, keyPair);
        
        // Store keys securely
        storePrivateKey(userId, "signing", keyPair.getPrivate().getEncoded());
        storePublicKey(userId, "verification", keyPair.getPublic().getEncoded());
    }
    
    public byte[] getIdentityKey(String userId) {
        UserKeyBundle bundle = getUserKeyBundle(userId);
        return Base64.getDecoder().decode(bundle.getIdentityKey());
    }
    
    public byte[] getIdentityPrivateKey(String userId) {
        return retrievePrivateKey(userId, "identity");
    }
    
    public byte[] getSignedPreKeyPrivate(String userId) {
        return retrievePrivateKey(userId, "signed_pre_key");
    }
    
    public byte[] getPreKeyPrivate(String userId, Integer preKeyId) {
        UserKeyBundle bundle = getUserKeyBundle(userId);
        PreKey preKey = preKeyRepository.findByKeyBundleAndKeyId(bundle, preKeyId)
            .orElseThrow(() -> new KeyManagementException("Pre-key not found"));
        
        return Base64.getDecoder().decode(preKey.getPrivateKey());
    }
    
    public PrivateKey getSigningKey(String userId) throws Exception {
        KeyPair keyPair = signingKeyCache.get(userId);
        if (keyPair == null) {
            // Load from storage
            byte[] privateKeyBytes = retrievePrivateKey(userId, "signing");
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
            
            // Reconstruct public key
            byte[] publicKeyBytes = retrievePublicKey(userId, "verification");
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            
            keyPair = new KeyPair(publicKey, privateKey);
            signingKeyCache.put(userId, keyPair);
        }
        
        return keyPair.getPrivate();
    }
    
    public PublicKey getVerificationKey(String userId) throws Exception {
        KeyPair keyPair = signingKeyCache.get(userId);
        if (keyPair == null) {
            byte[] publicKeyBytes = retrievePublicKey(userId, "verification");
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        }
        
        return keyPair.getPublic();
    }
    
    private void storePrivateKey(String userId, String keyType, byte[] key) {
        try {
            String storageKey = String.format("%s:%s:private", userId, keyType);
            
            // Generate unique salt for this key
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            
            // Derive encryption key from master key and salt
            SecretKey encryptionKey = deriveEncryptionKey(salt);
            
            // Encrypt the private key
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey);
            byte[] iv = cipher.getIV();
            byte[] encryptedKey = cipher.doFinal(key);
            
            // Store salt + iv + encrypted key in secure storage
            byte[] storedData = new byte[salt.length + iv.length + encryptedKey.length];
            System.arraycopy(salt, 0, storedData, 0, salt.length);
            System.arraycopy(iv, 0, storedData, salt.length, iv.length);
            System.arraycopy(encryptedKey, 0, storedData, salt.length + iv.length, encryptedKey.length);
            
            // Store in secure key storage (Redis with encryption or dedicated HSM)
            secureKeyStorage.store(storageKey, Base64.getEncoder().encodeToString(storedData));
            
            log.debug("Securely stored private key for user: {} type: {}", userId, keyType);
        } catch (Exception e) {
            log.error("Failed to store private key", e);
            throw new KeyManagementException("Failed to store private key securely", e);
        }
    }
    
    private void storePublicKey(String userId, String keyType, byte[] key) {
        try {
            String storageKey = String.format("%s:%s:public", userId, keyType);
            
            // Public keys don't need encryption but we still store them securely
            String encodedKey = Base64.getEncoder().encodeToString(key);
            secureKeyStorage.store(storageKey, encodedKey);
            
            log.debug("Stored public key for user: {} type: {}", userId, keyType);
        } catch (Exception e) {
            log.error("Failed to store public key", e);
            throw new KeyManagementException("Failed to store public key", e);
        }
    }
    
    private byte[] retrievePrivateKey(String userId, String keyType) {
        try {
            String storageKey = String.format("%s:%s:private", userId, keyType);
            
            // Retrieve encrypted data from secure storage
            String encodedData = secureKeyStorage.retrieve(storageKey);
            if (encodedData == null) {
                throw new KeyManagementException("Private key not found: " + storageKey);
            }
            
            byte[] storedData = Base64.getDecoder().decode(encodedData);
            
            // Extract salt, IV, and encrypted key
            byte[] salt = new byte[16];
            byte[] iv = new byte[12]; // GCM IV size
            byte[] encryptedKey = new byte[storedData.length - salt.length - iv.length];
            
            System.arraycopy(storedData, 0, salt, 0, salt.length);
            System.arraycopy(storedData, salt.length, iv, 0, iv.length);
            System.arraycopy(storedData, salt.length + iv.length, encryptedKey, 0, encryptedKey.length);
            
            // Derive decryption key
            SecretKey decryptionKey = deriveEncryptionKey(salt);
            
            // Decrypt the private key
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, decryptionKey, gcmSpec);
            
            return cipher.doFinal(encryptedKey);
            
        } catch (Exception e) {
            log.error("Failed to retrieve private key", e);
            throw new KeyManagementException("Failed to retrieve private key securely", e);
        }
    }
    
    private byte[] retrievePublicKey(String userId, String keyType) {
        try {
            String storageKey = String.format("%s:%s:public", userId, keyType);
            
            String encodedKey = secureKeyStorage.retrieve(storageKey);
            if (encodedKey == null) {
                throw new KeyManagementException("Public key not found: " + storageKey);
            }
            
            return Base64.getDecoder().decode(encodedKey);
            
        } catch (Exception e) {
            log.error("Failed to retrieve public key", e);
            throw new KeyManagementException("Failed to retrieve public key", e);
        }
    }
    
    private int getNextPreKeyId(UserKeyBundle keyBundle) {
        return preKeyRepository.findMaxKeyIdByKeyBundle(keyBundle)
            .map(maxId -> maxId + 1)
            .orElse(100); // Start from 100 for one-time pre-keys
    }
    
    public boolean hasKeys(String userId) {
        return keyBundleRepository.existsByUserId(userId);
    }
    
    private SecretKey deriveEncryptionKey(byte[] salt) throws Exception {
        // In production, use a proper master key from HSM or secure configuration
        String masterPassword = getMasterPassword();
        
        PBEKeySpec spec = new PBEKeySpec(masterPassword.toCharArray(), salt, 100000, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        
        return new SecretKeySpec(keyBytes, "AES");
    }
    
    private String getMasterPassword() {
        // In production, retrieve from secure configuration or HSM
        return System.getenv("KEY_ENCRYPTION_PASSWORD");
    }
}