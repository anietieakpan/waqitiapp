package com.waqiti.payment.controller;

public class PaymentApprovalRequest {
    private String reason;
    private String approverNotes;
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getApproverNotes() { return approverNotes; }
    public void setApproverNotes(String approverNotes) { this.approverNotes = approverNotes; }
}
