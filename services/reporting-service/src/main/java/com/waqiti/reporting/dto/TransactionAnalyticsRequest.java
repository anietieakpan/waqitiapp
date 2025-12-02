package com.waqiti.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionAnalyticsRequest {
    
    @NotNull(message = "Analysis type is required")
    private String analysisType;
    
    @NotNull(message = "Start date is required")
    private LocalDate startDate;
    
    @NotNull(message = "End date is required")
    private LocalDate endDate;
    
    private Map<String, Object> parameters;
    
    private String[] groupBy;
    
    private String[] metrics;
    
    private String currency;
    
    private Boolean includeRefunds;
    
    private Boolean includePending;
}