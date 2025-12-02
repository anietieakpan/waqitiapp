package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Device Analysis Result
 * Contains the result of device fingerprint and risk analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceAnalysisResult {

    private String deviceId;
    private String fingerprint;
    private Boolean fingerprintMatches;
    private Integer riskScore;
    private String riskLevel;
    private Boolean isTrustedDevice;
    private Boolean isSuspicious;
    private List<String> suspicionReasons;
    private Map<String, Object> deviceAttributes;
    private String analysisMethod;
    private Double confidence;
}
