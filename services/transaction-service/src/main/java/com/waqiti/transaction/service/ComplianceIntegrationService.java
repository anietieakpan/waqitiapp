package com.waqiti.transaction.service;

import com.waqiti.transaction.config.TransactionServiceConfiguration.ComplianceResult;

/**
 * Compliance integration service interface
 */
public interface ComplianceIntegrationService {
    
    ComplianceResult analyzeBlockedTransaction(Object transaction, Object block, Object event);
}