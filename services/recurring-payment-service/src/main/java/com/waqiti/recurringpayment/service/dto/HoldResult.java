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
public class HoldResult {
    private boolean successful;
    private String holdId;
    private BigDecimal amount;
    private Instant expiresAt;
    private String errorMessage;
}
