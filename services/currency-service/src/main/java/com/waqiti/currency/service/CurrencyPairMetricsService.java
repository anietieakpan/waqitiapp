package com.waqiti.currency.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Currency Pair Metrics Service
 *
 * Records metrics for currency pair operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyPairMetricsService {

    private final MeterRegistry meterRegistry;

    /**
     * Record unsupported currency pair
     */
    public void recordUnsupportedPair(String fromCurrency, String toCurrency, String correlationId) {
        log.warn("Recording unsupported currency pair: {}/{} correlationId={}", fromCurrency, toCurrency, correlationId);

        Counter.builder("currency.pair.unsupported")
                .tag("fromCurrency", fromCurrency)
                .tag("toCurrency", toCurrency)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record supported pair usage
     */
    public void recordSupportedPairUsage(String fromCurrency, String toCurrency, String correlationId) {
        log.debug("Recording supported pair usage: {}/{} correlationId={}", fromCurrency, toCurrency, correlationId);

        Counter.builder("currency.pair.usage")
                .tag("fromCurrency", fromCurrency)
                .tag("toCurrency", toCurrency)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record currency pair conversion
     */
    public void recordPairConversion(String fromCurrency, String toCurrency, boolean success,
                                    String correlationId) {
        log.debug("Recording pair conversion: {}/{} success={} correlationId={}",
                fromCurrency, toCurrency, success, correlationId);

        Counter.builder("currency.pair.conversion")
                .tag("fromCurrency", fromCurrency)
                .tag("toCurrency", toCurrency)
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .increment();
    }
}
