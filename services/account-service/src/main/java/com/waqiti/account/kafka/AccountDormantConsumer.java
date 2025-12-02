package com.waqiti.account.kafka;

import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.account.domain.Account;
import com.waqiti.account.domain.AccountStatus;
import com.waqiti.account.domain.DormantAccountRecord;
import com.waqiti.account.repository.AccountRepository;
import com.waqiti.account.repository.DormantAccountRepository;
import com.waqiti.account.service.AccountNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRITICAL FIX #17: AccountDormantConsumer
 * Flags dormant accounts per banking regulations (12+ months inactivity)
 * Compliance: FDIC dormant account requirements, state escheatment laws
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountDormantConsumer {
    private final AccountRepository accountRepository;
    private final DormantAccountRepository dormantAccountRepository;
    private final AccountNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    private static final int DORMANT_THRESHOLD_MONTHS = 12;
    private static final int ESCHEATMENT_THRESHOLD_YEARS = 3; // State-dependent

    @KafkaListener(topics = "account.dormant.detected", groupId = "account-dormant-processor")
    @Transactional
    public void handle(AccountDormantEvent event, Acknowledgment ack) {
        try {
            log.warn("😴 DORMANT ACCOUNT DETECTED: accountId={}, userId={}, lastActivity={}, inactiveDays={}",
                event.getAccountId(), event.getUserId(), event.getLastActivityDate(), event.getInactiveDays());

            String key = "account:dormant:" + event.getAccountId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            Account account = accountRepository.findById(event.getAccountId())
                .orElseThrow(() -> new BusinessException("Account not found"));

            if (account.getStatus() == AccountStatus.CLOSED) {
                log.warn("Account {} already closed", event.getAccountId());
                ack.acknowledge();
                return;
            }

            // Create dormant account record for compliance tracking
            DormantAccountRecord dormantRecord = DormantAccountRecord.builder()
                .id(UUID.randomUUID())
                .accountId(event.getAccountId())
                .userId(event.getUserId())
                .accountType(event.getAccountType())
                .balance(event.getBalance())
                .lastActivityDate(event.getLastActivityDate())
                .dormantDetectedAt(LocalDateTime.now())
                .inactiveDays(event.getInactiveDays())
                .state(event.getUserState())
                .escheatmentEligibleDate(calculateEscheatmentDate(event.getUserState()))
                .notificationSent(false)
                .build();

            dormantAccountRepository.save(dormantRecord);

            // Update account status
            account.setStatus(AccountStatus.DORMANT);
            account.setDormantSince(LocalDateTime.now());
            account.setDormantReason("No activity for " + event.getInactiveDays() + " days");
            accountRepository.save(account);

            log.warn("🔒 ACCOUNT MARKED DORMANT: accountId={}, balance=${}, escheatmentDate={}",
                event.getAccountId(), event.getBalance(), dormantRecord.getEscheatmentEligibleDate());

            // Notify user
            notifyDormantAccount(event, account, dormantRecord);

            // Compliance reporting
            if (event.getBalance().compareTo(new BigDecimal("100")) > 0) {
                log.warn("⚠️ HIGH-VALUE DORMANT ACCOUNT: accountId={}, balance=${} - requires compliance review",
                    event.getAccountId(), event.getBalance());
                metricsCollector.incrementCounter("account.dormant.high_value");
            }

            metricsCollector.incrementCounter("account.dormant.flagged");
            metricsCollector.recordGauge("account.dormant.balance", event.getBalance().doubleValue());
            metricsCollector.recordHistogram("account.dormant.inactive_days", event.getInactiveDays());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process dormant account event", e);
            dlqHandler.sendToDLQ("account.dormant.detected", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private void notifyDormantAccount(AccountDormantEvent event, Account account, DormantAccountRecord record) {
        String message = String.format("""
            Your account has been marked as dormant due to inactivity.

            Account Details:
            - Account Number: %s
            - Account Type: %s
            - Current Balance: $%s
            - Last Activity: %s
            - Inactive For: %d days

            Why This Matters:
            After %d months of inactivity, your account is considered dormant under banking regulations.

            Important - Escheatment Warning:
            If your account remains inactive, funds may be turned over to the state under
            escheatment laws on or after: %s

            To Reactivate Your Account:
            Simply log in and perform any transaction (transfer, payment, withdrawal).
            Your account will automatically be reactivated.

            Questions?
            Contact: support@example.com | 1-800-WAQITI
            Reference: Dormant Account ID %s
            """,
            maskAccountNumber(account.getAccountNumber()),
            event.getAccountType(),
            event.getBalance(),
            event.getLastActivityDate().toLocalDate(),
            event.getInactiveDays(),
            DORMANT_THRESHOLD_MONTHS,
            record.getEscheatmentEligibleDate().toLocalDate(),
            event.getAccountId());

        notificationService.sendDormantAccountNotification(
            event.getUserId(), event.getAccountId(), event.getBalance(), message);

        // Update notification tracking
        record.setNotificationSent(true);
        record.setNotificationSentAt(LocalDateTime.now());
        dormantAccountRepository.save(record);
    }

    private LocalDateTime calculateEscheatmentDate(String state) {
        // State-specific escheatment periods (simplified - actual implementation needs full state mapping)
        int years = switch (state.toUpperCase()) {
            case "CA", "NY", "TX" -> 3;
            case "FL", "IL" -> 5;
            case "PA" -> 7;
            default -> 3; // Conservative default
        };
        return LocalDateTime.now().plusYears(years);
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) return "****";
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private static class AccountDormantEvent {
        private UUID accountId, userId;
        private String accountType, userState;
        private BigDecimal balance;
        private LocalDateTime lastActivityDate;
        private int inactiveDays;

        public UUID getAccountId() { return accountId; }
        public UUID getUserId() { return userId; }
        public String getAccountType() { return accountType; }
        public String getUserState() { return userState; }
        public BigDecimal getBalance() { return balance; }
        public LocalDateTime getLastActivityDate() { return lastActivityDate; }
        public int getInactiveDays() { return inactiveDays; }
    }
}
