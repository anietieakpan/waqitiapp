package com.waqiti.account.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveFundsResult {
    private boolean success;
    private String failureReason;
    private String reservationId;
}