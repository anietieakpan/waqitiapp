package com.waqiti.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionTemplate {
    
    private final PlatformTransactionManager transactionManager;
    
    public <T> T execute(TransactionCallback<T> callback) {
        return execute(callback, TransactionDefinition.PROPAGATION_REQUIRED);
    }
    
    public <T> T execute(TransactionCallback<T> callback, int propagationBehavior) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(propagationBehavior);
        definition.setIsolationLevel(TransactionDefinition.ISOLATION_DEFAULT);
        
        TransactionStatus status = transactionManager.getTransaction(definition);
        
        try {
            log.debug("Starting transaction");
            
            T result = callback.execute(status);
            
            transactionManager.commit(status);
            log.debug("Transaction committed successfully");
            
            return result;
            
        } catch (Exception e) {
            log.error("Transaction failed, rolling back", e);
            
            if (!status.isCompleted()) {
                transactionManager.rollback(status);
            }
            
            throw new RuntimeException("Transaction execution failed", e);
        }
    }
    
    public void executeVoid(VoidTransactionCallback callback) {
        execute(status -> {
            callback.execute(status);
            return null;
        });
    }
    
    public <T> T executeWithReadOnly(TransactionCallback<T> callback) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        definition.setReadOnly(true);
        
        TransactionStatus status = transactionManager.getTransaction(definition);
        
        try {
            log.debug("Starting read-only transaction");
            
            T result = callback.execute(status);
            
            transactionManager.commit(status);
            log.debug("Read-only transaction completed successfully");
            
            return result;
            
        } catch (Exception e) {
            log.error("Read-only transaction failed", e);
            
            if (!status.isCompleted()) {
                transactionManager.rollback(status);
            }
            
            throw new RuntimeException("Read-only transaction execution failed", e);
        }
    }
    
    public <T> T executeWithNewTransaction(TransactionCallback<T> callback) {
        return execute(callback, TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    
    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute(TransactionStatus status);
    }
    
    @FunctionalInterface
    public interface VoidTransactionCallback {
        void execute(TransactionStatus status);
    }
}