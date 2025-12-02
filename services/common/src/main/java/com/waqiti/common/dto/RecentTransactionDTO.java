package com.waqiti.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentTransactionDTO {
    private UUID transactionId;
    private UUID walletId;
    private String type;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String description;
    private LocalDateTime timestamp;
    private String merchantName;
    private String category;
}