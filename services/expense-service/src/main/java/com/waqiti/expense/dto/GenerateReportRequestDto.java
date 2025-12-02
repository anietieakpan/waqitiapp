package com.waqiti.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for generating an expense report
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateReportRequestDto {

    @NotNull(message = "Report start date is required")
    private LocalDate startDate;

    @NotNull(message = "Report end date is required")
    private LocalDate endDate;

    @NotBlank(message = "Report format is required")
    @Size(max = 20, message = "Format cannot exceed 20 characters")
    private String format; // PDF, EXCEL, CSV

    @Size(max = 255, message = "Report title cannot exceed 255 characters")
    private String title;

    private List<@NotBlank @Size(max = 255) String> categoryIds;

    private Boolean includeReceipts = false;

    private Boolean includeCharts = true;

    private Boolean includeAnalytics = true;

    @Size(max = 100, message = "Group by field cannot exceed 100 characters")
    private String groupBy; // CATEGORY, MERCHANT, DATE, PAYMENT_METHOD

    @Size(max = 100, message = "Sort by field cannot exceed 100 characters")
    private String sortBy; // DATE, AMOUNT, CATEGORY

    private String sortDirection; // ASC, DESC
}
