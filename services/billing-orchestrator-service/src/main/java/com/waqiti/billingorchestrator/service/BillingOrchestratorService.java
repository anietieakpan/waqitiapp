package com.waqiti.billingorchestrator.service;

import com.waqiti.billingorchestrator.dto.request.*;
import com.waqiti.billingorchestrator.dto.response.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Main Billing Orchestrator Service Interface
 *
 * Orchestrates all billing operations across internal services and external microservices
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
public interface BillingOrchestratorService {

    // ==================== Billing Cycle Management ====================

    /**
     * Initiate a new billing cycle
     */
    BillingCycleResponse initiateBillingCycle(InitiateBillingCycleRequest request);

    /**
     * Get billing cycle details
     */
    BillingCycleResponse getBillingCycle(UUID cycleId);

    /**
     * Get all billing cycles with filters
     */
    Page<BillingCycleResponse> getBillingCycles(String status, LocalDate startDate, LocalDate endDate, Pageable pageable);

    // ==================== Invoice Management ====================

    /**
     * Generate invoices for billing cycle
     */
    InvoiceGenerationResponse generateInvoices(GenerateInvoicesRequest request);

    /**
     * Get invoice details
     */
    InvoiceResponse getInvoice(UUID invoiceId);

    /**
     * Get invoices for an account
     */
    Page<InvoiceResponse> getAccountInvoices(UUID accountId, String status, Pageable pageable);

    /**
     * Send invoice to customer via email/notification
     */
    void sendInvoice(UUID invoiceId);

    // ==================== Payment Processing ====================

    /**
     * Process payment for invoice
     * Orchestrates with payment-service and updates billing cycle
     */
    PaymentResponse processPayment(ProcessPaymentRequest request);

    /**
     * Get payment details
     */
    PaymentResponse getPayment(UUID paymentId);

    /**
     * Refund a payment
     */
    RefundResponse refundPayment(UUID paymentId, RefundRequest request);

    // ==================== Subscription Management ====================

    /**
     * Create a new subscription
     */
    SubscriptionResponse createSubscription(CreateSubscriptionRequest request);

    /**
     * Get subscription details
     */
    SubscriptionResponse getSubscription(UUID subscriptionId);

    /**
     * Update subscription
     */
    SubscriptionResponse updateSubscription(UUID subscriptionId, UpdateSubscriptionRequest request);

    /**
     * Cancel a subscription
     */
    void cancelSubscription(UUID subscriptionId, String reason);

    // ==================== Usage Tracking ====================

    /**
     * Record usage for metered billing
     */
    UsageRecordResponse recordUsage(RecordUsageRequest request);

    /**
     * Get usage summary for account
     */
    UsageSummaryResponse getUsageSummary(UUID accountId, LocalDate startDate, LocalDate endDate);

    // ==================== Billing Analytics ====================

    /**
     * Get revenue analytics
     */
    RevenueAnalyticsResponse getRevenueAnalytics(LocalDate startDate, LocalDate endDate, String groupBy);

    /**
     * Get outstanding payments report
     */
    OutstandingPaymentsResponse getOutstandingPayments(Integer daysOverdue);

    /**
     * Get churn analytics
     */
    ChurnAnalyticsResponse getChurnAnalytics(LocalDate startDate, LocalDate endDate);

    // ==================== Dunning Management ====================

    /**
     * Create dunning campaign for overdue accounts
     */
    DunningCampaignResponse createDunningCampaign(CreateDunningCampaignRequest request);

    /**
     * Get dunning campaign details
     */
    DunningCampaignResponse getDunningCampaign(UUID campaignId);

    // ==================== Credit Management ====================

    /**
     * Apply credit to account
     */
    CreditResponse applyCredit(ApplyCreditRequest request);

    /**
     * Get account credits
     */
    AccountCreditsResponse getAccountCredits(UUID accountId);

    // ==================== Tax Management ====================

    /**
     * Calculate tax for invoice
     */
    TaxCalculationResponse calculateTax(CalculateTaxRequest request);

    /**
     * Get tax rates by location
     */
    List<TaxRateResponse> getTaxRates(String location);

    // ==================== Dispute Management ====================

    /**
     * Create billing dispute
     */
    BillingDisputeResponse createDispute(CreateBillingDisputeRequest request);

    /**
     * Get billing dispute details
     */
    BillingDisputeResponse getDispute(UUID disputeId);

    /**
     * Resolve billing dispute
     */
    BillingDisputeResponse resolveDispute(UUID disputeId, ResolveDisputeRequest request);
}
