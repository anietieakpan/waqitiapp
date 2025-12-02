package com.waqiti.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountVerificationRequest {
    private String userId;
    private String accountNumber;
    private String routingNumber;
    private String accountType;
    private String verificationMethod;
    private List<BigDecimal> verificationAmounts;
    private Instant requestTimestamp;
}
