package com.waqiti.payment.plaid.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PlaidTransferResult {
    private String transferId;
    private String status; // pending, posted, failed, returned, cancelled
    private BigDecimal amount;
    private String network; // ach, same-day-ach
    private String description;
    private LocalDateTime created;
}