package com.waqiti.payment.reversal.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Payment Reversal Request
 */
@Data
@Builder
public class ReversalRequest {
    private String originalTransactionId;
    private UUID walletId;
    private BigDecimal amount;
    private String currency;
    private ReversalReason reason;
    private String reasonDescription;
    private String initiatedBy;
    private Map<String, Object> metadata;
}
