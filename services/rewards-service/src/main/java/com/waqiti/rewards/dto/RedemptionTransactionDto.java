package com.waqiti.rewards.dto;

import com.waqiti.rewards.enums.RedemptionMethod;
import com.waqiti.rewards.enums.RedemptionStatus;
import com.waqiti.rewards.enums.RedemptionType;
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
public class RedemptionTransactionDto {
    private UUID id;
    private String userId;
    private RedemptionType type;
    private BigDecimal amount;
    private String currency;
    private Long pointsAmount;
    private RedemptionMethod method;
    private RedemptionStatus status;
    private String description;
    private String redemptionCode;
    private Instant processedAt;
    private Instant completedAt;
    private Instant expiresAt;
    private String transactionReference;
    private String merchantName;
    private String giftCardDetails;
}