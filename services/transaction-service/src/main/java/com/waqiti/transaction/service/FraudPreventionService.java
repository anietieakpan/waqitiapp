package com.waqiti.transaction.service;

import com.waqiti.transaction.config.TransactionServiceConfiguration.FraudAssessment;

/**
 * Fraud prevention service interface
 */
public interface FraudPreventionService {
    
    FraudAssessment assessBlockedTransaction(Object transaction, Object block, Object event);
    void evaluateAccountRestrictions(String userId, Object fraudAssessment);
}