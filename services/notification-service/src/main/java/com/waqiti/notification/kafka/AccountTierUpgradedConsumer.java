package com.waqiti.notification.kafka;

import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.notification.domain.NotificationChannel;
import com.waqiti.notification.domain.NotificationPriority;
import com.waqiti.notification.domain.NotificationType;
import com.waqiti.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FIX #34: AccountTierUpgradedConsumer
 * Notifies users when account tier is upgraded with new benefits
 * Impact: Increases engagement with premium features
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountTierUpgradedConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "account.tier.upgraded", groupId = "notification-account-tier-upgraded")
    public void handle(AccountTierUpgradedEvent event, Acknowledgment ack) {
        try {
            log.info("⬆️ ACCOUNT TIER UPGRADED: userId={}, oldTier={}, newTier={}",
                event.getUserId(), event.getOldTier(), event.getNewTier());

            String key = "account:tier:upgraded:" + event.getUserId() + ":" + event.getUpgradedAt();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                🎉 Congratulations! Your Account Has Been Upgraded!

                Your Waqiti account has been upgraded to %s tier!

                Tier Upgrade:
                - Previous Tier: %s
                - New Tier: %s
                - Upgraded: %s
                - Upgrade Reason: %s

                %s

                %s

                %s

                Getting Started with Your New Benefits:
                1. Explore your enhanced dashboard: https://example.com/dashboard
                2. Review all benefits: https://example.com/account/benefits
                3. Set up premium features you want to use
                4. Contact premium support if you need help

                Premium Support:
                As a %s member, you now have access to priority support:
                • Premium Support Line: 1-800-WAQITI-VIP
                • Priority Email: premium@example.com
                • Average Response Time: < 1 hour
                • Dedicated Account Manager: %s

                Questions About Your Upgrade?
                Email: upgrades@example.com
                Phone: 1-800-WAQITI-TIER

                Thank you for being a valued Waqiti customer!
                """,
                event.getNewTier(),
                event.getOldTier(),
                event.getNewTier(),
                event.getUpgradedAt(),
                getUpgradeReason(event.getUpgradeReason()),
                getNewBenefits(event.getNewTier()),
                getTierComparison(event.getOldTier(), event.getNewTier()),
                getPricingInfo(event.getNewTier(), event.getMonthlyFee(), event.isWaivedFirstMonth()),
                event.getNewTier(),
                event.hasAccountManager() ? "Yes (will contact you within 48 hours)" : "Available upon request");

            notificationService.sendNotification(event.getUserId(), NotificationType.ACCOUNT_TIER_UPGRADED,
                NotificationChannel.EMAIL, NotificationPriority.HIGH,
                String.format("🎉 Welcome to %s Tier!", event.getNewTier()), message, Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.ACCOUNT_TIER_UPGRADED,
                NotificationChannel.PUSH, NotificationPriority.HIGH,
                "Account Upgraded!",
                String.format("Congratulations! You've been upgraded to %s tier with exclusive benefits!",
                    event.getNewTier()), Map.of());

            metricsCollector.incrementCounter("notification.account.tier.upgraded.sent");
            metricsCollector.incrementCounter("notification.account.tier.upgraded." +
                event.getNewTier().toLowerCase().replace(" ", "_"));

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process account tier upgraded event", e);
            dlqHandler.sendToDLQ("account.tier.upgraded", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String getUpgradeReason(String reason) {
        return switch (reason.toLowerCase()) {
            case "balance_threshold" -> "You've reached the minimum balance requirement";
            case "transaction_volume" -> "Your transaction volume qualified you for an upgrade";
            case "relationship_time" -> "Thank you for being a long-term customer";
            case "promotional" -> "Special promotional upgrade";
            case "employee_benefit" -> "Employer-sponsored benefit";
            case "referral_reward" -> "Earned through successful referrals";
            default -> reason;
        };
    }

    private String getNewBenefits(String newTier) {
        return switch (newTier.toLowerCase()) {
            case "gold" -> """
                Your New Gold Tier Benefits:
                ✅ No monthly account fees
                ✅ Unlimited domestic transfers (free)
                ✅ 5 free international transfers/month
                ✅ 2.5% APY on savings accounts
                ✅ ATM fee rebates (up to $15/month)
                ✅ Priority customer support
                ✅ Early access to new features
                ✅ Enhanced transaction limits
                ✅ Fraud protection with zero liability
                """;
            case "platinum" -> """
                Your New Platinum Tier Benefits:
                ✅ Zero fees on ALL transactions
                ✅ Unlimited free international transfers
                ✅ 3.5% APY on savings accounts
                ✅ Full ATM fee rebates (unlimited)
                ✅ 24/7 premium support with <1hr response
                ✅ Dedicated account manager
                ✅ Travel benefits (lounge access, travel insurance)
                ✅ Concierge services
                ✅ Investment advisory (free consultation)
                ✅ Extended fraud protection
                ✅ Priority loan approval
                """;
            case "diamond" -> """
                Your New Diamond Tier Benefits:
                ✅ All Platinum benefits PLUS:
                ✅ 4.5% APY on savings accounts
                ✅ Personal wealth manager
                ✅ Premium investment products access
                ✅ Exclusive event invitations
                ✅ Enhanced rewards program (2x points)
                ✅ Luxury travel perks
                ✅ Estate planning consultation
                ✅ Tax advisory services (annual)
                ✅ Priority everything
                """;
            default -> "Review your enhanced benefits at https://example.com/account/benefits";
        };
    }

    private String getTierComparison(String oldTier, String newTier) {
        return String.format("""
            What Changed:

            %s vs %s Comparison:
            • Transaction Fees: %s
            • International Transfers: %s
            • Savings APY: %s
            • Support: %s
            • Additional Perks: %s
            """,
            oldTier, newTier,
            getFeeDifference(oldTier, newTier),
            getTransferDifference(oldTier, newTier),
            getApyDifference(oldTier, newTier),
            getSupportDifference(oldTier, newTier),
            getPerksDifference(oldTier, newTier));
    }

    private String getFeeDifference(String oldTier, String newTier) {
        if ("platinum".equalsIgnoreCase(newTier) || "diamond".equalsIgnoreCase(newTier)) {
            return "Reduced to $0 (was per transaction)";
        }
        return "Reduced fees";
    }

    private String getTransferDifference(String oldTier, String newTier) {
        return switch (newTier.toLowerCase()) {
            case "gold" -> "5 free/month (was 0)";
            case "platinum", "diamond" -> "Unlimited free (was limited)";
            default -> "Increased allowance";
        };
    }

    private String getApyDifference(String oldTier, String newTier) {
        return switch (newTier.toLowerCase()) {
            case "gold" -> "2.5% APY (was 1.5%)";
            case "platinum" -> "3.5% APY (was 2.5%)";
            case "diamond" -> "4.5% APY (was 3.5%)";
            default -> "Increased rate";
        };
    }

    private String getSupportDifference(String oldTier, String newTier) {
        return switch (newTier.toLowerCase()) {
            case "gold" -> "Priority support (was standard)";
            case "platinum", "diamond" -> "24/7 premium + account manager (was priority)";
            default -> "Enhanced support";
        };
    }

    private String getPerksDifference(String oldTier, String newTier) {
        return switch (newTier.toLowerCase()) {
            case "gold" -> "Early feature access, ATM rebates";
            case "platinum" -> "Travel benefits, concierge, investment advisory";
            case "diamond" -> "Wealth manager, exclusive events, premium investments";
            default -> "Additional benefits included";
        };
    }

    private String getPricingInfo(String tier, BigDecimal monthlyFee, boolean waivedFirstMonth) {
        if (monthlyFee.compareTo(BigDecimal.ZERO) == 0) {
            return String.format("""
                Pricing:
                ✅ Your %s tier has NO monthly fee!
                This upgrade is completely free as long as you maintain qualifying activity.
                """, tier);
        }

        if (waivedFirstMonth) {
            return String.format("""
                Pricing:
                • Monthly Fee: $%s
                • First Month: FREE (waived as upgrade bonus)
                • Billing starts: %s

                💡 To maintain this tier fee-free, consider qualifying activities.
                """, monthlyFee, LocalDateTime.now().plusMonths(1).toLocalDate());
        }

        return String.format("""
            Pricing:
            • Monthly Fee: $%s
            • First Charge: %s
            • Billing Cycle: Monthly

            💡 Maintain minimum balance to waive monthly fee.
            """, monthlyFee, LocalDateTime.now().toLocalDate());
    }

    private static class AccountTierUpgradedEvent {
        private UUID userId;
        private String oldTier, newTier, upgradeReason;
        private BigDecimal monthlyFee;
        private LocalDateTime upgradedAt;
        private boolean waivedFirstMonth, accountManager;

        public UUID getUserId() { return userId; }
        public String getOldTier() { return oldTier; }
        public String getNewTier() { return newTier; }
        public String getUpgradeReason() { return upgradeReason; }
        public BigDecimal getMonthlyFee() { return monthlyFee; }
        public LocalDateTime getUpgradedAt() { return upgradedAt; }
        public boolean isWaivedFirstMonth() { return waivedFirstMonth; }
        public boolean hasAccountManager() { return accountManager; }
    }
}
