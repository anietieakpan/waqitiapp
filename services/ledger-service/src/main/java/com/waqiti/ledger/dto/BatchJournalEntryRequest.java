package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchJournalEntryRequest {
    
    @NotEmpty(message = "At least one journal entry is required")
    @Size(max = 100, message = "Batch size cannot exceed 100 entries")
    @Valid
    private List<CreateJournalEntryRequest> entries;
    
    private String batchDescription;
    private boolean stopOnError = false;
    private String createdBy;
}