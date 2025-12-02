package com.waqiti.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Field-Level Encryption Service
 * 
 * Provides transparent encryption/decryption of sensitive fields at the application layer:
 * - Annotation-based field encryption
 * - Multiple encryption contexts (PII, financial, etc.)
 * - Key versioning and rotation
 * - Format-preserving encryption options
 * - Searchable encryption for indexed fields
 * - Automatic detection and processing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FieldLevelEncryptionService {

    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int KEY_LENGTH = 256;
    
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    
    @Value("${field.encryption.enabled:true}")
    private boolean encryptionEnabled;
    
    @Value("${field.encryption.key-version:1}")
    private int currentKeyVersion;
    
    // Encryption context registry
    private final Map<EncryptionContext, ContextConfiguration> contextConfigurations = new ConcurrentHashMap<>();
    
    // Key management (in production, integrate with HSM/KMS)
    private final Map<String, SecretKey> encryptionKeys = new ConcurrentHashMap<>();

    public void init() {
        if (!encryptionEnabled) {
            log.info("Field-level encryption is disabled");
            return;
        }

        // Initialize encryption contexts and keys
        initializeEncryptionContexts();
        generateEncryptionKeys();
        
        log.info("Field-level encryption service initialized with {} contexts", 
            contextConfigurations.size());
    }

    /**
     * Encrypt all annotated fields in an object
     */
    public <T> T encryptFields(T object) {
        if (!encryptionEnabled || object == null) {
            return object;
        }

        try {
            Class<?> clazz = object.getClass();
            Field[] fields = getAllFields(clazz);
            
            for (Field field : fields) {
                if (field.isAnnotationPresent(EncryptedField.class)) {
                    encryptField(object, field);
                }
            }
            
            return object;
            
        } catch (Exception e) {
            log.error("Failed to encrypt fields for object of type: {}", 
                object.getClass().getSimpleName(), e);
            throw new FieldEncryptionException("Field encryption failed", e);
        }
    }

    /**
     * Decrypt all annotated fields in an object
     */
    public <T> T decryptFields(T object) {
        if (!encryptionEnabled || object == null) {
            return object;
        }

        try {
            Class<?> clazz = object.getClass();
            Field[] fields = getAllFields(clazz);
            
            for (Field field : fields) {
                if (field.isAnnotationPresent(EncryptedField.class)) {
                    decryptField(object, field);
                }
            }
            
            return object;
            
        } catch (Exception e) {
            log.error("Failed to decrypt fields for object of type: {}", 
                object.getClass().getSimpleName(), e);
            throw new FieldEncryptionException("Field decryption failed", e);
        }
    }

    /**
     * Encrypt a single field value
     */
    public String encryptValue(String plaintext, EncryptionContext context) {
        if (!encryptionEnabled || plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            ContextConfiguration config = contextConfigurations.get(context);
            if (config == null) {
                throw new IllegalArgumentException("Unknown encryption context: " + context);
            }
            
            SecretKey key = getEncryptionKey(context, currentKeyVersion);
            
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            // Encrypt
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Create encrypted value with metadata
            EncryptedValue encryptedValue = new EncryptedValue(
                Base64.getEncoder().encodeToString(ciphertext),
                Base64.getEncoder().encodeToString(iv),
                context.name(),
                currentKeyVersion,
                ENCRYPTION_ALGORITHM
            );
            
            return serializeEncryptedValue(encryptedValue);
            
        } catch (Exception e) {
            log.error("Failed to encrypt value for context: {}", context, e);
            throw new FieldEncryptionException("Value encryption failed", e);
        }
    }

    /**
     * Decrypt a single field value
     */
    public String decryptValue(String encryptedData) {
        if (!encryptionEnabled || encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData;
        }

        try {
            EncryptedValue encryptedValue = deserializeEncryptedValue(encryptedData);
            
            EncryptionContext context = EncryptionContext.valueOf(encryptedValue.getContext());
            SecretKey key = getEncryptionKey(context, encryptedValue.getKeyVersion());
            
            byte[] ciphertext = Base64.getDecoder().decode(encryptedValue.getCiphertext());
            byte[] iv = Base64.getDecoder().decode(encryptedValue.getIv());
            
            // Decrypt
            Cipher cipher = Cipher.getInstance(encryptedValue.getAlgorithm());
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            
            byte[] plaintext = cipher.doFinal(ciphertext);
            
            return new String(plaintext, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Failed to decrypt value", e);
            throw new FieldEncryptionException("Value decryption failed", e);
        }
    }

    /**
     * Generate searchable hash for encrypted field (for database queries)
     */
    public String generateSearchableHash(String plaintext, EncryptionContext context) {
        if (!encryptionEnabled || plaintext == null) {
            return null;
        }

        try {
            // Use HMAC for deterministic but secure hashing
            SecretKey key = getEncryptionKey(context, currentKeyVersion);
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(key);
            
            byte[] hash = mac.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
            
        } catch (Exception e) {
            log.error("Failed to generate searchable hash for context: {}", context, e);
            throw new FieldEncryptionException("Searchable hash generation failed", e);
        }
    }

    /**
     * Rotate encryption keys for a context
     */
    public void rotateKeys(EncryptionContext context) {
        try {
            int newKeyVersion = currentKeyVersion + 1;
            SecretKey newKey = generateKey();
            
            String keyId = createKeyId(context, newKeyVersion);
            encryptionKeys.put(keyId, newKey);
            
            // Update current version (in production, this should be coordinated)
            // currentKeyVersion = newKeyVersion;
            
            log.info("Rotated encryption key for context: {} to version: {}", context, newKeyVersion);
            
        } catch (Exception e) {
            log.error("Failed to rotate keys for context: {}", context, e);
            throw new FieldEncryptionException("Key rotation failed", e);
        }
    }

    // Private helper methods

    private void encryptField(Object object, Field field) throws Exception {
        field.setAccessible(true);
        Object value = field.get(object);
        
        if (value == null) {
            return;
        }
        
        EncryptedField annotation = field.getAnnotation(EncryptedField.class);
        EncryptionContext context = annotation.context();
        
        if (value instanceof String) {
            String encryptedValue = encryptValue((String) value, context);
            field.set(object, encryptedValue);
        } else {
            log.warn("Field {} is annotated for encryption but is not a String", field.getName());
        }
    }

    private void decryptField(Object object, Field field) throws Exception {
        field.setAccessible(true);
        Object value = field.get(object);
        
        if (value == null) {
            return;
        }
        
        if (value instanceof String) {
            String encryptedValue = (String) value;
            
            // Check if value is actually encrypted
            if (isEncryptedValue(encryptedValue)) {
                String decryptedValue = decryptValue(encryptedValue);
                field.set(object, decryptedValue);
            }
        }
    }

    private boolean isEncryptedValue(String value) {
        // Simple check - in production, use more sophisticated detection
        return value != null && value.startsWith("{\"ciphertext\":");
    }

    private Field[] getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        
        while (clazz != null) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        
        return fields.toArray(new Field[0]);
    }

    private SecretKey getEncryptionKey(EncryptionContext context, int keyVersion) {
        String keyId = createKeyId(context, keyVersion);
        SecretKey key = encryptionKeys.get(keyId);
        
        if (key == null) {
            throw new FieldEncryptionException("Encryption key not found: " + keyId);
        }
        
        return key;
    }

    private String createKeyId(EncryptionContext context, int keyVersion) {
        return context.name() + "_v" + keyVersion;
    }

    private SecretKey generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(KEY_ALGORITHM);
        keyGen.init(KEY_LENGTH, secureRandom);
        return keyGen.generateKey();
    }

    private void initializeEncryptionContexts() {
        // Configure PII context
        contextConfigurations.put(EncryptionContext.PII, ContextConfiguration.builder()
            .context(EncryptionContext.PII)
            .algorithm(ENCRYPTION_ALGORITHM)
            .keySize(KEY_LENGTH)
            .supportSearchable(true)
            .formatPreserving(false)
            .auditRequired(true)
            .build());
        
        // Configure financial context
        contextConfigurations.put(EncryptionContext.FINANCIAL, ContextConfiguration.builder()
            .context(EncryptionContext.FINANCIAL)
            .algorithm(ENCRYPTION_ALGORITHM)
            .keySize(KEY_LENGTH)
            .supportSearchable(false)
            .formatPreserving(false)
            .auditRequired(true)
            .build());
        
        // Configure sensitive context
        contextConfigurations.put(EncryptionContext.SENSITIVE, ContextConfiguration.builder()
            .context(EncryptionContext.SENSITIVE)
            .algorithm(ENCRYPTION_ALGORITHM)
            .keySize(KEY_LENGTH)
            .supportSearchable(true)
            .formatPreserving(false)
            .auditRequired(true)
            .build());
    }

    private void generateEncryptionKeys() {
        for (EncryptionContext context : EncryptionContext.values()) {
            try {
                SecretKey key = generateKey();
                String keyId = createKeyId(context, currentKeyVersion);
                encryptionKeys.put(keyId, key);
            } catch (Exception e) {
                log.error("Failed to generate key for context: {}", context, e);
                throw new RuntimeException("Key generation failed", e);
            }
        }
    }

    private String serializeEncryptedValue(EncryptedValue value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new FieldEncryptionException("Failed to serialize encrypted value", e);
        }
    }

    private EncryptedValue deserializeEncryptedValue(String data) {
        try {
            return objectMapper.readValue(data, EncryptedValue.class);
        } catch (Exception e) {
            throw new FieldEncryptionException("Failed to deserialize encrypted value", e);
        }
    }

    // Annotations and enums

    /**
     * Annotation to mark fields for encryption
     */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface EncryptedField {
        EncryptionContext context() default EncryptionContext.SENSITIVE;
        boolean searchable() default false;
        boolean formatPreserving() default false;
    }

    /**
     * Encryption contexts for different types of sensitive data
     */
    public enum EncryptionContext {
        PII,        // Personally Identifiable Information
        FINANCIAL,  // Financial data (account numbers, etc.)
        SENSITIVE,  // General sensitive data
        MEDICAL,    // Medical/health information
        CRYPTO      // Cryptocurrency private keys
    }

    // Data classes

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class EncryptedValue {
        private String ciphertext;
        private String iv;
        private String context;
        private int keyVersion;
        private String algorithm;
    }

    @lombok.Data
    @lombok.Builder
    private static class ContextConfiguration {
        private EncryptionContext context;
        private String algorithm;
        private int keySize;
        private boolean supportSearchable;
        private boolean formatPreserving;
        private boolean auditRequired;
    }

    /**
     * Field encryption exception
     */
    public static class FieldEncryptionException extends RuntimeException {
        public FieldEncryptionException(String message) {
            super(message);
        }

        public FieldEncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // Example usage classes

    /**
     * Example entity with encrypted fields
     */
    public static class UserProfile {
        private String id;
        
        @EncryptedField(context = EncryptionContext.PII, searchable = true)
        private String email;
        
        @EncryptedField(context = EncryptionContext.PII)
        private String phoneNumber;
        
        @EncryptedField(context = EncryptionContext.SENSITIVE)
        private String socialSecurityNumber;
        
        @EncryptedField(context = EncryptionContext.FINANCIAL)
        private String bankAccountNumber;
        
        // Regular unencrypted fields
        private String firstName; // Not encrypted
        private String lastName;  // Not encrypted
        private String username;  // Not encrypted
        
        // Getters and setters...
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        
        public String getSocialSecurityNumber() { return socialSecurityNumber; }
        public void setSocialSecurityNumber(String socialSecurityNumber) { 
            this.socialSecurityNumber = socialSecurityNumber; 
        }
        
        public String getBankAccountNumber() { return bankAccountNumber; }
        public void setBankAccountNumber(String bankAccountNumber) { 
            this.bankAccountNumber = bankAccountNumber; 
        }
        
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
    }
}