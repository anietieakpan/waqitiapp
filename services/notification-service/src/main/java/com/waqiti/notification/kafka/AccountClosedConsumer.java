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
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FIX #39: AccountClosedConsumer
 * Notifies users when accounts are closed
 * Impact: Closure confirmation, remaining balance instructions
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountClosedConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "account.closed", groupId = "notification-account-closed")
    public void handle(AccountClosedEvent event, Acknowledgment ack) {
        try {
            log.info("🔒 ACCOUNT CLOSED: accountId={}, userId={}, reason={}, finalBalance=${}",
                event.getAccountId(), event.getUserId(), event.getClosureReason(), event.getFinalBalance());

            String key = "account:closed:" + event.getAccountId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            long accountAgeDays = ChronoUnit.DAYS.between(event.getAccountOpenedDate(), event.getClosedAt());

            String message = String.format("""
                Account Closure Confirmation

                Your Waqiti account has been closed.

                Account Details:
                - Account Number: %s
                - Account Type: %s
                - Opened: %s
                - Closed: %s
                - Account Age: %s
                - Closure Reason: %s

                %s

                Final Balance Information:
                %s

                %s

                What Happens Next:
                %s

                Data Retention:
                • Your account history will be retained for 7 years (regulatory requirement)
                • You can request account statements at any time
                • Tax documents (1099, etc.) will still be mailed annually
                • Historical data available at: https://example.com/account/history

                Important Reminders:
                ✅ Cancel any linked services:
                • Update payment methods for subscriptions
                • Change direct deposit information with employer
                • Update autopay settings for bills
                • Notify anyone sending you money

                ✅ Download your data:
                • Transaction history
                • Tax documents
                • Account statements
                Download at: https://example.com/account/export

                %s

                Questions? Contact account closure support:
                Email: closures@example.com
                Phone: 1-800-WAQITI-CLOSE
                Reference: Account ID %s

                %s
                """,
                maskAccountNumber(event.getAccountNumber()),
                event.getAccountType(),
                event.getAccountOpenedDate().toLocalDate(),
                event.getClosedAt().toLocalDate(),
                formatAccountAge(accountAgeDays),
                event.getClosureReason(),
                getClosureReasonDetails(event.getClosureCode()),
                getFinalBalanceInfo(event.getFinalBalance(), event.getCheckIssued(), event.getCheckNumber()),
                getRemainingObligations(event.getPendingTransactions(), event.getScheduledPayments()),
                getNextSteps(event.getClosureCode(), event.getFinalBalance()),
                getReopeningInfo(event.getClosureCode()),
                event.getAccountId(),
                getFeedbackRequest(event.getClosureCode()));

            notificationService.sendNotification(event.getUserId(), NotificationType.ACCOUNT_CLOSED,
                NotificationChannel.EMAIL, NotificationPriority.HIGH,
                "Account Closure Confirmation", message, Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.ACCOUNT_CLOSED,
                NotificationChannel.PUSH, NotificationPriority.MEDIUM,
                "Account Closed",
                String.format("Your %s account has been closed. Check email for final balance details.",
                    event.getAccountType()), Map.of());

            metricsCollector.incrementCounter("notification.account.closed.sent");
            metricsCollector.incrementCounter("notification.account.closed." +
                event.getClosureCode().toLowerCase().replace(" ", "_"));
            metricsCollector.recordHistogram("account.age.days", accountAgeDays);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process account closed event", e);
            dlqHandler.sendToDLQ("account.closed", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String formatAccountAge(long days) {
        if (days < 30) return days + " days";
        if (days < 365) return (days / 30) + " months";
        long years = days / 365;
        long months = (days % 365) / 30;
        return String.format("%d year%s, %d month%s", years, years == 1 ? "" : "s", months, months == 1 ? "" : "s");
    }

    private String getClosureReasonDetails(String closureCode) {
        return switch (closureCode.toLowerCase()) {
            case "user_requested" ->
                "You requested to close this account.";
            case "inactivity" ->
                """
                Your account was closed due to extended inactivity.
                Accounts with no activity for 12+ months may be closed per our terms.
                """;
            case "compliance_violation" ->
                """
                ⚠️ Your account was closed due to a compliance violation.
                Please contact compliance@example.com for details.
                """;
            case "fraud_detected" ->
                """
                🚨 Your account was closed due to suspected fraudulent activity.
                For your protection, all funds have been secured.
                Contact security@example.com if you believe this was in error.
                """;
            case "negative_balance" ->
                """
                Your account was closed due to a persistent negative balance.
                Outstanding balance must be paid. See final balance section below.
                """;
            case "deceased" ->
                """
                This account was closed following notification of the account holder's passing.
                Estate representatives should contact estates@example.com.
                """;
            default -> "See closure reason above.";
        };
    }

    private String getFinalBalanceInfo(BigDecimal finalBalance, boolean checkIssued, String checkNumber) {
        if (finalBalance.compareTo(BigDecimal.ZERO) > 0) {
            if (checkIssued) {
                return String.format("""
                    Final Balance: $%s

                    ✅ Check Issued:
                    • Check Number: %s
                    • Amount: $%s
                    • Mailed to your address on file
                    • Please allow 5-7 business days for delivery
                    • Deposit or cash the check within 90 days

                    If check is lost or not received:
                    Email: checks@example.com
                    Phone: 1-800-WAQITI-CHECK
                    """, finalBalance, checkNumber, finalBalance);
            } else {
                return String.format("""
                    Final Balance: $%s

                    📬 Check Being Prepared:
                    • A check will be mailed to your address on file
                    • Expected mail date: Within 5 business days
                    • Delivery: 5-7 business days after mailing
                    • Ensure your mailing address is current

                    Update mailing address:
                    Email: closures@example.com with updated address
                    """, finalBalance);
            }
        } else if (finalBalance.compareTo(BigDecimal.ZERO) < 0) {
            return String.format("""
                Outstanding Balance: $%s (owed)

                ⚠️ Payment Required:
                You have an outstanding balance that must be paid.

                Payment Options:
                • Online: https://example.com/account/pay-balance
                • Phone: 1-800-WAQITI (automated payment)
                • Mail: Check to Waqiti Collections, PO Box 12345

                Failure to pay may result in:
                • Collection agency referral
                • Credit bureau reporting
                • Legal action

                Payment arrangements available - call 1-800-WAQITI
                """, finalBalance.abs());
        } else {
            return """
                Final Balance: $0.00

                ✅ Your account has been settled with a zero balance.
                No further action is required regarding account balance.
                """;
        }
    }

    private String getRemainingObligations(int pendingTransactions, int scheduledPayments) {
        if (pendingTransactions > 0 || scheduledPayments > 0) {
            StringBuilder obligations = new StringBuilder("⚠️ Pending Items:\n");
            if (pendingTransactions > 0) {
                obligations.append(String.format("• %d pending transaction(s) will be processed\n", pendingTransactions));
            }
            if (scheduledPayments > 0) {
                obligations.append(String.format("• %d scheduled payment(s) have been cancelled\n", scheduledPayments));
                obligations.append("• You'll need to set up payments elsewhere\n");
            }
            return obligations.toString();
        }
        return "✅ No pending transactions or scheduled payments.";
    }

    private String getNextSteps(String closureCode, BigDecimal finalBalance) {
        if ("user_requested".equals(closureCode)) {
            return """
                1. ✅ Account closure is complete
                2. 📬 Watch for final balance check (if applicable)
                3. 💾 Download your data if you haven't already
                4. 🔄 Update linked services and subscriptions
                5. 📧 Keep this email for your records
                """;
        } else if ("fraud_detected".equals(closureCode)) {
            return """
                1. 🚨 Contact security immediately if closure was in error
                2. 📋 File a police report if fraud occurred
                3. 🔒 Monitor credit reports for unauthorized activity
                4. 💳 Request new cards from other financial institutions
                5. 📞 Call 1-800-WAQITI-SEC for assistance
                """;
        } else {
            return """
                1. Review closure reason and details
                2. Watch for final balance check/payment instructions
                3. Download historical data and documents
                4. Update payment methods for linked services
                5. Contact support if you have questions
                """;
        }
    }

    private String getReopeningInfo(String closureCode) {
        if ("user_requested".equals(closureCode) || "inactivity".equals(closureCode)) {
            return """
                Want to Return?
                You can open a new account at any time:
                • Apply online: https://example.com/signup
                • Call: 1-800-WAQITI
                • Visit a branch location

                Note: This will be a new account, not a reopening.
                """;
        }
        return "";
    }

    private String getFeedbackRequest(String closureCode) {
        if ("user_requested".equals(closureCode)) {
            return """
                We're Sorry to See You Go

                Your feedback helps us improve. Please take 2 minutes to share why you closed your account:
                https://example.com/feedback/closure

                Thank you for being a Waqiti customer!
                """;
        }
        return "";
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) return "****";
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private static class AccountClosedEvent {
        private UUID userId, accountId;
        private String accountNumber, accountType, closureReason, closureCode, checkNumber;
        private BigDecimal finalBalance;
        private LocalDateTime accountOpenedDate, closedAt;
        private int pendingTransactions, scheduledPayments;
        private boolean checkIssued;

        public UUID getUserId() { return userId; }
        public UUID getAccountId() { return accountId; }
        public String getAccountNumber() { return accountNumber; }
        public String getAccountType() { return accountType; }
        public String getClosureReason() { return closureReason; }
        public String getClosureCode() { return closureCode; }
        public String getCheckNumber() { return checkNumber; }
        public BigDecimal getFinalBalance() { return finalBalance; }
        public LocalDateTime getAccountOpenedDate() { return accountOpenedDate; }
        public LocalDateTime getClosedAt() { return closedAt; }
        public int getPendingTransactions() { return pendingTransactions; }
        public int getScheduledPayments() { return scheduledPayments; }
        public boolean getCheckIssued() { return checkIssued; }
    }
}
