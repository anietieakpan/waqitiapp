package com.waqiti.payment.client;

import com.waqiti.payment.dto.ledger.FinalizePaymentEntryRequest;
import com.waqiti.payment.dto.ledger.FinalizePaymentEntryResponse;
import com.waqiti.payment.dto.ledger.RecordGroupPaymentRequest;
import com.waqiti.payment.dto.ledger.RecordGroupPaymentResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

/**
 * Feign Client for Ledger Service Integration
 * 
 * CRITICAL SERVICE - Handles double-entry bookkeeping:
 * - Finalizing payment ledger entries
 * - Account balance updates
 * - Transaction reconciliation
 * - Financial audit trails
 * - Compliance reporting
 * 
 * IMPORTANCE: This is a CRITICAL service for financial integrity
 * Failures here can cause accounting inconsistencies
 * 
 * RESILIENCE:
 * - Circuit breaker with strict thresholds
 * - Multiple retry attempts
 * - Fallback triggers manual reconciliation
 * - All failures logged for audit
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@FeignClient(
    name = "ledger-service",
    path = "/api/v1/ledger",
    fallback = LedgerServiceClientFallback.class
)
public interface LedgerServiceClient {
    
    /**
     * Finalize payment entry in the general ledger
     * 
     * CRITICAL OPERATION:
     * - Creates immutable ledger entries
     * - Updates account balances
     * - Ensures double-entry bookkeeping
     * - Records fees and net amounts
     * - Generates audit trail
     * 
     * FAILURE IMPACT:
     * - Accounting inconsistencies
     * - Reconciliation failures
     * - Compliance violations
     * - Requires manual intervention
     * 
     * @param request Ledger finalization request
     * @return Finalization response with entry IDs
     * @throws ServiceIntegrationException if ledger update fails
     */
    @PostMapping("/payments/finalize")
    @CircuitBreaker(name = "ledger-service")
    @Retry(name = "ledger-service")
    FinalizePaymentEntryResponse finalizePaymentEntry(
        @Valid @RequestBody FinalizePaymentEntryRequest request
    );

    /**
     * Record group payment event in the general ledger
     * 
     * CRITICAL OPERATION:
     * - Creates ledger entries for group payment events
     * - Records participant payment obligations
     * - Updates account balances for group transactions
     * - Maintains audit trail for group payments
     * - Supports split payment reconciliation
     * 
     * FAILURE IMPACT:
     * - Group payment accounting inconsistencies
     * - Participant balance discrepancies
     * - Split payment reconciliation failures
     * - Compliance and audit trail gaps
     * - Requires manual reconciliation
     * 
     * @param request Group payment ledger recording request
     * @return Recording response with ledger entry details
     * @throws ServiceIntegrationException if ledger recording fails
     */
    @PostMapping("/group-payments/record")
    @CircuitBreaker(name = "ledger-service")
    @Retry(name = "ledger-service")
    RecordGroupPaymentResponse recordGroupPayment(
        @Valid @RequestBody RecordGroupPaymentRequest request
    );
    
    /**
     * Record payment routing change in the general ledger
     * 
     * COST OPTIMIZATION TRACKING:
     * - Records gateway routing changes for cost analysis
     * - Tracks cost savings from intelligent routing
     * - Maintains routing audit trail
     * - Supports routing performance benchmarking
     * 
     * @param request Routing change recording request
     * @return Recording response with ledger entry details
     */
    @PostMapping("/routing-changes/record")
    @CircuitBreaker(name = "ledger-service")
    @Retry(name = "ledger-service")
    RecordRoutingChangeResponse recordRoutingChange(
        @Valid @RequestBody RecordRoutingChangeRequest request
    );
    
    /**
     * Record payment dispute resolution in the general ledger
     * 
     * CRITICAL OPERATION:
     * - Records dispute outcomes for financial tracking
     * - Tracks refunds and chargebacks
     * - Maintains dispute audit trail
     * - Supports compliance reporting
     * 
     * @param request Dispute resolution recording request
     * @return Recording response with ledger entry details
     * @throws ServiceIntegrationException if ledger recording fails
     */
    @PostMapping("/disputes/record-resolution")
    @CircuitBreaker(name = "ledger-service")
    @Retry(name = "ledger-service")
    RecordDisputeResolutionResponse recordDisputeResolution(
        @Valid @RequestBody RecordDisputeResolutionRequest request
    );
    
    /**
     * Record payment refund status update in the general ledger
     * 
     * CRITICAL OPERATION:
     * - Records refund lifecycle transitions
     * - Tracks refund amounts and completions
     * - Maintains refund audit trail
     * - Supports refund reconciliation
     * 
     * @param request Refund update recording request
     * @return Recording response with ledger entry details
     * @throws ServiceIntegrationException if ledger recording fails
     */
    @PostMapping("/refunds/record-update")
    @CircuitBreaker(name = "ledger-service")
    @Retry(name = "ledger-service")
    RecordRefundUpdateResponse recordRefundUpdate(
        @Valid @RequestBody RecordRefundUpdateRequest request
    );
    
    /**
     * Record payment reconciliation status update in the general ledger
     * 
     * CRITICAL OPERATION:
     * - Records reconciliation outcomes for audit trail
     * - Tracks discrepancies and resolutions
     * - Maintains financial reconciliation history
     * - Supports compliance reporting
     * 
     * @param request Reconciliation update recording request
     * @return Recording response with ledger entry details
     * @throws ServiceIntegrationException if ledger recording fails
     */
    @PostMapping("/reconciliations/record-update")
    @CircuitBreaker(name = "ledger-service")
    @Retry(name = "ledger-service")
    RecordReconciliationUpdateResponse recordReconciliationUpdate(
        @Valid @RequestBody RecordReconciliationUpdateRequest request
    );
}