package com.waqiti.common.fraud.model;

/**
 * Severity levels for detected anomalies.
 * 
 * Created to resolve compilation error in RealTimeFraudMonitoringService.
 */
public enum AnomalySeverity {
    /**
     * Low severity - informational
     */
    LOW(1.0),
    
    /**
     * Medium severity - requires monitoring
     */
    MEDIUM(2.0),
    
    /**
     * High severity - requires attention
     */
    HIGH(3.0),
    
    /**
     * Critical severity - immediate action required
     */
    CRITICAL(4.0);
    
    private final double weight;
    
    AnomalySeverity(double weight) {
        this.weight = weight;
    }
    
    public double getWeight() {
        return weight;
    }
}
