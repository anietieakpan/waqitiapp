package com.waqiti.user.gdpr.service;

import com.waqiti.user.gdpr.entity.GdprManualIntervention;
import com.waqiti.user.gdpr.repository.GdprManualInterventionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * GDPR Manual Intervention Service
 *
 * Manages failed GDPR operations that require manual processing
 * to ensure 30-day SLA compliance.
 *
 * REGULATORY CONTEXT:
 * - GDPR Article 12(3): Response without undue delay, within one month
 * - Article 12(3) extension: May extend by two additional months if complex
 * - Article 83(5): Fines up to €20M or 4% of global revenue
 *
 * USE CASES:
 * 1. External service failure during deletion (wallet-service down)
 * 2. Data export timeout for large user histories
 * 3. Complex deletion scenarios requiring human judgment
 * 4. Compensation needed after partial deletion failure
 *
 * MANUAL INTERVENTION WORKFLOW:
 * 1. Automated process fails
 * 2. Create intervention ticket with 30-day SLA
 * 3. Alert operations team (email, Slack, PagerDuty)
 * 4. Assign to operator
 * 5. Operator processes manually
 * 6. Mark as resolved with notes
 * 7. Automatic SLA breach alerts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GdprManualInterventionService {

    private final GdprManualInterventionRepository interventionRepository;
    // TODO: Inject alerting services when available
    // private final SlackAlertService slackAlertService;
    // private final PagerDutyService pagerDutyService;
    // private final EmailService emailService;

    /**
     * Create manual intervention ticket for failed GDPR operation
     *
     * @param userId User affected
     * @param operationType Type of operation that failed
     * @param description Human-readable description
     * @param cause Exception that caused the failure
     * @return Ticket number for tracking
     */
    @Transactional
    public String createIntervention(
            UUID userId,
            String operationType,
            String description,
            Exception cause) {

        String ticketNumber = generateTicketNumber();

        GdprManualIntervention intervention = GdprManualIntervention.builder()
                .ticketNumber(ticketNumber)
                .userId(userId)
                .operationType(operationType)
                .description(description)
                .failureReason(cause != null ? cause.getMessage() : "Unknown")
                .status("PENDING")
                .slaDeadline(calculateSlaDeadline())
                .createdAt(LocalDateTime.now())
                .build();

        interventionRepository.save(intervention);

        log.warn("GDPR INTERVENTION: Created ticket {} for user {} - operation: {} - reason: {}",
                ticketNumber, userId, operationType,
                cause != null ? cause.getMessage() : "Unknown");

        // Send alerts to operations team
        sendInterventionAlerts(intervention);

        return ticketNumber;
    }

    /**
     * Create intervention with full exception details
     */
    @Transactional
    public String createInterventionWithDetails(
            UUID userId,
            String operationType,
            String description,
            String failureReason,
            String stackTrace) {

        String ticketNumber = generateTicketNumber();

        GdprManualIntervention intervention = GdprManualIntervention.builder()
                .ticketNumber(ticketNumber)
                .userId(userId)
                .operationType(operationType)
                .description(description)
                .failureReason(failureReason)
                .stackTrace(stackTrace)
                .status("PENDING")
                .slaDeadline(calculateSlaDeadline())
                .createdAt(LocalDateTime.now())
                .build();

        interventionRepository.save(intervention);

        log.warn("GDPR INTERVENTION: Created detailed ticket {} for user {}",
                ticketNumber, userId);

        sendInterventionAlerts(intervention);

        return ticketNumber;
    }

    /**
     * Assign intervention to operator
     */
    @Transactional
    public void assignIntervention(String ticketNumber, String assignedTo) {
        GdprManualIntervention intervention = interventionRepository
                .findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Intervention not found: " + ticketNumber));

        intervention.setAssignedTo(assignedTo);
        intervention.setStatus("IN_PROGRESS");
        intervention.setUpdatedAt(LocalDateTime.now());

        interventionRepository.save(intervention);

        log.info("GDPR INTERVENTION: Ticket {} assigned to {}",
                ticketNumber, assignedTo);
    }

    /**
     * Mark intervention as resolved
     */
    @Transactional
    public void resolveIntervention(String ticketNumber, String resolutionNotes) {
        GdprManualIntervention intervention = interventionRepository
                .findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Intervention not found: " + ticketNumber));

        intervention.setStatus("RESOLVED");
        intervention.setResolutionNotes(resolutionNotes);
        intervention.setResolvedAt(LocalDateTime.now());
        intervention.setUpdatedAt(LocalDateTime.now());

        interventionRepository.save(intervention);

        long daysToResolve = java.time.Duration.between(
                intervention.getCreatedAt(),
                intervention.getResolvedAt()).toDays();

        log.info("GDPR INTERVENTION: Ticket {} resolved after {} days - within SLA: {}",
                ticketNumber, daysToResolve, daysToResolve <= 30);

        // Alert if SLA was breached
        if (daysToResolve > 30) {
            log.error("GDPR SLA BREACH: Ticket {} took {} days - exceeded 30-day limit!",
                    ticketNumber, daysToResolve);
            // TODO: Send SLA breach alert to compliance team
        }
    }

    /**
     * Escalate intervention to higher tier support
     */
    @Transactional
    public void escalateIntervention(String ticketNumber, String escalationReason) {
        GdprManualIntervention intervention = interventionRepository
                .findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Intervention not found: " + ticketNumber));

        intervention.setStatus("ESCALATED");
        intervention.setEscalationReason(escalationReason);
        intervention.setEscalatedAt(LocalDateTime.now());
        intervention.setUpdatedAt(LocalDateTime.now());

        interventionRepository.save(intervention);

        log.warn("GDPR INTERVENTION: Ticket {} escalated - reason: {}",
                ticketNumber, escalationReason);

        // Send escalation alerts
        sendEscalationAlerts(intervention);
    }

    /**
     * Get all pending interventions
     */
    @Transactional(readOnly = true)
    public List<GdprManualIntervention> getPendingInterventions() {
        return interventionRepository.findByStatus("PENDING");
    }

    /**
     * Get interventions approaching SLA deadline
     */
    @Transactional(readOnly = true)
    public List<GdprManualIntervention> getInterventionsApproachingSla(int daysBeforeDeadline) {
        LocalDateTime threshold = LocalDateTime.now().plusDays(daysBeforeDeadline);
        return interventionRepository.findBySlaDeadlineBefore(threshold);
    }

    /**
     * Get overdue interventions (SLA breached)
     */
    @Transactional(readOnly = true)
    public List<GdprManualIntervention> getOverdueInterventions() {
        return interventionRepository.findBySlaDeadlineBefore(LocalDateTime.now());
    }

    /**
     * Get interventions for specific user
     */
    @Transactional(readOnly = true)
    public List<GdprManualIntervention> getInterventionsForUser(UUID userId) {
        return interventionRepository.findByUserId(userId);
    }

    /**
     * Generate unique ticket number
     * Format: GDPR-YYYYMMDD-XXXXXX
     */
    private String generateTicketNumber() {
        String datePrefix = LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));

        String randomSuffix = UUID.randomUUID().toString()
                .substring(0, 6)
                .toUpperCase();

        return String.format("GDPR-%s-%s", datePrefix, randomSuffix);
    }

    /**
     * Calculate SLA deadline (30 days from now)
     * GDPR Article 12(3): One month maximum
     */
    private LocalDateTime calculateSlaDeadline() {
        return LocalDateTime.now().plusDays(30);
    }

    /**
     * Send alerts to operations team about new intervention
     */
    private void sendInterventionAlerts(GdprManualIntervention intervention) {
        // Log for now - integrate with actual alerting services
        log.warn("GDPR ALERT: Manual intervention required - Ticket: {} - User: {} - Operation: {} - SLA: {}",
                intervention.getTicketNumber(),
                intervention.getUserId(),
                intervention.getOperationType(),
                intervention.getSlaDeadline());

        // TODO: Integrate with actual alerting
        // slackAlertService.sendGdprIntervention(intervention);
        // emailService.sendGdprInterventionEmail(intervention);

        // For critical operations, page on-call engineer
        if (isCriticalOperation(intervention.getOperationType())) {
            log.error("GDPR CRITICAL: {} - requires immediate attention", intervention.getTicketNumber());
            // pagerDutyService.triggerIncident("gdpr-intervention", intervention);
        }
    }

    /**
     * Send escalation alerts
     */
    private void sendEscalationAlerts(GdprManualIntervention intervention) {
        log.error("GDPR ESCALATION: Ticket {} escalated - Reason: {}",
                intervention.getTicketNumber(),
                intervention.getEscalationReason());

        // TODO: Send to management/compliance team
        // slackAlertService.sendGdprEscalation(intervention);
        // pagerDutyService.escalateIncident(intervention.getTicketNumber());
    }

    /**
     * Determine if operation is critical
     */
    private boolean isCriticalOperation(String operationType) {
        return operationType != null && (
                operationType.contains("DELETION_FAILED") ||
                operationType.contains("WALLET_ANONYMIZATION_FAILED") ||
                operationType.contains("PAYMENT_ANONYMIZATION_FAILED")
        );
    }
}
