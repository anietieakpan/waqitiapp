package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class TransactionProcessingResult {
    
    public enum Status {
        SUCCESS, FAILED, PENDING
    }
    
    private UUID transactionId;
    private Status status;
    private String message;
    private String sagaId;
}