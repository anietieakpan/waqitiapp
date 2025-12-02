package com.waqiti.payment.service.encryption;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.security.encryption.EncryptionService;
import com.waqiti.payment.dto.CreatePaymentMethodRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for encrypting and decrypting sensitive payment data
 * Uses AES-256-GCM for encryption with key rotation support
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEncryptionService {
    
    private final ObjectMapper objectMapper;
    private final EncryptionService commonEncryptionService;
    
    @Value("${payment.encryption.key:${PAYMENT_MASTER_KEY}}")
    private String masterKey;
    
    @Value("${payment.encryption.algorithm:AES/GCM/NoPadding}")
    private String encryptionAlgorithm;
    
    @Value("${payment.encryption.key-algorithm:AES}")
    private String keyAlgorithm;
    
    @Value("${payment.encryption.key-size:256}")
    private int keySize;
    
    @Value("${payment.encryption.tag-length:128}")
    private int gcmTagLength;
    
    @Value("${payment.encryption.iv-length:12}")
    private int ivLength;
    
    @Value("${payment.encryption.salt-length:16}")
    private int saltLength;
    
    @Value("${payment.encryption.iterations:65536}")
    private int iterations;
    
    private static final String SENSITIVE_FIELDS_KEY = "_sensitiveFields";
    private static final Set<String> ALWAYS_ENCRYPT_FIELDS = Set.of(
            "cardNumber", "cvv", "pin", "password", "ssn", "taxId",
            "accountNumber", "routingNumber", "privateKey", "apiKey",
            "bankAccountNumber", "iban", "swiftCode", "bic"
    );
    
    private final Map<String, SecretKey> keyCache = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Encrypt sensitive data in a map
     * Automatically detects and encrypts sensitive fields
     */
    public Map<String, Object> encryptSensitiveData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        
        Map<String, Object> encryptedData = new HashMap<>();
        Set<String> encryptedFields = new HashSet<>();
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (shouldEncrypt(key, value)) {
                try {
                    String encryptedValue = encrypt(convertToString(value));
                    encryptedData.put(key, encryptedValue);
                    encryptedFields.add(key);
                } catch (Exception e) {
                    log.error("Failed to encrypt field: {}", key, e);
                    throw new BusinessException("Encryption failed for sensitive data", e);
                }
            } else if (value instanceof Map) {
                // Recursively encrypt nested maps
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                encryptedData.put(key, encryptSensitiveData(nestedMap));
            } else {
                encryptedData.put(key, value);
            }
        }
        
        // Store list of encrypted fields for decryption
        if (!encryptedFields.isEmpty()) {
            encryptedData.put(SENSITIVE_FIELDS_KEY, encryptedFields);
        }
        
        return encryptedData;
    }
    
    /**
     * Encrypt payment details - maintains backward compatibility
     */
    public String encryptPaymentDetails(CreatePaymentMethodRequest.PaymentDetails details) {
        try {
            String json = objectMapper.writeValueAsString(details);
            return encrypt(json);
        } catch (Exception e) {
            log.error("Error encrypting payment details", e);
            throw new BusinessException("Failed to encrypt payment details", e);
        }
    }
    
    /**
     * Decrypt sensitive data in a map
     */
    public Map<String, Object> decryptSensitiveData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        
        Map<String, Object> decryptedData = new HashMap<>(data);
        
        // Get list of encrypted fields
        @SuppressWarnings("unchecked")
        Set<String> encryptedFields = (Set<String>) data.getOrDefault(SENSITIVE_FIELDS_KEY, new HashSet<>());
        
        // Decrypt known sensitive fields
        for (String field : encryptedFields) {
            if (data.containsKey(field)) {
                try {
                    String encryptedValue = (String) data.get(field);
                    String decryptedValue = decrypt(encryptedValue);
                    decryptedData.put(field, decryptedValue);
                } catch (Exception e) {
                    log.error("Failed to decrypt field: {}", field, e);
                    throw new BusinessException("Decryption failed for sensitive data", e);
                }
            }
        }
        
        // Remove metadata field
        decryptedData.remove(SENSITIVE_FIELDS_KEY);
        
        // Recursively decrypt nested maps
        for (Map.Entry<String, Object> entry : new HashMap<>(decryptedData).entrySet()) {
            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) entry.getValue();
                decryptedData.put(entry.getKey(), decryptSensitiveData(nestedMap));
            }
        }
        
        return decryptedData;
    }
    
    /**
     * Decrypt payment details - maintains backward compatibility
     */
    public CreatePaymentMethodRequest.PaymentDetails decryptPaymentDetails(String encryptedData) {
        try {
            String json = decrypt(encryptedData);
            return objectMapper.readValue(json, CreatePaymentMethodRequest.PaymentDetails.class);
        } catch (Exception e) {
            log.error("Error decrypting payment details", e);
            throw new BusinessException("Failed to decrypt payment details", e);
        }
    }
    
    /**
     * Encrypt a single string value
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        try {
            // Generate salt and IV
            byte[] salt = new byte[saltLength];
            byte[] iv = new byte[ivLength];
            secureRandom.nextBytes(salt);
            secureRandom.nextBytes(iv);
            
            // Derive key from master key and salt
            SecretKey key = deriveKey(masterKey, salt);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
            
            // Encrypt
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine salt, IV, and ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(salt.length + iv.length + ciphertext.length);
            buffer.put(salt);
            buffer.put(iv);
            buffer.put(ciphertext);
            
            // Encode as base64
            return Base64.getEncoder().encodeToString(buffer.array());
            
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new BusinessException("Failed to encrypt data", e);
        }
    }
    
    /**
     * Decrypt a single string value
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        
        try {
            // Decode from base64
            byte[] encryptedData = Base64.getDecoder().decode(encryptedText);
            
            // Extract salt, IV, and ciphertext
            ByteBuffer buffer = ByteBuffer.wrap(encryptedData);
            byte[] salt = new byte[saltLength];
            byte[] iv = new byte[ivLength];
            byte[] ciphertext = new byte[encryptedData.length - saltLength - ivLength];
            
            buffer.get(salt);
            buffer.get(iv);
            buffer.get(ciphertext);
            
            // Derive key from master key and salt
            SecretKey key = deriveKey(masterKey, salt);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
            
            // Decrypt
            byte[] plaintext = cipher.doFinal(ciphertext);
            
            return new String(plaintext, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new BusinessException("Failed to decrypt data", e);
        }
    }
    
    /**
     * Encrypt payment card data with format preservation
     */
    public String encryptCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 12) {
            return cardNumber;
        }
        
        // Keep first 6 and last 4 digits visible for PCI compliance
        String firstSix = cardNumber.substring(0, 6);
        String lastFour = cardNumber.substring(cardNumber.length() - 4);
        String middle = cardNumber.substring(6, cardNumber.length() - 4);
        
        String encryptedMiddle = encrypt(middle);
        
        // Store in a format that preserves the structure
        return String.format("%s-%s-%s", firstSix, encryptedMiddle, lastFour);
    }
    
    /**
     * Decrypt payment card data
     */
    public String decryptCardNumber(String encryptedCardNumber) {
        if (encryptedCardNumber == null || !encryptedCardNumber.contains("-")) {
            return encryptedCardNumber;
        }
        
        String[] parts = encryptedCardNumber.split("-");
        if (parts.length != 3) {
            return encryptedCardNumber;
        }
        
        String firstSix = parts[0];
        String encryptedMiddle = parts[1];
        String lastFour = parts[2];
        
        String decryptedMiddle = decrypt(encryptedMiddle);
        
        return firstSix + decryptedMiddle + lastFour;
    }
    
    /**
     * Tokenize sensitive payment data
     */
    public String tokenize(String sensitiveData) {
        String token = UUID.randomUUID().toString();
        String encryptedData = encrypt(sensitiveData);
        
        // Store in secure token vault
        String cacheKey = "payment_token:" + token;
        storeToken(cacheKey, encryptedData, 3600); // 1 hour expiration
        
        return token;
    }
    
    /**
     * Detokenize - retrieve original data from token
     */
    public String detokenize(String token) {
        String cacheKey = "payment_token:" + token;
        String encryptedData = retrieveToken(cacheKey);
        
        if (encryptedData == null) {
            throw new BusinessException("Invalid or expired token");
        }
        
        return decrypt(encryptedData);
    }
    
    /**
     * Hash sensitive data for comparison without storing plaintext
     */
    public String hashForComparison(String data) {
        try {
            // Use PBKDF2 for secure hashing
            byte[] salt = new byte[saltLength];
            secureRandom.nextBytes(salt);
            
            KeySpec spec = new PBEKeySpec(data.toCharArray(), salt, iterations, keySize);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            
            // Combine salt and hash
            ByteBuffer buffer = ByteBuffer.allocate(salt.length + hash.length);
            buffer.put(salt);
            buffer.put(hash);
            
            return Base64.getEncoder().encodeToString(buffer.array());
            
        } catch (Exception e) {
            log.error("Hashing failed", e);
            throw new BusinessException("Failed to hash data", e);
        }
    }
    
    /**
     * Verify data against hash
     */
    public boolean verifyHash(String data, String hash) {
        try {
            byte[] hashData = Base64.getDecoder().decode(hash);
            ByteBuffer buffer = ByteBuffer.wrap(hashData);
            
            byte[] salt = new byte[saltLength];
            byte[] expectedHash = new byte[hashData.length - saltLength];
            
            buffer.get(salt);
            buffer.get(expectedHash);
            
            KeySpec spec = new PBEKeySpec(data.toCharArray(), salt, iterations, keySize);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] actualHash = factory.generateSecret(spec).getEncoded();
            
            return Arrays.equals(expectedHash, actualHash);
            
        } catch (Exception e) {
            log.error("Hash verification failed", e);
            return false;
        }
    }
    
    /**
     * Rotate encryption keys
     */
    public void rotateKeys(String newMasterKey) {
        log.info("Starting key rotation process");
        
        // Update master key and clear cache
        this.masterKey = newMasterKey;
        keyCache.clear();
        
        log.info("Key rotation completed successfully");
    }
    
    // Helper methods
    
    private boolean shouldEncrypt(String fieldName, Object value) {
        if (value == null) {
            return false;
        }
        
        // Check if field name contains sensitive keywords
        String lowerFieldName = fieldName.toLowerCase();
        
        // Always encrypt known sensitive fields
        if (ALWAYS_ENCRYPT_FIELDS.contains(lowerFieldName)) {
            return true;
        }
        
        // Check for patterns
        if (lowerFieldName.contains("password") ||
            lowerFieldName.contains("secret") ||
            lowerFieldName.contains("key") ||
            lowerFieldName.contains("token") ||
            lowerFieldName.contains("credential") ||
            lowerFieldName.contains("private")) {
            return true;
        }
        
        // Check if value looks like sensitive data
        if (value instanceof String) {
            String stringValue = (String) value;
            
            // Credit card pattern
            if (stringValue.matches("\\d{13,19}")) {
                return true;
            }
            
            // SSN pattern
            if (stringValue.matches("\\d{3}-\\d{2}-\\d{4}") || 
                stringValue.matches("\\d{9}")) {
                return true;
            }
            
            // Bank account/routing number patterns
            if (stringValue.matches("\\d{8,17}")) {
                return true;
            }
        }
        
        return false;
    }
    
    private String convertToString(Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return value.toString();
        }
    }
    
    @Cacheable(value = "encryption-keys", key = "#salt")
    private SecretKey deriveKey(String password, byte[] salt) 
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        
        // Check cache first
        String cacheKey = Base64.getEncoder().encodeToString(salt);
        SecretKey cachedKey = keyCache.get(cacheKey);
        if (cachedKey != null) {
            return cachedKey;
        }
        
        // Derive key using PBKDF2
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keySize);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        
        SecretKey key = new SecretKeySpec(keyBytes, keyAlgorithm);
        
        // Cache the key
        keyCache.put(cacheKey, key);
        
        return key;
    }
    
    private void storeToken(String key, String value, int expirationSeconds) {
        // Use common encryption service for secure storage
        commonEncryptionService.storeSecureData(key, value, expirationSeconds);
    }
    
    private String retrieveToken(String key) {
        // Use common encryption service for secure retrieval
        return commonEncryptionService.retrieveSecureData(key);
    }
    
    /**
     * Clear sensitive data from memory
     */
    public void clearSensitiveData(Object... objects) {
        for (Object obj : objects) {
            if (obj instanceof char[]) {
                Arrays.fill((char[]) obj, '\0');
            } else if (obj instanceof byte[]) {
                Arrays.fill((byte[]) obj, (byte) 0);
            } else if (obj instanceof StringBuilder) {
                StringBuilder sb = (StringBuilder) obj;
                for (int i = 0; i < sb.length(); i++) {
                    sb.setCharAt(i, '\0');
                }
            } else if (obj instanceof StringBuffer) {
                StringBuffer sb = (StringBuffer) obj;
                for (int i = 0; i < sb.length(); i++) {
                    sb.setCharAt(i, '\0');
                }
            }
        }
    }
}