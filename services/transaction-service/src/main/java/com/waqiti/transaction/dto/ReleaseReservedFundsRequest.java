package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ReleaseReservedFundsRequest {
    private UUID reservationId;
    private BigDecimal amount;
}