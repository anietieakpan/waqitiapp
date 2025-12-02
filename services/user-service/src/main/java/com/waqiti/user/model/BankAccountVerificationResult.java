package com.waqiti.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountVerificationResult {
    private boolean verified;
    private boolean accountExists;
    private String bankName;
    private String accountStatus;
    private BigDecimal verificationScore;
    private boolean microDepositsRequired;
    private Duration estimatedVerificationTime;
}
