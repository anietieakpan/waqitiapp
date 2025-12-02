package com.waqiti.payment.saga;

// Request DTOs
@lombok.Data
public class P2PTransferRequest {
    private String fromUserId;
    private String toUserId;
    private java.math.BigDecimal amount;
    private String currency;
    private String description;
    private String reference;
    private String requestId;
}
