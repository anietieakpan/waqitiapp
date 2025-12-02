package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaInitiationRequest {
    
    private String sagaType;
    private UUID transactionId;
    private String fromWalletId;
    private String toWalletId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private Map<String, String> metadata;
}