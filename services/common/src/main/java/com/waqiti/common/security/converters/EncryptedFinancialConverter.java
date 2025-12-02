package com.waqiti.common.security.converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.security.EnhancedFieldEncryptionService;
import com.waqiti.common.security.EnhancedFieldEncryptionService.DataClassification;
import com.waqiti.common.security.EnhancedFieldEncryptionService.EncryptedData;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Production-ready JPA Converter for automatic encryption/decryption of financial data
 * 
 * CRITICAL for PCI DSS Compliance:
 * - Handles BigDecimal amounts, account numbers, card numbers with precision
 * - Maintains exact decimal precision through encryption/decryption
 * - Automatic format validation and normalization
 * - Tokenization support for card numbers
 * - Audit logging for all financial data access
 * - Performance optimized with intelligent caching
 * - Support for currency-specific precision requirements
 * 
 * Usage:
 * @Column(name = "account_balance")
 * @Convert(converter = EncryptedFinancialConverter.class)
 * private BigDecimal balance;
 */
@Component
@Converter(autoApply = false)
@Slf4j
public class EncryptedFinancialConverter implements AttributeConverter<BigDecimal, String> {

    private static EnhancedFieldEncryptionService encryptionService;
    private static ObjectMapper objectMapper;
    private static MeterRegistry meterRegistry;
    
    @Value("${financial.encryption.precision:4}")
    private int defaultPrecision;
    
    @Value("${financial.encryption.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${financial.encryption.cache.ttl.minutes:10}")
    private int cacheTtlMinutes;
    
    @Value("${financial.encryption.audit.enabled:true}")
    private boolean auditEnabled;
    
    // High-performance cache for frequently accessed financial data
    private static final ConcurrentHashMap<String, CachedFinancialData> decryptCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 5000;
    private static final String ENCRYPTION_PREFIX = "FIN_V3:";
    private static final String LEGACY_PREFIX = "FIN:";
    
    // Metrics
    private static Counter encryptionCounter;
    private static Counter decryptionCounter;
    private static Counter cacheHitCounter;
    private static Counter cacheMissCounter;
    private static Counter precisionLossCounter;
    private static Timer encryptionTimer;
    private static Timer decryptionTimer;
    
    // Cache cleanup executor
    private static ScheduledExecutorService cacheCleanupExecutor;
    
    @Autowired
    public void setEncryptionService(EnhancedFieldEncryptionService service) {
        EncryptedFinancialConverter.encryptionService = service;
    }
    
    @Autowired
    public void setObjectMapper(ObjectMapper mapper) {
        EncryptedFinancialConverter.objectMapper = mapper;
    }
    
    @Autowired
    public void setMeterRegistry(MeterRegistry registry) {
        EncryptedFinancialConverter.meterRegistry = registry;
        initializeMetrics();
    }
    
    @PostConstruct
    public void init() {
        if (meterRegistry != null) {
            initializeMetrics();
        }
        
        // Initialize cache cleanup
        if (cacheEnabled) {
            cacheCleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("financial-cache-cleanup");
                return thread;
            });
            
            cacheCleanupExecutor.scheduleAtFixedRate(
                this::cleanupCache,
                cacheTtlMinutes,
                cacheTtlMinutes,
                TimeUnit.MINUTES
            );
        }
        
        log.info("FINANCIAL SECURITY: EncryptedFinancialConverter initialized - " +
            "precision={}, caching={}, audit={}", 
            defaultPrecision, cacheEnabled, auditEnabled);
    }
    
    private static void initializeMetrics() {
        if (meterRegistry == null) return;
        
        encryptionCounter = Counter.builder("financial.converter.encryptions")
            .description("Number of financial data encryptions")
            .tag("type", "bigdecimal")
            .register(meterRegistry);
            
        decryptionCounter = Counter.builder("financial.converter.decryptions")
            .description("Number of financial data decryptions")
            .tag("type", "bigdecimal")
            .register(meterRegistry);
            
        cacheHitCounter = Counter.builder("financial.converter.cache.hits")
            .description("Cache hits for financial data")
            .register(meterRegistry);
            
        cacheMissCounter = Counter.builder("financial.converter.cache.misses")
            .description("Cache misses for financial data")
            .register(meterRegistry);
            
        precisionLossCounter = Counter.builder("financial.converter.precision.loss")
            .description("Instances of precision loss detected")
            .register(meterRegistry);
            
        encryptionTimer = Timer.builder("financial.converter.encryption.time")
            .description("Time taken for financial encryption")
            .register(meterRegistry);
            
        decryptionTimer = Timer.builder("financial.converter.decryption.time")
            .description("Time taken for financial decryption")
            .register(meterRegistry);
    }
    
    @Override
    public String convertToDatabaseColumn(BigDecimal attribute) {
        if (attribute == null) {
            return null;
        }
        
        Timer.Sample sample = Timer.start();
        
        try {
            // Validate financial amount
            validateFinancialAmount(attribute);
            
            if (encryptionService == null) {
                log.error("PCI DSS CRITICAL: Encryption service not available - cannot store financial data");
                throw new SecurityException("Cannot store unencrypted financial data");
            }
            
            // Normalize the BigDecimal for consistent storage
            BigDecimal normalized = normalizeAmount(attribute);
            
            // Convert to string with full precision
            String plaintext = normalized.toPlainString();
            
            // Create financial metadata
            FinancialMetadata metadata = new FinancialMetadata();
            metadata.setScale(normalized.scale());
            metadata.setPrecision(normalized.precision());
            metadata.setOriginalValue(maskFinancialValue(normalized));
            metadata.setTimestamp(Instant.now());
            
            // Encrypt as financial data
            EncryptedData encryptedData = encryptionService.encryptSensitiveData(
                plaintext, DataClassification.FINANCIAL
            );
            
            // Generate blind index for range queries
            String blindIndex = generateFinancialBlindIndex(normalized);
            
            // Create structured format
            EncryptedFinancialData financialData = new EncryptedFinancialData();
            financialData.setCiphertext(encryptedData.getCiphertext());
            financialData.setKeyVersion(encryptedData.getKeyVersion());
            financialData.setClassification(DataClassification.FINANCIAL.name());
            financialData.setAlgorithm(encryptedData.getAlgorithm());
            financialData.setEncryptedAt(encryptedData.getEncryptedAt());
            financialData.setBlindIndex(blindIndex);
            financialData.setMetadata(metadata);
            
            // Serialize to compact format
            String jsonData = objectMapper.writeValueAsString(financialData);
            String result = ENCRYPTION_PREFIX + Base64.getEncoder().encodeToString(
                jsonData.getBytes(StandardCharsets.UTF_8)
            );
            
            // Update metrics
            if (encryptionCounter != null) encryptionCounter.increment();
            if (sample != null && encryptionTimer != null) sample.stop(encryptionTimer);
            
            // Audit log
            if (auditEnabled) {
                auditFinancialEncryption(metadata.getOriginalValue(), 
                    encryptedData.getKeyVersion());
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("PCI DSS CRITICAL: Failed to encrypt financial data", e);
            throw new SecurityException("Financial data encryption failed", e);
        }
    }
    
    @Override
    public BigDecimal convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        
        // Quick validation
        if (!isEncrypted(dbData)) {
            log.error("PCI DSS VIOLATION: Unencrypted financial data detected!");
            throw new SecurityException("Unencrypted financial data is not permitted");
        }
        
        // Check cache if enabled
        if (cacheEnabled) {
            String cacheKey = generateCacheKey(dbData);
            CachedFinancialData cached = decryptCache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                if (cacheHitCounter != null) cacheHitCounter.increment();
                return cached.getValue();
            }
            if (cacheMissCounter != null) cacheMissCounter.increment();
        }
        
        Timer.Sample sample = Timer.start();
        
        try {
            // Parse encrypted data
            EncryptedFinancialData financialData = parseEncryptedData(dbData);
            
            if (encryptionService == null) {
                log.error("PCI DSS: Encryption service not available for decryption");
                throw new SecurityException("Cannot decrypt financial data");
            }
            
            // Build EncryptedData for decryption
            EncryptedData encryptedData = EncryptedData.builder()
                .ciphertext(financialData.getCiphertext())
                .keyVersion(financialData.getKeyVersion())
                .classification(DataClassification.valueOf(financialData.getClassification()))
                .algorithm(financialData.getAlgorithm())
                .encryptedAt(financialData.getEncryptedAt())
                .blindIndex(financialData.getBlindIndex())
                .build();
            
            // Decrypt
            String plaintext = encryptionService.decryptSensitiveData(encryptedData);
            
            // Convert back to BigDecimal with original precision
            BigDecimal result = new BigDecimal(plaintext);
            
            // Restore original scale if different
            if (financialData.getMetadata() != null && 
                financialData.getMetadata().getScale() != null) {
                result = result.setScale(financialData.getMetadata().getScale(), 
                    RoundingMode.UNNECESSARY);
            }
            
            // Validate precision hasn't been lost
            validatePrecisionIntegrity(result, financialData.getMetadata());
            
            // Cache the result if enabled
            if (cacheEnabled && decryptCache.size() < MAX_CACHE_SIZE) {
                String cacheKey = generateCacheKey(dbData);
                decryptCache.put(cacheKey, new CachedFinancialData(result, cacheTtlMinutes));
            }
            
            // Update metrics
            if (decryptionCounter != null) decryptionCounter.increment();
            if (sample != null && decryptionTimer != null) sample.stop(decryptionTimer);
            
            // Audit log
            if (auditEnabled) {
                auditFinancialDecryption(maskFinancialValue(result), 
                    encryptedData.getKeyVersion());
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("PCI DSS: Failed to decrypt financial data", e);
            throw new SecurityException("Financial data decryption failed", e);
        }
    }
    
    private boolean isEncrypted(String data) {
        return data != null && (
            data.startsWith(ENCRYPTION_PREFIX) ||
            data.startsWith(LEGACY_PREFIX) ||
            data.startsWith("{\"ct\":") // Very old legacy format
        );
    }
    
    private EncryptedFinancialData parseEncryptedData(String dbData) throws Exception {
        if (dbData.startsWith(ENCRYPTION_PREFIX)) {
            // Current format
            String base64Data = dbData.substring(ENCRYPTION_PREFIX.length());
            String jsonData = new String(
                Base64.getDecoder().decode(base64Data),
                StandardCharsets.UTF_8
            );
            return objectMapper.readValue(jsonData, EncryptedFinancialData.class);
            
        } else if (dbData.startsWith(LEGACY_PREFIX)) {
            // Legacy format migration
            return migrateLegacyFormat(dbData);
            
        } else if (dbData.startsWith("{\"ct\":")) {
            // Very old JSON format
            return parseVeryOldFormat(dbData);
            
        } else {
            throw new IllegalArgumentException("Unknown encryption format");
        }
    }
    
    private EncryptedFinancialData migrateLegacyFormat(String dbData) throws Exception {
        // Extract base64 data after prefix
        String base64Data = dbData.substring(LEGACY_PREFIX.length());
        String jsonData = new String(
            Base64.getDecoder().decode(base64Data),
            StandardCharsets.UTF_8
        );
        
        // Parse and migrate to new format
        EncryptedFinancialData data = new EncryptedFinancialData();
        // Implementation would depend on exact legacy format
        // This is a placeholder for migration logic
        return objectMapper.readValue(jsonData, EncryptedFinancialData.class);
    }
    
    private EncryptedFinancialData parseVeryOldFormat(String jsonStr) {
        // Parse very old JSON format
        EncryptedFinancialData data = new EncryptedFinancialData();
        data.setCiphertext(extractJsonValue(jsonStr, "ct"));
        data.setKeyVersion(Integer.parseInt(extractJsonValue(jsonStr, "v")));
        data.setClassification(extractJsonValue(jsonStr, "c"));
        data.setAlgorithm("AES/GCM/NoPadding");
        data.setEncryptedAt(Instant.now());
        
        // Try to extract blind index if present
        String blindIndex = extractJsonValue(jsonStr, "bi");
        if (!blindIndex.isEmpty()) {
            data.setBlindIndex(blindIndex);
        }
        
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
            if (endIndex == -1) return "";
            return json.substring(startIndex, endIndex);
        }
        
        int endIndex = json.indexOf(',', startIndex);
        if (endIndex == -1) {
            endIndex = json.indexOf('}', startIndex);
        }
        if (endIndex == -1) return "";
        return json.substring(startIndex, endIndex).trim();
    }
    
    private void validateFinancialAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Financial amount cannot be null");
        }
        
        // Check for reasonable bounds (prevent overflow attacks)
        if (amount.abs().compareTo(new BigDecimal("999999999999999999")) > 0) {
            throw new IllegalArgumentException("Financial amount exceeds maximum allowed value");
        }
        
        // Check scale isn't excessive (prevent DoS)
        if (amount.scale() > 10) {
            throw new IllegalArgumentException("Financial amount precision exceeds maximum (10 decimal places)");
        }
    }
    
    private BigDecimal normalizeAmount(BigDecimal amount) {
        // Normalize to remove trailing zeros while preserving scale for currency
        // This ensures consistent encryption and storage
        return amount.stripTrailingZeros();
    }
    
    private String maskFinancialValue(BigDecimal value) {
        if (value == null) return "null";
        
        String str = value.toPlainString();
        if (str.length() <= 4) {
            return "****";
        }
        
        // Show first 2 and last 2 digits
        return str.substring(0, 2) + "***" + str.substring(str.length() - 2);
    }
    
    private String generateFinancialBlindIndex(BigDecimal value) {
        try {
            // Create a searchable index that preserves order for range queries
            // Normalize to 10 decimal places for consistent indexing
            BigDecimal normalized = value.setScale(10, RoundingMode.HALF_UP);
            
            // Generate HMAC-based blind index
            if (encryptionService != null) {
                return encryptionService.generateBlindIndex(
                    normalized.toPlainString(), 
                    DataClassification.FINANCIAL
                );
            }
            
            // Fallback to simple hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.toPlainString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).substring(0, 16);
            
        } catch (Exception e) {
            log.warn("Failed to generate blind index for financial data", e);
            return null;
        }
    }
    
    private void validatePrecisionIntegrity(BigDecimal value, FinancialMetadata metadata) {
        if (metadata == null || metadata.getPrecision() == null) {
            return; // No metadata to validate against
        }
        
        if (value.precision() != metadata.getPrecision()) {
            log.warn("PCI DSS: Precision mismatch detected - expected: {}, actual: {}",
                metadata.getPrecision(), value.precision());
            
            if (precisionLossCounter != null) {
                precisionLossCounter.increment();
            }
        }
    }
    
    private String generateCacheKey(String encryptedData) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(encryptedData.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return encryptedData; // Fallback
        }
    }
    
    private void cleanupCache() {
        try {
            long now = System.currentTimeMillis();
            int removed = 0;
            
            // Remove expired entries
            for (var entry : decryptCache.entrySet()) {
                if (entry.getValue().isExpired()) {
                    decryptCache.remove(entry.getKey());
                    removed++;
                }
            }
            
            // Enforce size limit if needed
            if (decryptCache.size() > MAX_CACHE_SIZE) {
                // Remove oldest entries
                decryptCache.entrySet().stream()
                    .sorted((a, b) -> Long.compare(
                        a.getValue().getCreatedAt(),
                        b.getValue().getCreatedAt()
                    ))
                    .limit(decryptCache.size() - MAX_CACHE_SIZE)
                    .forEach(entry -> {
                        decryptCache.remove(entry.getKey());
                    });
            }
            
            if (removed > 0) {
                log.debug("Financial cache cleanup: removed {} expired entries", removed);
            }
            
        } catch (Exception e) {
            log.error("Error during financial cache cleanup", e);
        }
    }
    
    private void auditFinancialEncryption(String maskedValue, int keyVersion) {
        log.info("PCI_AUDIT: Encrypted financial data - masked={}, keyVersion={}", 
            maskedValue, keyVersion);
        // In production, send to audit service/SIEM
    }
    
    private void auditFinancialDecryption(String maskedValue, int keyVersion) {
        log.debug("PCI_AUDIT: Decrypted financial data - masked={}, keyVersion={}", 
            maskedValue, keyVersion);
        // In production, send to audit service/SIEM
    }
    
    /**
     * Structured encrypted financial data
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class EncryptedFinancialData {
        private String ciphertext;
        private int keyVersion;
        private String classification;
        private String algorithm;
        private Instant encryptedAt;
        private String blindIndex;
        private FinancialMetadata metadata;
    }
    
    /**
     * Financial metadata for precision tracking
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class FinancialMetadata {
        private Integer scale;
        private Integer precision;
        private String originalValue; // Masked
        private Instant timestamp;
    }
    
    /**
     * Cached financial data with expiration
     */
    private static class CachedFinancialData {
        private final BigDecimal value;
        private final long createdAt;
        private final long expiresAt;
        
        public CachedFinancialData(BigDecimal value, int ttlMinutes) {
            this.value = value;
            this.createdAt = System.currentTimeMillis();
            this.expiresAt = createdAt + TimeUnit.MINUTES.toMillis(ttlMinutes);
        }
        
        public BigDecimal getValue() {
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

/**
 * Specialized converter for credit card numbers with PCI DSS compliance
 */
@Component
@Converter(autoApply = false)
@Slf4j
class EncryptedCardNumberConverter implements AttributeConverter<String, String> {
    
    private static EnhancedFieldEncryptionService encryptionService;
    private static ObjectMapper objectMapper;
    
    private static final String CARD_PREFIX = "PCI_CARD_V2:";
    
    @Autowired
    public void setEncryptionService(EnhancedFieldEncryptionService service) {
        EncryptedCardNumberConverter.encryptionService = service;
    }
    
    @Autowired
    public void setObjectMapper(ObjectMapper mapper) {
        EncryptedCardNumberConverter.objectMapper = mapper;
    }
    
    @Override
    public String convertToDatabaseColumn(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) {
            return cardNumber;
        }
        
        try {
            // Remove spaces and validate
            String cleanCardNumber = cardNumber.replaceAll("\\s", "");
            
            if (!isValidCardNumber(cleanCardNumber)) {
                throw new IllegalArgumentException("Invalid card number format");
            }
            
            if (encryptionService == null) {
                throw new SecurityException("PCI DSS: Cannot store card numbers without encryption");
            }
            
            // Encrypt as PCI data (highest security)
            EncryptedData encryptedData = encryptionService.encryptSensitiveData(
                cleanCardNumber, DataClassification.PCI
            );
            
            // Create tokenization data
            CardTokenData tokenData = new CardTokenData();
            tokenData.setCiphertext(encryptedData.getCiphertext());
            tokenData.setKeyVersion(encryptedData.getKeyVersion());
            tokenData.setLast4(cleanCardNumber.substring(cleanCardNumber.length() - 4));
            tokenData.setFirst6(cleanCardNumber.substring(0, 6)); // BIN for routing
            tokenData.setCardType(detectCardType(cleanCardNumber));
            tokenData.setTokenizedAt(Instant.now());
            
            // Generate token for use in non-secure contexts
            tokenData.setToken(generateCardToken(cleanCardNumber));
            
            String jsonData = objectMapper.writeValueAsString(tokenData);
            return CARD_PREFIX + Base64.getEncoder().encodeToString(
                jsonData.getBytes(StandardCharsets.UTF_8)
            );
            
        } catch (Exception e) {
            log.error("PCI DSS CRITICAL: Failed to encrypt card number", e);
            throw new SecurityException("Card encryption failed", e);
        }
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        
        if (!dbData.startsWith(CARD_PREFIX) && !dbData.startsWith("{\"ct\":")) {
            log.error("PCI DSS VIOLATION: Unencrypted card number detected!");
            throw new SecurityException("Unencrypted card numbers are forbidden");
        }
        
        try {
            CardTokenData tokenData = parseCardData(dbData);
            
            if (encryptionService == null) {
                // Return masked version for safety
                return "**** **** **** " + tokenData.getLast4();
            }
            
            EncryptedData encryptedData = EncryptedData.builder()
                .ciphertext(tokenData.getCiphertext())
                .keyVersion(tokenData.getKeyVersion())
                .classification(DataClassification.PCI)
                .algorithm("AES/GCM/NoPadding")
                .build();
            
            String cardNumber = encryptionService.decryptSensitiveData(encryptedData);
            
            // Format for display if needed
            return formatCardNumber(cardNumber);
            
        } catch (Exception e) {
            log.error("PCI DSS: Failed to decrypt card number", e);
            // For security, return masked version on error
            return "**** **** **** ****";
        }
    }
    
    private boolean isValidCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 13 || cardNumber.length() > 19) {
            return false;
        }
        
        // Luhn algorithm validation
        int sum = 0;
        boolean alternate = false;
        
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(cardNumber.substring(i, i + 1));
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
    
    private String detectCardType(String cardNumber) {
        if (cardNumber.startsWith("4")) return "VISA";
        if (cardNumber.startsWith("5")) return "MASTERCARD";
        if (cardNumber.startsWith("3")) return "AMEX";
        if (cardNumber.startsWith("6")) return "DISCOVER";
        return "OTHER";
    }
    
    private String generateCardToken(String cardNumber) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(cardNumber.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hash).substring(0, 22);
        } catch (Exception e) {
            throw new RuntimeException("Token generation failed", e);
        }
    }
    
    private String formatCardNumber(String cardNumber) {
        // Format as groups of 4 digits
        return cardNumber.replaceAll("(.{4})(?!$)", "$1 ");
    }
    
    private CardTokenData parseCardData(String dbData) throws Exception {
        if (dbData.startsWith(CARD_PREFIX)) {
            String base64Data = dbData.substring(CARD_PREFIX.length());
            String jsonData = new String(
                Base64.getDecoder().decode(base64Data),
                StandardCharsets.UTF_8
            );
            return objectMapper.readValue(jsonData, CardTokenData.class);
        } else {
            // Legacy format
            return parseLegacyCardFormat(dbData);
        }
    }
    
    private CardTokenData parseLegacyCardFormat(String jsonStr) {
        CardTokenData data = new CardTokenData();
        data.setCiphertext(extractJsonValue(jsonStr, "ct"));
        data.setKeyVersion(Integer.parseInt(extractJsonValue(jsonStr, "v")));
        data.setLast4(extractJsonValue(jsonStr, "l4"));
        data.setFirst6(extractJsonValue(jsonStr, "bin"));
        return data;
    }
    
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) return "";
        
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
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class CardTokenData {
        private String ciphertext;
        private int keyVersion;
        private String last4;
        private String first6;
        private String cardType;
        private String token;
        private Instant tokenizedAt;
    }
}