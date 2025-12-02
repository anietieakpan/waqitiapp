package com.waqiti.savings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request DTO for creating a milestone.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a savings milestone")
public class CreateMilestoneRequest {

    @NotBlank(message = "Milestone name is required")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    @Schema(description = "Milestone name", example = "50% Complete", required = true)
    private String name;

    @Schema(description = "Milestone description")
    @Size(max = 500, message = "Description too long")
    private String description;

    @Schema(description = "Target percentage (0-100)", example = "50.00")
    @DecimalMin(value = "0.00", message = "Percentage cannot be negative")
    @DecimalMax(value = "100.00", message = "Percentage cannot exceed 100")
    @Digits(integer = 3, fraction = 2, message = "Invalid percentage format")
    private BigDecimal targetPercentage;

    @Schema(description = "Target amount", example = "2500.00")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    @DecimalMax(value = "10000000.00", message = "Amount exceeds maximum")
    @Digits(integer = 19, fraction = 4, message = "Invalid amount format")
    private BigDecimal targetAmount;

    @Schema(description = "Target date for milestone")
    @Future(message = "Target date must be in the future")
    private LocalDateTime targetDate;

    @Schema(description = "Reward type", example = "BADGE")
    @Size(max = 30, message = "Reward type too long")
    private String rewardType;

    @Schema(description = "Reward value", example = "Gold Star Badge")
    @Size(max = 200, message = "Reward value too long")
    private String rewardValue;

    @Schema(description = "Milestone icon", example = "trophy")
    @Size(max = 50, message = "Icon name too long")
    private String icon;

    @Schema(description = "Milestone color (hex)", example = "#FFD700")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be valid hex code")
    private String color;

    @Schema(description = "Badge image URL")
    @Size(max = 500, message = "URL too long")
    private String badgeUrl;

    @Schema(description = "Notify when approaching milestone")
    private Boolean notifyOnApproach = true;

    @Schema(description = "Notify when milestone achieved")
    private Boolean notifyOnAchievement = true;

    @Schema(description = "Display order", example = "1")
    @Min(value = 0, message = "Display order cannot be negative")
    private Integer displayOrder;
}
