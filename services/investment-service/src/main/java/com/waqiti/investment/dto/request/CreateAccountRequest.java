package com.waqiti.investment.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequest {

    @NotNull(message = "Customer ID is required")
    private String customerId;

    @NotNull(message = "Wallet account ID is required")
    private String walletAccountId;

    private String riskProfile;

    private String investmentGoals;

    private BigDecimal riskTolerance;

    @NotBlank(message = "Brokerage provider is required")
    private String brokerageProvider;

    private BigDecimal initialDeposit;

    private Boolean enableFractionalShares;

    private Boolean enableAutoInvest;

    private Boolean enableMarginTrading;

    private Boolean enableOptionsTrading;

    private String taxId;

    private String employmentStatus;

    private String employerName;

    private BigDecimal annualIncome;

    private BigDecimal netWorth;

    private BigDecimal liquidNetWorth;

    private String investmentExperience;

    private String investmentObjective;

    private String timeHorizon;

    private String metadata;
}