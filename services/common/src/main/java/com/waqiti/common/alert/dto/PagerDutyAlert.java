package com.waqiti.common.alert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * PagerDuty Alert DTO
 *
 * Represents a PagerDuty Events API v2 event payload.
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagerDutyAlert {

    /**
     * Integration key (routing key) for the PagerDuty service
     */
    @JsonProperty("routing_key")
    private String routingKey;

    /**
     * Event action: trigger, acknowledge, or resolve
     */
    @JsonProperty("event_action")
    private String eventAction;

    /**
     * Deduplication key for grouping related events
     */
    @JsonProperty("dedup_key")
    private String dedupKey;

    /**
     * Event payload containing incident details
     */
    @JsonProperty("payload")
    private Payload payload;

    /**
     * Client details
     */
    @JsonProperty("client")
    private String client = "Waqiti Platform";

    /**
     * Client URL
     */
    @JsonProperty("client_url")
    private String clientUrl;

    /**
     * Payload inner class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payload {

        /**
         * Summary of the incident (max 1024 chars)
         */
        @JsonProperty("summary")
        private String summary;

        /**
         * Source of the event (hostname, service name, etc.)
         */
        @JsonProperty("source")
        private String source;

        /**
         * Severity: critical, error, warning, info
         */
        @JsonProperty("severity")
        private String severity;

        /**
         * Timestamp of the event (ISO 8601)
         */
        @JsonProperty("timestamp")
        private String timestamp;

        /**
         * Component that generated the event
         */
        @JsonProperty("component")
        private String component;

        /**
         * Group/team responsible
         */
        @JsonProperty("group")
        private String group;

        /**
         * Class/type of the event
         */
        @JsonProperty("class")
        private String clazz;

        /**
         * Custom details (arbitrary key-value pairs)
         */
        @JsonProperty("custom_details")
        private Map<String, Object> customDetails;
    }
}
