package com.waqiti.common.fraud.location;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Location analysis result for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationAnalysisResult {
    private LocationData locationData;
    private double riskScore;
    private LocationService.RiskLevel riskLevel;
    private ImpossibleTravelCheck impossibleTravelCheck;
    private GeofenceCheck geofenceCheck;
    private LocationPatternCheck patternCheck;
    private CountryRiskCheck countryRiskCheck;
    private VpnProxyCheck vpnProxyCheck;
    private String error;
    private LocalDateTime timestamp;
    private Map<String, Object> additionalData;
    
    /**
     * Check if location is high risk
     */
    public boolean isHighRisk() {
        return riskLevel == LocationService.RiskLevel.HIGH || 
               riskLevel == LocationService.RiskLevel.CRITICAL;
    }
    
    /**
     * Get risk summary
     */
    public String getRiskSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Risk Level: %s (Score: %.2f)", riskLevel, riskScore));
        
        if (impossibleTravelCheck != null && !impossibleTravelCheck.isPossible()) {
            summary.append(" | Impossible Travel Detected");
        }
        
        if (geofenceCheck != null && geofenceCheck.hasViolations()) {
            summary.append(" | Geofence Violations: ").append(geofenceCheck.getViolations().size());
        }
        
        if (vpnProxyCheck != null && (vpnProxyCheck.isVpn() || vpnProxyCheck.isProxy())) {
            summary.append(" | VPN/Proxy Detected");
        }
        
        return summary.toString();
    }
}

