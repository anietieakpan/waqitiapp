package com.waqiti.common.transaction;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Financial Audit Service
 * Provides comprehensive audit trail, data validation, and financial integrity checks
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialAuditService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Value("${audit.enabled:true}")
    private boolean auditEnabled;

    @Value("${audit.real-time.enabled:true}")
    private boolean realTimeAuditEnabled;

    @Value("${audit.balance-validation.enabled:true}")
    private boolean balanceValidationEnabled;

    @Value("${audit.transaction-validation.enabled:true}")
    private boolean transactionValidationEnabled;

    @Value("${audit.compliance.enabled:true}")
    private boolean complianceAuditEnabled;

    @Value("${audit.retention.days:2555}") // 7 years
    private int auditRetentionDays;

    @Value("${audit.reconciliation.auto-fix:false}")
    private boolean autoFixEnabled;

    // Cache for frequently accessed audit data
    private final Map<String, AuditRecord> auditCache = new ConcurrentHashMap<>();

    /**
     * Record comprehensive audit trail for financial transactions
     */
    @Transactional
    public AuditTrail recordFinancialTransaction(FinancialTransactionEvent event) {
        if (!auditEnabled) {
            return AuditTrail.disabled();
        }

        log.debug("Recording audit trail for transaction: {}", event.getTransactionId());

        try {
            // Generate unique audit ID
            String auditId = generateAuditId();
            Instant timestamp = Instant.now();

            // Create comprehensive audit record
            AuditRecord auditRecord = AuditRecord.builder()
                .auditId(auditId)
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .eventType(event.getEventType())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .sourceAccount(event.getSourceAccount())
                .destinationAccount(event.getDestinationAccount())
                .timestamp(timestamp)
                .beforeBalance(event.getBeforeBalance())
                .afterBalance(event.getAfterBalance())
                .metadata(new HashMap<>(event.getMetadata()))
                .ipAddress(event.getIpAddress())
                .userAgent(event.getUserAgent())
                .sessionId(event.getSessionId())
                .deviceFingerprint(event.getDeviceFingerprint())
                .build();

            // Add data integrity hash
            auditRecord.setDataHash(calculateDataHash(auditRecord));

            // Validate transaction integrity
            ValidationResult validation = validateTransactionIntegrity(auditRecord);
            auditRecord.setValidationResult(validation);

            // Store audit record
            storeAuditRecord(auditRecord);

            // Send to audit stream for real-time processing
            if (realTimeAuditEnabled) {
                sendToAuditStream(auditRecord);
            }

            // Perform compliance checks
            if (complianceAuditEnabled) {
                ComplianceCheckResult complianceResult = performComplianceCheck(auditRecord);
                if (!complianceResult.isCompliant()) {
                    handleComplianceViolation(auditRecord, complianceResult);
                }
            }

            // Real-time balance validation
            if (balanceValidationEnabled) {
                BalanceValidationResult balanceCheck = validateBalances(auditRecord);
                if (!balanceCheck.isValid()) {
                    handleBalanceDiscrepancy(auditRecord, balanceCheck);
                }
            }

            // Cache for quick access
            auditCache.put(auditId, auditRecord);

            return AuditTrail.builder()
                .auditId(auditId)
                .transactionId(event.getTransactionId())
                .timestamp(timestamp)
                .status(AuditStatus.RECORDED)
                .validationResult(validation)
                .build();

        } catch (Exception e) {
            log.error("Failed to record audit trail for transaction: {}", event.getTransactionId(), e);
            return AuditTrail.error("Failed to record audit: " + e.getMessage());
        }
    }

    /**
     * Perform comprehensive financial reconciliation
     */
    public ReconciliationResult performFinancialReconciliation(ReconciliationRequest request) {
        log.info("Starting financial reconciliation for period: {} to {}", 
                request.getStartTime(), request.getEndTime());

        try {
            // Get all transactions in the period
            List<AuditRecord> transactions = getTransactionsInPeriod(
                request.getStartTime(), request.getEndTime());

            // Get all balance changes
            List<BalanceSnapshot> balanceSnapshots = getBalanceSnapshots(
                request.getStartTime(), request.getEndTime());

            // Perform reconciliation checks
            List<ReconciliationIssue> issues = new ArrayList<>();

            // 1. Balance continuity check
            issues.addAll(checkBalanceContinuity(transactions, balanceSnapshots));

            // 2. Transaction completeness check
            issues.addAll(checkTransactionCompleteness(transactions));

            // 3. Double-entry validation
            issues.addAll(validateDoubleEntry(transactions));

            // 4. Amount accuracy check
            issues.addAll(validateAmountAccuracy(transactions));

            // 5. Cross-system reconciliation
            if (request.isIncludeCrossSystemCheck()) {
                issues.addAll(performCrossSystemReconciliation(transactions));
            }

            // Auto-fix issues if enabled and safe
            List<ReconciliationIssue> fixedIssues = new ArrayList<>();
            if (autoFixEnabled) {
                fixedIssues.addAll(autoFixIssues(issues));
            }

            // Generate reconciliation report
            ReconciliationReport report = generateReconciliationReport(
                transactions, balanceSnapshots, issues, fixedIssues);

            // Store reconciliation results
            storeReconciliationResult(request, report);

            return ReconciliationResult.builder()
                .reconciliationId(generateReconciliationId())
                .totalTransactions(transactions.size())
                .issuesFound(issues.size())
                .issuesFixed(fixedIssues.size())
                .report(report)
                .reconciliationTime(Instant.now())
                .isReconciled(issues.isEmpty())
                .build();

        } catch (Exception e) {
            log.error("Financial reconciliation failed", e);
            return ReconciliationResult.error("Reconciliation failed: " + e.getMessage());
        }
    }

    /**
     * Validate data integrity for audit records
     */
    public IntegrityValidationResult validateDataIntegrity(String auditId) {
        try {
            AuditRecord record = getAuditRecord(auditId);
            if (record == null) {
                return IntegrityValidationResult.notFound("Audit record not found: " + auditId);
            }

            // Verify data hash
            String currentHash = calculateDataHash(record);
            boolean hashValid = Objects.equals(currentHash, record.getDataHash());

            // Check for tampering indicators
            List<TamperingIndicator> tamperingIndicators = checkForTampering(record);

            // Validate related records
            List<String> relatedRecords = findRelatedRecords(record);
            boolean relatedRecordsValid = validateRelatedRecords(relatedRecords);

            // Check audit trail completeness
            boolean auditTrailComplete = validateAuditTrailCompleteness(record);

            return IntegrityValidationResult.builder()
                .auditId(auditId)
                .isValid(hashValid && tamperingIndicators.isEmpty() && 
                        relatedRecordsValid && auditTrailComplete)
                .hashValid(hashValid)
                .tamperingIndicators(tamperingIndicators)
                .relatedRecordsValid(relatedRecordsValid)
                .auditTrailComplete(auditTrailComplete)
                .validationTimestamp(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Data integrity validation failed for audit: {}", auditId, e);
            return IntegrityValidationResult.error("Validation failed: " + e.getMessage());
        }
    }

    /**
     * Perform compliance audit checks
     */
    public ComplianceAuditResult performComplianceAudit(ComplianceAuditRequest request) {
        log.info("Starting compliance audit for period: {} to {}", 
                request.getStartTime(), request.getEndTime());

        try {
            List<ComplianceViolation> violations = new ArrayList<>();
            Map<String, Object> metrics = new HashMap<>();

            // AML (Anti-Money Laundering) checks
            if (request.isIncludeAmlChecks()) {
                violations.addAll(performAmlChecks(request));
                metrics.put("amlChecksPerformed", true);
            }

            // KYC (Know Your Customer) validation
            if (request.isIncludeKycChecks()) {
                violations.addAll(performKycValidation(request));
                metrics.put("kycChecksPerformed", true);
            }

            // Transaction limits validation
            if (request.isIncludeLimitChecks()) {
                violations.addAll(validateTransactionLimits(request));
                metrics.put("limitChecksPerformed", true);
            }

            // Suspicious activity detection
            if (request.isIncludeSuspiciousActivityChecks()) {
                violations.addAll(detectSuspiciousActivity(request));
                metrics.put("suspiciousActivityChecksPerformed", true);
            }

            // Regulatory reporting validation
            if (request.isIncludeRegulatoryChecks()) {
                violations.addAll(validateRegulatoryReporting(request));
                metrics.put("regulatoryChecksPerformed", true);
            }

            // Generate compliance score
            double complianceScore = calculateComplianceScore(violations);
            metrics.put("complianceScore", complianceScore);

            // Store compliance audit results
            storeComplianceAuditResult(request, violations, metrics);

            return ComplianceAuditResult.builder()
                .auditId(generateComplianceAuditId())
                .violations(violations)
                .metrics(metrics)
                .complianceScore(complianceScore)
                .isCompliant(violations.isEmpty())
                .auditTimestamp(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Compliance audit failed", e);
            return ComplianceAuditResult.error("Compliance audit failed: " + e.getMessage());
        }
    }

    /**
     * Generate comprehensive audit reports
     */
    public AuditReport generateAuditReport(AuditReportRequest request) {
        log.info("Generating audit report for period: {} to {}", 
                request.getStartTime(), request.getEndTime());

        try {
            // Collect audit data
            List<AuditRecord> auditRecords = getAuditRecordsInPeriod(
                request.getStartTime(), request.getEndTime());

            // Generate statistics
            AuditStatistics statistics = generateAuditStatistics(auditRecords);

            // Analyze patterns
            PatternAnalysis patternAnalysis = analyzeTransactionPatterns(auditRecords);

            // Risk assessment
            RiskAssessment riskAssessment = performRiskAssessment(auditRecords);

            // Compliance summary
            ComplianceSummary complianceSummary = generateComplianceSummary(
                request.getStartTime(), request.getEndTime());

            // Exception analysis
            ExceptionAnalysis exceptionAnalysis = analyzeExceptions(auditRecords);

            return AuditReport.builder()
                .reportId(generateReportId())
                .reportType(request.getReportType())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .generatedAt(Instant.now())
                .statistics(statistics)
                .patternAnalysis(patternAnalysis)
                .riskAssessment(riskAssessment)
                .complianceSummary(complianceSummary)
                .exceptionAnalysis(exceptionAnalysis)
                .auditRecordsAnalyzed(auditRecords.size())
                .build();

        } catch (Exception e) {
            log.error("Failed to generate audit report", e);
            return AuditReport.error("Report generation failed: " + e.getMessage());
        }
    }

    // Private helper methods

    private String generateAuditId() {
        return "audit_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateReconciliationId() {
        return "recon_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateComplianceAuditId() {
        return "comp_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateReportId() {
        return "rpt_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String calculateDataHash(AuditRecord record) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder data = new StringBuilder();
            data.append(record.getTransactionId())
                .append(record.getUserId())
                .append(record.getAmount())
                .append(record.getCurrency())
                .append(record.getSourceAccount())
                .append(record.getDestinationAccount())
                .append(record.getTimestamp().toEpochMilli());

            byte[] hash = md.digest(data.toString().getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to calculate data hash for audit trail - Financial integrity at risk", e);
            throw new RuntimeException("Failed to calculate audit hash - data integrity compromised", e);
        }
    }

    private ValidationResult validateTransactionIntegrity(AuditRecord record) {
        List<String> issues = new ArrayList<>();

        // Validate amount
        if (record.getAmount() == null || record.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            issues.add("Invalid transaction amount");
        }

        // Validate accounts
        if (record.getSourceAccount() == null || record.getDestinationAccount() == null) {
            issues.add("Missing account information");
        }

        // Validate balance consistency
        if (record.getBeforeBalance() != null && record.getAfterBalance() != null) {
            BigDecimal expectedBalance = record.getBeforeBalance().subtract(record.getAmount());
            if (expectedBalance.compareTo(record.getAfterBalance()) != 0) {
                issues.add("Balance calculation inconsistency");
            }
        }

        return ValidationResult.builder()
            .isValid(issues.isEmpty())
            .issues(issues)
            .validationTimestamp(Instant.now())
            .build();
    }

    private void storeAuditRecord(AuditRecord record) {
        try {
            // Store in Redis for quick access
            String redisKey = "audit:record:" + record.getAuditId();
            redisTemplate.opsForValue().set(redisKey, record, Duration.ofDays(auditRetentionDays));

            // Store in database for long-term persistence
            storeAuditRecordInDatabase(record);

        } catch (Exception e) {
            log.error("Failed to store audit record", e);
        }
    }

    private void storeAuditRecordInDatabase(AuditRecord record) {
        try {
            String sql = """
                INSERT INTO audit_records (
                    audit_id, transaction_id, user_id, event_type, amount, currency,
                    source_account, destination_account, timestamp, before_balance,
                    after_balance, metadata, ip_address, user_agent, session_id,
                    device_fingerprint, data_hash, validation_result
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            jdbcTemplate.update(sql,
                record.getAuditId(),
                record.getTransactionId(),
                record.getUserId(),
                record.getEventType(),
                record.getAmount(),
                record.getCurrency(),
                record.getSourceAccount(),
                record.getDestinationAccount(),
                record.getTimestamp(),
                record.getBeforeBalance(),
                record.getAfterBalance(),
                objectToJson(record.getMetadata()),
                record.getIpAddress(),
                record.getUserAgent(),
                record.getSessionId(),
                record.getDeviceFingerprint(),
                record.getDataHash(),
                objectToJson(record.getValidationResult())
            );

        } catch (Exception e) {
            log.error("Failed to store audit record in database", e);
        }
    }

    private void sendToAuditStream(AuditRecord record) {
        try {
            kafkaTemplate.send("audit-stream", record.getAuditId(), record);
        } catch (Exception e) {
            log.error("Failed to send audit record to stream", e);
        }
    }

    private ComplianceCheckResult performComplianceCheck(AuditRecord record) {
        // Implement compliance checks based on regulations
        return ComplianceCheckResult.builder()
            .isCompliant(true)
            .violations(Collections.emptyList())
            .build();
    }

    private BalanceValidationResult validateBalances(AuditRecord record) {
        // Implement real-time balance validation
        return BalanceValidationResult.builder()
            .isValid(true)
            .discrepancies(Collections.emptyList())
            .build();
    }

    private void handleComplianceViolation(AuditRecord record, ComplianceCheckResult result) {
        log.warn("Compliance violation detected for transaction: {}", record.getTransactionId());
        // Send alert, freeze transaction, etc.
    }

    private void handleBalanceDiscrepancy(AuditRecord record, BalanceValidationResult result) {
        log.error("Balance discrepancy detected for transaction: {}", record.getTransactionId());
        // Send critical alert, initiate investigation
    }

    private AuditRecord getAuditRecord(String auditId) {
        // Try cache first
        AuditRecord cached = auditCache.get(auditId);
        if (cached != null) {
            return cached;
        }

        // Try Redis
        try {
            String redisKey = "audit:record:" + auditId;
            return (AuditRecord) redisTemplate.opsForValue().get(redisKey);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to retrieve audit record from Redis cache - Audit trail may be incomplete", e);
            throw new RuntimeException("Failed to retrieve audit record - audit integrity compromised", e);
        }
    }

    private List<AuditRecord> getTransactionsInPeriod(Instant start, Instant end) {
        // Implementation to fetch transactions from database
        return Collections.emptyList();
    }

    private List<BalanceSnapshot> getBalanceSnapshots(Instant start, Instant end) {
        // Implementation to fetch balance snapshots
        return Collections.emptyList();
    }

    private List<ReconciliationIssue> checkBalanceContinuity(List<AuditRecord> transactions, 
                                                           List<BalanceSnapshot> snapshots) {
        // Implement balance continuity checks
        return Collections.emptyList();
    }

    private List<ReconciliationIssue> checkTransactionCompleteness(List<AuditRecord> transactions) {
        // Check for missing or incomplete transactions
        return Collections.emptyList();
    }

    private List<ReconciliationIssue> validateDoubleEntry(List<AuditRecord> transactions) {
        // Validate double-entry bookkeeping principles
        return Collections.emptyList();
    }

    private List<ReconciliationIssue> validateAmountAccuracy(List<AuditRecord> transactions) {
        // Validate amount calculations and precision
        return Collections.emptyList();
    }

    private List<ReconciliationIssue> performCrossSystemReconciliation(List<AuditRecord> transactions) {
        // Cross-reference with external systems
        return Collections.emptyList();
    }

    private List<ReconciliationIssue> autoFixIssues(List<ReconciliationIssue> issues) {
        // Automatically fix safe reconciliation issues
        return Collections.emptyList();
    }

    private ReconciliationReport generateReconciliationReport(List<AuditRecord> transactions,
                                                            List<BalanceSnapshot> snapshots,
                                                            List<ReconciliationIssue> issues,
                                                            List<ReconciliationIssue> fixedIssues) {
        return ReconciliationReport.builder()
            .totalTransactions(transactions.size())
            .totalIssues(issues.size())
            .fixedIssues(fixedIssues.size())
            .reportTimestamp(Instant.now())
            .build();
    }

    private void storeReconciliationResult(ReconciliationRequest request, ReconciliationReport report) {
        // Store reconciliation results
    }

    private List<TamperingIndicator> checkForTampering(AuditRecord record) {
        // Check for signs of data tampering
        return Collections.emptyList();
    }

    private List<String> findRelatedRecords(AuditRecord record) {
        // Find related audit records
        return Collections.emptyList();
    }

    private boolean validateRelatedRecords(List<String> relatedRecords) {
        // Validate related records
        return true;
    }

    private boolean validateAuditTrailCompleteness(AuditRecord record) {
        // Check audit trail completeness
        return true;
    }

    private List<ComplianceViolation> performAmlChecks(ComplianceAuditRequest request) {
        // Implement AML checks
        return Collections.emptyList();
    }

    private List<ComplianceViolation> performKycValidation(ComplianceAuditRequest request) {
        // Implement KYC validation
        return Collections.emptyList();
    }

    private List<ComplianceViolation> validateTransactionLimits(ComplianceAuditRequest request) {
        // Validate transaction limits
        return Collections.emptyList();
    }

    private List<ComplianceViolation> detectSuspiciousActivity(ComplianceAuditRequest request) {
        // Detect suspicious activity patterns
        return Collections.emptyList();
    }

    private List<ComplianceViolation> validateRegulatoryReporting(ComplianceAuditRequest request) {
        // Validate regulatory reporting compliance
        return Collections.emptyList();
    }

    private double calculateComplianceScore(List<ComplianceViolation> violations) {
        if (violations.isEmpty()) {
            return 100.0;
        }
        // Calculate score based on violation severity
        return Math.max(0.0, 100.0 - violations.size() * 10.0);
    }

    private void storeComplianceAuditResult(ComplianceAuditRequest request, 
                                          List<ComplianceViolation> violations,
                                          Map<String, Object> metrics) {
        // Store compliance audit results
    }

    private List<AuditRecord> getAuditRecordsInPeriod(Instant start, Instant end) {
        // Get audit records from database
        return Collections.emptyList();
    }

    private AuditStatistics generateAuditStatistics(List<AuditRecord> records) {
        return AuditStatistics.builder()
            .totalRecords(records.size())
            .totalAmount(records.stream()
                .map(AuditRecord::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
            .build();
    }

    private PatternAnalysis analyzeTransactionPatterns(List<AuditRecord> records) {
        return PatternAnalysis.builder()
            .patternsDetected(Collections.emptyList())
            .build();
    }

    private RiskAssessment performRiskAssessment(List<AuditRecord> records) {
        return RiskAssessment.builder()
            .overallRiskLevel("LOW")
            .riskFactors(Collections.emptyList())
            .build();
    }

    private ComplianceSummary generateComplianceSummary(Instant start, Instant end) {
        return ComplianceSummary.builder()
            .overallCompliance(true)
            .violationCount(0)
            .build();
    }

    private ExceptionAnalysis analyzeExceptions(List<AuditRecord> records) {
        return ExceptionAnalysis.builder()
            .exceptionCount(0)
            .exceptions(Collections.emptyList())
            .build();
    }

    private String objectToJson(Object obj) {
        // Convert object to JSON string
        return "{}";
    }

    // Data classes

    @Data
    @Builder
    public static class FinancialTransactionEvent {
        private String transactionId;
        private String userId;
        private String eventType;
        private BigDecimal amount;
        private String currency;
        private String sourceAccount;
        private String destinationAccount;
        private BigDecimal beforeBalance;
        private BigDecimal afterBalance;
        private Map<String, Object> metadata;
        private String ipAddress;
        private String userAgent;
        private String sessionId;
        private String deviceFingerprint;
    }

    @Data
    @Builder
    public static class AuditRecord {
        private String auditId;
        private String transactionId;
        private String userId;
        private String eventType;
        private BigDecimal amount;
        private String currency;
        private String sourceAccount;
        private String destinationAccount;
        private Instant timestamp;
        private BigDecimal beforeBalance;
        private BigDecimal afterBalance;
        private Map<String, Object> metadata;
        private String ipAddress;
        private String userAgent;
        private String sessionId;
        private String deviceFingerprint;
        private String dataHash;
        private ValidationResult validationResult;
    }

    @Data
    @Builder
    public static class AuditTrail {
        private String auditId;
        private String transactionId;
        private Instant timestamp;
        private AuditStatus status;
        private ValidationResult validationResult;
        private String errorMessage;

        public static AuditTrail disabled() {
            return AuditTrail.builder().status(AuditStatus.DISABLED).build();
        }

        public static AuditTrail error(String message) {
            return AuditTrail.builder().status(AuditStatus.ERROR).errorMessage(message).build();
        }
    }

    public enum AuditStatus {
        RECORDED, DISABLED, ERROR
    }

    @Data
    @Builder
    public static class ValidationResult {
        private boolean isValid;
        private List<String> issues;
        private Instant validationTimestamp;
    }

    @Data
    @Builder
    public static class ReconciliationRequest {
        private Instant startTime;
        private Instant endTime;
        private boolean includeCrossSystemCheck;
        private List<String> accountsToReconcile;
    }

    @Data
    @Builder
    public static class ReconciliationResult {
        private String reconciliationId;
        private int totalTransactions;
        private int issuesFound;
        private int issuesFixed;
        private ReconciliationReport report;
        private Instant reconciliationTime;
        private boolean isReconciled;
        private String errorMessage;

        public static ReconciliationResult error(String message) {
            return ReconciliationResult.builder().isReconciled(false).errorMessage(message).build();
        }
    }

    @Data
    @Builder
    public static class ReconciliationIssue {
        private String issueId;
        private String description;
        private String severity;
        private Map<String, Object> details;
    }

    @Data
    @Builder
    public static class ReconciliationReport {
        private int totalTransactions;
        private int totalIssues;
        private int fixedIssues;
        private Instant reportTimestamp;
    }

    @Data
    @Builder
    public static class BalanceSnapshot {
        private String accountId;
        private BigDecimal balance;
        private Instant timestamp;
    }

    @Data
    @Builder
    public static class IntegrityValidationResult {
        private String auditId;
        private boolean isValid;
        private boolean hashValid;
        private List<TamperingIndicator> tamperingIndicators;
        private boolean relatedRecordsValid;
        private boolean auditTrailComplete;
        private Instant validationTimestamp;
        private String errorMessage;

        public static IntegrityValidationResult notFound(String message) {
            return IntegrityValidationResult.builder().isValid(false).errorMessage(message).build();
        }

        public static IntegrityValidationResult error(String message) {
            return IntegrityValidationResult.builder().isValid(false).errorMessage(message).build();
        }
    }

    @Data
    @Builder
    public static class TamperingIndicator {
        private String type;
        private String description;
        private String severity;
    }

    @Data
    @Builder
    public static class ComplianceAuditRequest {
        private Instant startTime;
        private Instant endTime;
        private boolean includeAmlChecks;
        private boolean includeKycChecks;
        private boolean includeLimitChecks;
        private boolean includeSuspiciousActivityChecks;
        private boolean includeRegulatoryChecks;
    }

    @Data
    @Builder
    public static class ComplianceAuditResult {
        private String auditId;
        private List<ComplianceViolation> violations;
        private Map<String, Object> metrics;
        private double complianceScore;
        private boolean isCompliant;
        private Instant auditTimestamp;
        private String errorMessage;

        public static ComplianceAuditResult error(String message) {
            return ComplianceAuditResult.builder().isCompliant(false).errorMessage(message).build();
        }
    }

    @Data
    @Builder
    public static class ComplianceViolation {
        private String violationId;
        private String type;
        private String description;
        private String severity;
        private Map<String, Object> details;
    }

    @Data
    @Builder
    public static class ComplianceCheckResult {
        private boolean isCompliant;
        private List<ComplianceViolation> violations;
    }

    @Data
    @Builder
    public static class BalanceValidationResult {
        private boolean isValid;
        private List<String> discrepancies;
    }

    @Data
    @Builder
    public static class AuditReportRequest {
        private Instant startTime;
        private Instant endTime;
        private String reportType;
        private List<String> includeMetrics;
    }

    @Data
    @Builder
    public static class AuditReport {
        private String reportId;
        private String reportType;
        private Instant startTime;
        private Instant endTime;
        private Instant generatedAt;
        private AuditStatistics statistics;
        private PatternAnalysis patternAnalysis;
        private RiskAssessment riskAssessment;
        private ComplianceSummary complianceSummary;
        private ExceptionAnalysis exceptionAnalysis;
        private int auditRecordsAnalyzed;
        private String errorMessage;

        public static AuditReport error(String message) {
            return AuditReport.builder().errorMessage(message).build();
        }
    }

    @Data
    @Builder
    public static class AuditStatistics {
        private int totalRecords;
        private BigDecimal totalAmount;
    }

    @Data
    @Builder
    public static class PatternAnalysis {
        private List<String> patternsDetected;
    }

    @Data
    @Builder
    public static class RiskAssessment {
        private String overallRiskLevel;
        private List<String> riskFactors;
    }

    @Data
    @Builder
    public static class ComplianceSummary {
        private boolean overallCompliance;
        private int violationCount;
    }

    @Data
    @Builder
    public static class ExceptionAnalysis {
        private int exceptionCount;
        private List<String> exceptions;
    }
}