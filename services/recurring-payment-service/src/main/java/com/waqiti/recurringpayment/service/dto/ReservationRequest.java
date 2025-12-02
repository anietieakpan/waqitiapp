package com.waqiti.recurringpayment.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationRequest {
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String purpose;
    private long durationSeconds;
    private String recurringPaymentId;
}
