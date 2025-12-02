package com.waqiti.wallet.job;

import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.service.WalletReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Daily Wallet Reconciliation Job
 *
 * Runs every day at 2 AM to reconcile wallet balances against ledger entries.
 * Critical for detecting discrepancies, preventing financial losses, and
 * ensuring accounting accuracy.
 *
 * Features:
 * - Compares wallet balance vs sum of ledger entries
 * - Auto-corrects minor discrepancies (<$0.10)
 * - Alerts finance team for major discrepancies
 * - Generates daily reconciliation reports
 * - Tracks reconciliation metrics
 *
 * Regulatory Compliance:
 * - SOX: Daily financial reconciliation requirement
 * - Internal Controls: Automated verification
 * - Audit Trail: Complete reconciliation history
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-10-02
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletReconciliationJob {

    private final WalletReconciliationService reconciliationService;
    private final WalletRepository walletRepository;

    /**
     * Daily reconciliation job - runs at 2 AM every day
     * Cron: 0 0 2 * * * (second minute hour day month weekday)
     */
    @Scheduled(cron = "${wallet.reconciliation.schedule:0 0 2 * * *}")
    public void reconcileAllWallets() {
        LocalDateTime startTime = LocalDateTime.now();
        log.info("🔄 RECONCILIATION JOB STARTING: Daily wallet reconciliation at {}", startTime);

        try {
            // Get count of active wallets
            long totalWallets = walletRepository.countActiveWallets();
            log.info("Reconciling {} active wallets", totalWallets);

            // Execute reconciliation
            var result = reconciliationService.reconcileAllWallets();

            // Log results
            LocalDateTime endTime = LocalDateTime.now();
            long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();

            log.info("✅ RECONCILIATION JOB COMPLETE:");
            log.info("  - Total Wallets: {}", result.getTotalWallets());
            log.info("  - Reconciled: {}", result.getReconciledWallets());
            log.info("  - Discrepancies Found: {}", result.getDiscrepanciesFound());
            log.info("  - Auto-Corrected: {}", result.getAutoCorrected());
            log.info("  - Requires Manual Review: {}", result.getRequiresManualReview());
            log.info("  - Duration: {} seconds", durationSeconds);

            // Alert if major issues found
            if (result.getRequiresManualReview() > 0) {
                log.warn("⚠️ {} wallets require manual review - Finance team notified",
                        result.getRequiresManualReview());
            }

        } catch (Exception e) {
            log.error("❌ RECONCILIATION JOB FAILED: {}", e.getMessage(), e);
            // Send alert to operations team
            reconciliationService.alertReconciliationFailure(e);
        }
    }

    /**
     * Emergency reconciliation - can be triggered manually
     */
    public void reconcileWallet(String walletId) {
        log.info("🔧 MANUAL RECONCILIATION: Wallet {}", walletId);

        try {
            var result = reconciliationService.reconcileWallet(java.util.UUID.fromString(walletId));

            if (result.hasDiscrepancy()) {
                log.warn("Discrepancy found in wallet {}: Expected={}, Actual={}, Difference={}",
                        walletId, result.getExpectedBalance(), result.getActualBalance(),
                        result.getDiscrepancyAmount());
            } else {
                log.info("Wallet {} reconciled successfully - No discrepancies", walletId);
            }

        } catch (Exception e) {
            log.error("Failed to reconcile wallet {}: {}", walletId, e.getMessage(), e);
            throw new RuntimeException("Reconciliation failed for wallet " + walletId, e);
        }
    }
}
