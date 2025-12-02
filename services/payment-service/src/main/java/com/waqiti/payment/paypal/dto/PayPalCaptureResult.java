package com.waqiti.payment.paypal.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PayPalCaptureResult {
    private String id;
    private String status; // COMPLETED, DECLINED, PARTIALLY_REFUNDED, PENDING, REFUNDED
    private PayPalAmount amount;
    private Boolean finalCapture;
    private LocalDateTime created;
    private List<PayPalLink> links;
}