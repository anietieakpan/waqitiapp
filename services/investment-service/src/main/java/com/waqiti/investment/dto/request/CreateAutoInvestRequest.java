package com.waqiti.investment.dto.request;

import com.waqiti.investment.domain.enums.AutoInvestFrequency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAutoInvestRequest {

    @NotNull(message = "Account ID is required")
    private String accountId;

    @NotBlank(message = "Plan name is required")
    @Size(max = 100, message = "Plan name must not exceed 100 characters")
    private String planName;

    @NotNull(message = "Investment amount is required")
    @DecimalMin(value = "1.00", message = "Minimum investment amount is $1.00")
    private BigDecimal amount;

    @NotNull(message = "Frequency is required")
    private AutoInvestFrequency frequency;

    @Min(value = 1, message = "Day of month must be between 1 and 31")
    @Max(value = 31, message = "Day of month must be between 1 and 31")
    private Integer dayOfMonth;

    @Min(value = 1, message = "Day of week must be between 1 and 7")
    @Max(value = 7, message = "Day of week must be between 1 and 7")
    private Integer dayOfWeek;

    @NotNull(message = "Start date is required")
    @FutureOrPresent(message = "Start date must be today or in the future")
    private LocalDate startDate;

    @Future(message = "End date must be in the future")
    private LocalDate endDate;

    private Boolean rebalanceEnabled = false;

    @DecimalMin(value = "0.01", message = "Rebalance threshold must be greater than 0")
    @DecimalMax(value = "100.00", message = "Rebalance threshold must be less than 100")
    private BigDecimal rebalanceThreshold;

    private Boolean fractionalSharesEnabled = true;

    private Boolean notificationsEnabled = true;

    @NotEmpty(message = "At least one allocation is required")
    @Valid
    private List<AllocationRequest> allocations;

    private String metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllocationRequest {
        
        @NotBlank(message = "Symbol is required")
        private String symbol;

        @NotBlank(message = "Instrument type is required")
        private String instrumentType;

        @NotBlank(message = "Name is required")
        private String name;

        @NotNull(message = "Percentage is required")
        @DecimalMin(value = "0.01", message = "Minimum allocation is 0.01%")
        @DecimalMax(value = "100.00", message = "Maximum allocation is 100%")
        private BigDecimal percentage;

        @DecimalMin(value = "0.01", message = "Minimum investment must be greater than 0")
        private BigDecimal minInvestment;

        private BigDecimal maxInvestment;

        private String notes;
    }

    public void validate() {
        if (allocations != null) {
            BigDecimal totalPercentage = allocations.stream()
                    .map(AllocationRequest::getPercentage)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            if (totalPercentage.compareTo(new BigDecimal("100")) != 0) {
                throw new IllegalArgumentException("Total allocation percentage must equal 100%");
            }
        }

        if (AutoInvestFrequency.WEEKLY.equals(frequency) && dayOfWeek == null) {
            throw new IllegalArgumentException("Day of week is required for weekly frequency");
        }

        if ((AutoInvestFrequency.MONTHLY.equals(frequency) || 
             AutoInvestFrequency.QUARTERLY.equals(frequency)) && dayOfMonth == null) {
            throw new IllegalArgumentException("Day of month is required for monthly/quarterly frequency");
        }

        if (endDate != null && !endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }
    }
}