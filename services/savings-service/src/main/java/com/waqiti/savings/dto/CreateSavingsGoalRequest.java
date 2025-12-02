package com.waqiti.savings.dto;

import com.waqiti.savings.domain.SavingsGoal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request DTO for creating a new savings goal.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a new savings goal")
public class CreateSavingsGoalRequest {

    @Schema(description = "User ID (will be overridden with authenticated user)")
    private UUID userId;

    @Schema(description = "Savings account ID for this goal")
    private UUID accountId;

    @NotBlank(message = "Goal name is required")
    @Size(min = 3, max = 100, message = "Goal name must be between 3 and 100 characters")
    @Schema(description = "Goal name", example = "Dream Vacation", required = true)
    private String goalName;

    @Schema(description = "Goal description", example = "Save for a 2-week trip to Hawaii")
    @Size(max = 500, message = "Description too long")
    private String description;

    @NotNull(message = "Goal category is required")
    @Schema(description = "Goal category", example = "VACATION", required = true)
    private SavingsGoal.Category category;

    @NotNull(message = "Target amount is required")
    @DecimalMin(value = "10.00", message = "Target amount must be at least $10")
    @DecimalMax(value = "1000000.00", message = "Target amount exceeds maximum")
    @Digits(integer = 19, fraction = 4, message = "Invalid amount format")
    @Schema(description = "Target amount to save", example = "5000.00", required = true)
    private BigDecimal targetAmount;

    @Schema(description = "Target completion date")
    @Future(message = "Target date must be in the future")
    private LocalDateTime targetDate;

    @Schema(description = "Goal priority", example = "HIGH")
    private SavingsGoal.Priority priority = SavingsGoal.Priority.MEDIUM;

    @Schema(description = "Goal visibility", example = "PRIVATE")
    private SavingsGoal.Visibility visibility = SavingsGoal.Visibility.PRIVATE;

    @Schema(description = "Goal icon", example = "plane")
    @Size(max = 50, message = "Icon name too long")
    private String icon;

    @Schema(description = "Goal color (hex)", example = "#3498DB")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Invalid hex color format")
    private String color;

    @Schema(description = "Goal image URL")
    @Size(max = 500, message = "Image URL too long")
    private String imageUrl;

    @Schema(description = "Enable auto-save for this goal")
    private Boolean autoSaveEnabled = false;

    @Schema(description = "Allow flexible target amount")
    private Boolean flexibleTarget = false;

    @Schema(description = "Allow withdrawals from this goal")
    private Boolean allowWithdrawals = true;

    @Schema(description = "Initial contribution amount", example = "500.00")
    @DecimalMin(value = "0.0", message = "Initial contribution must be positive")
    private BigDecimal initialContribution;
}
