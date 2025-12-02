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
public class ReleaseReservedFundsResult {
    private UUID reservationId;
    private BigDecimal amount;
    private boolean success;
    private String errorMessage;
    private LocalDateTime releasedAt;
    
    public static ReleaseReservedFundsResult success(UUID reservationId, BigDecimal amount) {
        return ReleaseReservedFundsResult.builder()
            .reservationId(reservationId)
            .amount(amount)
            .success(true)
            .releasedAt(LocalDateTime.now())
            .build();
    }
    
    public static ReleaseReservedFundsResult failure(String errorMessage) {
        return ReleaseReservedFundsResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }
}