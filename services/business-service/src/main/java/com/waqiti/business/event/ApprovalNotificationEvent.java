package com.waqiti.business.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Approval notification events
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApprovalNotificationEvent extends BusinessEvent {
    
    private String approvalId;
    private String requestId;
    private String requestType; // EXPENSE, BUDGET_INCREASE, VENDOR_PAYMENT, CONTRACT, PURCHASE_ORDER
    private String requestTitle;
    private String requestDescription;
    private String requesterName;
    private String requesterId;
    private String requesterEmail;
    private String approverName;
    private String approverId;
    private String approverEmail;
    private String action; // APPROVAL_REQUIRED, APPROVED, REJECTED, ESCALATED, DELEGATED
    private BigDecimal amount;
    private String currency;
    private String priority; // LOW, MEDIUM, HIGH, URGENT
    private LocalDateTime dueDate;
    private String urgencyReason;
    private String documentUrl;
    private String approvalWorkflowId;
    private int approvalLevel;
    private String rejectionReason;
    private String delegatedTo;
    private String escalatedTo;
    private String comments;
    
    public ApprovalNotificationEvent() {
        super("APPROVAL_NOTIFICATION");
    }
    
    public static ApprovalNotificationEvent approvalRequired(String businessId, String approvalId, String requestType, 
                                                           String requestTitle, String requesterName, String requesterId, 
                                                           String approverEmail, BigDecimal amount, String currency, 
                                                           LocalDateTime dueDate, String priority) {
        ApprovalNotificationEvent event = new ApprovalNotificationEvent();
        event.setBusinessId(businessId);
        event.setApprovalId(approvalId);
        event.setRequestType(requestType);
        event.setRequestTitle(requestTitle);
        event.setRequesterName(requesterName);
        event.setRequesterId(requesterId);
        event.setApproverEmail(approverEmail);
        event.setAction("APPROVAL_REQUIRED");
        event.setAmount(amount);
        event.setCurrency(currency);
        event.setDueDate(dueDate);
        event.setPriority(priority);
        return event;
    }
    
    public static ApprovalNotificationEvent requestApproved(String businessId, String approvalId, String requestType, 
                                                          String approverName, String approverId, String requesterEmail, 
                                                          BigDecimal amount, String comments) {
        ApprovalNotificationEvent event = new ApprovalNotificationEvent();
        event.setBusinessId(businessId);
        event.setApprovalId(approvalId);
        event.setRequestType(requestType);
        event.setApproverName(approverName);
        event.setApproverId(approverId);
        event.setRequesterEmail(requesterEmail);
        event.setAction("APPROVED");
        event.setAmount(amount);
        event.setComments(comments);
        return event;
    }
    
    public static ApprovalNotificationEvent requestRejected(String businessId, String approvalId, String requestType, 
                                                          String approverName, String approverId, String requesterEmail, 
                                                          String rejectionReason) {
        ApprovalNotificationEvent event = new ApprovalNotificationEvent();
        event.setBusinessId(businessId);
        event.setApprovalId(approvalId);
        event.setRequestType(requestType);
        event.setApproverName(approverName);
        event.setApproverId(approverId);
        event.setRequesterEmail(requesterEmail);
        event.setAction("REJECTED");
        event.setRejectionReason(rejectionReason);
        return event;
    }
    
    public static ApprovalNotificationEvent requestEscalated(String businessId, String approvalId, String requestType, 
                                                           String escalatedTo, String reason, BigDecimal amount, 
                                                           String urgencyReason) {
        ApprovalNotificationEvent event = new ApprovalNotificationEvent();
        event.setBusinessId(businessId);
        event.setApprovalId(approvalId);
        event.setRequestType(requestType);
        event.setEscalatedTo(escalatedTo);
        event.setAction("ESCALATED");
        event.setAmount(amount);
        event.setUrgencyReason(urgencyReason);
        event.setComments(reason);
        event.setPriority("URGENT");
        return event;
    }
    
    public static ApprovalNotificationEvent requestDelegated(String businessId, String approvalId, String requestType, 
                                                           String originalApprover, String delegatedTo, String reason) {
        ApprovalNotificationEvent event = new ApprovalNotificationEvent();
        event.setBusinessId(businessId);
        event.setApprovalId(approvalId);
        event.setRequestType(requestType);
        event.setApproverName(originalApprover);
        event.setDelegatedTo(delegatedTo);
        event.setAction("DELEGATED");
        event.setComments(reason);
        return event;
    }
    
    public static ApprovalNotificationEvent urgentExpenseApproval(String businessId, String expenseId, String employeeName, 
                                                                String approverEmail, BigDecimal amount, String currency, 
                                                                String urgencyReason) {
        ApprovalNotificationEvent event = new ApprovalNotificationEvent();
        event.setBusinessId(businessId);
        event.setRequestId(expenseId);
        event.setRequestType("EXPENSE");
        event.setRequestTitle("Urgent Expense Approval");
        event.setRequesterName(employeeName);
        event.setApproverEmail(approverEmail);
        event.setAction("APPROVAL_REQUIRED");
        event.setAmount(amount);
        event.setCurrency(currency);
        event.setPriority("URGENT");
        event.setUrgencyReason(urgencyReason);
        event.setDueDate(LocalDateTime.now().plusHours(4)); // 4 hour SLA for urgent
        return event;
    }
}