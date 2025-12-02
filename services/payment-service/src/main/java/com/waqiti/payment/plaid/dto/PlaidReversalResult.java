package com.waqiti.payment.plaid.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PlaidReversalResult {
    private String reversalId;
    private String transferId;
    private String status; // pending, posted, failed, returned
    private BigDecimal amount;
    private String description;
    private LocalDateTime createdAt;
}