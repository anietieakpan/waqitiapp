package com.waqiti.wallet.service;

import com.waqiti.common.eventsourcing.PaymentFailedEvent;
import com.waqiti.wallet.domain.CompensationRecord;
import com.waqiti.wallet.domain.CompensationStatus;
import com.waqiti.wallet.repository.CompensationRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wallet Compensation Service
 * 
 * Handles compensation logic for wallet-related failures, including:
 * - Creating compensation records for failed operations
 * - Managing compensation workflows
 * - Tracking compensation status and retries
 * - Publishing compensation events for audit and monitoring
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletCompensationService {

    private final CompensationRecordRepository compensationRecordRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final WalletService walletService;
    private final WalletNotificationService notificationService;

    /**
     * Create a compensation record for a failed payment event
     */
    @Transactional
    public CompensationRecord createRecord(PaymentFailedEvent event, Exception error) {
        log.info("Creating compensation record for payment: {} due to error: {}", 
                event.getPaymentId(), error.getMessage());

        try {
            // Create comprehensive compensation record
            CompensationRecord record = CompensationRecord.builder()
                    .id(UUID.randomUUID())
                    .paymentId(event.getPaymentId())
                    .userId(event.getUserId())
                    .walletId(event.getSenderWalletId())
                    .amount(event.getAmount())
                    .currency(event.getCurrency())
                    .failureReason(event.getFailureReason())
                    .failureCode(event.getFailureCode())
                    .errorMessage(error.getMessage())
                    .errorStackTrace(getStackTrace(error))
                    .status(CompensationStatus.PENDING)
                    .compensationType(determineCompensationType(event, error))
                    .attemptCount(0)
                    .maxAttempts(3)
                    .metadata(buildMetadata(event, error))
                    .createdAt(LocalDateTime.now())
                    .build();

            // Save the record
            record = compensationRecordRepository.save(record);

            // Publish compensation event for monitoring
            publishCompensationEvent(record, "CREATED");

            // If critical, trigger immediate alert
            if (isCriticalCompensation(event, error)) {
                triggerCriticalAlert(record);
            }

            log.info("Compensation record created successfully: recordId={}, paymentId={}", 
                    record.getId(), event.getPaymentId());

            return record;

        } catch (Exception e) {
            log.error("Failed to create compensation record for payment: {}", 
                    event.getPaymentId(), e);
            
            // Last resort: write to dead letter queue
            writeToDeadLetterQueue(event, error, e);
            
            throw new RuntimeException("Failed to create compensation record", e);
        }
    }

    /**
     * Execute compensation for a pending record
     */
    @Transactional
    public void executeCompensation(UUID recordId) {
        log.info("Executing compensation for record: {}", recordId);

        CompensationRecord record = compensationRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Compensation record not found: " + recordId));

        if (record.getStatus() != CompensationStatus.PENDING && 
            record.getStatus() != CompensationStatus.RETRY) {
            log.warn("Compensation record {} is not in compensatable status: {}", 
                    recordId, record.getStatus());
            return;
        }

        try {
            // Update attempt count
            record.setAttemptCount(record.getAttemptCount() + 1);
            record.setLastAttemptAt(LocalDateTime.now());
            record.setStatus(CompensationStatus.IN_PROGRESS);
            compensationRecordRepository.save(record);

            // Execute compensation based on type
            switch (record.getCompensationType()) {
                case "RELEASE_FUNDS":
                    compensateReleaseReservedFunds(record);
                    break;
                case "REVERSE_TRANSACTION":
                    compensateReverseTransaction(record);
                    break;
                case "CREDIT_ADJUSTMENT":
                    compensateCreditAdjustment(record);
                    break;
                case "MANUAL_INTERVENTION":
                    requestManualIntervention(record);
                    break;
                default:
                    log.warn("Unknown compensation type: {}", record.getCompensationType());
                    record.setStatus(CompensationStatus.FAILED);
            }

            // Mark as completed if successful
            if (record.getStatus() == CompensationStatus.IN_PROGRESS) {
                record.setStatus(CompensationStatus.COMPLETED);
                record.setCompletedAt(LocalDateTime.now());
                compensationRecordRepository.save(record);
                
                publishCompensationEvent(record, "COMPLETED");
                log.info("Compensation completed successfully for record: {}", recordId);
            }

        } catch (Exception e) {
            log.error("Failed to execute compensation for record: {}", recordId, e);
            
            record.setStatus(CompensationStatus.FAILED);
            record.setLastErrorMessage(e.getMessage());
            
            // Check if we should retry
            if (record.getAttemptCount() < record.getMaxAttempts()) {
                record.setStatus(CompensationStatus.RETRY);
                record.setNextRetryAt(LocalDateTime.now().plusMinutes(
                        (long) Math.pow(2, record.getAttemptCount()))); // Exponential backoff
            } else {
                record.setStatus(CompensationStatus.FAILED_PERMANENTLY);
                triggerManualReviewAlert(record);
            }
            
            compensationRecordRepository.save(record);
            publishCompensationEvent(record, "FAILED");
        }
    }

    /**
     * Compensate by releasing reserved funds
     */
    private void compensateReleaseReservedFunds(CompensationRecord record) {
        log.info("Compensating: Releasing reserved funds for wallet: {}, amount: {} {}", 
                record.getWalletId(), record.getAmount(), record.getCurrency());

        walletService.releaseReservation(
                record.getWalletId(),
                record.getPaymentId(),
                record.getAmount(),
                record.getCurrency(),
                "Compensation: " + record.getFailureReason()
        );

        record.getMetadata().put("fundsReleased", true);
        record.getMetadata().put("releaseTimestamp", LocalDateTime.now().toString());
    }

    /**
     * Compensate by reversing a transaction
     */
    private void compensateReverseTransaction(CompensationRecord record) {
        log.info("Compensating: Reversing transaction for payment: {}", record.getPaymentId());

        // Create reversal transaction
        String reversalId = walletService.createReversalTransaction(
                record.getWalletId(),
                record.getPaymentId(),
                record.getAmount(),
                record.getCurrency(),
                "Compensation reversal: " + record.getFailureReason()
        );

        record.getMetadata().put("reversalId", reversalId);
        record.getMetadata().put("reversalTimestamp", LocalDateTime.now().toString());
    }

    /**
     * Compensate with a credit adjustment
     */
    private void compensateCreditAdjustment(CompensationRecord record) {
        log.info("Compensating: Applying credit adjustment for wallet: {}, amount: {} {}", 
                record.getWalletId(), record.getAmount(), record.getCurrency());

        String adjustmentId = walletService.applyCompensationCredit(
                record.getWalletId(),
                record.getAmount(),
                record.getCurrency(),
                "Compensation credit for failed payment: " + record.getPaymentId()
        );

        record.getMetadata().put("creditAdjustmentId", adjustmentId);
        record.getMetadata().put("creditTimestamp", LocalDateTime.now().toString());

        // Notify user of credit
        notificationService.sendCompensationCreditNotification(
                record.getUserId(),
                record.getAmount(),
                record.getCurrency()
        );
    }

    /**
     * Request manual intervention for complex compensation cases
     */
    private void requestManualIntervention(CompensationRecord record) {
        log.info("Requesting manual intervention for compensation record: {}", record.getId());

        record.setStatus(CompensationStatus.MANUAL_REVIEW);
        record.getMetadata().put("manualReviewRequested", true);
        record.getMetadata().put("reviewRequestedAt", LocalDateTime.now().toString());

        // Create manual review task
        createManualReviewTask(record);

        // Notify operations team
        notifyOperationsTeam(record);
    }

    /**
     * Determine the type of compensation needed
     */
    private String determineCompensationType(PaymentFailedEvent event, Exception error) {
        // Complex logic to determine compensation type based on failure
        if (event.getFailureCode() != null) {
            switch (event.getFailureCode()) {
                case "INSUFFICIENT_FUNDS":
                case "LIMIT_EXCEEDED":
                    return "RELEASE_FUNDS";
                case "DUPLICATE_TRANSACTION":
                case "ALREADY_PROCESSED":
                    return "REVERSE_TRANSACTION";
                case "SYSTEM_ERROR":
                case "NETWORK_FAILURE":
                    return "CREDIT_ADJUSTMENT";
                default:
                    break;
            }
        }

        // Check error type
        if (error instanceof IllegalStateException) {
            return "RELEASE_FUNDS";
        } else if (error instanceof RuntimeException && 
                   error.getMessage().contains("timeout")) {
            return "MANUAL_INTERVENTION";
        }

        // Default to manual intervention for unknown cases
        return "MANUAL_INTERVENTION";
    }

    /**
     * Check if this is a critical compensation case
     */
    private boolean isCriticalCompensation(PaymentFailedEvent event, Exception error) {
        // Critical if large amount
        // ✅ FIXED: Using string literal instead of valueOf() for consistency
        if (event.getAmount() != null &&
            event.getAmount().compareTo(new java.math.BigDecimal("10000.00")) > 0) {
            return true;
        }

        // Critical if certain error types
        if (error.getMessage() != null && 
            (error.getMessage().contains("data corruption") ||
             error.getMessage().contains("security breach"))) {
            return true;
        }

        return false;
    }

    /**
     * Build metadata for the compensation record
     */
    private Map<String, Object> buildMetadata(PaymentFailedEvent event, Exception error) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("eventType", "PaymentFailed");
        metadata.put("originalAmount", event.getAmount());
        metadata.put("originalCurrency", event.getCurrency());
        metadata.put("failureReason", event.getFailureReason());
        metadata.put("failureCode", event.getFailureCode());
        metadata.put("errorClass", error.getClass().getName());
        metadata.put("timestamp", LocalDateTime.now().toString());
        
        if (event.getMetadata() != null) {
            metadata.putAll(event.getMetadata());
        }
        
        return metadata;
    }

    /**
     * Get stack trace as string
     */
    private String getStackTrace(Exception error) {
        if (error == null) return null;
        
        StringBuilder sb = new StringBuilder();
        sb.append(error.toString()).append("\n");
        
        StackTraceElement[] stackTrace = error.getStackTrace();
        int maxLines = Math.min(stackTrace.length, 20); // Limit to 20 lines
        
        for (int i = 0; i < maxLines; i++) {
            sb.append("\tat ").append(stackTrace[i]).append("\n");
        }
        
        if (stackTrace.length > maxLines) {
            sb.append("\t... ").append(stackTrace.length - maxLines).append(" more\n");
        }
        
        return sb.toString();
    }

    /**
     * Publish compensation event for monitoring
     */
    private void publishCompensationEvent(CompensationRecord record, String action) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("recordId", record.getId());
            event.put("paymentId", record.getPaymentId());
            event.put("action", action);
            event.put("status", record.getStatus());
            event.put("timestamp", LocalDateTime.now());
            
            kafkaTemplate.send("wallet-compensation-events", event);
            
        } catch (Exception e) {
            log.warn("Failed to publish compensation event: {}", e.getMessage());
        }
    }

    /**
     * Trigger critical alert
     */
    private void triggerCriticalAlert(CompensationRecord record) {
        log.error("CRITICAL COMPENSATION ALERT: recordId={}, paymentId={}, amount={} {}", 
                record.getId(), record.getPaymentId(), record.getAmount(), record.getCurrency());
        
        // Send to monitoring system
        Map<String, Object> alert = new HashMap<>();
        alert.put("type", "CRITICAL_COMPENSATION");
        alert.put("recordId", record.getId());
        alert.put("paymentId", record.getPaymentId());
        alert.put("amount", record.getAmount());
        alert.put("currency", record.getCurrency());
        alert.put("timestamp", LocalDateTime.now());
        
        kafkaTemplate.send("critical-alerts", alert);
    }

    /**
     * Trigger manual review alert
     */
    private void triggerManualReviewAlert(CompensationRecord record) {
        log.warn("Manual review required for compensation: recordId={}, paymentId={}", 
                record.getId(), record.getPaymentId());
        
        // Create review task
        createManualReviewTask(record);
        
        // Notify operations team
        notifyOperationsTeam(record);
    }

    /**
     * Create manual review task
     */
    private void createManualReviewTask(CompensationRecord record) {
        Map<String, Object> task = new HashMap<>();
        task.put("type", "COMPENSATION_REVIEW");
        task.put("recordId", record.getId());
        task.put("paymentId", record.getPaymentId());
        task.put("priority", "HIGH");
        task.put("assignedTeam", "OPERATIONS");
        task.put("createdAt", LocalDateTime.now());
        
        kafkaTemplate.send("manual-review-tasks", task);
    }

    /**
     * Notify operations team
     */
    private void notifyOperationsTeam(CompensationRecord record) {
        String message = String.format(
                "Manual compensation review required:\n" +
                "Record ID: %s\n" +
                "Payment ID: %s\n" +
                "Amount: %s %s\n" +
                "Failure: %s",
                record.getId(),
                record.getPaymentId(),
                record.getAmount(),
                record.getCurrency(),
                record.getFailureReason()
        );
        
        notificationService.sendOperationsAlert("COMPENSATION_REVIEW", message);
    }

    /**
     * Write to dead letter queue as last resort
     */
    private void writeToDeadLetterQueue(PaymentFailedEvent event, Exception originalError, Exception compensationError) {
        try {
            Map<String, Object> dlqEntry = new HashMap<>();
            dlqEntry.put("event", event);
            dlqEntry.put("originalError", originalError.getMessage());
            dlqEntry.put("compensationError", compensationError.getMessage());
            dlqEntry.put("timestamp", LocalDateTime.now());
            
            kafkaTemplate.send("wallet-compensation-dlq", dlqEntry);
            
            log.error("Written to DLQ: paymentId={}", event.getPaymentId());
            
        } catch (Exception e) {
            // Absolute last resort - log to file
            log.error("CRITICAL: Failed to write to DLQ for payment: {} - Original: {} - Compensation: {} - DLQ: {}", 
                    event.getPaymentId(), 
                    originalError.getMessage(),
                    compensationError.getMessage(),
                    e.getMessage());
        }
    }
}