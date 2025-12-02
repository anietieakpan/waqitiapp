package com.waqiti.kyc.compatibility;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompatibilityHealthStatus {
    
    private boolean legacyServiceHealthy;
    private boolean newServiceHealthy;
    private String legacyServiceError;
    private String newServiceError;
    
    // Feature flag states
    private boolean useNewService;
    private boolean dualWriteMode;
    private boolean shadowMode;
    
    // Metrics
    private Long totalLegacyCalls;
    private Long totalNewServiceCalls;
    private Long shadowModeMatches;
    private Long shadowModeMismatches;
    private Double shadowModeMatchRate;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    public boolean isFullyOperational() {
        return legacyServiceHealthy || newServiceHealthy;
    }
    
    public boolean isDegraded() {
        return (legacyServiceHealthy && !newServiceHealthy) || 
               (!legacyServiceHealthy && newServiceHealthy);
    }
    
    public boolean isDown() {
        return !legacyServiceHealthy && !newServiceHealthy;
    }
    
    public String getOperationalStatus() {
        if (isFullyOperational() && legacyServiceHealthy && newServiceHealthy) {
            return "HEALTHY";
        } else if (isDegraded()) {
            return "DEGRADED";
        } else if (isDown()) {
            return "DOWN";
        } else {
            return "PARTIAL";
        }
    }
}