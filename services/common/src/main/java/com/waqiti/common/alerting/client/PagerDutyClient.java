package com.waqiti.common.alerting.client;

import com.waqiti.common.alerting.model.Alert;
import com.waqiti.common.alerting.model.AlertSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise PagerDuty API Client
 *
 * Full-featured integration with PagerDuty Events API v2
 * - Create, acknowledge, resolve incidents
 * - Automatic deduplication
 * - Incident correlation
 * - Priority-based routing
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PagerDutyClient {

    private final RestTemplate restTemplate;

    @Value("${waqiti.alerting.pagerduty.api-key:}")
    private String apiKey;

    @Value("${waqiti.alerting.pagerduty.integration-key:}")
    private String integrationKey;

    @Value("${waqiti.alerting.pagerduty.enabled:false}")
    private boolean enabled;

    private static final String EVENTS_V2_URL = "https://events.pagerduty.com/v2/enqueue";

    // Cache for incident IDs by dedup key
    private final Map<String, String> incidentCache = new ConcurrentHashMap<>();

    /**
     * Create PagerDuty incident from alert (async)
     */
    @Async
    public CompletableFuture<String> createIncident(Alert alert) {
        if (!enabled) {
            log.debug("PagerDuty disabled, skipping incident creation");
            return CompletableFuture.completedFuture(null);
        }

        try {
            String dedupKey = generateDedupKey(alert);

            // Check if incident already exists
            if (incidentCache.containsKey(dedupKey)) {
                log.info("Incident already exists for dedup key: {}", dedupKey);
                return updateIncident(dedupKey, alert);
            }

            Map<String, Object> event = buildEventPayload(alert, "trigger");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(event, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(EVENTS_V2_URL, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String responseKey = (String) response.getBody().get("dedup_key");
                incidentCache.put(dedupKey, responseKey);
                log.info("PagerDuty incident created: {}", responseKey);
                return CompletableFuture.completedFuture(responseKey);
            }

            return CompletableFuture.failedFuture(new RuntimeException("Failed to create incident"));

        } catch (Exception e) {
            log.error("Error creating PagerDuty incident: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Acknowledge incident (stop paging)
     */
    @Async
    public CompletableFuture<Void> acknowledgeIncident(String incidentId, String acknowledger) {
        if (!enabled) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("routing_key", integrationKey);
            event.put("dedup_key", incidentId);
            event.put("event_action", "acknowledge");

            Map<String, Object> payload = new HashMap<>();
            payload.put("summary", "Acknowledged by " + acknowledger);
            payload.put("source", "waqiti-alerting");
            payload.put("severity", "info");
            event.put("payload", payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(event, headers);

            restTemplate.postForEntity(EVENTS_V2_URL, request, Map.class);
            log.info("PagerDuty incident acknowledged: {}", incidentId);

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            log.error("Error acknowledging incident: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Resolve incident
     */
    @Async
    public CompletableFuture<Void> resolveIncident(String incidentId, String resolution) {
        if (!enabled) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("routing_key", integrationKey);
            event.put("dedup_key", incidentId);
            event.put("event_action", "resolve");

            Map<String, Object> payload = new HashMap<>();
            payload.put("summary", "Resolved: " + resolution);
            payload.put("source", "waqiti-alerting");
            payload.put("severity", "info");
            event.put("payload", payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(event, headers);

            restTemplate.postForEntity(EVENTS_V2_URL, request, Map.class);
            log.info("PagerDuty incident resolved: {}", incidentId);

            incidentCache.values().removeIf(id -> id.equals(incidentId));
            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            log.error("Error resolving incident: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Update existing incident
     */
    @Async
    public CompletableFuture<String> updateIncident(String incidentId, Alert alert) {
        try {
            Map<String, Object> event = buildEventPayload(alert, "trigger");
            event.put("dedup_key", incidentId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(event, headers);

            restTemplate.postForEntity(EVENTS_V2_URL, request, Map.class);
            log.info("PagerDuty incident updated: {}", incidentId);

            return CompletableFuture.completedFuture(incidentId);

        } catch (Exception e) {
            log.error("Error updating incident: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Test PagerDuty connection
     */
    public CompletableFuture<Boolean> testConnection() {
        if (!enabled) {
            return CompletableFuture.completedFuture(false);
        }

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("routing_key", integrationKey);
            event.put("event_action", "trigger");
            event.put("dedup_key", "test-" + UUID.randomUUID());

            Map<String, Object> payload = new HashMap<>();
            payload.put("summary", "PagerDuty Connection Test");
            payload.put("source", "waqiti-alerting");
            payload.put("severity", "info");
            event.put("payload", payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(event, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(EVENTS_V2_URL, request, Map.class);
            boolean success = response.getStatusCode().is2xxSuccessful();

            if (success) {
                String dedupKey = (String) response.getBody().get("dedup_key");
                resolveIncident(dedupKey, "Test complete");
            }

            return CompletableFuture.completedFuture(success);

        } catch (Exception e) {
            log.error("PagerDuty test failed: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    private Map<String, Object> buildEventPayload(Alert alert, String action) {
        Map<String, Object> event = new HashMap<>();
        event.put("routing_key", integrationKey);
        event.put("event_action", action);
        event.put("dedup_key", generateDedupKey(alert));

        Map<String, Object> payload = new HashMap<>();
        payload.put("summary", alert.getMessage() != null ? alert.getMessage() : "Alert");
        payload.put("source", alert.getSource() != null ? alert.getSource() : "waqiti");
        payload.put("severity", mapSeverity(alert.getSeverity()));
        payload.put("timestamp", Instant.now().toString());

        Map<String, Object> details = new HashMap<>();
        details.put("alert_id", alert.getId());
        details.put("type", alert.getType());
        if (alert.getMetadata() != null) {
            details.putAll(alert.getMetadata());
        }
        payload.put("custom_details", details);

        event.put("payload", payload);
        return event;
    }

    private String generateDedupKey(Alert alert) {
        return alert.getDedupKey() != null ? alert.getDedupKey() :
               "waqiti-" + alert.getId();
    }

    private String mapSeverity(AlertSeverity severity) {
        if (severity == null) return "warning";
        switch (severity) {
            case CRITICAL: return "critical";
            case ERROR: return "error";
            case WARNING: return "warning";
            case INFO: return "info";
            default: return "warning";
        }
    }

    /**
     * Trigger PagerDuty incident (legacy method)
     */
    public void triggerIncident(String dedupKey, String summary, Map<String, Object> customDetails) {
        if (!enabled) {
            log.debug("PagerDuty disabled, would trigger incident: {}", summary);
            return;
        }

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("routing_key", integrationKey);
            event.put("event_action", "trigger");
            event.put("dedup_key", dedupKey);

            Map<String, Object> payload = new HashMap<>();
            payload.put("summary", summary);
            payload.put("severity", "critical");
            payload.put("source", "waqiti-platform");
            payload.put("timestamp", Instant.now().toString());
            payload.put("custom_details", customDetails);

            event.put("payload", payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(event, headers);

            restTemplate.postForEntity(EVENTS_V2_URL, request, String.class);
            log.info("PagerDuty incident triggered: {}", dedupKey);

        } catch (Exception e) {
            log.error("Failed to trigger PagerDuty incident: {}", dedupKey, e);
            throw new RuntimeException("PagerDuty incident trigger failed", e);
        }
    }

    /**
     * Send alert to PagerDuty (legacy method)
     */
    public void sendAlert(Alert alert) {
        Map<String, Object> details = new HashMap<>();
        details.put("alertId", alert.getId());
        details.put("message", alert.getMessage());
        details.put("severity", alert.getSeverity().name());
        details.put("source", alert.getSource());

        triggerIncident(alert.getId(), alert.getMessage(), details);
    }

    public void clearCache() {
        incidentCache.clear();
    }
}
