package com.waqiti.common.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for metrics registry beans
 */
@Configuration
public class MetricsConfiguration {
    
    /**
     * Provides a MeterRegistry bean for metrics collection
     */
    @Bean
    @ConditionalOnMissingBean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
    
    /**
     * Legacy alias for MetricsRegistry (for backwards compatibility)
     */
    @Bean(name = "metricsRegistry")
    @ConditionalOnMissingBean(name = "metricsRegistry")
    public MeterRegistry metricsRegistry() {
        return meterRegistry();
    }
}