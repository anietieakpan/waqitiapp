package com.waqiti.user.service;

import java.math.BigDecimal;
import java.util.List;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class AuthenticityResult {
    private boolean authentic;
    private BigDecimal confidenceScore;
    private List<String> fraudIndicators;
    private List<String> failedFeatures;
}
