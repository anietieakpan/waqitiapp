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
 * CRITICAL FIX #46: NewDeviceLoginConsumer
 * Notifies users when login occurs from a new/unrecognized device
 * Impact: Account security, unauthorized access prevention
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NewDeviceLoginConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "device.new.login", groupId = "notification-new-device-login")
    public void handle(NewDeviceLoginEvent event, Acknowledgment ack) {
        try {
            log.warn("🔐 NEW DEVICE LOGIN: userId={}, device={}, location={}",
                event.getUserId(), event.getDeviceType(), event.getLocation());

            String key = "device:new:login:" + event.getDeviceId() + ":" + event.getLoginAt().toString();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                🔐 Security Alert: New Device Login

                A login to your Waqiti account was detected from a new device.

                Login Details:
                - Device: %s
                - Operating System: %s
                - Browser: %s
                - Location: %s
                - IP Address: %s
                - Login Time: %s

                If This Was You:
                ✅ No action needed. Your device has been registered.

                You can manage this device at:
                https://example.com/security/devices

                If This Was NOT You:
                🚨 IMMEDIATE ACTION REQUIRED:
                Your account may be compromised.

                1. Secure your account NOW:
                   https://example.com/security/emergency

                2. Remove the unauthorized device:
                   https://example.com/security/devices/%s

                3. Change your password immediately

                4. Contact security: security@example.com | 1-800-WAQITI-SEC

                5. Review recent account activity

                6. Enable two-factor authentication (2FA)

                Device Information:
                - Device ID: %s
                - Device Name: %s
                - First Seen: %s
                - Status: %s

                Security Recommendations:
                🔒 Protect Your Account:
                • Enable 2FA for enhanced security
                • Use strong, unique passwords
                • Never share your login credentials
                • Log out from shared/public devices
                • Keep your contact info up to date

                🔒 Monitor Your Devices:
                • Review your device list regularly
                • Remove old/unused devices
                • Recognize your own devices
                • Report suspicious activity immediately

                Current Registered Devices:
                You currently have %d registered device(s).
                View all: https://example.com/security/devices

                What We Track:
                • Device type and OS
                • Browser information
                • Approximate location (city/state)
                • IP address
                • Login time

                What We Don't Track:
                • Exact GPS location
                • Contacts or personal files
                • Browsing history outside Waqiti
                • Device contents

                Device Management:
                • Trust this device: https://example.com/security/trust/%s
                • Remove this device: https://example.com/security/remove/%s
                • View all activity: https://example.com/security/activity

                Questions? Contact security support:
                Email: security@example.com
                Phone: 1-800-WAQITI-SEC (24/7)
                Reference: Device ID %s
                """,
                event.getDeviceType(),
                event.getOperatingSystem(),
                event.getBrowser(),
                event.getLocation() != null ? event.getLocation() : "Unknown",
                maskIpAddress(event.getIpAddress()),
                event.getLoginAt(),
                event.getDeviceId(),
                event.getDeviceId(),
                event.getDeviceName() != null ? event.getDeviceName() : "Unknown Device",
                event.getLoginAt(),
                event.isTrusted() ? "Trusted" : "New/Unverified",
                event.getTotalDeviceCount(),
                event.getDeviceId(),
                event.getDeviceId(),
                event.getDeviceId());

            // Multi-channel security alert - URGENT
            notificationService.sendNotification(event.getUserId(), NotificationType.NEW_DEVICE_LOGIN,
                NotificationChannel.EMAIL, NotificationPriority.URGENT,
                "Security Alert: New Device Login Detected", message, Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.NEW_DEVICE_LOGIN,
                NotificationChannel.PUSH, NotificationPriority.URGENT,
                "New Device Login",
                String.format("Login from %s in %s. If this wasn't you, secure your account immediately.",
                    event.getDeviceType(), event.getLocation() != null ? event.getLocation() : "unknown location"),
                Map.of());

            notificationService.sendNotification(event.getUserId(), NotificationType.NEW_DEVICE_LOGIN,
                NotificationChannel.SMS, NotificationPriority.HIGH, null,
                String.format("Waqiti: New device login from %s. If not you, call 1-800-WAQITI-SEC or visit waqiti.com/security/emergency",
                    event.getLocation() != null ? event.getLocation() : "unknown location"), Map.of());

            metricsCollector.incrementCounter("notification.new.device.login.sent");
            metricsCollector.incrementCounter("notification.new.device.login." +
                event.getDeviceType().toLowerCase().replace(" ", "_"));

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process new device login event", e);
            dlqHandler.sendToDLQ("device.new.login", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String maskIpAddress(String ipAddress) {
        if (ipAddress == null) return "Unknown";
        String[] parts = ipAddress.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***.***";
        }
        return ipAddress;
    }

    private static class NewDeviceLoginEvent {
        private UUID userId, deviceId;
        private String deviceType, deviceName, operatingSystem, browser;
        private String location, ipAddress;
        private LocalDateTime loginAt;
        private boolean trusted;
        private int totalDeviceCount;

        public UUID getUserId() { return userId; }
        public UUID getDeviceId() { return deviceId; }
        public String getDeviceType() { return deviceType; }
        public String getDeviceName() { return deviceName; }
        public String getOperatingSystem() { return operatingSystem; }
        public String getBrowser() { return browser; }
        public String getLocation() { return location; }
        public String getIpAddress() { return ipAddress; }
        public LocalDateTime getLoginAt() { return loginAt; }
        public boolean isTrusted() { return trusted; }
        public int getTotalDeviceCount() { return totalDeviceCount; }
    }
}
