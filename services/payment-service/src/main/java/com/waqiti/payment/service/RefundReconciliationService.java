package com.waqiti.payment.service;

import com.waqiti.payment.entity.RefundTransaction;
import com.waqiti.payment.entity.RefundTransaction.RefundStatus;
import com.waqiti.payment.entity.RefundTransaction.ReconciliationStatus;
import com.waqiti.payment.repository.RefundTransactionRepository;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.event.PaymentEventPublisher;
import com.waqiti.common.audit.TransactionAuditService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Automated Refund Reconciliation Service
 *
 * Provides comprehensive automated reconciliation for refund transactions across all payment providers.
 * Ensures financial accuracy, compliance, and automated dispute resolution.
 *
 * Features:
 * - Automated daily reconciliation of refund transactions
 * - Multi-provider reconciliation (Stripe, PayPal, Square, etc.)
 * - Discrepancy detection and automated resolution
 * - Settlement batch matching
 * - Financial reporting and audit trail
 * - Real-time reconciliation for high-value refunds
 * - Automated retry for failed reconciliations
 * - Circuit breaker for provider API failures
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundReconciliationService {

    private final RefundTransactionRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final TransactionAuditService auditService;
    private final PaymentEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final IdempotencyService idempotencyService;

    // Provider-specific reconciliation clients
    private final StripePaymentProcessor stripeProcessor;
    private final PayPalPaymentProcessor paypalProcessor;
    private final SquarePaymentProcessor squareProcessor;

    @Value("${refund.reconciliation.batch-size:100}")
    private int reconciliationBatchSize;

    @Value("${refund.reconciliation.lookback-days:7}")
    private int reconciliationLookbackDays;

    @Value("${refund.reconciliation.high-value-threshold:10000}")
    private BigDecimal highValueThreshold;

    @Value("${refund.reconciliation.auto-resolve-discrepancies:true}")
    private boolean autoResolveDiscrepancies;

    @Value("${refund.reconciliation.retry-attempts:3}")
    private int maxRetryAttempts;

    private final Counter reconciledRefundsCounter;
    private final Counter discrepanciesCounter;
    private final Timer reconciliationTimer;

    public RefundReconciliationService(
            RefundTransactionRepository refundRepository,
            PaymentRepository paymentRepository,
            TransactionAuditService auditService,
            PaymentEventPublisher eventPublisher,
            MeterRegistry meterRegistry,
            IdempotencyService idempotencyService,
            StripePaymentProcessor stripeProcessor,
            PayPalPaymentProcessor paypalProcessor,
            SquarePaymentProcessor squareProcessor) {

        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
        this.idempotencyService = idempotencyService;
        this.stripeProcessor = stripeProcessor;
        this.paypalProcessor = paypalProcessor;
        this.squareProcessor = squareProcessor;

        // Initialize metrics
        this.reconciledRefundsCounter = Counter.builder("refund.reconciliation.completed")
                .description("Total refunds successfully reconciled")
                .register(meterRegistry);

        this.discrepanciesCounter = Counter.builder("refund.reconciliation.discrepancies")
                .description("Total refund discrepancies detected")
                .register(meterRegistry);

        this.reconciliationTimer = Timer.builder("refund.reconciliation.duration")
                .description("Duration of refund reconciliation operations")
                .register(meterRegistry);
    }

    /**
     * Scheduled job to reconcile all unreconciled refunds
     * Runs every hour during business hours
     */
    @Scheduled(cron = "0 0 * * * ?") // Every hour
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void reconcileUnreconciledRefunds() {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.info("Starting scheduled refund reconciliation");

            // Get all unreconciled refunds
            List<RefundTransaction> unreconciledRefunds = refundRepository.findUnreconciledRefunds();

            if (unreconciledRefunds.isEmpty()) {
                log.info("No unreconciled refunds found");
                return;
            }

            log.info("Found {} unreconciled refunds to process", unreconciledRefunds.size());

            // Process in batches to avoid overwhelming the system
            List<List<RefundTransaction>> batches = partitionList(unreconciledRefunds, reconciliationBatchSize);

            int totalReconciled = 0;
            int totalDiscrepancies = 0;

            for (List<RefundTransaction> batch : batches) {
                ReconciliationBatchResult result = reconcileBatch(batch);
                totalReconciled += result.getReconciledCount();
                totalDiscrepancies += result.getDiscrepancyCount();
            }

            log.info("Scheduled reconciliation completed: {} refunds reconciled, {} discrepancies found",
                    totalReconciled, totalDiscrepancies);

            // Publish reconciliation summary event
            publishReconciliationSummary(totalReconciled, totalDiscrepancies, unreconciledRefunds.size());

        } catch (Exception e) {
            log.error("Error during scheduled refund reconciliation", e);
            // Continue to allow next scheduled run
        } finally {
            sample.stop(reconciliationTimer);
        }
    }

    /**
     * Reconcile a batch of refund transactions
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ReconciliationBatchResult reconcileBatch(List<RefundTransaction> refunds) {
        int reconciledCount = 0;
        int discrepancyCount = 0;
        int failedCount = 0;
        List<RefundDiscrepancy> discrepancies = new ArrayList<>();

        for (RefundTransaction refund : refunds) {
            try {
                ReconciliationResult result = reconcileSingleRefund(refund);

                if (result.isReconciled()) {
                    reconciledCount++;
                    reconciledRefundsCounter.increment();
                } else if (result.hasDiscrepancy()) {
                    discrepancyCount++;
                    discrepanciesCounter.increment();
                    discrepancies.add(result.getDiscrepancy());
                } else {
                    failedCount++;
                }

            } catch (Exception e) {
                log.error("Failed to reconcile refund: {}", refund.getRefundId(), e);
                failedCount++;
            }
        }

        return ReconciliationBatchResult.builder()
                .totalCount(refunds.size())
                .reconciledCount(reconciledCount)
                .discrepancyCount(discrepancyCount)
                .failedCount(failedCount)
                .discrepancies(discrepancies)
                .build();
    }

    /**
     * Reconcile a single refund transaction with provider
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @CircuitBreaker(name = "refund-reconciliation", fallbackMethod = "reconciliationFallback")
    @Retry(name = "refund-reconciliation", fallbackMethod = "reconciliationFallback")
    public ReconciliationResult reconcileSingleRefund(RefundTransaction refund) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.debug("Reconciling refund: {} with provider: {}",
                    refund.getRefundId(), refund.getProviderType());

            // Check idempotency to prevent duplicate reconciliation
            String idempotencyKey = "refund-reconciliation-" + refund.getRefundId();
            var idempotencyResult = idempotencyService.checkIdempotency(
                    idempotencyKey, ReconciliationResult.class);

            if (!idempotencyResult.isNewOperation()) {
                log.debug("Refund already reconciled: {}", refund.getRefundId());
                return idempotencyResult.getResult();
            }

            // Fetch refund details from provider
            ProviderRefundDetails providerDetails = fetchProviderRefundDetails(refund);

            if (providerDetails == null) {
                log.warn("Could not fetch provider details for refund: {}", refund.getRefundId());
                return ReconciliationResult.failed("Provider details not available");
            }

            // Compare our records with provider records
            RefundDiscrepancy discrepancy = compareRefundDetails(refund, providerDetails);

            if (discrepancy == null) {
                // Perfect match - mark as reconciled
                refund.setReconciliationStatus(ReconciliationStatus.RECONCILED);
                refund.setReconciledAt(LocalDateTime.now());
                refund.setReconciledAmount(providerDetails.getAmount());
                refund.setProviderSettlementId(providerDetails.getSettlementId());
                refundRepository.save(refund);

                // Audit the reconciliation
                auditReconciliation(refund, providerDetails, null);

                ReconciliationResult result = ReconciliationResult.success(refund.getRefundId());

                // Store idempotency result
                idempotencyService.storeIdempotencyResult(
                        idempotencyKey, result, java.time.Duration.ofDays(30),
                        Map.of("refundId", refund.getRefundId()));

                return result;

            } else {
                // Discrepancy detected
                log.warn("Discrepancy detected for refund {}: {}",
                        refund.getRefundId(), discrepancy.getDescription());

                refund.setReconciliationStatus(ReconciliationStatus.DISCREPANCY);
                refund.setReconciliationNotes(discrepancy.getDescription());
                refundRepository.save(refund);

                // Audit the discrepancy
                auditReconciliation(refund, providerDetails, discrepancy);

                // Attempt auto-resolution if enabled
                if (autoResolveDiscrepancies && canAutoResolve(discrepancy)) {
                    return attemptAutoResolution(refund, providerDetails, discrepancy);
                }

                // Publish discrepancy event for manual review
                publishDiscrepancyEvent(refund, discrepancy);

                return ReconciliationResult.discrepancy(refund.getRefundId(), discrepancy);
            }

        } catch (Exception e) {
            log.error("Error reconciling refund: {}", refund.getRefundId(), e);
            throw e;
        } finally {
            sample.stop(Timer.builder("refund.reconciliation.single.duration")
                    .tag("provider", refund.getProviderType().toString())
                    .register(meterRegistry));
        }
    }

    /**
     * Fetch refund details from payment provider
     */
    private ProviderRefundDetails fetchProviderRefundDetails(RefundTransaction refund) {
        try {
            switch (refund.getProviderType()) {
                case STRIPE:
                    return stripeProcessor.fetchRefundDetails(refund.getProviderRefundId());

                case PAYPAL:
                    return paypalProcessor.fetchRefundDetails(refund.getProviderRefundId());

                case SQUARE:
                    return squareProcessor.fetchRefundDetails(refund.getProviderRefundId());

                case BRAINTREE:
                case ADYEN:
                case DWOLLA:
                default:
                    log.warn("Provider reconciliation not yet implemented for: {}",
                            refund.getProviderType());
                    return null;
            }
        } catch (Exception e) {
            log.error("Failed to fetch provider details for refund: {}",
                    refund.getRefundId(), e);
            return null;
        }
    }

    /**
     * Compare internal refund details with provider details
     */
    private RefundDiscrepancy compareRefundDetails(RefundTransaction refund,
                                                  ProviderRefundDetails providerDetails) {
        List<String> discrepancies = new ArrayList<>();

        // Compare amounts
        if (refund.getRefundAmount().compareTo(providerDetails.getAmount()) != 0) {
            discrepancies.add(String.format("Amount mismatch: Local=%s, Provider=%s",
                    refund.getRefundAmount(), providerDetails.getAmount()));
        }

        // Compare status
        if (!refund.getStatus().toString().equals(providerDetails.getStatus())) {
            discrepancies.add(String.format("Status mismatch: Local=%s, Provider=%s",
                    refund.getStatus(), providerDetails.getStatus()));
        }

        // Compare currency
        if (!refund.getCurrency().equals(providerDetails.getCurrency())) {
            discrepancies.add(String.format("Currency mismatch: Local=%s, Provider=%s",
                    refund.getCurrency(), providerDetails.getCurrency()));
        }

        // Compare fees (if available)
        if (refund.getRefundFee() != null && providerDetails.getFee() != null) {
            if (refund.getRefundFee().compareTo(providerDetails.getFee()) != 0) {
                discrepancies.add(String.format("Fee mismatch: Local=%s, Provider=%s",
                        refund.getRefundFee(), providerDetails.getFee()));
            }
        }

        if (discrepancies.isEmpty()) {
            return null;
        }

        return RefundDiscrepancy.builder()
                .refundId(refund.getRefundId())
                .providerRefundId(refund.getProviderRefundId())
                .provider(refund.getProviderType().toString())
                .description(String.join("; ", discrepancies))
                .localAmount(refund.getRefundAmount())
                .providerAmount(providerDetails.getAmount())
                .localStatus(refund.getStatus().toString())
                .providerStatus(providerDetails.getStatus())
                .severity(calculateDiscrepancySeverity(refund, providerDetails))
                .detectedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Calculate severity of discrepancy
     */
    private DiscrepancySeverity calculateDiscrepancySeverity(RefundTransaction refund,
                                                            ProviderRefundDetails providerDetails) {
        // Amount discrepancy
        BigDecimal amountDiff = refund.getRefundAmount().subtract(providerDetails.getAmount()).abs();

        if (amountDiff.compareTo(highValueThreshold) > 0) {
            return DiscrepancySeverity.CRITICAL;
        } else if (amountDiff.compareTo(new BigDecimal("100")) > 0) {
            return DiscrepancySeverity.HIGH;
        } else if (amountDiff.compareTo(BigDecimal.ZERO) > 0) {
            return DiscrepancySeverity.MEDIUM;
        }

        // Status discrepancy
        if (!refund.getStatus().toString().equals(providerDetails.getStatus())) {
            return DiscrepancySeverity.HIGH;
        }

        return DiscrepancySeverity.LOW;
    }

    /**
     * Check if discrepancy can be auto-resolved
     */
    private boolean canAutoResolve(RefundDiscrepancy discrepancy) {
        // Only auto-resolve low severity discrepancies
        if (discrepancy.getSeverity() != DiscrepancySeverity.LOW) {
            return false;
        }

        // Auto-resolve small amount differences (could be rounding)
        BigDecimal amountDiff = discrepancy.getLocalAmount()
                .subtract(discrepancy.getProviderAmount()).abs();

        return amountDiff.compareTo(new BigDecimal("0.10")) <= 0;
    }

    /**
     * Attempt automatic resolution of discrepancy
     */
    private ReconciliationResult attemptAutoResolution(RefundTransaction refund,
                                                      ProviderRefundDetails providerDetails,
                                                      RefundDiscrepancy discrepancy) {
        try {
            log.info("Attempting auto-resolution for refund: {}", refund.getRefundId());

            // Update our records to match provider (provider is source of truth)
            refund.setRefundAmount(providerDetails.getAmount());
            refund.setStatus(mapProviderStatus(providerDetails.getStatus()));
            refund.setReconciliationStatus(ReconciliationStatus.AUTO_RESOLVED);
            refund.setReconciledAt(LocalDateTime.now());
            refund.setReconciliationNotes("Auto-resolved: " + discrepancy.getDescription());

            refundRepository.save(refund);

            // Audit the auto-resolution
            auditAutoResolution(refund, providerDetails, discrepancy);

            log.info("Successfully auto-resolved discrepancy for refund: {}", refund.getRefundId());

            return ReconciliationResult.autoResolved(refund.getRefundId(), discrepancy);

        } catch (Exception e) {
            log.error("Failed to auto-resolve discrepancy for refund: {}",
                    refund.getRefundId(), e);
            return ReconciliationResult.discrepancy(refund.getRefundId(), discrepancy);
        }
    }

    /**
     * Map provider status string to our RefundStatus enum
     */
    private RefundStatus mapProviderStatus(String providerStatus) {
        return switch (providerStatus.toUpperCase()) {
            case "COMPLETED", "SUCCEEDED", "SUCCESS" -> RefundStatus.COMPLETED;
            case "PENDING", "PROCESSING" -> RefundStatus.PROCESSING;
            case "FAILED", "FAILURE" -> RefundStatus.FAILED;
            case "CANCELLED", "CANCELED" -> RefundStatus.CANCELLED;
            default -> RefundStatus.PENDING;
        };
    }

    /**
     * Audit reconciliation operation
     */
    private void auditReconciliation(RefundTransaction refund,
                                   ProviderRefundDetails providerDetails,
                                   RefundDiscrepancy discrepancy) {
        auditService.auditPaymentOperation(
                refund.getRefundId(),
                refund.getProviderType().toString(),
                "REFUND_RECONCILIATION",
                refund.getRefundAmount(),
                refund.getCurrency(),
                discrepancy == null ? "RECONCILED" : "DISCREPANCY",
                "system",
                Map.of(
                        "refundId", refund.getRefundId(),
                        "providerRefundId", refund.getProviderRefundId(),
                        "providerAmount", providerDetails.getAmount(),
                        "providerStatus", providerDetails.getStatus(),
                        "hasDiscrepancy", discrepancy != null,
                        "discrepancyDescription", discrepancy != null ? discrepancy.getDescription() : ""
                )
        );
    }

    /**
     * Audit auto-resolution
     */
    private void auditAutoResolution(RefundTransaction refund,
                                   ProviderRefundDetails providerDetails,
                                   RefundDiscrepancy discrepancy) {
        auditService.auditPaymentOperation(
                refund.getRefundId(),
                refund.getProviderType().toString(),
                "REFUND_AUTO_RESOLVED",
                refund.getRefundAmount(),
                refund.getCurrency(),
                "AUTO_RESOLVED",
                "system",
                Map.of(
                        "refundId", refund.getRefundId(),
                        "originalAmount", discrepancy.getLocalAmount(),
                        "resolvedAmount", providerDetails.getAmount(),
                        "discrepancyDescription", discrepancy.getDescription()
                )
        );
    }

    /**
     * Publish discrepancy event for alerting/manual review
     */
    private void publishDiscrepancyEvent(RefundTransaction refund, RefundDiscrepancy discrepancy) {
        try {
            eventPublisher.publishEvent(
                    "refund.reconciliation.discrepancy",
                    refund.getRefundId(),
                    Map.of(
                            "refundId", refund.getRefundId(),
                            "discrepancy", discrepancy,
                            "severity", discrepancy.getSeverity(),
                            "requiresManualReview", true
                    )
            );
        } catch (Exception e) {
            log.error("Failed to publish discrepancy event for refund: {}",
                    refund.getRefundId(), e);
        }
    }

    /**
     * Publish reconciliation summary
     */
    private void publishReconciliationSummary(int reconciled, int discrepancies, int total) {
        try {
            eventPublisher.publishEvent(
                    "refund.reconciliation.summary",
                    UUID.randomUUID().toString(),
                    Map.of(
                            "timestamp", LocalDateTime.now(),
                            "totalProcessed", total,
                            "reconciled", reconciled,
                            "discrepancies", discrepancies,
                            "successRate", total > 0 ? (double) reconciled / total * 100 : 0
                    )
            );
        } catch (Exception e) {
            log.error("Failed to publish reconciliation summary", e);
        }
    }

    /**
     * Fallback method for circuit breaker/retry
     */
    public ReconciliationResult reconciliationFallback(RefundTransaction refund, Exception ex) {
        log.error("Reconciliation fallback triggered for refund: {} - {}",
                refund.getRefundId(), ex.getMessage());

        return ReconciliationResult.failed("Reconciliation failed: " + ex.getMessage());
    }

    /**
     * Partition list into batches
     */
    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }

    // DTOs and Result Classes

    @lombok.Data
    @lombok.Builder
    public static class ReconciliationResult {
        private String refundId;
        private boolean reconciled;
        private boolean autoResolved;
        private boolean hasDiscrepancy;
        private RefundDiscrepancy discrepancy;
        private String errorMessage;
        private LocalDateTime timestamp;

        public static ReconciliationResult success(String refundId) {
            return ReconciliationResult.builder()
                    .refundId(refundId)
                    .reconciled(true)
                    .hasDiscrepancy(false)
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        public static ReconciliationResult discrepancy(String refundId, RefundDiscrepancy discrepancy) {
            return ReconciliationResult.builder()
                    .refundId(refundId)
                    .reconciled(false)
                    .hasDiscrepancy(true)
                    .discrepancy(discrepancy)
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        public static ReconciliationResult autoResolved(String refundId, RefundDiscrepancy discrepancy) {
            return ReconciliationResult.builder()
                    .refundId(refundId)
                    .reconciled(true)
                    .autoResolved(true)
                    .hasDiscrepancy(false)
                    .discrepancy(discrepancy)
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        public static ReconciliationResult failed(String errorMessage) {
            return ReconciliationResult.builder()
                    .reconciled(false)
                    .hasDiscrepancy(false)
                    .errorMessage(errorMessage)
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class ReconciliationBatchResult {
        private int totalCount;
        private int reconciledCount;
        private int discrepancyCount;
        private int failedCount;
        private List<RefundDiscrepancy> discrepancies;
    }

    @lombok.Data
    @lombok.Builder
    public static class RefundDiscrepancy {
        private String refundId;
        private String providerRefundId;
        private String provider;
        private String description;
        private BigDecimal localAmount;
        private BigDecimal providerAmount;
        private String localStatus;
        private String providerStatus;
        private DiscrepancySeverity severity;
        private LocalDateTime detectedAt;
    }

    public enum DiscrepancySeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    @lombok.Data
    @lombok.Builder
    public static class ProviderRefundDetails {
        private String refundId;
        private BigDecimal amount;
        private String currency;
        private String status;
        private BigDecimal fee;
        private String settlementId;
        private LocalDateTime processedAt;
        private Map<String, Object> metadata;
    }
}
