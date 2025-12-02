package com.waqiti.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhoneVerificationResult {
    private boolean verified;
    private boolean numberExists;
    private String carrier;
    private String lineType;
    private String country;
    private BigDecimal riskScore;
    private BigDecimal fraudScore;
}
