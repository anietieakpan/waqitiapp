package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Result DTO for balance reservation operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceReservationResult {

    private boolean successful;
    private String reservationId;
    private String customerId;
    private BigDecimal reservedAmount;
    private String currency;
    private String status;
    
    private BigDecimal availableBalanceBefore;
    private BigDecimal availableBalanceAfter;
    private BigDecimal totalReserved;
    
    private Instant reservedAt;
    private Instant expiresAt;
    
    private String errorCode;
    private String errorMessage;
    private String failureReason;
    
    private Map<String, Object> metadata;
    
    /**
     * Creates a successful reservation result
     */
    public static BalanceReservationResult successful(String reservationId, String customerId, 
                                                     BigDecimal amount, String currency) {
        return BalanceReservationResult.builder()
                .successful(true)
                .reservationId(reservationId)
                .customerId(customerId)
                .reservedAmount(amount)
                .currency(currency)
                .status("ACTIVE")
                .reservedAt(Instant.now())
                .build();
    }
    
    /**
     * Creates a failed reservation result
     */
    public static BalanceReservationResult failed(String customerId, String errorMessage) {
        return BalanceReservationResult.builder()
                .successful(false)
                .customerId(customerId)
                .status("FAILED")
                .errorMessage(errorMessage)
                .build();
    }
}