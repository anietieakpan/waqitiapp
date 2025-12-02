package com.waqiti.business.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Business expense related events
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessExpenseEvent extends BusinessEvent {
    
    private String expenseId;
    private String employeeId;
    private String employeeName;
    private String action; // CREATED, SUBMITTED, APPROVED, REJECTED, REIMBURSED, UPDATED
    private BigDecimal amount;
    private String currency;
    private String category;
    private String description;
    private String receiptUrl;
    private LocalDateTime expenseDate;
    private String approverName;
    private String approverId;
    private String rejectionReason;
    private String projectCode;
    private String costCenter;
    private List<String> receiptAttachments;
    private String expenseStatus;
    private String paymentMethod;
    private String vendorName;
    private String location;
    private String notes;
    
    public BusinessExpenseEvent() {
        super("BUSINESS_EXPENSE");
    }
    
    public static BusinessExpenseEvent expenseCreated(String businessId, String expenseId, String employeeId, 
                                                    BigDecimal amount, String currency, String category, String description) {
        BusinessExpenseEvent event = new BusinessExpenseEvent();
        event.setBusinessId(businessId);
        event.setExpenseId(expenseId);
        event.setEmployeeId(employeeId);
        event.setAction("CREATED");
        event.setAmount(amount);
        event.setCurrency(currency);
        event.setCategory(category);
        event.setDescription(description);
        event.setExpenseStatus("DRAFT");
        return event;
    }
    
    public static BusinessExpenseEvent expenseSubmitted(String businessId, String expenseId, String employeeId, 
                                                      String receiptUrl, String projectCode) {
        BusinessExpenseEvent event = new BusinessExpenseEvent();
        event.setBusinessId(businessId);
        event.setExpenseId(expenseId);
        event.setEmployeeId(employeeId);
        event.setAction("SUBMITTED");
        event.setReceiptUrl(receiptUrl);
        event.setProjectCode(projectCode);
        event.setExpenseStatus("PENDING_APPROVAL");
        return event;
    }
    
    public static BusinessExpenseEvent expenseApproved(String businessId, String expenseId, String approverId, 
                                                     String approverName, String costCenter) {
        BusinessExpenseEvent event = new BusinessExpenseEvent();
        event.setBusinessId(businessId);
        event.setExpenseId(expenseId);
        event.setAction("APPROVED");
        event.setApproverId(approverId);
        event.setApproverName(approverName);
        event.setCostCenter(costCenter);
        event.setExpenseStatus("APPROVED");
        return event;
    }
    
    public static BusinessExpenseEvent expenseRejected(String businessId, String expenseId, String approverId, 
                                                     String rejectionReason) {
        BusinessExpenseEvent event = new BusinessExpenseEvent();
        event.setBusinessId(businessId);
        event.setExpenseId(expenseId);
        event.setAction("REJECTED");
        event.setApproverId(approverId);
        event.setRejectionReason(rejectionReason);
        event.setExpenseStatus("REJECTED");
        return event;
    }
    
    public static BusinessExpenseEvent expenseReimbursed(String businessId, String expenseId, BigDecimal amount, 
                                                       String paymentMethod) {
        BusinessExpenseEvent event = new BusinessExpenseEvent();
        event.setBusinessId(businessId);
        event.setExpenseId(expenseId);
        event.setAction("REIMBURSED");
        event.setAmount(amount);
        event.setPaymentMethod(paymentMethod);
        event.setExpenseStatus("REIMBURSED");
        return event;
    }
}