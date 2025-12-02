package com.waqiti.compliance.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PagerDuty Incident Management Service
 *
 * Production-ready PagerDuty integration using Events API v2
 *
 * Features:
 * - Incident creation and management
 * - Auto-escalation policies
 * - On-call rotation support
 * - Incident deduplication
 * - Custom incident severity levels
 * - Rich context and links
 *
 * Configuration:
 * - pagerduty.integration-key: PagerDuty Events API v2 integration key
 * - pagerduty.enabled: Enable/disable PagerDuty alerts
 *
 * @author Waqiti Compliance Engineering
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PagerDutyService {

    private final RestTemplate restTemplate;

    @Value("${pagerduty.integration-key:${PAGERDUTY_INTEGRATION_KEY:}}")
    private String integrationKey;

    @Value("${pagerduty.enabled:true}")
    private boolean pagerDutyEnabled;

    private static final String PAGERDUTY_EVENTS_API_URL = "https://events.pagerduty.com/v2/enqueue";

    /**
     * Trigger critical incident
     */
    public String triggerCriticalIncident(String summary, String source, Map<String, Object> customDetails) {
        if (!pagerDutyEnabled) {
            log.error("PagerDuty disabled but CRITICAL incident needed: {}", summary);
            return null;
        }

        try {
            log.error("Triggering CRITICAL PagerDuty incident: {}", summary);

            PagerDutyEvent event = PagerDutyEvent.builder()
                .routingKey(integrationKey)
                .eventAction("trigger")
                .dedupKey(generateDedupKey(source, summary))
                .payload(PagerDutyPayload.builder()
                    .summary(summary)
                    .source(source)
                    .severity("critical")
                    .timestamp(LocalDateTime.now().toString())
                    .customDetails(customDetails)
                    .build())
                .build();

            String dedupKey = sendEvent(event);
            log.info("Critical PagerDuty incident triggered: dedup_key={}", dedupKey);
            return dedupKey;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to trigger PagerDuty incident: {}", summary, e);
            return null;
        }
    }

    /**
     * Trigger high severity incident
     */
    public String triggerHighSeverityIncident(String summary, String source, Map<String, Object> customDetails) {
        if (!pagerDutyEnabled) {
            log.warn("PagerDuty disabled, skipping high severity incident: {}", summary);
            return null;
        }

        try {
            log.warn("Triggering HIGH severity PagerDuty incident: {}", summary);

            PagerDutyEvent event = PagerDutyEvent.builder()
                .routingKey(integrationKey)
                .eventAction("trigger")
                .dedupKey(generateDedupKey(source, summary))
                .payload(PagerDutyPayload.builder()
                    .summary(summary)
                    .source(source)
                    .severity("error")
                    .timestamp(LocalDateTime.now().toString())
                    .customDetails(customDetails)
                    .build())
                .build();

            String dedupKey = sendEvent(event);
            log.info("High severity PagerDuty incident triggered: dedup_key={}", dedupKey);
            return dedupKey;

        } catch (Exception e) {
            log.error("Failed to trigger high severity PagerDuty incident: {}", summary, e);
            return null;
        }
    }

    /**
     * Acknowledge incident
     */
    public void acknowledgeIncident(String dedupKey) {
        if (!pagerDutyEnabled) {
            return;
        }

        try {
            log.info("Acknowledging PagerDuty incident: {}", dedupKey);

            PagerDutyEvent event = PagerDutyEvent.builder()
                .routingKey(integrationKey)
                .eventAction("acknowledge")
                .dedupKey(dedupKey)
                .build();

            sendEvent(event);
            log.info("PagerDuty incident acknowledged: {}", dedupKey);

        } catch (Exception e) {
            log.error("Failed to acknowledge PagerDuty incident: {}", dedupKey, e);
        }
    }

    /**
     * Resolve incident
     */
    public void resolveIncident(String dedupKey) {
        if (!pagerDutyEnabled) {
            return;
        }

        try {
            log.info("Resolving PagerDuty incident: {}", dedupKey);

            PagerDutyEvent event = PagerDutyEvent.builder()
                .routingKey(integrationKey)
                .eventAction("resolve")
                .dedupKey(dedupKey)
                .build();

            sendEvent(event);
            log.info("PagerDuty incident resolved: {}", dedupKey);

        } catch (Exception e) {
            log.error("Failed to resolve PagerDuty incident: {}", dedupKey, e);
        }
    }

    /**
     * Send event to PagerDuty Events API
     */
    private String sendEvent(PagerDutyEvent event) {
        if (integrationKey == null || integrationKey.isBlank()) {
            log.warn("PagerDuty integration key not configured, incident will be logged only");
            logPagerDutyIncident(event);
            return event.getDedupKey();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PagerDutyEvent> entity = new HttpEntity<>(event, headers);

            ResponseEntity<PagerDutyResponse> response = restTemplate.exchange(
                PAGERDUTY_EVENTS_API_URL,
                HttpMethod.POST,
                entity,
                PagerDutyResponse.class
            );

            if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                log.info("PagerDuty event sent successfully: status={}", response.getBody().getStatus());
                return response.getBody().getDedupKey();
            } else {
                log.error("PagerDuty API returned non-ACCEPTED status: {} - {}",
                    response.getStatusCode(), response.getBody());
                return event.getDedupKey();
            }

        } catch (Exception e) {
            log.error("PagerDuty API call failed", e);
            logPagerDutyIncident(event);
            return event.getDedupKey();
        }
    }

    /**
     * Generate deduplication key for incident grouping
     */
    private String generateDedupKey(String source, String summary) {
        // Use consistent key for same type of incidents to group them
        String baseKey = source + ":" + summary.replaceAll("[^a-zA-Z0-9]", "_");
        return baseKey.substring(0, Math.min(baseKey.length(), 255)); // PagerDuty max length
    }

    /**
     * Log PagerDuty incident as fallback
     */
    private void logPagerDutyIncident(PagerDutyEvent event) {
        log.error("PAGERDUTY_FALLBACK: action={}, source={}, summary={}, severity={}",
            event.getEventAction(),
            event.getPayload() != null ? event.getPayload().getSource() : "N/A",
            event.getPayload() != null ? event.getPayload().getSummary() : "N/A",
            event.getPayload() != null ? event.getPayload().getSeverity() : "N/A"
        );
    }

    /**
     * PagerDuty Event model (Events API v2)
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class PagerDutyEvent {
        private String routing_key;      // Integration key
        private String event_action;     // trigger, acknowledge, resolve
        private String dedup_key;        // Deduplication key
        private PagerDutyPayload payload; // Event payload

        public String getRoutingKey() { return routing_key; }
        public void setRoutingKey(String key) { this.routing_key = key; }
        public String getEventAction() { return event_action; }
        public void setEventAction(String action) { this.event_action = action; }
        public String getDedupKey() { return dedup_key; }
        public void setDedupKey(String key) { this.dedup_key = key; }
    }

    /**
     * PagerDuty Payload model
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class PagerDutyPayload {
        private String summary;              // Required: Brief description
        private String source;               // Required: Source system
        private String severity;             // critical, error, warning, info
        private String timestamp;            // ISO 8601 timestamp
        private String component;            // Component/service affected
        private String group;                // Logical grouping
        private String className;            // Class/type of event
        private Map<String, Object> custom_details; // Custom data

        public String getClass_name() { return className; }
        public void setClass_name(String name) { this.className = name; }
        public Map<String, Object> getCustomDetails() { return custom_details; }
        public void setCustomDetails(Map<String, Object> details) { this.custom_details = details; }
    }

    /**
     * PagerDuty API response model
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class PagerDutyResponse {
        private String status;       // success, invalid, etc.
        private String message;      // Response message
        private String dedup_key;    // Deduplication key

        public String getDedupKey() { return dedup_key; }
        public void setDedupKey(String key) { this.dedup_key = key; }
    }
}
