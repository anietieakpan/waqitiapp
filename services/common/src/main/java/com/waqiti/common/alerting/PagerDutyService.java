package com.waqiti.common.alerting;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * ✅ CRITICAL PRODUCTION FIX: PagerDuty Integration Service
 *
 * Provides real-time incident alerting for critical production issues.
 * Integrates with PagerDuty Events API v2 for incident management.
 *
 * BUSINESS IMPACT:
 * - Immediate notification of P0/P1 incidents
 * - 24/7 on-call engineer escalation
 * - Reduced MTTR (Mean Time To Resolution)
 * - Regulatory compliance (incident response requirements)
 *
 * USAGE:
 * - P0/CRITICAL: Financial integrity issues, security breaches, data loss
 * - P1/HIGH: Payment failures, service outages, compliance violations
 * - P2/MEDIUM: Performance degradation, high error rates
 *
 * @author Waqiti Engineering Team
 * @since 2025-01-16
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PagerDutyService {

    @Value("${pagerduty.integration-key:#{null}}")
    private String integrationKey;

    @Value("${pagerduty.api-url:https://events.pagerduty.com/v2/enqueue}")
    private String apiUrl;

    @Value("${pagerduty.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate;

    /**
     * Trigger a PagerDuty incident for critical issues
     *
     * @param severity P0/P1/P2 or CRITICAL/HIGH/MEDIUM
     * @param summary Brief description of the incident
     * @param customDetails Additional context for on-call engineer
     */
    public void triggerIncident(String severity, String summary, Map<String, Object> customDetails) {
        if (!enabled) {
            log.warn("PagerDuty is disabled - Incident not triggered: {}", summary);
            return;
        }

        if (integrationKey == null || integrationKey.isBlank()) {
            log.error("PagerDuty integration key not configured - Cannot trigger incident: {}", summary);
            return;
        }

        try {
            String dedupeKey = generateDedupeKey(summary, customDetails);

            PagerDutyEvent event = PagerDutyEvent.builder()
                .routingKey(integrationKey)
                .eventAction("trigger")
                .dedupeKey(dedupeKey)
                .payload(PagerDutyPayload.builder()
                    .summary(truncate(summary, 1024))
                    .severity(mapSeverity(severity))
                    .source("waqiti-platform")
                    .timestamp(Instant.now().toString())
                    .component(extractComponent(customDetails))
                    .group(extractGroup(customDetails))
                    .customDetails(sanitizeDetails(customDetails))
                    .build())
                .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PagerDutyEvent> request = new HttpEntity<>(event, headers);

            ResponseEntity<PagerDutyResponse> response = restTemplate.postForEntity(
                apiUrl, request, PagerDutyResponse.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("PagerDuty incident triggered successfully: severity={}, dedupeKey={}, summary={}",
                    severity, dedupeKey, summary);
            } else {
                log.error("PagerDuty incident trigger failed: status={}, summary={}",
                    response.getStatusCode(), summary);
            }

        } catch (Exception e) {
            log.error("Failed to trigger PagerDuty incident: summary={}, error={}",
                summary, e.getMessage(), e);
            // Don't fail the calling operation - alerting is best-effort
        }
    }

    /**
     * Resolve an existing PagerDuty incident
     */
    public void resolveIncident(String dedupeKey, String resolution) {
        if (!enabled) {
            log.warn("PagerDuty is disabled - Cannot resolve incident: {}", dedupeKey);
            return;
        }

        try {
            PagerDutyEvent event = PagerDutyEvent.builder()
                .routingKey(integrationKey)
                .eventAction("resolve")
                .dedupeKey(dedupeKey)
                .payload(PagerDutyPayload.builder()
                    .summary(resolution)
                    .severity("info")
                    .source("waqiti-platform")
                    .timestamp(Instant.now().toString())
                    .build())
                .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PagerDutyEvent> request = new HttpEntity<>(event, headers);

            restTemplate.postForEntity(apiUrl, request, PagerDutyResponse.class);

            log.info("PagerDuty incident resolved: dedupeKey={}", dedupeKey);

        } catch (Exception e) {
            log.error("Failed to resolve PagerDuty incident: dedupeKey={}, error={}",
                dedupeKey, e.getMessage(), e);
        }
    }

    /**
     * Acknowledge an existing PagerDuty incident
     */
    public void acknowledgeIncident(String dedupeKey) {
        if (!enabled) {
            log.warn("PagerDuty is disabled - Cannot acknowledge incident: {}", dedupeKey);
            return;
        }

        try {
            PagerDutyEvent event = PagerDutyEvent.builder()
                .routingKey(integrationKey)
                .eventAction("acknowledge")
                .dedupeKey(dedupeKey)
                .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PagerDutyEvent> request = new HttpEntity<>(event, headers);

            restTemplate.postForEntity(apiUrl, request, PagerDutyResponse.class);

            log.info("PagerDuty incident acknowledged: dedupeKey={}", dedupeKey);

        } catch (Exception e) {
            log.error("Failed to acknowledge PagerDuty incident: dedupeKey={}, error={}",
                dedupeKey, e.getMessage(), e);
        }
    }

    /**
     * Map internal severity to PagerDuty severity
     */
    private String mapSeverity(String severity) {
        if (severity == null) return "error";

        return switch (severity.toUpperCase()) {
            case "P0", "CRITICAL" -> "critical";
            case "P1", "HIGH" -> "error";
            case "P2", "MEDIUM" -> "warning";
            case "P3", "LOW" -> "info";
            default -> "error";
        };
    }

    /**
     * Generate deduplication key to group related incidents
     */
    private String generateDedupeKey(String summary, Map<String, Object> details) {
        // Extract key identifiers for deduplication
        String component = extractComponent(details);
        String entityId = extractEntityId(details);

        if (entityId != null) {
            return String.format("%s:%s:%s", component, entityId, UUID.randomUUID().toString().substring(0, 8));
        } else {
            // Use summary hash for generic incidents
            return String.format("%s:%s", component, Integer.toHexString(summary.hashCode()));
        }
    }

    /**
     * Extract component from custom details
     */
    private String extractComponent(Map<String, Object> details) {
        if (details == null) return "platform";

        return String.valueOf(details.getOrDefault("component", "platform"));
    }

    /**
     * Extract group from custom details
     */
    private String extractGroup(Map<String, Object> details) {
        if (details == null) return "production";

        return String.valueOf(details.getOrDefault("group", "production"));
    }

    /**
     * Extract entity ID for deduplication
     */
    private String extractEntityId(Map<String, Object> details) {
        if (details == null) return null;

        // Try common ID fields
        for (String key : new String[]{"paymentId", "transactionId", "walletId", "userId", "entityId"}) {
            Object value = details.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }

        return null;
    }

    /**
     * Sanitize custom details to remove sensitive data
     */
    private Map<String, Object> sanitizeDetails(Map<String, Object> details) {
        if (details == null) return Map.of();

        // Remove sensitive fields
        details.remove("password");
        details.remove("token");
        details.remove("apiKey");
        details.remove("secret");
        details.remove("cvv");
        details.remove("pin");
        details.remove("cardNumber");

        // Mask partially sensitive fields
        if (details.containsKey("email")) {
            details.put("email", maskEmail(String.valueOf(details.get("email"))));
        }

        return details;
    }

    /**
     * Mask email address for privacy
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***@***";

        String[] parts = email.split("@");
        String localPart = parts[0];
        String domain = parts[1];

        if (localPart.length() <= 2) {
            return "**@" + domain;
        }

        return localPart.substring(0, 2) + "***@" + domain;
    }

    /**
     * Truncate string to max length
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() > maxLength ? str.substring(0, maxLength - 3) + "..." : str;
    }

    // DTOs for PagerDuty Events API v2

    @Data
    @Builder
    public static class PagerDutyEvent {
        private String routingKey;
        private String eventAction;  // trigger, acknowledge, resolve
        private String dedupeKey;
        private PagerDutyPayload payload;
    }

    @Data
    @Builder
    public static class PagerDutyPayload {
        private String summary;
        private String severity;  // critical, error, warning, info
        private String source;
        private String timestamp;
        private String component;
        private String group;
        private Map<String, Object> customDetails;
    }

    @Data
    public static class PagerDutyResponse {
        private String status;
        private String message;
        private String dedupeKey;
    }
}
