package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * ML Feature Vector
 * Feature vector for machine learning models
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLFeatureVector {

    private Map<String, Double> numericFeatures;
    private Map<String, String> categoricalFeatures;
    private Map<String, Object> rawFeatures;
    private Instant timestamp;

    public static MLFeatureVector fromAuthEvent(AuthenticationEvent event) {
        Map<String, Double> numeric = new java.util.HashMap<>();
        Map<String, String> categorical = new java.util.HashMap<>();
        Map<String, Object> raw = new java.util.HashMap<>();

        // Numeric features
        if (event.getLatitude() != null) numeric.put("latitude", event.getLatitude());
        if (event.getLongitude() != null) numeric.put("longitude", event.getLongitude());
        if (event.getAuthAttempts() != null) numeric.put("auth_attempts", event.getAuthAttempts().doubleValue());
        if (event.getTimeToComplete() != null) numeric.put("time_to_complete", event.getTimeToComplete().doubleValue());
        if (event.getRecentAuthCount() != null) numeric.put("recent_auth_count", event.getRecentAuthCount().doubleValue());
        if (event.getRecentFailedAuthCount() != null) numeric.put("recent_failed_count", event.getRecentFailedAuthCount().doubleValue());
        if (event.getDeviceRiskScore() != null) numeric.put("device_risk_score", event.getDeviceRiskScore().doubleValue());

        // Categorical features
        if (event.getCountry() != null) categorical.put("country", event.getCountry());
        if (event.getAuthMethod() != null) categorical.put("auth_method", event.getAuthMethod().toString());
        if (event.getAuthResult() != null) categorical.put("auth_result", event.getAuthResult().toString());
        if (event.getUserRiskLevel() != null) categorical.put("user_risk_level", event.getUserRiskLevel());
        if (event.getBrowserName() != null) categorical.put("browser", event.getBrowserName());
        if (event.getOsName() != null) categorical.put("os", event.getOsName());

        // Raw features for reference
        raw.put("event_id", event.getEventId());
        raw.put("user_id", event.getUserId());
        raw.put("timestamp", event.getTimestamp());

        return MLFeatureVector.builder()
            .numericFeatures(numeric)
            .categoricalFeatures(categorical)
            .rawFeatures(raw)
            .timestamp(Instant.now())
            .build();
    }
}
