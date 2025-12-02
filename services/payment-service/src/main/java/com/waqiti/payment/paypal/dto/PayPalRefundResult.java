package com.waqiti.payment.paypal.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PayPalRefundResult {
    private String id;
    private String status; // COMPLETED, PENDING, FAILED, CANCELLED
    private PayPalAmount amount;
    private String captureId;
    private String noteToPayer;
    private LocalDateTime created;
    private List<PayPalLink> links;
}