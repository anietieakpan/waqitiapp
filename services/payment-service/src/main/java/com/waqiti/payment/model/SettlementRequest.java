package com.waqiti.payment.model;

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
public class SettlementRequest {
    private String settlementId;
    private String batchId;
    private String merchantId;
    private String currency;
    private BigDecimal amount;
    private Instant settlementDate;
    private Integer paymentCount;
}
