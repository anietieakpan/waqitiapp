package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportTransactionsRequest {
    
    private String walletId;
    private String userId;
    private LocalDate startDate;
    private LocalDate endDate;
    
    @NotNull(message = "Format is required")
    @Pattern(regexp = "csv|pdf", message = "Format must be 'csv' or 'pdf'")
    private String format;
    
    private String type;
    private String status;
    private String currency;
    private Boolean includeMetadata = false;
    private Boolean includeFees = true;
}