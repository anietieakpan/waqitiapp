package com.waqiti.ml.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Network Analysis Result DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkAnalysisResult {
    
    private String transactionId;
    private String userId;
    private String ipAddress;
    private LocalDateTime timestamp;
    
    // Risk assessment
    private double riskScore;

    /**
     * Overall network risk score (0.0 - 1.0 scale for fraud detection engine)
     */
    @Builder.Default
    private Double overallNetworkScore = 0.0;

    private String riskLevel;

    /**
     * Get overall network score - converts from 0-100 riskScore if needed
     */
    public Double getOverallNetworkScore() {
        if (overallNetworkScore != null && overallNetworkScore > 0.0) {
            return overallNetworkScore;
        }
        // Fallback: normalize riskScore (0-100) to 0.0-1.0 scale
        return riskScore / 100.0;
    }
    
    // Network characteristics
    private boolean isVpn;
    private boolean isProxy;
    private boolean isTor;
    private boolean isDatacenter;
    private boolean isMalicious;
    
    // Reputation data
    private Double reputationScore;
    private List<String> threatCategories;
    private String countryCode;
    private String asn;
    private String isp;
    
    // Analysis metadata
    private long processingTimeMs;
    private Map<String, Object> additionalData;
    
    // Geographic data
    private String city;
    private String region;
    private double latitude;
    private double longitude;
    
    // Behavioral indicators
    private boolean isAnomalous;
    private int suspiciousPatterns;
}