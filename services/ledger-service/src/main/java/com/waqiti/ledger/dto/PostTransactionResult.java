package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostTransactionResult {
    private UUID transactionId;
    private boolean success;
    private List<LedgerEntryResponse> ledgerEntries;
    private String errorMessage;
    private String errorCode;
    
    public static PostTransactionResult failure(UUID transactionId, String errorMessage) {
        return PostTransactionResult.builder()
            .transactionId(transactionId)
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }
}