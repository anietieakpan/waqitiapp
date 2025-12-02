package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosePeriodRequest {
    
    @NotNull(message = "Period end date is required")
    private LocalDate periodEndDate;
    
    @NotNull(message = "Period start date is required")
    private LocalDate periodStartDate;
    
    private UUID periodId;
    
    private boolean performSoftClose = true;
    
    private boolean generateReports = true;
    
    private boolean closeRevenueAccounts = true;
    
    private boolean closeExpenseAccounts = true;
    
    private boolean createOpeningBalances = true;
    
    private String closedBy;
    
    private String notes;
}