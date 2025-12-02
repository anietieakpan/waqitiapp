package com.waqiti.payment.paypal.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PayPalRefundRequest {
    private String captureId;
    private PayPalAmount amount; // null for full refund
    private String noteToPayer;
    private String invoiceId;
}