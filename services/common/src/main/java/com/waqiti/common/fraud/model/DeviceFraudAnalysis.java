package com.waqiti.common.fraud.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Device fraud analysis
 */
@Data
@Builder
@Jacksonized
public class DeviceFraudAnalysis {
    private String deviceId;
    private String deviceFingerprint;
    private String fingerprint;
    private double riskScore;
    private Double confidence; // PRODUCTION FIX: Confidence score for ComprehensiveFraudBlacklistService
    private DeviceRiskLevel riskLevel;
    private boolean isJailbroken;
    private boolean isEmulator;
    private boolean isKnownDevice;
    private String deviceReputation;
    private DeviceReputationResult reputation;
    private DeviceCharacteristicsResult characteristics;
    private DeviceVelocityResult velocity;
    private DeviceSpoofingResult spoofing;
    private String analysisError;
    private List<String> anomalies;
    private Map<String, Object> deviceAttributes;
    private Instant firstSeen;
    private Instant lastSeen;

    /**
     * Get device fingerprint for identification
     */
    public String deviceFingerprint() {
        return deviceFingerprint != null ? deviceFingerprint : fingerprint;
    }

    /**
     * PRODUCTION FIX: Get confidence score
     */
    public Double getConfidence() {
        if (confidence != null) {
            return confidence;
        }
        // Calculate confidence based on device characteristics
        double baseConfidence = 1.0 - (riskScore / 100.0);
        if (isKnownDevice) {
            baseConfidence *= 1.2; // More confidence for known devices
        }
        if (isJailbroken || isEmulator) {
            baseConfidence *= 0.7; // Less confidence for suspicious devices
        }
        return Math.min(1.0, baseConfidence);
    }
}