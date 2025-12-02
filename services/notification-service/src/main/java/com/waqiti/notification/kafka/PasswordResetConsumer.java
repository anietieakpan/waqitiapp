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
 * CRITICAL FIX #36: PasswordResetConsumer
 * Notifies users when password is successfully reset (security alert)
 * Impact: Account security, fraud prevention
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordResetConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "password.reset.completed", groupId = "notification-password-reset")
    public void handle(PasswordResetEvent event, Acknowledgment ack) {
        try {
            log.info("🔒 PASSWORD RESET COMPLETED: userId={}, resetMethod={}, location={}",
                event.getUserId(), event.getResetMethod(), event.getLocation());

            String key = "password:reset:" + event.getUserId() + ":" + event.getResetAt().toString();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                🔒 Security Alert: Password Changed

                Your Waqiti account password was successfully changed.

                Reset Details:
                - Date/Time: %s
                - Reset Method: %s
                - Location: %s
                - IP Address: %s
                - Device: %s
                - Browser: %s

                If You Made This Change:
                ✅ No action needed. Your new password is now active.

                If You Did NOT Make This Change:
                🚨 IMMEDIATE ACTION REQUIRED:
                Your account may be compromised. Take these steps immediately:

                1. Reset your password again: https://example.com/security/password-reset
                2. Contact security immediately: security@example.com | 1-800-WAQITI-SEC
                3. Review recent account activity for unauthorized transactions
                4. Enable two-factor authentication (2FA)
                5. Check for unauthorized linked devices
                6. Update security questions

                Security Recommendations:
                ✅ Use a strong, unique password
                • At least 12 characters long
                • Mix of uppercase, lowercase, numbers, symbols
                • Avoid common words or personal information
                • Don't reuse passwords from other accounts

                ✅ Enable Two-Factor Authentication (2FA)
                • Adds an extra layer of security
                • Prevents unauthorized access even if password is compromised
                • Set up at: https://example.com/security/2fa

                ✅ Use a Password Manager
                • Generates and stores strong passwords
                • Reduces risk of password reuse
                • We recommend: 1Password, LastPass, Bitwarden

                ✅ Regular Security Checks
                • Review linked devices monthly
                • Check recent login activity
                • Update security questions annually
                • Enable login alerts

                Account Security Status:
                - Password: ✅ Just changed
                - Two-Factor Auth: %s
                - Security Questions: %s
                - Linked Devices: %d
                - Last Security Review: %s

                Review Your Security:
                https://example.com/security

                Questions? Contact security support:
                Email: security@example.com
                Phone: 1-800-WAQITI-SEC (24/7)
                """,
                event.getResetAt(),
                getResetMethodDescription(event.getResetMethod()),
                event.getLocation() != null ? event.getLocation() : "Unknown",
                maskIpAddress(event.getIpAddress()),
                event.getDeviceType() != null ? event.getDeviceType() : "Unknown device",
                event.getBrowser() != null ? event.getBrowser() : "Unknown browser",
                event.isTwoFactorEnabled() ? "✅ Enabled" : "❌ Not enabled (HIGHLY RECOMMENDED)",
                event.hasSecurityQuestions() ? "✅ Set" : "⚠️ Not set",
                event.getLinkedDeviceCount(),
                event.getLastSecurityReview() != null ? event.getLastSecurityReview().toLocalDate().toString() : "Never");

            // Multi-channel security alert
            notificationService.sendNotification(event.getUserId(), NotificationType.PASSWORD_RESET,
                NotificationChannel.EMAIL, NotificationPriority.URGENT,
                "Security Alert: Password Changed", message, Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.PASSWORD_RESET,
                NotificationChannel.SMS, NotificationPriority.HIGH, null,
                String.format("Waqiti Security Alert: Your password was changed on %s. If this wasn't you, call 1-800-WAQITI-SEC immediately.",
                    event.getResetAt().toLocalDate()), Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.PASSWORD_RESET,
                NotificationChannel.PUSH, NotificationPriority.URGENT,
                "Password Changed",
                "Your account password was just changed. If this wasn't you, secure your account immediately.", Map.of());

            metricsCollector.incrementCounter("notification.password.reset.sent");
            metricsCollector.incrementCounter("notification.password.reset." +
                event.getResetMethod().toLowerCase().replace(" ", "_"));

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process password reset event", e);
            dlqHandler.sendToDLQ("password.reset.completed", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String getResetMethodDescription(String resetMethod) {
        return switch (resetMethod.toLowerCase()) {
            case "email_link" -> "Email reset link";
            case "sms_code" -> "SMS verification code";
            case "security_questions" -> "Security questions";
            case "support_assisted" -> "Customer support assisted";
            case "mobile_app" -> "Mobile app";
            default -> resetMethod;
        };
    }

    private String maskIpAddress(String ipAddress) {
        if (ipAddress == null) return "Unknown";
        String[] parts = ipAddress.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***.***";
        }
        return ipAddress;
    }

    private static class PasswordResetEvent {
        private UUID userId;
        private String resetMethod, location, ipAddress, deviceType, browser;
        private LocalDateTime resetAt, lastSecurityReview;
        private boolean twoFactorEnabled, securityQuestions;
        private int linkedDeviceCount;

        public UUID getUserId() { return userId; }
        public String getResetMethod() { return resetMethod; }
        public String getLocation() { return location; }
        public String getIpAddress() { return ipAddress; }
        public String getDeviceType() { return deviceType; }
        public String getBrowser() { return browser; }
        public LocalDateTime getResetAt() { return resetAt; }
        public LocalDateTime getLastSecurityReview() { return lastSecurityReview; }
        public boolean isTwoFactorEnabled() { return twoFactorEnabled; }
        public boolean hasSecurityQuestions() { return securityQuestions; }
        public int getLinkedDeviceCount() { return linkedDeviceCount; }
    }
}
