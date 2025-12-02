package com.waqiti.common.alerting;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Production-grade Alerting Configuration
 *
 * Centralized configuration for alerting service
 * Supports environment-specific settings via Spring Boot properties
 *
 * @author Waqiti Engineering
 * @version 1.0.0
 */
@Configuration
@ConfigurationProperties(prefix = "waqiti.alerting")
@Data
@Validated
public class AlertingConfiguration {

    /**
     * Enable/disable alerting globally
     */
    @NotNull
    private Boolean enabled = true;

    /**
     * Alert deduplication window (seconds)
     * Alerts with same dedup key within this window are suppressed
     */
    @Min(1)
    private Integer deduplicationWindowSeconds = 300; // 5 minutes

    /**
     * Maximum alerts per minute (rate limiting)
     */
    @Min(1)
    private Integer maxAlertsPerMinute = 100;

    /**
     * Enable alert batching (group similar alerts)
     */
    private Boolean batchingEnabled = true;

    /**
     * Batch window (seconds) - collect alerts before sending
     */
    @Min(1)
    private Integer batchWindowSeconds = 30;

    /**
     * Enable maintenance mode (suppress non-critical alerts)
     */
    private Boolean maintenanceMode = false;

    /**
     * PagerDuty configuration
     */
    private PagerDutyConfig pagerduty = new PagerDutyConfig();

    /**
     * Slack configuration
     */
    private SlackConfig slack = new SlackConfig();

    @Data
    public static class PagerDutyConfig {
        private Boolean enabled = false;
        private String apiKey;
        private String integrationKey;
        private Integer timeoutSeconds = 10;
    }

    @Data
    public static class SlackConfig {
        private Boolean enabled = false;
        private String webhookUrl;
        private String defaultChannel = "#alerts";
        private Integer timeoutSeconds = 5;
    }

    /**
     * Convenience method: Check if PagerDuty is enabled
     */
    public boolean isPagerDutyEnabled() {
        return enabled != null && enabled && pagerduty != null && pagerduty.getEnabled() != null && pagerduty.getEnabled();
    }

    /**
     * Convenience method: Check if Slack is enabled
     */
    public boolean isSlackEnabled() {
        return enabled != null && enabled && slack != null && slack.getEnabled() != null && slack.getEnabled();
    }
}
