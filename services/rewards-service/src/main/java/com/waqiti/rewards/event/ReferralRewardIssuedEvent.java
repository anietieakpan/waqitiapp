package com.waqiti.rewards.event;

import com.waqiti.rewards.enums.RewardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a referral reward is issued
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferralRewardIssuedEvent {

    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String correlationId;

    // Event payload
    private String rewardId;
    private String linkId;
    private String programId;
    private UUID recipientId;
    private String recipientType; // REFERRER, REFEREE
    private RewardType rewardType;
    private Long pointsAmount;
    private BigDecimal cashbackAmount;
    private String currency;
    private String status;
    private Instant issuedAt;

    public static ReferralRewardIssuedEvent create(
            String rewardId, String linkId, String programId, UUID recipientId,
            String recipientType, RewardType rewardType, Long pointsAmount,
            BigDecimal cashbackAmount, String status, String correlationId) {
        return ReferralRewardIssuedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("REFERRAL_REWARD_ISSUED")
                .timestamp(Instant.now())
                .correlationId(correlationId)
                .rewardId(rewardId)
                .linkId(linkId)
                .programId(programId)
                .recipientId(recipientId)
                .recipientType(recipientType)
                .rewardType(rewardType)
                .pointsAmount(pointsAmount)
                .cashbackAmount(cashbackAmount)
                .currency("USD")
                .status(status)
                .issuedAt(Instant.now())
                .build();
    }
}
