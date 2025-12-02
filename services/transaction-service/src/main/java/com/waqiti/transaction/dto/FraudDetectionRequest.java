package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class FraudDetectionRequest {
    private UUID transactionId;
    private BigDecimal amount;
    private String sourceAccountId;
    private String targetAccountId;
    private String deviceFingerprint;
    private String locationData;
}