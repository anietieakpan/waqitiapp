package com.waqiti.payment.plaid.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PlaidCancelResult {
    private String transferId;
    private String status; // cancelled, failed, pending
    private LocalDateTime cancelledAt;
    private String network; // ach, same-day-ach
    private BigDecimal amount;
}