package com.waqiti.notification.service;

import com.waqiti.notification.domain.Alert;
import com.waqiti.notification.domain.AlertSeverity;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for correlating related alerts and detecting patterns
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlertCorrelationService {

    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Store recent alerts for correlation analysis
    private final ConcurrentHashMap<String, List<Alert>> recentAlertsByService = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Alert>> recentAlertsByType = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> alertCounts = new ConcurrentHashMap<>();

    private static final int CORRELATION_WINDOW_MINUTES = 30;
    private static final int ALERT_BURST_THRESHOLD = 5;
    private static final int ALERT_STORM_THRESHOLD = 10;

    /**
     * Check for correlation with other alerts and detect patterns
     */
    public void checkAlertCorrelation(Alert alert, String correlationId) {
        try {
            log.debug("Checking alert correlation for alert: {} with correlationId: {}",
                alert.getId(), correlationId);

            // Store the alert for future correlation
            storeAlertForCorrelation(alert);

            // Check for service-based correlation
            checkServiceCorrelation(alert, correlationId);

            // Check for type-based correlation
            checkTypeCorrelation(alert, correlationId);

            // Check for alert burst/storm patterns
            checkAlertBurstPattern(alert, correlationId);

            // Check for cascading failure patterns
            checkCascadingFailurePattern(alert, correlationId);

            // Clean up old alerts periodically
            cleanupOldAlerts();

        } catch (Exception e) {
            log.error("Failed to check alert correlation for alert {}: {}", alert.getId(), e.getMessage(), e);
        }
    }

    /**
     * Check for related alerts from the same service
     */
    private void checkServiceCorrelation(Alert alert, String correlationId) {
        if (alert.getAffectedService() == null) {
            return;
        }

        List<Alert> serviceAlerts = recentAlertsByService.getOrDefault(alert.getAffectedService(), new ArrayList<>());
        List<Alert> correlatedAlerts = serviceAlerts.stream()
            .filter(a -> !a.getId().equals(alert.getId()))
            .filter(a -> isWithinCorrelationWindow(a.getTimestamp()))
            .toList();

        if (correlatedAlerts.size() >= 2) {
            log.warn("Service correlation detected for {}: {} related alerts in the last {} minutes",
                alert.getAffectedService(), correlatedAlerts.size(), CORRELATION_WINDOW_MINUTES);

            publishCorrelationEvent(alert, correlatedAlerts, "SERVICE_CORRELATION", correlationId);

            // Check if this indicates a service outage
            if (correlatedAlerts.size() >= 5) {
                publishServiceOutageEvent(alert, correlatedAlerts, correlationId);
            }
        }
    }

    /**
     * Check for related alerts of the same type
     */
    private void checkTypeCorrelation(Alert alert, String correlationId) {
        if (alert.getType() == null) {
            return;
        }

        List<Alert> typeAlerts = recentAlertsByType.getOrDefault(alert.getType(), new ArrayList<>());
        List<Alert> correlatedAlerts = typeAlerts.stream()
            .filter(a -> !a.getId().equals(alert.getId()))
            .filter(a -> isWithinCorrelationWindow(a.getTimestamp()))
            .toList();

        if (correlatedAlerts.size() >= 3) {
            log.warn("Type correlation detected for {}: {} related alerts in the last {} minutes",
                alert.getType(), correlatedAlerts.size(), CORRELATION_WINDOW_MINUTES);

            publishCorrelationEvent(alert, correlatedAlerts, "TYPE_CORRELATION", correlationId);
        }
    }

    /**
     * Check for alert burst or storm patterns
     */
    private void checkAlertBurstPattern(Alert alert, String correlationId) {
        String alertKey = generateAlertKey(alert);
        int count = alertCounts.merge(alertKey, 1, Integer::sum);

        if (count >= ALERT_STORM_THRESHOLD) {
            log.error("ALERT STORM detected for {}: {} alerts in correlation window", alertKey, count);
            publishAlertStormEvent(alert, count, correlationId);
        } else if (count >= ALERT_BURST_THRESHOLD) {
            log.warn("Alert burst detected for {}: {} alerts in correlation window", alertKey, count);
            publishAlertBurstEvent(alert, count, correlationId);
        }
    }

    /**
     * Check for cascading failure patterns across services
     */
    private void checkCascadingFailurePattern(Alert alert, String correlationId) {
        if (alert.getSeverity() != AlertSeverity.CRITICAL) {
            return;
        }

        // Look for critical alerts across multiple services in a short timeframe
        List<Alert> recentCriticalAlerts = getAllRecentAlerts().stream()
            .filter(a -> a.getSeverity() == AlertSeverity.CRITICAL)
            .filter(a -> isWithinCorrelationWindow(a.getTimestamp()))
            .filter(a -> !a.getId().equals(alert.getId()))
            .toList();

        Set<String> affectedServices = recentCriticalAlerts.stream()
            .map(Alert::getAffectedService)
            .filter(Objects::nonNull)
            .collect(HashSet::new, HashSet::add, HashSet::addAll);

        if (affectedServices.size() >= 3) {
            log.error("CASCADING FAILURE pattern detected: {} critical alerts across {} services",
                recentCriticalAlerts.size(), affectedServices.size());

            publishCascadingFailureEvent(alert, recentCriticalAlerts, affectedServices, correlationId);
        }
    }

    /**
     * Store alert for future correlation analysis
     */
    private void storeAlertForCorrelation(Alert alert) {
        // Store by service
        if (alert.getAffectedService() != null) {
            recentAlertsByService.computeIfAbsent(alert.getAffectedService(), k -> new ArrayList<>()).add(alert);
        }

        // Store by type
        if (alert.getType() != null) {
            recentAlertsByType.computeIfAbsent(alert.getType(), k -> new ArrayList<>()).add(alert);
        }

        // Update alert count
        String alertKey = generateAlertKey(alert);
        alertCounts.put(alertKey, alertCounts.getOrDefault(alertKey, 0) + 1);
    }

    /**
     * Check if timestamp is within correlation window
     */
    private boolean isWithinCorrelationWindow(LocalDateTime timestamp) {
        return timestamp.isAfter(LocalDateTime.now().minusMinutes(CORRELATION_WINDOW_MINUTES));
    }

    /**
     * Generate a key for alert counting and correlation
     */
    private String generateAlertKey(Alert alert) {
        return String.format("%s-%s-%s",
            alert.getType() != null ? alert.getType() : "UNKNOWN",
            alert.getAffectedService() != null ? alert.getAffectedService() : "UNKNOWN",
            alert.getSeverity().getLevel()
        );
    }

    /**
     * Get all recent alerts from all correlation maps
     */
    private List<Alert> getAllRecentAlerts() {
        List<Alert> allAlerts = new ArrayList<>();

        recentAlertsByService.values().forEach(allAlerts::addAll);
        recentAlertsByType.values().forEach(allAlerts::addAll);

        // Remove duplicates based on alert ID
        return allAlerts.stream()
            .collect(HashMap::new,
                (map, alert) -> map.put(alert.getId(), alert),
                HashMap::putAll)
            .values()
            .stream()
            .filter(alert -> isWithinCorrelationWindow(alert.getTimestamp()))
            .toList();
    }

    /**
     * Clean up old alerts from correlation maps
     */
    private void cleanupOldAlerts() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(CORRELATION_WINDOW_MINUTES * 2);

        // Clean service-based alerts
        recentAlertsByService.values().forEach(alerts ->
            alerts.removeIf(alert -> alert.getTimestamp().isBefore(cutoffTime))
        );

        // Clean type-based alerts
        recentAlertsByType.values().forEach(alerts ->
            alerts.removeIf(alert -> alert.getTimestamp().isBefore(cutoffTime))
        );

        // Clean empty lists
        recentAlertsByService.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        recentAlertsByType.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        // Reset alert counts periodically
        if (alertCounts.size() > 1000) {
            alertCounts.clear();
        }
    }

    /**
     * Publish correlation event to Kafka
     */
    private void publishCorrelationEvent(Alert alert, List<Alert> correlatedAlerts, String correlationType, String correlationId) {
        Map<String, Object> correlationEvent = Map.of(
            "primaryAlertId", alert.getId(),
            "correlatedAlertIds", correlatedAlerts.stream().map(Alert::getId).toList(),
            "correlationType", correlationType,
            "correlationCount", correlatedAlerts.size(),
            "affectedService", alert.getAffectedService() != null ? alert.getAffectedService() : "UNKNOWN",
            "alertType", alert.getType() != null ? alert.getType() : "UNKNOWN",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        );

        kafkaTemplate.send("alert-correlations", correlationEvent);

        auditService.logNotificationEvent(
            "ALERT_CORRELATION_DETECTED",
            alert.getId(),
            correlationEvent
        );
    }

    /**
     * Publish service outage event
     */
    private void publishServiceOutageEvent(Alert alert, List<Alert> correlatedAlerts, String correlationId) {
        Map<String, Object> outageEvent = Map.of(
            "eventType", "SERVICE_OUTAGE_DETECTED",
            "affectedService", alert.getAffectedService(),
            "alertCount", correlatedAlerts.size() + 1,
            "primaryAlertId", alert.getId(),
            "correlatedAlertIds", correlatedAlerts.stream().map(Alert::getId).toList(),
            "severity", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        );

        kafkaTemplate.send("service-outage-events", outageEvent);
    }

    /**
     * Publish alert burst event
     */
    private void publishAlertBurstEvent(Alert alert, int count, String correlationId) {
        Map<String, Object> burstEvent = Map.of(
            "eventType", "ALERT_BURST_DETECTED",
            "alertKey", generateAlertKey(alert),
            "burstCount", count,
            "alertId", alert.getId(),
            "alertType", alert.getType() != null ? alert.getType() : "UNKNOWN",
            "affectedService", alert.getAffectedService() != null ? alert.getAffectedService() : "UNKNOWN",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        );

        kafkaTemplate.send("alert-burst-events", burstEvent);
    }

    /**
     * Publish alert storm event
     */
    private void publishAlertStormEvent(Alert alert, int count, String correlationId) {
        Map<String, Object> stormEvent = Map.of(
            "eventType", "ALERT_STORM_DETECTED",
            "alertKey", generateAlertKey(alert),
            "stormCount", count,
            "alertId", alert.getId(),
            "alertType", alert.getType() != null ? alert.getType() : "UNKNOWN",
            "affectedService", alert.getAffectedService() != null ? alert.getAffectedService() : "UNKNOWN",
            "severity", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        );

        kafkaTemplate.send("alert-storm-events", stormEvent);
    }

    /**
     * Publish cascading failure event
     */
    private void publishCascadingFailureEvent(Alert alert, List<Alert> criticalAlerts, Set<String> affectedServices, String correlationId) {
        Map<String, Object> cascadingEvent = Map.of(
            "eventType", "CASCADING_FAILURE_DETECTED",
            "triggerAlertId", alert.getId(),
            "criticalAlertCount", criticalAlerts.size(),
            "affectedServices", new ArrayList<>(affectedServices),
            "serviceCount", affectedServices.size(),
            "severity", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        );

        kafkaTemplate.send("cascading-failure-events", cascadingEvent);
    }
}