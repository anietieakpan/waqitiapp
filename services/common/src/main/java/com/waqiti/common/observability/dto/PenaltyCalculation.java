package com.waqiti.common.observability.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PenaltyCalculation {
    private String slaName;
    private double amount;
    private String reason;
    private LocalDateTime appliedDate;
    private String calculationDetails;
    
    public double getAmount() {
        return amount;
    }
}