package com.waqiti.virtualcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Device security status DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSecurityStatus {
    private String deviceId;
    private boolean trusted;
    private double trustScore;
    private LocalDateTime lastUsed;
    private LocalDateTime registeredAt;
    private boolean requiresBiometricSetup;
    private boolean isJailbroken;
    private boolean hasSecureHardware;
    private int successfulAuthentications;
    private int failedAuthentications;
    private String deviceType;
    private String osVersion;
}