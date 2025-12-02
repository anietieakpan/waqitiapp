package com.waqiti.rewards.dto.referral;

import com.waqiti.rewards.enums.ProgramType;
import com.waqiti.rewards.enums.RewardType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a new referral program")
public class CreateReferralProgramRequest {

    @NotBlank(message = "Program ID is required")
    @Pattern(regexp = "^REF-PROG-[A-Z0-9-]+$", message = "Program ID must follow format: REF-PROG-XXX")
    @Schema(description = "Program unique identifier", example = "REF-PROG-001")
    private String programId;

    @NotBlank(message = "Program name is required")
    @Size(min = 3, max = 255, message = "Program name must be between 3 and 255 characters")
    @Schema(description = "Program name", example = "Standard Referral Program")
    private String programName;

    @NotNull(message = "Program type is required")
    @Schema(description = "Program type", example = "STANDARD")
    private ProgramType programType;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Schema(description = "Program description")
    private String description;

    @NotNull(message = "Start date is required")
    @FutureOrPresent(message = "Start date must be today or in the future")
    @Schema(description = "Program start date")
    private LocalDate startDate;

    @Schema(description = "Program end date (null for no end date)")
    private LocalDate endDate;

    // Referrer rewards
    @NotNull(message = "Referrer reward type is required")
    @Schema(description = "Reward type for referrer", example = "CASHBACK")
    private RewardType referrerRewardType;

    @DecimalMin(value = "0.0", inclusive = false, message = "Referrer reward amount must be positive")
    @Digits(integer = 13, fraction = 2, message = "Invalid amount format")
    @Schema(description = "Reward amount for referrer (required if reward type is monetary)")
    private BigDecimal referrerRewardAmount;

    @Min(value = 1, message = "Referrer reward points must be at least 1")
    @Schema(description = "Reward points for referrer (required if reward type is points)")
    private Long referrerRewardPoints;

    // Referee rewards
    @Schema(description = "Reward type for referee", example = "POINTS")
    private RewardType refereeRewardType;

    @DecimalMin(value = "0.0", inclusive = false, message = "Referee reward amount must be positive")
    @Digits(integer = 13, fraction = 2, message = "Invalid amount format")
    @Schema(description = "Reward amount for referee")
    private BigDecimal refereeRewardAmount;

    @Min(value = 1, message = "Referee reward points must be at least 1")
    @Schema(description = "Reward points for referee")
    private Long refereeRewardPoints;

    // Requirements
    @DecimalMin(value = "0.0", message = "Minimum transaction amount cannot be negative")
    @Digits(integer = 13, fraction = 2, message = "Invalid amount format")
    @Schema(description = "Minimum transaction amount required for reward eligibility")
    private BigDecimal minTransactionAmount;

    @Min(value = 1, message = "Reward expiry days must be at least 1")
    @Max(value = 365, message = "Reward expiry days cannot exceed 365")
    @Schema(description = "Number of days before reward expires", example = "90")
    private Integer rewardExpiryDays;

    @Min(value = 1, message = "Max referrals per user must be at least 1")
    @Schema(description = "Maximum referrals allowed per user", example = "10")
    private Integer maxReferralsPerUser;

    @Schema(description = "Whether referee must complete first transaction", example = "true")
    private Boolean requiresFirstTransaction;

    // Budget
    @DecimalMin(value = "0.0", inclusive = false, message = "Max program budget must be positive")
    @Digits(integer = 16, fraction = 2, message = "Invalid budget format")
    @Schema(description = "Maximum program budget")
    private BigDecimal maxProgramBudget;

    // Metadata
    @Schema(description = "Additional program metadata")
    private Map<String, Object> metadata;
}
