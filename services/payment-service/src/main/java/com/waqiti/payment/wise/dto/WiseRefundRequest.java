package com.waqiti.payment.wise.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class WiseRefundRequest {
    private String transferId;
    private BigDecimal amount; // null for full refund
    private String reason; // Transaction rollback, customer request, etc.
}