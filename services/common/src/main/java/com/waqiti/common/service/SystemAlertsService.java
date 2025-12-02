package com.waqiti.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.model.alert.*;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.repository.SystemAlertRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Production-grade system alerts service for managing system-wide alerts.
 * Handles alert processing, recovery, escalation, and resolution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemAlertsService {

    private final SystemAlertRepository alertRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Autowired(required = false)
    private NotificationService notificationService;

    /**
     * Process system alert from DLQ with comprehensive recovery logic
     */
    @Transactional
    public SystemAlertRecoveryResult processSystemAlertsDlq(
            String alertData,
            String messageKey,
            String correlationId,
            String alertType,
            String severity,
            String sourceService,
            Instant timestamp) {

        log.info("Processing system alert DLQ: correlationId={}, alertType={}, severity={}, source={}",
                correlationId, alertType, severity, sourceService);

        try {
            // Parse alert data
            JsonNode alertNode = objectMapper.readTree(alertData);

            // Create or update alert entity
            SystemAlert alert = createOrUpdateAlert(alertNode, alertType, severity, sourceService, correlationId);

            // Save to repository
            alertRepository.save(alert);

            // Record metrics
            recordAlertMetrics(alertType, severity, sourceService);

            // Build success result
            return SystemAlertRecoveryResult.builder()
                    .alertId(alert.getId())
                    .alertType(alertType)
                    .severity(severity)
                    .sourceService(sourceService)
                    .recovered(true)
                    .correlationId(correlationId)
                    .resolutionTime(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to process system alert DLQ: correlationId={}, error={}",
                    correlationId, e.getMessage(), e);

            return SystemAlertRecoveryResult.builder()
                    .alertType(alertType)
                    .severity(severity)
                    .sourceService(sourceService)
                    .recovered(false)
                    .failureReason(e.getMessage())
                    .correlationId(correlationId)
                    .build();
        }
    }

    /**
     * Update alert status
     */
    @Transactional
    public void updateAlertStatus(String alertId, AlertStatus status,
                                  String details, String correlationId) {
        log.info("Updating alert status: alertId={}, status={}, correlationId={}",
                alertId, status, correlationId);

        alertRepository.findById(alertId).ifPresent(alert -> {
            alert.setStatus(status);
            alert.setStatusDetails(details);
            alert.setLastUpdated(Instant.now());
            alert.setCorrelationId(correlationId);
            alertRepository.save(alert);
        });
    }

    /**
     * Process system alert (basic version)
     */
    @Transactional
    public void processAlert(Object alert) {
        log.info("Processing alert: {}", alert);

        try {
            String alertJson = objectMapper.writeValueAsString(alert);
            JsonNode alertNode = objectMapper.readTree(alertJson);

            String alertType = extractField(alertNode, "alertType", "UNKNOWN");
            String severity = extractField(alertNode, "severity", "MEDIUM");
            String sourceService = extractField(alertNode, "sourceService", "UNKNOWN");

            SystemAlert systemAlert = createOrUpdateAlert(alertNode, alertType,
                    severity, sourceService, UUID.randomUUID().toString());

            alertRepository.save(systemAlert);

        } catch (Exception e) {
            log.error("Failed to process alert", e);
            throw new RuntimeException("Alert processing failed", e);
        }
    }

    /**
     * Escalate alert
     */
    @Transactional
    public void escalateAlert(String alertId) {
        log.warn("Escalating alert: {}", alertId);

        alertRepository.findById(alertId).ifPresent(alert -> {
            alert.setStatus(AlertStatus.ESCALATED);
            alert.setLastUpdated(Instant.now());
            alertRepository.save(alert);

            // Send notification if service available
            if (notificationService != null) {
                notificationService.sendAlert("Alert Escalated",
                    String.format("Alert %s has been escalated", alertId));
            }
        });
    }

    private SystemAlert createOrUpdateAlert(JsonNode alertNode, String alertType,
                                           String severity, String sourceService,
                                           String correlationId) {
        String alertId = extractField(alertNode, "alertId", UUID.randomUUID().toString());

        return SystemAlert.builder()
                .id(alertId)
                .alertType(alertType)
                .severity(AlertSeverity.fromString(severity))
                .sourceService(sourceService)
                .status(AlertStatus.ACTIVE)
                .message(extractField(alertNode, "message", ""))
                .details(alertNode.toString())
                .correlationId(correlationId)
                .createdAt(Instant.now())
                .lastUpdated(Instant.now())
                .build();
    }

    private String extractField(JsonNode node, String fieldName, String defaultValue) {
        return node.has(fieldName) ? node.get(fieldName).asText() : defaultValue;
    }

    private void recordAlertMetrics(String alertType, String severity, String sourceService) {
        Counter.builder("system_alerts_processed")
                .tag("alert_type", alertType)
                .tag("severity", severity)
                .tag("source", sourceService)
                .register(meterRegistry)
                .increment();
    }
}