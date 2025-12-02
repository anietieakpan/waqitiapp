package com.waqiti.reporting.client;

import com.waqiti.reporting.dto.RegulatoryReportResult.SuspiciousActivityRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static com.waqiti.reporting.client.ComplianceServiceClient.*;

@Slf4j
@Component
public class ComplianceServiceClientFallback implements ComplianceServiceClient {

    @Override
    public List<SuspiciousActivityRecord> getSARRecords(LocalDate fromDate, LocalDate toDate) {
        log.error("FALLBACK ACTIVATED: CRITICAL - Cannot retrieve SAR records (regulatory requirement). " +
                "DateRange: {} to {}", fromDate, toDate);
        return Collections.emptyList();
    }

    @Override
    public Double getComplianceScore(LocalDate date) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve compliance score - Compliance Service unavailable. Date: {}", date);
        return null;
    }

    @Override
    public Double getFraudDetectionRate(LocalDate date) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve fraud detection rate - Compliance Service unavailable. Date: {}", date);
        return null;
    }

    @Override
    public Double getKYCCompletionRate(LocalDate date) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve KYC completion rate - Compliance Service unavailable. Date: {}", date);
        return null;
    }

    @Override
    public Long getAMLAlertsCount(LocalDate date) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve AML alerts count (compliance monitoring). Date: {}", date);
        return null;
    }

    @Override
    public Long getSARFilingCount(LocalDate date) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve SAR filing count (regulatory reporting). Date: {}", date);
        return null;
    }

    @Override
    public Long getCTRFilingCount(LocalDate date) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve CTR filing count (regulatory reporting). Date: {}", date);
        return null;
    }

    @Override
    public Long getOFACScreeningCount(LocalDate date) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve OFAC screening count (sanctions compliance). Date: {}", date);
        return null;
    }

    @Override
    public Long getPEPScreeningCount(LocalDate date) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve PEP screening count (compliance monitoring). Date: {}", date);
        return null;
    }

    @Override
    public Double getComplianceTrainingCompletion(LocalDate date) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve training completion - Compliance Service unavailable. Date: {}", date);
        return null;
    }

    @Override
    public Long getAuditFindingsCount(LocalDate date) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve audit findings count - Compliance Service unavailable. Date: {}", date);
        return null;
    }

    @Override
    public List<ComplianceAlert> getComplianceAlerts(LocalDate fromDate, LocalDate toDate) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve compliance alerts - Compliance Service unavailable. " +
                "DateRange: {} to {}", fromDate, toDate);
        return Collections.emptyList();
    }

    @Override
    public List<RegulatoryViolation> getRegulatoryViolations(LocalDate fromDate, LocalDate toDate) {
        log.error("FALLBACK ACTIVATED: CRITICAL - Cannot retrieve regulatory violations (compliance monitoring). " +
                "DateRange: {} to {}", fromDate, toDate);
        return Collections.emptyList();
    }

    @Override
    public ComplianceMetricsSummary getComplianceMetricsSummary(LocalDate date) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve compliance metrics summary - Compliance Service unavailable. Date: {}", date);
        
        return new ComplianceMetricsSummary(
                null, null, null, null,
                null, null, null, null
        );
    }

    @Override
    public List<RiskAssessmentResult> getRiskAssessmentResults(LocalDate fromDate, LocalDate toDate) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve risk assessment results - Compliance Service unavailable. " +
                "DateRange: {} to {}", fromDate, toDate);
        return Collections.emptyList();
    }
}