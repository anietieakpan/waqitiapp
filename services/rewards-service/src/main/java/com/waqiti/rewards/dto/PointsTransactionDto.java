package com.waqiti.rewards.dto;

import com.waqiti.rewards.enums.PointsSource;
import com.waqiti.rewards.enums.PointsStatus;
import com.waqiti.rewards.enums.PointsTransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointsTransactionDto {
    private UUID id;
    private String userId;
    private PointsTransactionType type;
    private Long points;
    private String description;
    private PointsSource source;
    private PointsStatus status;
    private Instant processedAt;
    private Instant expiresAt;
    private String transactionId;
    private String redemptionId;
    private String campaignId;
}