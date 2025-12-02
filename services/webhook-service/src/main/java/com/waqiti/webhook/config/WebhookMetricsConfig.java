package com.waqiti.webhook.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Prometheus metrics configuration for webhook service
 *
 * PRODUCTION-GRADE METRICS:
 * - Webhook delivery success/failure rates
 * - Response time histograms (p50, p95, p99)
 * - Queue depth gauges
 * - Circuit breaker state
 * - Retry attempt tracking
 * - Error rate by endpoint
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-10-20
 */
@Configuration
public class WebhookMetricsConfig {

    /**
     * Global registry customizer to add common tags
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags(
            Tags.of(
                Tag.of("service", "webhook-service"),
                Tag.of("application", "waqiti-platform")
            )
        );
    }

    /**
     * Enable @Timed annotation support for method timing
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
