package com.waqiti.tax.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxSavingOpportunity {
    private String category;
    private String title;
    private String description;
    private BigDecimal potentialSavings;
    private String actionRequired;
    private Priority priority;
    private String deadline;
    private boolean implemented;

    public enum Priority {
        HIGH,
        MEDIUM,
        LOW
    }
}
