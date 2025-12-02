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
 * Event published when a user achieves a referral milestone
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferralMilestoneAchievedEvent {

    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String correlationId;

    // Event payload
    private String achievementId;
    private String milestoneId;
    private String milestoneName;
    private UUID userId;
    private String programId;
    private Integer referralCount;
    private Integer conversionCount;
    private BigDecimal revenue;
    private RewardType rewardType;
    private BigDecimal rewardAmount;
    private Long rewardPoints;
    private Instant achievedAt;

    public static ReferralMilestoneAchievedEvent create(
            String achievementId, String milestoneId, String milestoneName,
            UUID userId, String programId, Integer referralCount,
            Integer conversionCount, BigDecimal revenue, RewardType rewardType,
            BigDecimal rewardAmount, Long rewardPoints, String correlationId) {
        return ReferralMilestoneAchievedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("REFERRAL_MILESTONE_ACHIEVED")
                .timestamp(Instant.now())
                .correlationId(correlationId)
                .achievementId(achievementId)
                .milestoneId(milestoneId)
                .milestoneName(milestoneName)
                .userId(userId)
                .programId(programId)
                .referralCount(referralCount)
                .conversionCount(conversionCount)
                .revenue(revenue)
                .rewardType(rewardType)
                .rewardAmount(rewardAmount)
                .rewardPoints(rewardPoints)
                .achievedAt(Instant.now())
                .build();
    }
}
