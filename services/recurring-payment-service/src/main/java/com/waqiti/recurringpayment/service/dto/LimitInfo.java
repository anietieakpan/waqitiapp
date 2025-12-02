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
public class LimitInfo {
    private BigDecimal limit;
    private BigDecimal used;
    private BigDecimal remaining;
    private String currency;
    private Instant resetsAt;
}
