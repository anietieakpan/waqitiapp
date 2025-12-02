package com.waqiti.customer.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Observability Configuration for Customer Service.
 * Configures distributed tracing, metrics collection, and MDC logging
 * with correlation IDs for request tracking.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Configuration
@Slf4j
public class ObservabilityConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    /**
     * Customizes the meter registry with common tags and configurations.
     * Adds application name and environment tags to all metrics.
     *
     * @return MeterRegistryCustomizer
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> {
            registry.config()
                .commonTags(
                    "application", applicationName,
                    "environment", activeProfile,
                    "service", "customer-service"
                )
                .meterFilter(MeterFilter.deny(id -> {
                    String uri = id.getTag("uri");
                    return uri != null && (
                        uri.startsWith("/actuator") ||
                        uri.startsWith("/swagger-ui") ||
                        uri.startsWith("/v3/api-docs")
                    );
                }));

            log.info("Meter registry customized with common tags: application={}, environment={}",
                applicationName, activeProfile);
        };
    }

    /**
     * Configures timer percentiles for latency tracking.
     * Tracks p50, p95, and p99 latencies for HTTP requests.
     *
     * @return MeterRegistryCustomizer for timer configuration
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> timerPercentiles() {
        return registry -> registry.config()
            .meterFilter(new MeterFilter() {
                @Override
                public io.micrometer.core.instrument.distribution.DistributionStatisticConfig configure(
                    io.micrometer.core.instrument.Meter.Id id,
                    io.micrometer.core.instrument.distribution.DistributionStatisticConfig config) {

                    if (id.getName().startsWith("http.server.requests")) {
                        return io.micrometer.core.instrument.distribution.DistributionStatisticConfig.builder()
                            .percentiles(0.5, 0.95, 0.99)
                            .percentilesHistogram(true)
                            .build()
                            .merge(config);
                    }
                    return config;
                }
            });
    }

    /**
     * Creates a filter for correlation ID management.
     * Extracts or generates correlation IDs and adds them to MDC.
     *
     * @return CorrelationIdFilter
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorrelationIdFilter correlationIdFilter() {
        log.info("Correlation ID filter initialized");
        return new CorrelationIdFilter();
    }

    /**
     * Filter implementation for correlation ID and request ID management.
     * Ensures all requests have unique identifiers for distributed tracing.
     */
    public static class CorrelationIdFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
        ) throws ServletException, IOException {

            try {
                // Extract or generate correlation ID
                String correlationId = request.getHeader(CORRELATION_ID_HEADER);
                if (correlationId == null || correlationId.isBlank()) {
                    correlationId = UUID.randomUUID().toString();
                }

                // Generate request ID
                String requestId = UUID.randomUUID().toString();

                // Add to MDC for logging
                MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
                MDC.put(REQUEST_ID_MDC_KEY, requestId);

                // Add to response headers
                response.setHeader(CORRELATION_ID_HEADER, correlationId);
                response.setHeader(REQUEST_ID_HEADER, requestId);

                // Add to request attributes for downstream access
                request.setAttribute(CORRELATION_ID_MDC_KEY, correlationId);
                request.setAttribute(REQUEST_ID_MDC_KEY, requestId);

                log.debug("Request processing started: correlationId={}, requestId={}, uri={}",
                    correlationId, requestId, request.getRequestURI());

                filterChain.doFilter(request, response);

                log.debug("Request processing completed: correlationId={}, requestId={}, status={}",
                    correlationId, requestId, response.getStatus());

            } finally {
                // Clear MDC to prevent memory leaks
                MDC.remove(CORRELATION_ID_MDC_KEY);
                MDC.remove(REQUEST_ID_MDC_KEY);
            }
        }
    }
}
