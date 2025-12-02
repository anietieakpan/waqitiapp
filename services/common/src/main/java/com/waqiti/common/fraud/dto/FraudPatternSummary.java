package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FraudPatternSummary {
    private long totalPatterns;
    private long cardTestingPatterns;
    private long accountTakeoverPatterns;
    private long moneyLaunderingPatterns;
    private long syntheticIdentityPatterns;
    private long otherPatterns;
}