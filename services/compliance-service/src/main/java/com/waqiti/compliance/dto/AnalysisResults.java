package com.waqiti.compliance.dto;

import com.waqiti.compliance.domain.ComplianceIssue;
import com.waqiti.compliance.domain.SuspiciousPattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Consolidated analysis result classes for compliance operations
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmountAnalysisResult {
    private BigDecimal amount;
    private boolean requiresCTR;
    private boolean suspicious;
    private String suspiciousReason;

    public boolean isSuspicious() {
        return suspicious;
    }

    public String getSuspiciousReason() {
        return suspiciousReason;
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternAnalysisResult {
    private boolean hasSuspiciousPatterns;
    private List<SuspiciousPattern> suspiciousPatterns;
    private Double patternScore;
    private String analysisType;

    public List<SuspiciousPattern> getSuspiciousPatterns() {
        return suspiciousPatterns != null ? suspiciousPatterns : new ArrayList<>();
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRiskAssessment {
    private UUID customerId;
    private RiskLevel riskLevel;
    private List<String> riskFactors;
    private boolean highRisk;
    private LocalDateTime assessedAt;

    public boolean isHighRisk() {
        return highRisk;
    }

    public List<String> getRiskFactors() {
        return riskFactors != null ? riskFactors : new ArrayList<>();
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VelocityCheckResult {
    private boolean velocityExceeded;
    private String violationDetails;
    private Integer transactionCount;
    private BigDecimal transactionVolume;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityVerificationResult {
    private boolean verified;
    private String failureReason;
    private Double confidenceScore;
    private String verificationType;

    public boolean isVerified() {
        return verified;
    }

    public static IdentityVerificationResult verified() {
        return IdentityVerificationResult.builder()
                .verified(true)
                .confidenceScore(1.0)
                .verificationType("FULL_VERIFICATION")
                .build();
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerOFACResult {
    private boolean clean;
    private String matchDetails;
    private Double matchScore;
    private LocalDateTime screenedAt;

    public boolean isClean() {
        return clean;
    }

    public static CustomerOFACResult clean() {
        return CustomerOFACResult.builder()
                .clean(true)
                .matchDetails("No OFAC matches found")
                .matchScore(0.0)
                .screenedAt(LocalDateTime.now())
                .build();
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPEPResult {
    private boolean pep;
    private String pepDetails;
    private String pepCategory;
    private Double matchScore;

    public boolean isPEP() {
        return pep;
    }

    public static CustomerPEPResult notPEP() {
        return CustomerPEPResult.builder()
                .pep(false)
                .pepDetails("No PEP matches found")
                .matchScore(0.0)
                .build();
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedDueDiligenceResult {
    private boolean passed;
    private String failureReason;
    private List<ComplianceIssue> issues;
    private LocalDateTime completedAt;

    public boolean isPassed() {
        return passed;
    }

    public static EnhancedDueDiligenceResult passed() {
        return EnhancedDueDiligenceResult.builder()
                .passed(true)
                .issues(new ArrayList<>())
                .completedAt(LocalDateTime.now())
                .build();
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRiskClassification {
    public static final CustomerRiskClassification LOW = new CustomerRiskClassification("LOW", 0.2);
    public static final CustomerRiskClassification MEDIUM = new CustomerRiskClassification("MEDIUM", 0.5);
    public static final CustomerRiskClassification HIGH = new CustomerRiskClassification("HIGH", 0.8);

    private String classification;
    private Double riskScore;

    public CustomerRiskClassification(String classification, Double riskScore) {
        this.classification = classification;
        this.riskScore = riskScore;
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerComplianceResult {
    private UUID customerId;
    private boolean approved;
    private CustomerRiskClassification riskClassification;
    private List<ComplianceIssue> complianceIssues;
    private boolean requiresManualReview;
    private boolean approvalRequired;
    private LocalDateTime checkedAt;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulatorySubmissionResult {
    private boolean successful;
    private String referenceNumber;
    private String submissionId;
    private LocalDateTime submittedAt;
    private String errorMessage;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionAnomaly {
    private String anomalyType;
    private String description;
    private Double severity;
    private UUID transactionId;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionPatternAnalysis {
    private boolean hasAnomalies;
    private List<TransactionAnomaly> anomalies;
    private Double anomalyScore;

    public List<TransactionAnomaly> getAnomalies() {
        return anomalies != null ? anomalies : new ArrayList<>();
    }
}

// Enums needed
public enum CustomerRiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}

public enum SARStatus {
    DRAFT, SUBMITTED, ACCEPTED, REJECTED, PENDING_REVIEW
}
