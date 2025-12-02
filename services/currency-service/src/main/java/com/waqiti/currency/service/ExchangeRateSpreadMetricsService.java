package com.waqiti.currency.service;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Exchange Rate Spread Metrics Service
 *
 * Records spread metrics for exchange rates
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateSpreadMetricsService {

    private final MeterRegistry meterRegistry;

    /**
     * Record exchange rate spread
     */
    public void recordSpread(String fromCurrency, String toCurrency, BigDecimal rate,
                            String correlationId) {
        log.debug("Recording exchange rate spread: {}/{} rate={} correlationId={}",
                fromCurrency, toCurrency, rate, correlationId);

        DistributionSummary.builder("exchange_rate.spread")
                .tag("fromCurrency", fromCurrency)
                .tag("toCurrency", toCurrency)
                .register(meterRegistry)
                .record(rate.doubleValue());
    }
}
