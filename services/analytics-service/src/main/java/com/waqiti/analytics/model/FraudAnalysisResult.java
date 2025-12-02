package com.waqiti.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Result of fraud analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAnalysisResult {
    
    private String eventId;
    private String transactionId;
    
    // Risk assessment
    private BigDecimal riskScore;
    private String riskLevel;
    private List<String> riskFactors;
    
    // Decision
    private String recommendation;
    private BigDecimal confidence;
    
    // Analysis metadata
    private Instant analysisTimestamp;
    private String analysisVersion;
}