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
public class EmailVerificationResult {
    private boolean verified;
    private boolean emailExists;
    private boolean disposableEmail;
    private String domain;
    private String provider;
    private BigDecimal riskScore;
    private BigDecimal reputationScore;
}
