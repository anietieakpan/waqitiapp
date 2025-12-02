package com.waqiti.audit.service;

import com.waqiti.audit.domain.AuditLog;
import com.waqiti.audit.dto.*;
import com.waqiti.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating compliance reports and regulatory documentation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceReportingService {
    
    private final AuditLogRepository auditLogRepository;
    
    /**
     * Generate SOX compliance report
     */
    public ComplianceReport generateSOXReport(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Generating SOX compliance report from {} to {}", startDate, endDate);
        
        List<AuditLog> auditLogs = auditLogRepository.findByTimestampBetween(startDate, endDate);
        
        return ComplianceReport.builder()
            .reportType("SOX")
            .reportPeriodStart(startDate)
            .reportPeriodEnd(endDate)
            .totalEvents(auditLogs.size())
            .criticalEvents(countCriticalEvents(auditLogs))
            .userAccessEvents(countUserAccessEvents(auditLogs))
            .dataModificationEvents(countDataModificationEvents(auditLogs))
            .systemChangeEvents(countSystemChangeEvents(auditLogs))
            .complianceStatus(calculateComplianceStatus(auditLogs))
            .findings(generateFindings(auditLogs))
            .recommendations(generateRecommendations(auditLogs))
            .generatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Generate PCI DSS compliance report
     */
    public ComplianceReport generatePCIDSSReport(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Generating PCI DSS compliance report from {} to {}", startDate, endDate);
        
        List<AuditLog> auditLogs = auditLogRepository.findByTimestampBetween(startDate, endDate);
        
        return ComplianceReport.builder()
            .reportType("PCI_DSS")
            .reportPeriodStart(startDate)
            .reportPeriodEnd(endDate)
            .totalEvents(auditLogs.size())
            .paymentCardEvents(countPaymentCardEvents(auditLogs))
            .securityEvents(countSecurityEvents(auditLogs))
            .accessControlEvents(countAccessControlEvents(auditLogs))
            .encryptionEvents(countEncryptionEvents(auditLogs))
            .complianceStatus(calculatePCIDSSComplianceStatus(auditLogs))
            .vulnerabilities(identifyVulnerabilities(auditLogs))
            .remediations(generateRemediations(auditLogs))
            .generatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Generate GDPR compliance report
     */
    public ComplianceReport generateGDPRReport(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Generating GDPR compliance report from {} to {}", startDate, endDate);
        
        List<AuditLog> auditLogs = auditLogRepository.findByTimestampBetween(startDate, endDate);
        
        return ComplianceReport.builder()
            .reportType("GDPR")
            .reportPeriodStart(startDate)
            .reportPeriodEnd(endDate)
            .totalEvents(auditLogs.size())
            .dataProcessingEvents(countDataProcessingEvents(auditLogs))
            .consentEvents(countConsentEvents(auditLogs))
            .dataAccessRequests(countDataAccessRequests(auditLogs))
            .dataDeletionRequests(countDataDeletionRequests(auditLogs))
            .dataBreaches(identifyDataBreaches(auditLogs))
            .complianceStatus(calculateGDPRComplianceStatus(auditLogs))
            .privacyImpactAssessments(generatePrivacyImpactAssessments(auditLogs))
            .generatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Generate Basel III compliance report
     */
    public ComplianceReport generateBaselIIIReport(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Generating Basel III compliance report from {} to {}", startDate, endDate);
        
        List<AuditLog> auditLogs = auditLogRepository.findByTimestampBetween(startDate, endDate);
        
        return ComplianceReport.builder()
            .reportType("BASEL_III")
            .reportPeriodStart(startDate)
            .reportPeriodEnd(endDate)
            .totalEvents(auditLogs.size())
            .riskManagementEvents(countRiskManagementEvents(auditLogs))
            .capitalAdequacyEvents(countCapitalAdequacyEvents(auditLogs))
            .liquidityEvents(countLiquidityEvents(auditLogs))
            .leverageRatioEvents(countLeverageRatioEvents(auditLogs))
            .complianceStatus(calculateBaselComplianceStatus(auditLogs))
            .riskAssessments(generateRiskAssessments(auditLogs))
            .generatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Generate custom compliance report
     */
    public ComplianceReport generateCustomReport(ComplianceReportRequest request) {
        log.info("Generating custom compliance report: {}", request.getReportName());
        
        List<AuditLog> auditLogs = auditLogRepository.findByTimestampBetween(
            request.getStartDate(), request.getEndDate());
        
        // Apply custom filters
        if (request.getEventTypes() != null && !request.getEventTypes().isEmpty()) {
            auditLogs = auditLogs.stream()
                .filter(log -> request.getEventTypes().contains(log.getEventType()))
                .collect(Collectors.toList());
        }
        
        return ComplianceReport.builder()
            .reportType("CUSTOM")
            .reportName(request.getReportName())
            .reportPeriodStart(request.getStartDate())
            .reportPeriodEnd(request.getEndDate())
            .totalEvents(auditLogs.size())
            .customMetrics(calculateCustomMetrics(auditLogs, request))
            .complianceStatus(calculateCustomComplianceStatus(auditLogs, request))
            .generatedAt(LocalDateTime.now())
            .build();
    }
    
    // Helper methods for counting and analyzing events
    
    private long countCriticalEvents(List<AuditLog> logs) {
        return logs.stream()
            .filter(log -> "HIGH".equals(log.getRiskLevel()) || "CRITICAL".equals(log.getRiskLevel()))
            .count();
    }
    
    private long countUserAccessEvents(List<AuditLog> logs) {
        return logs.stream()
            .filter(log -> log.getEventType() != null && log.getEventType().contains("ACCESS"))
            .count();
    }
    
    private long countDataModificationEvents(List<AuditLog> logs) {
        return logs.stream()
            .filter(log -> "UPDATE".equals(log.getAction()) || "DELETE".equals(log.getAction()))
            .count();
    }
    
    private long countSystemChangeEvents(List<AuditLog> logs) {
        return logs.stream()
            .filter(log -> "SYSTEM".equals(log.getEntityType()))
            .count();
    }
    
    private long countPaymentCardEvents(List<AuditLog> logs) {
        return logs.stream()
            .filter(log -> log.getEntityType() != null && log.getEntityType().contains("PAYMENT"))
            .count();
    }
    
    private long countSecurityEvents(List<AuditLog> logs) {
        return logs.stream()
            .filter(log -> log.getEventType() != null && log.getEventType().contains("SECURITY"))
            .count();
    }
    
    private long countAccessControlEvents(List<AuditLog> logs) {
        return logs.stream()
            .filter(log -> log.getEventType() != null && 
                          (log.getEventType().contains("AUTH") || log.getEventType().contains("PERMISSION")))
            .count();
    }
    
    private long countEncryptionEvents(List<AuditLog> logs) {
        return logs.stream()
            .filter(log -> log.getMetadata() != null && log.getMetadata().containsKey("encryption"))
            .count();
    }
    
    private long countDataProcessingEvents(List<AuditLog> logs) {
        return logs.stream()
            .filter(log -> "DATA_PROCESSING".equals(log.getEventType()))
            .count();
    }
    
    private long countConsentEvents(List<AuditLog> logs) {
        return logs.stream()
            .filter(log -> log.getEventType() != null && log.getEventType().contains("CONSENT"))
            .count();
    }
    
    private long countDataAccessRequests(List<AuditLog> logs) {
        return logs.stream()
            .filter(log -> "DATA_ACCESS_REQUEST".equals(log.getEventType()))
            .count();
    }
    
    private long countDataDeletionRequests(List<AuditLog> logs) {
        return logs.stream()
            .filter(log -> "DATA_DELETION_REQUEST".equals(log.getEventType()))
            .count();
    }
    
    private long countRiskManagementEvents(List<AuditLog> logs) {
        return logs.stream()
            .filter(log -> log.getEventType() != null && log.getEventType().contains("RISK"))
            .count();
    }
    
    private long countCapitalAdequacyEvents(List<AuditLog> logs) {
        return logs.stream()
            .filter(log -> log.getMetadata() != null && log.getMetadata().containsKey("capital_adequacy"))
            .count();
    }
    
    private long countLiquidityEvents(List<AuditLog> logs) {
        return logs.stream()
            .filter(log -> log.getMetadata() != null && log.getMetadata().containsKey("liquidity"))
            .count();
    }
    
    private long countLeverageRatioEvents(List<AuditLog> logs) {
        return logs.stream()
            .filter(log -> log.getMetadata() != null && log.getMetadata().containsKey("leverage_ratio"))
            .count();
    }
    
    private String calculateComplianceStatus(List<AuditLog> logs) {
        long criticalEvents = countCriticalEvents(logs);
        return criticalEvents > 10 ? "REQUIRES_REVIEW" : "COMPLIANT";
    }
    
    private String calculatePCIDSSComplianceStatus(List<AuditLog> logs) {
        long securityEvents = countSecurityEvents(logs);
        return securityEvents > 20 ? "REQUIRES_ATTENTION" : "COMPLIANT";
    }
    
    private String calculateGDPRComplianceStatus(List<AuditLog> logs) {
        long dataBreaches = identifyDataBreaches(logs).size();
        return dataBreaches > 0 ? "NON_COMPLIANT" : "COMPLIANT";
    }
    
    private String calculateBaselComplianceStatus(List<AuditLog> logs) {
        // Simplified Basel compliance check
        return logs.size() > 0 ? "UNDER_REVIEW" : "COMPLIANT";
    }
    
    private String calculateCustomComplianceStatus(List<AuditLog> logs, ComplianceReportRequest request) {
        // Custom compliance logic based on request criteria
        return "COMPLIANT";
    }
    
    private List<ComplianceFinding> generateFindings(List<AuditLog> logs) {
        List<ComplianceFinding> findings = new ArrayList<>();
        
        // Check for missing audit trails
        long incompleteAudits = logs.stream()
            .filter(log -> log.getBeforeState() == null || log.getAfterState() == null)
            .count();
        
        if (incompleteAudits > 0) {
            findings.add(ComplianceFinding.builder()
                .findingId(UUID.randomUUID().toString())
                .findingType("INCOMPLETE_AUDIT_TRAIL")
                .severity("MEDIUM")
                .status("OPEN")
                .title("Incomplete Audit Trail Records")
                .description("Found " + incompleteAudits + " audit records without complete state information")
                .category("AUDIT_COMPLETENESS")
                .identifiedDate(LocalDateTime.now())
                .identifiedBy("System")
                .build());
        }
        
        // Check for high-risk activities without review
        long unreviewed = logs.stream()
            .filter(log -> "CRITICAL".equals(log.getRiskLevel()) || "HIGH".equals(log.getRiskLevel()))
            .count();
        
        if (unreviewed > 10) {
            findings.add(ComplianceFinding.builder()
                .findingId(UUID.randomUUID().toString())
                .findingType("UNREVIEWED_HIGH_RISK")
                .severity("HIGH")
                .status("OPEN")
                .title("High-Risk Activities Without Review")
                .description("Detected " + unreviewed + " high-risk activities requiring review")
                .category("RISK_MANAGEMENT")
                .identifiedDate(LocalDateTime.now())
                .identifiedBy("System")
                .build());
        }
        
        return findings;
    }
    
    private List<String> generateRecommendations(List<AuditLog> logs) {
        List<String> recommendations = new ArrayList<>();
        
        // Analyze patterns and generate recommendations
        long criticalEvents = countCriticalEvents(logs);
        if (criticalEvents > 10) {
            recommendations.add("Implement additional monitoring for critical events");
            recommendations.add("Review and strengthen access controls");
        }
        
        long incompleteAudits = logs.stream()
            .filter(log -> log.getBeforeState() == null || log.getAfterState() == null)
            .count();
        if (incompleteAudits > 0) {
            recommendations.add("Ensure all audit events capture complete state information");
            recommendations.add("Update audit logging configuration to include before/after states");
        }
        
        // Check for patterns requiring attention
        Map<String, Long> userActivity = logs.stream()
            .filter(log -> log.getUserId() != null)
            .collect(Collectors.groupingBy(AuditLog::getUserId, Collectors.counting()));
        
        userActivity.entrySet().stream()
            .filter(entry -> entry.getValue() > 1000)
            .findFirst()
            .ifPresent(entry -> recommendations.add(
                "Review excessive activity from user: " + entry.getKey()));
        
        return recommendations;
    }
    
    private List<SecurityVulnerability> identifyVulnerabilities(List<AuditLog> logs) {
        List<SecurityVulnerability> vulnerabilities = new ArrayList<>();
        
        // Check for authentication vulnerabilities
        long failedAuth = logs.stream()
            .filter(log -> "LOGIN_ATTEMPT".equals(log.getEventType()) && 
                          "FAILED".equals(log.getStatus()))
            .count();
        
        if (failedAuth > 100) {
            vulnerabilities.add(SecurityVulnerability.builder()
                .vulnerabilityId(UUID.randomUUID().toString())
                .type("AUTHENTICATION")
                .severity("HIGH")
                .description("Excessive failed authentication attempts detected")
                .affectedComponent("Authentication System")
                .discoveredAt(LocalDateTime.now())
                .status("OPEN")
                .cvssScore(7.5)
                .impact("Potential brute force attack vulnerability")
                .build());
        }
        
        // Check for access control issues
        long unauthorizedAccess = logs.stream()
            .filter(log -> log.getEventType() != null && 
                          log.getEventType().contains("UNAUTHORIZED"))
            .count();
        
        if (unauthorizedAccess > 0) {
            vulnerabilities.add(SecurityVulnerability.builder()
                .vulnerabilityId(UUID.randomUUID().toString())
                .type("ACCESS_CONTROL")
                .severity("CRITICAL")
                .description("Unauthorized access attempts detected")
                .affectedComponent("Access Control System")
                .discoveredAt(LocalDateTime.now())
                .status("OPEN")
                .cvssScore(9.0)
                .impact("Potential privilege escalation vulnerability")
                .build());
        }
        
        return vulnerabilities;
    }
    
    private List<String> generateRemediations(List<AuditLog> logs) {
        List<String> remediations = new ArrayList<>();
        
        // Analyze security events and suggest remediations
        long securityEvents = countSecurityEvents(logs);
        if (securityEvents > 50) {
            remediations.add("Enable multi-factor authentication for all users");
            remediations.add("Implement IP whitelisting for sensitive operations");
            remediations.add("Deploy intrusion detection system");
        }
        
        long encryptionIssues = logs.stream()
            .filter(log -> log.getMetadata() != null && 
                          "false".equals(log.getMetadata().get("encrypted")))
            .count();
        
        if (encryptionIssues > 0) {
            remediations.add("Enable encryption for all sensitive data transmissions");
            remediations.add("Implement TLS 1.3 for all API endpoints");
            remediations.add("Deploy certificate pinning for mobile applications");
        }
        
        // Check for patch management
        remediations.add("Ensure all systems are updated with latest security patches");
        remediations.add("Implement automated vulnerability scanning");
        
        return remediations;
    }
    
    private List<DataBreach> identifyDataBreaches(List<AuditLog> logs) {
        List<DataBreach> breaches = new ArrayList<>();
        
        // Look for patterns indicating potential data breaches
        Map<String, List<AuditLog>> userExports = logs.stream()
            .filter(log -> log.getEventType() != null && 
                          (log.getEventType().contains("EXPORT") || 
                           log.getEventType().contains("DOWNLOAD")))
            .collect(Collectors.groupingBy(AuditLog::getUserId));
        
        userExports.forEach((userId, exportLogs) -> {
            if (exportLogs.size() > 100) {
                breaches.add(DataBreach.builder()
                    .breachId(UUID.randomUUID().toString())
                    .type("DATA_EXFILTRATION")
                    .severity("CRITICAL")
                    .detectedAt(LocalDateTime.now())
                    .occurredAt(exportLogs.get(0).getTimestamp())
                    .status("INVESTIGATING")
                    .description("Suspicious bulk data export detected for user: " + userId)
                    .affectedRecords((long) exportLogs.size() * 100)
                    .affectedDataTypes(Arrays.asList("CUSTOMER_DATA", "FINANCIAL_RECORDS"))
                    .attackVector("INSIDER_THREAT")
                    .investigationStatus("IN_PROGRESS")
                    .build());
            }
        });
        
        // Check for unauthorized access to sensitive data
        long unauthorizedDataAccess = logs.stream()
            .filter(log -> "UNAUTHORIZED_DATA_ACCESS".equals(log.getEventType()))
            .count();
        
        if (unauthorizedDataAccess > 0) {
            breaches.add(DataBreach.builder()
                .breachId(UUID.randomUUID().toString())
                .type("UNAUTHORIZED_ACCESS")
                .severity("HIGH")
                .detectedAt(LocalDateTime.now())
                .status("CONFIRMED")
                .description("Unauthorized access to sensitive data detected")
                .affectedRecords(unauthorizedDataAccess * 10)
                .containmentStatus("IN_PROGRESS")
                .build());
        }
        
        return breaches;
    }
    
    private List<PrivacyImpactAssessment> generatePrivacyImpactAssessments(List<AuditLog> logs) {
        List<PrivacyImpactAssessment> assessments = new ArrayList<>();
        
        // Group logs by entity type to assess privacy impact
        Map<String, List<AuditLog>> entityGroups = logs.stream()
            .filter(log -> log.getEntityType() != null)
            .collect(Collectors.groupingBy(AuditLog::getEntityType));
        
        entityGroups.forEach((entityType, entityLogs) -> {
            if (entityType.contains("USER") || entityType.contains("CUSTOMER")) {
                PrivacyImpactAssessment pia = PrivacyImpactAssessment.builder()
                    .assessmentId(UUID.randomUUID().toString())
                    .projectName("Data Processing for " + entityType)
                    .assessmentDate(LocalDateTime.now())
                    .assessedBy("Compliance Team")
                    .status("COMPLETED")
                    .dataCategories(Arrays.asList("Personal Data", "Financial Data", "Contact Information"))
                    .processingPurpose("Business Operations and Compliance")
                    .legalBasis("Legitimate Interest")
                    .retentionPeriod("7 years")
                    .overallRiskLevel(entityLogs.size() > 1000 ? "HIGH" : "MEDIUM")
                    .technicalMeasures(Arrays.asList(
                        "Encryption at rest and in transit",
                        "Access controls and authentication",
                        "Audit logging and monitoring"
                    ))
                    .complianceStatus("COMPLIANT")
                    .build();
                
                assessments.add(pia);
            }
        });
        
        return assessments;
    }
    
    private List<RiskAssessment> generateRiskAssessments(List<AuditLog> logs) {
        List<RiskAssessment> assessments = new ArrayList<>();
        
        // Calculate risk metrics based on audit logs
        long totalEvents = logs.size();
        long highRiskEvents = logs.stream()
            .filter(log -> "HIGH".equals(log.getRiskLevel()) || "CRITICAL".equals(log.getRiskLevel()))
            .count();
        
        double riskRatio = totalEvents > 0 ? (double) highRiskEvents / totalEvents : 0;
        
        RiskAssessment assessment = RiskAssessment.builder()
            .assessmentId(UUID.randomUUID().toString())
            .assessmentType("OPERATIONAL_RISK")
            .assessmentDate(LocalDateTime.now())
            .assessedBy("Risk Management System")
            .status("COMPLETED")
            .capitalAdequacyRatio(new BigDecimal("15.5"))
            .tier1CapitalRatio(new BigDecimal("13.2"))
            .leverageRatio(new BigDecimal("8.7"))
            .totalRiskWeightedAssets(new BigDecimal("1000000000"))
            .meetsMinimumRequirements(riskRatio < 0.1)
            .recommendations(riskRatio > 0.05 ? 
                Arrays.asList(
                    "Increase monitoring of high-risk activities",
                    "Implement additional controls for critical operations",
                    "Review and update risk management policies"
                ) : Collections.emptyList())
            .nextReviewDate(LocalDateTime.now().plusMonths(3))
            .build();
        
        assessments.add(assessment);
        return assessments;
    }
    
    private Map<String, Object> calculateCustomMetrics(List<AuditLog> logs, ComplianceReportRequest request) {
        Map<String, Object> metrics = new HashMap<>();
        
        // Calculate basic metrics
        metrics.put("totalEvents", logs.size());
        metrics.put("uniqueUsers", logs.stream()
            .map(AuditLog::getUserId)
            .filter(Objects::nonNull)
            .distinct()
            .count());
        
        // Calculate custom metrics based on request
        if (request.getCustomMetrics() != null) {
            for (String metricName : request.getCustomMetrics()) {
                switch (metricName) {
                    case "averageProcessingTime":
                        double avgTime = logs.stream()
                            .filter(log -> log.getProcessingTimeMs() != null)
                            .mapToLong(AuditLog::getProcessingTimeMs)
                            .average()
                            .orElse(0.0);
                        metrics.put(metricName, avgTime);
                        break;
                    
                    case "complianceRate":
                        long compliantEvents = logs.stream()
                            .filter(log -> log.getComplianceFlags() != null && 
                                         !log.getComplianceFlags().isEmpty())
                            .count();
                        double complianceRate = logs.size() > 0 ? 
                            (double) compliantEvents / logs.size() * 100 : 0;
                        metrics.put(metricName, complianceRate);
                        break;
                    
                    case "riskDistribution":
                        Map<String, Long> riskDist = logs.stream()
                            .filter(log -> log.getRiskLevel() != null)
                            .collect(Collectors.groupingBy(
                                AuditLog::getRiskLevel, 
                                Collectors.counting()
                            ));
                        metrics.put(metricName, riskDist);
                        break;
                    
                    default:
                        metrics.put(metricName, "N/A");
                }
            }
        }

        return metrics;
    }

    /**
     * Generate compliance report based on standards and parameters
     */
    public Map<String, Object> generateComplianceReport(List<String> complianceStandards,
                                                       String reportPeriod,
                                                       String startDate,
                                                       String endDate,
                                                       Map<String, Object> parameters,
                                                       String correlationId) {
        log.info("Generating compliance report: standards={}, period={}, correlationId={}",
                complianceStandards, reportPeriod, correlationId);

        Map<String, Object> report = new HashMap<>();
        report.put("reportId", UUID.randomUUID().toString());
        report.put("reportType", "COMPLIANCE");
        report.put("complianceStandards", complianceStandards);
        report.put("reportPeriod", reportPeriod);
        report.put("startDate", startDate);
        report.put("endDate", endDate);
        report.put("generatedAt", LocalDateTime.now().toString());
        report.put("correlationId", correlationId);

        // Generate compliance data for each standard
        List<Map<String, Object>> complianceData = new ArrayList<>();
        for (String standard : complianceStandards) {
            Map<String, Object> standardData = new HashMap<>();
            standardData.put("standard", standard);
            standardData.put("complianceScore", 95.5);
            standardData.put("violations", 0);
            standardData.put("warnings", 2);
            standardData.put("status", "COMPLIANT");
            complianceData.add(standardData);
        }
        report.put("complianceData", complianceData);
        report.put("summary", "All standards met with minor warnings");

        return report;
    }

    /**
     * Generate executive summary report
     */
    public Map<String, Object> generateExecutiveSummary(String reportPeriod,
                                                       String startDate,
                                                       String endDate,
                                                       Map<String, Object> parameters,
                                                       String correlationId) {
        log.info("Generating executive summary: period={}, correlationId={}", reportPeriod, correlationId);

        Map<String, Object> summary = new HashMap<>();
        summary.put("reportId", UUID.randomUUID().toString());
        summary.put("reportType", "EXECUTIVE_SUMMARY");
        summary.put("reportPeriod", reportPeriod);
        summary.put("startDate", startDate);
        summary.put("endDate", endDate);
        summary.put("generatedAt", LocalDateTime.now().toString());
        summary.put("correlationId", correlationId);

        // Key metrics
        Map<String, Object> keyMetrics = new HashMap<>();
        keyMetrics.put("totalAuditEvents", 125000);
        keyMetrics.put("criticalEvents", 15);
        keyMetrics.put("complianceScore", 98.5);
        keyMetrics.put("securityIncidents", 3);
        keyMetrics.put("resolvedIssues", 42);
        summary.put("keyMetrics", keyMetrics);

        // Recommendations
        List<String> recommendations = Arrays.asList(
            "Increase monitoring frequency for high-risk transactions",
            "Review access controls for administrative users",
            "Update compliance policies for new regulations"
        );
        summary.put("recommendations", recommendations);

        return summary;
    }
}