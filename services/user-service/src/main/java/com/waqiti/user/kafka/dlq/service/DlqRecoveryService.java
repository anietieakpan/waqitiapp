package com.waqiti.user.kafka.dlq.service;

import com.waqiti.user.kafka.dlq.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Core service for DLQ recovery operations
 * Orchestrates recovery strategies and maintains audit trail
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DlqRecoveryService {

    private final DlqPersistenceService persistenceService;
    private final DlqAlertingService alertingService;
    private final DlqRetryScheduler retryScheduler;

    /**
     * Process a DLQ message with appropriate recovery strategy
     *
     * @param context Full context of the failed message
     * @return Result of recovery attempt
     */
    @Transactional
    public DlqRecoveryResult processDlqMessage(DlqRecoveryContext context) {
        long startTime = System.currentTimeMillis();

        log.warn("Processing DLQ message: type={}, severity={}, strategy={}, businessId={}",
                context.getEventType(),
                context.getSeverity(),
                context.getRecoveryStrategy(),
                context.getBusinessIdentifier());

        DlqRecoveryResult.DlqRecoveryResultBuilder resultBuilder = DlqRecoveryResult.builder();

        try {
            // Persist DLQ event for audit trail
            String dlqRecordId = persistenceService.persistDlqEvent(context);
            resultBuilder.notes("DLQ record persisted with ID: " + dlqRecordId);

            // Execute recovery strategy
            DlqRecoveryResult recoveryResult = executeRecoveryStrategy(context);

            // Update persistence with result
            persistenceService.updateRecoveryResult(dlqRecordId, recoveryResult);

            // Alert if necessary
            if (shouldAlert(context, recoveryResult)) {
                alertingService.sendAlert(context, recoveryResult);
            }

            // Schedule retry if needed
            if (recoveryResult.isShouldRetry()) {
                retryScheduler.scheduleRetry(context, recoveryResult.getRetryDelayMs());
            }

            recoveryResult.setDurationMs(System.currentTimeMillis() - startTime);

            return recoveryResult;

        } catch (Exception e) {
            log.error("Critical error in DLQ processing: {}", context.getBusinessIdentifier(), e);

            // Create failure result
            DlqRecoveryResult failureResult = resultBuilder
                    .success(false)
                    .status(DlqRecoveryResult.RecoveryStatus.FAILED_PERMANENT)
                    .errorMessage("Critical error in DLQ processing: " + e.getMessage())
                    .exception(e)
                    .requiresManualIntervention(true)
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();

            // Emergency alert
            alertingService.sendCriticalAlert(context, failureResult, e);

            return failureResult;
        }
    }

    /**
     * Execute the appropriate recovery strategy
     */
    private DlqRecoveryResult executeRecoveryStrategy(DlqRecoveryContext context) {
        DlqRecoveryStrategy strategy = context.getRecoveryStrategy();

        log.info("Executing recovery strategy: {} for event type: {}",
                strategy, context.getEventType());

        return switch (strategy) {
            case RETRY_WITH_BACKOFF -> handleRetryWithBackoff(context);
            case MANUAL_REVIEW -> handleManualReview(context);
            case SECURITY_ALERT -> handleSecurityAlert(context);
            case COMPENSATE -> handleCompensatingTransaction(context);
            case LOG_AND_IGNORE -> handleLogAndIgnore(context);
            case DEFER_TO_BATCH -> handleDeferToBatch(context);
            case ESCALATE_TO_ENGINEERING -> handleEscalateToEngineering(context);
        };
    }

    private DlqRecoveryResult handleRetryWithBackoff(DlqRecoveryContext context) {
        int retryAttempts = context.getRetryAttempts();
        long backoffMs = calculateExponentialBackoff(retryAttempts);

        DlqRecoveryResult result = DlqRecoveryResult.builder()
                .success(false)
                .status(DlqRecoveryResult.RecoveryStatus.FAILED_RETRY)
                .shouldRetry(retryAttempts < 5) // Max 5 retries
                .retryDelayMs(backoffMs)
                .requiresManualIntervention(retryAttempts >= 5)
                .build();

        result.addAction("SCHEDULE_RETRY",
                String.format("Scheduling retry #%d with backoff %dms", retryAttempts + 1, backoffMs),
                true,
                "Retry scheduled");

        if (retryAttempts >= 5) {
            result.addAction("MAX_RETRIES_EXCEEDED",
                    "Maximum retry attempts exceeded - manual intervention required",
                    false,
                    "Escalated to manual review queue");
        }

        return result;
    }

    private DlqRecoveryResult handleManualReview(DlqRecoveryContext context) {
        DlqRecoveryResult result = DlqRecoveryResult.builder()
                .success(false)
                .status(DlqRecoveryResult.RecoveryStatus.ESCALATED)
                .requiresManualIntervention(true)
                .build();

        String ticketNumber = alertingService.createManualReviewTicket(context);
        result.setTicketNumber(ticketNumber);

        result.addAction("CREATE_MANUAL_REVIEW_TICKET",
                "Created manual review ticket for operations team",
                true,
                "Ticket: " + ticketNumber);

        return result;
    }

    private DlqRecoveryResult handleSecurityAlert(DlqRecoveryContext context) {
        DlqRecoveryResult result = DlqRecoveryResult.builder()
                .success(true)
                .status(DlqRecoveryResult.RecoveryStatus.ESCALATED)
                .requiresManualIntervention(true)
                .build();

        // Send immediate security alert
        String incidentId = alertingService.triggerSecurityIncident(context);
        result.setTicketNumber(incidentId);

        result.addAction("SECURITY_ALERT",
                "Triggered security incident for immediate investigation",
                true,
                "Incident: " + incidentId);

        // Take protective action if critical
        if (context.getSeverity() == DlqSeverityLevel.CRITICAL) {
            String protectiveAction = takeProtectiveSecurityAction(context);
            result.addAction("PROTECTIVE_ACTION",
                    "Took protective security action",
                    true,
                    protectiveAction);
        }

        return result;
    }

    private DlqRecoveryResult handleCompensatingTransaction(DlqRecoveryContext context) {
        DlqRecoveryResult result = DlqRecoveryResult.builder()
                .success(false)
                .status(DlqRecoveryResult.RecoveryStatus.COMPENSATED)
                .build();

        try {
            String compensationResult = executeCompensation(context);

            result.setSuccess(true);
            result.addAction("COMPENSATING_TRANSACTION",
                    "Executed compensating transaction to restore consistency",
                    true,
                    compensationResult);

        } catch (Exception e) {
            log.error("Compensation failed for: {}", context.getBusinessIdentifier(), e);

            result.setSuccess(false);
            result.setRequiresManualIntervention(true);
            result.addAction("COMPENSATION_FAILED",
                    "Compensating transaction failed - manual intervention required",
                    false,
                    e.getMessage());
        }

        return result;
    }

    private DlqRecoveryResult handleLogAndIgnore(DlqRecoveryContext context) {
        DlqRecoveryResult result = DlqRecoveryResult.builder()
                .success(true)
                .status(DlqRecoveryResult.RecoveryStatus.RECOVERED)
                .build();

        result.addAction("LOG_AND_IGNORE",
                "Event logged for audit purposes - no further action required",
                true,
                "Event can be safely ignored per business rules");

        return result;
    }

    private DlqRecoveryResult handleDeferToBatch(DlqRecoveryContext context) {
        DlqRecoveryResult result = DlqRecoveryResult.builder()
                .success(true)
                .status(DlqRecoveryResult.RecoveryStatus.DEFERRED)
                .build();

        String batchJobId = persistenceService.queueForBatchProcessing(context);

        result.addAction("DEFER_TO_BATCH",
                "Queued for batch processing during off-peak hours",
                true,
                "Batch job ID: " + batchJobId);

        return result;
    }

    private DlqRecoveryResult handleEscalateToEngineering(DlqRecoveryContext context) {
        DlqRecoveryResult result = DlqRecoveryResult.builder()
                .success(false)
                .status(DlqRecoveryResult.RecoveryStatus.ESCALATED)
                .requiresManualIntervention(true)
                .build();

        String pagerDutyIncident = alertingService.triggerPagerDutyIncident(context);
        result.setTicketNumber(pagerDutyIncident);

        result.addAction("PAGERDUTY_INCIDENT",
                "Created PagerDuty incident for on-call engineer",
                true,
                "Incident: " + pagerDutyIncident);

        return result;
    }

    /**
     * Calculate exponential backoff with jitter
     */
    private long calculateExponentialBackoff(int retryAttempts) {
        // Base delay: 1 second
        // Exponential: 1s, 2s, 4s, 8s, 16s
        long baseDelayMs = 1000L;
        long exponentialDelay = (long) (baseDelayMs * Math.pow(2, retryAttempts));

        // Add jitter (±20%)
        double jitter = 0.8 + (Math.random() * 0.4);
        long delayWithJitter = (long) (exponentialDelay * jitter);

        // Cap at 5 minutes
        return Math.min(delayWithJitter, 300000L);
    }

    /**
     * Determine if alert should be sent
     */
    private boolean shouldAlert(DlqRecoveryContext context, DlqRecoveryResult result) {
        // Always alert for critical severity
        if (context.getSeverity() == DlqSeverityLevel.CRITICAL) {
            return true;
        }

        // Alert if manual intervention required
        if (result.isRequiresManualIntervention()) {
            return true;
        }

        // Alert for high severity after first retry
        if (context.getSeverity() == DlqSeverityLevel.HIGH && context.getRetryAttempts() > 0) {
            return true;
        }

        return false;
    }

    /**
     * Take protective security action (placeholder for implementation)
     */
    private String takeProtectiveSecurityAction(DlqRecoveryContext context) {
        // Implementation will depend on event type
        log.warn("SECURITY: Taking protective action for DLQ event: {}", context.getBusinessIdentifier());
        return "Protective action taken - details in security incident " + context.getBusinessIdentifier();
    }

    /**
     * Execute compensating transaction (placeholder for implementation)
     */
    private String executeCompensation(DlqRecoveryContext context) {
        // Implementation will depend on event type
        log.info("Executing compensating transaction for: {}", context.getBusinessIdentifier());
        return "Compensation executed for " + context.getBusinessIdentifier();
    }
}
