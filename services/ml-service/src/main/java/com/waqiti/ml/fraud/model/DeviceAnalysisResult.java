package com.waqiti.ml.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Result of device fingerprinting and analysis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceAnalysisResult {

    private LocalDateTime timestamp;
    private String deviceId;
    private String deviceFingerprint;

    // Device risk scores
    private Double deviceTrustScore; // 0.0 - 1.0
    private Double deviceRiskScore; // 0.0 - 1.0
    private Double overallDeviceScore; // 0.0 - 1.0

    // Device characteristics
    private Boolean isNewDevice;
    private Boolean isKnownDevice;
    private Boolean isTrustedDevice;
    private Boolean isCompromisedDevice;

    // Device reputation
    private Integer deviceAge; // days
    private Integer transactionsFromDevice;
    private Integer successfulTransactions;
    private Integer failedTransactions;
    private Double deviceSuccessRate;

    // Device anomalies
    private Boolean fingerprintMismatch;
    private Boolean suspiciousUserAgent;
    private Boolean emulatorDetected;
    private Boolean rootedOrJailbroken;
    private Boolean debuggerDetected;

    // Device attributes
    private String deviceType; // MOBILE, WEB, TABLET, etc.
    private String osType;
    private String osVersion;
    private String browserType;
    private String browserVersion;

    // Associated users
    private List<String> associatedUserIds;
    private Boolean multipleUsersDetected;

    // Risk indicators
    private List<String> deviceRiskIndicators;

    /**
     * Check if device is high risk
     */
    public boolean isHighRiskDevice() {
        return deviceRiskScore != null && deviceRiskScore > 0.7;
    }

    /**
     * Check if device should be blocked
     */
    public boolean shouldBlockDevice() {
        return Boolean.TRUE.equals(isCompromisedDevice) || 
               Boolean.TRUE.equals(emulatorDetected);
    }
}
