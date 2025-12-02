package com.waqiti.business.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Expense reimbursement events
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExpenseReimbursementEvent extends BusinessEvent {
    
    private String reimbursementId;
    private String expenseId;
    private String employeeId;
    private String employeeName;
    private String employeeEmail;
    private String action; // INITIATED, PROCESSED, COMPLETED, FAILED, CANCELLED
    private BigDecimal reimbursementAmount;
    private String currency;
    private String paymentMethod; // BANK_TRANSFER, PAYROLL, CHECK, DIGITAL_WALLET
    private String bankAccountId;
    private String transactionReference;
    private LocalDateTime expectedPaymentDate;
    private LocalDateTime actualPaymentDate;
    private String payrollPeriod;
    private String processingNotes;
    private String failureReason;
    private List<String> expenseIds; // For batch reimbursements
    private BigDecimal totalAmount;
    private String batchId;
    private String approvedBy;
    private String processedBy;
    
    public ExpenseReimbursementEvent() {
        super("EXPENSE_REIMBURSEMENT");
    }
    
    public static ExpenseReimbursementEvent reimbursementInitiated(String businessId, String reimbursementId, 
                                                                 String expenseId, String employeeId, 
                                                                 BigDecimal amount, String currency, 
                                                                 String paymentMethod, LocalDateTime expectedDate) {
        ExpenseReimbursementEvent event = new ExpenseReimbursementEvent();
        event.setBusinessId(businessId);
        event.setReimbursementId(reimbursementId);
        event.setExpenseId(expenseId);
        event.setEmployeeId(employeeId);
        event.setAction("INITIATED");
        event.setReimbursementAmount(amount);
        event.setCurrency(currency);
        event.setPaymentMethod(paymentMethod);
        event.setExpectedPaymentDate(expectedDate);
        return event;
    }
    
    public static ExpenseReimbursementEvent reimbursementProcessed(String businessId, String reimbursementId, 
                                                                 String transactionReference, String processedBy) {
        ExpenseReimbursementEvent event = new ExpenseReimbursementEvent();
        event.setBusinessId(businessId);
        event.setReimbursementId(reimbursementId);
        event.setAction("PROCESSED");
        event.setTransactionReference(transactionReference);
        event.setProcessedBy(processedBy);
        return event;
    }
    
    public static ExpenseReimbursementEvent reimbursementCompleted(String businessId, String reimbursementId, 
                                                                 BigDecimal amount, LocalDateTime paymentDate) {
        ExpenseReimbursementEvent event = new ExpenseReimbursementEvent();
        event.setBusinessId(businessId);
        event.setReimbursementId(reimbursementId);
        event.setAction("COMPLETED");
        event.setReimbursementAmount(amount);
        event.setActualPaymentDate(paymentDate);
        return event;
    }
    
    public static ExpenseReimbursementEvent reimbursementFailed(String businessId, String reimbursementId, 
                                                              String failureReason) {
        ExpenseReimbursementEvent event = new ExpenseReimbursementEvent();
        event.setBusinessId(businessId);
        event.setReimbursementId(reimbursementId);
        event.setAction("FAILED");
        event.setFailureReason(failureReason);
        return event;
    }
    
    public static ExpenseReimbursementEvent batchReimbursementInitiated(String businessId, String batchId, 
                                                                      List<String> expenseIds, BigDecimal totalAmount, 
                                                                      String currency, String paymentMethod) {
        ExpenseReimbursementEvent event = new ExpenseReimbursementEvent();
        event.setBusinessId(businessId);
        event.setBatchId(batchId);
        event.setExpenseIds(expenseIds);
        event.setAction("INITIATED");
        event.setTotalAmount(totalAmount);
        event.setCurrency(currency);
        event.setPaymentMethod(paymentMethod);
        return event;
    }
}