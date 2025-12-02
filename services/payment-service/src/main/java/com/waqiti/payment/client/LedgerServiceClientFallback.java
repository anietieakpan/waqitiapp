package com.waqiti.payment.client;

import com.waqiti.payment.dto.ledger.FinalizePaymentEntryRequest;
import com.waqiti.payment.dto.ledger.FinalizePaymentEntryResponse;
import com.waqiti.common.exception.ServiceIntegrationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for Ledger Service Client
 * 
 * CRITICAL FAILURE HANDLING:
 * This fallback is for a CRITICAL service - ledger integrity is non-negotiable
 * 
 * When ledger service is unavailable:
 * 1. DO NOT silently succeed - this would cause accounting inconsistencies
 * 2. Throw exception to trigger retry and DLT processing
 * 3. Alert operations team for immediate intervention
 * 4. Queue for manual reconciliation
 * 
 * IMPORTANT: Unlike rewards/analytics, ledger failures MUST block the flow
 * to maintain financial data integrity and regulatory compliance.
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Component
@Slf4j
public class LedgerServiceClientFallback implements LedgerServiceClient {
    
    @Override
    public FinalizePaymentEntryResponse finalizePaymentEntry(FinalizePaymentEntryRequest request) {
        log.error("CRITICAL: Ledger service unavailable for payment: {}. " +
                "This is a CRITICAL failure requiring immediate attention. " +
                "Payment will be queued for manual reconciliation.", 
                request.getPaymentId());
        
        // CRITICAL: Do not return success - throw exception to trigger retry/DLT
        // Ledger consistency is non-negotiable for financial compliance
        throw new ServiceIntegrationException(
            "CRITICAL: Ledger service unavailable - payment " + request.getPaymentId() + 
            " requires manual reconciliation. Financial data integrity at risk.");
    }
}