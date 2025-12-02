package com.waqiti.currency.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Currency Metrics Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyMetricsService {

    private final MeterRegistry meterRegistry;

    public void recordSuccessfulConversion(String fromCurrency, String toCurrency,
                                          BigDecimal originalAmount, BigDecimal convertedAmount,
                                          Instant conversionTime, String correlationId) {
        log.debug("Recording successful conversion: {}/{} correlationId={}", fromCurrency, toCurrency, correlationId);

        Counter.builder("currency.conversion.success")
                .tag("fromCurrency", fromCurrency)
                .tag("toCurrency", toCurrency)
                .register(meterRegistry)
                .increment();
    }
}
