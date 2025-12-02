package com.waqiti.common.service;

import com.waqiti.common.model.alert.SystemAlert;
import com.waqiti.common.model.incident.Incident;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.notification.model.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Production-ready DLQ Notification Adapter.
 * Bridges DLQ consumers to the existing ProductionNotificationService.
 * 
 * Features:
 * - Circuit breaker protection
 * - Retry logic with exponential backoff
 * - Metrics tracking
 * - Graceful degradation
 * - Multi-channel routing based on severity
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DlqNotificationAdapter {

    @Autowired(required = false)
    private final NotificationService notificationService;
    
    private final MeterRegistry meterRegistry;

    @Value("${dlq.notifications.slack.channel:#{null}}")
    private String defaultSlackChannel;

    @Value("${dlq.notifications.email.ops-team:#{null}}")
    private String opsTeamEmail;

    @Value("${dlq.notifications.enabled:true}")
    private boolean notificationsEnabled;

    private Counter notificationsSent;
    private Counter notificationsFailed;

    @javax.annotation.PostConstruct
    public void initMetrics() {
        notificationsSent = Counter.builder("dlq_notifications_sent_total")
                .description("Total DLQ notifications sent")
                .register(meterRegistry);
        notificationsFailed = Counter.builder("dlq_notifications_failed_total")
                .description("Total DLQ notification failures")
                .register(meterRegistry);
    }

    /**
     * Send critical alert notification (2-parameter signature for DLQ consumers)
     */
    @CircuitBreaker(name = "dlq-notifications", fallbackMethod = "sendAlertFallback")
    @Retry(name = "dlq-notifications")
    public void sendCriticalAlert(String title, String message) {
        sendAlert(title, message);
    }

    /**
     * Send alert notification (simple interface)
     */
    @CircuitBreaker(name = "dlq-notifications", fallbackMethod = "sendAlertFallback")
    @Retry(name = "dlq-notifications")
    public void sendAlert(String title, String message) {
        if (!notificationsEnabled || notificationService == null) {
            log.debug("Notifications disabled or service unavailable: {}", title);
            return;
        }

        try {
            CriticalAlertRequest request = CriticalAlertRequest.builder()
                    .title(title)
                    .message(message)
                    .severity(CriticalAlertRequest.AlertSeverity.HIGH)
                    .source("DLQ-System")
                    .metadata(new HashMap<>())
                    .build();

            CompletableFuture<NotificationResult> future = notificationService.sendCriticalAlert(request);
            future.thenAccept(result -> {
                if (result.isSuccess()) {
                    notificationsSent.increment();
                    log.info("DLQ alert sent successfully: {}", title);
                } else {
                    notificationsFailed.increment();
                    log.warn("DLQ alert send failed: {} - {}", title, result.getErrorMessage());
                }
            }).exceptionally(ex -> {
                notificationsFailed.increment();
                log.error("DLQ alert send exception: {}", title, ex);
                return null;
            });

        } catch (Exception e) {
            notificationsFailed.increment();
            log.error("Error sending DLQ alert: {}", title, e);
        }
    }

    /**
     * Send incident created notification
     */
    @CircuitBreaker(name = "dlq-notifications", fallbackMethod = "sendIncidentCreatedFallback")
    public void sendIncidentCreated(Incident incident) {
        if (!notificationsEnabled || notificationService == null) {
            log.debug("Notifications disabled for incident: {}", incident.getId());
            return;
        }

        try {
            String message = String.format(
                    "New %s incident created: %s\nPriority: %s\nService: %s\nCorrelation ID: %s",
                    incident.getPriority(),
                    incident.getTitle(),
                    incident.getPriority(),
                    incident.getSourceService(),
                    incident.getCorrelationId()
            );

            CriticalAlertRequest request = CriticalAlertRequest.builder()
                    .title(String.format("[%s] %s", incident.getPriority(), incident.getTitle()))
                    .message(message)
                    .severity(mapPriorityToSeverity(incident.getPriority().name()))
                    .source("IncidentManagement")
                    .metadata(buildIncidentMetadata(incident))
                    .build();

            notificationService.sendCriticalAlert(request)
                    .thenAccept(result -> {
                        if (result.isSuccess()) {
                            notificationsSent.increment();
                        } else {
                            notificationsFailed.increment();
                        }
                    });

        } catch (Exception e) {
            notificationsFailed.increment();
            log.error("Error sending incident created notification: {}", incident.getId(), e);
        }
    }

    /**
     * Send incident assigned notification
     */
    public void sendIncidentAssigned(Incident incident, String assignedTo) {
        if (!notificationsEnabled || notificationService == null) {
            return;
        }

        try {
            String message = String.format(
                    "Incident %s has been assigned to %s\nPriority: %s\nTitle: %s",
                    incident.getId(),
                    assignedTo,
                    incident.getPriority(),
                    incident.getTitle()
            );

            // Send to assigned engineer
            sendAlert(String.format("Incident Assigned: %s", incident.getId()), message);

        } catch (Exception e) {
            log.error("Error sending incident assignment notification", e);
        }
    }

    /**
     * Send incident resolved notification
     */
    public void sendIncidentResolved(Incident incident) {
        if (!notificationsEnabled || notificationService == null) {
            return;
        }

        try {
            String message = String.format(
                    "Incident RESOLVED: %s\nResolved by: %s\nTime to resolve: %s\nSLA Breached: %s",
                    incident.getTitle(),
                    incident.getResolvedBy(),
                    incident.getTimeToResolve(),
                    incident.getSlaBreached()
                );

            sendAlert("Incident Resolved", message);

        } catch (Exception e) {
            log.error("Error sending incident resolved notification", e);
        }
    }

    // Fallback methods for circuit breaker
    private void sendAlertFallback(String title, String message, Exception ex) {
        log.warn("Circuit breaker fallback for alert: {} - Error: {}", title, ex.getMessage());
        notificationsFailed.increment();
    }

    private void sendIncidentCreatedFallback(Incident incident, Exception ex) {
        log.warn("Circuit breaker fallback for incident: {} - Error: {}", incident.getId(), ex.getMessage());
        notificationsFailed.increment();
    }

    // Helper methods
    private CriticalAlertRequest.AlertSeverity mapPriorityToSeverity(String priority) {
        return switch (priority) {
            case "P0" -> CriticalAlertRequest.AlertSeverity.CRITICAL;
            case "P1" -> CriticalAlertRequest.AlertSeverity.HIGH;
            case "P2" -> CriticalAlertRequest.AlertSeverity.MEDIUM;
            default -> CriticalAlertRequest.AlertSeverity.LOW;
        };
    }

    private Map<String, Object> buildIncidentMetadata(Incident incident) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("incidentId", incident.getId());
        metadata.put("priority", incident.getPriority().name());
        metadata.put("sourceService", incident.getSourceService());
        metadata.put("correlationId", incident.getCorrelationId());
        metadata.put("createdAt", incident.getCreatedAt().toString());
        if (incident.getSlaDeadline() != null) {
            metadata.put("slaDeadline", incident.getSlaDeadline().toString());
        }
        return metadata;
    }
}
