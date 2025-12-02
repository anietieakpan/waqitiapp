package com.waqiti.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;



@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeVerificationRequest {
    private String userId;
    private BigDecimal annualIncome;
    private String incomeSource;
    private String verificationMethod;
    private String documentUrl;
    private Integer taxYear;
    private Instant requestTimestamp;
}