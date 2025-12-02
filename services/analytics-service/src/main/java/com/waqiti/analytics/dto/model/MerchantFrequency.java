package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Merchant Frequency DTO for analyzing transaction frequency per merchant
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantFrequency {
    private String merchantName;
    private Long frequency;
    private String frequencyLevel; // VERY_FREQUENT, FREQUENT, OCCASIONAL, RARE
}
