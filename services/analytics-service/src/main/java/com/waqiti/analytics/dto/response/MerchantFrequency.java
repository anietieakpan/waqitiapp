package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Merchant Frequency DTO
 *
 * Tracks transaction frequency with specific merchants.
 *
 * @author Waqiti Analytics Team
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantFrequency {

    @NotBlank
    private String merchantName;

    @PositiveOrZero
    private Integer visitCount;

    private String frequency; // DAILY, WEEKLY, MONTHLY, OCCASIONAL

    private Double averageDaysBetweenVisits;
}
