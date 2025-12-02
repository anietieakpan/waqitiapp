package com.waqiti.reconciliation.service;

import java.util.List;

/**
 * Matching Service
 * 
 * CRITICAL: Core service for transaction matching in reconciliation process.
 * Handles automated matching of transactions across different data sources.
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
public interface MatchingService {
    
    /**
     * Match transactions for a reconciliation session
     */
    void matchTransactions(String reconciliationId);
    
    /**
     * Find matching transactions for a given transaction
     */
    List<Object> findMatches(Object transaction);
}