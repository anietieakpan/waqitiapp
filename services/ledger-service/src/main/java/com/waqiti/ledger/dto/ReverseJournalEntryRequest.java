package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReverseJournalEntryRequest {
    
    @NotBlank(message = "Reversal reason is required")
    @Size(max = 500, message = "Reversal reason must not exceed 500 characters")
    private String reversalReason;
    
    @NotNull(message = "Reversal date is required")
    private LocalDateTime reversalDate;
    
    @NotBlank(message = "Reversed by is required")
    private String reversedBy;
    
    private boolean autoApprove = false;
    
    private String notes;
}