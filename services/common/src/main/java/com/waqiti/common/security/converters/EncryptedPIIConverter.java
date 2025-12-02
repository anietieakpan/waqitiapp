package com.waqiti.common.security.converters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.security.EnhancedFieldEncryptionService;
import com.waqiti.common.security.EnhancedFieldEncryptionService.DataClassification;
import com.waqiti.common.security.EnhancedFieldEncryptionService.EncryptedData;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Production-ready JPA Converter for automatic encryption/decryption of PII fields
 * 
 * CRITICAL SECURITY COMPONENT for GDPR Compliance:
 * - Automatic field-level encryption for PII data
 * - Format validation before encryption
 * - Caching for performance optimization
 * - Audit logging for compliance
 * - Automatic data masking in logs
 * - Support for different PII types (email, phone, SSN, etc.)
 * 
 * Usage:
 * @Column(name = "email")
 * @Convert(converter = EncryptedPIIConverter.class)
 * private String email;
 */
@Component
@Converter(autoApply = false)
@Slf4j
public class EncryptedPIIConverter implements AttributeConverter<String, String> {

    private static EnhancedFieldEncryptionService encryptionService;
    private static ObjectMapper objectMapper;
    private static MeterRegistry meterRegistry;
    private static CacheManager cacheManager;
    
    // Performance optimization: cache decrypted values for short period
    private static final ConcurrentHashMap<String, CachedValue> decryptCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final int MAX_CACHE_SIZE = 1000;
    
    // PII format validators
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^\\+?[1-9]\\d{1,14}$"
    );
    private static final Pattern SSN_PATTERN = Pattern.compile(
        "^\\d{3}-\\d{2}-\\d{4}$"
    );
    
    // Metrics
    private static Counter encryptionCounter;
    private static Counter decryptionCounter;
    private static Counter cacheHitCounter;
    private static Counter cacheMissCounter;
    private static Timer encryptionTimer;
    private static Timer decryptionTimer;
    
    // Encryption marker to identify encrypted data
    private static final String ENCRYPTION_MARKER = "ENC_PII_V2:";
    private static final String LEGACY_MARKER = "{\"ct\":";
    
    @Autowired
    public void setEncryptionService(EnhancedFieldEncryptionService service) {
        EncryptedPIIConverter.encryptionService = service;
    }
    
    @Autowired
    public void setObjectMapper(ObjectMapper mapper) {
        EncryptedPIIConverter.objectMapper = mapper;
    }
    
    @Autowired
    public void setMeterRegistry(MeterRegistry registry) {
        EncryptedPIIConverter.meterRegistry = registry;
        initializeMetrics();
    }
    
    @Autowired(required = false)
    public void setCacheManager(CacheManager manager) {
        EncryptedPIIConverter.cacheManager = manager;
    }
    
    @PostConstruct
    public void init() {
        if (meterRegistry != null) {
            initializeMetrics();
        }
        
        // Schedule cache cleanup
        Thread cleanupThread = new Thread(this::cleanupCache);
        cleanupThread.setDaemon(true);
        cleanupThread.start();
        
        log.info("EncryptedPIIConverter initialized with caching and metrics");
    }
    
    private static void initializeMetrics() {
        if (meterRegistry == null) return;
        
        encryptionCounter = Counter.builder("pii.converter.encryptions")
            .description("Number of PII encryptions")
            .register(meterRegistry);
            
        decryptionCounter = Counter.builder("pii.converter.decryptions")
            .description("Number of PII decryptions")
            .register(meterRegistry);
            
        cacheHitCounter = Counter.builder("pii.converter.cache.hits")
            .description("Number of cache hits")
            .register(meterRegistry);
            
        cacheMissCounter = Counter.builder("pii.converter.cache.misses")
            .description("Number of cache misses")
            .register(meterRegistry);
            
        encryptionTimer = Timer.builder("pii.converter.encryption.time")
            .description("Time taken for PII encryption")
            .register(meterRegistry);
            
        decryptionTimer = Timer.builder("pii.converter.decryption.time")
            .description("Time taken for PII decryption")
            .register(meterRegistry);
    }
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        
        // Check if already encrypted
        if (isEncrypted(attribute)) {
            log.warn("SECURITY: Attempted to double-encrypt PII data");
            return attribute;
        }
        
        Timer.Sample sample = Timer.start();
        
        try {
            // Validate PII format
            PiiType piiType = detectPiiType(attribute);
            validatePiiFormat(attribute, piiType);
            
            // Check encryption service availability
            if (encryptionService == null) {
                log.error("SECURITY CRITICAL: Encryption service not available, storing PII in plain text is forbidden");
                throw new SecurityException("Cannot store unencrypted PII data");
            }
            
            // Encrypt the PII data
            EncryptedData encryptedData = encryptionService.encryptSensitiveData(
                attribute, DataClassification.PII
            );
            
            // Generate blind index for searchability
            String blindIndex = encryptionService.generateBlindIndex(attribute.toLowerCase(), DataClassification.PII);
            if (blindIndex != null) {
                encryptedData = EncryptedData.builder()
                    .ciphertext(encryptedData.getCiphertext())
                    .keyVersion(encryptedData.getKeyVersion())
                    .classification(encryptedData.getClassification())
                    .algorithm(encryptedData.getAlgorithm())
                    .encryptedAt(encryptedData.getEncryptedAt())
                    .blindIndex(blindIndex)
                    .build();
            }
            
            // Create structured encrypted format
            EncryptedPiiData piiData = new EncryptedPiiData();
            piiData.setCiphertext(encryptedData.getCiphertext());
            piiData.setKeyVersion(encryptedData.getKeyVersion());
            piiData.setClassification(encryptedData.getClassification().name());
            piiData.setAlgorithm(encryptedData.getAlgorithm());
            piiData.setEncryptedAt(encryptedData.getEncryptedAt());
            piiData.setBlindIndex(blindIndex);
            piiData.setPiiType(piiType.name());
            piiData.setMaskedValue(maskPiiValue(attribute, piiType));
            
            // Serialize to JSON
            String jsonData = objectMapper.writeValueAsString(piiData);
            String result = ENCRYPTION_MARKER + Base64.getEncoder().encodeToString(
                jsonData.getBytes(StandardCharsets.UTF_8)
            );
            
            // Update metrics
            if (encryptionCounter != null) encryptionCounter.increment();
            if (sample != null && encryptionTimer != null) sample.stop(encryptionTimer);
            
            // Audit log (with masked value)
            log.info("GDPR AUDIT: Encrypted PII field type={}, masked={}", 
                piiType, piiData.getMaskedValue());
            
            return result;
            
        } catch (Exception e) {
            log.error("SECURITY CRITICAL: Failed to encrypt PII data", e);
            
            // In production, we must never store unencrypted PII
            throw new SecurityException("PII encryption failed - cannot store unencrypted PII", e);
        }
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        
        // Check if data is encrypted
        if (!isEncrypted(dbData)) {
            // Log security violation - PII should always be encrypted
            log.error("SECURITY VIOLATION: Unencrypted PII detected in database!");
            
            // In production, we should not return unencrypted PII
            // Return masked value instead
            return "***UNENCRYPTED_PII_BLOCKED***";
        }
        
        // Check cache first
        String cacheKey = generateCacheKey(dbData);
        CachedValue cached = decryptCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            if (cacheHitCounter != null) cacheHitCounter.increment();
            return cached.getValue();
        }
        
        if (cacheMissCounter != null) cacheMissCounter.increment();
        Timer.Sample sample = Timer.start();
        
        try {
            // Parse encrypted data
            EncryptedPiiData piiData = parseEncryptedData(dbData);
            
            // Check encryption service availability
            if (encryptionService == null) {
                log.error("SECURITY: Encryption service not available for decryption");
                // Return masked value for safety
                return piiData.getMaskedValue() != null ? piiData.getMaskedValue() : "***MASKED***";
            }
            
            // Build EncryptedData object
            EncryptedData encryptedData = EncryptedData.builder()
                .ciphertext(piiData.getCiphertext())
                .keyVersion(piiData.getKeyVersion())
                .classification(DataClassification.valueOf(piiData.getClassification()))
                .algorithm(piiData.getAlgorithm())
                .encryptedAt(piiData.getEncryptedAt())
                .blindIndex(piiData.getBlindIndex())
                .build();
            
            // Decrypt
            String decryptedValue = encryptionService.decryptSensitiveData(encryptedData);
            
            // Validate decrypted data matches expected PII type
            if (piiData.getPiiType() != null) {
                PiiType expectedType = PiiType.valueOf(piiData.getPiiType());
                validatePiiFormat(decryptedValue, expectedType);
            }
            
            // Cache the result
            if (decryptCache.size() < MAX_CACHE_SIZE) {
                decryptCache.put(cacheKey, new CachedValue(decryptedValue));
            }
            
            // Update metrics
            if (decryptionCounter != null) decryptionCounter.increment();
            if (sample != null && decryptionTimer != null) sample.stop(decryptionTimer);
            
            // Audit log (with masked value only)
            log.debug("GDPR AUDIT: Decrypted PII field type={}, masked={}", 
                piiData.getPiiType(), piiData.getMaskedValue());
            
            return decryptedValue;
            
        } catch (Exception e) {
            log.error("SECURITY: Failed to decrypt PII data, returning masked value", e);
            
            // For security, return masked value on decryption failure
            try {
                EncryptedPiiData piiData = parseEncryptedData(dbData);
                return piiData.getMaskedValue() != null ? piiData.getMaskedValue() : "***ERROR***";
            } catch (Exception parseError) {
                return "***DECRYPT_ERROR***";
            }
        }
    }
    
    private boolean isEncrypted(String data) {
        return data != null && (
            data.startsWith(ENCRYPTION_MARKER) || 
            data.startsWith(LEGACY_MARKER)
        );
    }
    
    private EncryptedPiiData parseEncryptedData(String dbData) throws Exception {
        if (dbData.startsWith(ENCRYPTION_MARKER)) {
            // New format
            String base64Data = dbData.substring(ENCRYPTION_MARKER.length());
            String jsonData = new String(
                Base64.getDecoder().decode(base64Data), 
                StandardCharsets.UTF_8
            );
            return objectMapper.readValue(jsonData, EncryptedPiiData.class);
            
        } else if (dbData.startsWith(LEGACY_MARKER)) {
            // Legacy format - convert to new format
            return parseLegacyFormat(dbData);
            
        } else {
            throw new IllegalArgumentException("Invalid encrypted data format");
        }
    }
    
    private EncryptedPiiData parseLegacyFormat(String jsonStr) {
        // Parse legacy JSON format for backward compatibility
        EncryptedPiiData data = new EncryptedPiiData();
        data.setCiphertext(extractJsonValue(jsonStr, "ct"));
        data.setKeyVersion(Integer.parseInt(extractJsonValue(jsonStr, "v")));
        data.setClassification(extractJsonValue(jsonStr, "c"));
        data.setAlgorithm("AES/GCM/NoPadding"); // Default for legacy
        data.setEncryptedAt(Instant.now()); // Unknown for legacy
        return data;
    }
    
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            return "";
        }
        
        startIndex += searchKey.length();
        
        if (json.charAt(startIndex) == '"') {
            startIndex++;
            int endIndex = json.indexOf('"', startIndex);
            return json.substring(startIndex, endIndex);
        }
        
        int endIndex = json.indexOf(',', startIndex);
        if (endIndex == -1) {
            endIndex = json.indexOf('}', startIndex);
        }
        return json.substring(startIndex, endIndex);
    }
    
    private PiiType detectPiiType(String value) {
        if (EMAIL_PATTERN.matcher(value).matches()) {
            return PiiType.EMAIL;
        } else if (PHONE_PATTERN.matcher(value).matches()) {
            return PiiType.PHONE;
        } else if (SSN_PATTERN.matcher(value).matches()) {
            return PiiType.SSN;
        } else if (value.matches("\\d{16}")) {
            return PiiType.CREDIT_CARD;
        } else {
            return PiiType.GENERIC;
        }
    }
    
    private void validatePiiFormat(String value, PiiType type) {
        switch (type) {
            case EMAIL:
                if (!EMAIL_PATTERN.matcher(value).matches()) {
                    throw new IllegalArgumentException("Invalid email format");
                }
                break;
            case PHONE:
                if (!PHONE_PATTERN.matcher(value).matches()) {
                    throw new IllegalArgumentException("Invalid phone format");
                }
                break;
            case SSN:
                if (!SSN_PATTERN.matcher(value).matches()) {
                    throw new IllegalArgumentException("Invalid SSN format");
                }
                break;
            case CREDIT_CARD:
                if (!isValidCreditCard(value)) {
                    throw new IllegalArgumentException("Invalid credit card format");
                }
                break;
            default:
                // Generic PII - no specific validation
        }
    }
    
    private boolean isValidCreditCard(String number) {
        // Luhn algorithm validation
        if (number == null || number.length() < 13 || number.length() > 19) {
            return false;
        }
        
        int sum = 0;
        boolean alternate = false;
        
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(number.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        
        return (sum % 10 == 0);
    }
    
    private String maskPiiValue(String value, PiiType type) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        switch (type) {
            case EMAIL:
                int atIndex = value.indexOf('@');
                if (atIndex > 2) {
                    return value.substring(0, 2) + "***" + value.substring(atIndex);
                }
                break;
                
            case PHONE:
                if (value.length() > 4) {
                    return "***" + value.substring(value.length() - 4);
                }
                break;
                
            case SSN:
                return "***-**-" + value.substring(value.length() - 4);
                
            case CREDIT_CARD:
                if (value.length() >= 16) {
                    return value.substring(0, 4) + "********" + value.substring(value.length() - 4);
                }
                break;
                
            default:
                if (value.length() > 4) {
                    return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
                }
        }
        
        return "***MASKED***";
    }
    
    private String generateCacheKey(String encryptedData) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(encryptedData.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return encryptedData; // Fallback to using encrypted data as key
        }
    }
    
    private void cleanupCache() {
        while (true) {
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(1));
                
                long now = System.currentTimeMillis();
                decryptCache.entrySet().removeIf(entry -> 
                    entry.getValue().isExpired()
                );
                
                // Also enforce max cache size
                if (decryptCache.size() > MAX_CACHE_SIZE) {
                    // Remove oldest entries
                    decryptCache.entrySet().stream()
                        .sorted((a, b) -> Long.compare(
                            a.getValue().getCreatedAt(),
                            b.getValue().getCreatedAt()
                        ))
                        .limit(decryptCache.size() - MAX_CACHE_SIZE)
                        .forEach(entry -> decryptCache.remove(entry.getKey()));
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * Internal class for structured encrypted PII data
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class EncryptedPiiData {
        private String ciphertext;
        private int keyVersion;
        private String classification;
        private String algorithm;
        private Instant encryptedAt;
        private String blindIndex;
        private String piiType;
        private String maskedValue;
    }
    
    /**
     * PII type enumeration
     */
    private enum PiiType {
        EMAIL,
        PHONE,
        SSN,
        CREDIT_CARD,
        PASSPORT,
        DRIVER_LICENSE,
        GENERIC
    }
    
    /**
     * Cached value with expiration
     */
    private static class CachedValue {
        private final String value;
        private final long createdAt;
        private final long expiresAt;
        
        public CachedValue(String value) {
            this.value = value;
            this.createdAt = System.currentTimeMillis();
            this.expiresAt = createdAt + CACHE_TTL_MS;
        }
        
        public String getValue() {
            return value;
        }
        
        public long getCreatedAt() {
            return createdAt;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}