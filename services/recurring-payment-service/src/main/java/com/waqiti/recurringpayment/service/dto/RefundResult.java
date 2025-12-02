package com.waqiti.recurringpayment.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResult {
    private boolean successful;
    private String refundId;
    private String status;
    private BigDecimal amount;
    private String errorMessage;
    private Instant processedAt;
}

