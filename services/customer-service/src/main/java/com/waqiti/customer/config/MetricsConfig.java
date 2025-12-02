package com.waqiti.customer.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom Metrics Configuration for Customer Service.
 * Defines business-specific Prometheus metrics for monitoring
 * customer operations, lifecycle events, and service performance.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Configuration
@Slf4j
public class MetricsConfig {

    // Atomic counters for gauge metrics
    private final AtomicInteger activeCustomersCount = new AtomicInteger(0);
    private final AtomicInteger atRiskCustomersCount = new AtomicInteger(0);
    private final AtomicInteger highRiskCustomersCount = new AtomicInteger(0);

    /**
     * Counter for total customer registrations.
     *
     * @param registry MeterRegistry
     * @return Counter for customer registrations
     */
    @Bean
    public Counter customerRegistrationsCounter(MeterRegistry registry) {
        return Counter.builder("customer.registrations.total")
            .description("Total number of customer registrations")
            .tags("service", "customer-service")
            .register(registry);
    }

    /**
     * Counter for total complaints created.
     *
     * @param registry MeterRegistry
     * @return Counter for complaints
     */
    @Bean
    public Counter complaintsCreatedCounter(MeterRegistry registry) {
        return Counter.builder("customer.complaints.created.total")
            .description("Total number of customer complaints created")
            .tags("service", "customer-service")
            .register(registry);
    }

    /**
     * Counter for CFPB complaint submissions.
     *
     * @param registry MeterRegistry
     * @return Counter for CFPB submissions
     */
    @Bean
    public Counter cfpbSubmissionsCounter(MeterRegistry registry) {
        return Counter.builder("customer.complaints.cfpb.submissions.total")
            .description("Total number of CFPB complaint submissions")
            .tags("service", "customer-service")
            .register(registry);
    }

    /**
     * Counter for account closures.
     *
     * @param registry MeterRegistry
     * @return Counter for account closures
     */
    @Bean
    public Counter accountClosuresCounter(MeterRegistry registry) {
        return Counter.builder("customer.account.closures.total")
            .description("Total number of account closure requests")
            .tags("service", "customer-service")
            .register(registry);
    }

    /**
     * Counter for churn predictions.
     *
     * @param registry MeterRegistry
     * @return Counter for churn predictions
     */
    @Bean
    public Counter churnPredictionsCounter(MeterRegistry registry) {
        return Counter.builder("customer.churn.predictions.total")
            .description("Total number of churn prediction calculations")
            .tags("service", "customer-service")
            .register(registry);
    }

    /**
     * Counter for retention campaign executions.
     *
     * @param registry MeterRegistry
     * @return Counter for retention campaigns
     */
    @Bean
    public Counter retentionCampaignsCounter(MeterRegistry registry) {
        return Counter.builder("customer.retention.campaigns.total")
            .description("Total number of retention campaigns executed")
            .tags("service", "customer-service")
            .register(registry);
    }

    /**
     * Counter for winback campaign executions.
     *
     * @param registry MeterRegistry
     * @return Counter for winback campaigns
     */
    @Bean
    public Counter winbackCampaignsCounter(MeterRegistry registry) {
        return Counter.builder("customer.winback.campaigns.total")
            .description("Total number of winback campaigns executed")
            .tags("service", "customer-service")
            .register(registry);
    }

    /**
     * Gauge for active customers count.
     *
     * @param registry MeterRegistry
     * @return Gauge for active customers
     */
    @Bean
    public Gauge activeCustomersGauge(MeterRegistry registry) {
        return Gauge.builder("customer.active.count", activeCustomersCount, AtomicInteger::get)
            .description("Current number of active customers")
            .tags("service", "customer-service")
            .register(registry);
    }

    /**
     * Gauge for at-risk customers count.
     *
     * @param registry MeterRegistry
     * @return Gauge for at-risk customers
     */
    @Bean
    public Gauge atRiskCustomersGauge(MeterRegistry registry) {
        return Gauge.builder("customer.at_risk.count", atRiskCustomersCount, AtomicInteger::get)
            .description("Current number of at-risk customers (churn likelihood)")
            .tags("service", "customer-service", "risk_level", "medium")
            .register(registry);
    }

    /**
     * Gauge for high-risk customers count.
     *
     * @param registry MeterRegistry
     * @return Gauge for high-risk customers
     */
    @Bean
    public Gauge highRiskCustomersGauge(MeterRegistry registry) {
        return Gauge.builder("customer.high_risk.count", highRiskCustomersCount, AtomicInteger::get)
            .description("Current number of high-risk customers (high churn likelihood)")
            .tags("service", "customer-service", "risk_level", "high")
            .register(registry);
    }

    /**
     * Timer for customer lifecycle transitions.
     *
     * @param registry MeterRegistry
     * @return Timer for lifecycle transitions
     */
    @Bean
    public Timer lifecycleTransitionTimer(MeterRegistry registry) {
        return Timer.builder("customer.lifecycle.transition.duration")
            .description("Duration of customer lifecycle state transitions")
            .tags("service", "customer-service")
            .register(registry);
    }

    /**
     * Timer for complaint resolution duration.
     *
     * @param registry MeterRegistry
     * @return Timer for complaint resolution
     */
    @Bean
    public Timer complaintResolutionTimer(MeterRegistry registry) {
        return Timer.builder("customer.complaint.resolution.duration")
            .description("Duration from complaint creation to resolution")
            .tags("service", "customer-service")
            .register(registry);
    }

    /**
     * Timer for account closure processing duration.
     *
     * @param registry MeterRegistry
     * @return Timer for account closure processing
     */
    @Bean
    public Timer accountClosureProcessingTimer(MeterRegistry registry) {
        return Timer.builder("customer.account.closure.processing.duration")
            .description("Duration of account closure processing")
            .tags("service", "customer-service")
            .register(registry);
    }

    /**
     * Timer for churn prediction calculation duration.
     *
     * @param registry MeterRegistry
     * @return Timer for churn predictions
     */
    @Bean
    public Timer churnPredictionTimer(MeterRegistry registry) {
        return Timer.builder("customer.churn.prediction.calculation.duration")
            .description("Duration of churn prediction calculations")
            .tags("service", "customer-service")
            .register(registry);
    }

    /**
     * Provides access to active customers count for updates.
     *
     * @return AtomicInteger for active customers
     */
    public AtomicInteger getActiveCustomersCount() {
        return activeCustomersCount;
    }

    /**
     * Provides access to at-risk customers count for updates.
     *
     * @return AtomicInteger for at-risk customers
     */
    public AtomicInteger getAtRiskCustomersCount() {
        return atRiskCustomersCount;
    }

    /**
     * Provides access to high-risk customers count for updates.
     *
     * @return AtomicInteger for high-risk customers
     */
    public AtomicInteger getHighRiskCustomersCount() {
        return highRiskCustomersCount;
    }
}
