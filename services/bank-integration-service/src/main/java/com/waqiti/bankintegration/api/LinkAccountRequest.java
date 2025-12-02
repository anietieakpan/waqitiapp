package com.waqiti.bankintegration.api;

// Request DTOs
@lombok.Data
@lombok.Builder
public class LinkAccountRequest {
    private String userId;
    private String accountNumber;
    private String routingNumber;
    private String bankName;
    private String accountType; // checking, savings
    private String accountHolderName;
}
