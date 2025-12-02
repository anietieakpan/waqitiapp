package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveFundsResult {
    private UUID reservationId;
    private BigDecimal amount;
    private boolean success;
    private String errorMessage;
    private LocalDateTime reservedAt;
    
    public static ReserveFundsResult success(UUID reservationId, BigDecimal amount) {
        return ReserveFundsResult.builder()
            .reservationId(reservationId)
            .amount(amount)
            .success(true)
            .reservedAt(LocalDateTime.now())
            .build();
    }
    
    public static ReserveFundsResult failure(String errorMessage) {
        return ReserveFundsResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }
}