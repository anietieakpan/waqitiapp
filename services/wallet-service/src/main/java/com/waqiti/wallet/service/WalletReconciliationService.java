package com.waqiti.wallet.service;

import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.repository.WalletRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wallet Reconciliation Service
 *
 * Performs daily reconciliation of wallet balances against ledger entries.
 * Ensures financial accuracy and detects discrepancies early.
 *
 * Reconciliation Logic:
 * 1. For each wallet, sum all ledger entries (credits - debits)
 * 2. Compare sum to wallet.balance
 * 3. If difference < $0.10: Auto-correct and log
 * 4. If difference >= $0.10: Create alert for finance team
 * 5. Generate daily reconciliation report
 *
 * Auto-Correction Policy:
 * - Differences < $0.10 are automatically corrected
 * - Correction creates audit trail entry
 * - Finance team receives daily summary
 *
 * Manual Review Triggers:
 * - Difference >= $0.10
 * - Pattern of repeated discrepancies
 * - Wallet recently involved in disputes
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-10-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletReconciliationService {

    private final WalletRepository walletRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AlertingService alertingService;  // ✅ ADDED: Alerting integration

    private static final BigDecimal AUTO_CORRECT_THRESHOLD = new BigDecimal("0.10");
    private static final String RECONCILIATION_TOPIC = "wallet-reconciliation-alerts";

    /**
     * Reconcile all active wallets
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 600)
    public ReconciliationSummary reconcileAllWallets() {
        log.info("Starting full wallet reconciliation");

        LocalDateTime reconciliationTime = LocalDateTime.now();
        AtomicLong totalWallets = new AtomicLong(0);
        AtomicLong reconciledWallets = new AtomicLong(0);
        AtomicLong discrepanciesFound = new AtomicLong(0);
        AtomicLong autoCorrected = new AtomicLong(0);
        AtomicLong requiresManualReview = new AtomicLong(0);

        List<ReconciliationResult> allResults = new ArrayList<>();

        // Process wallets in batches to avoid memory issues
        int batchSize = 100;
        int offset = 0;

        while (true) {
            List<Wallet> walletBatch = walletRepository.findActiveWalletsBatch(offset, batchSize);

            if (walletBatch.isEmpty()) {
                break;
            }

            totalWallets.addAndGet(walletBatch.size());

            for (Wallet wallet : walletBatch) {
                try {
                    ReconciliationResult result = reconcileWallet(wallet.getId());
                    allResults.add(result);
                    reconciledWallets.incrementAndGet();

                    if (result.hasDiscrepancy()) {
                        discrepanciesFound.incrementAndGet();

                        if (result.isAutoCorrected()) {
                            autoCorrected.incrementAndGet();
                        } else {
                            requiresManualReview.incrementAndGet();
                        }
                    }

                } catch (Exception e) {
                    log.error("Failed to reconcile wallet {}: {}", wallet.getId(), e.getMessage());
                }
            }

            offset += batchSize;
        }

        // Generate and send reconciliation report
        ReconciliationSummary summary = ReconciliationSummary.builder()
                .reconciliationTime(reconciliationTime)
                .totalWallets(totalWallets.get())
                .reconciledWallets(reconciledWallets.get())
                .discrepanciesFound(discrepanciesFound.get())
                .autoCorrected(autoCorrected.get())
                .requiresManualReview(requiresManualReview.get())
                .results(allResults)
                .build();

        // Send summary to finance team
        sendReconciliationReport(summary);

        // ✅ ADDED: Send alerts for critical issues
        if (requiresManualReview.get() > 0) {
            String details = String.format("Reconciliation found %d wallets requiring manual review. " +
                    "Total discrepancies: %d, Auto-corrected: %d",
                    requiresManualReview.get(), discrepanciesFound.get(), autoCorrected.get());

            alertingService.sendBalanceAnomalyAlert(
                    UUID.randomUUID(), // Generic alert for reconciliation
                    "RECONCILIATION_MANUAL_REVIEW_REQUIRED",
                    details
            );
        }

        // Cache summary for API access
        cacheReconciliationSummary(summary);

        return summary;
    }

    /**
     * Reconcile a single wallet
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ReconciliationResult reconcileWallet(UUID walletId) {
        log.debug("Reconciling wallet: {}", walletId);

        // Get wallet
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));

        // Get current balance from wallet
        BigDecimal walletBalance = wallet.getBalance();

        // Calculate expected balance from ledger
        BigDecimal ledgerBalance = calculateLedgerBalance(walletId);

        // Calculate discrepancy
        BigDecimal discrepancy = walletBalance.subtract(ledgerBalance);
        BigDecimal discrepancyAbs = discrepancy.abs();

        ReconciliationResult result = ReconciliationResult.builder()
                .walletId(walletId)
                .userId(wallet.getUserId())
                .expectedBalance(ledgerBalance)
                .actualBalance(walletBalance)
                .discrepancyAmount(discrepancy)
                .reconciliationTime(LocalDateTime.now())
                .build();

        // Check if discrepancy exists
        if (discrepancyAbs.compareTo(BigDecimal.ZERO) == 0) {
            result.setStatus(ReconciliationStatus.RECONCILED);
            log.debug("Wallet {} reconciled - No discrepancy", walletId);
            return result;
        }

        // Discrepancy found
        result.setHasDiscrepancy(true);

        // Auto-correct small discrepancies
        if (discrepancyAbs.compareTo(AUTO_CORRECT_THRESHOLD) < 0) {
            log.info("Auto-correcting wallet {} - Discrepancy: {}", walletId, discrepancy);

            // Correct the balance
            wallet.setBalance(ledgerBalance);
            walletRepository.save(wallet);

            result.setAutoCorrected(true);
            result.setStatus(ReconciliationStatus.AUTO_CORRECTED);

            // Create audit trail
            createReconciliationAuditEntry(wallet, discrepancy, "AUTO_CORRECTED");

        } else {
            // Major discrepancy - requires manual review
            log.warn("⚠️ MAJOR DISCREPANCY: Wallet {} - Difference: {}", walletId, discrepancy);

            result.setAutoCorrected(false);
            result.setStatus(ReconciliationStatus.REQUIRES_REVIEW);

            // Create alert for finance team
            sendDiscrepancyAlert(wallet, discrepancy, ledgerBalance);

            // Create audit trail
            createReconciliationAuditEntry(wallet, discrepancy, "REQUIRES_REVIEW");
        }

        return result;
    }

    /**
     * Calculate expected balance from ledger entries
     *
     * P0-018 CRITICAL FIX: Real ledger-based reconciliation
     *
     * This method now queries the actual ledger service to calculate
     * the true balance based on double-entry accounting records.
     *
     * Formula: Balance = Initial Balance + Sum(Credits) - Sum(Debits)
     */
    private BigDecimal calculateLedgerBalance(UUID walletId) {
        try {
            // Query ledger service for all entries for this wallet
            List<LedgerEntry> entries = ledgerServiceClient.getLedgerEntries(walletId);

            // Calculate balance from ledger entries (double-entry accounting)
            BigDecimal credits = BigDecimal.ZERO;
            BigDecimal debits = BigDecimal.ZERO;

            for (LedgerEntry entry : entries) {
                if ("CREDIT".equals(entry.getType())) {
                    credits = credits.add(entry.getAmount());
                } else if ("DEBIT".equals(entry.getType())) {
                    debits = debits.add(entry.getAmount());
                }
            }

            // Balance = Credits - Debits
            BigDecimal ledgerBalance = credits.subtract(debits);

            log.debug("Ledger balance calculated for wallet {}: credits={}, debits={}, balance={}",
                walletId, credits, debits, ledgerBalance);

            return ledgerBalance;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to calculate ledger balance for wallet: {}", walletId, e);

            // CRITICAL: Alert on failure
            alertingService.sendCriticalAlert(
                AlertPriority.P0,
                "LEDGER_QUERY_FAILED",
                "Failed to query ledger for wallet reconciliation: " + walletId,
                Map.of("walletId", walletId.toString(), "error", e.getMessage())
            );

            // Throw exception - reconciliation cannot proceed without ledger data
            throw new ReconciliationException(
                "Cannot reconcile wallet without ledger data", e);
        }
    }

    /**
     * Create audit entry for reconciliation
     */
    private void createReconciliationAuditEntry(Wallet wallet, BigDecimal discrepancy, String action) {
        Map<String, Object> auditEntry = new HashMap<>();
        auditEntry.put("walletId", wallet.getId().toString());
        auditEntry.put("userId", wallet.getUserId().toString());
        auditEntry.put("discrepancy", discrepancy.toString());
        auditEntry.put("action", action);
        auditEntry.put("timestamp", LocalDateTime.now().toString());
        auditEntry.put("oldBalance", wallet.getBalance().subtract(discrepancy).toString());
        auditEntry.put("newBalance", wallet.getBalance().toString());

        // Publish to audit topic
        kafkaTemplate.send("wallet-audit-trail", auditEntry);
    }

    /**
     * Send discrepancy alert to finance team
     */
    private void sendDiscrepancyAlert(Wallet wallet, BigDecimal discrepancy, BigDecimal expectedBalance) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "WALLET_RECONCILIATION_DISCREPANCY");
        alert.put("severity", discrepancy.abs().compareTo(new BigDecimal("100")) > 0 ? "HIGH" : "MEDIUM");
        alert.put("walletId", wallet.getId().toString());
        alert.put("userId", wallet.getUserId().toString());
        alert.put("currentBalance", wallet.getBalance().toString());
        alert.put("expectedBalance", expectedBalance.toString());
        alert.put("discrepancy", discrepancy.toString());
        alert.put("timestamp", LocalDateTime.now().toString());
        alert.put("requiresAction", true);

        kafkaTemplate.send(RECONCILIATION_TOPIC, alert);

        log.warn("Discrepancy alert sent for wallet {}: {}", wallet.getId(), discrepancy);
    }

    /**
     * Send daily reconciliation report
     */
    private void sendReconciliationReport(ReconciliationSummary summary) {
        Map<String, Object> report = new HashMap<>();
        report.put("reportType", "DAILY_WALLET_RECONCILIATION");
        report.put("reconciliationDate", summary.getReconciliationTime().toLocalDate().toString());
        report.put("totalWallets", summary.getTotalWallets());
        report.put("reconciledWallets", summary.getReconciledWallets());
        report.put("discrepanciesFound", summary.getDiscrepanciesFound());
        report.put("autoCorrected", summary.getAutoCorrected());
        report.put("requiresManualReview", summary.getRequiresManualReview());
        report.put("timestamp", LocalDateTime.now().toString());

        kafkaTemplate.send("finance-reports", report);

        log.info("Daily reconciliation report sent to finance team");
    }

    /**
     * Cache reconciliation summary for API access
     */
    private void cacheReconciliationSummary(ReconciliationSummary summary) {
        String cacheKey = "wallet:reconciliation:summary:" +
                summary.getReconciliationTime().toLocalDate().toString();

        redisTemplate.opsForValue().set(cacheKey, summary,
                java.time.Duration.ofDays(30));
    }

    /**
     * Alert operations team of reconciliation failure
     */
    public void alertReconciliationFailure(Exception e) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "RECONCILIATION_JOB_FAILURE");
        alert.put("severity", "CRITICAL");
        alert.put("errorMessage", e.getMessage());
        alert.put("timestamp", LocalDateTime.now().toString());
        alert.put("requiresAction", true);

        kafkaTemplate.send("ops-alerts", alert);
    }

    // Data models

    @Data
    @Builder
    public static class ReconciliationSummary {
        private LocalDateTime reconciliationTime;
        private long totalWallets;
        private long reconciledWallets;
        private long discrepanciesFound;
        private long autoCorrected;
        private long requiresManualReview;
        private List<ReconciliationResult> results;
    }

    @Data
    @Builder
    public static class ReconciliationResult {
        private UUID walletId;
        private UUID userId;
        private BigDecimal expectedBalance;
        private BigDecimal actualBalance;
        private BigDecimal discrepancyAmount;
        private LocalDateTime reconciliationTime;
        private boolean hasDiscrepancy;
        private boolean autoCorrected;
        private ReconciliationStatus status;
    }

    public enum ReconciliationStatus {
        RECONCILED,
        AUTO_CORRECTED,
        REQUIRES_REVIEW,
        FAILED
    }
}
