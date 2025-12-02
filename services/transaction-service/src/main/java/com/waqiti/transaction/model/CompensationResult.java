package com.waqiti.transaction.model;

import lombok.Builder;
import lombok.Data;

/**
 * Result of a saga transaction with compensation
 */
@Data
@Builder
public class CompensationResult {
    private boolean success;
    private String sagaId;
    private Object result;
    private String errorMessage;
    private CompensationExecutionResult compensationDetails;
    
    public static CompensationResult success(Object result, String sagaId) {
        return CompensationResult.builder()
            .success(true)
            .sagaId(sagaId)
            .result(result)
            .build();
    }
    
    public static CompensationResult failure(String errorMessage, String sagaId, 
                                            CompensationExecutionResult compensationDetails) {
        return CompensationResult.builder()
            .success(false)
            .sagaId(sagaId)
            .errorMessage(errorMessage)
            .compensationDetails(compensationDetails)
            .build();
    }
}