package com.waqiti.payment.controller;

import java.util.List;

public class BulkPaymentRequest {
    private List<PaymentRequest> payments;
    private java.math.BigDecimal totalAmount;
    
    public List<PaymentRequest> getPayments() { return payments; }
    public void setPayments(List<PaymentRequest> payments) { this.payments = payments; }
    public java.math.BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(java.math.BigDecimal totalAmount) { this.totalAmount = totalAmount; }
}
