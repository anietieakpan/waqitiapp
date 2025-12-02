package com.waqiti.analytics.dto.response;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantAnalysis {
    private List<MerchantSpending> topMerchants;
    private List<MerchantFrequency> frequentMerchants;
    private List<MerchantLoyalty> loyaltyPrograms;
    private Integer totalUniqueMerchants;
}
