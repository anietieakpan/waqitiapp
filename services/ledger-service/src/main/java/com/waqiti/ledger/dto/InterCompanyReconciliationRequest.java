package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterCompanyReconciliationRequest {
    
    @NotNull(message = "Start date is required")
    private LocalDate startDate;
    
    @NotNull(message = "End date is required") 
    private LocalDate endDate;
    
    @NotNull(message = "Source company ID is required")
    private UUID sourceCompanyId;
    
    @NotNull(message = "Target company ID is required")
    private UUID targetCompanyId;
    
    private List<UUID> sourceAccountIds;
    
    private List<UUID> targetAccountIds;
    
    private boolean includeUnmatchedTransactions = true;
    
    private boolean autoMatch = true;
    
    private MatchingCriteria matchingCriteria;
    
    private String reconciliationName;
    
    private String performedBy;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class MatchingCriteria {
    private boolean matchByAmount = true;
    private boolean matchByReference = true;
    private boolean matchByDate = true;
    private int dateToleranceDays = 0;
    private boolean matchByDescription = false;
    private double amountTolerancePercent = 0.0;
}