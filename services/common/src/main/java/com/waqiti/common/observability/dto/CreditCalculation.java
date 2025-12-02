package com.waqiti.common.observability.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class CreditCalculation {
    private double totalCredits;
    private Map<String, Double> creditsBySLA;
    private String creditPolicy;
    private LocalDateTime calculatedDate;
    
    public double getTotalCredits() {
        return totalCredits;
    }
}