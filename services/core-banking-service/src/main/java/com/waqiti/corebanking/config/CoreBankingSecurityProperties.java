package com.waqiti.corebanking.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

/**
 * Comprehensive Core Banking Security Configuration Properties
 * 
 * Provides complete security settings for core banking operations including:
 * - Transaction limits and controls
 * - Role-based access control
 * - Feature flags for security features
 * - Verification thresholds
 * - Advanced security configurations
 * - Authentication and authorization settings
 * 
 * @author Waqiti Core Banking Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Slf4j
@Data
@Validated
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "core-banking.security")
public class CoreBankingSecurityProperties {
    
    @NotNull
    private Boolean enableEncryption = true;
    
    @NotBlank
    private String encryptionAlgorithm = "AES-256-GCM";
    
    @NotNull
    private Boolean enableAuditLogging = true;
    
    @Min(300)
    @Max(86400)
    private Integer sessionTimeout = 1800; // 30 minutes
    
    @Valid
    @NotNull
    private LimitsConfig limits = new LimitsConfig();
    
    @Valid
    @NotNull
    private RequiredRolesConfig requiredRoles = new RequiredRolesConfig();
    
    @Valid
    @NotNull
    private FeaturesConfig features = new FeaturesConfig();
    
    @Valid
    @NotNull
    private VerificationThresholdsConfig verificationThresholds = new VerificationThresholdsConfig();
    
    @Valid
    @NotNull
    private AdvancedSecurityConfig advancedSecurity = new AdvancedSecurityConfig();
    
    @Valid
    @NotNull
    private AuthenticationConfig authentication = new AuthenticationConfig();
    
    /**
     * Transaction limits and controls configuration
     */
    @Data
    public static class LimitsConfig {
        @DecimalMin("0.01")
        @DecimalMax("999999999.99")
        private BigDecimal maxTransactionAmount = new BigDecimal("1000000.00");
        
        @DecimalMin("0.01")
        @DecimalMax("9999999999.99")
        private BigDecimal maxDailyTransactionVolume = new BigDecimal("10000000.00");
        
        @Min(1)
        @Max(10000)
        private Integer transactionBatchSize = 1000;
        
        @Min(1)
        @Max(100)
        private Integer maxAccountsPerCustomer = 10;
    }
    
    /**
     * Required roles configuration for different operations
     */
    @Data
    public static class RequiredRolesConfig {
        @NotBlank
        private String transactionCreate = "BANKING_USER,TRANSACTION_PROCESSOR";
        
        @NotBlank
        private String transactionRead = "BANKING_USER,BANKING_VIEWER";
        
        @NotBlank
        private String accountCreate = "BANKING_USER,ACCOUNT_MANAGER";
        
        @NotBlank
        private String accountRead = "BANKING_USER,BANKING_VIEWER";
        
        @NotBlank
        private String accountManage = "ACCOUNT_MANAGER,BANKING_ADMIN";
        
        @NotBlank
        private String ledgerRead = "LEDGER_VIEWER,BANKING_ADMIN";
        
        @NotBlank
        private String ledgerWrite = "LEDGER_MANAGER,BANKING_ADMIN";
        
        @Valid
        @NotNull
        private ScopesConfig scopes = new ScopesConfig();
        
        /**
         * Get roles as list for a given operation
         */
        public List<String> getRolesForOperation(String operation) {
            String roles = switch (operation.toLowerCase()) {
                case "transaction-create" -> transactionCreate;
                case "transaction-read" -> transactionRead;
                case "account-create" -> accountCreate;
                case "account-read" -> accountRead;
                case "account-manage" -> accountManage;
                case "ledger-read" -> ledgerRead;
                case "ledger-write" -> ledgerWrite;
                default -> "";
            };
            return roles.isEmpty() ? Collections.emptyList() : 
                   Arrays.asList(roles.split(","));
        }
    }
    
    /**
     * OAuth2 scopes configuration
     */
    @Data
    public static class ScopesConfig {
        @NotBlank
        private String transactionRead = "banking:transaction:read";
        
        @NotBlank
        private String ledgerRead = "banking:ledger:read";
        
        @NotBlank
        private String reconciliation = "banking:reconciliation";
        
        @NotBlank
        private String accountRead = "banking:account:read";
        
        @NotBlank
        private String feeCalculation = "banking:fee:calculate";
        
        @NotBlank
        private String balanceRead = "banking:balance:read";
        
        @NotBlank
        private String admin = "banking:admin";
        
        @NotBlank
        private String ledgerWrite = "banking:ledger:write";
        
        /**
         * Get scope for operation
         */
        public String getScopeForOperation(String operation) {
            return switch (operation.toLowerCase()) {
                case "transaction-read" -> transactionRead;
                case "ledger-read" -> ledgerRead;
                case "reconciliation" -> reconciliation;
                case "account-read" -> accountRead;
                case "fee-calculation" -> feeCalculation;
                case "balance-read" -> balanceRead;
                case "admin" -> admin;
                case "ledger-write" -> ledgerWrite;
                default -> "";
            };
        }
    }
    
    /**
     * Security features configuration
     */
    @Data
    public static class FeaturesConfig {
        @NotNull
        private Boolean transactionValidationEnabled = true;
        
        @NotNull
        private Boolean balanceVerificationEnabled = true;
        
        @NotNull
        private Boolean interestCalculationEnabled = true;
        
        @NotNull
        private Boolean feeCalculationEnabled = true;
        
        @NotNull
        private Boolean regulatoryReportingEnabled = true;
        
        @NotNull
        private Boolean doubleEntryValidationEnabled = true;
    }
    
    /**
     * Verification thresholds configuration
     */
    @Data
    public static class VerificationThresholdsConfig {
        @DecimalMin("0.01")
        @DecimalMax("999999999.99")
        private BigDecimal highValueTransaction = new BigDecimal("50000.00");
        
        @Min(1)
        @Max(1000)
        private Integer suspiciousTransactionPattern = 10;
        
        @DecimalMin("0.01")
        @DecimalMax("999999999.99")
        private BigDecimal largeBalanceTransfer = new BigDecimal("100000.00");
        
        @NotNull
        private Boolean dormantAccountActivation = true;
    }
    
    /**
     * Advanced security features configuration
     */
    @Data
    public static class AdvancedSecurityConfig {
        @NotNull
        private Boolean requireDualAuthorizationHighValue = true;
        
        @NotNull
        private Boolean requireManagerApprovalSettlements = true;
        
        @NotNull
        private Boolean automaticFraudDetection = true;
        
        @NotNull
        private Boolean realTimeBalanceMonitoring = true;
        
        @NotNull
        private Boolean transactionPatternAnalysis = true;
        
        @NotNull
        private Boolean regulatoryComplianceMonitoring = true;
    }
    
    /**
     * Authentication configuration
     */
    @Data
    public static class AuthenticationConfig {
        @NotNull
        private Boolean userResourceRoleMappings = true;
        
        @NotNull
        private Set<String> requiredAuthorities = new HashSet<>(Arrays.asList(
            "BANKING_USER", "ACCOUNT_MANAGER", "TRANSACTION_PROCESSOR"
        ));
        
        @NotNull
        private Map<String, String> roleHierarchy = new HashMap<>();
        
        /**
         * Check if user has required authority
         */
        public boolean hasRequiredAuthority(String authority) {
            return requiredAuthorities.contains(authority);
        }
        
        /**
         * Get role hierarchy for role
         */
        public String getHierarchyForRole(String role) {
            return roleHierarchy.getOrDefault(role, role);
        }
    }
    
    /**
     * Validate configuration on startup
     */
    @PostConstruct
    public void validateConfiguration() {
        log.info("Validating Core Banking Security configuration...");
        
        // Validate transaction limits
        if (limits.getMaxTransactionAmount().compareTo(limits.getMaxDailyTransactionVolume()) > 0) {
            log.warn("Max transaction amount exceeds daily volume limit");
        }
        
        // Validate verification thresholds
        if (verificationThresholds.getHighValueTransaction()
                .compareTo(verificationThresholds.getLargeBalanceTransfer()) > 0) {
            log.warn("High value transaction threshold exceeds large balance transfer threshold");
        }
        
        // Initialize role hierarchy if empty
        if (authentication.getRoleHierarchy().isEmpty()) {
            authentication.getRoleHierarchy().putAll(Map.of(
                "BANKING_ADMIN", "BANKING_MANAGER > BANKING_USER",
                "BANKING_MANAGER", "BANKING_USER",
                "ACCOUNT_MANAGER", "BANKING_USER",
                "TRANSACTION_PROCESSOR", "BANKING_USER",
                "LEDGER_MANAGER", "LEDGER_VIEWER"
            ));
        }
        
        log.info("Core Banking Security configuration validation completed successfully");
        logConfigurationSummary();
    }
    
    private void logConfigurationSummary() {
        log.info("=== Core Banking Security Configuration Summary ===");
        log.info("Encryption: enabled={}, algorithm={}", enableEncryption, encryptionAlgorithm);
        log.info("Session timeout: {} seconds", sessionTimeout);
        log.info("Max transaction amount: {}", limits.getMaxTransactionAmount());
        log.info("Max daily volume: {}", limits.getMaxDailyTransactionVolume());
        log.info("High value threshold: {}", verificationThresholds.getHighValueTransaction());
        log.info("Transaction validation: {}", features.getTransactionValidationEnabled());
        log.info("Balance verification: {}", features.getBalanceVerificationEnabled());
        log.info("Fraud detection: {}", advancedSecurity.getAutomaticFraudDetection());
        log.info("Dual authorization for high value: {}", advancedSecurity.getRequireDualAuthorizationHighValue());
        log.info("Required authorities: {}", authentication.getRequiredAuthorities());
        log.info("=================================================");
    }
    
    /**
     * Check if transaction requires additional verification
     */
    public boolean requiresAdditionalVerification(BigDecimal amount) {
        return amount.compareTo(verificationThresholds.getHighValueTransaction()) >= 0;
    }
    
    /**
     * Check if transaction requires dual authorization
     */
    public boolean requiresDualAuthorization(BigDecimal amount) {
        return advancedSecurity.getRequireDualAuthorizationHighValue() && 
               requiresAdditionalVerification(amount);
    }
    
    /**
     * Check if amount is within daily limit
     */
    public boolean isWithinDailyLimit(BigDecimal amount, BigDecimal currentDailyVolume) {
        BigDecimal totalVolume = currentDailyVolume.add(amount);
        return totalVolume.compareTo(limits.getMaxDailyTransactionVolume()) <= 0;
    }
    
    /**
     * Check if amount is within transaction limit
     */
    public boolean isWithinTransactionLimit(BigDecimal amount) {
        return amount.compareTo(limits.getMaxTransactionAmount()) <= 0;
    }
    
    /**
     * Get session timeout as Duration
     */
    public Duration getSessionTimeoutDuration() {
        return Duration.ofSeconds(sessionTimeout);
    }
}