package com.waqiti.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response DTO for mileage expense calculation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MileageCalculationDto {

    private BigDecimal distance;
    private String unit; // MILES, KILOMETERS
    private BigDecimal distanceInMiles;
    private String vehicleType;
    private BigDecimal ratePerMile;
    private BigDecimal calculatedAmount;
    private String currency;
    private String rateSource; // IRS, COMPANY_POLICY, CUSTOM
    private Integer taxYear;
    private String notes;
}
