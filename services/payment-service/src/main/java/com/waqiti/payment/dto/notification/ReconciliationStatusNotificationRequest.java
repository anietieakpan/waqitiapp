package com.waqiti.payment.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationStatusNotificationRequest {
    
    @NotBlank
    private String reconciliationId;
    
    @NotBlank
    private String settlementId;
    
    @NotBlank
    private String status;
    
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
    
    private Instant completedAt;
    
    @NotBlank
    private String priority;
    
    @NotNull
    private List<String> channels;
    
    @NotBlank
    private String correlationId;
}