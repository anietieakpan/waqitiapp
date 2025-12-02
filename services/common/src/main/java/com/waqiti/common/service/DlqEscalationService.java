package com.waqiti.common.service;

import com.waqiti.common.model.incident.Incident;
import com.waqiti.common.model.incident.IncidentPriority;
import com.waqiti.common.monitoring.ProductionAlertingService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-ready DLQ Escalation Service.
 * Handles incident escalation with PagerDuty, Slack, and multi-tier escalation policies.
 * 
 * Features:
 * - PagerDuty integration for P0/P1 incidents
 * - Multi-tier escalation (L1 → L2 → L3 → Executive)
 * - SLA-based auto-escalation
 * - Escalation cooldowns to prevent spam
 * - Circuit breaker protection
 * - Comprehensive metrics
 * - Async escalation processing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DlqEscalationService {

    @Autowired(required = false)
    private final ProductionAlertingService alertingService;

    @Autowired(required = false)
    private final DlqNotificationAdapter notificationAdapter;

    private final MeterRegistry meterRegistry;
    private final RestTemplate restTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${escalation.pagerduty.api-key:#{null}}")
    private String pagerDutyApiKey;

    @Value("${escalation.pagerduty.service-key.p0:#{null}}")
    private String pagerDutyP0ServiceKey;

    @Value("${escalation.pagerduty.service-key.p1:#{null}}")
    private String pagerDutyP1ServiceKey;

    @Value("${escalation.slack.webhook.critical:#{null}}")
    private String slackCriticalWebhook;

    @Value("${escalation.slack.webhook.oncall:#{null}}")
    private String slackOnCallWebhook;

    @Value("${escalation.enabled:true}")
    private boolean escalationEnabled;

    @Value("${escalation.cooldown.p0-minutes:5}")
    private int p0CooldownMinutes;

    @Value("${escalation.cooldown.p1-minutes:15}")
    private int p1CooldownMinutes;

    // Track escalations to prevent duplicates
    private final ConcurrentHashMap<String, Instant> escalationTracker = new ConcurrentHashMap<>();

    private Counter escalationsP0;
    private Counter escalationsP1;
    private Counter escalationsFailed;
    private Counter pagerDutyIncidents;

    @javax.annotation.PostConstruct
    public void initMetrics() {
        escalationsP0 = Counter.builder("dlq_escalations_p0_total")
                .description("Total P0 escalations triggered")
                .register(meterRegistry);
        escalationsP1 = Counter.builder("dlq_escalations_p1_total")
                .description("Total P1 escalations triggered")
                .register(meterRegistry);
        escalationsFailed = Counter.builder("dlq_escalations_failed_total")
                .description("Total escalation failures")
                .register(meterRegistry);
        pagerDutyIncidents = Counter.builder("pagerduty_incidents_created_total")
                .description("Total PagerDuty incidents created")
                .register(meterRegistry);
    }

    /**
     * Escalate P0 incident immediately with all channels
     */
    @Async
    @CircuitBreaker(name = "escalation", fallbackMethod = "escalateP0IncidentFallback")
    public void escalateP0Incident(Incident incident) {
        if (!escalationEnabled) {
            log.debug("Escalation disabled for P0 incident: {}", incident.getId());
            return;
        }

        String escalationKey = "P0-" + incident.getId();
        if (isRecentlyEscalated(escalationKey, p0CooldownMinutes)) {
            log.debug("P0 incident {} recently escalated, skipping", incident.getId());
            return;
        }

        log.error("🚨 P0 ESCALATION: {} - {}", incident.getId(), incident.getTitle());

        try {
            // 1. Create PagerDuty incident
            if (pagerDutyP0ServiceKey != null && pagerDutyApiKey != null) {
                createPagerDutyIncident(incident, pagerDutyP0ServiceKey, "P0");
            }

            // 2. Send critical Slack alert
            if (slackCriticalWebhook != null) {
                sendSlackEscalation(incident, slackCriticalWebhook, "P0 - CRITICAL");
            }

            // 3. Send email/SMS to on-call engineers
            if (notificationAdapter != null) {
                notificationAdapter.sendAlert(
                        "🚨 P0 INCIDENT: " + incident.getTitle(),
                        buildP0EscalationMessage(incident)
                );
            }

            // 4. Send to alerting service for multi-channel broadcast
            if (alertingService != null) {
                Map<String, Object> metadata = buildEscalationMetadata(incident);
                alertingService.sendCriticalAlert(
                        "P0 Incident: " + incident.getTitle(),
                        incident.getDescription(),
                        metadata
                );
            }

            // 5. Publish escalation event to Kafka
            publishEscalationEvent(incident, "P0", "IMMEDIATE");

            // 6. Schedule follow-up escalation if not acknowledged
            scheduleFollowUpEscalation(incident, Duration.ofMinutes(5));

            markAsEscalated(escalationKey);
            escalationsP0.increment();

            log.info("P0 escalation completed for incident: {}", incident.getId());

        } catch (Exception e) {
            escalationsFailed.increment();
            log.error("P0 escalation failed for incident: {}", incident.getId(), e);
            throw e;
        }
    }

    /**
     * Escalate P1 incident with standard on-call procedures
     */
    @Async
    @CircuitBreaker(name = "escalation", fallbackMethod = "escalateP1IncidentFallback")
    public void escalateP1Incident(Incident incident) {
        if (!escalationEnabled) {
            log.debug("Escalation disabled for P1 incident: {}", incident.getId());
            return;
        }

        String escalationKey = "P1-" + incident.getId();
        if (isRecentlyEscalated(escalationKey, p1CooldownMinutes)) {
            log.debug("P1 incident {} recently escalated, skipping", incident.getId());
            return;
        }

        log.warn("⚠️ P1 ESCALATION: {} - {}", incident.getId(), incident.getTitle());

        try {
            // 1. Create PagerDuty incident
            if (pagerDutyP1ServiceKey != null && pagerDutyApiKey != null) {
                createPagerDutyIncident(incident, pagerDutyP1ServiceKey, "P1");
            }

            // 2. Send Slack alert to on-call channel
            if (slackOnCallWebhook != null) {
                sendSlackEscalation(incident, slackOnCallWebhook, "P1 - HIGH PRIORITY");
            }

            // 3. Send notification to on-call
            if (notificationAdapter != null) {
                notificationAdapter.sendAlert(
                        "⚠️ P1 INCIDENT: " + incident.getTitle(),
                        buildP1EscalationMessage(incident)
                );
            }

            // 4. Publish escalation event
            publishEscalationEvent(incident, "P1", "HIGH");

            // 5. Schedule follow-up escalation
            scheduleFollowUpEscalation(incident, Duration.ofMinutes(15));

            markAsEscalated(escalationKey);
            escalationsP1.increment();

            log.info("P1 escalation completed for incident: {}", incident.getId());

        } catch (Exception e) {
            escalationsFailed.increment();
            log.error("P1 escalation failed for incident: {}", incident.getId(), e);
            throw e;
        }
    }

    /**
     * Notify escalation to specific level
     */
    public void notifyEscalation(Incident incident, int level, String reason) {
        if (!escalationEnabled) {
            return;
        }

        log.warn("Escalating incident {} to level {}: {}", incident.getId(), level, reason);

        try {
            String escalationMessage = String.format(
                    "Incident %s escalated to Level %d\nReason: %s\nTitle: %s\nPriority: %s",
                    incident.getId(),
                    level,
                    reason,
                    incident.getTitle(),
                    incident.getPriority()
            );

            // Route escalation based on level
            switch (level) {
                case 1 -> escalateToL1(incident, escalationMessage);
                case 2 -> escalateToL2(incident, escalationMessage);
                case 3 -> escalateToL3(incident, escalationMessage);
                default -> escalateToExecutive(incident, escalationMessage);
            }

            publishEscalationEvent(incident, "LEVEL_" + level, reason);

        } catch (Exception e) {
            escalationsFailed.increment();
            log.error("Escalation notification failed: {}", incident.getId(), e);
        }
    }

    /**
     * Create PagerDuty incident via Events API v2
     */
    @CircuitBreaker(name = "pagerduty", fallbackMethod = "createPagerDutyIncidentFallback")
    private void createPagerDutyIncident(Incident incident, String serviceKey, String priority) {
        if (pagerDutyApiKey == null || serviceKey == null) {
            log.warn("PagerDuty not configured, skipping incident creation");
            return;
        }

        try {
            String url = "https://events.pagerduty.com/v2/enqueue";

            Map<String, Object> payload = new HashMap<>();
            payload.put("routing_key", serviceKey);
            payload.put("event_action", "trigger");
            payload.put("dedup_key", "incident-" + incident.getId());

            Map<String, Object> payloadData = new HashMap<>();
            payloadData.put("summary", String.format("[%s] %s", priority, incident.getTitle()));
            payloadData.put("severity", priority.equals("P0") ? "critical" : "error");
            payloadData.put("source", incident.getSourceService());
            payloadData.put("timestamp", incident.getCreatedAt().toString());

            Map<String, Object> customDetails = new HashMap<>();
            customDetails.put("incident_id", incident.getId());
            customDetails.put("priority", incident.getPriority().name());
            customDetails.put("correlation_id", incident.getCorrelationId());
            customDetails.put("sla_deadline", incident.getSlaDeadline() != null ? 
                    incident.getSlaDeadline().toString() : "N/A");
            customDetails.put("description", incident.getDescription());

            payloadData.put("custom_details", customDetails);
            payload.put("payload", payloadData);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Token token=" + pagerDutyApiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(url, request, String.class);

            pagerDutyIncidents.increment();
            log.info("PagerDuty incident created: {} for {}", priority, incident.getId());

        } catch (Exception e) {
            log.error("Failed to create PagerDuty incident: {}", incident.getId(), e);
            throw e;
        }
    }

    /**
     * Send Slack escalation message
     */
    private void sendSlackEscalation(Incident incident, String webhookUrl, String priorityLabel) {
        try {
            Map<String, Object> slackPayload = new HashMap<>();
            slackPayload.put("text", String.format("*%s*\n%s", priorityLabel, incident.getTitle()));

            Map<String, Object> attachment = new HashMap<>();
            attachment.put("color", incident.isP0() ? "danger" : "warning");
            attachment.put("title", "Incident Details");
            attachment.put("text", incident.getDescription());

            Map<String, String> field1 = new HashMap<>();
            field1.put("title", "Incident ID");
            field1.put("value", incident.getId());
            field1.put("short", "true");

            Map<String, String> field2 = new HashMap<>();
            field2.put("title", "Priority");
            field2.put("value", incident.getPriority().name());
            field2.put("short", "true");

            Map<String, String> field3 = new HashMap<>();
            field3.put("title", "Service");
            field3.put("value", incident.getSourceService());
            field3.put("short", "true");

            Map<String, String> field4 = new HashMap<>();
            field4.put("title", "SLA Deadline");
            field4.put("value", incident.getSlaDeadline() != null ? 
                    incident.getSlaDeadline().toString() : "N/A");
            field4.put("short", "true");

            attachment.put("fields", new Object[]{field1, field2, field3, field4});
            slackPayload.put("attachments", new Object[]{attachment});

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(slackPayload, headers);

            restTemplate.postForEntity(webhookUrl, request, String.class);

            log.info("Slack escalation sent for incident: {}", incident.getId());

        } catch (Exception e) {
            log.error("Failed to send Slack escalation: {}", incident.getId(), e);
        }
    }

    // Escalation tier methods
    private void escalateToL1(Incident incident, String message) {
        log.info("L1 escalation for incident: {}", incident.getId());
        if (notificationAdapter != null) {
            notificationAdapter.sendAlert("L1 Escalation: " + incident.getTitle(), message);
        }
    }

    private void escalateToL2(Incident incident, String message) {
        log.warn("L2 escalation for incident: {}", incident.getId());
        if (slackOnCallWebhook != null) {
            sendSlackEscalation(incident, slackOnCallWebhook, "L2 ESCALATION");
        }
        if (notificationAdapter != null) {
            notificationAdapter.sendAlert("L2 Escalation: " + incident.getTitle(), message);
        }
    }

    private void escalateToL3(Incident incident, String message) {
        log.error("L3 escalation for incident: {}", incident.getId());
        if (pagerDutyP1ServiceKey != null) {
            createPagerDutyIncident(incident, pagerDutyP1ServiceKey, "L3");
        }
        if (notificationAdapter != null) {
            notificationAdapter.sendAlert("🚨 L3 ESCALATION: " + incident.getTitle(), message);
        }
    }

    private void escalateToExecutive(Incident incident, String message) {
        log.error("EXECUTIVE escalation for incident: {}", incident.getId());
        if (alertingService != null) {
            alertingService.sendCriticalAlert(
                    "EXECUTIVE ESCALATION: " + incident.getTitle(),
                    message,
                    buildEscalationMetadata(incident)
            );
        }
    }

    // Helper methods
    private boolean isRecentlyEscalated(String escalationKey, int cooldownMinutes) {
        Instant lastEscalation = escalationTracker.get(escalationKey);
        if (lastEscalation == null) {
            return false;
        }

        Instant cooldownExpiry = lastEscalation.plus(Duration.ofMinutes(cooldownMinutes));
        return Instant.now().isBefore(cooldownExpiry);
    }

    private void markAsEscalated(String escalationKey) {
        escalationTracker.put(escalationKey, Instant.now());

        // Cleanup old entries (older than 24 hours)
        escalationTracker.entrySet().removeIf(entry ->
                entry.getValue().isBefore(Instant.now().minus(Duration.ofHours(24)))
        );
    }

    private void publishEscalationEvent(Incident incident, String escalationType, String reason) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "INCIDENT_ESCALATION");
            event.put("incidentId", incident.getId());
            event.put("escalationType", escalationType);
            event.put("reason", reason);
            event.put("priority", incident.getPriority().name());
            event.put("sourceService", incident.getSourceService());
            event.put("correlationId", incident.getCorrelationId());
            event.put("timestamp", Instant.now().toString());

            kafkaTemplate.send("incident-escalations", incident.getId(), event);

        } catch (Exception e) {
            log.error("Failed to publish escalation event: {}", incident.getId(), e);
        }
    }

    private void scheduleFollowUpEscalation(Incident incident, Duration delay) {
        try {
            Map<String, Object> followUp = new HashMap<>();
            followUp.put("incidentId", incident.getId());
            followUp.put("escalationType", "FOLLOW_UP");
            followUp.put("delayMinutes", delay.toMinutes());
            followUp.put("scheduleTime", Instant.now().plus(delay).toString());

            kafkaTemplate.send("scheduled-escalations", incident.getId(), followUp);

        } catch (Exception e) {
            log.error("Failed to schedule follow-up escalation: {}", incident.getId(), e);
        }
    }

    private String buildP0EscalationMessage(Incident incident) {
        return String.format(
                "🚨 P0 CRITICAL INCIDENT 🚨\n\n" +
                "Incident ID: %s\n" +
                "Title: %s\n" +
                "Service: %s\n" +
                "Created: %s\n" +
                "SLA Deadline: %s\n" +
                "Correlation ID: %s\n\n" +
                "Description:\n%s\n\n" +
                "⚠️ IMMEDIATE ACTION REQUIRED",
                incident.getId(),
                incident.getTitle(),
                incident.getSourceService(),
                incident.getCreatedAt(),
                incident.getSlaDeadline(),
                incident.getCorrelationId(),
                incident.getDescription()
        );
    }

    private String buildP1EscalationMessage(Incident incident) {
        return String.format(
                "⚠️ P1 HIGH PRIORITY INCIDENT\n\n" +
                "Incident ID: %s\n" +
                "Title: %s\n" +
                "Service: %s\n" +
                "Created: %s\n" +
                "SLA Deadline: %s\n\n" +
                "Description:\n%s",
                incident.getId(),
                incident.getTitle(),
                incident.getSourceService(),
                incident.getCreatedAt(),
                incident.getSlaDeadline(),
                incident.getDescription()
        );
    }

    private Map<String, Object> buildEscalationMetadata(Incident incident) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("incidentId", incident.getId());
        metadata.put("priority", incident.getPriority().name());
        metadata.put("sourceService", incident.getSourceService());
        metadata.put("correlationId", incident.getCorrelationId());
        metadata.put("createdAt", incident.getCreatedAt().toString());
        metadata.put("slaDeadline", incident.getSlaDeadline() != null ? 
                incident.getSlaDeadline().toString() : "N/A");
        return metadata;
    }

    // Fallback methods for circuit breaker
    private void escalateP0IncidentFallback(Incident incident, Exception ex) {
        log.error("Circuit breaker fallback for P0 escalation: {} - Error: {}", 
                incident.getId(), ex.getMessage());
        escalationsFailed.increment();
    }

    private void escalateP1IncidentFallback(Incident incident, Exception ex) {
        log.warn("Circuit breaker fallback for P1 escalation: {} - Error: {}", 
                incident.getId(), ex.getMessage());
        escalationsFailed.increment();
    }

    private void createPagerDutyIncidentFallback(Incident incident, String serviceKey, 
                                                 String priority, Exception ex) {
        log.error("PagerDuty circuit breaker fallback: {} - Error: {}", 
                incident.getId(), ex.getMessage());
    }
}
