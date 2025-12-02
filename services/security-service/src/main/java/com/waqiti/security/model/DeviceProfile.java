package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Device Profile
 * Contains device identification and risk information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceProfile {

    private String deviceId;
    private String fingerprint;
    private boolean knownDevice;
    private Integer riskScore;
    private Instant firstSeen;
    private Instant lastSeen;
    private String deviceType;
    private String operatingSystem;
    private String browser;
    private String userAgent;
    private Integer loginCount;
    private Integer failedLoginCount;
    private Boolean trusted;
    private Boolean blocked;
    private String blockReason;
}
