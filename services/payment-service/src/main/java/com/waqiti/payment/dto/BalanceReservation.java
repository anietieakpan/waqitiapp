package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO representing a balance reservation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceReservation {
    
    private String reservationId;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private String status; // ACTIVE, RELEASED, CONFIRMED, EXPIRED
    private String reason;
    private String reference;
    private Instant createdAt;
    private Instant expiresAt;
}