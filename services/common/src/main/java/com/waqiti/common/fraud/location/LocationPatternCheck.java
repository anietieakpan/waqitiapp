package com.waqiti.common.fraud.location;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List; /**
 * Location pattern check result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationPatternCheck {
    private boolean hasAnomalies;
    private List<String> anomalies;
    private double anomalyScore;
    private boolean isKnownLocation;
    private double confidence;
    
    /**
     * Check if there are any location pattern anomalies
     */
    public boolean hasAnomalies() {
        return hasAnomalies && anomalies != null && !anomalies.isEmpty();
    }
    
    /**
     * Get anomaly score for location patterns
     */
    public double getAnomalyScore() {
        return anomalyScore;
    }
    
    /**
     * Get list of detected anomalies
     */
    public List<String> getAnomalies() {
        return anomalies != null ? anomalies : List.of();
    }
}
