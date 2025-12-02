package com.waqiti.payment.controller;

import java.util.UUID;

public class PaymentResponse {
    private UUID id;
    private String status;
    private java.math.BigDecimal amount;
    private String currency;
    private java.time.LocalDateTime createdAt;
    
    // Getters and setters omitted for brevity
}
