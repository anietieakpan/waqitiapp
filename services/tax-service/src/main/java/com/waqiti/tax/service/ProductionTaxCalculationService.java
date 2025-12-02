package com.waqiti.tax.service;

import com.waqiti.tax.client.AvalaraTaxClient;
import com.waqiti.tax.dto.TaxCalculationRequest;
import com.waqiti.tax.dto.TaxCalculationResponse;
import com.waqiti.tax.entity.TaxTransaction;
import com.waqiti.tax.repository.TaxTransactionRepository;
// CRITICAL P0 FIX: Add payment service client for payment confirmation
import org.springframework.web.client.RestTemplate;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production Tax Calculation Service with Avalara Integration
 *
 * ENTERPRISE FEATURES:
 * - Real-time tax calculation via Avalara AvaTax API
 * - Fallback to internal tax rules when Avalara is unavailable
 * - Cross-validation between Avalara and internal calculations
 * - Automatic tax transaction recording
 * - Kafka event publishing for tax events
 * - Scheduled transaction commitment
 * - Tax reconciliation and audit trail
 *
 * CALCULATION FLOW:
 * 1. Attempt Avalara calculation (primary)
 * 2. If Avalara fails, use internal calculation (fallback)
 * 3. For critical transactions, cross-validate both methods
 * 4. Record transaction in database
 * 5. Publish tax calculation event to Kafka
 * 6. Schedule for commitment (if payment confirmed)
 *
 * COMPLIANCE:
 * - SOX: Complete audit trail of all tax calculations
 * - Tax Authority: Automatic filing preparation
 * - Multi-jurisdiction: Handles all US states + international
 *
 * @author Waqiti Tax Team
 * @version 3.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductionTaxCalculationService {

    private final AvalaraTaxClient avalaraTaxClient;
    private final TaxCalculationService internalTaxCalculationService;
    private final TaxTransactionRepository taxTransactionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    // CRITICAL P0 FIX: Add RestTemplate for payment service integration
    private final RestTemplate restTemplate;

    @Value("${tax.avalara.enabled:true}")
    private boolean avalaraEnabled;

    @Value("${tax.validation.cross-check.enabled:true}")
    private boolean crossCheckEnabled;

    @Value("${tax.validation.tolerance:0.10}")
    private BigDecimal validationTolerance;

    @Value("${tax.minimum.amount.for.avalara:1.00}")
    private BigDecimal minimumAmountForAvalara;

    // CRITICAL P0 FIX: Payment service configuration
    @Value("${payment.service.url:http://payment-service:8080}")
    private String paymentServiceUrl;

    /**
     * Calculate tax with intelligent routing
     *
     * DECISION TREE:
     * 1. Amount < $1.00 → Internal calculation (skip Avalara to save API calls)
     * 2. Avalara enabled AND US address → Avalara (most accurate)
     * 3. Avalara disabled OR international → Internal calculation
     * 4. Critical transaction (>$10,000) → Cross-validate both methods
     */
    @Transactional
    @CircuitBreaker(name = "tax-calculation", fallbackMethod = "calculateTaxFallback")
    public TaxCalculationResponse calculateTax(TaxCalculationRequest request) {
        log.info("Starting tax calculation for transaction: {}, amount: ${}",
            request.getTransactionId(), request.getAmount());

        try {
            // Validation
            validateTaxCalculationRequest(request);

            // Determine calculation method
            TaxCalculationResponse response;

            if (shouldUseAvalara(request)) {
                response = calculateWithAvalara(request);
            } else {
                response = calculateWithInternalRules(request);
            }

            // Cross-validation for high-value transactions
            if (shouldCrossValidate(request)) {
                performCrossValidation(request, response);
            }

            // Record transaction
            TaxTransaction transaction = recordTaxTransaction(request, response);

            // Publish event
            publishTaxCalculationEvent(transaction, response);

            log.info("Tax calculation completed for {}: Total tax=${}, Source={}",
                request.getTransactionId(), response.getTotalTaxAmount(), response.getSource());

            return response;

        } catch (Exception e) {
            log.error("Tax calculation failed for transaction: {}", request.getTransactionId(), e);
            throw new TaxCalculationException("Tax calculation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Calculate tax using Avalara AvaTax API
     */
    private TaxCalculationResponse calculateWithAvalara(TaxCalculationRequest request) {
        log.debug("Using Avalara for tax calculation");

        try {
            TaxCalculationResponse response = avalaraTaxClient.calculateTax(request);

            if (response != null && response.getTotalTaxAmount() != null) {
                return response;
            } else {
                log.warn("Avalara returned null/invalid response, falling back to internal");
                return calculateWithInternalRules(request);
            }

        } catch (Exception e) {
            log.error("Avalara calculation failed, falling back to internal: {}", e.getMessage());
            return calculateWithInternalRules(request);
        }
    }

    /**
     * Calculate tax using internal tax rules
     */
    private TaxCalculationResponse calculateWithInternalRules(TaxCalculationRequest request) {
        log.debug("Using internal tax rules for calculation");
        return internalTaxCalculationService.calculateTax(request);
    }

    /**
     * Cross-validate Avalara against internal calculation
     *
     * CRITICAL: Detects discrepancies that might indicate:
     * - Outdated internal tax rules
     * - Avalara configuration issues
     * - Complex multi-jurisdiction scenarios
     */
    @Async
    public CompletableFuture<Void> performCrossValidation(TaxCalculationRequest request,
                                                            TaxCalculationResponse primaryResponse) {
        log.info("Performing cross-validation for transaction: {}", request.getTransactionId());

        try {
            TaxCalculationResponse secondaryResponse;

            if ("AVALARA".equals(primaryResponse.getSource())) {
                // Primary was Avalara, validate against internal
                secondaryResponse = calculateWithInternalRules(request);
            } else {
                // Primary was internal, validate against Avalara
                secondaryResponse = calculateWithAvalara(request);
            }

            // Compare results
            BigDecimal difference = primaryResponse.getTotalTaxAmount()
                .subtract(secondaryResponse.getTotalTaxAmount()).abs();

            if (difference.compareTo(validationTolerance) > 0) {
                log.warn("CROSS-VALIDATION DISCREPANCY DETECTED for transaction {}: " +
                    "Primary={} ({}), Secondary={} ({}), Difference=${}",
                    request.getTransactionId(),
                    primaryResponse.getTotalTaxAmount(), primaryResponse.getSource(),
                    secondaryResponse.getTotalTaxAmount(), secondaryResponse.getSource(),
                    difference);

                // Publish alert for compliance team
                publishTaxDiscrepancyAlert(request, primaryResponse, secondaryResponse, difference);
            } else {
                log.debug("Cross-validation passed: Difference=${} (within tolerance={})",
                    difference, validationTolerance);
            }

        } catch (Exception e) {
            log.error("Cross-validation failed for transaction: {}", request.getTransactionId(), e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Commit tax transaction to Avalara after payment confirmation
     *
     * USAGE: Call this method after payment is successfully processed
     * - Records transaction in Avalara for tax filing
     * - Cannot be undone (must void instead)
     */
    @Transactional
    public void commitTaxTransaction(UUID transactionId) {
        log.info("Committing tax transaction: {}", transactionId);

        try {
            TaxTransaction taxTransaction = taxTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new TaxCalculationException("Tax transaction not found: " + transactionId));

            if ("AVALARA".equals(taxTransaction.getSource())) {
                // Commit to Avalara
                String avalaraTransactionCode = taxTransaction.getExternalTransactionId();

                if (avalaraTransactionCode != null) {
                    avalaraTaxClient.commitTransaction(avalaraTransactionCode);

                    taxTransaction.setStatus("COMMITTED");
                    taxTransaction.setCommittedAt(LocalDateTime.now());
                    taxTransactionRepository.save(taxTransaction);

                    log.info("Tax transaction committed successfully: {}", transactionId);
                } else {
                    log.error("Cannot commit: No Avalara transaction code found for {}", transactionId);
                }
            } else {
                // Internal transaction - just mark as committed
                taxTransaction.setStatus("COMMITTED");
                taxTransaction.setCommittedAt(LocalDateTime.now());
                taxTransactionRepository.save(taxTransaction);

                log.info("Internal tax transaction marked as committed: {}", transactionId);
            }

        } catch (Exception e) {
            log.error("Failed to commit tax transaction: {}", transactionId, e);
            throw new TaxCalculationException("Failed to commit tax transaction", e);
        }
    }

    /**
     * Void tax transaction (for refunds/cancellations)
     *
     * USAGE: Call when order is cancelled or refunded
     */
    @Transactional
    public void voidTaxTransaction(UUID transactionId, String reason) {
        log.info("Voiding tax transaction: {}, reason: {}", transactionId, reason);

        try {
            TaxTransaction taxTransaction = taxTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new TaxCalculationException("Tax transaction not found: " + transactionId));

            if ("AVALARA".equals(taxTransaction.getSource())) {
                // Void in Avalara
                String avalaraTransactionCode = taxTransaction.getExternalTransactionId();

                if (avalaraTransactionCode != null) {
                    avalaraTaxClient.voidTransaction(avalaraTransactionCode, reason);
                }
            }

            // Update local record
            taxTransaction.setStatus("VOIDED");
            taxTransaction.setVoidedAt(LocalDateTime.now());
            taxTransaction.setVoidReason(reason);
            taxTransactionRepository.save(taxTransaction);

            log.info("Tax transaction voided successfully: {}", transactionId);

        } catch (Exception e) {
            log.error("Failed to void tax transaction: {}", transactionId, e);
            throw new TaxCalculationException("Failed to void tax transaction", e);
        }
    }

    /**
     * Scheduled job to commit pending transactions
     *
     * RUNS: Every hour
     * FUNCTION: Auto-commits transactions that have been paid but not yet committed
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void commitPendingTransactions() {
        log.info("Starting scheduled commit of pending tax transactions");

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(1);

            List<TaxTransaction> pendingTransactions = taxTransactionRepository
                .findByStatusAndCreatedAtBefore("CALCULATED", cutoffTime);

            int committed = 0;
            int failed = 0;

            for (TaxTransaction transaction : pendingTransactions) {
                try {
                    // Check if payment was confirmed
                    if (isPaymentConfirmed(transaction.getTransactionId())) {
                        commitTaxTransaction(transaction.getId());
                        committed++;
                    }
                } catch (Exception e) {
                    log.error("Failed to commit transaction: {}", transaction.getId(), e);
                    failed++;
                }
            }

            log.info("Scheduled commit completed: {} committed, {} failed out of {} pending",
                committed, failed, pendingTransactions.size());

        } catch (Exception e) {
            log.error("Scheduled commit job failed", e);
        }
    }

    /**
     * Validate address before tax calculation
     *
     * IMPROVES ACCURACY: Corrected addresses = more precise tax jurisdiction
     */
    public Map<String, Object> validateAddress(Map<String, String> address) {
        log.debug("Validating address for tax calculation");

        if (avalaraEnabled && isUSAddress(address)) {
            try {
                return avalaraTaxClient.validateAddress(address);
            } catch (Exception e) {
                log.warn("Avalara address validation failed, using original address", e);
            }
        }

        return new HashMap<>(address);
    }

    /**
     * Get tax rates for specific address
     *
     * USAGE: For quick rate lookup without full transaction
     */
    public Map<String, BigDecimal> getTaxRates(String address, String postalCode,
                                                String city, String region, String country) {
        log.debug("Getting tax rates for: {}, {}, {}", city, region, postalCode);

        if (avalaraEnabled && "US".equalsIgnoreCase(country)) {
            try {
                return avalaraTaxClient.getTaxRates(address, postalCode, city, region, country);
            } catch (Exception e) {
                log.warn("Failed to get tax rates from Avalara", e);
            }
        }

        // Fallback to internal rates
        Map<String, BigDecimal> fallbackRates = new HashMap<>();
        fallbackRates.put("totalRate", new BigDecimal("0.08")); // Default 8%
        return fallbackRates;
    }

    /**
     * Record tax transaction in database
     */
    private TaxTransaction recordTaxTransaction(TaxCalculationRequest request,
                                                TaxCalculationResponse response) {

        TaxTransaction transaction = TaxTransaction.builder()
            .transactionId(request.getTransactionId())
            .userId(request.getUserId())
            .jurisdiction(response.getJurisdiction())
            .transactionType(request.getTransactionType())
            .transactionAmount(request.getAmount())
            .taxAmount(response.getTotalTaxAmount())
            .taxableAmount(response.getTaxableAmount())
            .effectiveTaxRate(response.getEffectiveTaxRate())
            .taxBreakdown(response.getTaxBreakdown())
            .calculationDate(LocalDateTime.now())
            .taxYear(Year.now().getValue())
            .status("CALCULATED")
            .source(response.getSource())
            .externalTransactionId(extractExternalTransactionId(response))
            .currency(response.getCurrency())
            .metadata(response.getMetadata())
            .build();

        return taxTransactionRepository.save(transaction);
    }

    /**
     * Extract external transaction ID from response metadata
     */
    private String extractExternalTransactionId(TaxCalculationResponse response) {
        if (response.getMetadata() != null) {
            return response.getMetadata().get("avalaraTransactionId");
        }
        return null;
    }

    /**
     * Publish tax calculation event to Kafka
     */
    private void publishTaxCalculationEvent(TaxTransaction transaction,
                                            TaxCalculationResponse response) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "TAX_CALCULATED");
            event.put("transactionId", transaction.getTransactionId());
            event.put("userId", transaction.getUserId());
            event.put("taxAmount", transaction.getTaxAmount());
            event.put("source", transaction.getSource());
            event.put("timestamp", LocalDateTime.now());

            kafkaTemplate.send("tax-events", transaction.getTransactionId().toString(), event);

            log.debug("Published tax calculation event to Kafka");

        } catch (Exception e) {
            log.error("Failed to publish tax calculation event", e);
            // Don't fail the transaction if Kafka publish fails
        }
    }

    /**
     * Publish tax discrepancy alert to Kafka
     */
    private void publishTaxDiscrepancyAlert(TaxCalculationRequest request,
                                            TaxCalculationResponse primary,
                                            TaxCalculationResponse secondary,
                                            BigDecimal difference) {
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("eventType", "TAX_DISCREPANCY_DETECTED");
            alert.put("transactionId", request.getTransactionId());
            alert.put("primarySource", primary.getSource());
            alert.put("primaryAmount", primary.getTotalTaxAmount());
            alert.put("secondarySource", secondary.getSource());
            alert.put("secondaryAmount", secondary.getTotalTaxAmount());
            alert.put("difference", difference);
            alert.put("transactionAmount", request.getAmount());
            alert.put("timestamp", LocalDateTime.now());
            alert.put("severity", "HIGH");

            kafkaTemplate.send("tax-alerts", request.getTransactionId().toString(), alert);

            log.info("Published tax discrepancy alert to Kafka");

        } catch (Exception e) {
            log.error("Failed to publish tax discrepancy alert", e);
        }
    }

    /**
     * Validate tax calculation request
     */
    private void validateTaxCalculationRequest(TaxCalculationRequest request) {
        if (request.getTransactionId() == null) {
            throw new IllegalArgumentException("Transaction ID is required");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (request.getTransactionDate() == null) {
            throw new IllegalArgumentException("Transaction date is required");
        }
    }

    /**
     * Determine if Avalara should be used for this request
     */
    private boolean shouldUseAvalara(TaxCalculationRequest request) {
        // Don't use Avalara if disabled
        if (!avalaraEnabled) {
            return false;
        }

        // Don't use Avalara for small amounts (save API calls)
        if (request.getAmount().compareTo(minimumAmountForAvalara) < 0) {
            log.debug("Skipping Avalara for small amount: ${}", request.getAmount());
            return false;
        }

        // Use Avalara for US addresses
        if (request.getDestinationAddress() != null) {
            String country = request.getDestinationAddress().get("country");
            return "US".equalsIgnoreCase(country);
        }

        // Default to Avalara for unspecified locations (assume US)
        return true;
    }

    /**
     * Determine if cross-validation is needed
     */
    private boolean shouldCrossValidate(TaxCalculationRequest request) {
        if (!crossCheckEnabled) {
            return false;
        }

        // Cross-validate high-value transactions (>= $10,000)
        BigDecimal highValueThreshold = new BigDecimal("10000.00");
        return request.getAmount().compareTo(highValueThreshold) >= 0;
    }

    /**
     * Check if address is US-based
     */
    private boolean isUSAddress(Map<String, String> address) {
        String country = address.get("country");
        return country == null || "US".equalsIgnoreCase(country);
    }

    /**
     * CRITICAL P0 FIX: Check if payment was confirmed for transaction
     *
     * Queries payment service to verify payment status before tax commitment.
     * Implements circuit breaker pattern for resilience.
     *
     * @param transactionId Transaction ID to check
     * @return true if payment confirmed, false otherwise
     */
    @CircuitBreaker(name = "payment-service", fallbackMethod = "isPaymentConfirmedFallback")
    private boolean isPaymentConfirmed(UUID transactionId) {
        try {
            log.debug("Checking payment confirmation for transaction: {}", transactionId);

            // Call payment service API to check payment status
            String url = String.format("%s/api/v1/payments/status/%s", paymentServiceUrl, transactionId);

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null && response.containsKey("status")) {
                String status = (String) response.get("status");

                // Payment is confirmed if status is COMPLETED or SETTLED
                boolean isConfirmed = "COMPLETED".equalsIgnoreCase(status) ||
                    "SETTLED".equalsIgnoreCase(status) ||
                    "SUCCESS".equalsIgnoreCase(status);

                log.info("Payment confirmation check - Transaction: {}, Status: {}, Confirmed: {}",
                    transactionId, status, isConfirmed);

                return isConfirmed;
            }

            log.warn("Payment status check returned invalid response for transaction: {}", transactionId);
            return false;

        } catch (Exception e) {
            log.error("Failed to check payment confirmation for transaction: {}", transactionId, e);
            // Fail closed - don't auto-commit if we can't verify payment
            return false;
        }
    }

    /**
     * CRITICAL P0 FIX: Fallback method when payment service is unavailable
     *
     * Returns false to prevent auto-commit when payment service is down.
     * This ensures we don't commit tax transactions for unconfirmed payments.
     *
     * @param transactionId Transaction ID
     * @param ex Exception from circuit breaker
     * @return false (fail closed for safety)
     */
    private boolean isPaymentConfirmedFallback(UUID transactionId, Exception ex) {
        log.warn("Payment service unavailable for transaction: {} - Falling back to manual confirmation. Error: {}",
            transactionId, ex.getMessage());

        // CRITICAL: Fail closed - require manual confirmation when payment service is down
        // This prevents auto-committing tax transactions for potentially failed payments
        return false;
    }

    /**
     * Fallback method when primary calculation fails
     */
    private TaxCalculationResponse calculateTaxFallback(TaxCalculationRequest request, Exception e) {
        log.error("All tax calculation methods failed, using emergency fallback", e);

        // Emergency fallback: 8% flat rate
        BigDecimal emergencyRate = new BigDecimal("0.08");
        BigDecimal taxAmount = request.getAmount().multiply(emergencyRate);

        return TaxCalculationResponse.builder()
            .transactionId(request.getTransactionId())
            .totalTaxAmount(taxAmount)
            .taxableAmount(request.getAmount())
            .totalAmount(request.getAmount().add(taxAmount))
            .effectiveTaxRate(new BigDecimal("8.00"))
            .calculationDate(LocalDateTime.now())
            .currency(request.getCurrency())
            .status("FALLBACK")
            .source("EMERGENCY_FALLBACK")
            .build();
    }

    /**
     * Custom exception for tax calculation errors
     */
    public static class TaxCalculationException extends RuntimeException {
        public TaxCalculationException(String message) {
            super(message);
        }

        public TaxCalculationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
