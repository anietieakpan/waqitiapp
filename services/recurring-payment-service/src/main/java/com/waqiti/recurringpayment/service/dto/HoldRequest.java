package com.waqiti.recurringpayment.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldRequest {
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String reason;
    private long durationSeconds;
    private String referenceId;
    private Map<String, String> metadata;
}
