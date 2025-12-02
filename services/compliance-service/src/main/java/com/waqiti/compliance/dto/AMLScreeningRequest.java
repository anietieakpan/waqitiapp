package com.waqiti.compliance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AMLScreeningRequest {
    
    @NotNull
    private String transactionId;
    
    @NotNull
    private UUID userId;
    
    @NotNull
    private String transactionType;
    
    @NotNull
    private BigDecimal amount;
    
    @NotNull
    private String currency;
    
    private String sourceAccount;
    private String destinationAccount;
    private String counterpartyName;
    private String counterpartyCountry;
    private String purpose;
    private LocalDateTime transactionDate;
    private Map<String, Object> additionalData;
    private Boolean highRiskTransaction;
    private String riskFactors;
}