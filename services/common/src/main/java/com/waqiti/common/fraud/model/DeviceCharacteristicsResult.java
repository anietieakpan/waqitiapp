package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Device characteristics analysis result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCharacteristicsResult {
    
    private String deviceId;
    private String deviceType;
    private String operatingSystem;
    private String browser;
    private String screenResolution;
    private String timezone;
    private String language;
    private boolean isSuspicious;
    private double riskScore;
}