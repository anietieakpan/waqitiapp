package com.waqiti.account.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Account Metrics Service
 * Tracks account-related metrics for monitoring
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountMetricsService {
    
    private final MeterRegistry meterRegistry;
    
    /**
     * Record account activation metric
     */
    public void recordAccountActivated(String accountTier) {
        log.debug("Recording account activation metric: tier={}", accountTier);
        
        Counter.builder("account.activations")
                .tag("tier", accountTier)
                .description("Number of account activations by tier")
                .register(meterRegistry)
                .increment();
    }
}
