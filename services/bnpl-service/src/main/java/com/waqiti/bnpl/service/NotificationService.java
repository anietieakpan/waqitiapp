package com.waqiti.bnpl.service;

import com.waqiti.bnpl.domain.BnplInstallment;
import com.waqiti.bnpl.domain.BnplPlan;
import com.waqiti.bnpl.domain.BnplTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for sending BNPL notifications
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String NOTIFICATION_TOPIC = "bnpl-notifications";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    
    /**
     * Send notification when BNPL plan is created
     */
    public void sendPlanCreatedNotification(BnplPlan plan) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "BNPL_PLAN_CREATED");
        notification.put("userId", plan.getUserId());
        notification.put("title", "BNPL Plan Created");
        notification.put("message", String.format(
                "Your BNPL plan for %s has been created. Amount: $%.2f in %d installments.",
                plan.getMerchantName(),
                plan.getTotalAmount(),
                plan.getNumberOfInstallments()
        ));
        notification.put("planId", plan.getId());
        notification.put("planNumber", plan.getPlanNumber());
        notification.put("timestamp", LocalDateTime.now());
        
        sendNotification(notification);
    }

    /**
     * Send notification when BNPL plan is approved
     */
    public void sendPlanApprovedNotification(BnplPlan plan) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "BNPL_PLAN_APPROVED");
        notification.put("userId", plan.getUserId());
        notification.put("title", "BNPL Plan Approved");
        notification.put("message", String.format(
                "Your BNPL plan %s has been approved! First payment of $%.2f is due on %s.",
                plan.getPlanNumber(),
                plan.getInstallmentAmount(),
                plan.getFirstPaymentDate().format(DATE_FORMATTER)
        ));
        notification.put("planId", plan.getId());
        notification.put("timestamp", LocalDateTime.now());
        
        sendNotification(notification);
    }

    /**
     * Send notification when BNPL plan is activated
     */
    public void sendPlanActivatedNotification(BnplPlan plan) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "BNPL_PLAN_ACTIVATED");
        notification.put("userId", plan.getUserId());
        notification.put("title", "BNPL Plan Activated");
        notification.put("message", String.format(
                "Your BNPL plan %s is now active. Track your payments in the app.",
                plan.getPlanNumber()
        ));
        notification.put("planId", plan.getId());
        notification.put("timestamp", LocalDateTime.now());
        
        sendNotification(notification);
    }

    /**
     * Send notification when BNPL plan is cancelled
     */
    public void sendPlanCancelledNotification(BnplPlan plan) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "BNPL_PLAN_CANCELLED");
        notification.put("userId", plan.getUserId());
        notification.put("title", "BNPL Plan Cancelled");
        notification.put("message", String.format(
                "Your BNPL plan %s has been cancelled. Reason: %s",
                plan.getPlanNumber(),
                plan.getCancellationReason()
        ));
        notification.put("planId", plan.getId());
        notification.put("timestamp", LocalDateTime.now());
        
        sendNotification(notification);
    }

    /**
     * Send notification for successful payment
     */
    public void sendPaymentSuccessNotification(BnplPlan plan, BnplTransaction transaction) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "BNPL_PAYMENT_SUCCESS");
        notification.put("userId", plan.getUserId());
        notification.put("title", "Payment Successful");
        notification.put("message", String.format(
                "Payment of $%.2f for BNPL plan %s was successful. Remaining balance: $%.2f",
                transaction.getAmount(),
                plan.getPlanNumber(),
                plan.getRemainingBalance()
        ));
        notification.put("planId", plan.getId());
        notification.put("transactionId", transaction.getTransactionId());
        notification.put("timestamp", LocalDateTime.now());
        
        sendNotification(notification);
    }

    /**
     * Send payment reminder notification
     */
    public void sendPaymentReminderNotification(BnplPlan plan, BnplInstallment installment) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "BNPL_PAYMENT_REMINDER");
        notification.put("userId", plan.getUserId());
        notification.put("title", "Payment Reminder");
        notification.put("message", String.format(
                "Payment of $%.2f for BNPL plan %s is due on %s. Pay now to avoid late fees.",
                installment.getAmountDue(),
                plan.getPlanNumber(),
                installment.getDueDate().format(DATE_FORMATTER)
        ));
        notification.put("planId", plan.getId());
        notification.put("installmentId", installment.getId());
        notification.put("dueDate", installment.getDueDate());
        notification.put("timestamp", LocalDateTime.now());
        
        sendNotification(notification);
    }

    /**
     * Send overdue notification
     */
    public void sendOverdueNotification(BnplPlan plan, BnplInstallment installment) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "BNPL_PAYMENT_OVERDUE");
        notification.put("userId", plan.getUserId());
        notification.put("title", "Payment Overdue");
        notification.put("message", String.format(
                "Your payment of $%.2f for BNPL plan %s is %d days overdue. Late fees may apply.",
                installment.getAmountDue(),
                plan.getPlanNumber(),
                installment.getDaysOverdue()
        ));
        notification.put("planId", plan.getId());
        notification.put("installmentId", installment.getId());
        notification.put("daysOverdue", installment.getDaysOverdue());
        notification.put("timestamp", LocalDateTime.now());
        notification.put("priority", "HIGH");
        
        sendNotification(notification);
    }

    /**
     * Send late fee notification
     */
    public void sendLateFeeNotification(BnplPlan plan, BnplInstallment installment, BigDecimal lateFee) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "BNPL_LATE_FEE_APPLIED");
        notification.put("userId", plan.getUserId());
        notification.put("title", "Late Fee Applied");
        notification.put("message", String.format(
                "A late fee of $%.2f has been applied to your BNPL plan %s. Total due: $%.2f",
                lateFee,
                plan.getPlanNumber(),
                installment.getAmountDue()
        ));
        notification.put("planId", plan.getId());
        notification.put("installmentId", installment.getId());
        notification.put("lateFee", lateFee);
        notification.put("timestamp", LocalDateTime.now());
        
        sendNotification(notification);
    }

    /**
     * Send plan completion notification
     */
    public void sendPlanCompletedNotification(BnplPlan plan) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "BNPL_PLAN_COMPLETED");
        notification.put("userId", plan.getUserId());
        notification.put("title", "BNPL Plan Completed! 🎉");
        notification.put("message", String.format(
                "Congratulations! You've completed all payments for BNPL plan %s. Total paid: $%.2f",
                plan.getPlanNumber(),
                plan.getTotalPaid()
        ));
        notification.put("planId", plan.getId());
        notification.put("timestamp", LocalDateTime.now());
        
        sendNotification(notification);
    }

    /**
     * Send application status notification
     */
    public void sendApplicationStatusNotification(com.waqiti.bnpl.entity.BnplApplication application) {
        Map<String, Object> notification = new HashMap<>();
        
        switch (application.getStatus()) {
            case APPROVED -> {
                notification.put("type", "BNPL_APPLICATION_APPROVED");
                notification.put("title", "BNPL Application Approved");
                notification.put("message", String.format(
                        "Your BNPL application %s has been approved for $%.2f!",
                        application.getApplicationNumber(),
                        application.getFinancedAmount()
                ));
            }
            case REJECTED -> {
                notification.put("type", "BNPL_APPLICATION_REJECTED");
                notification.put("title", "BNPL Application Declined");
                notification.put("message", String.format(
                        "Your BNPL application %s has been declined. %s",
                        application.getApplicationNumber(),
                        application.getDecisionReason() != null ? application.getDecisionReason() : ""
                ));
            }
            case PENDING -> {
                notification.put("type", "BNPL_APPLICATION_SUBMITTED");
                notification.put("title", "BNPL Application Submitted");
                notification.put("message", String.format(
                        "Your BNPL application %s has been submitted and is under review.",
                        application.getApplicationNumber()
                ));
            }
            case CANCELLED -> {
                notification.put("type", "BNPL_APPLICATION_CANCELLED");
                notification.put("title", "BNPL Application Cancelled");
                notification.put("message", String.format(
                        "Your BNPL application %s has been cancelled.",
                        application.getApplicationNumber()
                ));
            }
            default -> {
                notification.put("type", "BNPL_APPLICATION_STATUS_UPDATE");
                notification.put("title", "BNPL Application Update");
                notification.put("message", String.format(
                        "Status update for BNPL application %s: %s",
                        application.getApplicationNumber(),
                        application.getStatus()
                ));
            }
        }
        
        notification.put("userId", application.getUserId());
        notification.put("applicationId", application.getId());
        notification.put("applicationNumber", application.getApplicationNumber());
        notification.put("status", application.getStatus().toString());
        notification.put("timestamp", LocalDateTime.now());
        
        sendNotification(notification);
    }

    /**
     * Send notification to Kafka topic
     */
    private void sendNotification(Map<String, Object> notification) {
        try {
            kafkaTemplate.send(NOTIFICATION_TOPIC, notification);
            log.info("Notification sent: {} for user: {}", 
                    notification.get("type"), 
                    notification.get("userId"));
        } catch (Exception e) {
            log.error("Failed to send notification", e);
        }
    }
}