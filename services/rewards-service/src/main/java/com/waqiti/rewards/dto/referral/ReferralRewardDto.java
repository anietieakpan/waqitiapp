package com.waqiti.rewards.dto.referral;

import com.waqiti.rewards.enums.RewardStatus;
import com.waqiti.rewards.enums.RewardType;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Referral reward details")
public class ReferralRewardDto {

    @Schema(description = "Reward unique identifier", example = "RWD-ABC12345")
    private String rewardId;

    @Schema(description = "Referral identifier")
    private String referralId;

    @Schema(description = "Program identifier")
    private String programId;

    @Schema(description = "Program name")
    private String programName;

    @Schema(description = "User ID who receives the reward")
    private UUID recipientUserId;

    @Schema(description = "Recipient type", example = "REFERRER")
    private String recipientType;

    @Schema(description = "Reward type", example = "CASHBACK")
    private RewardType rewardType;

    @Schema(description = "Points amount (if applicable)")
    private Long pointsAmount;

    @Schema(description = "Cashback amount (if applicable)")
    private BigDecimal cashbackAmount;

    @Schema(description = "Current status of the reward", example = "ISSUED")
    private RewardStatus status;

    @Schema(description = "Whether reward requires manual approval")
    private Boolean requiresApproval;

    @Schema(description = "Approval timestamp")
    private LocalDateTime approvedAt;

    @Schema(description = "User ID who approved (if applicable)")
    private UUID approvedBy;

    @Schema(description = "Issuance timestamp")
    private LocalDateTime issuedAt;

    @Schema(description = "Redemption timestamp")
    private LocalDateTime redeemedAt;

    @Schema(description = "Expiration timestamp")
    private LocalDateTime expiresAt;

    @Schema(description = "Whether reward has expired")
    private Boolean isExpired;

    @Schema(description = "Account ID where reward was redeemed")
    private String redemptionAccountId;

    @Schema(description = "Transaction ID for redemption")
    private String redemptionTransactionId;

    @Schema(description = "Rejection reason (if applicable)")
    private String rejectionReason;

    @Schema(description = "Rejection timestamp")
    private LocalDateTime rejectedAt;

    @Schema(description = "User ID who rejected (if applicable)")
    private UUID rejectedBy;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}
