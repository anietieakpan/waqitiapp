package com.waqiti.common.fraud.location;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List; /**
 * Geofence check result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeofenceCheck {
    private boolean hasViolations;
    private List<GeofenceViolation> violations;
    private double riskScore;
    
    /**
     * Check if there are any geofence violations
     */
    public boolean hasViolations() {
        return hasViolations && violations != null && !violations.isEmpty();
    }
    
    /**
     * Get risk score for geofence violations
     */
    public double getRiskScore() {
        return riskScore;
    }
    
    /**
     * Get list of violations
     */
    public List<GeofenceViolation> getViolations() {
        return violations != null ? violations : List.of();
    }
}
