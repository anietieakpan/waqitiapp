package com.waqiti.expense.dto;

import com.waqiti.expense.domain.enums.ExpenseType;
import com.waqiti.expense.domain.enums.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for creating a new expense
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExpenseRequestDto {

    @NotBlank(message = "Description is required")
    @Size(min = 3, max = 500, message = "Description must be between 3 and 500 characters")
    private String description;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 15, fraction = 4, message = "Amount must have at most 15 integer digits and 4 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid ISO 4217 code (e.g., USD, EUR)")
    private String currency;

    @NotNull(message = "Expense date is required")
    @PastOrPresent(message = "Expense date cannot be in the future")
    private LocalDate expenseDate;

    @Size(max = 255, message = "Category ID cannot exceed 255 characters")
    private String categoryId;

    @Size(max = 255, message = "Budget ID cannot exceed 255 characters")
    private String budgetId;

    @NotNull(message = "Expense type is required")
    private ExpenseType expenseType;

    private PaymentMethod paymentMethod;

    // Merchant Information
    @Size(max = 255, message = "Merchant name cannot exceed 255 characters")
    private String merchantName;

    @Size(max = 255, message = "Merchant category cannot exceed 255 characters")
    private String merchantCategory;

    @Size(max = 255, message = "Location city cannot exceed 255 characters")
    private String locationCity;

    @Size(min = 2, max = 2, message = "Country code must be 2 characters (ISO 3166-1 alpha-2)")
    private String locationCountry;

    // Expense Details
    private Boolean isRecurring = false;

    @Size(max = 50, message = "Recurring frequency cannot exceed 50 characters")
    private String recurringFrequency;

    private Boolean isReimbursable = false;

    private Boolean isBusinessExpense = false;

    private Boolean taxDeductible = false;

    // Notes and Tags
    @Size(max = 2000, message = "Notes cannot exceed 2000 characters")
    private String notes;

    @Size(max = 20, message = "Cannot add more than 20 tags")
    private List<@NotBlank @Size(max = 100) String> tags;

    // Attachments (URLs to already uploaded files)
    @Size(max = 10, message = "Cannot attach more than 10 files")
    private List<@NotBlank @Size(max = 1000) String> attachmentUrls;

    // Geolocation
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double longitude;

    // Transaction reference (if expense is linked to a transaction)
    @Size(max = 255, message = "Transaction ID cannot exceed 255 characters")
    private String transactionId;
}
