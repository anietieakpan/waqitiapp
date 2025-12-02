package com.waqiti.common.fraud.model;

/**
 * Types of anomalies detected in fraud monitoring.
 * 
 * Created to resolve compilation error in RealTimeFraudMonitoringService.
 */
public enum AnomalyType {
    /**
     * Unusual amount for the user
     */
    UNUSUAL_AMOUNT,
    
    /**
     * Transaction at unusual time
     */
    UNUSUAL_TIME,
    
    /**
     * Transaction from unusual location
     */
    UNUSUAL_LOCATION,
    
    /**
     * Unusual device or fingerprint
     */
    UNUSUAL_DEVICE,
    
    /**
     * Unusual merchant or recipient
     */
    UNUSUAL_RECIPIENT,
    
    /**
     * High transaction velocity
     */
    HIGH_VELOCITY,
    
    /**
     * Behavioral pattern anomaly
     */
    BEHAVIORAL_ANOMALY,
    
    /**
     * Network or graph anomaly
     */
    NETWORK_ANOMALY,

    /**
     * New or unrecognized device
     */
    NEW_DEVICE,

    /**
     * Other anomaly type
     */
    OTHER
}
