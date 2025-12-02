package com.waqiti.compliance.scheduler;

import com.waqiti.compliance.model.SarFilingStatus;
import com.waqiti.compliance.repository.SarFilingStatusRepository;
import com.waqiti.compliance.service.SarFilingService;
import com.waqiti.compliance.service.ComplianceNotificationService;
import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.common.events.SarFilingRequestEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * SAR Filing Scheduler - Automated SAR lifecycle management
 * 
 * CRITICAL REGULATORY COMPLIANCE: Ensures timely SAR filing per BSA requirements
 * 
 * This scheduler handles:
 * - Processing pending SAR filings on a regular schedule
 * - Detecting and escalating overdue SARs
 * - Automatic filing of time-sensitive SARs
 * - Follow-up scheduling for filed SARs
 * - Compliance deadline monitoring
 * - Executive escalations for critical delays
 * 
 * REGULATORY IMPACT: Failure to file SARs within required timeframes
 * (30 days from detection, 60 days for continuing activity) can result in:
 * - Regulatory fines up to $2M per violation
 * - Criminal prosecution
 * - Loss of banking license
 * - Personal liability for compliance officers
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SARFilingScheduler {

    private final SarFilingStatusRepository sarFilingStatusRepository;
    private final SarFilingService sarFilingService;
    private final ComplianceNotificationService complianceNotificationService;
    private final ComprehensiveAuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${compliance.sar.filing.warning.hours:24}")
    private int filingWarningHours;

    @Value("${compliance.sar.filing.critical.hours:6}")
    private int filingCriticalHours;

    @Value("${compliance.sar.filing.emergency.hours:2}")
    private int filingEmergencyHours;

    @Value("${compliance.sar.filing.max.retry:3}")
    private int maxRetryAttempts;

    @Value("${compliance.sar.filing.batch.size:50}")
    private int batchSize;

    @Value("${compliance.sar.filing.enabled:true}")
    private boolean filingEnabled;

    // Metrics
    private Counter sarsProcessed;
    private Counter sarsFileSuccessfully;
    private Counter sarsFiledFailed;
    private Counter overdueAlertsGenerated;
    private Counter emergencyEscalations;
    private Counter automaticFilings;
    private Timer filingProcessingTime;

    /**
     * Process pending SAR filings every 15 minutes
     * 
     * This method runs every 15 minutes to ensure timely processing
     * of all pending SAR filings and prevent regulatory violations
     */
    @Scheduled(fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void processPendingSARFilings() {
        if (!filingEnabled) {
            log.debug("SAR filing scheduler is disabled");
            return;
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("REGULATORY: Starting scheduled SAR filing processing");

        try {
            // Get pending SARs that need to be filed
            List<SarFilingStatus> pendingSars = sarFilingStatusRepository
                .findByStatusInAndDeadlineBefore(
                    List.of(
                        SarFilingStatus.FilingStatus.PENDING_REVIEW,
                        SarFilingStatus.FilingStatus.APPROVED,
                        SarFilingStatus.FilingStatus.SCHEDULED
                    ),
                    LocalDateTime.now().plusHours(filingWarningHours)
                );

            if (pendingSars.isEmpty()) {
                log.debug("No pending SAR filings to process");
                return;
            }

            log.warn("REGULATORY: Found {} pending SAR filings requiring processing", pendingSars.size());
            incrementCounter("sars.pending.count", pendingSars.size());

            // Process in batches to avoid overwhelming the system
            for (int i = 0; i < pendingSars.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, pendingSars.size());
                List<SarFilingStatus> batch = pendingSars.subList(i, endIndex);
                
                processSARBatch(batch);
                
                // Brief pause between batches
                if (endIndex < pendingSars.size()) {
                    Thread.sleep(1000); // 1 second pause between batches
                }
            }

            log.info("REGULATORY: Completed processing {} pending SAR filings", pendingSars.size());

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process pending SAR filings", e);
            
            // Send emergency alert for scheduler failure
            sendSchedulerFailureAlert(e);
            
        } finally {
            sample.stop(getTimer("sar.filing.processing.time"));
        }
    }

    /**
     * Check for overdue SARs every hour
     * 
     * This critical method runs hourly to detect any SARs that are
     * approaching or have missed their regulatory filing deadline
     */
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void checkForOverdueSARs() {
        if (!filingEnabled) {
            return;
        }

        log.info("REGULATORY: Checking for overdue SAR filings");

        try {
            LocalDateTime now = LocalDateTime.now();
            
            // Find SARs that are overdue or approaching deadline
            List<SarFilingStatus> overdueSars = sarFilingStatusRepository
                .findByStatusNotInAndDeadlineBefore(
                    List.of(
                        SarFilingStatus.FilingStatus.FILED,
                        SarFilingStatus.FilingStatus.CANCELLED,
                        SarFilingStatus.FilingStatus.REJECTED
                    ),
                    now
                );

            if (!overdueSars.isEmpty()) {
                log.error("CRITICAL REGULATORY VIOLATION: Found {} OVERDUE SAR filings", overdueSars.size());
                incrementCounter("sars.overdue.count", overdueSars.size());
                
                // Process each overdue SAR
                for (SarFilingStatus sar : overdueSars) {
                    handleOverdueSAR(sar);
                }
            }

            // Check for SARs approaching deadline
            checkApproachingDeadlines();

        } catch (Exception e) {
            log.error("CRITICAL: Failed to check for overdue SARs", e);
            sendSchedulerFailureAlert(e);
        }
    }

    /**
     * Process scheduled automatic filings every 5 minutes
     * 
     * This method handles SARs that have been scheduled for automatic filing
     */
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void processScheduledFilings() {
        if (!filingEnabled) {
            return;
        }

        log.debug("Processing scheduled SAR filings");

        try {
            // Get SARs scheduled for filing now
            List<SarFilingStatus> scheduledSars = sarFilingStatusRepository
                .findByStatusAndScheduledFilingDateBefore(
                    SarFilingStatus.FilingStatus.SCHEDULED,
                    LocalDateTime.now()
                );

            if (!scheduledSars.isEmpty()) {
                log.info("REGULATORY: Processing {} scheduled SAR filings", scheduledSars.size());
                
                for (SarFilingStatus sar : scheduledSars) {
                    processScheduledFiling(sar);
                }
            }

        } catch (Exception e) {
            log.error("Failed to process scheduled filings", e);
        }
    }

    /**
     * Daily compliance report generation at 6 AM
     * 
     * Generates daily SAR filing status report for compliance team
     */
    @Scheduled(cron = "0 0 6 * * *") // 6:00 AM daily
    public void generateDailyComplianceReport() {
        if (!filingEnabled) {
            return;
        }

        log.info("REGULATORY: Generating daily SAR filing compliance report");

        try {
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            LocalDateTime today = LocalDateTime.now();

            // Get filing statistics
            Map<String, Object> stats = generateFilingStatistics(yesterday, today);

            // Get critical/overdue SARs
            List<SarFilingStatus> criticalSars = sarFilingStatusRepository
                .findByPriorityAndStatusNotIn(
                    SarFilingStatus.FilingPriority.CRITICAL,
                    List.of(SarFilingStatus.FilingStatus.FILED)
                );

            // Generate report
            Map<String, Object> report = Map.of(
                "reportDate", today,
                "statistics", stats,
                "criticalPendingCount", criticalSars.size(),
                "criticalSars", criticalSars,
                "reportType", "DAILY_SAR_COMPLIANCE"
            );

            // Send report to compliance team
            sendComplianceReport(report);

            // Audit report generation
            auditService.auditCriticalComplianceEvent(
                "DAILY_SAR_REPORT_GENERATED",
                "SYSTEM",
                "Daily SAR compliance report generated",
                stats
            );

        } catch (Exception e) {
            log.error("Failed to generate daily compliance report", e);
        }
    }

    /**
     * Weekly executive summary every Monday at 8 AM
     */
    @Scheduled(cron = "0 0 8 * * MON") // Monday 8:00 AM
    public void generateWeeklyExecutiveSummary() {
        if (!filingEnabled) {
            return;
        }

        log.info("REGULATORY: Generating weekly SAR executive summary");

        try {
            LocalDateTime weekStart = LocalDateTime.now().minusWeeks(1);
            LocalDateTime weekEnd = LocalDateTime.now();

            // Generate comprehensive weekly statistics
            Map<String, Object> weeklyStats = generateWeeklyStatistics(weekStart, weekEnd);

            // Get high-risk patterns
            List<Map<String, Object>> riskPatterns = identifyHighRiskPatterns(weekStart, weekEnd);

            // Create executive summary
            Map<String, Object> summary = Map.of(
                "summaryType", "WEEKLY_EXECUTIVE_SAR_SUMMARY",
                "weekStart", weekStart,
                "weekEnd", weekEnd,
                "statistics", weeklyStats,
                "riskPatterns", riskPatterns,
                "complianceStatus", calculateComplianceStatus(weeklyStats)
            );

            // Send to executives
            sendExecutiveSummary(summary);

            log.info("Weekly executive SAR summary sent successfully");

        } catch (Exception e) {
            log.error("Failed to generate weekly executive summary", e);
        }
    }

    // Helper methods

    private void processSARBatch(List<SarFilingStatus> batch) {
        for (SarFilingStatus sar : batch) {
            try {
                processSingleSAR(sar);
                incrementCounter("sars.processed.success", 1);
            } catch (Exception e) {
                log.error("Failed to process SAR: {}", sar.getSarId(), e);
                incrementCounter("sars.processed.failed", 1);
                handleProcessingFailure(sar, e);
            }
        }
    }

    private void processSingleSAR(SarFilingStatus sar) {
        log.info("Processing SAR: {} - Status: {}, Deadline: {}", 
            sar.getSarId(), sar.getStatus(), sar.getDeadline());

        LocalDateTime now = LocalDateTime.now();
        long hoursUntilDeadline = ChronoUnit.HOURS.between(now, sar.getDeadline());

        // Determine urgency level
        if (hoursUntilDeadline <= filingEmergencyHours) {
            handleEmergencySAR(sar);
        } else if (hoursUntilDeadline <= filingCriticalHours) {
            handleCriticalSAR(sar);
        } else if (hoursUntilDeadline <= filingWarningHours) {
            handleWarningSAR(sar);
        } else {
            // Standard processing
            if (sar.getStatus() == SarFilingStatus.FilingStatus.APPROVED) {
                initiateSARFiling(sar);
            }
        }
    }

    private void handleEmergencySAR(SarFilingStatus sar) {
        log.error("EMERGENCY: SAR {} requires immediate filing - {} hours until deadline", 
            sar.getSarId(), ChronoUnit.HOURS.between(LocalDateTime.now(), sar.getDeadline()));

        incrementCounter("sars.emergency.count", 1);

        // Immediate automatic filing
        initiateEmergencyFiling(sar);

        // Send emergency notifications
        complianceNotificationService.sendEmergencyExecutiveAlert(
            sar.getSarId(),
            "EMERGENCY_SAR_DEADLINE",
            "SAR requires immediate filing to avoid regulatory violation",
            sar.getUserId()
        );

        // Audit emergency action
        auditService.auditCriticalComplianceEvent(
            "EMERGENCY_SAR_FILING",
            sar.getUserId(),
            "Emergency SAR filing initiated due to approaching deadline",
            Map.of(
                "sarId", sar.getSarId(),
                "deadline", sar.getDeadline(),
                "hoursRemaining", ChronoUnit.HOURS.between(LocalDateTime.now(), sar.getDeadline())
            )
        );
    }

    private void handleCriticalSAR(SarFilingStatus sar) {
        log.warn("CRITICAL: SAR {} approaching deadline - {} hours remaining", 
            sar.getSarId(), ChronoUnit.HOURS.between(LocalDateTime.now(), sar.getDeadline()));

        incrementCounter("sars.critical.count", 1);

        // Escalate to compliance team
        complianceNotificationService.sendUrgentNotification(
            sar.getSarId(),
            "CRITICAL_SAR_DEADLINE",
            "SAR filing deadline approaching - immediate action required",
            "CRITICAL"
        );

        // Update status to critical
        sar.setPriority(SarFilingStatus.FilingPriority.CRITICAL);
        sar.setLastUpdated(LocalDateTime.now());
        sarFilingStatusRepository.save(sar);
    }

    private void handleWarningSAR(SarFilingStatus sar) {
        log.warn("WARNING: SAR {} deadline approaching - {} hours remaining", 
            sar.getSarId(), ChronoUnit.HOURS.between(LocalDateTime.now(), sar.getDeadline()));

        // Send warning notification
        complianceNotificationService.notifyComplianceTeam(
            sar.getSarId(),
            sar.getUserId(),
            "HIGH",
            sar.getDeadline()
        );
    }

    private void handleOverdueSAR(SarFilingStatus sar) {
        log.error("REGULATORY VIOLATION: SAR {} is OVERDUE - Deadline was {}", 
            sar.getSarId(), sar.getDeadline());

        incrementCounter("sars.overdue.violations", 1);

        // Immediate emergency filing
        initiateEmergencyFiling(sar);

        // Executive escalation
        complianceNotificationService.sendEmergencyExecutiveAlert(
            sar.getSarId(),
            "SAR_FILING_OVERDUE",
            "REGULATORY VIOLATION: SAR filing is overdue",
            sar.getUserId()
        );

        // Update status
        sar.setStatus(SarFilingStatus.FilingStatus.OVERDUE);
        sar.setLastUpdated(LocalDateTime.now());
        sarFilingStatusRepository.save(sar);

        // Create compliance incident
        createComplianceIncident(sar, "SAR_FILING_OVERDUE");
    }

    private void initiateSARFiling(SarFilingStatus sar) {
        try {
            log.info("Initiating filing for SAR: {}", sar.getSarId());

            // Create filing event
            Map<String, Object> filingEvent = Map.of(
                "sarId", sar.getSarId(),
                "userId", sar.getUserId(),
                "priority", sar.getPriority(),
                "category", sar.getCategory(),
                "violationType", sar.getViolationType(),
                "filingType", "SCHEDULED",
                "timestamp", LocalDateTime.now()
            );

            // Send to filing queue
            kafkaTemplate.send("sar-filing-execute", filingEvent);

            // Update status
            sar.setStatus(SarFilingStatus.FilingStatus.FILING_IN_PROGRESS);
            sar.setLastUpdated(LocalDateTime.now());
            sarFilingStatusRepository.save(sar);

            incrementCounter("sars.filing.initiated", 1);

        } catch (Exception e) {
            log.error("Failed to initiate SAR filing: {}", sar.getSarId(), e);
            handleProcessingFailure(sar, e);
        }
    }

    private void initiateEmergencyFiling(SarFilingStatus sar) {
        try {
            log.error("EMERGENCY FILING: Initiating emergency filing for SAR: {}", sar.getSarId());

            // Create emergency filing event
            Map<String, Object> emergencyEvent = Map.of(
                "sarId", sar.getSarId(),
                "userId", sar.getUserId(),
                "priority", "EMERGENCY",
                "category", sar.getCategory(),
                "violationType", sar.getViolationType(),
                "filingType", "EMERGENCY",
                "reason", "DEADLINE_APPROACHING",
                "timestamp", LocalDateTime.now()
            );

            // Send to priority filing queue
            kafkaTemplate.send("sar-filing-emergency", emergencyEvent);

            // Update status
            sar.setStatus(SarFilingStatus.FilingStatus.EMERGENCY_FILING);
            sar.setPriority(SarFilingStatus.FilingPriority.EMERGENCY);
            sar.setLastUpdated(LocalDateTime.now());
            sarFilingStatusRepository.save(sar);

            incrementCounter("sars.emergency.filings", 1);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to initiate emergency filing for SAR: {}", sar.getSarId(), e);
            // This is critical - create incident
            createComplianceIncident(sar, "EMERGENCY_FILING_FAILED");
        }
    }

    private void processScheduledFiling(SarFilingStatus sar) {
        log.info("Processing scheduled filing for SAR: {}", sar.getSarId());

        try {
            // Initiate the filing
            initiateSARFiling(sar);

            // Update scheduled filing date
            sar.setActualFilingDate(LocalDateTime.now());
            sarFilingStatusRepository.save(sar);

            incrementCounter("sars.scheduled.processed", 1);

        } catch (Exception e) {
            log.error("Failed to process scheduled filing: {}", sar.getSarId(), e);
            handleProcessingFailure(sar, e);
        }
    }

    private void checkApproachingDeadlines() {
        LocalDateTime now = LocalDateTime.now();
        
        // Check 72 hours out
        LocalDateTime warningThreshold = now.plusHours(72);
        List<SarFilingStatus> approachingSars = sarFilingStatusRepository
            .findByStatusNotInAndDeadlineBetween(
                List.of(SarFilingStatus.FilingStatus.FILED),
                now,
                warningThreshold
            );

        if (!approachingSars.isEmpty()) {
            log.warn("Found {} SARs approaching deadline within 72 hours", approachingSars.size());
            
            // Send consolidated warning
            sendDeadlineWarningReport(approachingSars);
        }
    }

    private void handleProcessingFailure(SarFilingStatus sar, Exception e) {
        // Increment retry count
        int retryCount = sar.getRetryCount() != null ? sar.getRetryCount() : 0;
        retryCount++;

        if (retryCount >= maxRetryAttempts) {
            // Max retries exceeded - escalate
            log.error("Max retries exceeded for SAR: {}", sar.getSarId());
            sar.setStatus(SarFilingStatus.FilingStatus.FAILED);
            createComplianceIncident(sar, "SAR_PROCESSING_FAILED");
        } else {
            // Update retry count
            sar.setRetryCount(retryCount);
        }

        sar.setLastError(e.getMessage());
        sar.setLastUpdated(LocalDateTime.now());
        sarFilingStatusRepository.save(sar);
    }

    private void createComplianceIncident(SarFilingStatus sar, String incidentType) {
        try {
            Map<String, Object> incident = Map.of(
                "incidentId", UUID.randomUUID().toString(),
                "incidentType", incidentType,
                "sarId", sar.getSarId(),
                "userId", sar.getUserId(),
                "severity", "CRITICAL",
                "description", "Compliance incident for SAR filing",
                "timestamp", LocalDateTime.now()
            );

            kafkaTemplate.send("compliance-incidents", incident);

            log.error("Compliance incident created for SAR: {} - Type: {}", sar.getSarId(), incidentType);

        } catch (Exception e) {
            log.error("Failed to create compliance incident", e);
        }
    }

    private Map<String, Object> generateFilingStatistics(LocalDateTime start, LocalDateTime end) {
        // Generate statistics for the period
        long totalFiled = sarFilingStatusRepository.countByStatusAndActualFilingDateBetween(
            SarFilingStatus.FilingStatus.FILED, start, end);
        
        long pendingCount = sarFilingStatusRepository.countByStatusIn(
            List.of(
                SarFilingStatus.FilingStatus.DRAFT,
                SarFilingStatus.FilingStatus.PENDING_REVIEW,
                SarFilingStatus.FilingStatus.APPROVED
            ));

        long overdueCount = sarFilingStatusRepository.countByStatusAndDeadlineBefore(
            SarFilingStatus.FilingStatus.OVERDUE, LocalDateTime.now());

        return Map.of(
            "periodStart", start,
            "periodEnd", end,
            "totalFiled", totalFiled,
            "pendingCount", pendingCount,
            "overdueCount", overdueCount,
            "complianceRate", calculateComplianceRate(totalFiled, overdueCount)
        );
    }

    private Map<String, Object> generateWeeklyStatistics(LocalDateTime start, LocalDateTime end) {
        Map<String, Object> basicStats = generateFilingStatistics(start, end);
        
        // Add weekly-specific metrics
        long emergencyFilings = sarFilingStatusRepository.countByPriorityAndLastUpdatedBetween(
            SarFilingStatus.FilingPriority.EMERGENCY, start, end);
        
        long criticalFilings = sarFilingStatusRepository.countByPriorityAndLastUpdatedBetween(
            SarFilingStatus.FilingPriority.CRITICAL, start, end);

        basicStats.put("emergencyFilings", emergencyFilings);
        basicStats.put("criticalFilings", criticalFilings);
        
        return basicStats;
    }

    private List<Map<String, Object>> identifyHighRiskPatterns(LocalDateTime start, LocalDateTime end) {
        // Identify patterns that require executive attention
        // This would typically involve more complex analysis
        return List.of();
    }

    private String calculateComplianceStatus(Map<String, Object> stats) {
        long overdueCount = (long) stats.getOrDefault("overdueCount", 0L);
        
        if (overdueCount > 0) {
            return "NON_COMPLIANT";
        }
        
        double complianceRate = (double) stats.getOrDefault("complianceRate", 0.0);
        if (complianceRate < 95.0) {
            return "AT_RISK";
        }
        
        return "COMPLIANT";
    }

    private double calculateComplianceRate(long filed, long overdue) {
        if (filed + overdue == 0) return 100.0;
        return (filed * 100.0) / (filed + overdue);
    }

    private void sendComplianceReport(Map<String, Object> report) {
        kafkaTemplate.send("compliance-reports", report);
        log.info("Compliance report sent: {}", report.get("reportType"));
    }

    private void sendExecutiveSummary(Map<String, Object> summary) {
        kafkaTemplate.send("executive-reports", summary);
        log.info("Executive summary sent: {}", summary.get("summaryType"));
    }

    private void sendDeadlineWarningReport(List<SarFilingStatus> sars) {
        Map<String, Object> warning = Map.of(
            "warningType", "SAR_DEADLINE_APPROACHING",
            "sarCount", sars.size(),
            "sars", sars,
            "timestamp", LocalDateTime.now()
        );
        
        kafkaTemplate.send("compliance-warnings", warning);
    }

    private void sendSchedulerFailureAlert(Exception e) {
        log.error("CRITICAL: SAR Filing Scheduler failure", e);
        
        Map<String, Object> alert = Map.of(
            "alertType", "SAR_SCHEDULER_FAILURE",
            "error", e.getMessage(),
            "severity", "CRITICAL",
            "timestamp", LocalDateTime.now()
        );
        
        kafkaTemplate.send("system-alerts", alert);
    }

    // Metrics helpers
    private void incrementCounter(String name, long value) {
        Counter.builder(name)
            .tag("service", "compliance-service")
            .tag("component", "sar-scheduler")
            .register(meterRegistry)
            .increment(value);
    }

    private Timer getTimer(String name) {
        return Timer.builder(name)
            .tag("service", "compliance-service")
            .tag("component", "sar-scheduler")
            .register(meterRegistry);
    }
}