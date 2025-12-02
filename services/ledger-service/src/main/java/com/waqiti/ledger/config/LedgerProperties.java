package com.waqiti.ledger.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Comprehensive configuration properties for the Ledger Service
 * Provides centralized configuration management with validation and defaults
 */
@Slf4j
@Data
@Validated
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "ledger")
public class LedgerProperties {
    
    @Valid
    @NotNull
    private Audit audit = new Audit();
    
    @Valid
    @NotNull
    private ChartOfAccounts chartOfAccounts = new ChartOfAccounts();
    
    @Valid
    @NotNull
    private JournalEntry journalEntry = new JournalEntry();
    
    @Valid
    @NotNull
    private Reconciliation reconciliation = new Reconciliation();
    
    @Valid
    @NotNull
    private Accounting accounting = new Accounting();
    
    @Valid
    @NotNull
    private Reports reports = new Reports();
    
    @Valid
    @NotNull
    private Performance performance = new Performance();
    
    @Valid
    @NotNull
    private Security security = new Security();
    
    @Valid
    @NotNull
    private Integration integration = new Integration();
    
    /**
     * Audit configuration for tracking all ledger changes
     */
    @Data
    public static class Audit {
        @NotNull
        private Boolean logAllChanges = true;
        
        @Min(1)
        @Max(10000)
        private Integer retentionDays = 2555; // ~7 years
        
        @NotBlank
        private String auditTablePrefix = "audit_";
        
        @NotNull
        private Boolean encryptSensitiveData = true;
        
        @NotNull
        private Boolean includeBeforeAfterValues = true;
        
        @Size(min = 1)
        private List<String> excludedFields = Arrays.asList("password", "secret", "token", "key");
        
        @NotNull
        private CompressionType compressionType = CompressionType.GZIP;
        
        @Min(1)
        @Max(1000)
        private Integer batchSize = 100;
        
        @NotNull
        private Boolean asyncProcessing = true;
        
        @Min(1)
        @Max(100)
        private Integer threadPoolSize = 10;
        
        public enum CompressionType {
            NONE, GZIP, ZSTD, LZ4, SNAPPY
        }
        
        public boolean shouldAuditField(String fieldName) {
            if (fieldName == null) return false;
            String lowerFieldName = fieldName.toLowerCase();
            return excludedFields.stream()
                .noneMatch(excluded -> lowerFieldName.contains(excluded.toLowerCase()));
        }
        
        public long getRetentionMillis() {
            return TimeUnit.DAYS.toMillis(retentionDays);
        }
    }
    
    /**
     * Chart of Accounts configuration for account structure management
     */
    @Data
    public static class ChartOfAccounts {
        @NotNull
        private Boolean enforceBalanceTypes = true;
        
        @Min(1)
        @Max(10)
        private Integer maxDepth = 5;
        
        @NotBlank
        @Pattern(regexp = "^[\\\\d\\-{}\\[\\]().*+?^$|]+$")
        private String accountNumberFormat = "\\d{4}-\\d{3}-\\d{3}";
        
        @NotNull
        private Boolean allowDynamicAccountCreation = false;
        
        @NotNull
        private ValidationMode validationMode = ValidationMode.STRICT;
        
        @Size(min = 1)
        private List<AccountType> supportedAccountTypes = Arrays.asList(
            AccountType.ASSET, AccountType.LIABILITY, AccountType.EQUITY,
            AccountType.REVENUE, AccountType.EXPENSE
        );
        
        @NotNull
        private Boolean requireParentChildTypeConsistency = true;
        
        @Min(1)
        @Max(100000)
        private Integer maxAccountsPerType = 10000;
        
        @NotNull
        private Map<AccountType, String> accountPrefixes = Map.of(
            AccountType.ASSET, "1",
            AccountType.LIABILITY, "2",
            AccountType.EQUITY, "3",
            AccountType.REVENUE, "4",
            AccountType.EXPENSE, "5"
        );
        
        public enum ValidationMode {
            STRICT, MODERATE, LENIENT
        }
        
        public enum AccountType {
            ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE, CONTRA_ASSET, 
            CONTRA_LIABILITY, CONTRA_EQUITY, CONTRA_REVENUE, CONTRA_EXPENSE
        }
        
        public boolean isValidAccountNumber(String accountNumber) {
            if (accountNumber == null || accountNumber.isEmpty()) return false;
            try {
                return Pattern.compile(accountNumberFormat).matcher(accountNumber).matches();
            } catch (PatternSyntaxException e) {
                log.error("Invalid account number format pattern: {}", accountNumberFormat, e);
                return false;
            }
        }
        
        public String getAccountPrefix(AccountType type) {
            return accountPrefixes.getOrDefault(type, "9");
        }
    }
    
    /**
     * Journal Entry configuration for transaction recording
     */
    @Data
    public static class JournalEntry {
        @NotNull
        private Boolean requireApproval = true;
        
        @Min(0)
        @Max(365)
        private Integer autoReverseDays = 30;
        
        @NotNull
        private Boolean allowBackdating = false;
        
        @Min(0)
        @Max(365)
        private Integer maxBackdatingDays = 7;
        
        @NotNull
        private Boolean requireAttachments = false;
        
        @Min(1)
        @Max(100)
        private Integer maxAttachmentsPerEntry = 10;
        
        @Min(1)
        @Max(52428800) // 50MB
        private Long maxAttachmentSizeBytes = 10485760L; // 10MB
        
        @NotNull
        private Boolean enforceDoubleEntry = true;
        
        @NotNull
        private Boolean allowZeroAmountEntries = false;
        
        @DecimalMin("0.01")
        @DecimalMax("1000000000")
        private BigDecimal maxTransactionAmount = new BigDecimal("10000000");
        
        @Min(2)
        @Max(1000)
        private Integer maxLineItemsPerEntry = 100;
        
        @NotNull
        private ApprovalMode approvalMode = ApprovalMode.HIERARCHICAL;
        
        @Min(1)
        @Max(10)
        private Integer requiredApprovals = 2;
        
        @NotNull
        private Boolean notifyOnApproval = true;
        
        public enum ApprovalMode {
            NONE, SINGLE, HIERARCHICAL, CONSENSUS, WEIGHTED
        }
        
        public boolean isBackdatingAllowed(Date entryDate) {
            if (!allowBackdating) return false;
            long daysDiff = TimeUnit.MILLISECONDS.toDays(
                System.currentTimeMillis() - entryDate.getTime()
            );
            return daysDiff <= maxBackdatingDays;
        }
        
        public boolean isAmountValid(BigDecimal amount) {
            if (amount == null) return false;
            if (amount.compareTo(BigDecimal.ZERO) == 0 && !allowZeroAmountEntries) return false;
            return amount.abs().compareTo(maxTransactionAmount) <= 0;
        }
    }
    
    /**
     * Reconciliation configuration for account matching and verification
     */
    @Data
    public static class Reconciliation {
        @Min(1)
        @Max(365)
        private Integer maxAgeDays = 90;
        
        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("1000.00")
        private BigDecimal autoMatchThreshold = new BigDecimal("0.01");
        
        @NotNull
        private MatchingStrategy matchingStrategy = MatchingStrategy.FUZZY;
        
        @Min(0.0)
        @Max(1.0)
        private Double fuzzyMatchConfidence = 0.95;
        
        @NotNull
        private Boolean requireManualApproval = true;
        
        @Min(1)
        @Max(10000)
        private Integer batchSize = 1000;
        
        @NotNull
        private Boolean generateReports = true;
        
        @NotNull
        private List<ReconciliationField> matchingFields = Arrays.asList(
            ReconciliationField.AMOUNT,
            ReconciliationField.DATE,
            ReconciliationField.REFERENCE
        );
        
        @Min(1)
        @Max(100)
        private Integer maxRetryAttempts = 3;
        
        @NotNull
        private Duration retryDelay = Duration.ofMinutes(5);
        
        @NotNull
        private Boolean notifyOnMismatch = true;
        
        @NotNull
        private Boolean autoCreateAdjustments = false;
        
        public enum MatchingStrategy {
            EXACT, FUZZY, RULE_BASED, ML_BASED, HYBRID
        }
        
        public enum ReconciliationField {
            AMOUNT, DATE, REFERENCE, DESCRIPTION, ACCOUNT_NUMBER,
            TRANSACTION_ID, CUSTOMER_ID, INVOICE_NUMBER
        }
        
        public boolean isWithinAgeLimit(Date transactionDate) {
            long daysDiff = TimeUnit.MILLISECONDS.toDays(
                System.currentTimeMillis() - transactionDate.getTime()
            );
            return daysDiff <= maxAgeDays;
        }
        
        public boolean isWithinThreshold(BigDecimal difference) {
            return difference.abs().compareTo(autoMatchThreshold) <= 0;
        }
    }
    
    /**
     * Accounting configuration for financial calculations and reporting
     */
    @Data
    public static class Accounting {
        @Valid
        @NotNull
        private Currency currency = new Currency();
        
        @Valid
        @NotNull
        private FiscalYear fiscalYear = new FiscalYear();
        
        @Valid
        @NotNull
        private TaxConfiguration tax = new TaxConfiguration();
        
        @NotNull
        private RoundingMode roundingMode = RoundingMode.HALF_UP;
        
        @Min(0)
        @Max(10)
        private Integer decimalPlaces = 2;
        
        @NotNull
        private Boolean useAccountingPeriods = true;
        
        @NotNull
        private PeriodClosureMode periodClosureMode = PeriodClosureMode.SOFT;
        
        public enum RoundingMode {
            UP, DOWN, HALF_UP, HALF_DOWN, HALF_EVEN, CEILING, FLOOR
        }
        
        public enum PeriodClosureMode {
            SOFT, HARD, PROVISIONAL
        }
        
        @Data
        public static class Currency {
            @NotEmpty
            @Size(min = 1, max = 50)
            private List<String> supported = Arrays.asList("USD", "EUR", "GBP", "NGN", "KES", "ZAR");
            
            @NotBlank
            @Size(min = 3, max = 3)
            private String defaultCurrency = "USD";
            
            @NotBlank
            private String exchangeRateProvider = "ECB";
            
            @NotNull
            private Boolean cacheExchangeRates = true;
            
            @Min(1)
            @Max(1440)
            private Integer cacheExpirationMinutes = 60;
            
            @NotNull
            private Boolean allowManualRates = true;
            
            @Min(2)
            @Max(10)
            private Integer exchangeRateScale = 6;
            
            @NotNull
            private Map<String, BigDecimal> fixedRates = new HashMap<>();
            
            public boolean isSupportedCurrency(String currencyCode) {
                return supported.contains(currencyCode);
            }
            
            public BigDecimal getFixedRate(String fromCurrency, String toCurrency) {
                String key = fromCurrency + "_" + toCurrency;
                return fixedRates.get(key);
            }
        }
        
        @Data
        public static class FiscalYear {
            @Min(1)
            @Max(12)
            private Integer startMonth = 1;
            
            @Min(1)
            @Max(31)
            private Integer startDay = 1;
            
            @NotNull
            private Boolean useCalendarYear = true;
            
            @NotNull
            private List<String> periods = Arrays.asList(
                "Q1", "Q2", "Q3", "Q4"
            );
            
            @NotNull
            private Boolean allowCrossYearTransactions = false;
            
            public boolean isInCurrentFiscalYear(Date date) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                int month = cal.get(Calendar.MONTH) + 1;
                int day = cal.get(Calendar.DAY_OF_MONTH);
                
                if (useCalendarYear) {
                    return cal.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR);
                }
                
                // Custom fiscal year logic
                if (month > startMonth || (month == startMonth && day >= startDay)) {
                    return true;
                }
                return false;
            }
        }
        
        @Data
        public static class TaxConfiguration {
            @NotNull
            private Boolean enabled = true;
            
            @NotNull
            private Map<String, BigDecimal> taxRates = Map.of(
                "VAT", new BigDecimal("0.15"),
                "GST", new BigDecimal("0.10"),
                "SALES", new BigDecimal("0.08")
            );
            
            @NotNull
            private Boolean autoCalculate = true;
            
            @NotNull
            private TaxMethod defaultMethod = TaxMethod.EXCLUSIVE;
            
            @NotNull
            private Boolean trackTaxCredits = true;
            
            public enum TaxMethod {
                INCLUSIVE, EXCLUSIVE, EXEMPT
            }
            
            public BigDecimal calculateTax(BigDecimal amount, String taxType) {
                BigDecimal rate = taxRates.getOrDefault(taxType, BigDecimal.ZERO);
                return amount.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP);
            }
        }
    }
    
    /**
     * Reports configuration for financial reporting
     */
    @Data
    public static class Reports {
        @Min(1)
        @Max(1000000)
        private Integer maxExportRows = 100000;
        
        @NotNull
        private Boolean asyncGeneration = true;
        
        @NotNull
        private List<ExportFormat> supportedFormats = Arrays.asList(
            ExportFormat.PDF, ExportFormat.EXCEL, ExportFormat.CSV, ExportFormat.JSON
        );
        
        @NotNull
        private Boolean compressLargeReports = true;
        
        @Min(1048576) // 1MB
        @Max(104857600) // 100MB
        private Long compressionThresholdBytes = 10485760L; // 10MB
        
        @NotNull
        private Boolean watermarkEnabled = true;
        
        @NotBlank
        private String watermarkText = "CONFIDENTIAL";
        
        @Min(1)
        @Max(365)
        private Integer retentionDays = 90;
        
        @NotNull
        private Boolean encryptExports = true;
        
        @NotNull
        private Map<ReportType, Schedule> scheduledReports = new HashMap<>();
        
        @Min(1)
        @Max(100)
        private Integer maxConcurrentReports = 10;
        
        @NotNull
        private Duration generationTimeout = Duration.ofMinutes(30);
        
        public enum ExportFormat {
            PDF, EXCEL, CSV, JSON, XML, HTML
        }
        
        public enum ReportType {
            BALANCE_SHEET, INCOME_STATEMENT, CASH_FLOW, TRIAL_BALANCE,
            GENERAL_LEDGER, AGED_RECEIVABLES, AGED_PAYABLES, TAX_REPORT
        }
        
        @Data
        public static class Schedule {
            private String cronExpression;
            private List<String> recipients;
            private ExportFormat format;
            private Boolean enabled;
        }
        
        public boolean shouldCompress(long fileSize) {
            return compressLargeReports && fileSize > compressionThresholdBytes;
        }
    }
    
    /**
     * Performance configuration for optimization
     */
    @Data
    public static class Performance {
        @Min(1)
        @Max(10000)
        private Integer batchSize = 500;
        
        @NotNull
        private Boolean enableCaching = true;
        
        @Min(1)
        @Max(1440)
        private Integer cacheExpirationMinutes = 15;
        
        @NotNull
        private Boolean useConnectionPooling = true;
        
        @Min(5)
        @Max(100)
        private Integer connectionPoolSize = 20;
        
        @NotNull
        private Boolean enableQueryOptimization = true;
        
        @Min(100)
        @Max(60000)
        private Integer queryTimeoutMillis = 5000;
        
        @NotNull
        private Boolean enableLazyLoading = true;
        
        @Min(1)
        @Max(100)
        private Integer maxConcurrentTransactions = 50;
        
        @NotNull
        private Duration lockTimeout = Duration.ofSeconds(30);
        
        @NotNull
        private Boolean enableMetrics = true;
        
        public boolean shouldBatch(int itemCount) {
            return itemCount > batchSize;
        }
        
        public int calculateBatchCount(int totalItems) {
            return (int) Math.ceil((double) totalItems / batchSize);
        }
    }
    
    /**
     * Security configuration for ledger operations
     */
    @Data
    public static class Security {
        @NotNull
        private Boolean enableEncryption = true;
        
        @NotBlank
        private String encryptionAlgorithm = "AES-256-GCM";
        
        @NotNull
        private Boolean enforceSegregationOfDuties = true;
        
        @NotNull
        private Boolean requireDigitalSignatures = false;
        
        @NotNull
        private Boolean enableAuditLogging = true;
        
        @NotNull
        private Boolean maskSensitiveData = true;
        
        @NotEmpty
        private List<String> sensitiveFields = Arrays.asList(
            "accountNumber", "ssn", "taxId", "bankAccount"
        );
        
        @Min(1)
        @Max(10)
        private Integer maxFailedAttempts = 3;
        
        @NotNull
        private Duration lockoutDuration = Duration.ofMinutes(30);
        
        @NotNull
        private Boolean requireMfa = false;
        
        @NotNull
        private Boolean enableIpWhitelisting = false;
        
        @NotEmpty
        private List<String> whitelistedIps = Arrays.asList("127.0.0.1", "::1");
        
        public String maskField(String fieldName, String value) {
            if (!maskSensitiveData || value == null) return value;
            if (!sensitiveFields.contains(fieldName)) return value;
            
            int length = value.length();
            if (length <= 4) return "****";
            return value.substring(0, 2) + "*".repeat(length - 4) + value.substring(length - 2);
        }
        
        public boolean isIpWhitelisted(String ipAddress) {
            if (!enableIpWhitelisting) return true;
            return whitelistedIps.contains(ipAddress);
        }
    }
    
    /**
     * Integration configuration for external systems
     */
    @Data
    public static class Integration {
        @NotNull
        private Boolean enableWebhooks = true;
        
        @NotEmpty
        private Map<String, WebhookConfig> webhooks = new HashMap<>();
        
        @NotNull
        private Boolean enableEventStreaming = true;
        
        @NotBlank
        private String eventTopic = "ledger-events";
        
        @Min(1)
        @Max(10)
        private Integer maxRetries = 3;
        
        @NotNull
        private Duration retryBackoff = Duration.ofSeconds(5);
        
        @NotNull
        private Boolean enableCircuitBreaker = true;
        
        @Min(1)
        @Max(100)
        private Integer circuitBreakerThreshold = 5;
        
        @NotNull
        private Duration circuitBreakerTimeout = Duration.ofMinutes(1);
        
        @NotNull
        private Map<String, ApiEndpoint> externalApis = new HashMap<>();
        
        @Data
        public static class WebhookConfig {
            private String url;
            private String secret;
            private List<String> events;
            private Boolean enabled;
            private Map<String, String> headers;
            
            public boolean shouldTrigger(String eventType) {
                return enabled && events.contains(eventType);
            }
        }
        
        @Data
        public static class ApiEndpoint {
            private String baseUrl;
            private Integer timeout;
            private Map<String, String> defaultHeaders;
            private Boolean useAuth;
            private String authType;
            private Map<String, String> authConfig;
        }
    }
    
    /**
     * Validate configuration on startup
     */
    @PostConstruct
    public void validateConfiguration() {
        log.info("Validating Ledger Service configuration...");
        
        // Validate account number format
        try {
            Pattern.compile(chartOfAccounts.getAccountNumberFormat());
        } catch (PatternSyntaxException e) {
            log.error("Invalid account number format pattern: {}", chartOfAccounts.getAccountNumberFormat());
            throw new IllegalArgumentException("Invalid account number format pattern", e);
        }
        
        // Validate currency configuration
        if (!accounting.getCurrency().getSupported().contains(accounting.getCurrency().getDefaultCurrency())) {
            throw new IllegalArgumentException("Default currency must be in the list of supported currencies");
        }
        
        // Validate reconciliation threshold
        if (reconciliation.getAutoMatchThreshold().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Auto-match threshold cannot be negative");
        }
        
        // Validate journal entry configuration
        if (journalEntry.getRequiredApprovals() > 0 && !journalEntry.getRequireApproval()) {
            log.warn("Required approvals set but approval is not required - enabling approval requirement");
            journalEntry.setRequireApproval(true);
        }
        
        // Validate performance settings
        if (performance.getConnectionPoolSize() < performance.getMaxConcurrentTransactions()) {
            log.warn("Connection pool size is less than max concurrent transactions - adjusting pool size");
            performance.setConnectionPoolSize(performance.getMaxConcurrentTransactions() + 10);
        }
        
        log.info("Ledger Service configuration validation completed successfully");
        logConfigurationSummary();
    }
    
    /**
     * Log configuration summary for visibility
     */
    private void logConfigurationSummary() {
        log.info("=== Ledger Service Configuration Summary ===");
        log.info("Audit: retention={} days, encryption={}, async={}", 
            audit.getRetentionDays(), audit.getEncryptSensitiveData(), audit.getAsyncProcessing());
        log.info("Chart of Accounts: maxDepth={}, enforceTypes={}, format={}", 
            chartOfAccounts.getMaxDepth(), chartOfAccounts.getEnforceBalanceTypes(), 
            chartOfAccounts.getAccountNumberFormat());
        log.info("Journal Entry: requireApproval={}, maxAmount={}, approvals={}", 
            journalEntry.getRequireApproval(), journalEntry.getMaxTransactionAmount(), 
            journalEntry.getRequiredApprovals());
        log.info("Reconciliation: maxAge={} days, threshold={}, strategy={}", 
            reconciliation.getMaxAgeDays(), reconciliation.getAutoMatchThreshold(), 
            reconciliation.getMatchingStrategy());
        log.info("Currency: default={}, supported={}, provider={}", 
            accounting.getCurrency().getDefaultCurrency(), 
            accounting.getCurrency().getSupported().size(), 
            accounting.getCurrency().getExchangeRateProvider());
        log.info("Reports: maxRows={}, async={}, formats={}", 
            reports.getMaxExportRows(), reports.getAsyncGeneration(), 
            reports.getSupportedFormats());
        log.info("Performance: batchSize={}, caching={}, poolSize={}", 
            performance.getBatchSize(), performance.getEnableCaching(), 
            performance.getConnectionPoolSize());
        log.info("Security: encryption={}, mfa={}, ipWhitelist={}", 
            security.getEnableEncryption(), security.getRequireMfa(), 
            security.getEnableIpWhitelisting());
        log.info("Integration: webhooks={}, streaming={}, circuitBreaker={}", 
            integration.getEnableWebhooks(), integration.getEnableEventStreaming(), 
            integration.getEnableCircuitBreaker());
        log.info("==========================================");
    }
}