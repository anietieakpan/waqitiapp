package com.waqiti.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrossBorderTransferResponse {
    @NotNull
    private UUID transferId;
    @NotNull
    private UUID fromUserId;
    @NotNull
    private UUID toUserId;
    @NotNull
    private String fromCountry;
    @NotNull
    private String toCountry;
    @NotNull
    private BigDecimal amount;
    @NotNull
    private String currency;
    @NotNull
    private String status;
    private String complianceStatus;
    private BigDecimal exchangeRate;
    private BigDecimal fee;
    private String transferMethod;
    private LocalDateTime initiatedAt;
    private LocalDateTime estimatedCompletionAt;
    private String trackingNumber;
}
