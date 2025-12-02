package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PostTransactionResult {
    private boolean success;
    private String failureReason;
    private String ledgerTransactionId;
}