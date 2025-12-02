package com.waqiti.billingorchestrator.controller;

import com.waqiti.billingorchestrator.dto.*;
import com.waqiti.billingorchestrator.service.BillingOrchestratorService;
import com.waqiti.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Billing Orchestrator", description = "Comprehensive billing management and orchestration")
public class BillingOrchestratorController {

    private final BillingOrchestratorService billingOrchestratorService;

    // Billing Cycle Management
    @PostMapping("/cycles/initiate")
    @Operation(summary = "Initiate a new billing cycle", 
               description = "Starts a new billing cycle for accounts")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    public ResponseEntity<ApiResponse<BillingCycleResponse>> initiateBillingCycle(
            @Valid @RequestBody InitiateBillingCycleRequest request) {
        log.info("Initiating billing cycle for period: {} to {}", 
                request.getStartDate(), request.getEndDate());
        
        BillingCycleResponse response = billingOrchestratorService.initiateBillingCycle(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/cycles/{cycleId}")
    @Operation(summary = "Get billing cycle details")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<BillingCycleResponse>> getBillingCycle(
            @PathVariable UUID cycleId) {
        log.info("Retrieving billing cycle: {}", cycleId);
        
        BillingCycleResponse response = billingOrchestratorService.getBillingCycle(cycleId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/cycles")
    @Operation(summary = "Get all billing cycles with pagination")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    public ResponseEntity<ApiResponse<Page<BillingCycleResponse>>> getBillingCycles(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {
        log.info("Retrieving billing cycles with filters - status: {}, startDate: {}, endDate: {}", 
                status, startDate, endDate);
        
        Page<BillingCycleResponse> response = billingOrchestratorService.getBillingCycles(
                status, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Invoice Generation
    @PostMapping("/invoices/generate")
    @Operation(summary = "Generate invoices for billing cycle")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    public ResponseEntity<ApiResponse<InvoiceGenerationResponse>> generateInvoices(
            @Valid @RequestBody GenerateInvoicesRequest request) {
        log.info("Generating invoices for cycle: {}", request.getCycleId());
        
        InvoiceGenerationResponse response = billingOrchestratorService.generateInvoices(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/invoices/{invoiceId}")
    @Operation(summary = "Get invoice details")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoice(
            @PathVariable UUID invoiceId) {
        log.info("Retrieving invoice: {}", invoiceId);
        
        InvoiceResponse response = billingOrchestratorService.getInvoice(invoiceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/accounts/{accountId}/invoices")
    @Operation(summary = "Get invoices for an account")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Page<InvoiceResponse>>> getAccountInvoices(
            @PathVariable UUID accountId,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        log.info("Retrieving invoices for account: {}", accountId);
        
        Page<InvoiceResponse> response = billingOrchestratorService.getAccountInvoices(
                accountId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/invoices/{invoiceId}/send")
    @Operation(summary = "Send invoice to customer")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> sendInvoice(
            @PathVariable UUID invoiceId) {
        log.info("Sending invoice: {}", invoiceId);
        
        billingOrchestratorService.sendInvoice(invoiceId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Payment Processing
    @PostMapping("/payments/process")
    @Operation(summary = "Process payment for invoice")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @Valid @RequestBody ProcessPaymentRequest request) {
        log.info("Processing payment for invoice: {} amount: {}", 
                request.getInvoiceId(), request.getAmount());
        
        PaymentResponse response = billingOrchestratorService.processPayment(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/payments/{paymentId}")
    @Operation(summary = "Get payment details")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @PathVariable UUID paymentId) {
        log.info("Retrieving payment: {}", paymentId);
        
        PaymentResponse response = billingOrchestratorService.getPayment(paymentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/payments/{paymentId}/refund")
    @Operation(summary = "Refund a payment")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    public ResponseEntity<ApiResponse<RefundResponse>> refundPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody RefundRequest request) {
        log.info("Refunding payment: {} amount: {}", paymentId, request.getAmount());
        
        RefundResponse response = billingOrchestratorService.refundPayment(paymentId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Subscription Management
    @PostMapping("/subscriptions")
    @Operation(summary = "Create a new subscription")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request) {
        log.info("Creating subscription for account: {} plan: {}", 
                request.getAccountId(), request.getPlanId());
        
        SubscriptionResponse response = billingOrchestratorService.createSubscription(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/subscriptions/{subscriptionId}")
    @Operation(summary = "Get subscription details")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getSubscription(
            @PathVariable UUID subscriptionId) {
        log.info("Retrieving subscription: {}", subscriptionId);
        
        SubscriptionResponse response = billingOrchestratorService.getSubscription(subscriptionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/subscriptions/{subscriptionId}")
    @Operation(summary = "Update subscription")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> updateSubscription(
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody UpdateSubscriptionRequest request) {
        log.info("Updating subscription: {}", subscriptionId);
        
        SubscriptionResponse response = billingOrchestratorService.updateSubscription(
                subscriptionId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/subscriptions/{subscriptionId}/cancel")
    @Operation(summary = "Cancel a subscription")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> cancelSubscription(
            @PathVariable UUID subscriptionId,
            @RequestParam(required = false) String reason) {
        log.info("Cancelling subscription: {} reason: {}", subscriptionId, reason);
        
        billingOrchestratorService.cancelSubscription(subscriptionId, reason);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Usage Tracking
    @PostMapping("/usage/record")
    @Operation(summary = "Record usage for metered billing")
    @PreAuthorize("hasRole('SYSTEM')")
    public ResponseEntity<ApiResponse<UsageRecordResponse>> recordUsage(
            @Valid @RequestBody RecordUsageRequest request) {
        log.info("Recording usage for account: {} metric: {} quantity: {}", 
                request.getAccountId(), request.getMetricName(), request.getQuantity());
        
        UsageRecordResponse response = billingOrchestratorService.recordUsage(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/accounts/{accountId}/usage")
    @Operation(summary = "Get usage summary for account")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<UsageSummaryResponse>> getUsageSummary(
            @PathVariable UUID accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Retrieving usage summary for account: {} from {} to {}", 
                accountId, startDate, endDate);
        
        UsageSummaryResponse response = billingOrchestratorService.getUsageSummary(
                accountId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Billing Analytics
    @GetMapping("/analytics/revenue")
    @Operation(summary = "Get revenue analytics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    public ResponseEntity<ApiResponse<RevenueAnalyticsResponse>> getRevenueAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String groupBy) {
        log.info("Retrieving revenue analytics from {} to {}", startDate, endDate);
        
        RevenueAnalyticsResponse response = billingOrchestratorService.getRevenueAnalytics(
                startDate, endDate, groupBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/analytics/outstanding")
    @Operation(summary = "Get outstanding payments report")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    public ResponseEntity<ApiResponse<OutstandingPaymentsResponse>> getOutstandingPayments(
            @RequestParam(required = false, defaultValue = "30") Integer daysOverdue) {
        log.info("Retrieving outstanding payments older than {} days", daysOverdue);
        
        OutstandingPaymentsResponse response = billingOrchestratorService.getOutstandingPayments(daysOverdue);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/analytics/churn")
    @Operation(summary = "Get churn analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ChurnAnalyticsResponse>> getChurnAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Retrieving churn analytics from {} to {}", startDate, endDate);
        
        ChurnAnalyticsResponse response = billingOrchestratorService.getChurnAnalytics(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Dunning Management
    @PostMapping("/dunning/campaigns")
    @Operation(summary = "Create dunning campaign for overdue accounts")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    public ResponseEntity<ApiResponse<DunningCampaignResponse>> createDunningCampaign(
            @Valid @RequestBody CreateDunningCampaignRequest request) {
        log.info("Creating dunning campaign: {}", request.getCampaignName());
        
        DunningCampaignResponse response = billingOrchestratorService.createDunningCampaign(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/dunning/campaigns/{campaignId}")
    @Operation(summary = "Get dunning campaign details")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    public ResponseEntity<ApiResponse<DunningCampaignResponse>> getDunningCampaign(
            @PathVariable UUID campaignId) {
        log.info("Retrieving dunning campaign: {}", campaignId);
        
        DunningCampaignResponse response = billingOrchestratorService.getDunningCampaign(campaignId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Credit Management
    @PostMapping("/credits/apply")
    @Operation(summary = "Apply credit to account")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    public ResponseEntity<ApiResponse<CreditResponse>> applyCredit(
            @Valid @RequestBody ApplyCreditRequest request) {
        log.info("Applying credit of {} to account: {}", request.getAmount(), request.getAccountId());
        
        CreditResponse response = billingOrchestratorService.applyCredit(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/accounts/{accountId}/credits")
    @Operation(summary = "Get account credits")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<AccountCreditsResponse>> getAccountCredits(
            @PathVariable UUID accountId) {
        log.info("Retrieving credits for account: {}", accountId);
        
        AccountCreditsResponse response = billingOrchestratorService.getAccountCredits(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Tax Management
    @PostMapping("/tax/calculate")
    @Operation(summary = "Calculate tax for invoice")
    @PreAuthorize("hasRole('SYSTEM')")
    public ResponseEntity<ApiResponse<TaxCalculationResponse>> calculateTax(
            @Valid @RequestBody CalculateTaxRequest request) {
        log.info("Calculating tax for amount: {} location: {}", 
                request.getAmount(), request.getLocation());
        
        TaxCalculationResponse response = billingOrchestratorService.calculateTax(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/tax/rates")
    @Operation(summary = "Get tax rates by location")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    public ResponseEntity<ApiResponse<List<TaxRateResponse>>> getTaxRates(
            @RequestParam String location) {
        log.info("Retrieving tax rates for location: {}", location);
        
        List<TaxRateResponse> response = billingOrchestratorService.getTaxRates(location);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Dispute Management
    @PostMapping("/disputes")
    @Operation(summary = "Create billing dispute")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<BillingDisputeResponse>> createDispute(
            @Valid @RequestBody CreateBillingDisputeRequest request) {
        log.info("Creating billing dispute for invoice: {}", request.getInvoiceId());
        
        BillingDisputeResponse response = billingOrchestratorService.createDispute(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/disputes/{disputeId}")
    @Operation(summary = "Get billing dispute details")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<BillingDisputeResponse>> getDispute(
            @PathVariable UUID disputeId) {
        log.info("Retrieving billing dispute: {}", disputeId);
        
        BillingDisputeResponse response = billingOrchestratorService.getDispute(disputeId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/disputes/{disputeId}/resolve")
    @Operation(summary = "Resolve billing dispute")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BILLING_MANAGER')")
    public ResponseEntity<ApiResponse<BillingDisputeResponse>> resolveDispute(
            @PathVariable UUID disputeId,
            @Valid @RequestBody ResolveDisputeRequest request) {
        log.info("Resolving billing dispute: {}", disputeId);
        
        BillingDisputeResponse response = billingOrchestratorService.resolveDispute(disputeId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Health Check
    @GetMapping("/health")
    @Operation(summary = "Health check for billing orchestrator service")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("Billing Orchestrator Service is healthy"));
    }
}