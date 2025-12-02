package com.waqiti.corebanking.client;

import com.waqiti.common.api.ApiResponse;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Feign client for Compliance Service integration
 * 
 * Provides integration with the compliance service for:
 * - Real-time transaction screening
 * - AML/KYC compliance checks
 * - Risk assessment
 * - Regulatory reporting
 */
@FeignClient(
    name = "compliance-service",
    path = "/api/v1/compliance",
    fallback = ComplianceServiceClient.ComplianceServiceFallback.class
)
public interface ComplianceServiceClient {

    /**
     * Screen transaction for AML compliance
     */
    @PostMapping("/aml/transactions/screen")
    ApiResponse<AMLScreeningResponse> screenTransaction(@Valid @RequestBody AMLScreeningRequest request);

    /**
     * Perform compliance risk assessment
     */
    @PostMapping("/risk-assessment")
    ApiResponse<RiskAssessmentResponse> performRiskAssessment(@Valid @RequestBody RiskAssessmentRequest request);

    /**
     * Screen entity against sanctions lists
     */
    @PostMapping("/sanctions/screen")
    ApiResponse<SanctionsScreeningResponse> screenEntity(@Valid @RequestBody SanctionsScreeningRequest request);

    /**
     * Generate regulatory report
     */
    @PostMapping("/reports/generate")
    ApiResponse<RegulatoryReportResponse> generateRegulatoryReport(@Valid @RequestBody GenerateReportRequest request);

    // DTOs for compliance service integration

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AMLScreeningRequest {
        private UUID transactionId;
        private UUID customerId;
        private String customerName;
        private BigDecimal amount;
        private String currency;
        private String transactionType;
        private String sourceCountry;
        private String destinationCountry;
        private LocalDateTime transactionDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AMLScreeningResponse {
        private UUID transactionId;
        private boolean approved;
        private String status;
        private List<String> alerts;
        private Integer riskScore;
        private String riskLevel;
        private boolean requiresManualReview;
        private String reviewReason;
        private LocalDateTime screenedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessmentRequest {
        private UUID entityId;
        private String entityType;
        private UUID customerId;
        private String customerName;
        private BigDecimal transactionAmount;
        private String transactionType;
        private List<String> additionalFactors;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessmentResponse {
        private UUID assessmentId;
        private UUID entityId;
        private String riskLevel;
        private Integer riskScore;
        private List<String> riskFactors;
        private boolean requiresEnhancedDueDiligence;
        private String recommendation;
        private LocalDateTime assessedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SanctionsScreeningRequest {
        private UUID entityId;
        private String entityType;
        private String fullName;
        private String dateOfBirth;
        private String nationality;
        private String address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SanctionsScreeningResponse {
        private UUID screeningId;
        private UUID entityId;
        private boolean hasMatches;
        private List<SanctionsMatch> matches;
        private String screeningStatus;
        private LocalDateTime screenedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SanctionsMatch {
        private String listName;
        private String matchedName;
        private Double confidenceScore;
        private String matchType;
        private String additionalInfo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerateReportRequest {
        private String reportType;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private List<String> includedAccounts;
        private String format;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegulatoryReportResponse {
        private UUID reportId;
        private String reportType;
        private String status;
        private String downloadUrl;
        private LocalDateTime generatedAt;
        private LocalDateTime expiresAt;
    }

    /**
     * Fallback implementation for compliance service client
     */
    @org.springframework.stereotype.Component
    public static class ComplianceServiceFallback implements ComplianceServiceClient {

        @Override
        public ApiResponse<AMLScreeningResponse> screenTransaction(AMLScreeningRequest request) {
            // Fallback: Allow transaction but mark for review
            AMLScreeningResponse response = AMLScreeningResponse.builder()
                .transactionId(request.getTransactionId())
                .approved(true)
                .status("FALLBACK_APPROVED")
                .riskScore(50)
                .riskLevel("MEDIUM")
                .requiresManualReview(true)
                .reviewReason("Compliance service unavailable - manual review required")
                .screenedAt(LocalDateTime.now())
                .build();
            
            return ApiResponse.success(response);
        }

        @Override
        public ApiResponse<RiskAssessmentResponse> performRiskAssessment(RiskAssessmentRequest request) {
            RiskAssessmentResponse response = RiskAssessmentResponse.builder()
                .assessmentId(UUID.randomUUID())
                .entityId(request.getEntityId())
                .riskLevel("MEDIUM")
                .riskScore(50)
                .requiresEnhancedDueDiligence(false)
                .recommendation("Manual review recommended - compliance service unavailable")
                .assessedAt(LocalDateTime.now())
                .build();
            
            return ApiResponse.success(response);
        }

        @Override
        public ApiResponse<SanctionsScreeningResponse> screenEntity(SanctionsScreeningRequest request) {
            SanctionsScreeningResponse response = SanctionsScreeningResponse.builder()
                .screeningId(UUID.randomUUID())
                .entityId(request.getEntityId())
                .hasMatches(false)
                .screeningStatus("FALLBACK_CLEAR")
                .screenedAt(LocalDateTime.now())
                .build();
            
            return ApiResponse.success(response);
        }

        @Override
        public ApiResponse<RegulatoryReportResponse> generateRegulatoryReport(GenerateReportRequest request) {
            RegulatoryReportResponse response = RegulatoryReportResponse.builder()
                .reportId(UUID.randomUUID())
                .reportType(request.getReportType())
                .status("FAILED")
                .generatedAt(LocalDateTime.now())
                .build();
            
            return ApiResponse.error("Compliance service unavailable", response);
        }
    }
}