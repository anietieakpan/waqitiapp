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
public class ReservationResult {
    private boolean successful;
    private String reservationId;
    private BigDecimal reservedAmount;
    private Instant expiresAt;
    private String errorMessage;
}
