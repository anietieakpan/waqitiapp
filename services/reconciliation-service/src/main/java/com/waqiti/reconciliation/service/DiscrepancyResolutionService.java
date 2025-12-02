package com.waqiti.reconciliation.service;

/**
 * Discrepancy Resolution Service
 * 
 * CRITICAL: Service for resolving discrepancies found during reconciliation.
 * Handles automated and manual resolution of reconciliation mismatches.
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
public interface DiscrepancyResolutionService {
    
    /**
     * Resolve a discrepancy with the provided resolution
     */
    void resolveDiscrepancy(String discrepancyId, String resolution);
}