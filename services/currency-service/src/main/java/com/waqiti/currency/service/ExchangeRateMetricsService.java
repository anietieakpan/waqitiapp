package com.waqiti.currency.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Exchange Rate Metrics Service
 *
 * Records metrics for exchange rate operations:
 * - Rate availability/unavailability
 * - Rate fetch success/failure
 * - Rate staleness
 * - Rate provider performance
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateMetricsService {

    private final MeterRegistry meterRegistry;

    /**
     * Record rate unavailability
     */
    public void recordRateUnavailability(String fromCurrency, String toCurrency, String correlationId) {
        log.warn("Recording rate unavailability: {}/{} correlationId={}", fromCurrency, toCurrency, correlationId);

        Counter.builder("exchange_rate.unavailable")
                .tag("fromCurrency", fromCurrency)
                .tag("toCurrency", toCurrency)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record rate availability
     */
    public void recordRateAvailability(String fromCurrency, String toCurrency, String correlationId) {
        log.debug("Recording rate availability: {}/{} correlationId={}", fromCurrency, toCurrency, correlationId);

        Counter.builder("exchange_rate.available")
                .tag("fromCurrency", fromCurrency)
                .tag("toCurrency", toCurrency)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record rate fetch success
     */
    public void recordRateFetchSuccess(String fromCurrency, String toCurrency, String provider,
                                      String correlationId) {
        log.debug("Recording rate fetch success: {}/{} provider={} correlationId={}",
                fromCurrency, toCurrency, provider, correlationId);

        Counter.builder("exchange_rate.fetch.success")
                .tag("fromCurrency", fromCurrency)
                .tag("toCurrency", toCurrency)
                .tag("provider", provider)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record rate fetch failure
     */
    public void recordRateFetchFailure(String fromCurrency, String toCurrency, String provider,
                                      String reason, String correlationId) {
        log.error("Recording rate fetch failure: {}/{} provider={} reason={} correlationId={}",
                fromCurrency, toCurrency, provider, reason, correlationId);

        Counter.builder("exchange_rate.fetch.failure")
                .tag("fromCurrency", fromCurrency)
                .tag("toCurrency", toCurrency)
                .tag("provider", provider)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record stale rate usage
     */
    public void recordStaleRateUsage(String fromCurrency, String toCurrency, int ageSeconds,
                                    String correlationId) {
        log.warn("Recording stale rate usage: {}/{} age={}s correlationId={}",
                fromCurrency, toCurrency, ageSeconds, correlationId);

        Counter.builder("exchange_rate.stale_usage")
                .tag("fromCurrency", fromCurrency)
                .tag("toCurrency", toCurrency)
                .register(meterRegistry)
                .increment();
    }
}
