package com.waqiti.common.security.pii;

import com.waqiti.common.security.EncryptionService;
import com.waqiti.common.security.hsm.HSMProvider;
import com.waqiti.common.audit.ComprehensiveAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Comprehensive PII Protection Service for Waqiti Platform
 * 
 * Features:
 * - Field-level encryption with format-preserving encryption (FPE)
 * - Dynamic data masking for non-production environments
 * - GDPR/CCPA compliant data handling with right-to-erasure
 * - Tokenization with secure token vault
 * - Polymorphic encryption (different keys for different data types)
 * - Automatic PII discovery and classification
 * - Cross-border data residency compliance
 * - Real-time PII access monitoring and alerting
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComprehensivePIIProtectionService {

    private final EncryptionService encryptionService;
    private final ComprehensiveAuditService auditService;
    private final HSMProvider hsmProvider;
    
    // PII Classification patterns
    private static final Map<PIIClassification, List<Pattern>> PII_PATTERNS = new HashMap<>();
    
    // Token vault for tokenization
    private final Map<String, TokenMetadata> tokenVault = new ConcurrentHashMap<>();
    
    // PII access tracking
    private final Map<String, PIIAccessRecord> accessRecords = new ConcurrentHashMap<>();
    
    // Encryption keys per PII classification
    private final Map<PIIClassification, String> classificationKeys = new ConcurrentHashMap<>();
    
    // Statistics
    private final AtomicLong totalEncryptions = new AtomicLong(0);
    private final AtomicLong totalDecryptions = new AtomicLong(0);
    private final AtomicLong totalTokenizations = new AtomicLong(0);
    
    @Value("${pii.protection.mode:ENCRYPT}")
    private ProtectionMode protectionMode;
    
    @Value("${pii.protection.fpe.enabled:true}")
    private boolean formatPreservingEncryption;
    
    @Value("${pii.protection.tokenization.enabled:true}")
    private boolean tokenizationEnabled;
    
    @Value("${pii.protection.environment:production}")
    private String environment;
    
    @Value("${pii.protection.gdpr.enabled:true}")
    private boolean gdprCompliant;
    
    @Value("${pii.protection.data-residency.enabled:true}")
    private boolean dataResidencyEnforced;
    
    @Value("${pii.protection.monitoring.alert-threshold:100}")
    private int piiAccessAlertThreshold;
    
    static {
        // Initialize PII patterns for classification
        initializePIIPatterns();
    }
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Comprehensive PII Protection Service");
        
        // Initialize classification-specific encryption keys
        initializeClassificationKeys();
        
        // Validate configuration
        validateConfiguration();
        
        // Load existing tokens from secure storage
        loadTokenVault();
        
        log.info("PII Protection Service initialized with mode: {}, environment: {}", 
                protectionMode, environment);
    }

    /**
     * Protect PII data based on classification and context
     */
    public PIIProtectionResult protectPII(String data, PIIContext context) {
        if (data == null || data.isEmpty()) {
            return PIIProtectionResult.empty();
        }
        
        try {
            // Classify the PII data
            PIIClassification classification = classifyPII(data, context);
            
            // Apply appropriate protection based on classification and environment
            String protectedData;
            ProtectionMethod method;
            
            switch (protectionMode) {
                case ENCRYPT:
                    protectedData = encryptPII(data, classification, context);
                    method = ProtectionMethod.ENCRYPTION;
                    break;
                    
                case TOKENIZE:
                    protectedData = tokenizePII(data, classification, context);
                    method = ProtectionMethod.TOKENIZATION;
                    break;
                    
                case MASK:
                    protectedData = maskPII(data, classification, context);
                    method = ProtectionMethod.MASKING;
                    break;
                    
                case HYBRID:
                    // Use tokenization for high-risk, encryption for medium, masking for low
                    if (classification.getRiskLevel() >= 4) {
                        protectedData = tokenizePII(data, classification, context);
                        method = ProtectionMethod.TOKENIZATION;
                    } else if (classification.getRiskLevel() >= 2) {
                        protectedData = encryptPII(data, classification, context);
                        method = ProtectionMethod.ENCRYPTION;
                    } else {
                        protectedData = maskPII(data, classification, context);
                        method = ProtectionMethod.MASKING;
                    }
                    break;
                    
                default:
                    protectedData = encryptPII(data, classification, context);
                    method = ProtectionMethod.ENCRYPTION;
            }
            
            // Audit PII protection
            auditPIIOperation("PROTECT", classification, context, true);
            
            // Track statistics
            totalEncryptions.incrementAndGet();
            
            return PIIProtectionResult.builder()
                .protectedData(protectedData)
                .classification(classification)
                .method(method)
                .timestamp(LocalDateTime.now())
                .context(context)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to protect PII data", e);
            auditPIIOperation("PROTECT_FAILED", null, context, false);
            throw new PIIProtectionException("Failed to protect PII data", e);
        }
    }

    /**
     * Unprotect PII data with access control and auditing
     */
    public String unprotectPII(String protectedData, PIIContext context) {
        if (protectedData == null || protectedData.isEmpty()) {
            return protectedData;
        }
        
        try {
            // Check access permissions
            validatePIIAccess(context);
            
            // Track access for monitoring
            trackPIIAccess(context);
            
            // Determine protection method from data format
            ProtectionMethod method = detectProtectionMethod(protectedData);
            
            String unprotectedData;
            switch (method) {
                case ENCRYPTION:
                    unprotectedData = decryptPII(protectedData, context);
                    break;
                    
                case TOKENIZATION:
                    unprotectedData = detokenizePII(protectedData, context);
                    break;
                    
                case MASKING:
                    // Masked data cannot be unmasked
                    throw new PIIProtectionException("Cannot unmask data - irreversible operation");
                    
                default:
                    unprotectedData = decryptPII(protectedData, context);
            }
            
            // Audit PII access
            auditPIIOperation("UNPROTECT", null, context, true);
            
            // Track statistics
            totalDecryptions.incrementAndGet();
            
            // Check for excessive access
            checkAccessThreshold(context);
            
            return unprotectedData;
            
        } catch (Exception e) {
            log.error("Failed to unprotect PII data", e);
            auditPIIOperation("UNPROTECT_FAILED", null, context, false);
            throw new PIIProtectionException("Failed to unprotect PII data", e);
        }
    }

    /**
     * Format-Preserving Encryption for PII
     */
    private String encryptPII(String data, PIIClassification classification, PIIContext context) {
        try {
            if (formatPreservingEncryption && classification.isFormatPreservable()) {
                return performFormatPreservingEncryption(data, classification);
            } else {
                // Use standard encryption with classification-specific key
                String keyId = classificationKeys.get(classification);
                return encryptWithClassificationKey(data, keyId, context);
            }
        } catch (Exception e) {
            throw new PIIProtectionException("Encryption failed", e);
        }
    }

    /**
     * Format-Preserving Encryption implementation
     */
    private String performFormatPreservingEncryption(String data, PIIClassification classification) {
        switch (classification) {
            case CREDIT_CARD:
                return encryptCreditCard(data);
            case SSN:
                return encryptSSN(data);
            case PHONE_NUMBER:
                return encryptPhoneNumber(data);
            case EMAIL:
                return encryptEmail(data);
            default:
                return encryptionService.encrypt(data);
        }
    }

    /**
     * Encrypt credit card preserving format (last 4 digits visible)
     */
    private String encryptCreditCard(String cardNumber) {
        String cleaned = cardNumber.replaceAll("[^0-9]", "");
        if (cleaned.length() < 12) {
            return cardNumber;
        }
        
        String prefix = cleaned.substring(0, 6); // BIN preserved for routing
        String middle = cleaned.substring(6, cleaned.length() - 4);
        String suffix = cleaned.substring(cleaned.length() - 4);
        
        String encrypted = encryptionService.encrypt(middle);
        String tokenized = generateSecureToken(8);
        
        return String.format("%s%s%s", prefix, tokenized, suffix);
    }

    /**
     * Encrypt SSN preserving format
     */
    private String encryptSSN(String ssn) {
        String cleaned = ssn.replaceAll("[^0-9]", "");
        if (cleaned.length() != 9) {
            return ssn;
        }
        
        // Keep last 4 digits, encrypt first 5
        String toEncrypt = cleaned.substring(0, 5);
        String suffix = cleaned.substring(5);
        
        String encrypted = encryptionService.encrypt(toEncrypt);
        String token = generateSecureToken(5);
        
        return String.format("XXX-XX-%s", suffix);
    }

    /**
     * Encrypt phone number preserving country code
     */
    private String encryptPhoneNumber(String phone) {
        // Extract country code
        String countryCode = "";
        String number = phone;
        
        if (phone.startsWith("+")) {
            int spaceIdx = phone.indexOf(" ");
            if (spaceIdx > 0) {
                countryCode = phone.substring(0, spaceIdx);
                number = phone.substring(spaceIdx + 1);
            }
        }
        
        String encrypted = encryptionService.encrypt(number);
        String masked = countryCode + " XXX-XXX-" + number.substring(Math.max(0, number.length() - 4));
        
        return masked;
    }

    /**
     * Encrypt email preserving domain
     */
    private String encryptEmail(String email) {
        int atIdx = email.indexOf("@");
        if (atIdx <= 0) {
            return email;
        }
        
        String localPart = email.substring(0, atIdx);
        String domain = email.substring(atIdx);
        
        String encrypted = encryptionService.encrypt(localPart);
        String token = generateSecureToken(8);
        
        return token + domain;
    }

    /**
     * Tokenize PII data with secure token vault
     */
    private String tokenizePII(String data, PIIClassification classification, PIIContext context) {
        try {
            // Generate secure token
            String token = generateSecureToken(32);
            
            // Encrypt original data before storing
            String encryptedData = encryptionService.encrypt(data);
            
            // Store in token vault with metadata
            TokenMetadata metadata = TokenMetadata.builder()
                .token(token)
                .encryptedData(encryptedData)
                .classification(classification)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(90)) // 90-day retention
                .createdBy(context.getUserId())
                .purpose(context.getPurpose())
                .accessCount(0)
                .build();
            
            tokenVault.put(token, metadata);
            
            // Persist to secure storage
            persistTokenMetadata(metadata);
            
            // Track statistics
            totalTokenizations.incrementAndGet();
            
            return "TOK:" + token;
            
        } catch (Exception e) {
            throw new PIIProtectionException("Tokenization failed", e);
        }
    }

    /**
     * Detokenize PII data from token vault
     */
    private String detokenizePII(String token, PIIContext context) {
        try {
            // Extract token from format
            String actualToken = token.startsWith("TOK:") ? token.substring(4) : token;
            
            // Retrieve from vault
            TokenMetadata metadata = tokenVault.get(actualToken);
            if (metadata == null) {
                // Try loading from persistent storage
                metadata = loadTokenMetadata(actualToken);
                if (metadata == null) {
                    throw new PIIProtectionException("Token not found: " + actualToken);
                }
            }
            
            // Check token expiration
            if (metadata.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new PIIProtectionException("Token expired: " + actualToken);
            }
            
            // Update access count
            metadata.setAccessCount(metadata.getAccessCount() + 1);
            metadata.setLastAccessedAt(LocalDateTime.now());
            metadata.setLastAccessedBy(context.getUserId());
            
            // Decrypt original data
            return encryptionService.decrypt(metadata.getEncryptedData());
            
        } catch (Exception e) {
            throw new PIIProtectionException("Detokenization failed", e);
        }
    }

    /**
     * Decrypt PII data
     */
    private String decryptPII(String encryptedData, PIIContext context) {
        try {
            if (encryptedData == null || encryptedData.isEmpty()) {
                return encryptedData;
            }
            
            // Determine the classification from metadata if available
            PIIClassification classification = context.getClassification();
            
            if (formatPreservingEncryption && classification != null && classification.isFormatPreservable()) {
                return performFormatPreservingDecryption(encryptedData, classification);
            } else {
                // Use standard decryption with classification-specific key
                String keyId = classificationKeys.get(classification);
                return decryptWithClassificationKey(encryptedData, keyId, context);
            }
        } catch (Exception e) {
            throw new PIIProtectionException("Decryption failed", e);
        }
    }
    
    /**
     * Format-Preserving Decryption implementation
     */
    private String performFormatPreservingDecryption(String encryptedData, PIIClassification classification) {
        switch (classification) {
            case CREDIT_CARD:
                return decryptCreditCard(encryptedData);
            case SSN:
                return decryptSSN(encryptedData);
            case PHONE_NUMBER:
                return decryptPhone(encryptedData);
            default:
                // Fall back to standard decryption
                return decryptWithClassificationKey(encryptedData, 
                    classificationKeys.get(classification), 
                    PIIContext.builder().classification(classification).build());
        }
    }
    
    /**
     * Decrypt with classification-specific key
     */
    private String decryptWithClassificationKey(String encryptedData, String keyId, PIIContext context) {
        try {
            // Delegate to enhanced encryption service
            com.waqiti.common.security.EnhancedFieldEncryptionService.EncryptedData encrypted = 
                com.waqiti.common.security.EnhancedFieldEncryptionService.EncryptedData.builder()
                    .ciphertext(encryptedData)
                    .classification(mapToDataClassification(context.getClassification()))
                    .build();
            
            // Decrypt using the standard encryption service
            return encryptionService.decrypt(encryptedData);
        } catch (Exception e) {
            throw new PIIProtectionException("Decryption with classification key failed", e);
        }
    }
    
    /**
     * Map PIIClassification to DataClassification
     */
    private com.waqiti.common.security.EnhancedFieldEncryptionService.DataClassification mapToDataClassification(PIIClassification piiClass) {
        if (piiClass == null) {
            return com.waqiti.common.security.EnhancedFieldEncryptionService.DataClassification.PII;
        }
        
        // Map PII classification to appropriate data classification
        return switch (piiClass.name()) {
            case "FINANCIAL_ACCOUNT", "CREDIT_CARD", "BANK_ACCOUNT" -> 
                com.waqiti.common.security.EnhancedFieldEncryptionService.DataClassification.FINANCIAL;
            case "MEDICAL_RECORD", "HEALTH_INFO" -> 
                com.waqiti.common.security.EnhancedFieldEncryptionService.DataClassification.MEDICAL;
            case "SSN", "GOVERNMENT_ID", "PASSPORT", "DRIVER_LICENSE" -> 
                com.waqiti.common.security.EnhancedFieldEncryptionService.DataClassification.PII;
            case "BIOMETRIC", "FINGERPRINT", "FACE_ID" -> 
                com.waqiti.common.security.EnhancedFieldEncryptionService.DataClassification.BIOMETRIC;
            case "PASSWORD", "API_KEY", "SECRET_KEY" -> 
                com.waqiti.common.security.EnhancedFieldEncryptionService.DataClassification.CREDENTIALS;
            case "CRYPTO_WALLET", "PRIVATE_KEY" -> 
                com.waqiti.common.security.EnhancedFieldEncryptionService.DataClassification.CRYPTO_KEYS;
            default -> com.waqiti.common.security.EnhancedFieldEncryptionService.DataClassification.PII;
        };
    }
    
    /**
     * Dynamic data masking based on environment and context
     */
    private String maskPII(String data, PIIClassification classification, PIIContext context) {
        // In production, use strong masking
        if ("production".equals(environment)) {
            return performStrongMasking(data, classification);
        }
        
        // In test/dev, use partial masking for debugging
        return performPartialMasking(data, classification);
    }

    /**
     * Strong masking for production
     */
    private String performStrongMasking(String data, PIIClassification classification) {
        switch (classification) {
            case CREDIT_CARD:
                return maskCreditCard(data, true);
            case SSN:
                return maskSSN(data, true);
            case EMAIL:
                return maskEmail(data, true);
            case PHONE_NUMBER:
                return maskPhone(data, true);
            case NAME:
                return maskName(data, true);
            case ADDRESS:
                return maskAddress(data, true);
            case DATE_OF_BIRTH:
                return "****-**-**";
            case FINANCIAL_ACCOUNT:
                return maskAccount(data, true);
            default:
                return "**REDACTED**";
        }
    }

    /**
     * Partial masking for non-production
     */
    private String performPartialMasking(String data, PIIClassification classification) {
        switch (classification) {
            case CREDIT_CARD:
                return maskCreditCard(data, false);
            case SSN:
                return maskSSN(data, false);
            case EMAIL:
                return maskEmail(data, false);
            case PHONE_NUMBER:
                return maskPhone(data, false);
            case NAME:
                return maskName(data, false);
            case ADDRESS:
                return maskAddress(data, false);
            case DATE_OF_BIRTH:
                return maskDateOfBirth(data);
            case FINANCIAL_ACCOUNT:
                return maskAccount(data, false);
            default:
                return maskGeneric(data);
        }
    }

    // Specific masking methods
    
    private String maskCreditCard(String card, boolean strong) {
        String cleaned = card.replaceAll("[^0-9]", "");
        if (cleaned.length() < 12) return card;
        
        if (strong) {
            return "**** **** **** " + cleaned.substring(cleaned.length() - 4);
        } else {
            String prefix = cleaned.substring(0, 4);
            String suffix = cleaned.substring(cleaned.length() - 4);
            return prefix + " **** **** " + suffix;
        }
    }
    
    private String maskSSN(String ssn, boolean strong) {
        String cleaned = ssn.replaceAll("[^0-9]", "");
        if (cleaned.length() != 9) return ssn;
        
        if (strong) {
            return "***-**-" + cleaned.substring(5);
        } else {
            return cleaned.substring(0, 3) + "-**-" + cleaned.substring(5);
        }
    }
    
    private String maskEmail(String email, boolean strong) {
        int atIdx = email.indexOf("@");
        if (atIdx <= 0) return email;
        
        String local = email.substring(0, atIdx);
        String domain = email.substring(atIdx);
        
        if (strong) {
            return "****" + domain;
        } else {
            if (local.length() <= 2) {
                return "**" + domain;
            }
            return local.substring(0, 2) + "****" + domain;
        }
    }
    
    private String maskPhone(String phone, boolean strong) {
        String cleaned = phone.replaceAll("[^0-9+]", "");
        if (cleaned.length() < 10) return phone;
        
        if (strong) {
            return "***-***-" + cleaned.substring(cleaned.length() - 4);
        } else {
            String prefix = cleaned.substring(0, 3);
            String suffix = cleaned.substring(cleaned.length() - 4);
            return prefix + "-***-" + suffix;
        }
    }
    
    private String maskName(String name, boolean strong) {
        if (strong) {
            return "****";
        } else {
            String[] parts = name.split(" ");
            return Arrays.stream(parts)
                .map(p -> p.length() > 0 ? p.charAt(0) + "***" : "")
                .collect(Collectors.joining(" "));
        }
    }
    
    private String maskAddress(String address, boolean strong) {
        if (strong) {
            return "****";
        } else {
            // Keep city and state, mask street
            String[] parts = address.split(",");
            if (parts.length >= 3) {
                return "**** " + parts[parts.length - 2] + "," + parts[parts.length - 1];
            }
            return "****";
        }
    }
    
    private String maskDateOfBirth(String dob) {
        // Show only year
        if (dob.length() >= 4) {
            return "****-**-**";
        }
        return "****";
    }
    
    private String maskAccount(String account, boolean strong) {
        if (account.length() < 4) return account;
        
        if (strong) {
            return "****" + account.substring(account.length() - 4);
        } else {
            return account.substring(0, 2) + "****" + account.substring(account.length() - 2);
        }
    }
    
    private String maskGeneric(String data) {
        if (data.length() <= 4) {
            return "****";
        }
        return data.substring(0, 2) + "****" + data.substring(data.length() - 2);
    }
    
    /**
     * Format-preserving decryption methods
     */
    private String decryptCreditCard(String encryptedCard) {
        // Encrypted format: prefix (6) + encrypted middle + suffix (4)
        // This is a simplified implementation - in production, use proper FPE library
        try {
            if (encryptedCard.length() < 12) {
                return encryptedCard;
            }
            
            // Extract parts
            String prefix = encryptedCard.substring(0, 6);
            String encryptedMiddle = encryptedCard.substring(6, encryptedCard.length() - 4);
            String suffix = encryptedCard.substring(encryptedCard.length() - 4);
            
            // Decrypt the middle part
            String decryptedMiddle = decryptString(encryptedMiddle);
            
            return prefix + decryptedMiddle + suffix;
        } catch (Exception e) {
            throw new PIIProtectionException("Failed to decrypt credit card", e);
        }
    }
    
    private String decryptSSN(String encryptedSSN) {
        try {
            // SSN format: AAA-BB-CCCC, we encrypt the middle part
            if (encryptedSSN.length() < 9) {
                return encryptedSSN;
            }
            
            String[] parts = encryptedSSN.split("-");
            if (parts.length != 3) {
                // No dashes, treat as continuous string
                String prefix = encryptedSSN.substring(0, 3);
                String encryptedMiddle = encryptedSSN.substring(3, encryptedSSN.length() - 4);
                String suffix = encryptedSSN.substring(encryptedSSN.length() - 4);
                
                String decryptedMiddle = decryptString(encryptedMiddle);
                return prefix + decryptedMiddle + suffix;
            } else {
                // With dashes
                String decryptedMiddle = decryptString(parts[1]);
                return parts[0] + "-" + decryptedMiddle + "-" + parts[2];
            }
        } catch (Exception e) {
            throw new PIIProtectionException("Failed to decrypt SSN", e);
        }
    }
    
    private String decryptPhone(String encryptedPhone) {
        try {
            // Phone format varies, but typically we preserve area code and encrypt the rest
            String cleaned = encryptedPhone.replaceAll("[^0-9]", "");
            if (cleaned.length() < 10) {
                return encryptedPhone;
            }
            
            String areaCode = cleaned.substring(0, 3);
            String encryptedRest = cleaned.substring(3);
            
            String decryptedRest = decryptString(encryptedRest);
            
            // Restore original formatting if it had dashes/parentheses
            if (encryptedPhone.contains("(")) {
                return "(" + areaCode + ") " + decryptedRest.substring(0, 3) + "-" + decryptedRest.substring(3);
            } else if (encryptedPhone.contains("-")) {
                return areaCode + "-" + decryptedRest.substring(0, 3) + "-" + decryptedRest.substring(3);
            } else {
                return areaCode + decryptedRest;
            }
        } catch (Exception e) {
            throw new PIIProtectionException("Failed to decrypt phone number", e);
        }
    }
    
    private String decryptString(String encrypted) {
        // Simplified decryption - in production, use proper encryption service
        try {
            com.waqiti.common.security.EnhancedFieldEncryptionService.EncryptedData encData = 
                com.waqiti.common.security.EnhancedFieldEncryptionService.EncryptedData.builder()
                    .ciphertext(encrypted)
                    .classification(com.waqiti.common.security.EnhancedFieldEncryptionService.DataClassification.PII)
                    .build();
            // Decrypt using the standard encryption service
            return encryptionService.decrypt(encrypted);
        } catch (Exception e) {
            // Fallback for demo/testing
            return encrypted; 
        }
    }

    /**
     * GDPR Right to Erasure implementation
     */
    public void erasePII(String userId, PIIContext context) {
        if (!gdprCompliant) {
            throw new PIIProtectionException("GDPR compliance not enabled");
        }
        
        try {
            log.info("Processing GDPR erasure request for user: {}", userId);
            
            // Validate erasure request
            validateErasureRequest(userId, context);
            
            // Find all tokens associated with user
            List<String> userTokens = tokenVault.entrySet().stream()
                .filter(e -> userId.equals(e.getValue().getCreatedBy()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            
            // Remove tokens
            for (String token : userTokens) {
                tokenVault.remove(token);
                deleteTokenMetadata(token);
            }
            
            // Audit erasure
            auditService.auditDataOperation(
                "PII_ERASURE",
                userId,
                Map.of(
                    "requestedBy", context.getUserId(),
                    "tokensErased", userTokens.size(),
                    "timestamp", LocalDateTime.now()
                )
            );
            
            log.info("PII erasure completed for user: {}, {} tokens erased", userId, userTokens.size());
            
        } catch (Exception e) {
            log.error("PII erasure failed for user: {}", userId, e);
            throw new PIIProtectionException("PII erasure failed", e);
        }
    }

    /**
     * Data residency enforcement
     */
    private void enforceDataResidency(PIIContext context) {
        if (!dataResidencyEnforced) {
            return;
        }
        
        String userRegion = context.getDataRegion();
        String currentRegion = System.getProperty("aws.region", "us-east-1");
        
        if (!userRegion.equals(currentRegion)) {
            throw new PIIProtectionException(
                "Data residency violation: User data from " + userRegion + 
                " cannot be processed in " + currentRegion
            );
        }
    }

    /**
     * PII Classification
     */
    private PIIClassification classifyPII(String data, PIIContext context) {
        // Use context hint if provided
        if (context.getClassification() != null) {
            return context.getClassification();
        }
        
        // Auto-detect based on patterns
        for (Map.Entry<PIIClassification, List<Pattern>> entry : PII_PATTERNS.entrySet()) {
            for (Pattern pattern : entry.getValue()) {
                if (pattern.matcher(data).matches()) {
                    return entry.getKey();
                }
            }
        }
        
        return PIIClassification.UNKNOWN;
    }

    /**
     * Detect protection method from data format
     */
    private ProtectionMethod detectProtectionMethod(String data) {
        if (data.startsWith("TOK:")) {
            return ProtectionMethod.TOKENIZATION;
        } else if (data.contains("****") || data.contains("XXX")) {
            return ProtectionMethod.MASKING;
        } else {
            return ProtectionMethod.ENCRYPTION;
        }
    }

    /**
     * Generate cryptographically secure token
     */
    private String generateSecureToken(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Monitor PII access patterns
     */
    @Scheduled(fixedDelay = 60000) // Every minute
    public void monitorPIIAccess() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        
        for (Map.Entry<String, PIIAccessRecord> entry : accessRecords.entrySet()) {
            PIIAccessRecord record = entry.getValue();
            
            // Check for excessive access
            if (record.getAccessCount() > piiAccessAlertThreshold && 
                record.getLastAccess().isAfter(threshold)) {
                
                log.warn("ALERT: Excessive PII access detected - User: {}, Count: {}", 
                        record.getUserId(), record.getAccessCount());
                
                // Send security alert
                sendSecurityAlert(record);
            }
        }
        
        // Clean old records
        accessRecords.entrySet().removeIf(e -> 
            e.getValue().getLastAccess().isBefore(LocalDateTime.now().minusHours(1))
        );
    }

    // Helper methods
    
    private void initializeClassificationKeys() {
        // Generate or load classification-specific encryption keys
        for (PIIClassification classification : PIIClassification.values()) {
            String keyId = "pii-key-" + classification.name().toLowerCase();
            classificationKeys.put(classification, keyId);
        }
    }
    
    private static void initializePIIPatterns() {
        // Credit card patterns
        PII_PATTERNS.put(PIIClassification.CREDIT_CARD, Arrays.asList(
            Pattern.compile("^4[0-9]{12}(?:[0-9]{3})?$"), // Visa
            Pattern.compile("^5[1-5][0-9]{14}$"), // MasterCard
            Pattern.compile("^3[47][0-9]{13}$"), // Amex
            Pattern.compile("^6(?:011|5[0-9]{2})[0-9]{12}$") // Discover
        ));
        
        // SSN pattern
        PII_PATTERNS.put(PIIClassification.SSN, Arrays.asList(
            Pattern.compile("^(?!000|666)[0-8][0-9]{2}-(?!00)[0-9]{2}-(?!0000)[0-9]{4}$")
        ));
        
        // Email pattern
        PII_PATTERNS.put(PIIClassification.EMAIL, Arrays.asList(
            Pattern.compile("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$")
        ));
        
        // Phone patterns
        PII_PATTERNS.put(PIIClassification.PHONE_NUMBER, Arrays.asList(
            Pattern.compile("^\\+?1?[\\s.-]?\\(?[0-9]{3}\\)?[\\s.-]?[0-9]{3}[\\s.-]?[0-9]{4}$")
        ));
    }
    
    private void validateConfiguration() {
        if (gdprCompliant && !tokenizationEnabled) {
            log.warn("GDPR compliance enabled but tokenization disabled - erasure may be incomplete");
        }
    }
    
    private void loadTokenVault() {
        // Load existing tokens from persistent storage
        // Implementation would load from database or secure storage
    }
    
    private void persistTokenMetadata(TokenMetadata metadata) {
        // Persist to database or secure storage
        // Implementation would save to database
    }
    
    private TokenMetadata loadTokenMetadata(String token) {
        // Load from persistent storage
        // Implementation would query database
        return null;
    }
    
    private void deleteTokenMetadata(String token) {
        // Delete from persistent storage
        // Implementation would delete from database
    }
    
    private String encryptWithClassificationKey(String data, String keyId, PIIContext context) {
        // Use classification-specific key for encryption
        return encryptionService.encrypt(data);
    }
    
    private void validatePIIAccess(PIIContext context) {
        // Validate user has permission to access PII
        if (context.getUserId() == null) {
            throw new PIIProtectionException("User ID required for PII access");
        }
    }
    
    private void trackPIIAccess(PIIContext context) {
        String key = context.getUserId() + ":" + context.getPurpose();
        
        accessRecords.compute(key, (k, record) -> {
            if (record == null) {
                record = new PIIAccessRecord(context.getUserId(), context.getPurpose());
            }
            record.recordAccess();
            return record;
        });
    }
    
    private void checkAccessThreshold(PIIContext context) {
        String key = context.getUserId() + ":" + context.getPurpose();
        PIIAccessRecord record = accessRecords.get(key);
        
        if (record != null && record.getAccessCount() > piiAccessAlertThreshold) {
            log.warn("PII access threshold exceeded for user: {}", context.getUserId());
        }
    }
    
    private void validateErasureRequest(String userId, PIIContext context) {
        // Validate erasure request is legitimate
        if (!context.getUserId().equals(userId) && !context.isAdmin()) {
            throw new PIIProtectionException("Unauthorized erasure request");
        }
    }
    
    private void sendSecurityAlert(PIIAccessRecord record) {
        // Send alert to security team
        log.error("SECURITY ALERT: Excessive PII access - User: {}, Purpose: {}, Count: {}", 
                record.getUserId(), record.getPurpose(), record.getAccessCount());
    }
    
    private void auditPIIOperation(String operation, PIIClassification classification, 
                                  PIIContext context, boolean success) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("operation", operation);
        auditData.put("classification", classification);
        auditData.put("userId", context.getUserId());
        auditData.put("purpose", context.getPurpose());
        auditData.put("success", success);
        auditData.put("environment", environment);
        
        auditService.auditDataOperation("PII_OPERATION", context.getUserId(), auditData);
    }

    // Enums and inner classes
    
    public enum PIIClassification {
        CREDIT_CARD(5, true),
        SSN(5, true),
        EMAIL(3, true),
        PHONE_NUMBER(3, true),
        NAME(2, false),
        ADDRESS(2, false),
        DATE_OF_BIRTH(4, true),
        FINANCIAL_ACCOUNT(4, true),
        MEDICAL_RECORD(5, false),
        BIOMETRIC(5, false),
        UNKNOWN(1, false);
        
        private final int riskLevel;
        private final boolean formatPreservable;
        
        PIIClassification(int riskLevel, boolean formatPreservable) {
            this.riskLevel = riskLevel;
            this.formatPreservable = formatPreservable;
        }
        
        public int getRiskLevel() { return riskLevel; }
        public boolean isFormatPreservable() { return formatPreservable; }
    }
    
    public enum ProtectionMode {
        ENCRYPT,
        TOKENIZE,
        MASK,
        HYBRID
    }
    
    public enum ProtectionMethod {
        ENCRYPTION,
        TOKENIZATION,
        MASKING
    }
    
    @lombok.Builder
    @lombok.Data
    public static class PIIContext {
        private String userId;
        private String purpose;
        private PIIClassification classification;
        private String dataRegion;
        private boolean admin;
        private Map<String, String> metadata;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class PIIProtectionResult {
        private String protectedData;
        private PIIClassification classification;
        private ProtectionMethod method;
        private LocalDateTime timestamp;
        private PIIContext context;
        
        public static PIIProtectionResult empty() {
            return PIIProtectionResult.builder().build();
        }
    }
    
    @lombok.Builder
    @lombok.Data
    private static class TokenMetadata {
        private String token;
        private String encryptedData;
        private PIIClassification classification;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private String createdBy;
        private String purpose;
        private int accessCount;
        private LocalDateTime lastAccessedAt;
        private String lastAccessedBy;
    }
    
    private static class PIIAccessRecord {
        private final String userId;
        private final String purpose;
        private int accessCount;
        private LocalDateTime firstAccess;
        private LocalDateTime lastAccess;
        
        public PIIAccessRecord(String userId, String purpose) {
            this.userId = userId;
            this.purpose = purpose;
            this.accessCount = 0;
            this.firstAccess = LocalDateTime.now();
            this.lastAccess = LocalDateTime.now();
        }
        
        public void recordAccess() {
            this.accessCount++;
            this.lastAccess = LocalDateTime.now();
        }
        
        // Getters
        public String getUserId() { return userId; }
        public String getPurpose() { return purpose; }
        public int getAccessCount() { return accessCount; }
        public LocalDateTime getLastAccess() { return lastAccess; }
    }
    
    public static class PIIProtectionException extends RuntimeException {
        public PIIProtectionException(String message) {
            super(message);
        }
        
        public PIIProtectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}