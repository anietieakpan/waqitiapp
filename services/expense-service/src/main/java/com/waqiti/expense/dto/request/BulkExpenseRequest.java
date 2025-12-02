package com.waqiti.expense.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Bulk Expense Request DTO
 * Used for creating multiple expenses in a single operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkExpenseRequest {

    @NotEmpty(message = "Expenses list cannot be empty")
    @Size(max = 100, message = "Cannot process more than 100 expenses at once")
    @Valid
    private List<ExpenseRequest> expenses;

    private Boolean validateAll = true;
    private Boolean stopOnError = false;
    private Boolean autoAssignBudget = true;
    private Boolean autoCategorize = true;
}
