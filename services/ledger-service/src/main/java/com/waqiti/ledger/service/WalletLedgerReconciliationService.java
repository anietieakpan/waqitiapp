package com.waqiti.ledger.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * P1 CRITICAL: Wallet-Ledger Reconciliation Service
 *
 * Automatically reconciles wallet balances against ledger entries daily.
 * Detects and alerts on any discrepancies to maintain financial integrity.
 *
 * Reconciliation Process:
 * 1. Query all wallet balances from wallet-service
 * 2. Calculate expected balances from ledger entries
 * 3. Compare actual vs expected
 * 4. Generate reconciliation report
 * 5. Alert on mismatches
 *
 * Runs: Daily at 2:00 AM (low traffic period)
 *
 * Compliance: SOX 404, PCI DSS, E-Money Regulations
 *
 * @author Waqiti Engineering Team
 * @since 2025-10-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletLedgerReconciliationService {

    private final JdbcTemplate jdbcTemplate;
    private final WebClient.Builder webClientBuilder;

    private static final String WALLET_SERVICE_URL = "${wallet-service.url:http://localhost:8082}";

    /**
     * Scheduled reconciliation job - runs daily at 2:00 AM.
     *
     * Cron: 0 0 2 * * * (second minute hour day month weekday)
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional(readOnly = true)
    public void performDailyReconciliation() {
        log.info("🔍 Starting daily wallet-ledger reconciliation at {}", LocalDateTime.now());

        long startTime = System.currentTimeMillis();
        ReconciliationReport report = new ReconciliationReport();

        try {
            // Step 1: Get all wallet balances from ledger
            Map<String, BigDecimal> ledgerBalances = getLedgerBalances();
            log.info("Retrieved {} wallet balances from ledger", ledgerBalances.size());

            // Step 2: Get all wallet balances from wallet-service
            Map<String, BigDecimal> walletBalances = getWalletBalances();
            log.info("Retrieved {} wallet balances from wallet-service", walletBalances.size());

            // Step 3: Compare balances
            List<BalanceMismatch> mismatches = compareBalances(ledgerBalances, walletBalances);

            // Step 4: Generate report
            report.setReconciliationDate(LocalDateTime.now());
            report.setTotalWallets(ledgerBalances.size());
            report.setMismatchCount(mismatches.size());
            report.setMismatches(mismatches);
            report.setSuccess(mismatches.isEmpty());

            // Step 5: Alert if mismatches found
            if (!mismatches.isEmpty()) {
                handleMismatches(mismatches, report);
            }

            // Step 6: Save report
            saveReconciliationReport(report);

            long duration = System.currentTimeMillis() - startTime;
            log.info("✅ Reconciliation completed in {}ms - {} mismatches found",
                    duration, mismatches.size());

        } catch (Exception e) {
            log.error("❌ Reconciliation failed: {}", e.getMessage(), e);
            report.setSuccess(false);
            report.setErrorMessage(e.getMessage());
            alertReconciliationFailure(e);
        }
    }

    /**
     * Get wallet balances from ledger entries.
     *
     * Sums all CREDIT and DEBIT entries per wallet to calculate expected balance.
     */
    private Map<String, BigDecimal> getLedgerBalances() {
        String sql = """
            SELECT
                account_id as wallet_id,
                SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END) as balance
            FROM ledger_entries
            WHERE account_type = 'WALLET_LIABILITY'
              AND status != 'REVERSED'
            GROUP BY account_id
            """;

        Map<String, BigDecimal> balances = new HashMap<>();

        jdbcTemplate.query(sql, rs -> {
            String walletId = rs.getString("wallet_id");
            BigDecimal balance = rs.getBigDecimal("balance");
            balances.put(walletId, balance != null ? balance : BigDecimal.ZERO);
        });

        return balances;
    }

    /**
     * ✅ CRITICAL PRODUCTION FIX: Get wallet balances from wallet-service via REST API
     * Calls wallet-service internal endpoint to fetch all wallet balances for reconciliation
     */
    private Map<String, BigDecimal> getWalletBalances() {
        log.info("Fetching wallet balances from wallet-service for reconciliation");

        try {
            // Call wallet-service internal API endpoint
            ResponseEntity<List<WalletBalanceDto>> response = webClient.get()
                .uri("http://wallet-service/api/v1/internal/wallets/balances")
                .header("X-Internal-Service", "ledger-service")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<WalletBalanceDto>>() {})
                .block(Duration.ofSeconds(30));

            if (response == null || response.getBody() == null) {
                log.error("CRITICAL: Wallet service returned null response for balance reconciliation");
                throw new ReconciliationException("Wallet service returned null response");
            }

            List<WalletBalanceDto> walletBalances = response.getBody();
            log.info("Retrieved {} wallet balances from wallet-service", walletBalances.size());

            // Convert list to map: walletId -> balance
            return walletBalances.stream()
                .collect(Collectors.toMap(
                    dto -> dto.getWalletId().toString(),
                    WalletBalanceDto::getBalance,
                    (existing, replacement) -> {
                        log.warn("Duplicate wallet ID in response: {}", existing);
                        return replacement; // Use latest value
                    }
                ));

        } catch (WebClientResponseException e) {
            log.error("HTTP error calling wallet-service: status={}, body={}",
                e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new ReconciliationException("Failed to fetch wallet balances: HTTP " + e.getStatusCode(), e);

        } catch (ResourceAccessException e) {
            log.error("Network error calling wallet-service - service may be down", e);
            throw new ReconciliationException("Wallet service unreachable", e);

        } catch (Exception e) {
            log.error("Unexpected error fetching wallet balances", e);
            throw new ReconciliationException("Failed to fetch wallet balances: " + e.getMessage(), e);
        }
    }

    /**
     * Compare ledger vs wallet balances and identify mismatches.
     */
    private List<BalanceMismatch> compareBalances(
            Map<String, BigDecimal> ledgerBalances,
            Map<String, BigDecimal> walletBalances) {

        List<BalanceMismatch> mismatches = new ArrayList<>();
        BigDecimal tolerance = new BigDecimal("0.01"); // 1 cent tolerance for rounding

        // Check all wallets in ledger
        for (Map.Entry<String, BigDecimal> entry : ledgerBalances.entrySet()) {
            String walletId = entry.getKey();
            BigDecimal ledgerBalance = entry.getValue();
            BigDecimal walletBalance = walletBalances.getOrDefault(walletId, BigDecimal.ZERO);

            BigDecimal difference = ledgerBalance.subtract(walletBalance).abs();

            if (difference.compareTo(tolerance) > 0) {
                BalanceMismatch mismatch = new BalanceMismatch();
                mismatch.setWalletId(walletId);
                mismatch.setLedgerBalance(ledgerBalance);
                mismatch.setWalletBalance(walletBalance);
                mismatch.setDifference(difference);
                mismatch.setDetectedAt(LocalDateTime.now());

                mismatches.add(mismatch);

                log.error("🚨 BALANCE MISMATCH: wallet={}, ledger={}, wallet={}, diff={}",
                        walletId, ledgerBalance, walletBalance, difference);
            }
        }

        // Check for wallets in wallet-service but not in ledger
        for (String walletId : walletBalances.keySet()) {
            if (!ledgerBalances.containsKey(walletId)) {
                BigDecimal walletBalance = walletBalances.get(walletId);

                if (walletBalance.compareTo(BigDecimal.ZERO) != 0) {
                    BalanceMismatch mismatch = new BalanceMismatch();
                    mismatch.setWalletId(walletId);
                    mismatch.setLedgerBalance(BigDecimal.ZERO);
                    mismatch.setWalletBalance(walletBalance);
                    mismatch.setDifference(walletBalance);
                    mismatch.setDetectedAt(LocalDateTime.now());

                    mismatches.add(mismatch);

                    log.error("🚨 ORPHANED WALLET: wallet={} has balance={} but no ledger entries",
                            walletId, walletBalance);
                }
            }
        }

        return mismatches;
    }

    /**
     * Handle mismatches - alert finance team.
     */
    private void handleMismatches(List<BalanceMismatch> mismatches, ReconciliationReport report) {
        log.error("🚨🚨🚨 CRITICAL: {} WALLET-LEDGER MISMATCHES DETECTED 🚨🚨🚨", mismatches.size());

        BigDecimal totalDiscrepancy = mismatches.stream()
                .map(BalanceMismatch::getDifference)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.error("Total discrepancy amount: ${}", totalDiscrepancy);

        // Alert finance team
        alertFinanceTeam(mismatches, totalDiscrepancy);

        // Create high-priority tickets
        for (BalanceMismatch mismatch : mismatches) {
            createReconciliationTicket(mismatch);
        }
    }

    /**
     * Alert finance team via multiple channels.
     */
    private void alertFinanceTeam(List<BalanceMismatch> mismatches, BigDecimal totalDiscrepancy) {
        log.error("🚨 FINANCE TEAM ALERT: Wallet-Ledger Reconciliation Failure");
        log.error("Mismatches: {}", mismatches.size());
        log.error("Total Discrepancy: ${}", totalDiscrepancy);

        // TODO: PagerDuty alert (P1 for discrepancy > $1000, P2 otherwise)
        // TODO: Slack #finance-ops + @channel
        // TODO: Email: cfo@example.com, finance@example.com, engineering-oncall@example.com
    }

    /**
     * Create ticket for each mismatch.
     */
    private void createReconciliationTicket(BalanceMismatch mismatch) {
        log.error("📋 Creating reconciliation ticket for wallet: {}", mismatch.getWalletId());

        // TODO: Create JIRA ticket with:
        // - Summary: "Wallet-Ledger Mismatch: {walletId}"
        // - Priority: High
        // - Assignee: Finance Ops team
        // - Description: Full mismatch details
    }

    /**
     * Save reconciliation report to database.
     */
    private void saveReconciliationReport(ReconciliationReport report) {
        String sql = """
            INSERT INTO reconciliation_reports
            (id, reconciliation_date, total_wallets, mismatch_count, success, error_message, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.update(sql,
                UUID.randomUUID(),
                report.getReconciliationDate(),
                report.getTotalWallets(),
                report.getMismatchCount(),
                report.isSuccess(),
                report.getErrorMessage(),
                LocalDateTime.now()
        );

        log.info("📊 Reconciliation report saved");
    }

    /**
     * Alert on reconciliation job failure.
     */
    private void alertReconciliationFailure(Exception e) {
        log.error("🚨 RECONCILIATION JOB FAILED: {}", e.getMessage());

        // TODO: PagerDuty P1 alert
        // TODO: Slack #finance-ops + #engineering-oncall
        // TODO: Email oncall engineers
    }

    // DTOs

    static class ReconciliationReport {
        private LocalDateTime reconciliationDate;
        private int totalWallets;
        private int mismatchCount;
        private List<BalanceMismatch> mismatches;
        private boolean success;
        private String errorMessage;

        // Getters and setters
        public LocalDateTime getReconciliationDate() { return reconciliationDate; }
        public void setReconciliationDate(LocalDateTime reconciliationDate) { this.reconciliationDate = reconciliationDate; }
        public int getTotalWallets() { return totalWallets; }
        public void setTotalWallets(int totalWallets) { this.totalWallets = totalWallets; }
        public int getMismatchCount() { return mismatchCount; }
        public void setMismatchCount(int mismatchCount) { this.mismatchCount = mismatchCount; }
        public List<BalanceMismatch> getMismatches() { return mismatches; }
        public void setMismatches(List<BalanceMismatch> mismatches) { this.mismatches = mismatches; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    static class BalanceMismatch {
        private String walletId;
        private BigDecimal ledgerBalance;
        private BigDecimal walletBalance;
        private BigDecimal difference;
        private LocalDateTime detectedAt;

        // Getters and setters
        public String getWalletId() { return walletId; }
        public void setWalletId(String walletId) { this.walletId = walletId; }
        public BigDecimal getLedgerBalance() { return ledgerBalance; }
        public void setLedgerBalance(BigDecimal ledgerBalance) { this.ledgerBalance = ledgerBalance; }
        public BigDecimal getWalletBalance() { return walletBalance; }
        public void setWalletBalance(BigDecimal walletBalance) { this.walletBalance = walletBalance; }
        public BigDecimal getDifference() { return difference; }
        public void setDifference(BigDecimal difference) { this.difference = difference; }
        public LocalDateTime getDetectedAt() { return detectedAt; }
        public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }
    }
}
