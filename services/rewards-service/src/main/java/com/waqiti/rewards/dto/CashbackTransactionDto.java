package com.waqiti.rewards.dto;

import com.waqiti.rewards.enums.CashbackStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashbackTransactionDto {
    private UUID id;
    private String transactionId;
    private String merchantName;
    private String merchantCategory;
    private BigDecimal transactionAmount;
    private String currency;
    private BigDecimal cashbackRate;
    private BigDecimal cashbackAmount;
    private CashbackStatus status;
    private Instant earnedAt;
    private Instant processedAt;
    private String description;
}