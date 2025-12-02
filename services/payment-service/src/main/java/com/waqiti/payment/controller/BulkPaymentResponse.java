package com.waqiti.payment.controller;

import java.util.List;

public class BulkPaymentResponse {
    private int successCount;
    private int failureCount;
    private List<PaymentResponse> processedPayments;
    
    // Getters and setters omitted for brevity
}
