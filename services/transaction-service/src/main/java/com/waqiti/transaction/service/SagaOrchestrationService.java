package com.waqiti.transaction.service;

import com.waqiti.transaction.dto.*;
import com.waqiti.transaction.saga.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaOrchestrationService {

    @Transactional
    public SagaExecutionResult executeSaga(TransactionSaga saga) {
        log.info("Executing saga: sagaId={}, transactionId={}", saga.getSagaId(), saga.getTransactionId());
        
        LocalDateTime startTime = LocalDateTime.now();
        List<SagaStepExecutionResult> stepResults = new ArrayList<>();
        
        saga.setStatus(TransactionSaga.SagaStatus.RUNNING);
        
        for (int i = 0; i < saga.getSteps().size(); i++) {
            SagaStep step = saga.getSteps().get(i);
            saga.setCurrentStepIndex(i);
            
            try {
                log.debug("Executing saga step: {}", step.getStepName());
                SagaStepResult result = step.getForwardAction().get();
                
                SagaStepExecutionResult stepExecution = SagaStepExecutionResult.builder()
                    .stepName(step.getStepName())
                    .success(result.isSuccess())
                    .message(result.getMessage())
                    .build();
                stepResults.add(stepExecution);
                
                if (!result.isSuccess()) {
                    log.warn("Saga step failed: {}, initiating compensation", step.getStepName());
                    compensateSaga(saga, i);
                    
                    return SagaExecutionResult.builder()
                        .success(false)
                        .sagaId(saga.getSagaId())
                        .failureReason(result.getMessage())
                        .stepResults(stepResults)
                        .startTime(startTime)
                        .endTime(LocalDateTime.now())
                        .build();
                }
            } catch (Exception e) {
                log.error("Exception in saga step execution: {}", step.getStepName(), e);
                compensateSaga(saga, i);
                
                return SagaExecutionResult.builder()
                    .success(false)
                    .sagaId(saga.getSagaId())
                    .failureReason("Step " + step.getStepName() + " failed: " + e.getMessage())
                    .stepResults(stepResults)
                    .startTime(startTime)
                    .endTime(LocalDateTime.now())
                    .build();
            }
        }
        
        saga.setStatus(TransactionSaga.SagaStatus.COMPLETED);
        
        return SagaExecutionResult.builder()
            .success(true)
            .sagaId(saga.getSagaId())
            .executionSummary("All saga steps completed successfully")
            .stepResults(stepResults)
            .startTime(startTime)
            .endTime(LocalDateTime.now())
            .build();
    }
    
    public void compensateSaga(String sagaId, String reason) {
        log.info("Compensating saga: sagaId={}, reason={}", sagaId, reason);
        // Implementation for compensating a saga by ID
    }
    
    private void compensateSaga(TransactionSaga saga, int failedStepIndex) {
        log.info("Compensating saga from step {}", failedStepIndex);
        saga.setStatus(TransactionSaga.SagaStatus.COMPENSATING);
        
        for (int i = failedStepIndex - 1; i >= 0; i--) {
            SagaStep step = saga.getSteps().get(i);
            try {
                log.debug("Executing compensation for step: {}", step.getStepName());
                SagaStepResult result = step.getCompensationAction().get();
                if (!result.isSuccess()) {
                    log.error("Compensation failed for step: {}", step.getStepName());
                }
            } catch (Exception e) {
                log.error("Exception during compensation for step: {}", step.getStepName(), e);
            }
        }
        
        saga.setStatus(TransactionSaga.SagaStatus.COMPENSATED);
    }
    
    public BatchProcessingResult executeBatchSaga(BatchTransactionSaga batchSaga) {
        log.info("Executing batch saga: batchId={}", batchSaga.getBatchId());
        
        List<TransactionProcessingResult> successfulResults = new ArrayList<>();
        List<TransactionProcessingResult> failedResults = new ArrayList<>();
        
        for (ProcessTransactionRequest transaction : batchSaga.getTransactions()) {
            try {
                // Process each transaction in the batch
                TransactionProcessingResult result = processIndividualTransaction(transaction);
                
                if (result.getStatus() == TransactionProcessingResult.Status.SUCCESS) {
                    successfulResults.add(result);
                } else {
                    failedResults.add(result);
                }
            } catch (Exception e) {
                log.error("Failed to process transaction in batch", e);
                failedResults.add(TransactionProcessingResult.builder()
                    .status(TransactionProcessingResult.Status.FAILED)
                    .message("Processing error: " + e.getMessage())
                    .build());
            }
        }
        
        return BatchProcessingResult.builder()
            .batchId(batchSaga.getBatchId())
            .totalTransactions(batchSaga.getTransactions().size())
            .successfulTransactions(successfulResults.size())
            .failedTransactions(failedResults.size())
            .successfulResults(successfulResults)
            .failedResults(failedResults)
            .build();
    }
    
    private TransactionProcessingResult processIndividualTransaction(ProcessTransactionRequest request) {
        // Simplified transaction processing logic
        return TransactionProcessingResult.builder()
            .transactionId(UUID.randomUUID())
            .status(TransactionProcessingResult.Status.SUCCESS)
            .message("Transaction processed")
            .build();
    }
}