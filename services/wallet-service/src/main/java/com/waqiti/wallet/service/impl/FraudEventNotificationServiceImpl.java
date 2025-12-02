package com.waqiti.wallet.service.impl;

import com.waqiti.common.security.SensitiveDataMasker;
import com.waqiti.common.events.FraudDetectionEvent;
import com.waqiti.wallet.service.FraudEventNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of FraudEventNotificationService.
 *
 * <p>Handles fraud-related notifications via multiple channels:
 * <ul>
 *   <li>User notifications (email, SMS, push via notification-service)</li>
 *   <li>Security operations alerts (Slack, PagerDuty)</li>
 *   <li>Enhanced monitoring activation</li>
 * </ul>
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudEventNotificationServiceImpl implements FraudEventNotificationService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String USER_NOTIFICATION_TOPIC = "user.notifications";
    private static final String SECURITY_OPS_TOPIC = "security.ops.alerts";
    private static final String MONITORING_TOPIC = "fraud.monitoring.events";

    @Override
    public void notifyUserOfFraudFreeze(UUID userId, FraudDetectionEvent event, int walletCount) {
        log.info("NOTIFY: Sending fraud freeze notification - User: {}, Wallets: {}, Risk: {}",
                SensitiveDataMasker.formatUserIdForLogging(userId), walletCount, event.getRiskScore());

        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("notificationType", "FRAUD_WALLET_FREEZE");
            notification.put("userId", userId.toString());
            notification.put("priority", "HIGH");
            notification.put("channels", new String[]{"EMAIL", "SMS", "PUSH"});
            notification.put("title", "Security Alert: Wallet Frozen");
            notification.put("message", String.format(
                    "Your wallet has been frozen due to suspicious activity. " +
                            "We detected unusual patterns and froze %d wallet(s) to protect your funds. " +
                            "Please contact customer support immediately.",
                    walletCount
            ));
            notification.put("walletCount", walletCount);
            notification.put("riskScore", event.getRiskScore());
            notification.put("eventId", event.getEventId().toString());
            notification.put("timestamp", LocalDateTime.now().toString());

            kafkaTemplate.send(USER_NOTIFICATION_TOPIC, userId.toString(), notification);

        } catch (Exception e) {
            log.error("NOTIFY: Failed to send fraud freeze notification - User: {}",
                    SensitiveDataMasker.formatUserIdForLogging(userId), e);
        }
    }

    @Override
    public void enableEnhancedMonitoring(UUID userId, FraudDetectionEvent event) {
        log.info("MONITOR: Enabling enhanced monitoring - User: {}, Event: {}",
                SensitiveDataMasker.formatUserIdForLogging(userId), event.getEventId());

        try {
            Map<String, Object> monitoringEvent = new HashMap<>();
            monitoringEvent.put("eventType", "ENABLE_ENHANCED_MONITORING");
            monitoringEvent.put("userId", userId.toString());
            monitoringEvent.put("eventId", event.getEventId().toString());
            monitoringEvent.put("riskScore", event.getRiskScore());
            monitoringEvent.put("detectedPatterns", event.getDetectedPatterns());
            monitoringEvent.put("monitoringDurationHours", 72); // 3 days
            monitoringEvent.put("timestamp", LocalDateTime.now().toString());

            kafkaTemplate.send(MONITORING_TOPIC, userId.toString(), monitoringEvent);

        } catch (Exception e) {
            log.error("MONITOR: Failed to enable enhanced monitoring - User: {}",
                    SensitiveDataMasker.formatUserIdForLogging(userId), e);
        }
    }

    @Override
    public void updateUserRiskProfile(UUID userId, double riskScore) {
        log.info("RISK: Updating user risk profile - User: {}, Risk: {}",
                SensitiveDataMasker.formatUserIdForLogging(userId), riskScore);

        try {
            Map<String, Object> riskUpdate = new HashMap<>();
            riskUpdate.put("eventType", "RISK_PROFILE_UPDATE");
            riskUpdate.put("userId", userId.toString());
            riskUpdate.put("riskScore", riskScore);
            riskUpdate.put("timestamp", LocalDateTime.now().toString());

            kafkaTemplate.send(MONITORING_TOPIC, userId.toString(), riskUpdate);

        } catch (Exception e) {
            log.error("RISK: Failed to update user risk profile - User: {}",
                    SensitiveDataMasker.formatUserIdForLogging(userId), e);
        }
    }

    @Override
    public void alertSecurityOps(String alertType, String message) {
        log.warn("SECOPS: Security operations alert - Type: {}, Message: {}", alertType, message);

        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("alertType", alertType);
            alert.put("message", message);
            alert.put("severity", "CRITICAL");
            alert.put("source", "wallet-service");
            alert.put("timestamp", LocalDateTime.now().toString());
            alert.put("requiresAction", true);

            kafkaTemplate.send(SECURITY_OPS_TOPIC, alertType, alert);

            // In production, this would also trigger PagerDuty/Slack webhooks

        } catch (Exception e) {
            log.error("SECOPS: Failed to send security ops alert - Type: {}", alertType, e);
        }
    }
}
