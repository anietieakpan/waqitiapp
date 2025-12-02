package com.waqiti.security.rasp.detector;

import com.waqiti.security.rasp.RaspRequestWrapper;
import com.waqiti.security.rasp.model.SecurityEvent;

/**
 * Base interface for attack detectors in RASP
 */
public interface AttackDetector {
    
    /**
     * Detect potential threats in the request
     * @param request The wrapped HTTP request
     * @return SecurityEvent if threat detected, null otherwise
     */
    SecurityEvent detectThreat(RaspRequestWrapper request);
    
    /**
     * Get the name of this detector
     */
    String getDetectorName();
    
    /**
     * Check if this detector is enabled
     */
    boolean isEnabled();
    
    /**
     * Get detector priority (higher number = higher priority)
     */
    int getPriority();
}