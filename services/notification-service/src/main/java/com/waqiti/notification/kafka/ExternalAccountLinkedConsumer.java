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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FIX #42: ExternalAccountLinkedConsumer
 * Notifies users when external bank accounts are linked (security alert)
 * Impact: Account security, fraud prevention
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalAccountLinkedConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "external.account.linked", groupId = "notification-external-account-linked")
    public void handle(ExternalAccountLinkedEvent event, Acknowledgment ack) {
        try {
            log.info("🔗 EXTERNAL ACCOUNT LINKED: userId={}, bank={}, accountType={}, method={}",
                event.getUserId(), event.getBankName(), event.getAccountType(), event.getLinkingMethod());

            String key = "external:account:linked:" + event.getLinkedAccountId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                🔗 External Bank Account Linked

                A new external bank account has been linked to your Waqiti account.

                Account Details:
                - Bank: %s
                - Account Type: %s
                - Account: %s
                - Nickname: %s
                - Linked: %s
                - Linking Method: %s

                Verification Status:
                %s

                What You Can Do Now:
                ✅ Transfer funds between accounts
                ✅ Set up direct deposit
                ✅ Schedule automatic transfers
                ✅ Use for bill payments
                ✅ Backup funding source for transactions

                %s

                If You Made This Change:
                ✅ No action needed. Your external account is now active.

                If You Did NOT Link This Account:
                🚨 IMMEDIATE ACTION REQUIRED:
                This could indicate unauthorized access to your account.

                1. Remove this linked account immediately:
                   https://example.com/settings/linked-accounts
                2. Contact security: security@example.com | 1-800-WAQITI-SEC
                3. Review recent account activity
                4. Change your password
                5. Enable two-factor authentication (2FA)

                Linking Details:
                - Location: %s
                - IP Address: %s
                - Device: %s
                - Browser: %s

                Security Information:
                🔒 How We Protect Linked Accounts:
                • Bank-level encryption for all data
                • Plaid/Yodlee secure connection
                • We never store your bank login credentials
                • Micro-deposit verification for security
                • You can remove accounts anytime

                Manage Linked Accounts:
                • View all linked accounts: https://example.com/settings/linked-accounts
                • Remove accounts: One-click unlink
                • Update nicknames: Organize your accounts
                • Set default account: For transfers

                Transfer Limits:
                - Daily limit: %s
                - Monthly limit: %s
                - Instant transfers: %s

                Questions? Contact account linking support:
                Email: linking@example.com
                Phone: 1-800-WAQITI-LINK
                Reference: Link ID %s
                """,
                event.getBankName(),
                event.getAccountType(),
                maskAccountNumber(event.getAccountNumber()),
                event.getNickname() != null ? event.getNickname() : "Not set",
                event.getLinkedAt(),
                getLinkingMethodDescription(event.getLinkingMethod()),
                getVerificationStatus(event.getVerificationStatus(), event.getVerificationMethod()),
                getNextSteps(event.getVerificationStatus()),
                event.getLocation() != null ? event.getLocation() : "Unknown",
                maskIpAddress(event.getIpAddress()),
                event.getDeviceType() != null ? event.getDeviceType() : "Unknown",
                event.getBrowser() != null ? event.getBrowser() : "Unknown",
                event.getDailyTransferLimit() != null ? "$" + event.getDailyTransferLimit() : "Standard limits apply",
                event.getMonthlyTransferLimit() != null ? "$" + event.getMonthlyTransferLimit() : "Standard limits apply",
                event.isInstantTransfersEnabled() ? "Available" : "Not available",
                event.getLinkedAccountId());

            // Security alert - high priority
            notificationService.sendNotification(event.getUserId(), NotificationType.EXTERNAL_ACCOUNT_LINKED,
                NotificationChannel.EMAIL, NotificationPriority.HIGH,
                "Security Alert: External Bank Account Linked", message, Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.EXTERNAL_ACCOUNT_LINKED,
                NotificationChannel.PUSH, NotificationPriority.HIGH,
                "New Account Linked",
                String.format("A %s account from %s was linked to your Waqiti account. If this wasn't you, secure your account immediately.",
                    event.getAccountType(), event.getBankName()), Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.EXTERNAL_ACCOUNT_LINKED,
                NotificationChannel.SMS, NotificationPriority.MEDIUM, null,
                String.format("Waqiti: External bank account linked (%s). If not you, call 1-800-WAQITI-SEC now.",
                    event.getBankName()), Map.of());

            metricsCollector.incrementCounter("notification.external.account.linked.sent");
            metricsCollector.incrementCounter("notification.external.account.linked." +
                event.getLinkingMethod().toLowerCase().replace(" ", "_"));

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process external account linked event", e);
            dlqHandler.sendToDLQ("external.account.linked", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String getLinkingMethodDescription(String linkingMethod) {
        return switch (linkingMethod.toLowerCase()) {
            case "instant_verification" -> "Instant Verification (via Plaid)";
            case "micro_deposits" -> "Micro-Deposit Verification";
            case "manual_entry" -> "Manual Account Entry";
            case "oauth" -> "Bank OAuth Authorization";
            default -> linkingMethod;
        };
    }

    private String getVerificationStatus(String status, String method) {
        return switch (status.toLowerCase()) {
            case "verified" -> """
                ✅ Fully Verified
                Your account is verified and ready to use for transfers.
                """;
            case "pending_micro_deposits" -> String.format("""
                ⏳ Pending Verification
                Verification Method: %s

                Next Steps:
                1. Check your external bank account in 1-2 business days
                2. Look for two small deposits from Waqiti (< $1.00 each)
                3. Enter the exact amounts to complete verification
                4. Verify at: https://example.com/settings/verify-account

                Until verified, transfers may be delayed or limited.
                """, method);
            case "pending_manual_review" -> """
                ⏳ Pending Manual Review
                Our team is reviewing your account linking request.
                This typically takes 1-2 business days.

                You'll be notified once verification is complete.
                """;
            default -> String.format("Status: %s", status);
        };
    }

    private String getNextSteps(String verificationStatus) {
        if ("pending_micro_deposits".equalsIgnoreCase(verificationStatus)) {
            return """
                Next Steps to Complete Setup:
                1. Wait 1-2 business days for micro-deposits
                2. Check your external bank account
                3. Note the exact deposit amounts
                4. Return to Waqiti to verify
                5. Once verified, full functionality is enabled
                """;
        } else if ("verified".equalsIgnoreCase(verificationStatus)) {
            return """
                Your Account is Ready:
                1. Transfer funds between accounts
                2. Set up recurring transfers
                3. Use for bill payments
                4. Configure as backup funding source
                """;
        }
        return "Follow instructions above to complete account setup.";
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) return "****";
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private String maskIpAddress(String ipAddress) {
        if (ipAddress == null) return "Unknown";
        String[] parts = ipAddress.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***.***";
        }
        return ipAddress;
    }

    private static class ExternalAccountLinkedEvent {
        private UUID userId, linkedAccountId;
        private String bankName, accountType, accountNumber, nickname;
        private String linkingMethod, verificationStatus, verificationMethod;
        private String location, ipAddress, deviceType, browser;
        private String dailyTransferLimit, monthlyTransferLimit;
        private LocalDateTime linkedAt;
        private boolean instantTransfersEnabled;

        public UUID getUserId() { return userId; }
        public UUID getLinkedAccountId() { return linkedAccountId; }
        public String getBankName() { return bankName; }
        public String getAccountType() { return accountType; }
        public String getAccountNumber() { return accountNumber; }
        public String getNickname() { return nickname; }
        public String getLinkingMethod() { return linkingMethod; }
        public String getVerificationStatus() { return verificationStatus; }
        public String getVerificationMethod() { return verificationMethod; }
        public String getLocation() { return location; }
        public String getIpAddress() { return ipAddress; }
        public String getDeviceType() { return deviceType; }
        public String getBrowser() { return browser; }
        public String getDailyTransferLimit() { return dailyTransferLimit; }
        public String getMonthlyTransferLimit() { return monthlyTransferLimit; }
        public LocalDateTime getLinkedAt() { return linkedAt; }
        public boolean isInstantTransfersEnabled() { return instantTransfersEnabled; }
    }
}
