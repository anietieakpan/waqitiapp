package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PostTransactionRequest {
    private UUID transactionId;
    private List<LedgerEntryRequest> ledgerEntries;
}