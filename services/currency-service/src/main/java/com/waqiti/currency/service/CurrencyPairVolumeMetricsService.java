package com.waqiti.currency.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Currency Pair Volume Metrics Service
 *
 * Tracks conversion volume for currency pairs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyPairVolumeMetricsService {

    private final MeterRegistry meterRegistry;

    /**
     * Record conversion volume
     */
    public void recordVolume(String fromCurrency, String toCurrency, BigDecimal amount,
                            String correlationId) {
        log.debug("Recording conversion volume: {}/{} amount={} correlationId={}",
                fromCurrency, toCurrency, amount, correlationId);

        DistributionSummary.builder("currency.pair.volume")
                .tag("fromCurrency", fromCurrency)
                .tag("toCurrency", toCurrency)
                .register(meterRegistry)
                .record(amount.doubleValue());

        Counter.builder("currency.pair.volume.count")
                .tag("fromCurrency", fromCurrency)
                .tag("toCurrency", toCurrency)
                .register(meterRegistry)
                .increment();
    }
}
