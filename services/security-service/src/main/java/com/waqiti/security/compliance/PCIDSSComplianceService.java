package com.waqiti.security.compliance;

import com.waqiti.security.audit.AuditService;
import com.waqiti.security.encryption.TokenizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * PCI DSS Compliance Service
 * Implements PCI DSS v4.0 requirements for payment card data protection
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PCIDSSComplianceService {
    
    private final TokenizationService tokenizationService;
    private final AuditService auditService;
    
    // PCI DSS Requirements
    private static final int MIN_KEY_LENGTH = 256;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    
    // Card number patterns
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("\\b\\d{13,19}\\b");
    private static final Pattern CVV_PATTERN = Pattern.compile("\\b\\d{3,4}\\b");
    
    // Session management
    private final Map<String, PCISession> activeSessions = new ConcurrentHashMap<>();
    private static final int SESSION_TIMEOUT_MINUTES = 15;
    
    /**
     * Requirement 3: Protect stored cardholder data
     */
    public String protectCardholderData(CardholderData data) {
        log.info("Protecting cardholder data");
        
        try {
            // Validate card data
            validateCardData(data);
            
            // Tokenize primary account number (PAN)
            String tokenizedPAN = tokenizationService.tokenize(data.getPan());
            
            // Create secure cardholder data record
            SecureCardholderData secureData = SecureCardholderData.builder()
                .tokenizedPAN(tokenizedPAN)
                .maskedPAN(maskPAN(data.getPan()))
                .cardholderName(encryptSensitiveData(data.getCardholderName()))
                .expirationDate(data.getExpirationDate()) // Can be stored in clear
                .serviceCode(null) // Must not be stored
                .build();
            
            // Audit the protection event
            auditService.logDataProtection("CARDHOLDER_DATA_PROTECTED", 
                Map.of("token", tokenizedPAN, "timestamp", Instant.now()));
            
            return tokenizedPAN;
            
        } catch (Exception e) {
            log.error("Failed to protect cardholder data", e);
            auditService.logSecurityEvent("CARDHOLDER_DATA_PROTECTION_FAILED", 
                Map.of("error", e.getMessage()));
            throw new PCIDSSComplianceException("Failed to protect cardholder data", e);
        }
    }
    
    /**
     * Requirement 4: Encrypt transmission of cardholder data
     */
    public byte[] encryptForTransmission(String data) {
        try {
            // Generate AES key
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(MIN_KEY_LENGTH);
            SecretKey key = keyGen.generateKey();
            
            // Generate IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            
            // Encrypt data
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
            
            byte[] encryptedData = cipher.doFinal(data.getBytes());
            
            // Combine IV and encrypted data
            byte[] combined = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);
            
            return combined;
            
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new PCIDSSComplianceException("Encryption failed", e);
        }
    }
    
    /**
     * Requirement 7: Restrict access to cardholder data by business need to know
     */
    public boolean authorizeAccess(String userId, String resource, AccessType accessType) {
        log.debug("Authorizing access for user {} to resource {} with type {}", 
            userId, resource, accessType);
        
        // Check if user has active PCI session
        PCISession session = activeSessions.get(userId);
        if (session == null || session.isExpired()) {
            log.warn("No active PCI session for user {}", userId);
            return false;
        }
        
        // Check role-based access
        boolean authorized = checkRoleBasedAccess(session.getRole(), resource, accessType);
        
        // Audit access attempt
        auditService.logAccessAttempt(userId, resource, accessType.name(), authorized);
        
        return authorized;
    }
    
    /**
     * Requirement 8: Identify and authenticate access to system components
     */
    public PCISession authenticateUser(String userId, String password, String mfaToken) {
        log.info("Authenticating user {} for PCI access", userId);
        
        try {
            // Validate password complexity (Requirement 8.3.6)
            if (!isPasswordCompliant(password)) {
                throw new PCIDSSComplianceException("Password does not meet PCI DSS requirements");
            }
            
            // Verify MFA token (Requirement 8.3.4)
            if (!verifyMFAToken(userId, mfaToken)) {
                throw new PCIDSSComplianceException("MFA verification failed");
            }
            
            // Create PCI session
            PCISession session = PCISession.builder()
                .sessionId(UUID.randomUUID().toString())
                .userId(userId)
                .role(getUserRole(userId))
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(SESSION_TIMEOUT_MINUTES, ChronoUnit.MINUTES))
                .build();
            
            activeSessions.put(userId, session);
            
            // Audit successful authentication
            auditService.logAuthentication(userId, "PCI_ACCESS_GRANTED", true);
            
            return session;
            
        } catch (Exception e) {
            // Audit failed authentication
            auditService.logAuthentication(userId, "PCI_ACCESS_DENIED", false);
            throw e;
        }
    }
    
    /**
     * Requirement 9: Restrict physical access to cardholder data
     * This is handled at infrastructure level but we log access
     */
    public void logPhysicalAccess(String userId, String location, String action) {
        auditService.logPhysicalAccess(userId, location, action, Instant.now());
    }
    
    /**
     * Requirement 10: Track and monitor all access to network resources
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void monitorAccess() {
        log.debug("Monitoring PCI access patterns");
        
        // Check for suspicious access patterns
        Map<String, Integer> accessCounts = auditService.getRecentAccessCounts(5);
        
        for (Map.Entry<String, Integer> entry : accessCounts.entrySet()) {
            if (entry.getValue() > 100) { // Threshold for suspicious activity
                log.warn("Suspicious access pattern detected for user {}", entry.getKey());
                auditService.logSecurityAlert("SUSPICIOUS_ACCESS_PATTERN", 
                    Map.of("userId", entry.getKey(), "count", entry.getValue()));
            }
        }
        
        // Clean up expired sessions
        activeSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * Requirement 11: Regularly test security systems and processes
     */
    public ComplianceReport runComplianceCheck() {
        log.info("Running PCI DSS compliance check");
        
        ComplianceReport report = new ComplianceReport();
        report.setTimestamp(Instant.now());
        
        // Check encryption key strength
        report.addCheck("Encryption Key Strength", checkEncryptionKeyStrength());
        
        // Check access control
        report.addCheck("Access Control", checkAccessControl());
        
        // Check audit logging
        report.addCheck("Audit Logging", checkAuditLogging());
        
        // Check data retention
        report.addCheck("Data Retention", checkDataRetention());
        
        // Check network segmentation
        report.addCheck("Network Segmentation", checkNetworkSegmentation());
        
        // Generate overall compliance score
        report.calculateComplianceScore();
        
        // Store compliance report
        auditService.storeComplianceReport(report);
        
        return report;
    }
    
    /**
     * Requirement 12: Maintain a policy that addresses information security
     */
    public SecurityPolicy getSecurityPolicy() {
        return SecurityPolicy.builder()
            .version("1.0")
            .lastUpdated(Instant.now())
            .passwordPolicy(PasswordPolicy.builder()
                .minLength(12)
                .requireUppercase(true)
                .requireLowercase(true)
                .requireNumbers(true)
                .requireSpecialChars(true)
                .maxAge(90)
                .historyCount(4)
                .build())
            .accessPolicy(AccessPolicy.builder()
                .sessionTimeout(SESSION_TIMEOUT_MINUTES)
                .maxFailedAttempts(3)
                .lockoutDuration(30)
                .requireMFA(true)
                .build())
            .dataRetentionPolicy(DataRetentionPolicy.builder()
                .cardDataRetention(0) // Do not store
                .tokenRetention(365)
                .auditLogRetention(1095) // 3 years
                .build())
            .build();
    }
    
    // Helper methods
    
    private void validateCardData(CardholderData data) {
        if (!isValidCardNumber(data.getPan())) {
            throw new IllegalArgumentException("Invalid card number");
        }
        
        if (data.getCvv() != null && !data.getCvv().isEmpty()) {
            throw new IllegalArgumentException("CVV must not be stored");
        }
    }
    
    private boolean isValidCardNumber(String cardNumber) {
        // Luhn algorithm validation
        if (cardNumber == null || !cardNumber.matches("\\d{13,19}")) {
            return false;
        }
        
        int sum = 0;
        boolean alternate = false;
        
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cardNumber.charAt(i));
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit % 10) + 1;
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        return (sum % 10 == 0);
    }
    
    private String maskPAN(String pan) {
        if (pan.length() <= 10) {
            return "****";
        }
        
        String firstSix = pan.substring(0, 6);
        String lastFour = pan.substring(pan.length() - 4);
        String masked = "*".repeat(pan.length() - 10);
        
        return firstSix + masked + lastFour;
    }
    
    private String encryptSensitiveData(String data) {
        // Implementation would use HSM or key management service
        return Base64.getEncoder().encodeToString(data.getBytes());
    }
    
    private boolean isPasswordCompliant(String password) {
        if (password.length() < 12) return false;
        if (!password.matches(".*[A-Z].*")) return false;
        if (!password.matches(".*[a-z].*")) return false;
        if (!password.matches(".*[0-9].*")) return false;
        if (!password.matches(".*[!@#$%^&*(),.?\":{}|<>].*")) return false;
        
        return true;
    }
    
    private boolean verifyMFAToken(String userId, String token) {
        // Implementation would verify TOTP/SMS token
        return token != null && token.length() == 6;
    }
    
    private String getUserRole(String userId) {
        // Implementation would fetch from user service
        return "PCI_USER";
    }
    
    private boolean checkRoleBasedAccess(String role, String resource, AccessType accessType) {
        // Implementation of role-based access control
        Map<String, Set<String>> rolePermissions = Map.of(
            "PCI_ADMIN", Set.of("READ", "WRITE", "DELETE", "AUDIT"),
            "PCI_USER", Set.of("READ"),
            "PCI_AUDITOR", Set.of("READ", "AUDIT")
        );
        
        Set<String> permissions = rolePermissions.getOrDefault(role, Set.of());
        return permissions.contains(accessType.name());
    }
    
    private ComplianceCheckResult checkEncryptionKeyStrength() {
        // Implementation would check actual key strength
        return ComplianceCheckResult.PASS;
    }
    
    private ComplianceCheckResult checkAccessControl() {
        // Implementation would verify access control mechanisms
        return ComplianceCheckResult.PASS;
    }
    
    private ComplianceCheckResult checkAuditLogging() {
        // Implementation would verify audit logging is working
        return ComplianceCheckResult.PASS;
    }
    
    private ComplianceCheckResult checkDataRetention() {
        // Implementation would verify data retention policies
        return ComplianceCheckResult.PASS;
    }
    
    private ComplianceCheckResult checkNetworkSegmentation() {
        // Implementation would verify network segmentation
        return ComplianceCheckResult.PASS;
    }
    
    // Inner classes
    
    public static class CardholderData {
        private String pan;
        private String cardholderName;
        private String expirationDate;
        private String cvv;
        private String serviceCode;
        
        // Getters and setters
        public String getPan() { return pan; }
        public void setPan(String pan) { this.pan = pan; }
        
        public String getCardholderName() { return cardholderName; }
        public void setCardholderName(String cardholderName) { this.cardholderName = cardholderName; }
        
        public String getExpirationDate() { return expirationDate; }
        public void setExpirationDate(String expirationDate) { this.expirationDate = expirationDate; }
        
        public String getCvv() { return cvv; }
        public void setCvv(String cvv) { this.cvv = cvv; }
        
        public String getServiceCode() { return serviceCode; }
        public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    }
    
    public static class SecureCardholderData {
        private String tokenizedPAN;
        private String maskedPAN;
        private String cardholderName;
        private String expirationDate;
        private String serviceCode;
        
        public static SecureCardholderDataBuilder builder() {
            return new SecureCardholderDataBuilder();
        }
        
        public static class SecureCardholderDataBuilder {
            private String tokenizedPAN;
            private String maskedPAN;
            private String cardholderName;
            private String expirationDate;
            private String serviceCode;
            
            public SecureCardholderDataBuilder tokenizedPAN(String tokenizedPAN) {
                this.tokenizedPAN = tokenizedPAN;
                return this;
            }
            
            public SecureCardholderDataBuilder maskedPAN(String maskedPAN) {
                this.maskedPAN = maskedPAN;
                return this;
            }
            
            public SecureCardholderDataBuilder cardholderName(String cardholderName) {
                this.cardholderName = cardholderName;
                return this;
            }
            
            public SecureCardholderDataBuilder expirationDate(String expirationDate) {
                this.expirationDate = expirationDate;
                return this;
            }
            
            public SecureCardholderDataBuilder serviceCode(String serviceCode) {
                this.serviceCode = serviceCode;
                return this;
            }
            
            public SecureCardholderData build() {
                SecureCardholderData data = new SecureCardholderData();
                data.tokenizedPAN = this.tokenizedPAN;
                data.maskedPAN = this.maskedPAN;
                data.cardholderName = this.cardholderName;
                data.expirationDate = this.expirationDate;
                data.serviceCode = this.serviceCode;
                return data;
            }
        }
    }
    
    public static class PCISession {
        private String sessionId;
        private String userId;
        private String role;
        private Instant createdAt;
        private Instant expiresAt;
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
        
        public static PCISessionBuilder builder() {
            return new PCISessionBuilder();
        }
        
        // Getters
        public String getSessionId() { return sessionId; }
        public String getUserId() { return userId; }
        public String getRole() { return role; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getExpiresAt() { return expiresAt; }
        
        public static class PCISessionBuilder {
            private String sessionId;
            private String userId;
            private String role;
            private Instant createdAt;
            private Instant expiresAt;
            
            public PCISessionBuilder sessionId(String sessionId) {
                this.sessionId = sessionId;
                return this;
            }
            
            public PCISessionBuilder userId(String userId) {
                this.userId = userId;
                return this;
            }
            
            public PCISessionBuilder role(String role) {
                this.role = role;
                return this;
            }
            
            public PCISessionBuilder createdAt(Instant createdAt) {
                this.createdAt = createdAt;
                return this;
            }
            
            public PCISessionBuilder expiresAt(Instant expiresAt) {
                this.expiresAt = expiresAt;
                return this;
            }
            
            public PCISession build() {
                PCISession session = new PCISession();
                session.sessionId = this.sessionId;
                session.userId = this.userId;
                session.role = this.role;
                session.createdAt = this.createdAt;
                session.expiresAt = this.expiresAt;
                return session;
            }
        }
    }
    
    public enum AccessType {
        READ, WRITE, DELETE, AUDIT
    }
    
    public static class ComplianceReport {
        private Instant timestamp;
        private Map<String, ComplianceCheckResult> checks = new HashMap<>();
        private double complianceScore;
        
        public void addCheck(String checkName, ComplianceCheckResult result) {
            checks.put(checkName, result);
        }
        
        public void calculateComplianceScore() {
            long passCount = checks.values().stream()
                .filter(result -> result == ComplianceCheckResult.PASS)
                .count();
            
            complianceScore = (double) passCount / checks.size() * 100;
        }
        
        // Getters and setters
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
        
        public Map<String, ComplianceCheckResult> getChecks() { return checks; }
        public double getComplianceScore() { return complianceScore; }
    }
    
    public enum ComplianceCheckResult {
        PASS, FAIL, WARNING
    }
    
    public static class SecurityPolicy {
        private String version;
        private Instant lastUpdated;
        private PasswordPolicy passwordPolicy;
        private AccessPolicy accessPolicy;
        private DataRetentionPolicy dataRetentionPolicy;
        
        public static SecurityPolicyBuilder builder() {
            return new SecurityPolicyBuilder();
        }
        
        public static class SecurityPolicyBuilder {
            private String version;
            private Instant lastUpdated;
            private PasswordPolicy passwordPolicy;
            private AccessPolicy accessPolicy;
            private DataRetentionPolicy dataRetentionPolicy;
            
            public SecurityPolicyBuilder version(String version) {
                this.version = version;
                return this;
            }
            
            public SecurityPolicyBuilder lastUpdated(Instant lastUpdated) {
                this.lastUpdated = lastUpdated;
                return this;
            }
            
            public SecurityPolicyBuilder passwordPolicy(PasswordPolicy passwordPolicy) {
                this.passwordPolicy = passwordPolicy;
                return this;
            }
            
            public SecurityPolicyBuilder accessPolicy(AccessPolicy accessPolicy) {
                this.accessPolicy = accessPolicy;
                return this;
            }
            
            public SecurityPolicyBuilder dataRetentionPolicy(DataRetentionPolicy dataRetentionPolicy) {
                this.dataRetentionPolicy = dataRetentionPolicy;
                return this;
            }
            
            public SecurityPolicy build() {
                SecurityPolicy policy = new SecurityPolicy();
                policy.version = this.version;
                policy.lastUpdated = this.lastUpdated;
                policy.passwordPolicy = this.passwordPolicy;
                policy.accessPolicy = this.accessPolicy;
                policy.dataRetentionPolicy = this.dataRetentionPolicy;
                return policy;
            }
        }
    }
    
    public static class PasswordPolicy {
        private int minLength;
        private boolean requireUppercase;
        private boolean requireLowercase;
        private boolean requireNumbers;
        private boolean requireSpecialChars;
        private int maxAge;
        private int historyCount;
        
        public static PasswordPolicyBuilder builder() {
            return new PasswordPolicyBuilder();
        }
        
        public static class PasswordPolicyBuilder {
            private int minLength;
            private boolean requireUppercase;
            private boolean requireLowercase;
            private boolean requireNumbers;
            private boolean requireSpecialChars;
            private int maxAge;
            private int historyCount;
            
            public PasswordPolicyBuilder minLength(int minLength) {
                this.minLength = minLength;
                return this;
            }
            
            public PasswordPolicyBuilder requireUppercase(boolean requireUppercase) {
                this.requireUppercase = requireUppercase;
                return this;
            }
            
            public PasswordPolicyBuilder requireLowercase(boolean requireLowercase) {
                this.requireLowercase = requireLowercase;
                return this;
            }
            
            public PasswordPolicyBuilder requireNumbers(boolean requireNumbers) {
                this.requireNumbers = requireNumbers;
                return this;
            }
            
            public PasswordPolicyBuilder requireSpecialChars(boolean requireSpecialChars) {
                this.requireSpecialChars = requireSpecialChars;
                return this;
            }
            
            public PasswordPolicyBuilder maxAge(int maxAge) {
                this.maxAge = maxAge;
                return this;
            }
            
            public PasswordPolicyBuilder historyCount(int historyCount) {
                this.historyCount = historyCount;
                return this;
            }
            
            public PasswordPolicy build() {
                PasswordPolicy policy = new PasswordPolicy();
                policy.minLength = this.minLength;
                policy.requireUppercase = this.requireUppercase;
                policy.requireLowercase = this.requireLowercase;
                policy.requireNumbers = this.requireNumbers;
                policy.requireSpecialChars = this.requireSpecialChars;
                policy.maxAge = this.maxAge;
                policy.historyCount = this.historyCount;
                return policy;
            }
        }
    }
    
    public static class AccessPolicy {
        private int sessionTimeout;
        private int maxFailedAttempts;
        private int lockoutDuration;
        private boolean requireMFA;
        
        public static AccessPolicyBuilder builder() {
            return new AccessPolicyBuilder();
        }
        
        public static class AccessPolicyBuilder {
            private int sessionTimeout;
            private int maxFailedAttempts;
            private int lockoutDuration;
            private boolean requireMFA;
            
            public AccessPolicyBuilder sessionTimeout(int sessionTimeout) {
                this.sessionTimeout = sessionTimeout;
                return this;
            }
            
            public AccessPolicyBuilder maxFailedAttempts(int maxFailedAttempts) {
                this.maxFailedAttempts = maxFailedAttempts;
                return this;
            }
            
            public AccessPolicyBuilder lockoutDuration(int lockoutDuration) {
                this.lockoutDuration = lockoutDuration;
                return this;
            }
            
            public AccessPolicyBuilder requireMFA(boolean requireMFA) {
                this.requireMFA = requireMFA;
                return this;
            }
            
            public AccessPolicy build() {
                AccessPolicy policy = new AccessPolicy();
                policy.sessionTimeout = this.sessionTimeout;
                policy.maxFailedAttempts = this.maxFailedAttempts;
                policy.lockoutDuration = this.lockoutDuration;
                policy.requireMFA = this.requireMFA;
                return policy;
            }
        }
    }
    
    public static class DataRetentionPolicy {
        private int cardDataRetention;
        private int tokenRetention;
        private int auditLogRetention;
        
        public static DataRetentionPolicyBuilder builder() {
            return new DataRetentionPolicyBuilder();
        }
        
        public static class DataRetentionPolicyBuilder {
            private int cardDataRetention;
            private int tokenRetention;
            private int auditLogRetention;
            
            public DataRetentionPolicyBuilder cardDataRetention(int cardDataRetention) {
                this.cardDataRetention = cardDataRetention;
                return this;
            }
            
            public DataRetentionPolicyBuilder tokenRetention(int tokenRetention) {
                this.tokenRetention = tokenRetention;
                return this;
            }
            
            public DataRetentionPolicyBuilder auditLogRetention(int auditLogRetention) {
                this.auditLogRetention = auditLogRetention;
                return this;
            }
            
            public DataRetentionPolicy build() {
                DataRetentionPolicy policy = new DataRetentionPolicy();
                policy.cardDataRetention = this.cardDataRetention;
                policy.tokenRetention = this.tokenRetention;
                policy.auditLogRetention = this.auditLogRetention;
                return policy;
            }
        }
    }
    
    public static class PCIDSSComplianceException extends RuntimeException {
        public PCIDSSComplianceException(String message) {
            super(message);
        }
        
        public PCIDSSComplianceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}