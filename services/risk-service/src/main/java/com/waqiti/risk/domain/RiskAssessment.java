package com.waqiti.risk.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "risk_assessments")
public class RiskAssessment {
    @Id
    private String id;
    private String eventId;
    private String transactionId;
    private String userId;
    private String merchantId;
    
    // Transaction details
    private String transactionType;
    private BigDecimal transactionAmount;
    private String currency;
    private String channel;
    private String ipAddress;
    private String deviceId;
    private String location;
    private String sessionId;
    
    // Risk scores
    private Double riskScore;
    private RiskLevel riskLevel;
    private Double mlFraudScore;
    private String mlModelVersion;
    private Double mlConfidence;
    private Double behaviorScore;
    private Double behaviorDeviation;
    private Double velocityScore;
    private Double geographicRiskScore;
    private Double deviceRiskScore;
    private Double patternScore;
    private Double merchantRiskScore;
    
    // Risk factors
    private Set<RiskFactor> riskFactors;
    private List<String> triggeredRules;
    
    // Geographic risk
    private String countryCode;
    private String city;
    private boolean impossibleTravelDetected;
    
    // Device risk
    private String deviceTrustLevel;
    
    // Merchant risk
    private String merchantCategory;
    
    // Decision
    private RiskDecision decision;
    private String decisionReason;
    private LocalDateTime decisionMadeAt;
    private String requiredAuthentication;
    private List<String> mitigationMeasures;
    
    // Timestamps
    private LocalDateTime assessmentStartedAt;
    private LocalDateTime assessmentCompletedAt;
    private Long processingTimeMs;
    
    // Metadata
    private String correlationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

