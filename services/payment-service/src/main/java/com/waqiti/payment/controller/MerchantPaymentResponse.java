package com.waqiti.payment.controller;

import java.util.UUID;

public class MerchantPaymentResponse {
    private UUID transactionId;
    private String status;
    private java.time.LocalDateTime processedAt;
    
    // Getters and setters omitted for brevity
}
