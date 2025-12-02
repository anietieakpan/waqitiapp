package com.waqiti.business.consumer;

import com.waqiti.business.event.ExpenseNotificationEvent;
import com.waqiti.business.service.BusinessNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Production-grade Kafka consumer for expense notification events
 * Handles expense-related notifications and alerts
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpenseNotificationConsumer {

    private final BusinessNotificationService notificationService;

    @KafkaListener(topics = "expense-notifications", groupId = "expense-notification-processor")
    public void processExpenseNotification(@Payload ExpenseNotificationEvent event,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                         @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                         @Header(KafkaHeaders.OFFSET) long offset,
                                         Acknowledgment acknowledgment) {
        try {
            log.info("Processing expense notification: {} type: {} recipient: {} expense: {}", 
                    event.getEventId(), event.getNotificationType(), event.getRecipientEmail(), event.getExpenseId());
            
            // Validate event
            validateExpenseNotificationEvent(event);
            
            // Process based on notification type
            switch (event.getNotificationType()) {
                case "APPROVAL_REQUIRED" -> handleApprovalRequired(event);
                case "APPROVED" -> handleExpenseApproved(event);
                case "REJECTED" -> handleExpenseRejected(event);
                case "OVERDUE" -> handleExpenseOverdue(event);
                case "REMINDER" -> handleExpenseReminder(event);
                default -> {
                    log.warn("Unknown notification type: {} for event: {}", event.getNotificationType(), event.getEventId());
                    // Don't throw exception for unknown types to avoid DLQ
                }
            }
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed expense notification: {} type: {}", 
                    event.getEventId(), event.getNotificationType());
            
        } catch (Exception e) {
            log.error("Failed to process expense notification: {} error: {}", 
                    event.getEventId(), e.getMessage(), e);
            
            // Don't acknowledge - let retry mechanism handle it
            throw new RuntimeException("Expense notification processing failed", e);
        }
    }

    private void validateExpenseNotificationEvent(ExpenseNotificationEvent event) {
        if (event.getBusinessId() == null || event.getBusinessId().trim().isEmpty()) {
            throw new IllegalArgumentException("Business ID is required for expense notification");
        }
        
        if (event.getExpenseId() == null || event.getExpenseId().trim().isEmpty()) {
            throw new IllegalArgumentException("Expense ID is required for expense notification");
        }
        
        if (event.getNotificationType() == null || event.getNotificationType().trim().isEmpty()) {
            throw new IllegalArgumentException("Notification type is required for expense notification");
        }
        
        if (event.getRecipientEmail() == null || event.getRecipientEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Recipient email is required for expense notification");
        }
    }

    private void handleApprovalRequired(ExpenseNotificationEvent event) {
        try {
            log.info("Handling approval required notification: {} for expense: {} amount: {} {}", 
                    event.getEventId(), event.getExpenseId(), event.getAmount(), event.getCurrency());
            
            // Send approval request email
            notificationService.sendApprovalRequestEmail(
                event.getBusinessId(),
                event.getRecipientEmail(),
                event.getMessageTitle(),
                event.getMessageBody(),
                event.getExpenseId(),
                event.getAmount(),
                event.getCurrency(),
                event.getEmployeeName(),
                event.getDueDate(),
                event.getActionUrl()
            );
            
            // Send in-app notification if enabled
            notificationService.sendInAppNotification(
                event.getBusinessId(),
                event.getRecipientId(),
                "EXPENSE_APPROVAL",
                event.getMessageTitle(),
                event.getMessageBody(),
                event.getPriority(),
                event.getExpenseId()
            );
            
            // Schedule reminder if due date exists
            if (event.getDueDate() != null) {
                notificationService.scheduleApprovalReminder(
                    event.getBusinessId(),
                    event.getExpenseId(),
                    event.getRecipientEmail(),
                    event.getDueDate(),
                    event.getAmount(),
                    event.getEmployeeName()
                );
            }
            
            // Track notification delivery
            notificationService.trackNotificationDelivery(
                event.getBusinessId(),
                event.getEventId(),
                "APPROVAL_REQUIRED",
                event.getRecipientEmail(),
                "SENT"
            );
            
            log.info("Approval required notification processed successfully: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to handle approval required notification for {}: {}", 
                    event.getExpenseId(), e.getMessage(), e);
            throw new RuntimeException("Approval required notification processing failed", e);
        }
    }

    private void handleExpenseApproved(ExpenseNotificationEvent event) {
        try {
            log.info("Handling expense approved notification: {} for expense: {} approver: {}", 
                    event.getEventId(), event.getExpenseId(), event.getApproverName());
            
            // Send approval confirmation email
            notificationService.sendApprovalConfirmationEmail(
                event.getBusinessId(),
                event.getRecipientEmail(),
                event.getMessageTitle(),
                event.getMessageBody(),
                event.getExpenseId(),
                event.getAmount(),
                event.getApproverName()
            );
            
            // Send SMS notification if urgent
            if ("HIGH".equals(event.getPriority()) || "URGENT".equals(event.getPriority())) {
                notificationService.sendSMSNotification(
                    event.getBusinessId(),
                    event.getRecipientId(),
                    "Your expense has been approved and will be processed for reimbursement."
                );
            }
            
            // Cancel any pending reminders for this expense
            notificationService.cancelPendingReminders(
                event.getBusinessId(),
                event.getExpenseId()
            );
            
            // Track notification delivery
            notificationService.trackNotificationDelivery(
                event.getBusinessId(),
                event.getEventId(),
                "APPROVED",
                event.getRecipientEmail(),
                "SENT"
            );
            
            log.info("Expense approved notification processed successfully: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to handle expense approved notification for {}: {}", 
                    event.getExpenseId(), e.getMessage(), e);
            throw new RuntimeException("Expense approved notification processing failed", e);
        }
    }

    private void handleExpenseRejected(ExpenseNotificationEvent event) {
        try {
            log.info("Handling expense rejected notification: {} for expense: {} reason: {}", 
                    event.getEventId(), event.getExpenseId(), event.getReason());
            
            // Send rejection notification email
            notificationService.sendRejectionNotificationEmail(
                event.getBusinessId(),
                event.getRecipientEmail(),
                event.getMessageTitle(),
                event.getMessageBody(),
                event.getExpenseId(),
                event.getAmount(),
                event.getReason()
            );
            
            // Send in-app notification
            notificationService.sendInAppNotification(
                event.getBusinessId(),
                event.getRecipientId(),
                "EXPENSE_REJECTION",
                event.getMessageTitle(),
                event.getMessageBody(),
                "MEDIUM",
                event.getExpenseId()
            );
            
            // Send push notification for mobile users
            notificationService.sendPushNotification(
                event.getBusinessId(),
                event.getRecipientId(),
                "Expense Rejected",
                String.format("Your expense of %s has been rejected", event.getAmount()),
                event.getExpenseId()
            );
            
            // Cancel any pending reminders for this expense
            notificationService.cancelPendingReminders(
                event.getBusinessId(),
                event.getExpenseId()
            );
            
            // Track notification delivery
            notificationService.trackNotificationDelivery(
                event.getBusinessId(),
                event.getEventId(),
                "REJECTED",
                event.getRecipientEmail(),
                "SENT"
            );
            
            log.info("Expense rejected notification processed successfully: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to handle expense rejected notification for {}: {}", 
                    event.getExpenseId(), e.getMessage(), e);
            throw new RuntimeException("Expense rejected notification processing failed", e);
        }
    }

    private void handleExpenseOverdue(ExpenseNotificationEvent event) {
        try {
            log.info("Handling expense overdue notification: {} for expense: {} due: {}", 
                    event.getEventId(), event.getExpenseId(), event.getDueDate());
            
            // Send overdue notification email
            notificationService.sendOverdueNotificationEmail(
                event.getBusinessId(),
                event.getRecipientEmail(),
                event.getMessageTitle(),
                event.getMessageBody(),
                event.getExpenseId(),
                event.getAmount(),
                event.getEmployeeName(),
                event.getDueDate()
            );
            
            // Send urgent in-app notification
            notificationService.sendInAppNotification(
                event.getBusinessId(),
                event.getRecipientId(),
                "EXPENSE_OVERDUE",
                event.getMessageTitle(),
                event.getMessageBody(),
                "HIGH",
                event.getExpenseId()
            );
            
            // Escalate if severely overdue
            notificationService.checkEscalationRequirement(
                event.getBusinessId(),
                event.getExpenseId(),
                event.getDueDate(),
                event.getAmount()
            );
            
            // Schedule follow-up reminder
            notificationService.scheduleFollowUpReminder(
                event.getBusinessId(),
                event.getExpenseId(),
                event.getRecipientEmail(),
                event.getAmount(),
                event.getEmployeeName()
            );
            
            // Track notification delivery
            notificationService.trackNotificationDelivery(
                event.getBusinessId(),
                event.getEventId(),
                "OVERDUE",
                event.getRecipientEmail(),
                "SENT"
            );
            
            log.info("Expense overdue notification processed successfully: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to handle expense overdue notification for {}: {}", 
                    event.getExpenseId(), e.getMessage(), e);
            throw new RuntimeException("Expense overdue notification processing failed", e);
        }
    }

    private void handleExpenseReminder(ExpenseNotificationEvent event) {
        try {
            log.info("Handling expense reminder notification: {} for expense: {}", 
                    event.getEventId(), event.getExpenseId());
            
            // Send reminder email
            notificationService.sendReminderEmail(
                event.getBusinessId(),
                event.getRecipientEmail(),
                event.getMessageTitle(),
                event.getMessageBody(),
                event.getExpenseId(),
                event.getAmount(),
                event.getEmployeeName(),
                event.getDueDate()
            );
            
            // Send in-app reminder
            notificationService.sendInAppNotification(
                event.getBusinessId(),
                event.getRecipientId(),
                "EXPENSE_REMINDER",
                event.getMessageTitle(),
                event.getMessageBody(),
                event.getPriority(),
                event.getExpenseId()
            );
            
            // Update reminder tracking
            notificationService.updateReminderTracking(
                event.getBusinessId(),
                event.getExpenseId(),
                event.getRecipientEmail()
            );
            
            // Track notification delivery
            notificationService.trackNotificationDelivery(
                event.getBusinessId(),
                event.getEventId(),
                "REMINDER",
                event.getRecipientEmail(),
                "SENT"
            );
            
            log.info("Expense reminder notification processed successfully: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to handle expense reminder notification for {}: {}", 
                    event.getExpenseId(), e.getMessage(), e);
            throw new RuntimeException("Expense reminder notification processing failed", e);
        }
    }
}