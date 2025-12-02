package com.waqiti.common.fraud.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Data
public class UserRiskProfileUpdateRequest {
    @NotBlank(message = "Updated by is required")
    private String updatedBy;
    private BigDecimal typicalTransactionAmount;
    private Set<Integer> typicalActiveHours;
    private List<Location> typicalLocations;
    private int typicalDailyTransactions;
    private double overallRiskScore;
    private String reason;
}