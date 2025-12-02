package com.waqiti.transaction.service;

/**
 * Transaction recovery service interface
 */
public interface TransactionRecoveryService {
    
    void initiateRecoveryWorkflow(Object transaction, Object block);
    void scheduleAutomaticUnblock(Object transaction, Object block);
}