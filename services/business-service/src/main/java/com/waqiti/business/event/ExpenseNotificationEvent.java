package com.waqiti.business.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Expense notification events
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExpenseNotificationEvent extends BusinessEvent {
    
    private String expenseId;
    private String recipientId;
    private String recipientEmail;
    private String notificationType; // APPROVAL_REQUIRED, APPROVED, REJECTED, OVERDUE, REMINDER
    private String messageTitle;
    private String messageBody;
    private BigDecimal amount;
    private String currency;
    private String category;
    private String employeeName;
    private String approverName;
    private LocalDateTime dueDate;
    private String priority; // LOW, MEDIUM, HIGH, URGENT
    private String channel; // EMAIL, SMS, PUSH, IN_APP
    private String templateId;
    private String actionUrl;
    private String reason;
    
    public ExpenseNotificationEvent() {
        super("EXPENSE_NOTIFICATION");
    }
    
    public static ExpenseNotificationEvent approvalRequired(String businessId, String expenseId, String recipientId, 
                                                          String recipientEmail, BigDecimal amount, String currency, 
                                                          String employeeName, LocalDateTime dueDate) {
        ExpenseNotificationEvent event = new ExpenseNotificationEvent();
        event.setBusinessId(businessId);
        event.setExpenseId(expenseId);
        event.setRecipientId(recipientId);
        event.setRecipientEmail(recipientEmail);
        event.setNotificationType("APPROVAL_REQUIRED");
        event.setMessageTitle("Expense Approval Required");
        event.setMessageBody(String.format("Expense of %s %s from %s requires your approval", 
                           amount, currency, employeeName));
        event.setAmount(amount);
        event.setCurrency(currency);
        event.setEmployeeName(employeeName);
        event.setDueDate(dueDate);
        event.setPriority("MEDIUM");
        event.setChannel("EMAIL");
        return event;
    }
    
    public static ExpenseNotificationEvent expenseApproved(String businessId, String expenseId, String employeeId, 
                                                         String employeeEmail, BigDecimal amount, String approverName) {
        ExpenseNotificationEvent event = new ExpenseNotificationEvent();
        event.setBusinessId(businessId);
        event.setExpenseId(expenseId);
        event.setRecipientId(employeeId);
        event.setRecipientEmail(employeeEmail);
        event.setNotificationType("APPROVED");
        event.setMessageTitle("Expense Approved");
        event.setMessageBody(String.format("Your expense of %s has been approved by %s", amount, approverName));
        event.setAmount(amount);
        event.setApproverName(approverName);
        event.setPriority("LOW");
        event.setChannel("EMAIL");
        return event;
    }
    
    public static ExpenseNotificationEvent expenseRejected(String businessId, String expenseId, String employeeId, 
                                                         String employeeEmail, BigDecimal amount, String reason) {
        ExpenseNotificationEvent event = new ExpenseNotificationEvent();
        event.setBusinessId(businessId);
        event.setExpenseId(expenseId);
        event.setRecipientId(employeeId);
        event.setRecipientEmail(employeeEmail);
        event.setNotificationType("REJECTED");
        event.setMessageTitle("Expense Rejected");
        event.setMessageBody(String.format("Your expense of %s has been rejected. Reason: %s", amount, reason));
        event.setAmount(amount);
        event.setReason(reason);
        event.setPriority("MEDIUM");
        event.setChannel("EMAIL");
        return event;
    }
    
    public static ExpenseNotificationEvent expenseOverdue(String businessId, String expenseId, String approverEmail, 
                                                        BigDecimal amount, String employeeName, LocalDateTime originalDue) {
        ExpenseNotificationEvent event = new ExpenseNotificationEvent();
        event.setBusinessId(businessId);
        event.setExpenseId(expenseId);
        event.setRecipientEmail(approverEmail);
        event.setNotificationType("OVERDUE");
        event.setMessageTitle("Overdue Expense Approval");
        event.setMessageBody(String.format("Expense of %s from %s is overdue for approval since %s", 
                           amount, employeeName, originalDue));
        event.setAmount(amount);
        event.setEmployeeName(employeeName);
        event.setDueDate(originalDue);
        event.setPriority("HIGH");
        event.setChannel("EMAIL");
        return event;
    }
}