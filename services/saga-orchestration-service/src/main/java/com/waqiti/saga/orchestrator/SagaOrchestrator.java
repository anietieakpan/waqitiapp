package com.waqiti.saga.orchestrator;

import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.saga.domain.SagaType;
import com.waqiti.saga.dto.SagaResponse;

/**
 * Base interface for all saga orchestrators
 * 
 * @param <T> The type of request this orchestrator handles
 */
public interface SagaOrchestrator<T> {
    
    /**
     * Get the saga type this orchestrator handles
     */
    SagaType getSagaType();
    
    /**
     * Execute the saga with the given request
     */
    SagaResponse execute(T request);
    
    /**
     * Retry a failed saga
     */
    SagaResponse retry(String sagaId);
    
    /**
     * Cancel a running saga
     */
    SagaResponse cancel(String sagaId, String reason);
    
    /**
     * Get saga execution details
     */
    SagaExecution getExecution(String sagaId);
}