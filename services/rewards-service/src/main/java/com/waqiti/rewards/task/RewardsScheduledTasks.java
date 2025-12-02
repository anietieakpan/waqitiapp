package com.waqiti.rewards.task;

import com.waqiti.rewards.service.RewardsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class RewardsScheduledTasks {

    private final RewardsService rewardsService;

    /**
     * Process pending cashback transactions every 5 minutes
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void processPendingCashback() {
        log.info("Starting scheduled processing of pending cashback transactions");
        try {
            int processed = rewardsService.processPendingCashback();
            log.info("Processed {} pending cashback transactions", processed);
        } catch (Exception e) {
            log.error("Error processing pending cashback", e);
        }
    }

    /**
     * Process pending points transactions every 5 minutes
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 90000)
    public void processPendingPoints() {
        log.info("Starting scheduled processing of pending points transactions");
        try {
            int processed = rewardsService.processPendingPoints();
            log.info("Processed {} pending points transactions", processed);
        } catch (Exception e) {
            log.error("Error processing pending points", e);
        }
    }

    /**
     * Update user tiers daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void updateUserTiers() {
        log.info("Starting daily user tier update process");
        try {
            int updated = rewardsService.updateAllUserTiers();
            log.info("Updated tiers for {} users", updated);
        } catch (Exception e) {
            log.error("Error updating user tiers", e);
        }
    }

    /**
     * Expire points quarterly (run on 1st of Jan, Apr, Jul, Oct at 3 AM)
     */
    @Scheduled(cron = "0 0 3 1 1,4,7,10 *")
    public void expirePoints() {
        log.info("Starting quarterly points expiration process");
        try {
            LocalDate expirationDate = LocalDate.now().minusYears(1);
            int expired = rewardsService.expirePointsOlderThan(expirationDate);
            log.info("Expired points for {} users", expired);
        } catch (Exception e) {
            log.error("Error expiring points", e);
        }
    }

    /**
     * Process monthly statements on the 1st of each month at 4 AM
     */
    @Scheduled(cron = "0 0 4 1 * *")
    public void generateMonthlyStatements() {
        log.info("Starting monthly rewards statement generation");
        try {
            LocalDate previousMonth = LocalDate.now().minusMonths(1);
            int generated = rewardsService.generateMonthlyStatements(
                previousMonth.getYear(), 
                previousMonth.getMonthValue()
            );
            log.info("Generated {} monthly statements", generated);
        } catch (Exception e) {
            log.error("Error generating monthly statements", e);
        }
    }

    /**
     * Check and activate campaigns every hour
     */
    @Scheduled(fixedDelay = 3600000)
    public void manageCampaigns() {
        log.info("Checking campaign statuses");
        try {
            // Activate campaigns that should start
            int activated = rewardsService.activateScheduledCampaigns();
            log.info("Activated {} campaigns", activated);
            
            // Deactivate expired campaigns
            int deactivated = rewardsService.deactivateExpiredCampaigns();
            log.info("Deactivated {} expired campaigns", deactivated);
        } catch (Exception e) {
            log.error("Error managing campaigns", e);
        }
    }

    /**
     * Clean up old processed transactions weekly on Sunday at 3 AM
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    public void cleanupOldTransactions() {
        log.info("Starting weekly cleanup of old transactions");
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(6);
            int cleaned = rewardsService.archiveOldTransactions(cutoffDate);
            log.info("Archived {} old transactions", cleaned);
        } catch (Exception e) {
            log.error("Error cleaning up old transactions", e);
        }
    }

    /**
     * Reconcile rewards balances daily at 1 AM
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void reconcileBalances() {
        log.info("Starting daily balance reconciliation");
        try {
            int reconciled = rewardsService.reconcileAllBalances();
            log.info("Reconciled balances for {} accounts", reconciled);
        } catch (Exception e) {
            log.error("Error reconciling balances", e);
        }
    }

    /**
     * Send tier upgrade notifications daily at 10 AM
     */
    @Scheduled(cron = "0 0 10 * * *")
    public void sendTierNotifications() {
        log.info("Checking for tier upgrade notifications");
        try {
            int sent = rewardsService.sendTierUpgradeNotifications();
            log.info("Sent {} tier upgrade notifications", sent);
        } catch (Exception e) {
            log.error("Error sending tier notifications", e);
        }
    }

    /**
     * Calculate and cache merchant rewards rates every 30 minutes
     */
    @Scheduled(fixedDelay = 1800000)
    public void updateMerchantRewardsCache() {
        log.debug("Updating merchant rewards cache");
        try {
            rewardsService.refreshMerchantRewardsCache();
            log.debug("Successfully updated merchant rewards cache");
        } catch (Exception e) {
            log.error("Error updating merchant rewards cache", e);
        }
    }

    /**
     * Monitor and alert on suspicious rewards activity every 15 minutes
     */
    @Scheduled(fixedDelay = 900000)
    public void monitorSuspiciousActivity() {
        log.debug("Monitoring for suspicious rewards activity");
        try {
            int flagged = rewardsService.detectAndFlagSuspiciousActivity();
            if (flagged > 0) {
                log.warn("Flagged {} accounts for suspicious rewards activity", flagged);
            }
        } catch (Exception e) {
            log.error("Error monitoring suspicious activity", e);
        }
    }
}