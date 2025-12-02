package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// AML DTOs
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AMLViolation {
    private String violationType;
    private String description;
    private String severity;
    private LocalDateTime detectedAt;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AMLAlert {
    private UUID alertId;
    private String alertType;
    private String description;
    private String priority;
    private String status;
    private LocalDateTime createdAt;
}

// Risk Factor DTOs
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskFactor {
    private String factorType;
    private String description;
    private Double weight;
    private String category;
    private String severity;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRiskAnalysis {
    private List<RiskFactor> riskFactors;
    private Double riskScore;
    private String analysisType;
    private LocalDateTime analyzedAt;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeographicRisk {
    private List<RiskFactor> riskFactors;
    private Double riskScore;
    private String country;
    private String region;
    private String riskCategory;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndustryRisk {
    private List<RiskFactor> riskFactors;
    private Double riskScore;
    private String industryType;
    private String riskCategory;
}

// Date Range DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DateRange {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    
    public static DateRange of(LocalDateTime start, LocalDateTime end) {
        return DateRange.builder()
            .startDate(start)
            .endDate(end)
            .build();
    }
}

// SAR Request DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SARRequest {
    private UUID customerId;
    private UUID transactionId;
    private List<String> suspiciousActivities;
    private String narrativeDescription;
    private String reportedBy;
    private DateRange timeRange;
    private UUID accountId;
}

// SAR Generation Result DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SARGenerationResult {
    private UUID sarId;
    private String referenceNumber;
    private boolean successful;
    private LocalDateTime submittedAt;
    private String status;
    private String errorMessage;
}

// Risk Level enum representation
public enum RiskLevel {
    LOW, MEDIUM, HIGH
}

// Risk Level Change enum
public enum RiskLevelChange {
    NO_CHANGE, INCREASED, DECREASED
}

// Compliance Status enum
public enum ComplianceStatus {
    COMPLIANT, ALERT, REVIEW_REQUIRED, VIOLATION
}

// Monitoring Period DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringPeriod {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String periodType;
    
    public static MonitoringPeriod of(LocalDateTime start, LocalDateTime end) {
        return MonitoringPeriod.builder()
            .startDate(start)
            .endDate(end)
            .periodType("CUSTOM")
            .build();
    }
}

// Compliance Monitoring Result DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceMonitoringResult {
    private UUID customerId;
    private MonitoringPeriod monitoringPeriod;
    private Integer transactionsAnalyzed;
    private Integer alertsGenerated;
    private List<com.waqiti.compliance.domain.ComplianceAlert> alerts;
    private RiskLevelChange riskLevelChange;
    private LocalDateTime monitoredAt;
}

// Transaction Summary (for analysis)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummary {
    private UUID transactionId;
    private BigDecimal amount;
    private String currency;
    private String transactionType;
    private LocalDateTime timestamp;
    private UUID fromAccountId;
    private UUID toAccountId;
    private String description;
    private Map<String, Object> metadata;
}

// Reporting Institution Info
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportingInstitutionInfo {
    private String institutionName;
    private String rssdId;
    private String ein;
    private String address;
    private String contactPerson;
    private String phoneNumber;
}

// Analysis Results
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuringAnalysisResult {
    private boolean structuringDetected;
    private String structuringEvidence;
    private List<UUID> relatedTransactions;
    private Double confidenceScore;
    
    public static StructuringAnalysisResult noStructuring() {
        return StructuringAnalysisResult.builder()
            .structuringDetected(false)
            .confidenceScore(0.0)
            .build();
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VelocityAnalysisResult {
    private boolean excessive;
    private String velocityDetails;
    private Integer transactionCount;
    private BigDecimal totalAmount;
    private String timeframe;
    
    public static VelocityAnalysisResult normal() {
        return VelocityAnalysisResult.builder()
            .excessive(false)
            .build();
    }
}
