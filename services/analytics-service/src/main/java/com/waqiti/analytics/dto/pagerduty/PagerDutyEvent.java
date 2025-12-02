package com.waqiti.analytics.dto.pagerduty;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * PagerDuty Event API v2 Request
 *
 * Creates incidents in PagerDuty for critical operational alerts.
 *
 * API Reference: https://developer.pagerduty.com/api-reference/
 *
 * @author Waqiti Analytics Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-15
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagerDutyEvent {

    /**
     * Integration key (routing key) for the service
     */
    @JsonProperty("routing_key")
    private String routingKey;

    /**
     * Event action (trigger, acknowledge, resolve)
     */
    @JsonProperty("event_action")
    @Builder.Default
    private String eventAction = "trigger";

    /**
     * Deduplication key to prevent duplicate incidents
     */
    @JsonProperty("dedup_key")
    private String dedupKey;

    /**
     * Event payload with alert details
     */
    @JsonProperty("payload")
    private Payload payload;

    /**
     * Link to additional information
     */
    @JsonProperty("links")
    private Link[] links;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payload {
        /**
         * Brief summary of the problem
         */
        @JsonProperty("summary")
        private String summary;

        /**
         * Timestamp of the event
         */
        @JsonProperty("timestamp")
        private String timestamp;

        /**
         * Severity (critical, error, warning, info)
         */
        @JsonProperty("severity")
        private String severity;

        /**
         * Source of the event
         */
        @JsonProperty("source")
        @Builder.Default
        private String source = "analytics-service";

        /**
         * Component that triggered the event
         */
        @JsonProperty("component")
        private String component;

        /**
         * Group for similar alerts
         */
        @JsonProperty("group")
        private String group;

        /**
         * Class/type of event
         */
        @JsonProperty("class")
        private String eventClass;

        /**
         * Custom details (additional context)
         */
        @JsonProperty("custom_details")
        private Map<String, Object> customDetails;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Link {
        @JsonProperty("href")
        private String href;

        @JsonProperty("text")
        private String text;
    }
}
