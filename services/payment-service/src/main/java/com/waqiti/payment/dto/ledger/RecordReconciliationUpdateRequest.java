package com.waqiti.payment.dto.ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordReconciliationUpdateRequest {
    
    @NotBlank
    private String reconciliationId;
    
    @NotBlank
    private String settlementId;
    
    @NotBlank
    private String paymentId;
    
    @NotBlank
    private String status;
    
    private String previousStatus;
    
    @NotBlank
    private String reconciliationType;
    
    @NotNull
    @Positive
    private BigDecimal reconciliationAmount;
    
    @NotBlank
    private String currency;
    
    @NotNull
    private Integer matchedTransactions;
    
    @NotNull
    private Integer unmatchedTransactions;
    
    private BigDecimal discrepancyAmount;
    
    private String discrepancyReason;
    
    @NotBlank
    private String gatewayId;
    
    @NotBlank
    private String merchantId;
    
    @NotNull
    private Instant periodStartDate;
    
    @NotNull
    private Instant periodEndDate;
    
    private Instant completedAt;
    
    @NotNull
    private Instant updatedAt;
    
    @NotBlank
    private String correlationId;
}