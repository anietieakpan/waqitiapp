package com.waqiti.business.consumer;

import com.waqiti.business.event.BusinessExpenseEvent;
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
 * Production-grade Kafka consumer for business expense events
 * Handles expense lifecycle, approvals, and reimbursements
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessExpenseEventConsumer {

    private final BusinessExpenseManagementService expenseManagementService;

    @KafkaListener(topics = "business-expense-events", groupId = "business-expense-processor")
    public void processBusinessExpenseEvent(@Payload BusinessExpenseEvent event,
                                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                          @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                          @Header(KafkaHeaders.OFFSET) long offset,
                                          Acknowledgment acknowledgment) {
        try {
            log.info("Processing business expense event: {} for business: {} action: {} amount: {}", 
                    event.getEventId(), event.getBusinessId(), event.getAction(), event.getAmount());
            
            // Validate event
            validateBusinessExpenseEvent(event);
            
            // Process based on action type
            switch (event.getAction()) {
                case "CREATED" -> handleExpenseCreated(event);
                case "SUBMITTED" -> handleExpenseSubmitted(event);
                case "APPROVED" -> handleExpenseApproved(event);
                case "REJECTED" -> handleExpenseRejected(event);
                case "REIMBURSED" -> handleExpenseReimbursed(event);
                case "UPDATED" -> handleExpenseUpdated(event);
                default -> {
                    log.warn("Unknown expense action: {} for event: {}", event.getAction(), event.getEventId());
                    // Don't throw exception for unknown actions to avoid DLQ
                }
            }
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed business expense event: {} action: {}", 
                    event.getEventId(), event.getAction());
            
        } catch (Exception e) {
            log.error("Failed to process business expense event: {} error: {}", 
                    event.getEventId(), e.getMessage(), e);
            
            // Don't acknowledge - let retry mechanism handle it
            throw new RuntimeException("Business expense event processing failed", e);
        }
    }

    private void validateBusinessExpenseEvent(BusinessExpenseEvent event) {
        if (event.getBusinessId() == null || event.getBusinessId().trim().isEmpty()) {
            throw new IllegalArgumentException("Business ID is required for expense event");
        }
        
        if (event.getExpenseId() == null || event.getExpenseId().trim().isEmpty()) {
            throw new IllegalArgumentException("Expense ID is required for expense event");
        }
        
        if (event.getAction() == null || event.getAction().trim().isEmpty()) {
            throw new IllegalArgumentException("Action is required for expense event");
        }
        
        if (event.getEmployeeId() == null || event.getEmployeeId().trim().isEmpty()) {
            throw new IllegalArgumentException("Employee ID is required for expense event");
        }
    }

    private void handleExpenseCreated(BusinessExpenseEvent event) {
        try {
            log.info("Handling expense creation: {} for employee: {} amount: {} {}", 
                    event.getExpenseId(), event.getEmployeeId(), event.getAmount(), event.getCurrency());
            
            // Create expense record
            expenseManagementService.createExpenseRecord(
                event.getBusinessId(),
                event.getExpenseId(),
                event.getEmployeeId(),
                event.getAmount(),
                event.getCurrency(),
                event.getCategory(),
                event.getDescription(),
                event.getExpenseDate()
            );
            
            // Update employee expense tracking
            expenseManagementService.updateEmployeeExpenseTracking(
                event.getBusinessId(),
                event.getEmployeeId(),
                event.getAmount(),
                event.getCurrency()
            );
            
            // Check against budget limits
            expenseManagementService.checkBudgetLimits(
                event.getBusinessId(),
                event.getCategory(),
                event.getAmount(),
                event.getCurrency()
            );
            
            log.info("Expense creation processed successfully: {}", event.getExpenseId());
            
        } catch (Exception e) {
            log.error("Failed to handle expense creation for {}: {}", event.getExpenseId(), e.getMessage(), e);
            throw new RuntimeException("Expense creation processing failed", e);
        }
    }

    private void handleExpenseSubmitted(BusinessExpenseEvent event) {
        try {
            log.info("Handling expense submission: {} for approval project: {}", 
                    event.getExpenseId(), event.getProjectCode());
            
            // Update expense status to submitted
            expenseManagementService.submitExpenseForApproval(
                event.getBusinessId(),
                event.getExpenseId(),
                event.getReceiptUrl(),
                event.getProjectCode(),
                event.getCostCenter()
            );
            
            // Trigger approval workflow
            expenseManagementService.initiateApprovalWorkflow(
                event.getBusinessId(),
                event.getExpenseId(),
                event.getAmount(),
                event.getCurrency(),
                event.getCategory()
            );
            
            // Send notification to approver
            expenseManagementService.notifyApprover(
                event.getBusinessId(),
                event.getExpenseId(),
                event.getEmployeeId(),
                event.getAmount(),
                event.getCurrency()
            );
            
            // Update compliance tracking
            expenseManagementService.updateComplianceTracking(
                event.getBusinessId(),
                event.getExpenseId(),
                event.getCategory(),
                event.getAmount()
            );
            
            log.info("Expense submission processed successfully: {}", event.getExpenseId());
            
        } catch (Exception e) {
            log.error("Failed to handle expense submission for {}: {}", event.getExpenseId(), e.getMessage(), e);
            throw new RuntimeException("Expense submission processing failed", e);
        }
    }

    private void handleExpenseApproved(BusinessExpenseEvent event) {
        try {
            log.info("Handling expense approval: {} by approver: {} cost center: {}", 
                    event.getExpenseId(), event.getApproverName(), event.getCostCenter());
            
            // Update expense status to approved
            expenseManagementService.approveExpense(
                event.getBusinessId(),
                event.getExpenseId(),
                event.getApproverId(),
                event.getApproverName(),
                event.getCostCenter()
            );
            
            // Initiate reimbursement process
            expenseManagementService.initiateReimbursementProcess(
                event.getBusinessId(),
                event.getExpenseId(),
                event.getEmployeeId()
            );
            
            // Update budget tracking
            expenseManagementService.updateBudgetTracking(
                event.getBusinessId(),
                event.getCostCenter(),
                event.getCategory(),
                event.getAmount(),
                event.getCurrency()
            );
            
            // Send approval notification to employee
            expenseManagementService.sendApprovalNotification(
                event.getBusinessId(),
                event.getExpenseId(),
                event.getEmployeeId(),
                event.getApproverName()
            );
            
            // Update expense analytics
            expenseManagementService.updateExpenseAnalytics(
                event.getBusinessId(),
                event.getCategory(),
                event.getAmount(),
                event.getCurrency(),
                "APPROVED"
            );
            
            log.info("Expense approval processed successfully: {}", event.getExpenseId());
            
        } catch (Exception e) {
            log.error("Failed to handle expense approval for {}: {}", event.getExpenseId(), e.getMessage(), e);
            throw new RuntimeException("Expense approval processing failed", e);
        }
    }

    private void handleExpenseRejected(BusinessExpenseEvent event) {
        try {
            log.info("Handling expense rejection: {} by approver: {} reason: {}", 
                    event.getExpenseId(), event.getApproverId(), event.getRejectionReason());
            
            // Update expense status to rejected
            expenseManagementService.rejectExpense(
                event.getBusinessId(),
                event.getExpenseId(),
                event.getApproverId(),
                event.getRejectionReason()
            );
            
            // Send rejection notification to employee
            expenseManagementService.sendRejectionNotification(
                event.getBusinessId(),
                event.getExpenseId(),
                event.getEmployeeId(),
                event.getRejectionReason()
            );
            
            // Update expense analytics
            expenseManagementService.updateExpenseAnalytics(
                event.getBusinessId(),
                event.getCategory(),
                event.getAmount(),
                event.getCurrency(),
                "REJECTED"
            );
            
            // Check if rejection rate is concerning
            expenseManagementService.analyzeRejectionPatterns(
                event.getBusinessId(),
                event.getEmployeeId(),
                event.getCategory()
            );
            
            log.info("Expense rejection processed successfully: {}", event.getExpenseId());
            
        } catch (Exception e) {
            log.error("Failed to handle expense rejection for {}: {}", event.getExpenseId(), e.getMessage(), e);
            throw new RuntimeException("Expense rejection processing failed", e);
        }
    }

    private void handleExpenseReimbursed(BusinessExpenseEvent event) {
        try {
            log.info("Handling expense reimbursement: {} amount: {} {} method: {}", 
                    event.getExpenseId(), event.getAmount(), event.getCurrency(), event.getPaymentMethod());
            
            // Update expense status to reimbursed
            expenseManagementService.completeReimbursement(
                event.getBusinessId(),
                event.getExpenseId(),
                event.getAmount(),
                event.getCurrency(),
                event.getPaymentMethod()
            );
            
            // Send reimbursement confirmation to employee
            expenseManagementService.sendReimbursementConfirmation(
                event.getBusinessId(),
                event.getExpenseId(),
                event.getEmployeeId(),
                event.getAmount(),
                event.getCurrency()
            );
            
            // Update financial records
            expenseManagementService.updateFinancialRecords(
                event.getBusinessId(),
                event.getExpenseId(),
                event.getAmount(),
                event.getCurrency(),
                event.getPaymentMethod()
            );
            
            // Close expense workflow
            expenseManagementService.closeExpenseWorkflow(
                event.getBusinessId(),
                event.getExpenseId()
            );
            
            log.info("Expense reimbursement processed successfully: {}", event.getExpenseId());
            
        } catch (Exception e) {
            log.error("Failed to handle expense reimbursement for {}: {}", event.getExpenseId(), e.getMessage(), e);
            throw new RuntimeException("Expense reimbursement processing failed", e);
        }
    }

    private void handleExpenseUpdated(BusinessExpenseEvent event) {
        try {
            log.info("Handling expense update: {} category: {} amount: {}", 
                    event.getExpenseId(), event.getCategory(), event.getAmount());
            
            // Update expense details
            expenseManagementService.updateExpenseDetails(
                event.getBusinessId(),
                event.getExpenseId(),
                event.getAmount(),
                event.getCurrency(),
                event.getCategory(),
                event.getDescription(),
                event.getVendorName(),
                event.getLocation()
            );
            
            // Re-validate against policies if amount changed
            if (event.getAmount() != null) {
                expenseManagementService.revalidateExpensePolicies(
                    event.getBusinessId(),
                    event.getExpenseId(),
                    event.getAmount(),
                    event.getCategory()
                );
            }
            
            // Update audit trail
            expenseManagementService.updateExpenseAuditTrail(
                event.getBusinessId(),
                event.getExpenseId(),
                "UPDATED",
                event.getNotes()
            );
            
            log.info("Expense update processed successfully: {}", event.getExpenseId());
            
        } catch (Exception e) {
            log.error("Failed to handle expense update for {}: {}", event.getExpenseId(), e.getMessage(), e);
            throw new RuntimeException("Expense update processing failed", e);
        }
    }
}