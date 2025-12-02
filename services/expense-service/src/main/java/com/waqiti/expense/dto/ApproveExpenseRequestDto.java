package com.waqiti.expense.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for approving or rejecting an expense
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveExpenseRequestDto {

    @NotNull(message = "Approval decision is required")
    private boolean approved;

    @Size(max = 1000, message = "Comments cannot exceed 1000 characters")
    private String comments;

    @Size(max = 255, message = "Approver name cannot exceed 255 characters")
    private String approverName;
}
