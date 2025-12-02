package com.waqiti.payment.stripe.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StripeCancelResult {
    private String paymentIntentId;
    private String status; // canceled, requires_payment_method, succeeded, etc.
    private LocalDateTime canceledAt;
    private String cancellationReason;
}