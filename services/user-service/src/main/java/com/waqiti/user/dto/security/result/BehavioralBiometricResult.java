package com.waqiti.user.dto.security.result;

import com.waqiti.user.security.BehavioralRiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of behavioral biometric analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehavioralBiometricResult {
    private String userId;
    private double compositeScore;
    private BehavioralRiskLevel riskLevel;
    private double typingScore;
    private double interactionScore;
    private double navigationScore;
    private List<String> anomalies;
    private List<String> recommendations;
}