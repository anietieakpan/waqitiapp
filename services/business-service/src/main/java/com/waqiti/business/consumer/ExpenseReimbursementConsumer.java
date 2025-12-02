package com.waqiti.business.consumer;

import com.waqiti.business.event.ExpenseReimbursementEvent;
import com.waqiti.business.service.BusinessExpenseManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Production-grade Kafka consumer for expense reimbursement events
 * Handles reimbursement processing and payment workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpenseReimbursementConsumer {

    private final BusinessExpenseManagementService expenseManagementService;

    @KafkaListener(topics = "expense-reimbursement-events", groupId = "expense-reimbursement-processor")
    public void processExpenseReimbursementEvent(@Payload ExpenseReimbursementEvent event,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                               @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                               @Header(KafkaHeaders.OFFSET) long offset,
                                               Acknowledgment acknowledgment) {
        try {
            log.info("Processing expense reimbursement event: {} action: {} amount: {} {}", 
                    event.getEventId(), event.getAction(), event.getReimbursementAmount(), event.getCurrency());
            
            // Validate event
            validateExpenseReimbursementEvent(event);
            
            // Process based on action type
            switch (event.getAction()) {
                case "INITIATED" -> handleReimbursementInitiated(event);
                case "PROCESSED" -> handleReimbursementProcessed(event);
                case "COMPLETED" -> handleReimbursementCompleted(event);
                case "FAILED" -> handleReimbursementFailed(event);
                case "CANCELLED" -> handleReimbursementCancelled(event);
                default -> {
                    log.warn("Unknown reimbursement action: {} for event: {}", event.getAction(), event.getEventId());
                    // Don't throw exception for unknown actions to avoid DLQ
                }
            }
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed expense reimbursement event: {} action: {}", 
                    event.getEventId(), event.getAction());
            
        } catch (Exception e) {
            log.error("Failed to process expense reimbursement event: {} error: {}", 
                    event.getEventId(), e.getMessage(), e);
            
            // Don't acknowledge - let retry mechanism handle it
            throw new RuntimeException("Expense reimbursement event processing failed", e);
        }
    }

    private void validateExpenseReimbursementEvent(ExpenseReimbursementEvent event) {
        if (event.getBusinessId() == null || event.getBusinessId().trim().isEmpty()) {
            throw new IllegalArgumentException("Business ID is required for reimbursement event");
        }
        
        if (event.getAction() == null || event.getAction().trim().isEmpty()) {
            throw new IllegalArgumentException("Action is required for reimbursement event");
        }
        
        if (event.getEmployeeId() == null || event.getEmployeeId().trim().isEmpty()) {
            throw new IllegalArgumentException("Employee ID is required for reimbursement event");
        }
    }

    private void handleReimbursementInitiated(ExpenseReimbursementEvent event) {
        try {
            log.info("Handling reimbursement initiation: {} for employee: {} amount: {} {} method: {}", 
                    event.getReimbursementId(), event.getEmployeeId(), event.getReimbursementAmount(), 
                    event.getCurrency(), event.getPaymentMethod());
            
            // Create reimbursement record
            expenseManagementService.createReimbursementRecord(
                event.getBusinessId(),
                event.getReimbursementId(),
                event.getExpenseId(),
                event.getEmployeeId(),
                event.getReimbursementAmount(),
                event.getCurrency(),
                event.getPaymentMethod(),
                event.getExpectedPaymentDate()
            );
            
            // Validate payment method and account details
            expenseManagementService.validatePaymentDetails(
                event.getBusinessId(),
                event.getEmployeeId(),
                event.getPaymentMethod(),
                event.getBankAccountId()
            );
            
            // Check reimbursement limits and policies
            expenseManagementService.validateReimbursementPolicies(
                event.getBusinessId(),
                event.getEmployeeId(),
                event.getReimbursementAmount(),
                event.getCurrency(),
                event.getPaymentMethod()
            );
            
            // Queue for payment processing
            expenseManagementService.queueForPaymentProcessing(
                event.getBusinessId(),
                event.getReimbursementId(),
                event.getPaymentMethod(),
                event.getExpectedPaymentDate()
            );
            
            // Send initiation confirmation
            expenseManagementService.sendReimbursementInitiationNotification(
                event.getBusinessId(),
                event.getEmployeeId(),
                event.getReimbursementId(),
                event.getReimbursementAmount(),
                event.getCurrency(),
                event.getExpectedPaymentDate()
            );
            
            log.info("Reimbursement initiation processed successfully: {}", event.getReimbursementId());
            
        } catch (Exception e) {
            log.error("Failed to handle reimbursement initiation for {}: {}", 
                    event.getReimbursementId(), e.getMessage(), e);
            throw new RuntimeException("Reimbursement initiation processing failed", e);
        }
    }

    private void handleReimbursementProcessed(ExpenseReimbursementEvent event) {
        try {
            log.info("Handling reimbursement processing: {} transaction: {} processed by: {}", 
                    event.getReimbursementId(), event.getTransactionReference(), event.getProcessedBy());
            
            // Update reimbursement status
            expenseManagementService.updateReimbursementStatus(
                event.getBusinessId(),
                event.getReimbursementId(),
                "PROCESSING",
                event.getTransactionReference(),
                event.getProcessedBy()
            );
            
            // Record transaction details
            expenseManagementService.recordPaymentTransaction(
                event.getBusinessId(),
                event.getReimbursementId(),
                event.getTransactionReference(),
                event.getProcessedBy()
            );
            
            // Update financial records
            expenseManagementService.updateFinancialLedger(
                event.getBusinessId(),
                event.getReimbursementId(),
                event.getReimbursementAmount(),
                event.getCurrency(),
                "PROCESSING"
            );
            
            // Send processing notification
            expenseManagementService.sendReimbursementProcessingNotification(
                event.getBusinessId(),
                event.getEmployeeId(),
                event.getReimbursementId(),
                event.getTransactionReference()
            );
            
            log.info("Reimbursement processing handled successfully: {}", event.getReimbursementId());
            
        } catch (Exception e) {
            log.error("Failed to handle reimbursement processing for {}: {}", 
                    event.getReimbursementId(), e.getMessage(), e);
            throw new RuntimeException("Reimbursement processing failed", e);
        }
    }

    private void handleReimbursementCompleted(ExpenseReimbursementEvent event) {
        try {
            log.info("Handling reimbursement completion: {} amount: {} {} date: {}", 
                    event.getReimbursementId(), event.getReimbursementAmount(), 
                    event.getCurrency(), event.getActualPaymentDate());
            
            // Mark reimbursement as completed
            expenseManagementService.completeReimbursement(
                event.getBusinessId(),
                event.getReimbursementId(),
                event.getReimbursementAmount(),
                event.getCurrency(),
                event.getActualPaymentDate()
            );
            
            // Update expense status to reimbursed
            if (event.getExpenseId() != null) {
                expenseManagementService.markExpenseAsReimbursed(
                    event.getBusinessId(),
                    event.getExpenseId(),
                    event.getReimbursementId(),
                    event.getActualPaymentDate()
                );
            }
            
            // Complete financial reconciliation
            expenseManagementService.reconcileReimbursementPayment(
                event.getBusinessId(),
                event.getReimbursementId(),
                event.getReimbursementAmount(),
                event.getCurrency()
            );
            
            // Send completion notification
            expenseManagementService.sendReimbursementCompletionNotification(
                event.getBusinessId(),
                event.getEmployeeId(),
                event.getReimbursementId(),
                event.getReimbursementAmount(),
                event.getCurrency(),
                event.getActualPaymentDate()
            );
            
            // Update reimbursement analytics
            expenseManagementService.updateReimbursementAnalytics(
                event.getBusinessId(),
                event.getPaymentMethod(),
                event.getReimbursementAmount(),
                event.getCurrency(),
                "COMPLETED"
            );
            
            // Archive completed reimbursement
            expenseManagementService.archiveReimbursementRecord(
                event.getBusinessId(),
                event.getReimbursementId()
            );
            
            log.info("Reimbursement completion processed successfully: {}", event.getReimbursementId());
            
        } catch (Exception e) {
            log.error("Failed to handle reimbursement completion for {}: {}", 
                    event.getReimbursementId(), e.getMessage(), e);
            throw new RuntimeException("Reimbursement completion processing failed", e);
        }
    }

    private void handleReimbursementFailed(ExpenseReimbursementEvent event) {
        try {
            log.info("Handling reimbursement failure: {} reason: {}", 
                    event.getReimbursementId(), event.getFailureReason());
            
            // Mark reimbursement as failed
            expenseManagementService.markReimbursementAsFailed(
                event.getBusinessId(),
                event.getReimbursementId(),
                event.getFailureReason()
            );
            
            // Revert expense status if needed
            if (event.getExpenseId() != null) {
                expenseManagementService.revertExpenseStatus(
                    event.getBusinessId(),
                    event.getExpenseId(),
                    "APPROVED"
                );
            }
            
            // Analyze failure reason for retry eligibility
            boolean retryEligible = expenseManagementService.analyzeFailureForRetry(
                event.getBusinessId(),
                event.getReimbursementId(),
                event.getFailureReason()
            );
            
            if (retryEligible) {
                // Schedule retry
                expenseManagementService.scheduleReimbursementRetry(
                    event.getBusinessId(),
                    event.getReimbursementId(),
                    event.getFailureReason()
                );
            } else {
                // Send failure notification
                expenseManagementService.sendReimbursementFailureNotification(
                    event.getBusinessId(),
                    event.getEmployeeId(),
                    event.getReimbursementId(),
                    event.getFailureReason()
                );
            }
            
            // Update failure analytics
            expenseManagementService.updateReimbursementAnalytics(
                event.getBusinessId(),
                event.getPaymentMethod(),
                event.getReimbursementAmount(),
                event.getCurrency(),
                "FAILED"
            );
            
            log.info("Reimbursement failure processed successfully: {}", event.getReimbursementId());
            
        } catch (Exception e) {
            log.error("Failed to handle reimbursement failure for {}: {}", 
                    event.getReimbursementId(), e.getMessage(), e);
            throw new RuntimeException("Reimbursement failure processing failed", e);
        }
    }

    private void handleReimbursementCancelled(ExpenseReimbursementEvent event) {
        try {
            log.info("Handling reimbursement cancellation: {}", event.getReimbursementId());
            
            // Cancel reimbursement
            expenseManagementService.cancelReimbursement(
                event.getBusinessId(),
                event.getReimbursementId(),
                "CANCELLED"
            );
            
            // Update expense status
            if (event.getExpenseId() != null) {
                expenseManagementService.revertExpenseStatus(
                    event.getBusinessId(),
                    event.getExpenseId(),
                    "APPROVED"
                );
            }
            
            // Send cancellation notification
            expenseManagementService.sendReimbursementCancellationNotification(
                event.getBusinessId(),
                event.getEmployeeId(),
                event.getReimbursementId()
            );
            
            log.info("Reimbursement cancellation processed successfully: {}", event.getReimbursementId());
            
        } catch (Exception e) {
            log.error("Failed to handle reimbursement cancellation for {}: {}", 
                    event.getReimbursementId(), e.getMessage(), e);
            throw new RuntimeException("Reimbursement cancellation processing failed", e);
        }
    }
}