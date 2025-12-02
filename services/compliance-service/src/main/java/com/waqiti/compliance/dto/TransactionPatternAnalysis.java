package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionPatternAnalysis {
    private List<TransactionAnomaly> anomalies = new ArrayList<>();
    private boolean hasAnomalies;
    private LocalDateTime analysisTimestamp;
    private Integer transactionCount;
    private Double overallRiskScore;
    private String analysisType;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class TransactionAnomaly {
    private String anomalyType;
    private String description;
    private String severity;
    private LocalDateTime detectedAt;
    private Double confidenceScore;
    private List<String> relatedTransactionIds;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PatternAnalysisResult {
    private List<SuspiciousPattern> suspiciousPatterns = new ArrayList<>();
    private boolean hasSuspiciousPatterns;
    private LocalDateTime analyzedAt;
    private String analysisMethod;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SuspiciousPattern {
    private String patternType;
    private String description;
    private Double confidenceScore;
    private List<String> indicators;
}