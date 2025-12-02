package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Proxy detection result with comprehensive proxy/VPN/Tor detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Jacksonized
public class ProxyDetectionResult {
    private String ipAddress;
    private boolean isProxy;
    private boolean isVpn;
    private boolean isTor;
    private boolean isDataCenter;
    private boolean isHosting;
    private boolean isPublicProxy;
    private String proxyType;
    private String provider;
    private String threatLevel;
    private double riskScore;
    private List<String> detectionMethods;
    private boolean isAnonymizer;
    private boolean isRelay;
    private String connectionType;
    private double confidence;
    private boolean isAnonymous;
    private String detectionMethod;
    private LocalDateTime analysisTimestamp;
}