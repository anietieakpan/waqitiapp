package com.waqiti.currency.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.model.alert.CurrencyConversionRecoveryResult;
import com.waqiti.common.service.IdempotencyService;
import com.waqiti.common.service.IncidentManagementService;
import com.waqiti.currency.repository.ConversionAuditRepository;
import com.waqiti.currency.model.ConversionStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Currency Conversion DLQ Recovery Service
 *
 * Handles recovery of failed currency conversion events with:
 * - Exchange rate validation and refresh
 * - Currency pair support verification
 * - Financial compliance checks
 * - Treasury operations integration
 * - Distributed idempotency
 * - Complete audit trails
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyConversionService {

    private final IdempotencyService idempotencyService;
    private final ExchangeRateService exchangeRateService;
    private final ConversionComplianceService complianceService;
    private final ConversionAuditRepository auditRepository;
    private final IncidentManagementService incidentManagementService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of(
        "USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "CNY", "SEK", "NZD",
        "MXN", "SGD", "HKD", "NOK", "KRW", "TRY", "RUB", "INR", "BRL", "ZAR"
    );

    private static final BigDecimal HIGH_VALUE_THRESHOLD = BigDecimal.valueOf(10000);
    private static final int RATE_VALIDITY_SECONDS = 300; // 5 minutes

    /**
     * Main DLQ recovery processing for currency conversion events
     */
    @Transactional
    public CurrencyConversionRecoveryResult processCurrencyConversionEventsDlq(
            String conversionData, String eventKey, String correlationId,
            String conversionId, String sourceCurrency, String targetCurrency,
            BigDecimal amount, Instant timestamp) {

        log.info("Processing currency conversion DLQ: conversionId={} pair={}→{} amount={} correlationId={}",
                conversionId, sourceCurrency, targetCurrency, amount, correlationId);

        Timer.Sample sample = Timer.start(meterRegistry);
        Instant startTime = Instant.now();

        try {
            // Distributed idempotency check
            String idempotencyKey = String.format("currency-conversion-dlq:%s:%s", conversionId, eventKey);
            if (idempotencyService.wasProcessed(idempotencyKey)) {
                log.info("Currency conversion already processed (idempotent): conversionId={} correlationId={}",
                        conversionId, correlationId);
                incrementCounter("currency.conversion.dlq.idempotent");
                return retrieveCachedResult(conversionId, correlationId);
            }

            // Parse conversion data
            JsonNode conversionNode = objectMapper.readTree(conversionData);

            // Validate currency pair support
            CurrencyPairValidation validation = validateCurrencyPair(
                sourceCurrency, targetCurrency, conversionId, correlationId
            );

            if (!validation.isSupported()) {
                return buildUnsupportedPairResult(conversionNode, conversionId,
                    sourceCurrency, targetCurrency, amount, validation.getReason(), correlationId);
            }

            // Get current exchange rate
            ExchangeRateResult rateResult = exchangeRateService.getCurrentRate(
                sourceCurrency, targetCurrency, correlationId
            );

            if (!rateResult.isAvailable()) {
                return buildRateUnavailableResult(conversionNode, conversionId,
                    sourceCurrency, targetCurrency, amount, rateResult.getReason(), correlationId);
            }

            // Validate rate freshness
            if (!isRateFresh(rateResult.getRateTimestamp())) {
                log.warn("Exchange rate is stale: conversionId={} age={}s correlationId={}",
                        conversionId, getAgeSeconds(rateResult.getRateTimestamp()), correlationId);

                // Attempt rate refresh
                ExchangeRateResult refreshedRate = exchangeRateService.refreshRate(
                    sourceCurrency, targetCurrency, correlationId
                );

                if (refreshedRate.isAvailable() && isRateFresh(refreshedRate.getRateTimestamp())) {
                    rateResult = refreshedRate;
                } else {
                    return buildRateStaleResult(conversionNode, conversionId, sourceCurrency,
                        targetCurrency, amount, rateResult, correlationId);
                }
            }

            // Calculate conversion
            ConversionCalculation calculation = calculateConversion(
                amount, rateResult.getRate(), sourceCurrency, targetCurrency, correlationId
            );

            // Compliance validation (high-value, cross-border, AML)
            ComplianceValidationResult compliance = complianceService.validateConversion(
                conversionId, sourceCurrency, targetCurrency, amount,
                calculation.getConvertedAmount(), conversionNode, correlationId
            );

            if (!compliance.isCompliant()) {
                return buildComplianceFailureResult(conversionNode, conversionId, sourceCurrency,
                    targetCurrency, amount, compliance, correlationId);
            }

            // Record audit trail
            auditRepository.save(conversionId, "DLQ_RECOVERY_SUCCESS",
                String.format("Conversion recovered: %s %s → %s %s at rate %.6f",
                    amount, sourceCurrency, calculation.getConvertedAmount(),
                    targetCurrency, rateResult.getRate()),
                correlationId);

            // Mark as processed
            idempotencyService.markProcessed(idempotencyKey, correlationId);

            Duration processingTime = Duration.between(startTime, Instant.now());
            sample.stop(meterRegistry.timer("currency.conversion.dlq.processing.time"));
            incrementCounter("currency.conversion.dlq.success");

            log.info("Currency conversion DLQ recovery successful: conversionId={} " +
                    "converted={} {} rate={} time={}ms correlationId={}",
                    conversionId, calculation.getConvertedAmount(), targetCurrency,
                    rateResult.getRate(), processingTime.toMillis(), correlationId);

            return CurrencyConversionRecoveryResult.builder()
                    .recovered(true)
                    .conversionId(conversionId)
                    .sourceCurrency(sourceCurrency)
                    .targetCurrency(targetCurrency)
                    .sourceAmount(amount)
                    .targetAmount(calculation.getConvertedAmount())
                    .exchangeRate(rateResult.getRate())
                    .rateProvider(rateResult.getProvider())
                    .conversionSuccessful(true)
                    .conversionTimestamp(Instant.now())
                    .appliedFee(calculation.getFee())
                    .feeType(calculation.getFeeType())
                    .rateStale(false)
                    .rateAgeSeconds(getAgeSeconds(rateResult.getRateTimestamp()))
                    .correlationId(correlationId)
                    .build();

        } catch (Exception e) {
            sample.stop(meterRegistry.timer("currency.conversion.dlq.processing.time"));
            incrementCounter("currency.conversion.dlq.error");

            log.error("Error processing currency conversion DLQ: conversionId={} correlationId={}",
                    conversionId, correlationId, e);

            return CurrencyConversionRecoveryResult.builder()
                    .recovered(false)
                    .conversionId(conversionId)
                    .sourceCurrency(sourceCurrency)
                    .targetCurrency(targetCurrency)
                    .sourceAmount(amount)
                    .conversionSuccessful(false)
                    .failureReason(e.getMessage())
                    .correlationId(correlationId)
                    .build();
        }
    }

    /**
     * Validate currency pair support
     */
    private CurrencyPairValidation validateCurrencyPair(String sourceCurrency, String targetCurrency,
                                                        String conversionId, String correlationId) {

        if (!SUPPORTED_CURRENCIES.contains(sourceCurrency)) {
            log.warn("Unsupported source currency: {} conversionId={} correlationId={}",
                    sourceCurrency, conversionId, correlationId);
            return CurrencyPairValidation.builder()
                    .supported(false)
                    .reason("Unsupported source currency: " + sourceCurrency)
                    .build();
        }

        if (!SUPPORTED_CURRENCIES.contains(targetCurrency)) {
            log.warn("Unsupported target currency: {} conversionId={} correlationId={}",
                    targetCurrency, conversionId, correlationId);
            return CurrencyPairValidation.builder()
                    .supported(false)
                    .reason("Unsupported target currency: " + targetCurrency)
                    .build();
        }

        if (sourceCurrency.equals(targetCurrency)) {
            log.warn("Same currency conversion attempted: {} conversionId={} correlationId={}",
                    sourceCurrency, conversionId, correlationId);
            return CurrencyPairValidation.builder()
                    .supported(false)
                    .reason("Cannot convert currency to itself")
                    .build();
        }

        return CurrencyPairValidation.builder()
                .supported(true)
                .build();
    }

    /**
     * Calculate conversion with fees
     */
    private ConversionCalculation calculateConversion(BigDecimal sourceAmount, BigDecimal exchangeRate,
                                                      String sourceCurrency, String targetCurrency,
                                                      String correlationId) {

        // Base conversion
        BigDecimal convertedAmount = sourceAmount.multiply(exchangeRate)
                .setScale(2, RoundingMode.HALF_UP);

        // Calculate spread/fee (0.25% for standard, 0.5% for exotic pairs)
        BigDecimal feePercentage = isExoticPair(sourceCurrency, targetCurrency)
                ? BigDecimal.valueOf(0.005)
                : BigDecimal.valueOf(0.0025);

        BigDecimal fee = convertedAmount.multiply(feePercentage)
                .setScale(2, RoundingMode.HALF_UP);

        String feeType = isExoticPair(sourceCurrency, targetCurrency)
                ? "EXOTIC_PAIR_SPREAD"
                : "STANDARD_SPREAD";

        log.debug("Conversion calculation: {} {} * {} = {} {} (fee: {} {}) correlationId={}",
                sourceAmount, sourceCurrency, exchangeRate, convertedAmount, targetCurrency,
                fee, targetCurrency, correlationId);

        return ConversionCalculation.builder()
                .convertedAmount(convertedAmount)
                .fee(fee)
                .feeType(feeType)
                .effectiveRate(exchangeRate)
                .build();
    }

    /**
     * Check if currency pair is exotic
     */
    private boolean isExoticPair(String sourceCurrency, String targetCurrency) {
        Set<String> majorCurrencies = Set.of("USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF");
        return !majorCurrencies.contains(sourceCurrency) || !majorCurrencies.contains(targetCurrency);
    }

    /**
     * Check if rate is fresh (within 5 minutes)
     */
    private boolean isRateFresh(Instant rateTimestamp) {
        if (rateTimestamp == null) return false;
        return getAgeSeconds(rateTimestamp) <= RATE_VALIDITY_SECONDS;
    }

    /**
     * Get rate age in seconds
     */
    private int getAgeSeconds(Instant rateTimestamp) {
        if (rateTimestamp == null) return Integer.MAX_VALUE;
        return (int) Duration.between(rateTimestamp, Instant.now()).getSeconds();
    }

    /**
     * Build result for unsupported currency pair
     */
    private CurrencyConversionRecoveryResult buildUnsupportedPairResult(
            JsonNode conversionNode, String conversionId, String sourceCurrency,
            String targetCurrency, BigDecimal amount, String reason, String correlationId) {

        log.warn("Unsupported currency pair: conversionId={} {}→{} reason={} correlationId={}",
                conversionId, sourceCurrency, targetCurrency, reason, correlationId);

        auditRepository.save(conversionId, "UNSUPPORTED_PAIR",
                String.format("Currency pair not supported: %s→%s - %s",
                    sourceCurrency, targetCurrency, reason),
                correlationId);

        incrementCounter("currency.conversion.dlq.unsupported_pair");

        return CurrencyConversionRecoveryResult.builder()
                .recovered(false)
                .conversionId(conversionId)
                .sourceCurrency(sourceCurrency)
                .targetCurrency(targetCurrency)
                .sourceAmount(amount)
                .conversionSuccessful(false)
                .failureReason("UNSUPPORTED_CURRENCY_PAIR: " + reason)
                .correlationId(correlationId)
                .build();
    }

    /**
     * Build result for unavailable exchange rate
     */
    private CurrencyConversionRecoveryResult buildRateUnavailableResult(
            JsonNode conversionNode, String conversionId, String sourceCurrency,
            String targetCurrency, BigDecimal amount, String reason, String correlationId) {

        log.warn("Exchange rate unavailable: conversionId={} {}→{} reason={} correlationId={}",
                conversionId, sourceCurrency, targetCurrency, reason, correlationId);

        auditRepository.save(conversionId, "RATE_UNAVAILABLE",
                String.format("Exchange rate unavailable: %s→%s - %s",
                    sourceCurrency, targetCurrency, reason),
                correlationId);

        incrementCounter("currency.conversion.dlq.rate_unavailable");

        return CurrencyConversionRecoveryResult.builder()
                .recovered(false)
                .conversionId(conversionId)
                .sourceCurrency(sourceCurrency)
                .targetCurrency(targetCurrency)
                .sourceAmount(amount)
                .conversionSuccessful(false)
                .failureReason("EXCHANGE_RATE_UNAVAILABLE: " + reason)
                .correlationId(correlationId)
                .build();
    }

    /**
     * Build result for stale exchange rate
     */
    private CurrencyConversionRecoveryResult buildRateStaleResult(
            JsonNode conversionNode, String conversionId, String sourceCurrency,
            String targetCurrency, BigDecimal amount, ExchangeRateResult rateResult,
            String correlationId) {

        int ageSeconds = getAgeSeconds(rateResult.getRateTimestamp());

        log.warn("Exchange rate is stale: conversionId={} {}→{} age={}s correlationId={}",
                conversionId, sourceCurrency, targetCurrency, ageSeconds, correlationId);

        auditRepository.save(conversionId, "RATE_STALE",
                String.format("Exchange rate too old: %s→%s (age: %ds)",
                    sourceCurrency, targetCurrency, ageSeconds),
                correlationId);

        incrementCounter("currency.conversion.dlq.rate_stale");

        return CurrencyConversionRecoveryResult.builder()
                .recovered(false)
                .conversionId(conversionId)
                .sourceCurrency(sourceCurrency)
                .targetCurrency(targetCurrency)
                .sourceAmount(amount)
                .exchangeRate(rateResult.getRate())
                .rateProvider(rateResult.getProvider())
                .conversionSuccessful(false)
                .rateStale(true)
                .rateAgeSeconds(ageSeconds)
                .failureReason("EXCHANGE_RATE_STALE: Rate is " + ageSeconds + " seconds old")
                .correlationId(correlationId)
                .build();
    }

    /**
     * Build result for compliance failure
     */
    private CurrencyConversionRecoveryResult buildComplianceFailureResult(
            JsonNode conversionNode, String conversionId, String sourceCurrency,
            String targetCurrency, BigDecimal amount, ComplianceValidationResult compliance,
            String correlationId) {

        log.warn("Compliance validation failed: conversionId={} violations={} correlationId={}",
                conversionId, compliance.getViolations(), correlationId);

        auditRepository.save(conversionId, "COMPLIANCE_FAILURE",
                String.format("Compliance validation failed: %s",
                    String.join(", ", compliance.getViolations())),
                correlationId);

        incrementCounter("currency.conversion.dlq.compliance_failure");

        return CurrencyConversionRecoveryResult.builder()
                .recovered(false)
                .conversionId(conversionId)
                .sourceCurrency(sourceCurrency)
                .targetCurrency(targetCurrency)
                .sourceAmount(amount)
                .conversionSuccessful(false)
                .failureReason("COMPLIANCE_FAILURE: " + String.join(", ", compliance.getViolations()))
                .correlationId(correlationId)
                .build();
    }

    /**
     * Retrieve cached result (for idempotent requests)
     */
    private CurrencyConversionRecoveryResult retrieveCachedResult(String conversionId,
                                                                  String correlationId) {
        log.debug("Retrieving cached currency conversion result: conversionId={} correlationId={}",
                conversionId, correlationId);

        return CurrencyConversionRecoveryResult.builder()
                .recovered(true)
                .conversionId(conversionId)
                .conversionSuccessful(true)
                .correlationId(correlationId)
                .build();
    }

    /**
     * Increment counter metric
     */
    private void incrementCounter(String metricName) {
        Counter.builder(metricName)
                .register(meterRegistry)
                .increment();
    }

    // Inner classes for structured results

    @lombok.Data
    @lombok.Builder
    private static class CurrencyPairValidation {
        private boolean supported;
        private String reason;
    }

    @lombok.Data
    @lombok.Builder
    public static class ExchangeRateResult {
        private boolean available;
        private BigDecimal rate;
        private String provider;
        private Instant rateTimestamp;
        private String reason;
    }

    @lombok.Data
    @lombok.Builder
    private static class ConversionCalculation {
        private BigDecimal convertedAmount;
        private BigDecimal fee;
        private String feeType;
        private BigDecimal effectiveRate;
    }

    @lombok.Data
    @lombok.Builder
    public static class ComplianceValidationResult {
        private boolean compliant;
        private List<String> violations;
        private String riskLevel;
    }
}
