package com.waqiti.currency.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Currency Conversion Metrics Service
 *
 * Records comprehensive metrics for currency conversion operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyConversionMetricsService {

    private final MeterRegistry meterRegistry;

    /**
     * Record conversion processing
     */
    public void recordProcessing(String fromCurrency, String toCurrency, String status,
                                 BigDecimal originalAmount, BigDecimal convertedAmount,
                                 Duration processingTime, String correlationId) {
        log.debug("Recording conversion processing: {}/{} status={} time={}ms correlationId={}",
                fromCurrency, toCurrency, status, processingTime != null ? processingTime.toMillis() : 0, correlationId);

        Counter.builder("currency.conversion.processing")
                .tag("fromCurrency", fromCurrency)
                .tag("toCurrency", toCurrency)
                .tag("status", status)
                .register(meterRegistry)
                .increment();

        if (processingTime != null) {
            Timer.builder("currency.conversion.processing.duration")
                    .tag("fromCurrency", fromCurrency)
                    .tag("toCurrency", toCurrency)
                    .tag("status", status)
                    .register(meterRegistry)
                    .record(processingTime);
        }
    }

    /**
     * Record conversion success
     */
    public void recordSuccess(String fromCurrency, String toCurrency, BigDecimal amount,
                             String correlationId) {
        log.debug("Recording conversion success: {}/{} amount={} correlationId={}",
                fromCurrency, toCurrency, amount, correlationId);

        Counter.builder("currency.conversion.success")
                .tag("fromCurrency", fromCurrency)
                .tag("toCurrency", toCurrency)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record conversion failure
     */
    public void recordFailure(String fromCurrency, String toCurrency, String failureReason,
                             String correlationId) {
        log.error("Recording conversion failure: {}/{} reason={} correlationId={}",
                fromCurrency, toCurrency, failureReason, correlationId);

        Counter.builder("currency.conversion.failure")
                .tag("fromCurrency", fromCurrency)
                .tag("toCurrency", toCurrency)
                .tag("reason", failureReason)
                .register(meterRegistry)
                .increment();
    }
}
