package com.waqiti.common.encryption;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Status information about key rotation state
 */
@Data
@Builder
public class KeyRotationStatus {
    private int currentVersion;
    private long keyAge; // Days since current key was created
    private int rotationInterval; // Configured rotation interval in days
    private String status; // OK, WARNING, OVERDUE, ERROR
    private String message;
    private boolean rotationRecommended;
    private LocalDateTime lastRotation;
    private int totalKeyVersions;
}