package com.waqiti.analytics.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsEvent {

    private UUID eventId;
    private String eventType;
    private String source;
    private String metricName;
    private Object metricValue;
    private String userId;
    private String sessionId;
    private LocalDateTime timestamp;
    private Map<String, Object> properties;
    private Map<String, String> tags;

    // Getter methods for compatibility
    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSource() {
        return source;
    }

    public String getMetricName() {
        return metricName;
    }

    public String getMetricType() {
        return properties != null ? (String) properties.get("metricType") : null;
    }

    public Object getMetricValue() {
        return metricValue;
    }

    public String getUserId() {
        return userId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp != null ? timestamp : LocalDateTime.now();
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public String getEngagementType() {
        return properties != null ? (String) properties.get("engagementType") : null;
    }

    public String getEngagementValue() {
        return properties != null ? (String) properties.get("engagementValue") : null;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getFeatureName() {
        return properties != null ? (String) properties.get("featureName") : null;
    }

    public String getUserAgent() {
        return properties != null ? (String) properties.get("userAgent") : null;
    }

    public String getUsageCount() {
        return properties != null ? String.valueOf(properties.get("usageCount")) : "0";
    }

    public String getUsageDuration() {
        return properties != null ? String.valueOf(properties.get("usageDuration")) : "0";
    }

    public String getConversionStep() {
        return properties != null ? (String) properties.get("conversionStep") : null;
    }

    public String getFunnelId() {
        return properties != null ? (String) properties.get("funnelId") : null;
    }

    public String getStepName() {
        return properties != null ? (String) properties.get("stepName") : null;
    }

    public String getGeneric() {
        return properties != null ? (String) properties.get("generic") : null;
    }
}
