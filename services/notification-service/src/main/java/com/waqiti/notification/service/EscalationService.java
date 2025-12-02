package com.waqiti.notification.service;

import com.waqiti.notification.domain.Alert;
import com.waqiti.notification.domain.AlertSeverity;
import com.waqiti.notification.domain.NotificationChannel;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for handling alert escalation logic
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EscalationService {

    private final AlertNotificationService alertNotificationService;
    private final NotificationRoutingService routingService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Track escalated alerts to prevent duplicate escalations
    private final ConcurrentHashMap<String, LocalDateTime> escalatedAlerts = new ConcurrentHashMap<>();
    private static final long ESCALATION_COOLDOWN_MINUTES = 30;

    /**
     * Escalate an alert using appropriate escalation channels
     */
    public void escalateAlert(Alert alert, String correlationId) {
        try {
            String escalationKey = alert.getId() + "-" + alert.getSeverity();

            // Check if already escalated recently
            if (isRecentlyEscalated(escalationKey)) {
                log.debug("Alert {} was recently escalated, skipping", alert.getId());
                return;
            }

            log.warn("ESCALATING ALERT: {} - severity: {}, type: {}, correlationId: {}",
                alert.getId(), alert.getSeverity(), alert.getType(), correlationId);

            // Determine escalation channels
            List<NotificationChannel> escalationChannels = routingService.determineEscalationChannels(alert);

            // Create escalated alert with enhanced details
            Alert escalatedAlert = createEscalatedAlert(alert, correlationId);

            // Send through escalation channels
            for (NotificationChannel channel : escalationChannels) {
                try {
                    alertNotificationService.sendAlert(escalatedAlert, channel, correlationId);
                } catch (Exception e) {
                    log.error("Failed to send escalated alert through channel {}: {}", channel, e.getMessage());
                }
            }

            // Mark as escalated
            markAsEscalated(escalationKey);

            // Send escalation event to Kafka for tracking
            publishEscalationEvent(alert, escalationChannels, correlationId);

            // Log escalation in audit trail
            auditService.logNotificationEvent(
                "ALERT_ESCALATED",
                alert.getId(),
                Map.of(
                    "originalSeverity", alert.getSeverity().getLevel(),
                    "escalationChannels", escalationChannels.toString(),
                    "escalationReason", determineEscalationReason(alert),
                    "correlationId", correlationId,
                    "timestamp", Instant.now().toString()
                )
            );

            // Schedule follow-up escalation for critical alerts
            if (alert.getSeverity() == AlertSeverity.CRITICAL) {
                scheduleFollowUpEscalation(alert, correlationId);
            }

        } catch (Exception e) {
            log.error("Failed to escalate alert {}: {}", alert.getId(), e.getMessage(), e);
        }
    }

    /**
     * Handle escalation for unacknowledged alerts
     */
    @Async
    public void escalateUnacknowledgedAlert(Alert alert, String correlationId, long waitTimeMinutes) {
        try {
            // Wait for the specified time
            Thread.sleep(waitTimeMinutes * 60 * 1000);

            // Check if alert was acknowledged during wait time
            if (isAlertAcknowledged(alert.getId())) {
                log.info("Alert {} was acknowledged, canceling escalation", alert.getId());
                return;
            }

            log.warn("Alert {} was not acknowledged within {} minutes, escalating",
                alert.getId(), waitTimeMinutes);

            // Escalate unacknowledged alert
            Alert unacknowledgedAlert = alert.toBuilder()
                .title("UNACKNOWLEDGED: " + alert.getTitle())
                .description("This alert was not acknowledged within " + waitTimeMinutes + " minutes. " + alert.getDescription())
                .build();

            escalateAlert(unacknowledgedAlert, correlationId + "-unacked");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Escalation wait was interrupted for alert {}", alert.getId());
        } catch (Exception e) {
            log.error("Failed to escalate unacknowledged alert {}: {}", alert.getId(), e.getMessage(), e);
        }
    }

    /**
     * Escalate based on alert age for persistent issues
     */
    public void escalateByAge(Alert alert, String correlationId) {
        long ageMinutes = alert.getAgeInMinutes();

        if (shouldEscalateByAge(alert, ageMinutes)) {
            log.warn("Escalating alert {} due to age: {} minutes", alert.getId(), ageMinutes);

            Alert agedAlert = alert.toBuilder()
                .title("PERSISTENT: " + alert.getTitle())
                .description(String.format("This alert has been active for %d minutes. %s", ageMinutes, alert.getDescription()))
                .build();

            escalateAlert(agedAlert, correlationId + "-aged");
        }
    }

    /**
     * Emergency escalation for system-critical alerts
     */
    public void emergencyEscalation(Alert alert, String correlationId) {
        log.error("EMERGENCY ESCALATION for alert: {} - {}", alert.getId(), alert.getTitle());

        Alert emergencyAlert = alert.toBuilder()
            .title("EMERGENCY: " + alert.getTitle())
            .severity(AlertSeverity.CRITICAL)
            .requiresAcknowledgment(true)
            .description("EMERGENCY ESCALATION: " + alert.getDescription())
            .build();

        // Use all available channels for emergency escalation
        for (NotificationChannel channel : NotificationChannel.values()) {
            try {
                alertNotificationService.sendAlert(emergencyAlert, channel, correlationId);
            } catch (Exception e) {
                log.error("Failed emergency escalation through {}: {}", channel, e.getMessage());
            }
        }

        // Send emergency escalation event
        kafkaTemplate.send("emergency-escalations", Map.of(
            "alertId", alert.getId(),
            "originalSeverity", alert.getSeverity().getLevel(),
            "escalationType", "EMERGENCY",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private Alert createEscalatedAlert(Alert originalAlert, String correlationId) {
        return originalAlert.toBuilder()
            .title("ESCALATED: " + originalAlert.getTitle())
            .description("This alert has been escalated. Original: " + originalAlert.getDescription())
            .correlationId(correlationId + "-escalated")
            .timestamp(LocalDateTime.now())
            .build();
    }

    private boolean isRecentlyEscalated(String escalationKey) {
        LocalDateTime lastEscalation = escalatedAlerts.get(escalationKey);
        if (lastEscalation == null) {
            return false;
        }

        return LocalDateTime.now().minusMinutes(ESCALATION_COOLDOWN_MINUTES).isBefore(lastEscalation);
    }

    private void markAsEscalated(String escalationKey) {
        escalatedAlerts.put(escalationKey, LocalDateTime.now());

        // Clean up old entries periodically
        escalatedAlerts.entrySet().removeIf(entry ->
            entry.getValue().isBefore(LocalDateTime.now().minusHours(24))
        );
    }

    private String determineEscalationReason(Alert alert) {
        if (alert.getSeverity() == AlertSeverity.CRITICAL) {
            return "CRITICAL_SEVERITY";
        } else if (Boolean.TRUE.equals(alert.getRequiresAcknowledgment())) {
            return "REQUIRES_ACKNOWLEDGMENT";
        } else if (alert.getAgeInMinutes() > 60) {
            return "ALERT_AGE";
        } else {
            return "HIGH_PRIORITY";
        }
    }

    private void publishEscalationEvent(Alert alert, List<NotificationChannel> channels, String correlationId) {
        kafkaTemplate.send("alert-escalations", Map.of(
            "alertId", alert.getId(),
            "alertType", alert.getType(),
            "severity", alert.getSeverity().getLevel(),
            "escalationChannels", channels.toString(),
            "escalationReason", determineEscalationReason(alert),
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void scheduleFollowUpEscalation(Alert alert, String correlationId) {
        // Schedule a follow-up escalation for critical alerts after 15 minutes
        kafkaTemplate.send("scheduled-escalations", Map.of(
            "alertId", alert.getId(),
            "correlationId", correlationId,
            "scheduleType", "FOLLOW_UP",
            "delayMinutes", 15,
            "timestamp", Instant.now().toString()
        ));
    }

    private boolean shouldEscalateByAge(Alert alert, long ageMinutes) {
        switch (alert.getSeverity()) {
            case CRITICAL:
                return ageMinutes > 15; // Escalate critical alerts after 15 minutes
            case HIGH:
                return ageMinutes > 30; // Escalate high priority alerts after 30 minutes
            case MEDIUM:
                return ageMinutes > 60; // Escalate medium priority alerts after 1 hour
            default:
                return false; // Don't escalate low/info alerts by age
        }
    }

    private boolean isAlertAcknowledged(String alertId) {
        // This would check the alert acknowledgment status from database or cache
        // For now, return false to trigger escalation
        return false;
    }
}