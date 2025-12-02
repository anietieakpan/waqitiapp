package com.waqiti.account.kafka;

import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.account.domain.Account;
import com.waqiti.account.domain.AccountStatus;
import com.waqiti.account.domain.SecurityLock;
import com.waqiti.account.repository.AccountRepository;
import com.waqiti.account.repository.SecurityLockRepository;
import com.waqiti.account.service.AccountNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRITICAL FIX #21: AccountLockedConsumer
 * Notifies users when accounts are locked due to suspicious activity
 * Security: Prevents account takeover, protects user funds
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountLockedConsumer {
    private final AccountRepository accountRepository;
    private final SecurityLockRepository securityLockRepository;
    private final AccountNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "account.locked.suspicious_activity", groupId = "account-lock-processor")
    @Transactional
    public void handle(AccountLockedEvent event, Acknowledgment ack) {
        try {
            log.warn("🔒 ACCOUNT LOCKED - SUSPICIOUS ACTIVITY: accountId={}, userId={}, reason={}",
                event.getAccountId(), event.getUserId(), event.getLockReason());

            String key = "account:locked:" + event.getAccountId();
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

            // Create security lock record
            SecurityLock securityLock = SecurityLock.builder()
                .id(UUID.randomUUID())
                .accountId(event.getAccountId())
                .userId(event.getUserId())
                .lockReason(event.getLockReason())
                .lockCategory(event.getLockCategory())
                .suspiciousActivityType(event.getActivityType())
                .detectionSource(event.getDetectionSource())
                .riskScore(event.getRiskScore())
                .ipAddress(event.getIpAddress())
                .deviceId(event.getDeviceId())
                .location(event.getLocation())
                .lockedAt(LocalDateTime.now())
                .autoUnlockAt(calculateAutoUnlock(event.getLockCategory()))
                .requiresManualReview(isManualReviewRequired(event))
                .build();

            securityLockRepository.save(securityLock);

            // Update account status
            account.setStatus(AccountStatus.LOCKED);
            account.setLockReason(event.getLockReason());
            account.setLockedAt(LocalDateTime.now());
            account.setSecurityLockId(securityLock.getId());
            accountRepository.save(account);

            log.error("🚨 ACCOUNT LOCKED FOR SECURITY: accountId={}, category={}, riskScore={}",
                event.getAccountId(), event.getLockCategory(), event.getRiskScore());

            // Notify user via all channels
            notifyAccountLocked(event, account, securityLock);

            // High-risk alerts
            if (event.getRiskScore() >= 80) {
                log.error("⚠️ HIGH-RISK ACCOUNT LOCK: accountId={}, riskScore={}, requiring immediate review",
                    event.getAccountId(), event.getRiskScore());
                notificationService.alertSecurityTeam(event.getAccountId(), event.getLockReason(),
                    event.getRiskScore());
                metricsCollector.incrementCounter("account.locked.high_risk");
            }

            metricsCollector.incrementCounter("account.locked.suspicious_activity");
            metricsCollector.incrementCounter("account.locked." +
                event.getLockCategory().toLowerCase().replace(" ", "_"));
            metricsCollector.recordGauge("account.locked.risk_score", event.getRiskScore());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process account locked event", e);
            dlqHandler.sendToDLQ("account.locked.suspicious_activity", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private void notifyAccountLocked(AccountLockedEvent event, Account account, SecurityLock securityLock) {
        String message = String.format("""
            🚨 IMPORTANT SECURITY ALERT: Your account has been locked

            Your account was automatically locked to protect your funds due to suspicious activity.

            Account Details:
            - Account Number: %s
            - Locked At: %s
            - Lock Reason: %s

            Suspicious Activity Detected:
            - Type: %s
            - Risk Score: %d/100
            - Location: %s
            - IP Address: %s
            - Device: %s

            What This Means:
            %s

            %s

            To Unlock Your Account:
            1. Verify your identity: https://example.com/security/verify
            2. Review recent activity
            3. Update your password
            4. Enable two-factor authentication

            If You Did Not Authorize This Activity:
            • Do not attempt to access your account
            • Contact our security team immediately
            • Change passwords on any linked accounts

            🔒 Security Support (24/7):
            Email: security@example.com
            Phone: 1-800-WAQITI-SEC
            Reference: Lock ID %s

            Your funds are safe. We take security seriously.
            """,
            maskAccountNumber(account.getAccountNumber()),
            securityLock.getLockedAt(),
            event.getLockReason(),
            event.getActivityType(),
            event.getRiskScore(),
            event.getLocation() != null ? event.getLocation() : "Unknown",
            maskIpAddress(event.getIpAddress()),
            event.getDeviceId() != null ? "Unknown device" : "Known device",
            getActivityExplanation(event.getActivityType()),
            securityLock.isRequiresManualReview()
                ? "⚠️ This lock requires manual security review. Our team will contact you within 24 hours."
                : String.format("✅ This lock may be automatically lifted after security review (approx. %s)",
                    securityLock.getAutoUnlockAt() != null ? securityLock.getAutoUnlockAt().toLocalDate() : "24-48 hours"),
            securityLock.getId());

        // Multi-channel emergency notification
        notificationService.sendAccountLockedNotification(
            event.getUserId(), event.getAccountId(), event.getLockReason(), message);
    }

    private LocalDateTime calculateAutoUnlock(String lockCategory) {
        return switch (lockCategory.toLowerCase()) {
            case "unusual_login" -> LocalDateTime.now().plusHours(24);
            case "velocity_check" -> LocalDateTime.now().plusHours(12);
            case "geo_anomaly" -> LocalDateTime.now().plusHours(48);
            case "device_fingerprint" -> LocalDateTime.now().plusHours(24);
            case "fraud_suspected" -> null; // Manual review required
            case "account_takeover" -> null; // Manual review required
            default -> LocalDateTime.now().plusHours(48);
        };
    }

    private boolean isManualReviewRequired(AccountLockedEvent event) {
        // High-risk situations require manual review
        if (event.getRiskScore() >= 80) return true;
        if ("account_takeover".equals(event.getLockCategory())) return true;
        if ("fraud_suspected".equals(event.getLockCategory())) return true;
        return false;
    }

    private String getActivityExplanation(String activityType) {
        return switch (activityType.toLowerCase()) {
            case "unusual_login_location" ->
                "We detected a login attempt from a location you don't typically use.";
            case "multiple_failed_logins" ->
                "Multiple failed login attempts were detected on your account.";
            case "suspicious_transaction_pattern" ->
                "We identified unusual transaction patterns that don't match your normal behavior.";
            case "high_velocity_transfers" ->
                "An unusually high number of transfers were attempted in a short time period.";
            case "compromised_credentials" ->
                "Your login credentials may have been compromised in a data breach.";
            case "unknown_device" ->
                "A login was attempted from a device we've never seen before.";
            case "geo_impossible_travel" ->
                "Login attempts were made from locations too far apart to be possible.";
            case "api_abuse" ->
                "Suspicious API activity was detected on your account.";
            default ->
                "Unusual activity was detected that triggered our security systems.";
        };
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

    private static class AccountLockedEvent {
        private UUID accountId, userId;
        private String lockReason, lockCategory, activityType, detectionSource;
        private String ipAddress, deviceId, location;
        private int riskScore;
        private LocalDateTime detectedAt;

        public UUID getAccountId() { return accountId; }
        public UUID getUserId() { return userId; }
        public String getLockReason() { return lockReason; }
        public String getLockCategory() { return lockCategory; }
        public String getActivityType() { return activityType; }
        public String getDetectionSource() { return detectionSource; }
        public String getIpAddress() { return ipAddress; }
        public String getDeviceId() { return deviceId; }
        public String getLocation() { return location; }
        public int getRiskScore() { return riskScore; }
        public LocalDateTime getDetectedAt() { return detectedAt; }
    }
}
