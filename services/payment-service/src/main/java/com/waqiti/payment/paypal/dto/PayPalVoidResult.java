package com.waqiti.payment.paypal.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PayPalVoidResult {
    private String authorizationId;
    private String status; // VOIDED, FAILED
    private LocalDateTime voidedAt;
    private String reason;
}