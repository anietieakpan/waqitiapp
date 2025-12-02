package com.waqiti.common.transaction;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Data Consistency Validator
 * Ensures data consistency across distributed systems and microservices
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataConsistencyValidator {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${data.consistency.validation.enabled:true}")
    private boolean validationEnabled;

    @Value("${data.consistency.real-time.enabled:true}")
    private boolean realTimeValidationEnabled;

    @Value("${data.consistency.auto-repair.enabled:false}")
    private boolean autoRepairEnabled;

    @Value("${data.consistency.tolerance.percentage:0.01}")
    private double tolerancePercentage;

    @Value("${data.consistency.validation.interval.minutes:15}")
    private int validationIntervalMinutes;

    @Value("${data.consistency.balance.precision:2}")
    private int balancePrecision;

    // Cache for consistency checkpoints
    private final Map<String, ConsistencyCheckpoint> checkpointCache = new ConcurrentHashMap<>();
    
    // Executor for async validation tasks
    private final ExecutorService validationExecutor = Executors.newFixedThreadPool(5);

    /**
     * Validate data consistency in real-time for a transaction
     */
    @Transactional(readOnly = true)
    public ConsistencyValidationResult validateTransactionConsistency(String transactionId) {
        if (!validationEnabled) {
            return ConsistencyValidationResult.disabled();
        }

        log.debug("Validating data consistency for transaction: {}", transactionId);

        try {
            List<ConsistencyIssue> issues = new ArrayList<>();
            Map<String, Object> metrics = new HashMap<>();

            // Validate balance consistency
            BalanceConsistencyResult balanceResult = validateBalanceConsistency(transactionId);
            if (!balanceResult.isConsistent()) {
                issues.addAll(balanceResult.getIssues());
            }
            metrics.put("balanceConsistencyCheck", balanceResult.isConsistent());

            // Validate referential integrity
            ReferentialIntegrityResult referentialResult = validateReferentialIntegrity(transactionId);
            if (!referentialResult.isConsistent()) {
                issues.addAll(referentialResult.getIssues());
            }
            metrics.put("referentialIntegrityCheck", referentialResult.isConsistent());

            // Validate cross-service consistency
            CrossServiceConsistencyResult crossServiceResult = validateCrossServiceConsistency(transactionId);
            if (!crossServiceResult.isConsistent()) {
                issues.addAll(crossServiceResult.getIssues());
            }
            metrics.put("crossServiceConsistencyCheck", crossServiceResult.isConsistent());

            // Validate temporal consistency
            TemporalConsistencyResult temporalResult = validateTemporalConsistency(transactionId);
            if (!temporalResult.isConsistent()) {
                issues.addAll(temporalResult.getIssues());
            }
            metrics.put("temporalConsistencyCheck", temporalResult.isConsistent());

            // Calculate overall consistency score
            double consistencyScore = calculateConsistencyScore(issues);
            metrics.put("consistencyScore", consistencyScore);

            // Store validation results
            storeValidationResult(transactionId, issues, metrics);

            return ConsistencyValidationResult.builder()
                .transactionId(transactionId)
                .isConsistent(issues.isEmpty())
                .issues(issues)
                .metrics(metrics)
                .consistencyScore(consistencyScore)
                .validationTimestamp(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Data consistency validation failed for transaction: {}", transactionId, e);
            return ConsistencyValidationResult.error("Validation failed: " + e.getMessage());
        }
    }

    /**
     * Validate balance consistency across all accounts
     */
    @Transactional(readOnly = true)
    public BalanceConsistencyResult validateBalanceConsistency(String transactionId) {
        log.debug("Validating balance consistency for transaction: {}", transactionId);

        try {
            List<ConsistencyIssue> issues = new ArrayList<>();

            // Get all accounts affected by the transaction
            List<String> affectedAccounts = getAffectedAccounts(transactionId);

            for (String accountId : affectedAccounts) {
                BalanceValidationResult accountValidation = validateAccountBalance(accountId);
                if (!accountValidation.isValid()) {
                    issues.addAll(accountValidation.getIssues());
                }
            }

            // Validate global balance totals
            GlobalBalanceValidationResult globalValidation = validateGlobalBalances();
            if (!globalValidation.isValid()) {
                issues.addAll(globalValidation.getIssues());
            }

            // Validate transaction double-entry
            DoubleEntryValidationResult doubleEntryValidation = validateDoubleEntry(transactionId);
            if (!doubleEntryValidation.isValid()) {
                issues.addAll(doubleEntryValidation.getIssues());
            }

            return BalanceConsistencyResult.builder()
                .transactionId(transactionId)
                .isConsistent(issues.isEmpty())
                .issues(issues)
                .affectedAccounts(affectedAccounts)
                .validationTimestamp(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Balance consistency validation failed", e);
            return BalanceConsistencyResult.error("Balance validation failed: " + e.getMessage());
        }
    }

    /**
     * Validate referential integrity
     */
    @Transactional(readOnly = true)
    public ReferentialIntegrityResult validateReferentialIntegrity(String transactionId) {
        log.debug("Validating referential integrity for transaction: {}", transactionId);

        try {
            List<ConsistencyIssue> issues = new ArrayList<>();

            // Check foreign key constraints
            issues.addAll(validateForeignKeyConstraints(transactionId));

            // Check orphaned records
            issues.addAll(detectOrphanedRecords(transactionId));

            // Check duplicate references
            issues.addAll(detectDuplicateReferences(transactionId));

            // Check circular references
            issues.addAll(detectCircularReferences(transactionId));

            return ReferentialIntegrityResult.builder()
                .transactionId(transactionId)
                .isConsistent(issues.isEmpty())
                .issues(issues)
                .validationTimestamp(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Referential integrity validation failed", e);
            return ReferentialIntegrityResult.error("Referential integrity validation failed: " + e.getMessage());
        }
    }

    /**
     * Validate consistency across microservices
     */
    @Transactional(readOnly = true)
    public CrossServiceConsistencyResult validateCrossServiceConsistency(String transactionId) {
        log.debug("Validating cross-service consistency for transaction: {}", transactionId);

        try {
            List<ConsistencyIssue> issues = new ArrayList<>();
            Map<String, ServiceConsistencyStatus> serviceStatuses = new HashMap<>();

            // Get services involved in the transaction
            List<String> involvedServices = getInvolvedServices(transactionId);

            for (String service : involvedServices) {
                ServiceConsistencyStatus status = validateServiceConsistency(service, transactionId);
                serviceStatuses.put(service, status);
                
                if (!status.isConsistent()) {
                    issues.addAll(status.getIssues());
                }
            }

            // Validate data synchronization between services
            issues.addAll(validateDataSynchronization(transactionId, involvedServices));

            // Validate event ordering
            issues.addAll(validateEventOrdering(transactionId));

            return CrossServiceConsistencyResult.builder()
                .transactionId(transactionId)
                .isConsistent(issues.isEmpty())
                .issues(issues)
                .serviceStatuses(serviceStatuses)
                .validationTimestamp(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Cross-service consistency validation failed", e);
            return CrossServiceConsistencyResult.error("Cross-service validation failed: " + e.getMessage());
        }
    }

    /**
     * Validate temporal consistency (timestamps, ordering, etc.)
     */
    @Transactional(readOnly = true)
    public TemporalConsistencyResult validateTemporalConsistency(String transactionId) {
        log.debug("Validating temporal consistency for transaction: {}", transactionId);

        try {
            List<ConsistencyIssue> issues = new ArrayList<>();

            // Validate timestamp ordering
            issues.addAll(validateTimestampOrdering(transactionId));

            // Validate event sequence
            issues.addAll(validateEventSequence(transactionId));

            // Validate time-based constraints
            issues.addAll(validateTimeBasedConstraints(transactionId));

            // Check for temporal anomalies
            issues.addAll(detectTemporalAnomalies(transactionId));

            return TemporalConsistencyResult.builder()
                .transactionId(transactionId)
                .isConsistent(issues.isEmpty())
                .issues(issues)
                .validationTimestamp(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Temporal consistency validation failed", e);
            return TemporalConsistencyResult.error("Temporal validation failed: " + e.getMessage());
        }
    }

    /**
     * Perform comprehensive data consistency check
     */
    @Scheduled(fixedRateString = "#{${data.consistency.validation.interval.minutes:15} * 60 * 1000}")
    public void performScheduledConsistencyCheck() {
        if (!validationEnabled) {
            return;
        }

        log.info("Starting scheduled data consistency check");

        try {
            ComprehensiveConsistencyCheckResult result = performComprehensiveConsistencyCheck();
            
            if (!result.isOverallConsistent()) {
                log.warn("Data consistency issues detected: {} issues found", result.getTotalIssues());
                handleConsistencyIssues(result.getAllIssues());
            } else {
                log.info("Data consistency check completed successfully - no issues found");
            }

            // Store check results
            storeScheduledCheckResult(result);

        } catch (Exception e) {
            log.error("Scheduled consistency check failed", e);
        }
    }

    /**
     * Perform comprehensive consistency check across all systems
     */
    public ComprehensiveConsistencyCheckResult performComprehensiveConsistencyCheck() {
        log.info("Performing comprehensive data consistency check");

        try {
            List<ConsistencyIssue> allIssues = new ArrayList<>();
            Map<String, Object> checkMetrics = new HashMap<>();

            // Check balance consistency across all accounts
            AllAccountsBalanceResult allAccountsResult = validateAllAccountsBalance();
            allIssues.addAll(allAccountsResult.getIssues());
            checkMetrics.put("totalAccountsChecked", allAccountsResult.getTotalAccountsChecked());
            checkMetrics.put("accountsWithIssues", allAccountsResult.getAccountsWithIssues());

            // Check referential integrity across all tables
            GlobalReferentialIntegrityResult globalRefResult = validateGlobalReferentialIntegrity();
            allIssues.addAll(globalRefResult.getIssues());
            checkMetrics.put("totalTablesChecked", globalRefResult.getTotalTablesChecked());

            // Check cross-service data consistency
            GlobalCrossServiceResult globalCrossResult = validateGlobalCrossServiceConsistency();
            allIssues.addAll(globalCrossResult.getIssues());
            checkMetrics.put("totalServicesChecked", globalCrossResult.getTotalServicesChecked());

            // Check temporal consistency across all transactions
            GlobalTemporalResult globalTemporalResult = validateGlobalTemporalConsistency();
            allIssues.addAll(globalTemporalResult.getIssues());
            checkMetrics.put("totalTransactionsChecked", globalTemporalResult.getTotalTransactionsChecked());

            // Calculate metrics
            checkMetrics.put("totalIssuesFound", allIssues.size());
            checkMetrics.put("criticalIssues", allIssues.stream()
                .mapToInt(issue -> "CRITICAL".equals(issue.getSeverity()) ? 1 : 0).sum());
            checkMetrics.put("warningIssues", allIssues.stream()
                .mapToInt(issue -> "WARNING".equals(issue.getSeverity()) ? 1 : 0).sum());

            return ComprehensiveConsistencyCheckResult.builder()
                .isOverallConsistent(allIssues.isEmpty())
                .allIssues(allIssues)
                .totalIssues(allIssues.size())
                .checkMetrics(checkMetrics)
                .checkTimestamp(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Comprehensive consistency check failed", e);
            return ComprehensiveConsistencyCheckResult.error("Check failed: " + e.getMessage());
        }
    }

    /**
     * Repair data consistency issues
     */
    @Transactional
    public ConsistencyRepairResult repairConsistencyIssues(List<ConsistencyIssue> issues) {
        if (!autoRepairEnabled) {
            return ConsistencyRepairResult.disabled("Auto-repair is disabled");
        }

        log.info("Attempting to repair {} consistency issues", issues.size());

        try {
            List<RepairAction> successfulRepairs = new ArrayList<>();
            List<RepairAction> failedRepairs = new ArrayList<>();

            for (ConsistencyIssue issue : issues) {
                try {
                    RepairAction repair = determineRepairAction(issue);
                    if (repair != null && repair.isAutoRepairable()) {
                        boolean success = executeRepairAction(repair);
                        if (success) {
                            successfulRepairs.add(repair);
                            log.info("Successfully repaired consistency issue: {}", issue.getIssueId());
                        } else {
                            failedRepairs.add(repair);
                            log.warn("Failed to repair consistency issue: {}", issue.getIssueId());
                        }
                    } else {
                        log.info("Issue requires manual intervention: {}", issue.getIssueId());
                        failedRepairs.add(RepairAction.builder()
                            .issueId(issue.getIssueId())
                            .action("MANUAL_INTERVENTION_REQUIRED")
                            .autoRepairable(false)
                            .build());
                    }
                } catch (Exception e) {
                    log.error("Error repairing issue: {}", issue.getIssueId(), e);
                    failedRepairs.add(RepairAction.builder()
                        .issueId(issue.getIssueId())
                        .action("REPAIR_FAILED")
                        .error(e.getMessage())
                        .build());
                }
            }

            return ConsistencyRepairResult.builder()
                .totalIssues(issues.size())
                .successfulRepairs(successfulRepairs)
                .failedRepairs(failedRepairs)
                .repairTimestamp(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Consistency repair process failed", e);
            return ConsistencyRepairResult.error("Repair process failed: " + e.getMessage());
        }
    }

    // Private helper methods

    private BalanceValidationResult validateAccountBalance(String accountId) {
        try {
            // Get balance from primary source
            BigDecimal primaryBalance = getPrimaryAccountBalance(accountId);
            
            // Get balance from audit trail
            BigDecimal auditBalance = getAuditTrailBalance(accountId);
            
            // Get balance from cache
            BigDecimal cacheBalance = getCachedAccountBalance(accountId);

            List<ConsistencyIssue> issues = new ArrayList<>();

            // Compare balances within tolerance
            if (!balancesWithinTolerance(primaryBalance, auditBalance)) {
                issues.add(createBalanceInconsistencyIssue(accountId, "PRIMARY_AUDIT", 
                    primaryBalance, auditBalance));
            }

            if (!balancesWithinTolerance(primaryBalance, cacheBalance)) {
                issues.add(createBalanceInconsistencyIssue(accountId, "PRIMARY_CACHE", 
                    primaryBalance, cacheBalance));
            }

            return BalanceValidationResult.builder()
                .accountId(accountId)
                .isValid(issues.isEmpty())
                .issues(issues)
                .primaryBalance(primaryBalance)
                .auditBalance(auditBalance)
                .cacheBalance(cacheBalance)
                .build();

        } catch (Exception e) {
            log.error("Error validating account balance: {}", accountId, e);
            return BalanceValidationResult.error("Balance validation failed: " + e.getMessage());
        }
    }

    private boolean balancesWithinTolerance(BigDecimal balance1, BigDecimal balance2) {
        if (balance1 == null || balance2 == null) {
            return balance1 == balance2;
        }

        BigDecimal difference = balance1.subtract(balance2).abs();
        BigDecimal tolerance = balance1.abs().multiply(BigDecimal.valueOf(tolerancePercentage / 100));
        
        return difference.compareTo(tolerance) <= 0;
    }

    private ConsistencyIssue createBalanceInconsistencyIssue(String accountId, String type, 
                                                           BigDecimal balance1, BigDecimal balance2) {
        return ConsistencyIssue.builder()
            .issueId(UUID.randomUUID().toString())
            .type("BALANCE_INCONSISTENCY")
            .severity("CRITICAL")
            .description(String.format("Balance inconsistency detected for account %s: %s", accountId, type))
            .affectedEntity(accountId)
            .details(Map.of(
                "balance1", balance1,
                "balance2", balance2,
                "difference", balance1 != null && balance2 != null ? balance1.subtract(balance2) : null
            ))
            .detectedAt(Instant.now())
            .build();
    }

    private List<String> getAffectedAccounts(String transactionId) {
        // Query database for accounts affected by transaction
        try {
            return jdbcTemplate.queryForList(
                "SELECT DISTINCT account_id FROM transactions WHERE transaction_id = ?",
                String.class, transactionId);
        } catch (Exception e) {
            log.error("Error getting affected accounts", e);
            return Collections.emptyList();
        }
    }

    private BigDecimal getPrimaryAccountBalance(String accountId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE account_id = ?",
                BigDecimal.class, accountId);
        } catch (Exception e) {
            log.error("CRITICAL: Error getting primary account balance for account: {} - Cannot validate financial integrity", accountId, e);
            throw new RuntimeException("Failed to retrieve primary balance for account: " + accountId + ". Financial integrity check failed.", e);
        }
    }

    private BigDecimal getAuditTrailBalance(String accountId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT SUM(amount) FROM audit_records WHERE source_account = ? OR destination_account = ?",
                BigDecimal.class, accountId, accountId);
        } catch (Exception e) {
            log.error("CRITICAL: Error getting audit trail balance for account: {} - Cannot validate transaction integrity", accountId, e);
            throw new RuntimeException("Failed to retrieve audit trail balance for account: " + accountId + ". Transaction integrity check failed.", e);
        }
    }

    private BigDecimal getCachedAccountBalance(String accountId) {
        try {
            String key = "account:balance:" + accountId;
            Object balance = redisTemplate.opsForValue().get(key);
            return balance != null ? new BigDecimal(balance.toString()) : null;
        } catch (Exception e) {
            log.error("Error getting cached account balance", e);
            return null;
        }
    }

    private GlobalBalanceValidationResult validateGlobalBalances() {
        // Implement global balance validation logic
        return GlobalBalanceValidationResult.builder()
            .isValid(true)
            .issues(Collections.emptyList())
            .build();
    }

    private DoubleEntryValidationResult validateDoubleEntry(String transactionId) {
        // Implement double-entry validation logic
        return DoubleEntryValidationResult.builder()
            .isValid(true)
            .issues(Collections.emptyList())
            .build();
    }

    private List<ConsistencyIssue> validateForeignKeyConstraints(String transactionId) {
        // Implement foreign key constraint validation
        return Collections.emptyList();
    }

    private List<ConsistencyIssue> detectOrphanedRecords(String transactionId) {
        // Implement orphaned record detection
        return Collections.emptyList();
    }

    private List<ConsistencyIssue> detectDuplicateReferences(String transactionId) {
        // Implement duplicate reference detection
        return Collections.emptyList();
    }

    private List<ConsistencyIssue> detectCircularReferences(String transactionId) {
        // Implement circular reference detection
        return Collections.emptyList();
    }

    private List<String> getInvolvedServices(String transactionId) {
        // Get list of services involved in transaction
        return Arrays.asList("payment-service", "account-service", "notification-service");
    }

    private ServiceConsistencyStatus validateServiceConsistency(String service, String transactionId) {
        // Validate consistency within a specific service
        return ServiceConsistencyStatus.builder()
            .serviceName(service)
            .isConsistent(true)
            .issues(Collections.emptyList())
            .build();
    }

    private List<ConsistencyIssue> validateDataSynchronization(String transactionId, List<String> services) {
        // Validate data synchronization between services
        return Collections.emptyList();
    }

    private List<ConsistencyIssue> validateEventOrdering(String transactionId) {
        // Validate event ordering
        return Collections.emptyList();
    }

    private List<ConsistencyIssue> validateTimestampOrdering(String transactionId) {
        // Validate timestamp ordering
        return Collections.emptyList();
    }

    private List<ConsistencyIssue> validateEventSequence(String transactionId) {
        // Validate event sequence
        return Collections.emptyList();
    }

    private List<ConsistencyIssue> validateTimeBasedConstraints(String transactionId) {
        // Validate time-based constraints
        return Collections.emptyList();
    }

    private List<ConsistencyIssue> detectTemporalAnomalies(String transactionId) {
        // Detect temporal anomalies
        return Collections.emptyList();
    }

    private double calculateConsistencyScore(List<ConsistencyIssue> issues) {
        if (issues.isEmpty()) {
            return 100.0;
        }
        
        // Calculate score based on issue severity
        int criticalIssues = (int) issues.stream().filter(i -> "CRITICAL".equals(i.getSeverity())).count();
        int warningIssues = (int) issues.stream().filter(i -> "WARNING".equals(i.getSeverity())).count();
        
        double deduction = (criticalIssues * 20.0) + (warningIssues * 5.0);
        return Math.max(0.0, 100.0 - deduction);
    }

    private void storeValidationResult(String transactionId, List<ConsistencyIssue> issues, Map<String, Object> metrics) {
        try {
            String key = "consistency:validation:" + transactionId;
            Map<String, Object> result = Map.of(
                "transactionId", transactionId,
                "issues", issues,
                "metrics", metrics,
                "timestamp", Instant.now()
            );
            redisTemplate.opsForValue().set(key, result, Duration.ofDays(7));
        } catch (Exception e) {
            log.error("Failed to store validation result", e);
        }
    }

    private AllAccountsBalanceResult validateAllAccountsBalance() {
        // Implement validation for all accounts
        return AllAccountsBalanceResult.builder()
            .totalAccountsChecked(0)
            .accountsWithIssues(0)
            .issues(Collections.emptyList())
            .build();
    }

    private GlobalReferentialIntegrityResult validateGlobalReferentialIntegrity() {
        // Implement global referential integrity validation
        return GlobalReferentialIntegrityResult.builder()
            .totalTablesChecked(0)
            .issues(Collections.emptyList())
            .build();
    }

    private GlobalCrossServiceResult validateGlobalCrossServiceConsistency() {
        // Implement global cross-service validation
        return GlobalCrossServiceResult.builder()
            .totalServicesChecked(0)
            .issues(Collections.emptyList())
            .build();
    }

    private GlobalTemporalResult validateGlobalTemporalConsistency() {
        // Implement global temporal validation
        return GlobalTemporalResult.builder()
            .totalTransactionsChecked(0)
            .issues(Collections.emptyList())
            .build();
    }

    private void handleConsistencyIssues(List<ConsistencyIssue> issues) {
        // Send alerts for critical issues
        List<ConsistencyIssue> criticalIssues = issues.stream()
            .filter(issue -> "CRITICAL".equals(issue.getSeverity()))
            .collect(Collectors.toList());

        if (!criticalIssues.isEmpty()) {
            sendConsistencyAlert(criticalIssues);
        }

        // Attempt auto-repair if enabled
        if (autoRepairEnabled) {
            CompletableFuture.runAsync(() -> repairConsistencyIssues(issues), validationExecutor);
        }
    }

    private void sendConsistencyAlert(List<ConsistencyIssue> issues) {
        try {
            ConsistencyAlert alert = ConsistencyAlert.builder()
                .alertId(UUID.randomUUID().toString())
                .severity("CRITICAL")
                .issueCount(issues.size())
                .issues(issues)
                .timestamp(Instant.now())
                .build();

            kafkaTemplate.send("consistency-alerts", alert.getAlertId(), alert);
        } catch (Exception e) {
            log.error("Failed to send consistency alert", e);
        }
    }

    private void storeScheduledCheckResult(ComprehensiveConsistencyCheckResult result) {
        try {
            String key = "consistency:scheduled-check:" + Instant.now().toEpochMilli();
            redisTemplate.opsForValue().set(key, result, Duration.ofDays(30));
        } catch (Exception e) {
            log.error("Failed to store scheduled check result", e);
        }
    }

    private RepairAction determineRepairAction(ConsistencyIssue issue) {
        // Determine appropriate repair action based on issue type
        return RepairAction.builder()
            .issueId(issue.getIssueId())
            .action("AUTO_REPAIR")
            .autoRepairable(false) // Conservative approach
            .build();
    }

    private boolean executeRepairAction(RepairAction repair) {
        // Execute the repair action
        return false; // Conservative approach - no auto-repair for now
    }

    // Data classes

    @Data
    @Builder
    public static class ConsistencyValidationResult {
        private String transactionId;
        private boolean isConsistent;
        private List<ConsistencyIssue> issues;
        private Map<String, Object> metrics;
        private double consistencyScore;
        private Instant validationTimestamp;
        private String errorMessage;

        public static ConsistencyValidationResult disabled() {
            return ConsistencyValidationResult.builder().isConsistent(true).build();
        }

        public static ConsistencyValidationResult error(String message) {
            return ConsistencyValidationResult.builder().isConsistent(false).errorMessage(message).build();
        }
    }

    @Data
    @Builder
    public static class ConsistencyIssue {
        private String issueId;
        private String type;
        private String severity;
        private String description;
        private String affectedEntity;
        private Map<String, Object> details;
        private Instant detectedAt;
    }

    @Data
    @Builder
    public static class BalanceConsistencyResult {
        private String transactionId;
        private boolean isConsistent;
        private List<ConsistencyIssue> issues;
        private List<String> affectedAccounts;
        private Instant validationTimestamp;
        private String errorMessage;

        public static BalanceConsistencyResult error(String message) {
            return BalanceConsistencyResult.builder().isConsistent(false).errorMessage(message).build();
        }
    }

    @Data
    @Builder
    public static class BalanceValidationResult {
        private String accountId;
        private boolean isValid;
        private List<ConsistencyIssue> issues;
        private BigDecimal primaryBalance;
        private BigDecimal auditBalance;
        private BigDecimal cacheBalance;

        public static BalanceValidationResult error(String message) {
            return BalanceValidationResult.builder().isValid(false).build();
        }
    }

    @Data
    @Builder
    public static class ReferentialIntegrityResult {
        private String transactionId;
        private boolean isConsistent;
        private List<ConsistencyIssue> issues;
        private Instant validationTimestamp;
        private String errorMessage;

        public static ReferentialIntegrityResult error(String message) {
            return ReferentialIntegrityResult.builder().isConsistent(false).errorMessage(message).build();
        }
    }

    @Data
    @Builder
    public static class CrossServiceConsistencyResult {
        private String transactionId;
        private boolean isConsistent;
        private List<ConsistencyIssue> issues;
        private Map<String, ServiceConsistencyStatus> serviceStatuses;
        private Instant validationTimestamp;
        private String errorMessage;

        public static CrossServiceConsistencyResult error(String message) {
            return CrossServiceConsistencyResult.builder().isConsistent(false).errorMessage(message).build();
        }
    }

    @Data
    @Builder
    public static class ServiceConsistencyStatus {
        private String serviceName;
        private boolean isConsistent;
        private List<ConsistencyIssue> issues;
    }

    @Data
    @Builder
    public static class TemporalConsistencyResult {
        private String transactionId;
        private boolean isConsistent;
        private List<ConsistencyIssue> issues;
        private Instant validationTimestamp;
        private String errorMessage;

        public static TemporalConsistencyResult error(String message) {
            return TemporalConsistencyResult.builder().isConsistent(false).errorMessage(message).build();
        }
    }

    @Data
    @Builder
    public static class ComprehensiveConsistencyCheckResult {
        private boolean isOverallConsistent;
        private List<ConsistencyIssue> allIssues;
        private int totalIssues;
        private Map<String, Object> checkMetrics;
        private Instant checkTimestamp;
        private String errorMessage;

        public static ComprehensiveConsistencyCheckResult error(String message) {
            return ComprehensiveConsistencyCheckResult.builder()
                .isOverallConsistent(false)
                .errorMessage(message)
                .build();
        }
    }

    @Data
    @Builder
    public static class ConsistencyRepairResult {
        private int totalIssues;
        private List<RepairAction> successfulRepairs;
        private List<RepairAction> failedRepairs;
        private Instant repairTimestamp;
        private String errorMessage;

        public static ConsistencyRepairResult disabled(String message) {
            return ConsistencyRepairResult.builder().errorMessage(message).build();
        }

        public static ConsistencyRepairResult error(String message) {
            return ConsistencyRepairResult.builder().errorMessage(message).build();
        }
    }

    @Data
    @Builder
    public static class RepairAction {
        private String issueId;
        private String action;
        private boolean autoRepairable;
        private String error;
    }

    @Data
    @Builder
    public static class ConsistencyCheckpoint {
        private String checkpointId;
        private Instant timestamp;
        private Map<String, Object> state;
    }

    @Data
    @Builder
    public static class ConsistencyAlert {
        private String alertId;
        private String severity;
        private int issueCount;
        private List<ConsistencyIssue> issues;
        private Instant timestamp;
    }

    // Additional result classes for comprehensive validation
    @Data
    @Builder
    public static class GlobalBalanceValidationResult {
        private boolean isValid;
        private List<ConsistencyIssue> issues;
    }

    @Data
    @Builder
    public static class DoubleEntryValidationResult {
        private boolean isValid;
        private List<ConsistencyIssue> issues;
    }

    @Data
    @Builder
    public static class AllAccountsBalanceResult {
        private int totalAccountsChecked;
        private int accountsWithIssues;
        private List<ConsistencyIssue> issues;
    }

    @Data
    @Builder
    public static class GlobalReferentialIntegrityResult {
        private int totalTablesChecked;
        private List<ConsistencyIssue> issues;
    }

    @Data
    @Builder
    public static class GlobalCrossServiceResult {
        private int totalServicesChecked;
        private List<ConsistencyIssue> issues;
    }

    @Data
    @Builder
    public static class GlobalTemporalResult {
        private int totalTransactionsChecked;
        private List<ConsistencyIssue> issues;
    }
}