package com.waqiti.compliance.fincen;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.monitoring.AlertingService;
import com.waqiti.compliance.fincen.entity.ManualFilingQueueEntry;
import com.waqiti.compliance.fincen.entity.FilingStatus;
import com.waqiti.compliance.fincen.entity.FilingPriority;
import com.waqiti.compliance.fincen.repository.ManualFilingQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FinCEN Manual Filing Queue Service
 *
 * CRITICAL REGULATORY COMPLIANCE SYSTEM:
 * Manages queue of SARs requiring manual filing when FinCEN API is unavailable.
 *
 * LEGAL REQUIREMENTS:
 * - SARs must be filed within 30 days of detection
 * - Late filing = $25,000 - $1,000,000 penalties
 * - Willful violations = criminal prosecution
 *
 * FEATURES:
 * - Persistent queue in database
 * - Priority handling for expedited filings
 * - Automatic alerting to compliance team
 * - SLA tracking and escalation
 * - Integration with ticketing system (JIRA)
 * - Daily digest reports
 * - Audit trail for regulatory exams
 *
 * @author Waqiti Compliance Team
 * @version 2.0.0
 * @since 2025-11-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FincenManualFilingQueueService {

    private final ManualFilingQueueRepository queueRepository;
    private final AuditService auditService;
    private final AlertingService alertingService;
    private final FincenJiraIntegrationService jiraService;
    private final FincenEmailService emailService;

    // SLA Configuration
    private static final int EXPEDITED_SLA_HOURS = 24;
    private static final int STANDARD_SLA_HOURS = 72;
    private static final int WARNING_THRESHOLD_HOURS = 12;

    /**
     * Add SAR to manual filing queue
     *
     * Called when FinCEN API submission fails and fallback is triggered.
     *
     * CRITICAL ACTIONS:
     * 1. Store SAR in persistent queue
     * 2. Create JIRA ticket
     * 3. Send email alert to compliance team
     * 4. Log in audit trail
     * 5. Escalate if expedited filing
     *
     * @param sarId Internal SAR ID
     * @param sarXml SAR XML content
     * @param expedited Whether this is expedited filing
     * @param failureReason Reason for API failure
     * @return Queue entry ID
     */
    @Transactional
    public UUID addToManualQueue(String sarId, String sarXml, boolean expedited, String failureReason) {
        log.error("FINCEN MANUAL QUEUE: Adding SAR to manual filing queue - sarId={}, expedited={}",
                sarId, expedited);

        try {
            // 1. Create queue entry
            ManualFilingQueueEntry entry = ManualFilingQueueEntry.builder()
                    .id(UUID.randomUUID())
                    .sarId(sarId)
                    .sarXml(sarXml)
                    .status(FilingStatus.PENDING)
                    .priority(expedited ? FilingPriority.EXPEDITED : FilingPriority.STANDARD)
                    .failureReason(failureReason)
                    .queuedAt(LocalDateTime.now())
                    .slaDeadline(calculateSlaDeadline(expedited))
                    .escalated(false)
                    .retryCount(0)
                    .build();

            entry = queueRepository.save(entry);

            // 2. Create JIRA ticket
            String jiraTicketId = jiraService.createManualFilingTicket(
                    sarId,
                    expedited,
                    failureReason,
                    entry.getSlaDeadline()
            );

            entry.setJiraTicketId(jiraTicketId);
            queueRepository.save(entry);

            log.info("FINCEN MANUAL QUEUE: Created JIRA ticket - sarId={}, jiraTicketId={}",
                    sarId, jiraTicketId);

            // 3. Send email alert to compliance team
            emailService.sendManualFilingAlert(
                    sarId,
                    jiraTicketId,
                    expedited,
                    failureReason,
                    entry.getSlaDeadline()
            );

            // 4. Send critical alert
            alertingService.sendCriticalAlert(
                    "FINCEN_MANUAL_FILING_REQUIRED",
                    String.format("SAR requires MANUAL FILING - sarId=%s, JIRA=%s, SLA Deadline=%s",
                            sarId, jiraTicketId, entry.getSlaDeadline()),
                    Map.of(
                            "sarId", sarId,
                            "jiraTicketId", jiraTicketId,
                            "expedited", String.valueOf(expedited),
                            "slaDeadline", entry.getSlaDeadline().toString(),
                            "failureReason", failureReason
                    )
            );

            // 5. Audit log
            auditService.logCriticalError(
                    "FincenManualFilingQueue",
                    "SAR_QUEUED_FOR_MANUAL_FILING",
                    "SAR added to manual filing queue due to API failure",
                    Map.of(
                            "sarId", sarId,
                            "queueEntryId", entry.getId().toString(),
                            "jiraTicketId", jiraTicketId,
                            "expedited", String.valueOf(expedited),
                            "failureReason", failureReason
                    )
            );

            log.warn("FINCEN MANUAL QUEUE: SAR successfully queued - sarId={}, queueEntryId={}, jiraTicketId={}",
                    sarId, entry.getId(), jiraTicketId);

            return entry.getId();

        } catch (Exception e) {
            log.error("FINCEN MANUAL QUEUE: CRITICAL ERROR - Failed to queue SAR for manual filing. " +
                    "This is a REGULATORY VIOLATION RISK. sarId={}", sarId, e);

            // Send emergency alert
            alertingService.sendEmergencyAlert(
                    "FINCEN_MANUAL_QUEUE_FAILURE",
                    "CRITICAL: Failed to queue SAR for manual filing - IMMEDIATE ACTION REQUIRED",
                    Map.of("sarId", sarId, "error", e.getMessage())
            );

            throw new RuntimeException("Failed to queue SAR for manual filing: " + sarId, e);
        }
    }

    /**
     * Mark SAR as manually filed
     *
     * Called by compliance team after successful manual filing to FinCEN.
     *
     * @param queueEntryId Queue entry ID
     * @param filingNumber FinCEN filing number
     * @param filedBy Compliance officer who filed
     * @param notes Filing notes
     */
    @Transactional
    public void markAsManuallyFiled(
            UUID queueEntryId,
            String filingNumber,
            String filedBy,
            String notes) {

        log.info("FINCEN MANUAL QUEUE: Marking SAR as manually filed - queueEntryId={}, filingNumber={}",
                queueEntryId, filingNumber);

        ManualFilingQueueEntry entry = queueRepository.findById(queueEntryId)
                .orElseThrow(() -> new IllegalArgumentException("Queue entry not found: " + queueEntryId));

        entry.setStatus(FilingStatus.MANUALLY_FILED);
        entry.setFilingNumber(filingNumber);
        entry.setFiledBy(filedBy);
        entry.setFiledAt(LocalDateTime.now());
        entry.setNotes(notes);

        queueRepository.save(entry);

        // Close JIRA ticket
        if (entry.getJiraTicketId() != null) {
            jiraService.closeTicket(entry.getJiraTicketId(), "SAR manually filed to FinCEN");
        }

        // Audit log
        auditService.logManualIntervention(
                "SAR_MANUALLY_FILED",
                entry.getSarId(),
                Map.of(
                        "queueEntryId", queueEntryId.toString(),
                        "filingNumber", filingNumber,
                        "filedBy", filedBy,
                        "jiraTicketId", entry.getJiraTicketId() != null ? entry.getJiraTicketId() : "N/A"
                )
        );

        log.info("FINCEN MANUAL QUEUE: SAR marked as manually filed - sarId={}, filingNumber={}",
                entry.getSarId(), filingNumber);
    }

    /**
     * Scheduled job to check SLAs and escalate overdue filings
     *
     * Runs every hour to monitor SLA compliance.
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void checkSlaAndEscalate() {
        log.info("FINCEN MANUAL QUEUE: Running SLA check and escalation");

        List<ManualFilingQueueEntry> pendingEntries = queueRepository.findByStatus(FilingStatus.PENDING);

        int escalatedCount = 0;
        int overdueCount = 0;

        for (ManualFilingQueueEntry entry : pendingEntries) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime warningTime = entry.getSlaDeadline().minusHours(WARNING_THRESHOLD_HOURS);

            // Check if approaching SLA deadline
            if (now.isAfter(warningTime) && !entry.isEscalated()) {
                escalateEntry(entry, "Approaching SLA deadline");
                escalatedCount++;
            }

            // Check if SLA deadline passed
            if (now.isAfter(entry.getSlaDeadline())) {
                entry.setStatus(FilingStatus.OVERDUE);
                queueRepository.save(entry);

                sendOverdueAlert(entry);
                overdueCount++;

                log.error("FINCEN MANUAL QUEUE: SAR filing OVERDUE - sarId={}, queuedAt={}, slaDeadline={}",
                        entry.getSarId(), entry.getQueuedAt(), entry.getSlaDeadline());
            }
        }

        if (escalatedCount > 0 || overdueCount > 0) {
            log.warn("FINCEN MANUAL QUEUE: SLA check completed - escalated={}, overdue={}",
                    escalatedCount, overdueCount);
        } else {
            log.debug("FINCEN MANUAL QUEUE: SLA check completed - all within SLA");
        }
    }

    /**
     * Get daily digest for compliance team
     *
     * Summary of all pending, overdue, and recently filed SARs.
     */
    @Scheduled(cron = "0 0 9 * * MON-FRI") // 9 AM weekdays
    public void sendDailyDigest() {
        log.info("FINCEN MANUAL QUEUE: Sending daily digest");

        long pendingCount = queueRepository.countByStatus(FilingStatus.PENDING);
        long overdueCount = queueRepository.countByStatus(FilingStatus.OVERDUE);
        long filedLast24Hours = queueRepository.countFiledInLast24Hours();

        List<ManualFilingQueueEntry> criticalEntries = queueRepository.findCriticalEntries();

        emailService.sendDailyDigest(
                pendingCount,
                overdueCount,
                filedLast24Hours,
                criticalEntries
        );

        log.info("FINCEN MANUAL QUEUE: Daily digest sent - pending={}, overdue={}, filed24h={}",
                pendingCount, overdueCount, filedLast24Hours);
    }

    /**
     * Get queue statistics for monitoring
     */
    public ManualFilingQueueStatistics getStatistics() {
        return ManualFilingQueueStatistics.builder()
                .totalPending(queueRepository.countByStatus(FilingStatus.PENDING))
                .totalOverdue(queueRepository.countByStatus(FilingStatus.OVERDUE))
                .totalFiled(queueRepository.countByStatus(FilingStatus.MANUALLY_FILED))
                .expeditedPending(queueRepository.countByStatusAndPriority(
                        FilingStatus.PENDING, FilingPriority.EXPEDITED))
                .oldestPendingEntry(queueRepository.findOldestPending())
                .averageTimeToFile(queueRepository.calculateAverageTimeToFile())
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ========== PRIVATE HELPER METHODS ==========

    private LocalDateTime calculateSlaDeadline(boolean expedited) {
        int hoursToAdd = expedited ? EXPEDITED_SLA_HOURS : STANDARD_SLA_HOURS;
        return LocalDateTime.now().plusHours(hoursToAdd);
    }

    private void escalateEntry(ManualFilingQueueEntry entry, String reason) {
        log.warn("FINCEN MANUAL QUEUE: Escalating entry - sarId={}, reason={}",
                entry.getSarId(), reason);

        entry.setEscalated(true);
        entry.setEscalatedAt(LocalDateTime.now());
        entry.setEscalationReason(reason);
        queueRepository.save(entry);

        // Update JIRA ticket priority
        if (entry.getJiraTicketId() != null) {
            jiraService.escalateTicket(entry.getJiraTicketId(), reason);
        }

        // Send escalation alert
        alertingService.sendAlert(
                "FINCEN_FILING_ESCALATED",
                AlertSeverity.CRITICAL,
                String.format("SAR filing escalated - sarId=%s, JIRA=%s, reason=%s",
                        entry.getSarId(), entry.getJiraTicketId(), reason),
                Map.of(
                        "sarId", entry.getSarId(),
                        "jiraTicketId", entry.getJiraTicketId(),
                        "reason", reason,
                        "slaDeadline", entry.getSlaDeadline().toString()
                )
        );
    }

    private void sendOverdueAlert(ManualFilingQueueEntry entry) {
        alertingService.sendEmergencyAlert(
                "FINCEN_FILING_OVERDUE",
                "EMERGENCY: SAR filing SLA VIOLATED - REGULATORY PENALTY RISK",
                Map.of(
                        "sarId", entry.getSarId(),
                        "jiraTicketId", entry.getJiraTicketId(),
                        "queuedAt", entry.getQueuedAt().toString(),
                        "slaDeadline", entry.getSlaDeadline().toString(),
                        "hoursOverdue", String.valueOf(calculateHoursOverdue(entry))
                )
        );

        // Audit critical compliance violation
        auditService.logCriticalError(
                "FincenManualFilingQueue",
                "SAR_FILING_SLA_VIOLATED",
                "SAR filing SLA deadline exceeded - regulatory penalty risk",
                Map.of(
                        "sarId", entry.getSarId(),
                        "queueEntryId", entry.getId().toString(),
                        "jiraTicketId", entry.getJiraTicketId()
                )
        );
    }

    private long calculateHoursOverdue(ManualFilingQueueEntry entry) {
        return java.time.Duration.between(entry.getSlaDeadline(), LocalDateTime.now()).toHours();
    }
}
