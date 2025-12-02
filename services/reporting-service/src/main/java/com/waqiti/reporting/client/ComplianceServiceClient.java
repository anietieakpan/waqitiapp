package com.waqiti.reporting.client;

import com.waqiti.reporting.dto.RegulatoryReportResult.SuspiciousActivityRecord;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@FeignClient(
    name = "compliance-service", 
    path = "/api/compliance",
    fallback = ComplianceServiceClientFallback.class
)
public interface ComplianceServiceClient {

    /**
     * Get Suspicious Activity Reports (SAR) for regulatory reporting
     */
    @GetMapping("/sar/records")
    List<SuspiciousActivityRecord> getSARRecords(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    );

    /**
     * Get overall compliance score for a date
     */
    @GetMapping("/score")
    Double getComplianceScore(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get fraud detection rate
     */
    @GetMapping("/fraud/detection-rate")
    Double getFraudDetectionRate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get KYC completion rate
     */
    @GetMapping("/kyc/completion-rate")
    Double getKYCCompletionRate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get AML alerts count for a date
     */
    @GetMapping("/aml/alerts/count")
    Long getAMLAlertsCount(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get SAR filing count
     */
    @GetMapping("/sar/count")
    Long getSARFilingCount(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get CTR filing count
     */
    @GetMapping("/ctr/count")
    Long getCTRFilingCount(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get OFAC screening count
     */
    @GetMapping("/ofac/screening/count")
    Long getOFACScreeningCount(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get PEP screening count
     */
    @GetMapping("/pep/screening/count")
    Long getPEPScreeningCount(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get compliance training completion rate
     */
    @GetMapping("/training/completion-rate")
    Double getComplianceTrainingCompletion(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get audit findings count
     */
    @GetMapping("/audit/findings/count")
    Long getAuditFindingsCount(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get compliance alerts for reporting
     */
    @GetMapping("/alerts")
    List<ComplianceAlert> getComplianceAlerts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    );

    /**
     * Get regulatory violations
     */
    @GetMapping("/violations")
    List<RegulatoryViolation> getRegulatoryViolations(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    );

    /**
     * Get compliance metrics summary
     */
    @GetMapping("/metrics/summary")
    ComplianceMetricsSummary getComplianceMetricsSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get risk assessment results
     */
    @GetMapping("/risk-assessment")
    List<RiskAssessmentResult> getRiskAssessmentResults(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    );

    // Supporting DTOs
    record ComplianceAlert(
            String alertId,
            LocalDate alertDate,
            String alertType,
            String severity,
            String customerId,
            String description,
            String status
    ) {}

    record RegulatoryViolation(
            String violationId,
            LocalDate violationDate,
            String violationType,
            String regulation,
            String description,
            String severity,
            String status,
            String remedialAction
    ) {}

    record ComplianceMetricsSummary(
            Double overallScore,
            Long totalAlerts,
            Long resolvedAlerts,
            Long pendingAlerts,
            Double kycCompletionRate,
            Long sarCount,
            Long ctrCount,
            Double fraudDetectionRate
    ) {}

    record RiskAssessmentResult(
            String customerId,
            String riskLevel,
            Double riskScore,
            LocalDate assessmentDate,
            String assessmentType,
            List<String> riskFactors
    ) {}
}