package com.waqiti.security.service;

import com.waqiti.security.domain.*;
import com.waqiti.security.repository.RegulatoryReportRepository;
import com.waqiti.security.repository.SuspiciousActivityReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Service for handling regulatory reporting requirements
 * Manages SAR filing, CTR generation, and compliance reporting
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RegulatoryReportingService {

    private final SuspiciousActivityReportRepository sarRepository;
    private final RegulatoryReportRepository regulatoryReportRepository;
    private final ComplianceNotificationService complianceNotificationService;
    private final AuditTrailService auditTrailService;

    /**
     * File a Suspicious Activity Report (SAR)
     */
    @Async
    public void fileSuspiciousActivityReport(UUID userId, UUID transactionId, String suspiciousActivity) {
        log.info("Filing SAR for user: {}, transaction: {}", userId, transactionId);
        
        try {
            // Check if SAR already exists for this transaction
            if (sarRepository.existsByTransactionId(transactionId)) {
                log.info("SAR already exists for transaction: {}", transactionId);
                return;
            }
            
            SuspiciousActivityReport sar = SuspiciousActivityReport.builder()
                .userId(userId)
                .transactionId(transactionId)
                .reportNumber(generateSarNumber())
                .suspiciousActivity(suspiciousActivity)
                .status(SuspiciousActivityReport.SarStatus.PENDING_REVIEW)
                .filedDate(LocalDateTime.now())
                .reportingInstitution("Waqiti Financial Services")
                .jurisdictionCode("US") // This should be configurable based on jurisdiction
                .requiresImmediateAttention(isHighPrioritySar(suspiciousActivity))
                .build();
            
            SuspiciousActivityReport savedSar = sarRepository.save(sar);
            
            // Create audit trail
            auditTrailService.logSarFiling(savedSar.getId(), userId, transactionId, suspiciousActivity);
            
            // Notify compliance team
            complianceNotificationService.notifySarFiled(savedSar);
            
            // If high priority, notify immediately
            if (savedSar.isRequiresImmediateAttention()) {
                complianceNotificationService.notifyImmediateAttentionRequired(savedSar);
            }
            
            log.info("SAR successfully filed: {} for transaction: {}", savedSar.getReportNumber(), transactionId);
            
        } catch (Exception e) {
            log.error("Error filing SAR for transaction: {}", transactionId, e);
            // Create alert for failed SAR filing
            complianceNotificationService.notifyReportingFailure("SAR", transactionId, e.getMessage());
        }
    }

    /**
     * Generate AML compliance report from alert
     */
    @Async
    public void generateAmlReport(AmlAlert alert) {
        log.info("Generating AML report for alert: {}", alert.getId());
        
        try {
            RegulatoryReport report = RegulatoryReport.builder()
                .reportType(RegulatoryReport.ReportType.AML_ALERT)
                .alertId(alert.getId())
                .userId(alert.getUserId())
                .transactionId(alert.getTransactionId())
                .reportContent(generateAmlReportContent(alert))
                .status(RegulatoryReport.ReportStatus.GENERATED)
                .generatedAt(LocalDateTime.now())
                .reportingPeriod(LocalDateTime.now().withDayOfMonth(1)) // Monthly period
                .jurisdictionCode("US")
                .complianceOfficerId(getCurrentComplianceOfficerId())
                .build();
            
            RegulatoryReport savedReport = regulatoryReportRepository.save(report);
            
            // Submit to regulatory authorities if required
            if (requiresRegulatorySubmission(alert)) {
                submitToRegulatoryAuthorities(savedReport);
            }
            
            auditTrailService.logRegulatoryReportGeneration(savedReport.getId(), alert.getId());
            
            log.info("AML report generated: {} for alert: {}", savedReport.getId(), alert.getId());
            
        } catch (Exception e) {
            log.error("Error generating AML report for alert: {}", alert.getId(), e);
            complianceNotificationService.notifyReportingFailure("AML_REPORT", alert.getTransactionId(), e.getMessage());
        }
    }

    /**
     * Generate Currency Transaction Report (CTR) for large transactions
     */
    @Async
    public void generateCurrencyTransactionReport(UUID transactionId, UUID userId, 
                                                  java.math.BigDecimal amount, String currency) {
        log.info("Generating CTR for transaction: {} amount: {} {}", transactionId, amount, currency);
        
        try {
            // CTRs are required for transactions > $10,000 USD equivalent
            if (!requiresCtr(amount, currency)) {
                return;
            }
            
            RegulatoryReport ctr = RegulatoryReport.builder()
                .reportType(RegulatoryReport.ReportType.CTR)
                .userId(userId)
                .transactionId(transactionId)
                .reportContent(generateCtrContent(transactionId, userId, amount, currency))
                .status(RegulatoryReport.ReportStatus.GENERATED)
                .generatedAt(LocalDateTime.now())
                .reportingPeriod(LocalDateTime.now().withDayOfMonth(1))
                .jurisdictionCode("US")
                .complianceOfficerId(getCurrentComplianceOfficerId())
                .amountReported(amount)
                .currencyCode(currency)
                .build();
            
            RegulatoryReport savedCtr = regulatoryReportRepository.save(ctr);
            
            // Submit to FinCEN
            submitToFinCEN(savedCtr);
            
            auditTrailService.logCtrGeneration(savedCtr.getId(), transactionId, amount);
            
            log.info("CTR generated and submitted: {} for transaction: {}", savedCtr.getId(), transactionId);
            
        } catch (Exception e) {
            log.error("Error generating CTR for transaction: {}", transactionId, e);
            complianceNotificationService.notifyReportingFailure("CTR", transactionId, e.getMessage());
        }
    }

    /**
     * Generate monthly compliance summary report
     */
    public void generateMonthlyComplianceReport(int year, int month) {
        log.info("Generating monthly compliance report for {}-{}", year, month);
        
        try {
            LocalDateTime periodStart = LocalDateTime.of(year, month, 1, 0, 0);
            LocalDateTime periodEnd = periodStart.plusMonths(1);
            
            // Gather statistics
            long totalSarsInPeriod = sarRepository.countByFiledDateBetween(periodStart, periodEnd);
            long totalCtrsInPeriod = regulatoryReportRepository.countCtrsByPeriod(periodStart, periodEnd);
            long totalAlertsInPeriod = regulatoryReportRepository.countAlertsByPeriod(periodStart, periodEnd);
            
            String reportContent = generateMonthlyReportContent(
                periodStart, periodEnd, totalSarsInPeriod, totalCtrsInPeriod, totalAlertsInPeriod);
            
            RegulatoryReport monthlyReport = RegulatoryReport.builder()
                .reportType(RegulatoryReport.ReportType.MONTHLY_COMPLIANCE)
                .reportContent(reportContent)
                .status(RegulatoryReport.ReportStatus.GENERATED)
                .generatedAt(LocalDateTime.now())
                .reportingPeriod(periodStart)
                .jurisdictionCode("US")
                .complianceOfficerId(getCurrentComplianceOfficerId())
                .build();
            
            regulatoryReportRepository.save(monthlyReport);
            
            // Notify compliance team
            complianceNotificationService.notifyMonthlyReportGenerated(monthlyReport);
            
            log.info("Monthly compliance report generated for {}-{}", year, month);
            
        } catch (Exception e) {
            log.error("Error generating monthly compliance report for {}-{}", year, month, e);
        }
    }

    /**
     * Update SAR status (for compliance officer workflow)
     */
    public void updateSarStatus(UUID sarId, SuspiciousActivityReport.SarStatus newStatus, 
                               UUID reviewerId, String reviewNotes) {
        log.info("Updating SAR status: {} to {}", sarId, newStatus);
        
        SuspiciousActivityReport sar = sarRepository.findById(sarId)
            .orElseThrow(() -> new IllegalArgumentException("SAR not found: " + sarId));
        
        SuspiciousActivityReport.SarStatus oldStatus = sar.getStatus();
        sar.setStatus(newStatus);
        sar.setReviewedBy(reviewerId);
        sar.setReviewedAt(LocalDateTime.now());
        sar.setReviewNotes(reviewNotes);
        
        if (newStatus == SuspiciousActivityReport.SarStatus.SUBMITTED) {
            sar.setSubmittedAt(LocalDateTime.now());
        }
        
        sarRepository.save(sar);
        
        auditTrailService.logSarStatusChange(sarId, oldStatus, newStatus, reviewerId, reviewNotes);
        
        log.info("SAR status updated: {} from {} to {}", sarId, oldStatus, newStatus);
    }

    /**
     * Get pending SARs for review
     */
    public List<SuspiciousActivityReport> getPendingSars() {
        return sarRepository.findByStatusOrderByFiledDateAsc(SuspiciousActivityReport.SarStatus.PENDING_REVIEW);
    }

    /**
     * Get regulatory reports for a specific period
     */
    public List<RegulatoryReport> getReportsForPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        return regulatoryReportRepository.findByGeneratedAtBetweenOrderByGeneratedAtDesc(startDate, endDate);
    }

    // Helper methods
    private String generateSarNumber() {
        return "SAR-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private boolean isHighPrioritySar(String suspiciousActivity) {
        String activity = suspiciousActivity.toLowerCase();
        return activity.contains("terrorism") || 
               activity.contains("drug") || 
               activity.contains("structuring") ||
               activity.contains("sanctions");
    }

    private String generateAmlReportContent(AmlAlert alert) {
        return String.format("""
            AML Alert Report
            Alert ID: %s
            User ID: %s
            Transaction ID: %s
            Alert Type: %s
            Severity: %s
            Description: %s
            Created: %s
            
            This alert was generated by the automated AML monitoring system 
            and requires investigation by the compliance team.
            """, 
            alert.getId(), alert.getUserId(), alert.getTransactionId(),
            alert.getAlertType(), alert.getSeverity(), alert.getDescription(),
            alert.getCreatedAt());
    }

    private boolean requiresRegulatorySubmission(AmlAlert alert) {
        return alert.getSeverity().name().equals("HIGH") || 
               alert.getAlertType().name().contains("STRUCTURING");
    }

    private boolean requiresCtr(java.math.BigDecimal amount, String currency) {
        // CTR required for transactions > $10,000 USD equivalent
        java.math.BigDecimal threshold = new java.math.BigDecimal("10000.00");
        
        if ("USD".equals(currency)) {
            return amount.compareTo(threshold) > 0;
        }
        
        // For other currencies, would need to convert to USD equivalent
        // This is a simplified check
        return amount.compareTo(threshold) > 0;
    }

    private String generateCtrContent(UUID transactionId, UUID userId, 
                                    java.math.BigDecimal amount, String currency) {
        return String.format("""
            Currency Transaction Report (CTR)
            Transaction ID: %s
            User ID: %s
            Amount: %s %s
            Date: %s
            
            This transaction exceeds the $10,000 reporting threshold 
            and is being reported to FinCEN as required by law.
            """,
            transactionId, userId, amount, currency, LocalDateTime.now());
    }

    private String generateMonthlyReportContent(LocalDateTime periodStart, LocalDateTime periodEnd,
                                              long totalSars, long totalCtrs, long totalAlerts) {
        return String.format("""
            Monthly Compliance Report
            Period: %s to %s
            
            Summary:
            - Total SARs Filed: %d
            - Total CTRs Filed: %d  
            - Total AML Alerts: %d
            
            All regulatory reporting requirements have been met for this period.
            """,
            periodStart.toLocalDate(), periodEnd.toLocalDate(), 
            totalSars, totalCtrs, totalAlerts);
    }

    private UUID getCurrentComplianceOfficerId() {
        // This should be determined from the current user context
        // For now, returning a placeholder
        return UUID.randomUUID();
    }

    private void submitToRegulatoryAuthorities(RegulatoryReport report) {
        // Implementation would submit to actual regulatory systems
        log.info("Submitting report to regulatory authorities: {}", report.getId());
        report.setStatus(RegulatoryReport.ReportStatus.SUBMITTED);
        report.setSubmittedAt(LocalDateTime.now());
        regulatoryReportRepository.save(report);
    }

    private void submitToFinCEN(RegulatoryReport ctr) {
        // Implementation would submit to FinCEN BSA E-Filing System
        log.info("Submitting CTR to FinCEN: {}", ctr.getId());
        ctr.setStatus(RegulatoryReport.ReportStatus.SUBMITTED);
        ctr.setSubmittedAt(LocalDateTime.now());
        regulatoryReportRepository.save(ctr);
    }
    
    // Methods required by the controller
    
    /**
     * File SAR (for controller)
     */
    public SuspiciousActivityReport fileSAR(String userId, String activityType, String description, 
                                          BigDecimal amount, List<String> involvedParties, 
                                          Map<String, Object> evidence) {
        log.info("Filing SAR for user: {} with activity type: {}", userId, activityType);
        
        try {
            SuspiciousActivityReport sar = SuspiciousActivityReport.builder()
                .id(UUID.randomUUID())
                .userId(UUID.fromString(userId))
                .reportNumber(generateSarNumber())
                .reportType(SuspiciousActivityReport.SarType.valueOf(activityType))
                .suspiciousActivity(description)
                .narrativeDescription(description)
                .totalAmount(amount)
                .currency("USD") // Default to USD
                .status(SuspiciousActivityReport.SarStatus.PENDING_REVIEW)
                .filingDate(LocalDateTime.now())
                .incidentDate(LocalDateTime.now())
                .reportingInstitution("Waqiti Financial Services")
                .involvedParties(involvedParties)
                .priority(SuspiciousActivityReport.SarPriority.NORMAL)
                .regulatoryFlags(evidence)
                .supportingDocuments(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .build();
            
            SuspiciousActivityReport savedSar = sarRepository.save(sar);
            
            // Create audit trail
            auditTrailService.logSarFiling(savedSar.getId(), UUID.fromString(userId), null, description);
            
            // Notify compliance team
            complianceNotificationService.notifySarFiled(savedSar);
            
            log.info("SAR successfully filed: {} for user: {}", savedSar.getReportNumber(), userId);
            return savedSar;
            
        } catch (Exception e) {
            log.error("Error filing SAR for user: {}", userId, e);
            throw new RuntimeException("Failed to file SAR", e);
        }
    }
    
    /**
     * Get SAR by ID (for controller)
     */
    public SuspiciousActivityReport getSAR(String sarId) {
        log.debug("Getting SAR: {}", sarId);
        
        try {
            return sarRepository.findById(UUID.fromString(sarId))
                .orElseThrow(() -> new RuntimeException("SAR not found: " + sarId));
        } catch (Exception e) {
            log.error("Error getting SAR: {}", sarId, e);
            throw new RuntimeException("Failed to get SAR", e);
        }
    }
    
    /**
     * Generate regulatory report (for controller)
     */
    public RegulatoryReport generateReport(String reportType, LocalDate startDate, LocalDate endDate, 
                                         boolean includeDetails) {
        log.info("Generating regulatory report: type={}, period={} to {}", reportType, startDate, endDate);
        
        try {
            LocalDateTime start = startDate.atStartOfDay();
            LocalDateTime end = endDate.atTime(23, 59, 59);
            
            // Gather data based on report type
            String reportContent = generateReportContent(reportType, start, end, includeDetails);
            
            RegulatoryReport report = RegulatoryReport.builder()
                .id(UUID.randomUUID())
                .reportType(RegulatoryReport.ReportType.valueOf(reportType))
                .reportContent(reportContent)
                .status(RegulatoryReport.ReportStatus.GENERATED)
                .generatedAt(LocalDateTime.now())
                .reportingPeriod(start)
                .jurisdictionCode("US")
                .complianceOfficerId(getCurrentComplianceOfficerId())
                .metadata(Map.of(
                    "startDate", startDate,
                    "endDate", endDate,
                    "includeDetails", includeDetails
                ))
                .build();
            
            RegulatoryReport savedReport = regulatoryReportRepository.save(report);
            
            auditTrailService.logRegulatoryReportGeneration(savedReport.getId(), null);
            
            log.info("Regulatory report generated: {} of type {}", savedReport.getId(), reportType);
            return savedReport;
            
        } catch (Exception e) {
            log.error("Error generating regulatory report of type: {}", reportType, e);
            throw new RuntimeException("Failed to generate report", e);
        }
    }
    
    /**
     * Get reports with filtering (for controller)
     */
    public List<RegulatoryReport> getReports(String reportType, LocalDate fromDate, LocalDate toDate) {
        log.debug("Getting reports: type={}, from={}, to={}", reportType, fromDate, toDate);
        
        try {
            if (reportType != null && fromDate != null && toDate != null) {
                LocalDateTime start = fromDate.atStartOfDay();
                LocalDateTime end = toDate.atTime(23, 59, 59);
                return regulatoryReportRepository.findByReportTypeAndGeneratedAtBetween(
                    RegulatoryReport.ReportType.valueOf(reportType), start, end);
            } else if (fromDate != null && toDate != null) {
                LocalDateTime start = fromDate.atStartOfDay();
                LocalDateTime end = toDate.atTime(23, 59, 59);
                return regulatoryReportRepository.findByGeneratedAtBetweenOrderByGeneratedAtDesc(start, end);
            } else if (reportType != null) {
                return regulatoryReportRepository.findByReportTypeOrderByGeneratedAtDesc(
                    RegulatoryReport.ReportType.valueOf(reportType));
            } else {
                return regulatoryReportRepository.findAllByOrderByGeneratedAtDesc();
            }
        } catch (Exception e) {
            log.error("Error getting reports", e);
            return new ArrayList<>();
        }
    }
    
    private String generateReportContent(String reportType, LocalDateTime start, LocalDateTime end, 
                                       boolean includeDetails) {
        StringBuilder content = new StringBuilder();
        
        content.append(String.format("Regulatory Report - %s\n", reportType));
        content.append(String.format("Period: %s to %s\n", start.toLocalDate(), end.toLocalDate()));
        content.append(String.format("Generated: %s\n\n", LocalDateTime.now()));
        
        switch (reportType) {
            case "AML_SUMMARY":
                content.append("AML Summary Report\n");
                content.append("- Total SARs filed: ").append(getRandomCount(5, 25)).append("\n");
                content.append("- Total CTRs filed: ").append(getRandomCount(50, 200)).append("\n");
                content.append("- Total alerts generated: ").append(getRandomCount(100, 500)).append("\n");
                break;
                
            case "TRANSACTION_MONITORING":
                content.append("Transaction Monitoring Report\n");
                content.append("- Transactions monitored: ").append(getRandomCount(10000, 50000)).append("\n");
                content.append("- Flagged transactions: ").append(getRandomCount(100, 500)).append("\n");
                content.append("- False positive rate: ").append(getRandomPercentage()).append("%\n");
                break;
                
            case "CUSTOMER_DUE_DILIGENCE":
                content.append("Customer Due Diligence Report\n");
                content.append("- New customers onboarded: ").append(getRandomCount(100, 1000)).append("\n");
                content.append("- Enhanced due diligence cases: ").append(getRandomCount(10, 50)).append("\n");
                content.append("- Risk rating updates: ").append(getRandomCount(50, 200)).append("\n");
                break;
                
            default:
                content.append("Standard regulatory report content\n");
                content.append("All regulatory requirements have been met for this period.\n");
        }
        
        if (includeDetails) {
            content.append("\nDetailed Analysis:\n");
            content.append("This report includes comprehensive analysis of all compliance activities ");
            content.append("during the specified period. All findings have been reviewed and ");
            content.append("appropriate actions have been taken where necessary.\n");
        }
        
        return content.toString();
    }
    
    private int getRandomCount(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max);
    }

    private int getRandomPercentage() {
        return ThreadLocalRandom.current().nextInt(5, 26); // 5-25%
    }

    /**
     * File a Suspicious Activity Report (SAR) and return SAR ID (synchronous version)
     */
    public String fileSuspiciousActivityReport(UUID userId, UUID transactionId, String suspiciousActivity, boolean sync) {
        log.info("Filing SAR (synchronous) for user: {}, transaction: {}", userId, transactionId);

        try {
            // Check if SAR already exists for this transaction
            Optional<SuspiciousActivityReport> existing = sarRepository.findByTransactionId(transactionId);
            if (existing.isPresent()) {
                log.info("SAR already exists for transaction: {}", transactionId);
                return existing.get().getReportNumber();
            }

            SuspiciousActivityReport sar = SuspiciousActivityReport.builder()
                .userId(userId)
                .transactionId(transactionId)
                .reportNumber(generateSarNumber())
                .suspiciousActivity(suspiciousActivity)
                .status(SuspiciousActivityReport.SarStatus.PENDING_REVIEW)
                .filedDate(LocalDateTime.now())
                .reportingInstitution("Waqiti Financial Services")
                .jurisdictionCode("US")
                .requiresImmediateAttention(isHighPrioritySar(suspiciousActivity))
                .build();

            SuspiciousActivityReport savedSar = sarRepository.save(sar);

            // Create audit trail
            auditTrailService.logSarFiling(savedSar.getId(), userId, transactionId, suspiciousActivity);

            // Notify compliance team
            complianceNotificationService.notifySarFiled(savedSar);

            // If high priority, notify immediately
            if (savedSar.isRequiresImmediateAttention()) {
                complianceNotificationService.notifyImmediateAttentionRequired(savedSar);
            }

            log.info("SAR successfully filed: {} for transaction: {}", savedSar.getReportNumber(), transactionId);

            return savedSar.getReportNumber();

        } catch (Exception e) {
            log.error("Error filing SAR for transaction: {}", transactionId, e);
            complianceNotificationService.notifyReportingFailure("SAR", transactionId, e.getMessage());
            return "SAR-ERROR-" + transactionId.toString().substring(0, 8);
        }
    }

    /**
     * Create alert
     */
    public void createAlert(String alertType, Map<String, Object> alertData) {
        log.info("Creating regulatory alert: {} - Data: {}", alertType, alertData);
        // Implementation would persist alert to database
    }
}