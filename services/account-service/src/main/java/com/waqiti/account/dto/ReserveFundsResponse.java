package com.waqiti.account.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveFundsResponse {
    private String reservationId;
    private UUID accountId;
    private BigDecimal amount;
    private boolean success;
    private String message;
}