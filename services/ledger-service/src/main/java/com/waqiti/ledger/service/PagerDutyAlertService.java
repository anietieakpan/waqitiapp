package com.waqiti.ledger.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * PagerDuty Alert Service
 *
 * Integrates with PagerDuty Events API v2 to send critical alerts
 * to on-call engineers and leadership.
 *
 * Security: CRITICAL - Used for production incidents
 * SLA: P0 alerts must be delivered within 30 seconds
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PagerDutyAlertService {

    private final RestTemplate restTemplate;

    @Value("${waqiti.pagerduty.api-url:https://events.pagerduty.com/v2/enqueue}")
    private String pagerDutyApiUrl;

    @Value("${waqiti.pagerduty.integration-key:}")
    private String integrationKey;

    @Value("${waqiti.pagerduty.enabled:true}")
    private boolean enabled;

    /**
     * Sends a PagerDuty alert
     *
     * @param priority Priority level (P0, P1, P2, P3, P4)
     * @param message Alert message
     * @param details Additional context
     */
    public void sendAlert(String priority, String message, Map<String, Object> details) {
        if (!enabled) {
            log.warn("PagerDuty is disabled. Alert not sent: {} - {}", priority, message);
            return;
        }

        if (integrationKey == null || integrationKey.isEmpty()) {
            log.error("PagerDuty integration key not configured! Cannot send alert: {} - {}", priority, message);
            return;
        }

        try {
            Map<String, Object> payload = buildPayload(priority, message, details);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    pagerDutyApiUrl,
                    request,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ PagerDuty alert sent successfully - Priority: {}, Status: {}", priority, response.getStatusCode());
            } else {
                log.error("❌ PagerDuty alert failed - Priority: {}, Status: {}, Response: {}",
                        priority, response.getStatusCode(), response.getBody());
            }

        } catch (Exception e) {
            log.error("❌ Failed to send PagerDuty alert - Priority: {}, Message: {}, Error: {}",
                    priority, message, e.getMessage(), e);
        }
    }

    /**
     * Sends a P0 critical alert (highest priority)
     */
    public void sendP0Alert(String message, Map<String, Object> details) {
        sendAlert("P0", message, details);
    }

    /**
     * Sends a P1 high priority alert
     */
    public void sendP1Alert(String message, Map<String, Object> details) {
        sendAlert("P1", message, details);
    }

    /**
     * Sends a P2 medium priority alert
     */
    public void sendP2Alert(String message, Map<String, Object> details) {
        sendAlert("P2", message, details);
    }

    /**
     * Builds PagerDuty Events API v2 payload
     */
    private Map<String, Object> buildPayload(String priority, String message, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>();

        // Required fields
        payload.put("routing_key", integrationKey);
        payload.put("event_action", "trigger"); // trigger, acknowledge, or resolve

        // Payload data
        Map<String, Object> payloadData = new HashMap<>();
        payloadData.put("summary", message);
        payloadData.put("severity", mapPriorityToSeverity(priority));
        payloadData.put("source", "waqiti-ledger-service");
        payloadData.put("timestamp", LocalDateTime.now().toString());
        payloadData.put("component", "ledger-reconciliation");
        payloadData.put("group", "financial-systems");
        payloadData.put("class", "reconciliation-failure");

        // Custom details
        Map<String, Object> customDetails = new HashMap<>();
        customDetails.put("priority", priority);
        customDetails.put("service", "ledger-service");
        customDetails.put("environment", System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", "production"));
        if (details != null) {
            customDetails.putAll(details);
        }
        payloadData.put("custom_details", customDetails);

        payload.put("payload", payloadData);

        // Deduplication key to prevent duplicate alerts
        String dedupKey = String.format("ledger-%s-%s", priority, message.hashCode());
        payload.put("dedup_key", dedupKey);

        // Client information
        payload.put("client", "Waqiti Ledger Service");
        payload.put("client_url", "https://api.example.com/ledger");

        return payload;
    }

    /**
     * Maps internal priority (P0-P4) to PagerDuty severity
     */
    private String mapPriorityToSeverity(String priority) {
        switch (priority.toUpperCase()) {
            case "P0":
                return "critical";
            case "P1":
                return "error";
            case "P2":
                return "warning";
            case "P3":
            case "P4":
                return "info";
            default:
                return "error";
        }
    }

    /**
     * Resolves an alert (marks as fixed)
     */
    public void resolveAlert(String dedupKey, String resolution) {
        if (!enabled) {
            log.warn("PagerDuty is disabled. Resolve not sent for key: {}", dedupKey);
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("routing_key", integrationKey);
            payload.put("event_action", "resolve");
            payload.put("dedup_key", dedupKey);

            Map<String, Object> payloadData = new HashMap<>();
            payloadData.put("summary", "Resolved: " + resolution);
            payload.put("payload", payloadData);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    pagerDutyApiUrl,
                    request,
                    Map.class
            );

            log.info("✅ PagerDuty alert resolved - Dedup Key: {}, Status: {}", dedupKey, response.getStatusCode());

        } catch (Exception e) {
            log.error("❌ Failed to resolve PagerDuty alert - Dedup Key: {}, Error: {}", dedupKey, e.getMessage(), e);
        }
    }
}
