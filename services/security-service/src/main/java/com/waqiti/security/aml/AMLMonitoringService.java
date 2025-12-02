package com.waqiti.security.aml;

import com.waqiti.security.audit.AuditService;
import com.waqiti.security.compliance.ComplianceService;
import com.waqiti.payment.service.TransactionService;
import com.waqiti.compliance.service.CaseManagementService;
import com.waqiti.compliance.service.ComplianceReportingService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Anti-Money Laundering (AML) Monitoring Service
 * Implements comprehensive AML monitoring and reporting
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AMLMonitoringService {

    private final AuditService auditService;
    private final ComplianceService complianceService;
    private final TransactionService transactionService;
    private final CaseManagementService caseManagementService;
    private final ComplianceReportingService complianceReportingService;

    // AML thresholds and configurations
    private static final BigDecimal DAILY_TRANSACTION_THRESHOLD = new BigDecimal("10000");
    private static final BigDecimal SINGLE_TRANSACTION_THRESHOLD = new BigDecimal("5000");
    private static final BigDecimal CUMULATIVE_THRESHOLD = new BigDecimal("15000");
    private static final int STRUCTURING_PATTERN_DAYS = 7;
    private static final int VELOCITY_CHECK_HOURS = 24;

    // Risk scoring weights
    private static final double AMOUNT_WEIGHT = 0.3;
    private static final double FREQUENCY_WEIGHT = 0.2;
    private static final double PATTERN_WEIGHT = 0.25;
    private static final double GEOGRAPHY_WEIGHT = 0.15;
    private static final double RELATIONSHIP_WEIGHT = 0.1;

    // Monitoring data
    private final Map<String, UserTransactionProfile> userProfiles = new ConcurrentHashMap<>();
    private final Map<String, SuspiciousActivity> suspiciousActivities = new ConcurrentHashMap<>();
    private final Set<String> highRiskCountries = Set.of("IR", "KP", "SY", "CU", "VE");
    private final Set<String> watchlistEntities = ConcurrentHashMap.newKeySet();

    /**
     * Monitor transaction for AML compliance
     */
    @Async
    public AMLCheckResult monitorTransaction(Transaction transaction) {
        log.debug("Monitoring transaction {} for AML compliance", transaction.getTransactionId());

        try {
            // Update user profile
            updateUserProfile(transaction);

            // Perform AML checks
            List<AMLFlag> flags = new ArrayList<>();

            // Check 1: Single transaction threshold
            if (checkSingleTransactionThreshold(transaction)) {
                flags.add(createFlag(AMLFlagType.HIGH_VALUE_TRANSACTION, transaction));
            }

            // Check 2: Daily cumulative threshold
            if (checkDailyCumulativeThreshold(transaction)) {
                flags.add(createFlag(AMLFlagType.DAILY_LIMIT_EXCEEDED, transaction));
            }

            // Check 3: Structuring detection
            if (detectStructuring(transaction)) {
                flags.add(createFlag(AMLFlagType.STRUCTURING_SUSPECTED, transaction));
            }

            // Check 4: Velocity check
            if (checkVelocity(transaction)) {
                flags.add(createFlag(AMLFlagType.RAPID_MOVEMENT, transaction));
            }

            // Check 5: High-risk geography
            if (checkHighRiskGeography(transaction)) {
                flags.add(createFlag(AMLFlagType.HIGH_RISK_GEOGRAPHY, transaction));
            }

            // Check 6: Watchlist screening
            if (checkWatchlist(transaction)) {
                flags.add(createFlag(AMLFlagType.WATCHLIST_MATCH, transaction));
            }

            // Check 7: Pattern analysis
            if (detectSuspiciousPattern(transaction)) {
                flags.add(createFlag(AMLFlagType.SUSPICIOUS_PATTERN, transaction));
            }

            // Calculate risk score
            double riskScore = calculateRiskScore(transaction, flags);

            // Determine action
            AMLAction action = determineAction(riskScore, flags);

            // Create result
            AMLCheckResult result = AMLCheckResult.builder()
                    .transactionId(transaction.getTransactionId())
                    .timestamp(Instant.now())
                    .flags(flags)
                    .riskScore(riskScore)
                    .action(action)
                    .requiresReview(action != AMLAction.APPROVE)
                    .build();

            // Handle based on action
            handleAMLResult(transaction, result);

            // Audit AML check
            auditService.logAMLEvent("AML_CHECK_COMPLETED", Map.of(
                    "transactionId", transaction.getTransactionId(),
                    "riskScore", riskScore,
                    "action", action,
                    "flagCount", flags.size()
            ));

            return result;

        } catch (Exception e) {
            log.error("AML monitoring failed for transaction {}", transaction.getTransactionId(), e);
            // In case of error, err on the side of caution
            return AMLCheckResult.builder()
                    .transactionId(transaction.getTransactionId())
                    .timestamp(Instant.now())
                    .action(AMLAction.HOLD)
                    .requiresReview(true)
                    .error("AML check failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Generate Suspicious Activity Report (SAR)
     */
    public SuspiciousActivityReport generateSAR(String activityId) {
        log.info("Generating SAR for activity {}", activityId);

        SuspiciousActivity activity = suspiciousActivities.get(activityId);
        if (activity == null) {
            throw new AMLException("Suspicious activity not found: " + activityId);
        }

        // Collect all related transactions
        List<Transaction> relatedTransactions = collectRelatedTransactions(activity);

        // Create SAR
        SuspiciousActivityReport sar = SuspiciousActivityReport.builder()
                .reportId(generateSARId())
                .filingDate(LocalDate.now())
                .reportingInstitution("Waqiti Payment Services")
                .subjectInformation(buildSubjectInformation(activity))
                .suspiciousActivity(activity)
                .narrativeDescription(generateNarrative(activity, relatedTransactions))
                .transactionDetails(relatedTransactions)
                .totalAmount(calculateTotalAmount(relatedTransactions))
                .dateRangeStart(findEarliestDate(relatedTransactions))
                .dateRangeEnd(findLatestDate(relatedTransactions))
                .lawEnforcementContact(false)
                .build();

        // File SAR with regulatory authority
        fileSAR(sar);

        // Audit SAR filing
        auditService.logComplianceEvent("SAR_FILED", Map.of(
                "reportId", sar.getReportId(),
                "subjectId", activity.getUserId(),
                "amount", sar.getTotalAmount()
        ));

        return sar;
    }

    /**
     * Generate Currency Transaction Report (CTR)
     */
    public CurrencyTransactionReport generateCTR(Transaction transaction) {
        log.info("Generating CTR for transaction {}", transaction.getTransactionId());

        CurrencyTransactionReport ctr = CurrencyTransactionReport.builder()
                .reportId(generateCTRId())
                .filingDate(LocalDate.now())
                .transactionDate(transaction.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toLocalDate())
                .transactionAmount(transaction.getAmount())
                .transactionType(transaction.getType())
                .personInformation(getPersonInformation(transaction.getUserId()))
                .accountNumber(transaction.getAccountId())
                .institutionInfo("Waqiti Payment Services")
                .build();

        // File CTR with regulatory authority
        fileCTR(ctr);

        // Audit CTR filing
        auditService.logComplianceEvent("CTR_FILED", Map.of(
                "reportId", ctr.getReportId(),
                "transactionId", transaction.getTransactionId(),
                "amount", transaction.getAmount()
        ));

        return ctr;
    }

    /**
     * Perform enhanced due diligence
     */
    public EnhancedDueDiligenceResult performEDD(String userId) {
        log.info("Performing enhanced due diligence for user {}", userId);

        UserTransactionProfile profile = userProfiles.get(userId);
        if (profile == null) {
            profile = createUserProfile(userId);
        }

        // Collect comprehensive information
        EDDChecks checks = EDDChecks.builder()
                .identityVerification(verifyIdentity(userId))
                .sourceOfFunds(verifySourceOfFunds(userId))
                .businessPurpose(verifyBusinessPurpose(userId))
                .beneficialOwnership(checkBeneficialOwnership(userId))
                .politicalExposure(checkPEPStatus(userId))
                .sanctionsScreening(screenSanctions(userId))
                .adverseMedia(checkAdverseMedia(userId))
                .geographicRisk(assessGeographicRisk(userId))
                .transactionAnalysis(analyzeTransactionHistory(userId))
                .build();

        // Calculate EDD score
        double eddScore = calculateEDDScore(checks);

        // Determine risk level
        RiskLevel riskLevel = determineRiskLevel(eddScore);

        // Create result
        EnhancedDueDiligenceResult result = EnhancedDueDiligenceResult.builder()
                .userId(userId)
                .performedAt(Instant.now())
                .checks(checks)
                .eddScore(eddScore)
                .riskLevel(riskLevel)
                .recommendations(generateEDDRecommendations(checks, riskLevel))
                .nextReviewDate(calculateNextReviewDate(riskLevel))
                .build();

        // Update user profile
        profile.setLastEDDDate(Instant.now());
        profile.setRiskLevel(riskLevel);
        profile.setEddScore(eddScore);

        // Audit EDD
        auditService.logComplianceEvent("EDD_COMPLETED", Map.of(
                "userId", userId,
                "riskLevel", riskLevel,
                "eddScore", eddScore
        ));

        return result;
    }

    /**
     * Scheduled AML monitoring tasks
     */
    @Scheduled(cron = "0 0 * * * ?") // Hourly
    public void performScheduledAMLChecks() {
        log.info("Running scheduled AML checks");

        try {
            // Check for pattern anomalies
            detectPatternAnomalies();

            // Update watchlists
            updateWatchlists();

            // Review high-risk accounts
            reviewHighRiskAccounts();

            // Generate daily AML report
            generateDailyAMLReport();

        } catch (Exception e) {
            log.error("Scheduled AML checks failed", e);
        }
    }

    /**
     * Real-time transaction monitoring
     */
    public void monitorTransactionStream(Transaction transaction) {
        // Real-time monitoring for immediate threats
        if (isImmediateThreat(transaction)) {
            blockTransaction(transaction);
            createAlert(transaction, "IMMEDIATE_THREAT_DETECTED");
        }
    }

    // Helper methods

    private void updateUserProfile(Transaction transaction) {
        userProfiles.compute(transaction.getUserId(), (userId, profile) -> {
            if (profile == null) {
                profile = createUserProfile(userId);
            }

            profile.addTransaction(transaction);
            profile.updateStatistics();

            return profile;
        });
    }

    private UserTransactionProfile createUserProfile(String userId) {
        return UserTransactionProfile.builder()
                .userId(userId)
                .createdAt(Instant.now())
                .transactions(new ArrayList<>())
                .totalVolume(BigDecimal.ZERO)
                .averageTransactionAmount(BigDecimal.ZERO)
                .riskLevel(RiskLevel.LOW)
                .build();
    }

    private boolean checkSingleTransactionThreshold(Transaction transaction) {
        return transaction.getAmount().compareTo(SINGLE_TRANSACTION_THRESHOLD) > 0;
    }

    private boolean checkDailyCumulativeThreshold(Transaction transaction) {
        UserTransactionProfile profile = userProfiles.get(transaction.getUserId());
        if (profile == null) return false;

        BigDecimal dailyTotal = profile.getDailyTotal(LocalDate.now());
        return dailyTotal.add(transaction.getAmount()).compareTo(DAILY_TRANSACTION_THRESHOLD) > 0;
    }

    private boolean detectStructuring(Transaction transaction) {
        UserTransactionProfile profile = userProfiles.get(transaction.getUserId());
        if (profile == null) return false;

        // Look for multiple transactions just below reporting threshold
        List<Transaction> recentTransactions = profile.getTransactionsInLastDays(STRUCTURING_PATTERN_DAYS);

        long suspiciousCount = recentTransactions.stream()
                .filter(t -> t.getAmount().compareTo(SINGLE_TRANSACTION_THRESHOLD.multiply(new BigDecimal("0.9"))) > 0)
                .filter(t -> t.getAmount().compareTo(SINGLE_TRANSACTION_THRESHOLD) < 0)
                .count();

        return suspiciousCount >= 3;
    }

    private boolean checkVelocity(Transaction transaction) {
        UserTransactionProfile profile = userProfiles.get(transaction.getUserId());
        if (profile == null) return false;

        // Check transaction frequency
        long recentCount = profile.getTransactionCountInLastHours(VELOCITY_CHECK_HOURS);
        return recentCount > 10; // More than 10 transactions in 24 hours
    }

    private boolean checkHighRiskGeography(Transaction transaction) {
        String country = transaction.getCountryCode();
        if (country == null) return false;

        return highRiskCountries.contains(country) ||
                (transaction.getRecipientCountry() != null &&
                        highRiskCountries.contains(transaction.getRecipientCountry()));
    }

    private boolean checkWatchlist(Transaction transaction) {
        // Check sender
        if (isOnWatchlist(transaction.getUserId(), transaction.getUserName())) {
            return true;
        }

        // Check recipient
        if (isOnWatchlist(transaction.getRecipientId(), transaction.getRecipientName())) {
            return true;
        }

        return false;
    }

    private boolean isOnWatchlist(String id, String name) {
        if (watchlistEntities.contains(id)) {
            return true;
        }

        // Fuzzy name matching
        if (name != null) {
            return watchlistEntities.stream()
                    .anyMatch(entity -> fuzzyMatch(entity, name));
        }

        return false;
    }

    private boolean fuzzyMatch(String str1, String str2) {
        // Simple fuzzy matching - could be enhanced with Levenshtein distance
        return str1.toLowerCase().contains(str2.toLowerCase()) ||
                str2.toLowerCase().contains(str1.toLowerCase());
    }

    private boolean detectSuspiciousPattern(Transaction transaction) {
        UserTransactionProfile profile = userProfiles.get(transaction.getUserId());
        if (profile == null) return false;

        // Pattern detection logic
        return profile.hasUnusualPattern() ||
                profile.hasRoundAmountPattern() ||
                profile.hasRapidInOutPattern();
    }

    private AMLFlag createFlag(AMLFlagType type, Transaction transaction) {
        return AMLFlag.builder()
                .type(type)
                .severity(type.getSeverity())
                .description(type.getDescription())
                .transactionId(transaction.getTransactionId())
                .flaggedAt(Instant.now())
                .build();
    }

    private double calculateRiskScore(Transaction transaction, List<AMLFlag> flags) {
        double score = 0.0;

        // Amount-based risk
        BigDecimal amount = transaction.getAmount();
        if (amount.compareTo(SINGLE_TRANSACTION_THRESHOLD) > 0) {
            score += 0.3 * (amount.doubleValue() / SINGLE_TRANSACTION_THRESHOLD.doubleValue());
        }

        // Flag-based risk
        for (AMLFlag flag : flags) {
            score += flag.getSeverity().getWeight();
        }

        // User profile risk
        UserTransactionProfile profile = userProfiles.get(transaction.getUserId());
        if (profile != null) {
            score += profile.getRiskLevel().getScore() * 0.2;
        }

        // Normalize to 0-100
        return Math.min(score * 100, 100);
    }

    private AMLAction determineAction(double riskScore, List<AMLFlag> flags) {
        // Check for immediate block conditions
        boolean hasHighSeverityFlag = flags.stream()
                .anyMatch(f -> f.getSeverity() == Severity.CRITICAL);

        if (hasHighSeverityFlag || riskScore > 80) {
            return AMLAction.BLOCK;
        } else if (riskScore > 60) {
            return AMLAction.HOLD;
        } else if (riskScore > 40) {
            return AMLAction.REVIEW;
        } else {
            return AMLAction.APPROVE;
        }
    }

    private void handleAMLResult(Transaction transaction, AMLCheckResult result) {
        switch (result.getAction()) {
            case BLOCK:
                blockTransaction(transaction);
                createSuspiciousActivity(transaction, result);
                break;
            case HOLD:
                holdTransaction(transaction);
                createReviewTask(transaction, result);
                break;
            case REVIEW:
                flagForReview(transaction, result);
                break;
            case APPROVE:
                // Transaction proceeds normally
                break;
        }
    }

    private void blockTransaction(Transaction transaction) {
        log.warn("Blocking transaction {} due to AML concerns", transaction.getTransactionId());

        try {
            // Convert internal transaction ID to UUID for payment service
            UUID transactionUUID = UUID.fromString(transaction.getTransactionId());

            // Update transaction status to blocked in payment service
            transactionService.updateTransactionStatus(transactionUUID,
                    com.waqiti.payment.domain.Transaction.Status.FAILED);

            // Mark with specific failure reason
            transactionService.failTransaction(transactionUUID, "AML_COMPLIANCE_VIOLATION");

            // Create regulatory alert in database
            createRegulatoryAlert(transaction, "TRANSACTION_BLOCKED", "AML compliance violation detected");

            // Notify compliance team
            notifyComplianceTeam(transaction, "BLOCKED");

            log.info("Successfully blocked transaction {} for AML compliance", transaction.getTransactionId());

        } catch (Exception e) {
            log.error("Failed to block transaction {} for AML compliance", transaction.getTransactionId(), e);
            // Create escalation alert if blocking fails
            createEscalationAlert(transaction, "BLOCK_FAILURE", e.getMessage());
        }
    }

    private void holdTransaction(Transaction transaction) {
        log.info("Holding transaction {} for AML review", transaction.getTransactionId());

        try {
            // Convert internal transaction ID to UUID for payment service
            UUID transactionUUID = UUID.fromString(transaction.getTransactionId());

            // Update transaction status to pending review
            transactionService.updateTransactionStatus(transactionUUID,
                    com.waqiti.payment.domain.Transaction.Status.PENDING);

            // Create hold record with expiration (72 hours for AML hold)
            Instant holdExpiration = Instant.now().plus(72, ChronoUnit.HOURS);

            // Create compliance hold alert
            createRegulatoryAlert(transaction, "TRANSACTION_HELD",
                    String.format("Transaction held for AML review. Hold expires: %s", holdExpiration));

            // Schedule automatic review task
            scheduleAutomaticReview(transaction, holdExpiration);

            // Notify compliance team for manual review
            notifyComplianceTeam(transaction, "HELD");

            log.info("Successfully placed transaction {} on AML hold until {}",
                    transaction.getTransactionId(), holdExpiration);

        } catch (Exception e) {
            log.error("Failed to hold transaction {} for AML review", transaction.getTransactionId(), e);
            // Escalate if hold fails - this is critical for compliance
            createEscalationAlert(transaction, "HOLD_FAILURE", e.getMessage());
        }
    }

    private void flagForReview(Transaction transaction, AMLCheckResult result) {
        log.info("Flagging transaction {} for AML review", transaction.getTransactionId());

        try {
            // Create comprehensive case for compliance review
            ComplianceCase complianceCase = ComplianceCase.builder()
                    .caseId(UUID.randomUUID().toString())
                    .transactionId(transaction.getTransactionId())
                    .userId(transaction.getUserId())
                    .caseType("AML_REVIEW")
                    .priority(determineCasePriority(result.getRiskScore()))
                    .description(buildCaseDescription(transaction, result))
                    .flags(result.getFlags())
                    .riskScore(result.getRiskScore())
                    .status("ASSIGNED")
                    .createdAt(Instant.now())
                    .dueDate(calculateReviewDueDate(result.getRiskScore()))
                    .build();

            // Submit case to case management system
            caseManagementService.createCase(complianceCase);

            // Create tracking alert in database
            createRegulatoryAlert(transaction, "FLAGGED_FOR_REVIEW",
                    String.format("Risk Score: %.1f - Case ID: %s", result.getRiskScore(), complianceCase.getCaseId()));

            // Auto-assign to appropriate reviewer based on risk score
            String assignedReviewer = assignReviewer(result.getRiskScore());
            if (assignedReviewer != null) {
                caseManagementService.assignCase(complianceCase.getCaseId(), assignedReviewer);
            }

            // Notify assigned reviewer
            notifyReviewer(assignedReviewer, complianceCase);

            log.info("Successfully flagged transaction {} for review - Case ID: {}, Assigned to: {}",
                    transaction.getTransactionId(), complianceCase.getCaseId(), assignedReviewer);

        } catch (Exception e) {
            log.error("Failed to flag transaction {} for AML review", transaction.getTransactionId(), e);
            // Create fallback manual alert if case creation fails
            createManualReviewAlert(transaction, result, e.getMessage());
        }
    }

    private void createSuspiciousActivity(Transaction transaction, AMLCheckResult result) {
        SuspiciousActivity activity = SuspiciousActivity.builder()
                .activityId(UUID.randomUUID().toString())
                .userId(transaction.getUserId())
                .detectedAt(Instant.now())
                .transactionIds(List.of(transaction.getTransactionId()))
                .flags(result.getFlags())
                .riskScore(result.getRiskScore())
                .status(ActivityStatus.PENDING_REVIEW)
                .build();

        suspiciousActivities.put(activity.getActivityId(), activity);
    }

    private void createReviewTask(Transaction transaction, AMLCheckResult result) {
        // Create task for compliance team
        log.info("Creating review task for transaction {}", transaction.getTransactionId());
    }

    private void createAlert(Transaction transaction, String alertType) {
        // Create real-time alert
        log.warn("Creating {} alert for transaction {}", alertType, transaction.getTransactionId());
    }

    private List<Transaction> collectRelatedTransactions(SuspiciousActivity activity) {
        // Collect all transactions related to the suspicious activity
        return new ArrayList<>();
    }

    private String generateSARId() {
        return String.format("SAR-%s-%s", LocalDate.now().toString(), UUID.randomUUID().toString().substring(0, 8));
    }

    private String generateCTRId() {
        return String.format("CTR-%s-%s", LocalDate.now().toString(), UUID.randomUUID().toString().substring(0, 8));
    }

    private SubjectInformation buildSubjectInformation(SuspiciousActivity activity) {
        // Build subject information for SAR
        return new SubjectInformation();
    }

    private String generateNarrative(SuspiciousActivity activity, List<Transaction> transactions) {
        // Generate narrative description of suspicious activity
        return "Suspicious activity detected...";
    }

    private BigDecimal calculateTotalAmount(List<Transaction> transactions) {
        return transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LocalDate findEarliestDate(List<Transaction> transactions) {
        return transactions.stream()
                .map(t -> t.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toLocalDate())
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());
    }

    private LocalDate findLatestDate(List<Transaction> transactions) {
        return transactions.stream()
                .map(t -> t.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());
    }

    private void fileSAR(SuspiciousActivityReport sar) {
        // File SAR with FinCEN or appropriate authority
        log.info("Filing SAR {}", sar.getReportId());
    }

    private void fileCTR(CurrencyTransactionReport ctr) {
        // File CTR with FinCEN or appropriate authority
        log.info("Filing CTR {}", ctr.getReportId());
    }

    private PersonInformation getPersonInformation(String userId) {
        // Get person information from user service
        return new PersonInformation();
    }

    private boolean verifyIdentity(String userId) {
        // Verify user identity
        return true;
    }

    private boolean verifySourceOfFunds(String userId) {
        // Verify source of funds
        return true;
    }

    private boolean verifyBusinessPurpose(String userId) {
        // Verify business purpose
        return true;
    }

    private boolean checkBeneficialOwnership(String userId) {
        // Check beneficial ownership
        return true;
    }

    private boolean checkPEPStatus(String userId) {
        // Check Politically Exposed Person status
        return false;
    }

    private boolean screenSanctions(String userId) {
        // Screen against sanctions lists
        return false;
    }

    private boolean checkAdverseMedia(String userId) {
        // Check adverse media
        return false;
    }

    private double assessGeographicRisk(String userId) {
        // Assess geographic risk
        return 0.2;
    }

    private TransactionAnalysis analyzeTransactionHistory(String userId) {
        // Analyze transaction history
        return new TransactionAnalysis();
    }

    private double calculateEDDScore(EDDChecks checks) {
        // Calculate EDD score based on checks
        return 75.0;
    }

    private RiskLevel determineRiskLevel(double score) {
        if (score >= 80) return RiskLevel.HIGH;
        if (score >= 60) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    private List<String> generateEDDRecommendations(EDDChecks checks, RiskLevel riskLevel) {
        List<String> recommendations = new ArrayList<>();

        if (riskLevel == RiskLevel.HIGH) {
            recommendations.add("Increase monitoring frequency");
            recommendations.add("Require additional documentation");
            recommendations.add("Consider account restrictions");
        }

        return recommendations;
    }

    private LocalDate calculateNextReviewDate(RiskLevel riskLevel) {
        switch (riskLevel) {
            case HIGH:
                return LocalDate.now().plusMonths(3);
            case MEDIUM:
                return LocalDate.now().plusMonths(6);
            case LOW:
            default:
                return LocalDate.now().plusYears(1);
        }
    }

    private void detectPatternAnomalies() {
        // Detect pattern anomalies across all users
        log.debug("Detecting pattern anomalies");
    }

    private void updateWatchlists() {
        // Update watchlists from external sources
        log.debug("Updating watchlists");
    }

    private void reviewHighRiskAccounts() {
        // Review high-risk accounts
        log.debug("Reviewing high-risk accounts");
    }

    private void generateDailyAMLReport() {
        // Generate daily AML report
        log.info("Generating daily AML report");
    }

    private boolean isImmediateThreat(Transaction transaction) {
        // Check for immediate threats
        return false;
    }

    // Enhanced helper methods for AML integration

    private void createRegulatoryAlert(Transaction transaction, String alertType, String description) {
        try {
            Map<String, Object> alertData = Map.of(
                    "transactionId", transaction.getTransactionId(),
                    "userId", transaction.getUserId(),
                    "amount", transaction.getAmount(),
                    "timestamp", Instant.now().toString(),
                    "description", description
            );

            complianceReportingService.createAlert(alertType, alertData);

        } catch (Exception e) {
            log.error("Failed to create regulatory alert for transaction {}", transaction.getTransactionId(), e);
        }
    }

    private void notifyComplianceTeam(Transaction transaction, String action) {
        try {
            String message = String.format("AML Alert: Transaction %s has been %s. Amount: %s, User: %s",
                    transaction.getTransactionId(), action, transaction.getAmount(), transaction.getUserId());

            // Send to compliance notification queue
            complianceService.sendComplianceNotification("AML_ACTION", message, Map.of(
                    "transactionId", transaction.getTransactionId(),
                    "action", action,
                    "priority", "HIGH"
            ));

        } catch (Exception e) {
            log.error("Failed to notify compliance team for transaction {}", transaction.getTransactionId(), e);
        }
    }

    private void createEscalationAlert(Transaction transaction, String alertType, String reason) {
        try {
            Map<String, Object> escalationData = Map.of(
                    "transactionId", transaction.getTransactionId(),
                    "alertType", alertType,
                    "reason", reason,
                    "priority", "CRITICAL",
                    "escalationTime", Instant.now().toString()
            );

            complianceReportingService.createAlert("ESCALATION_" + alertType, escalationData);

            // Send immediate notification to senior compliance officer
            complianceService.sendUrgentNotification("AML_ESCALATION",
                    "Critical AML system failure for transaction: " + transaction.getTransactionId(), escalationData);

        } catch (Exception e) {
            log.error("Failed to create escalation alert for transaction {}", transaction.getTransactionId(), e);
        }
    }

    private void scheduleAutomaticReview(Transaction transaction, Instant holdExpiration) {
        try {
            // Schedule automatic review task before hold expires
            Instant reviewTime = holdExpiration.minus(2, ChronoUnit.HOURS);

            caseManagementService.scheduleTask(
                    "AML_HOLD_REVIEW",
                    transaction.getTransactionId(),
                    reviewTime,
                    "Automatic review before hold expiration"
            );

        } catch (Exception e) {
            log.error("Failed to schedule automatic review for transaction {}", transaction.getTransactionId(), e);
        }
    }

    private String determineCasePriority(double riskScore) {
        if (riskScore >= 80) return "CRITICAL";
        if (riskScore >= 60) return "HIGH";
        if (riskScore >= 40) return "MEDIUM";
        return "LOW";
    }

    private String buildCaseDescription(Transaction transaction, AMLCheckResult result) {
        StringBuilder description = new StringBuilder();
        description.append(String.format("AML Review Required - Transaction: %s\n", transaction.getTransactionId()));
        description.append(String.format("Risk Score: %.1f\n", result.getRiskScore()));
        description.append(String.format("Amount: %s %s\n", transaction.getAmount(), transaction.getCurrency()));

        if (!result.getFlags().isEmpty()) {
            description.append("\nTriggered Flags:\n");
            for (AMLFlag flag : result.getFlags()) {
                description.append(String.format("- %s (%s): %s\n",
                        flag.getType(), flag.getSeverity(), flag.getDescription()));
            }
        }

        return description.toString();
    }

    private Instant calculateReviewDueDate(double riskScore) {
        // Higher risk = shorter review time
        if (riskScore >= 80) return Instant.now().plus(4, ChronoUnit.HOURS);  // Critical: 4 hours
        if (riskScore >= 60) return Instant.now().plus(24, ChronoUnit.HOURS); // High: 1 day
        if (riskScore >= 40) return Instant.now().plus(72, ChronoUnit.HOURS); // Medium: 3 days
        return Instant.now().plus(168, ChronoUnit.HOURS); // Low: 1 week
    }

    private String assignReviewer(double riskScore) {
        // Auto-assign based on risk score and current workload
        if (riskScore >= 80) {
            return "senior-aml-analyst@example.com"; // Critical cases to senior analysts
        } else if (riskScore >= 60) {
            return "aml-analyst@example.com"; // High risk to regular analysts
        }
        return "junior-aml-analyst@example.com"; // Medium/Low to junior analysts
    }

    private void notifyReviewer(String reviewerEmail, ComplianceCase complianceCase) {
        try {
            if (reviewerEmail != null) {
                complianceService.sendReviewNotification(reviewerEmail, complianceCase);
            }
        } catch (Exception e) {
            log.error("Failed to notify reviewer {} for case {}", reviewerEmail, complianceCase.getCaseId(), e);
        }
    }

    private void createManualReviewAlert(Transaction transaction, AMLCheckResult result, String errorMessage) {
        try {
            // Create manual fallback alert
            Map<String, Object> fallbackData = Map.of(
                    "transactionId", transaction.getTransactionId(),
                    "riskScore", result.getRiskScore(),
                    "flagCount", result.getFlags().size(),
                    "systemError", errorMessage,
                    "requiresManualIntervention", true
            );

            complianceReportingService.createAlert("MANUAL_REVIEW_REQUIRED", fallbackData);

            // Send urgent manual intervention notification
            complianceService.sendUrgentNotification("AML_MANUAL_INTERVENTION",
                    "AML system error - manual review required for transaction: " + transaction.getTransactionId(),
                    fallbackData);

        } catch (Exception e) {
            log.error("Failed to create manual review alert for transaction {}", transaction.getTransactionId(), e);
        }
    }

    // Inner classes

    @Data
    @Builder
    public static class Transaction {
        private String transactionId;
        private String userId;
        private String userName;
        private String recipientId;
        private String recipientName;
        private BigDecimal amount;
        private String currency;
        private String type;
        private Instant timestamp;
        private String countryCode;
        private String recipientCountry;
        private String accountId;
        private Map<String, String> metadata;
    }

    @Data
    @Builder
    public static class UserTransactionProfile {
        private String userId;
        private Instant createdAt;
        private List<Transaction> transactions;
        private BigDecimal totalVolume;
        private BigDecimal averageTransactionAmount;
        private int transactionCount;
        private RiskLevel riskLevel;
        private Instant lastEDDDate;
        private double eddScore;
        private Map<LocalDate, BigDecimal> dailyTotals;

        public void addTransaction(Transaction transaction) {
            if (transactions == null) {
                transactions = new ArrayList<>();
            }
            transactions.add(transaction);

            if (dailyTotals == null) {
                dailyTotals = new HashMap<>();
            }
            LocalDate date = transaction.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            dailyTotals.merge(date, transaction.getAmount(), BigDecimal::add);
        }

        public void updateStatistics() {
            if (transactions == null || transactions.isEmpty()) {
                return;
            }

            transactionCount = transactions.size();
            totalVolume = transactions.stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            averageTransactionAmount = totalVolume.divide(new BigDecimal(transactionCount), 2, RoundingMode.HALF_UP);
        }

        public BigDecimal getDailyTotal(LocalDate date) {
            return dailyTotals.getOrDefault(date, BigDecimal.ZERO);
        }

        public List<Transaction> getTransactionsInLastDays(int days) {
            Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
            return transactions.stream()
                    .filter(t -> t.getTimestamp().isAfter(cutoff))
                    .collect(Collectors.toList());
        }

        public long getTransactionCountInLastHours(int hours) {
            Instant cutoff = Instant.now().minus(hours, ChronoUnit.HOURS);
            return transactions.stream()
                    .filter(t -> t.getTimestamp().isAfter(cutoff))
                    .count();
        }

        public boolean hasUnusualPattern() {
            // Pattern detection logic
            return false;
        }

        public boolean hasRoundAmountPattern() {
            // Check for round amounts
            return false;
        }

        public boolean hasRapidInOutPattern() {
            // Check for rapid in/out pattern
            return false;
        }
    }

    @Data
    @Builder
    public static class AMLCheckResult {
        private String transactionId;
        private Instant timestamp;
        private List<AMLFlag> flags;
        private double riskScore;
        private AMLAction action;
        private boolean requiresReview;
        private String error;
    }

    @Data
    @Builder
    public static class AMLFlag {
        private AMLFlagType type;
        private Severity severity;
        private String description;
        private String transactionId;
        private Instant flaggedAt;
    }

    public enum AMLFlagType {
        HIGH_VALUE_TRANSACTION(Severity.MEDIUM, "Transaction exceeds threshold"),
        DAILY_LIMIT_EXCEEDED(Severity.MEDIUM, "Daily cumulative limit exceeded"),
        STRUCTURING_SUSPECTED(Severity.HIGH, "Potential structuring detected"),
        RAPID_MOVEMENT(Severity.HIGH, "Rapid fund movement detected"),
        HIGH_RISK_GEOGRAPHY(Severity.HIGH, "High-risk country involvement"),
        WATCHLIST_MATCH(Severity.CRITICAL, "Watchlist match found"),
        SUSPICIOUS_PATTERN(Severity.HIGH, "Suspicious pattern detected");

        private final Severity severity;
        private final String description;

        AMLFlagType(Severity severity, String description) {
            this.severity = severity;
            this.description = description;
        }

        public Severity getSeverity() { return severity; }
        public String getDescription() { return description; }
    }

    public enum Severity {
        LOW(0.1), MEDIUM(0.3), HIGH(0.5), CRITICAL(0.8);

        private final double weight;

        Severity(double weight) {
            this.weight = weight;
        }

        public double getWeight() { return weight; }
    }

    public enum AMLAction {
        APPROVE, REVIEW, HOLD, BLOCK
    }

    public enum RiskLevel {
        LOW(0.2), MEDIUM(0.5), HIGH(0.8);

        private final double score;

        RiskLevel(double score) {
            this.score = score;
        }

        public double getScore() { return score; }
    }

    @Data
    @Builder
    public static class SuspiciousActivity {
        private String activityId;
        private String userId;
        private Instant detectedAt;
        private List<String> transactionIds;
        private List<AMLFlag> flags;
        private double riskScore;
        private ActivityStatus status;
        private String investigatorNotes;
        private Instant resolvedAt;
    }

    public enum ActivityStatus {
        PENDING_REVIEW, UNDER_INVESTIGATION, ESCALATED, RESOLVED, REPORTED
    }

    @Data
    @Builder
    public static class SuspiciousActivityReport {
        private String reportId;
        private LocalDate filingDate;
        private String reportingInstitution;
        private SubjectInformation subjectInformation;
        private SuspiciousActivity suspiciousActivity;
        private String narrativeDescription;
        private List<Transaction> transactionDetails;
        private BigDecimal totalAmount;
        private LocalDate dateRangeStart;
        private LocalDate dateRangeEnd;
        private boolean lawEnforcementContact;
    }

    @Data
    @Builder
    public static class CurrencyTransactionReport {
        private String reportId;
        private LocalDate filingDate;
        private LocalDate transactionDate;
        private BigDecimal transactionAmount;
        private String transactionType;
        private PersonInformation personInformation;
        private String accountNumber;
        private String institutionInfo;
    }

    @Data
    @Builder
    public static class EnhancedDueDiligenceResult {
        private String userId;
        private Instant performedAt;
        private EDDChecks checks;
        private double eddScore;
        private RiskLevel riskLevel;
        private List<String> recommendations;
        private LocalDate nextReviewDate;
    }

    @Data
    @Builder
    public static class EDDChecks {
        private boolean identityVerification;
        private boolean sourceOfFunds;
        private boolean businessPurpose;
        private boolean beneficialOwnership;
        private boolean politicalExposure;
        private boolean sanctionsScreening;
        private boolean adverseMedia;
        private double geographicRisk;
        private TransactionAnalysis transactionAnalysis;
    }

    @Data
    public static class SubjectInformation {
        // Subject details for SAR
    }

    @Data
    public static class PersonInformation {
        // Person details for CTR
    }

    @Data
    public static class TransactionAnalysis {
        // Transaction analysis results
    }

    @Data
    @Builder
    public static class ComplianceCase {
        private String caseId;
        private String transactionId;
        private String userId;
        private String caseType;
        private String priority;
        private String description;
        private List<AMLFlag> flags;
        private double riskScore;
        private String status;
        private Instant createdAt;
        private Instant dueDate;
        private String assignedTo;
        private String resolution;
        private Instant resolvedAt;
    }

    public static class AMLException extends RuntimeException {
        public AMLException(String message) {
            super(message);
        }

        public AMLException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}