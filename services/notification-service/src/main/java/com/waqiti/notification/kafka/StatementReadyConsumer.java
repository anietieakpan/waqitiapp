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
 * CRITICAL FIX #45: StatementReadyConsumer
 * Notifies users when monthly account statements are ready
 * Impact: Regulatory compliance, account transparency
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StatementReadyConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "statement.ready", groupId = "notification-statement-ready")
    public void handle(StatementReadyEvent event, Acknowledgment ack) {
        try {
            log.info("📄 STATEMENT READY: userId={}, statementPeriod={}, accountType={}",
                event.getUserId(), event.getStatementPeriod(), event.getAccountType());

            String key = "statement:ready:" + event.getStatementId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                📄 Your Account Statement is Ready

                Your monthly account statement is now available for download.

                Statement Details:
                - Account: %s
                - Account Type: %s
                - Statement Period: %s
                - Generated: %s
                - Pages: %d

                Account Summary:
                - Beginning Balance: $%s
                - Ending Balance: $%s
                - %s
                - Total Deposits: $%s
                - Total Withdrawals: $%s
                - Total Fees: $%s
                - Interest Earned: $%s

                Transaction Summary:
                - Total Transactions: %d
                - Deposits: %d
                - Withdrawals: %d
                - Purchases: %d
                - Transfers: %d

                %s

                Download Your Statement:
                📥 PDF: %s
                📥 CSV (transactions): %s

                View Online:
                https://example.com/statements/%s

                Statement Features:
                • Detailed transaction history
                • Fee breakdown
                • Interest calculations
                • YTD summaries
                • Tax information (if applicable)

                %s

                Why Statements Matter:
                📋 Keep Records:
                • Tax preparation and filing
                • Expense tracking and budgeting
                • Loan applications
                • Dispute resolution
                • Financial planning

                📋 Review Carefully:
                • Verify all transactions are authorized
                • Check for errors or unauthorized charges
                • Monitor fees and interest
                • Track spending patterns
                • Identify unusual activity

                Statement Retention:
                • We recommend keeping statements for 7 years
                • Download and save to your device
                • Store securely (encrypted if possible)
                • Available online for 7 years

                Paperless Statements:
                %s

                Questions? Contact statement support:
                Email: statements@example.com
                Phone: 1-800-WAQITI-STMT
                Reference: Statement ID %s

                %s
                """,
                maskAccountNumber(event.getAccountNumber()),
                event.getAccountType(),
                event.getStatementPeriod(),
                event.getGeneratedAt(),
                event.getPageCount(),
                event.getBeginningBalance(),
                event.getEndingBalance(),
                event.getEndingBalance().compareTo(event.getBeginningBalance()) >= 0
                    ? String.format("Change: +$%s", event.getEndingBalance().subtract(event.getBeginningBalance()))
                    : String.format("Change: -$%s", event.getBeginningBalance().subtract(event.getEndingBalance())),
                event.getTotalDeposits(),
                event.getTotalWithdrawals(),
                event.getTotalFees(),
                event.getInterestEarned(),
                event.getTransactionCount(),
                event.getDepositCount(),
                event.getWithdrawalCount(),
                event.getPurchaseCount(),
                event.getTransferCount(),
                getHighlights(event),
                event.getPdfDownloadUrl(),
                event.getCsvDownloadUrl(),
                event.getStatementId(),
                getTaxInformation(event.getAccountType(), event.getStatementPeriod()),
                getPaperlessInfo(event.isPaperlessEnabled()),
                event.getStatementId(),
                getNextStatementInfo(event.getStatementPeriod()));

            notificationService.sendNotification(event.getUserId(), NotificationType.STATEMENT_READY,
                NotificationChannel.EMAIL, NotificationPriority.MEDIUM,
                String.format("Your %s Statement is Ready", event.getStatementPeriod()), message, Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.STATEMENT_READY,
                NotificationChannel.PUSH, NotificationPriority.LOW,
                "Statement Available",
                String.format("Your %s account statement for %s is ready to download.",
                    event.getAccountType(), event.getStatementPeriod()), Map.of());

            metricsCollector.incrementCounter("notification.statement.ready.sent");
            metricsCollector.incrementCounter("notification.statement.ready." +
                event.getAccountType().toLowerCase().replace(" ", "_"));

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process statement ready event", e);
            dlqHandler.sendToDLQ("statement.ready", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String getHighlights(StatementReadyEvent event) {
        StringBuilder highlights = new StringBuilder("Statement Highlights:\n");

        if (event.getInterestEarned().compareTo(BigDecimal.ZERO) > 0) {
            highlights.append(String.format("💰 Interest Earned: $%s\n", event.getInterestEarned()));
        }

        if (event.getTotalFees().compareTo(BigDecimal.ZERO) > 0) {
            highlights.append(String.format("⚠️ Total Fees: $%s\n", event.getTotalFees()));
        }

        BigDecimal netChange = event.getEndingBalance().subtract(event.getBeginningBalance());
        if (netChange.compareTo(BigDecimal.ZERO) > 0) {
            highlights.append(String.format("📈 Account grew by $%s this period\n", netChange));
        } else if (netChange.compareTo(BigDecimal.ZERO) < 0) {
            highlights.append(String.format("📉 Account decreased by $%s this period\n", netChange.abs()));
        }

        if (event.getTransactionCount() > 100) {
            highlights.append(String.format("📊 High activity: %d transactions\n", event.getTransactionCount()));
        }

        return highlights.toString();
    }

    private String getTaxInformation(String accountType, String statementPeriod) {
        if (statementPeriod.contains("December") || statementPeriod.contains("12/")) {
            return """
                📋 Year-End Tax Information:
                Your year-end statement includes information for tax preparation:
                • Interest income (Form 1099-INT)
                • Dividend income (Form 1099-DIV)
                • Capital gains/losses (if applicable)

                Tax forms will be mailed by January 31st.
                Early access available at: https://example.com/tax-documents
                """;
        }
        return "";
    }

    private String getPaperlessInfo(boolean isPaperless) {
        if (isPaperless) {
            return """
                ✅ You're enrolled in paperless statements!
                • Statements available online immediately
                • Environmentally friendly
                • Secure digital storage
                • No paper clutter

                Manage preferences: https://example.com/settings/paperless
                """;
        } else {
            return """
                📬 Paper Statement:
                A printed copy will be mailed to your address on file within 5-7 business days.

                💡 Go Paperless:
                Switch to paperless statements for:
                • Instant access (no waiting for mail)
                • Better security (no mail theft risk)
                • Environmental benefits
                • Free up mailbox space

                Enroll at: https://example.com/settings/paperless
                """;
        }
    }

    private String getNextStatementInfo(String currentPeriod) {
        return """
            Next Statement:
            Your next monthly statement will be available on the 5th business day
            of next month for the previous month's activity.

            Set up statement notifications:
            • Email alerts (default)
            • Push notifications
            • SMS alerts (optional)

            Manage at: https://example.com/settings/notifications
            """;
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) return "****";
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private static class StatementReadyEvent {
        private UUID userId, statementId;
        private String accountNumber, accountType, statementPeriod;
        private String pdfDownloadUrl, csvDownloadUrl;
        private BigDecimal beginningBalance, endingBalance, totalDeposits, totalWithdrawals;
        private BigDecimal totalFees, interestEarned;
        private int transactionCount, depositCount, withdrawalCount, purchaseCount, transferCount, pageCount;
        private LocalDateTime generatedAt;
        private boolean paperlessEnabled;

        public UUID getUserId() { return userId; }
        public UUID getStatementId() { return statementId; }
        public String getAccountNumber() { return accountNumber; }
        public String getAccountType() { return accountType; }
        public String getStatementPeriod() { return statementPeriod; }
        public String getPdfDownloadUrl() { return pdfDownloadUrl; }
        public String getCsvDownloadUrl() { return csvDownloadUrl; }
        public BigDecimal getBeginningBalance() { return beginningBalance; }
        public BigDecimal getEndingBalance() { return endingBalance; }
        public BigDecimal getTotalDeposits() { return totalDeposits; }
        public BigDecimal getTotalWithdrawals() { return totalWithdrawals; }
        public BigDecimal getTotalFees() { return totalFees; }
        public BigDecimal getInterestEarned() { return interestEarned; }
        public int getTransactionCount() { return transactionCount; }
        public int getDepositCount() { return depositCount; }
        public int getWithdrawalCount() { return withdrawalCount; }
        public int getPurchaseCount() { return purchaseCount; }
        public int getTransferCount() { return transferCount; }
        public int getPageCount() { return pageCount; }
        public LocalDateTime getGeneratedAt() { return generatedAt; }
        public boolean isPaperlessEnabled() { return paperlessEnabled; }
    }
}
