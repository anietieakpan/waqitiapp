package com.waqiti.payment.service;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PaymentSummary {
    private String paymentId;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String paymentType;
    private String description;
    private String recipientName;
    private String recipientTaxId;
}