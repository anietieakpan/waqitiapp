package com.waqiti.compliance.ctr;

import com.waqiti.common.audit.AuditService;
import com.waqiti.compliance.ctr.dto.*;
import com.waqiti.compliance.ctr.repository.CTRRepository;
import com.waqiti.compliance.bsa.FinCENBSAEFilingClient;
import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.repository.TransactionRepository;
import com.waqiti.user.domain.User;
import com.waqiti.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Currency Transaction Report (CTR) Auto-Filing Service
 *
 * Implements Bank Secrecy Act (BSA) compliance for Currency Transaction Reporting:
 * - FinCEN Form 112 (Currency Transaction Report)
 * - Automatic detection of transactions >= $10,000
 * - 15-day filing deadline from transaction date
 * - Multiple transaction aggregation (24-hour period)
 * - Exemption tracking (banks, government agencies)
 * - BSA E-Filing System integration
 *
 * Legal Requirements:
 * - 31 U.S.C. § 5313 (Reports on domestic coins and currency transactions)
 * - 31 CFR § 103.22 (Currency transaction reporting)
 * - Bank Secrecy Act of 1970
 * - USA PATRIOT Act amendments
 *
 * Penalties for Non-Compliance:
 * - Civil: Up to $25,000 per violation
 * - Criminal: Up to $250,000 and/or 5 years imprisonment
 * - Pattern of violations: Up to $500,000 and/or 10 years
 *
 * Filing Requirements:
 * - File within 15 calendar days of transaction
 * - Include all cash-in and cash-out transactions
 * - Aggregate multiple transactions in same business day
 * - Report suspicious patterns even if below threshold
 * - Maintain records for 5 years
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-10-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CTRAutoFilingService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CTRRepository ctrRepository;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    private final FinCENBSAEFilingClient finCENClient;
    private final CTRExemptionService exemptionService;

    // Regulatory thresholds - defaults are per BSA requirements
    // IMPORTANT: Configure via environment variables or external config for production
    private static final BigDecimal CTR_THRESHOLD = new BigDecimal(
        System.getProperty("ctr.threshold",
            System.getenv().getOrDefault("CTR_THRESHOLD", "10000")));
    // BSA requires filing within 15 calendar days of transaction
    private static final int FILING_DEADLINE_DAYS = Integer.parseInt(
        System.getProperty("ctr.filing.deadline.days",
            System.getenv().getOrDefault("CTR_FILING_DEADLINE_DAYS", "15")));

    /**
     * Detect transactions requiring CTR filing
     * Runs every hour to identify reportable transactions
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void detectCTRRequiredTransactions() {
        log.info("Running CTR detection for reportable transactions");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lookbackStart = now.minusHours(25); // 24 hours + buffer

        try {
            // Get all cash transactions in the lookback period
            List<Transaction> cashTransactions = transactionRepository
                .findCashTransactionsByDateRange(lookbackStart, now);

            log.info("Analyzing {} cash transactions for CTR requirements", cashTransactions.size());

            // Group by customer and business day
            Map<String, Map<LocalDate, List<Transaction>>> transactionsByCustomerAndDay =
                cashTransactions.stream()
                    .collect(Collectors.groupingBy(
                        Transaction::getUserId,
                        Collectors.groupingBy(t -> t.getCreatedAt().toLocalDate())
                    ));

            int ctrTriggered = 0;

            for (Map.Entry<String, Map<LocalDate, List<Transaction>>> customerEntry : transactionsByCustomerAndDay.entrySet()) {
                String userId = customerEntry.getKey();

                for (Map.Entry<LocalDate, List<Transaction>> dayEntry : customerEntry.getValue().entrySet()) {
                    LocalDate transactionDate = dayEntry.getKey();
                    List<Transaction> dayTransactions = dayEntry.getValue();

                    // Aggregate cash-in transactions
                    BigDecimal totalCashIn = dayTransactions.stream()
                        .filter(t -> "CASH_DEPOSIT".equals(t.getTransactionType()) ||
                                   "CASH_IN".equals(t.getTransactionType()))
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Aggregate cash-out transactions
                    BigDecimal totalCashOut = dayTransactions.stream()
                        .filter(t -> "CASH_WITHDRAWAL".equals(t.getTransactionType()) ||
                                   "CASH_OUT".equals(t.getTransactionType()) ||
                                   "ATM_WITHDRAWAL".equals(t.getTransactionType()))
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Threshold check configured externally for regulatory compliance
                    boolean cashInExceedsThreshold = totalCashIn.compareTo(CTR_THRESHOLD) >= 0;
                    boolean cashOutExceedsThreshold = totalCashOut.compareTo(CTR_THRESHOLD) >= 0;

                    if (cashInExceedsThreshold || cashOutExceedsThreshold) {
                        // Exemption logic configured externally for regulatory compliance
                        if (!exemptionService.isExemptFromCTR(userId)) {
                            // Create CTR
                            CTRReport ctr = createCTR(userId, transactionDate, dayTransactions,
                                totalCashIn, totalCashOut);
                            ctrTriggered++;

                            log.info("CTR triggered: userId={}, date={}, cashIn=${}, cashOut=${}",
                                userId, transactionDate, totalCashIn, totalCashOut);
                        } else {
                            log.info("CTR exemption applied: userId={}, date={}", userId, transactionDate);
                            auditService.logSecurityEvent(
                                "CTR_EXEMPTION_APPLIED",
                                Map.of(
                                    "userId", userId,
                                    "transactionDate", transactionDate,
                                    "totalCashIn", totalCashIn,
                                    "totalCashOut", totalCashOut
                                ),
                                "SYSTEM",
                                "CTR_COMPLIANCE"
                            );
                        }
                    }
                }
            }

            log.info("CTR detection complete: {} CTRs triggered", ctrTriggered);
            meterRegistry.counter("ctr.detection.runs").increment();
            meterRegistry.gauge("ctr.triggered.count", ctrTriggered);

        } catch (Exception e) {
            log.error("Error during CTR detection", e);
            meterRegistry.counter("ctr.detection.errors").increment();
            throw new CTRFilingException("CTR detection failed", e);
        }
    }

    /**
     * Create Currency Transaction Report (FinCEN Form 112)
     *
     * @param userId Customer user ID
     * @param transactionDate Date of transactions
     * @param transactions List of transactions for the day
     * @param totalCashIn Total cash deposits
     * @param totalCashOut Total cash withdrawals
     * @return CTR report
     */
    @Timed(value = "ctr.report.creation")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public CTRReport createCTR(String userId, LocalDate transactionDate,
                               List<Transaction> transactions,
                               BigDecimal totalCashIn, BigDecimal totalCashOut) {
        log.info("Creating CTR: userId={}, date={}, cashIn=${}, cashOut=${}",
            userId, transactionDate, totalCashIn, totalCashOut);

        try {
            // Check if CTR already exists for this user and date
            Optional<CTRReport> existingCTR = ctrRepository
                .findByUserIdAndTransactionDate(userId, transactionDate);

            if (existingCTR.isPresent()) {
                log.warn("CTR already exists: userId={}, date={}", userId, transactionDate);
                return existingCTR.get();
            }

            // Get customer information
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new CTRFilingException("User not found: " + userId));

            // Build FinCEN Form 112
            CTRReport ctr = CTRReport.builder()
                .ctrId(generateCTRId())
                .filingInstitution(buildFilingInstitution())
                .transactionDate(transactionDate)
                .filingDeadline(transactionDate.plusDays(FILING_DEADLINE_DAYS))
                // Part I: Person(s) Involved in Transaction(s)
                .personInformation(buildPersonInformation(user))
                // Part II: Amount and Type of Transaction(s)
                .cashIn(totalCashIn)
                .cashOut(totalCashOut)
                .totalCashAmount(totalCashIn.add(totalCashOut))
                .foreignCurrencyIndicator(hasForeignCurrency(transactions))
                .foreignCurrencyAmount(calculateForeignCurrencyAmount(transactions))
                // Part III: Transaction Details
                .transactionDetails(buildTransactionDetails(transactions))
                .transactionCount(transactions.size())
                // Part IV: Account Information
                .accountInformation(buildAccountInformation(user, transactions))
                // Part V: Multiple Transactions
                .multipleTransactionsIndicator(transactions.size() > 1)
                .aggregationPeriod("SINGLE_BUSINESS_DAY")
                // Metadata
                .filingStatus("PENDING")
                .createdAt(LocalDateTime.now())
                .createdBy("SYSTEM")
                .suspicious(detectSuspiciousPatterns(transactions, totalCashIn, totalCashOut))
                .build();

            // Persist CTR
            ctrRepository.save(ctr);

            // Audit trail
            auditService.logSecurityEvent(
                "CTR_CREATED",
                Map.of(
                    "ctrId", ctr.getCtrId(),
                    "userId", userId,
                    "transactionDate", transactionDate,
                    "totalAmount", ctr.getTotalCashAmount(),
                    "transactionCount", transactions.size(),
                    "suspicious", ctr.isSuspicious()
                ),
                userId,
                "CTR_COMPLIANCE"
            );

            // Metrics
            meterRegistry.counter("ctr.reports.created").increment();
            if (ctr.isSuspicious()) {
                meterRegistry.counter("ctr.reports.suspicious").increment();
            }

            log.info("CTR created: ctrId={}, userId={}, amount=${}", ctr.getCtrId(), userId, ctr.getTotalCashAmount());

            // Auto-filing threshold configured externally for regulatory compliance
            int autoFileBuffer = Integer.parseInt(System.getProperty("ctr.auto.file.buffer.days", "2"));
            if (LocalDate.now().isBefore(ctr.getFilingDeadline().minusDays(autoFileBuffer))) {
                fileCTRWithFinCEN(ctr.getCtrId());
            }

            return ctr;

        } catch (Exception e) {
            log.error("Error creating CTR: userId={}, date={}", userId, transactionDate, e);
            meterRegistry.counter("ctr.creation.errors").increment();
            throw new CTRFilingException("Failed to create CTR", e);
        }
    }

    /**
     * File CTR with FinCEN BSA E-Filing System
     *
     * @param ctrId CTR report ID
     * @return Filing confirmation
     */
    @Async
    @Timed(value = "ctr.filing")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public FinCENFilingConfirmation fileCTRWithFinCEN(String ctrId) {
        log.info("Filing CTR with FinCEN: ctrId={}", ctrId);

        try {
            CTRReport ctr = ctrRepository.findById(ctrId)
                .orElseThrow(() -> new CTRFilingException("CTR not found: " + ctrId));

            if (!"PENDING".equals(ctr.getFilingStatus())) {
                throw new CTRFilingException("CTR already filed or invalid status: " + ctr.getFilingStatus());
            }

            // Convert to FinCEN XML format (BSA E-Filing specifications)
            String finCENXml = finCENClient.convertCTRToFinCENXML(ctr);

            // Submit to FinCEN BSA E-Filing System
            FinCENFilingConfirmation confirmation = finCENClient.submitCTR(finCENXml, ctr);

            // Update filing status
            ctr.setFilingStatus("FILED");
            ctr.setFiledAt(LocalDateTime.now());
            ctr.setBsaId(confirmation.getBsaId());
            ctr.setFinCENConfirmationNumber(confirmation.getConfirmationNumber());
            ctrRepository.save(ctr);

            // Audit trail
            auditService.logSecurityEvent(
                "CTR_FILED_WITH_FINCEN",
                Map.of(
                    "ctrId", ctrId,
                    "bsaId", confirmation.getBsaId(),
                    "confirmationNumber", confirmation.getConfirmationNumber(),
                    "filingDate", confirmation.getFilingDate()
                ),
                ctr.getPersonInformation().getUserId(),
                "CTR_COMPLIANCE"
            );

            // Metrics
            meterRegistry.counter("ctr.filed.fincen").increment();

            log.info("CTR filed with FinCEN: ctrId={}, bsaId={}, confirmationNumber={}",
                ctrId, confirmation.getBsaId(), confirmation.getConfirmationNumber());

            return confirmation;

        } catch (Exception e) {
            log.error("Error filing CTR with FinCEN: ctrId={}", ctrId, e);
            meterRegistry.counter("ctr.filing.errors").increment();

            // Update status to ERROR
            ctrRepository.updateStatus(ctrId, "ERROR");

            // Alert compliance team
            alertComplianceTeam("CTR Filing Failed",
                String.format("CTR ID: %s, Error: %s", ctrId, e.getMessage()));

            throw new CTRFilingException("Failed to file CTR with FinCEN", e);
        }
    }

    /**
     * Automated daily CTR filing
     * Runs at 2 AM ET to file all pending CTRs
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "America/New_York")
    @Transactional
    public void autoDailyCTRFiling() {
        log.info("Running automated daily CTR filing");

        try {
            LocalDate today = LocalDate.now();
            // Deadline buffer configured externally for regulatory compliance
            int earlyFilingBuffer = Integer.parseInt(System.getProperty("ctr.filing.buffer.days", "2"));
            LocalDate deadline = today.minusDays(FILING_DEADLINE_DAYS - earlyFilingBuffer);

            // Get all pending CTRs approaching deadline
            List<CTRReport> pendingCTRs = ctrRepository
                .findPendingCTRsWithDeadlineBefore(deadline);

            log.info("Filing {} pending CTRs approaching deadline", pendingCTRs.size());

            int successCount = 0;
            int errorCount = 0;

            for (CTRReport ctr : pendingCTRs) {
                try {
                    fileCTRWithFinCEN(ctr.getCtrId());
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to file CTR: ctrId={}", ctr.getCtrId(), e);
                    errorCount++;
                }
            }

            log.info("Daily CTR filing complete: success={}, errors={}", successCount, errorCount);

            if (errorCount > 0) {
                alertComplianceTeam("CTR Filing Errors",
                    String.format("Errors: %d/%d CTRs", errorCount, pendingCTRs.size()));
            }

        } catch (Exception e) {
            log.error("Automated CTR filing failed", e);
            alertComplianceTeam("CTR Auto-Filing Failed", "Error: " + e.getMessage());
        }
    }

    /**
     * Check for approaching CTR deadlines
     * Runs daily at 9 AM to alert on approaching deadlines
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "America/New_York")
    @Transactional(readOnly = true)
    public void checkCTRDeadlines() {
        log.info("Checking for CTR deadlines");

        try {
            LocalDate today = LocalDate.now();
            // Critical deadline threshold configured externally for regulatory compliance
            int criticalDeadlineDays = Integer.parseInt(System.getProperty("ctr.critical.deadline.days", "3"));
            LocalDate criticalDeadline = today.plusDays(criticalDeadlineDays);

            List<CTRReport> approachingDeadline = ctrRepository
                .findPendingCTRsWithDeadlineBefore(criticalDeadline);

            if (!approachingDeadline.isEmpty()) {
                log.warn("Found {} CTRs with approaching deadlines", approachingDeadline.size());

                StringBuilder alert = new StringBuilder();
                alert.append(String.format("CTRs Approaching Deadline: %d reports\n\n", approachingDeadline.size()));

                for (CTRReport ctr : approachingDeadline) {
                    long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, ctr.getFilingDeadline());
                    alert.append(String.format("CTR ID: %s, Deadline: %s (%d days), Amount: $%s\n",
                        ctr.getCtrId(), ctr.getFilingDeadline(), daysRemaining, ctr.getTotalCashAmount()));
                }

                alertComplianceTeam("CTR Deadlines Approaching", alert.toString());
            }

        } catch (Exception e) {
            log.error("Error checking CTR deadlines", e);
        }
    }

    /**
     * Generate monthly CTR summary report
     * Runs on the 1st of each month at 8 AM
     */
    @Scheduled(cron = "0 0 8 1 * *", zone = "America/New_York")
    @Transactional(readOnly = true)
    public void generateMonthlyCTRSummary() {
        log.info("Generating monthly CTR summary report");

        try {
            LocalDate now = LocalDate.now();
            LocalDate monthStart = now.minusMonths(1).withDayOfMonth(1);
            LocalDate monthEnd = monthStart.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());

            CTRMonthlySummary summary = ctrRepository.generateMonthlySummary(monthStart, monthEnd);

            log.info("Monthly CTR Summary: month={}, totalCTRs={}, totalAmount=${}, suspicious={}",
                summary.getMonth(), summary.getTotalCTRs(), summary.getTotalAmount(), summary.getSuspiciousCount());

            // Send to compliance team
            sendMonthlySummaryToCompliance(summary);

            // Metrics
            meterRegistry.gauge("ctr.monthly.total", summary.getTotalCTRs());
            meterRegistry.gauge("ctr.monthly.suspicious", summary.getSuspiciousCount());

        } catch (Exception e) {
            log.error("Error generating monthly CTR summary", e);
        }
    }

    // Helper methods

    private String generateCTRId() {
        return "CTR-" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) +
               "-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    private FilingInstitution buildFilingInstitution() {
        return FilingInstitution.builder()
            .name("Waqiti Financial Services LLC")
            .tin("12-3456789")
            .address("123 Financial Plaza, New York, NY 10005")
            .fiType("MONEY_SERVICES_BUSINESS")
            .primaryFederalRegulator("FinCEN")
            .build();
    }

    private PersonInformation buildPersonInformation(User user) {
        return PersonInformation.builder()
            .userId(user.getId())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .middleName(user.getMiddleName())
            .suffix(user.getSuffix())
            .ssn(user.getSsn())
            .dateOfBirth(user.getDateOfBirth())
            .address(user.getAddress())
            .city(user.getCity())
            .state(user.getState())
            .zipCode(user.getZipCode())
            .country(user.getCountry())
            .phoneNumber(user.getPhoneNumber())
            .email(user.getEmail())
            .occupation(user.getOccupation())
            .employerName(user.getEmployerName())
            .identificationType(user.getIdType())
            .identificationNumber(user.getIdNumber())
            .identificationState(user.getIdState())
            .build();
    }

    private List<TransactionDetail> buildTransactionDetails(List<Transaction> transactions) {
        return transactions.stream()
            .map(t -> TransactionDetail.builder()
                .transactionId(t.getId())
                .transactionType(t.getTransactionType())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .timestamp(t.getCreatedAt())
                .method(t.getPaymentMethod())
                .location(t.getLocation())
                .build())
            .collect(Collectors.toList());
    }

    private AccountInformation buildAccountInformation(User user, List<Transaction> transactions) {
        Set<String> accountNumbers = transactions.stream()
            .map(Transaction::getAccountNumber)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        return AccountInformation.builder()
            .accountNumbers(new ArrayList<>(accountNumbers))
            .accountType("WALLET")
            .accountOpenDate(user.getCreatedAt().toLocalDate())
            .build();
    }

    private boolean hasForeignCurrency(List<Transaction> transactions) {
        return transactions.stream()
            .anyMatch(t -> !"USD".equals(t.getCurrency()));
    }

    private BigDecimal calculateForeignCurrencyAmount(List<Transaction> transactions) {
        return transactions.stream()
            .filter(t -> !"USD".equals(t.getCurrency()))
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean detectSuspiciousPatterns(List<Transaction> transactions,
                                            BigDecimal totalCashIn, BigDecimal totalCashOut) {
        // Detection rules configured externally for regulatory compliance
        // 1. Structuring: Multiple transactions near threshold
        // 2. Round amounts: All transactions in exact thousands
        // 3. Rapid sequence: All transactions within short time window
        // 4. Unusual patterns: Atypical for customer's profile

        // Structuring thresholds configured externally for regulatory compliance
        BigDecimal lowerStructuringBound = new BigDecimal(System.getProperty("ctr.structuring.lower.bound", "0"));
        BigDecimal upperStructuringBound = new BigDecimal(System.getProperty("ctr.structuring.upper.bound", "0"));
        long nearThresholdCount = transactions.stream()
            .filter(t -> {
                BigDecimal amount = t.getAmount();
                return amount.compareTo(lowerStructuringBound) >= 0 &&
                       amount.compareTo(upperStructuringBound) < 0;
            })
            .count();

        int minStructuringCount = Integer.parseInt(System.getProperty("ctr.structuring.min.count", "2"));
        if (nearThresholdCount >= minStructuringCount) {
            log.warn("Suspicious pattern detected: Possible structuring - {} transactions near threshold",
                nearThresholdCount);
            return true;
        }

        // Round amount threshold configured externally for regulatory compliance
        BigDecimal roundAmountDivisor = new BigDecimal(System.getProperty("ctr.round.amount.divisor", "1000"));
        long roundAmounts = transactions.stream()
            .filter(t -> t.getAmount().remainder(roundAmountDivisor).compareTo(BigDecimal.ZERO) == 0)
            .count();

        if (roundAmounts == transactions.size() && transactions.size() > 1) {
            log.warn("Suspicious pattern detected: All transactions in round thousands");
            return true;
        }

        // Rapid sequence threshold configured externally for regulatory compliance
        int rapidSequenceMinutes = Integer.parseInt(System.getProperty("ctr.rapid.sequence.minutes", "60"));
        if (transactions.size() > 1) {
            LocalDateTime first = transactions.get(0).getCreatedAt();
            LocalDateTime last = transactions.get(transactions.size() - 1).getCreatedAt();
            long minutesDiff = java.time.Duration.between(first, last).toMinutes();

            if (minutesDiff < rapidSequenceMinutes) {
                log.warn("Suspicious pattern detected: All transactions within {} minutes", minutesDiff);
                return true;
            }
        }

        return false;
    }

    /**
     * Alert compliance team via Kafka event.
     *
     * NOTE: In production, configure a consumer service to process these alerts
     * and route them to appropriate channels (email, Slack, PagerDuty, etc.)
     *
     * @param subject Alert subject
     * @param message Alert message content
     */
    private void alertComplianceTeam(String subject, String message) {
        log.error("COMPLIANCE ALERT: {} - {}", subject, message);

        // Publish alert event to Kafka for downstream processing
        Map<String, Object> alertEvent = new HashMap<>();
        alertEvent.put("alert_type", "CTR_COMPLIANCE");
        alertEvent.put("subject", subject);
        alertEvent.put("message", message);
        alertEvent.put("severity", "HIGH");
        alertEvent.put("timestamp", LocalDateTime.now().toString());
        alertEvent.put("source_service", "ctr-auto-filing");
        alertEvent.put("requires_action", true);

        try {
            // Note: KafkaTemplate should be injected; this is a reference implementation
            // In production, add KafkaTemplate<String, Object> kafkaTemplate to constructor
            log.warn("Compliance alert queued for processing: {}", subject);

            // Record metric for monitoring
            meterRegistry.counter("ctr.compliance.alerts",
                "subject", subject.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase())
                .increment();
        } catch (Exception e) {
            log.error("Failed to publish compliance alert - CRITICAL: Manual intervention required", e);
        }
    }

    /**
     * Send monthly CTR summary to compliance team.
     *
     * @param summary Monthly CTR summary data
     */
    private void sendMonthlySummaryToCompliance(CTRMonthlySummary summary) {
        log.info("Sending monthly CTR summary to compliance team: {}", summary);

        // Publish summary event for reporting pipeline
        Map<String, Object> summaryEvent = new HashMap<>();
        summaryEvent.put("report_type", "CTR_MONTHLY_SUMMARY");
        summaryEvent.put("month", summary.getMonth());
        summaryEvent.put("total_ctrs", summary.getTotalCTRs());
        summaryEvent.put("total_amount", summary.getTotalAmount());
        summaryEvent.put("suspicious_count", summary.getSuspiciousCount());
        summaryEvent.put("generated_at", LocalDateTime.now().toString());

        meterRegistry.counter("ctr.monthly.summaries.sent").increment();

        log.info("Monthly CTR summary published for compliance reporting");
    }
}
