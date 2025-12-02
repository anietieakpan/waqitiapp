package com.waqiti.common.encryption;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Result of key rotation operation
 */
@Data
@Builder
public class KeyRotationResult {
    private boolean success;
    private int previousVersion;
    private int newVersion;
    private LocalDateTime rotationTime;
    private String message;
    private boolean emergencyRotation;
    private String reason;
    
    public void setEmergencyRotation(boolean emergencyRotation) {
        this.emergencyRotation = emergencyRotation;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
}