package com.waqiti.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compliance Metrics - Production-Ready Regulatory and Compliance Monitoring
 *
 * Comprehensive metrics collection for regulatory compliance monitoring,
 * audit trail generation, and regulatory reporting requirements.
 *
 * REGULATORY REQUIREMENTS:
 * - FinCEN SAR filing tracking
 * - IRS Form 8300 filing tracking
 * - OFAC/AML screening metrics
 * - Account freeze success/failure rates
 * - Compliance incident response times
 *
 * METRICS COLLECTED:
 * - Account freeze failures by reason and severity
 * - Regulatory filing success/failure rates
 * - Compliance screening throughput
 * - Alert response times
 * - False positive rates
 *
 * FEATURES:
 * - Real-time compliance monitoring
 * - SLA tracking for regulatory responses
 * - Audit trail metrics
 * - Dimensional metrics with regulatory context
 * - Integration with Prometheus/Grafana
 *
 * BUSINESS VALUE:
 * - Regulatory compliance assurance
 * - Early detection of compliance violations
 * - Audit readiness
 * - Risk management
 * - Regulatory reporting automation
 *
 * @author Waqiti Compliance Team
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ComplianceMetrics {

    private final MeterRegistry meterRegistry;

    // Thread-safe metric caches
    private final Map<String, Counter> freezeFailureCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> regulatoryFilingCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> complianceResponseTimers = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> activeIncidentGauges = new ConcurrentHashMap<>();

    @PostConstruct
    public void initMetrics() {
        log.info("Initializing ComplianceMetrics with MeterRegistry: {}", meterRegistry.getClass().getSimpleName());

        // Register baseline gauges
        Gauge.builder("compliance.active_incidents.total", activeIncidentGauges,
                        map -> map.values().stream().mapToInt(AtomicInteger::get).sum())
                .description("Total number of active compliance incidents requiring review")
                .register(meterRegistry);
    }

    /**
     * Record account freeze failure
     *
     * Tracks failures in account freeze operations - critical for compliance
     * as freeze failures may result in regulatory violations (e.g., allowing
     * sanctioned entities to continue transactions).
     *
     * @param freezeReason Reason for freeze (SANCTIONS_MATCH, AML_SUSPICIOUS_ACTIVITY, etc.)
     * @param complianceSeverity Compliance severity level (CRITICAL_REGULATORY, HIGH_REGULATORY, etc.)
     * @param isRegulatoryFreeze Whether this is a regulatory freeze (OFAC, sanctions, etc.)
     */
    public void recordFreezeFailure(String freezeReason, String complianceSeverity, boolean isRegulatoryFreeze) {
        try {
            String metricKey = String.format("freeze_failure.%s.%s.%s",
                    freezeReason, complianceSeverity, isRegulatoryFreeze);

            Counter counter = freezeFailureCounters.computeIfAbsent(metricKey, key ->
                    Counter.builder("compliance.freeze.failures.total")
                            .description("Total number of account freeze failures - CRITICAL COMPLIANCE METRIC")
                            .tag("freeze_reason", freezeReason)
                            .tag("compliance_severity", complianceSeverity)
                            .tag("regulatory_freeze", String.valueOf(isRegulatoryFreeze))
                            .tag("alert_priority", isRegulatoryFreeze ? "CRITICAL" : "HIGH")
                            .register(meterRegistry)
            );

            counter.increment();

            log.error("COMPLIANCE_METRIC: Account freeze failure recorded - Reason: {}, Severity: {}, Regulatory: {}, Total: {}",
                    freezeReason, complianceSeverity, isRegulatoryFreeze, counter.count());

            // Update active incidents gauge if regulatory
            if (isRegulatoryFreeze) {
                activeIncidentGauges.computeIfAbsent("regulatory_freeze_failures", k -> new AtomicInteger(0))
                        .incrementAndGet();
            }

        } catch (Exception e) {
            log.error("Failed to record freeze failure metric - Reason: {}, Severity: {}, Regulatory: {}",
                    freezeReason, complianceSeverity, isRegulatoryFreeze, e);
        }
    }

    /**
     * Record successful account freeze
     *
     * @param freezeReason Reason for freeze
     * @param complianceSeverity Compliance severity
     * @param responseTimeMs Time taken to execute freeze (ms)
     */
    public void recordFreezeSuccess(String freezeReason, String complianceSeverity, long responseTimeMs) {
        try {
            String metricKey = String.format("freeze_success.%s.%s", freezeReason, complianceSeverity);

            Counter counter = freezeFailureCounters.computeIfAbsent(metricKey, key ->
                    Counter.builder("compliance.freeze.success.total")
                            .description("Total number of successful account freezes")
                            .tag("freeze_reason", freezeReason)
                            .tag("compliance_severity", complianceSeverity)
                            .register(meterRegistry)
            );

            counter.increment();

            // Record response time
            String timerKey = String.format("freeze_response.%s", freezeReason);
            Timer timer = complianceResponseTimers.computeIfAbsent(timerKey, key ->
                    Timer.builder("compliance.freeze.response.duration")
                            .description("Account freeze response time")
                            .tag("freeze_reason", freezeReason)
                            .register(meterRegistry)
            );

            timer.record(responseTimeMs, TimeUnit.MILLISECONDS);

            log.info("COMPLIANCE_METRIC: Account freeze success recorded - Reason: {}, Severity: {}, ResponseTime: {}ms",
                    freezeReason, complianceSeverity, responseTimeMs);

        } catch (Exception e) {
            log.error("Failed to record freeze success metric - Reason: {}, Severity: {}",
                    freezeReason, complianceSeverity, e);
        }
    }

    /**
     * Record SAR filing event
     *
     * Tracks Suspicious Activity Report (SAR) filing to FinCEN.
     * Required for regulatory compliance and audit trails.
     *
     * @param status Filing status (FILED, FAILED, PENDING)
     * @param filingMethod Filing method (ELECTRONIC, MANUAL)
     * @param processingTimeMs Time to process and file SAR
     */
    public void recordSARFiling(String status, String filingMethod, long processingTimeMs) {
        try {
            String metricKey = String.format("sar_filing.%s.%s", status, filingMethod);

            Counter counter = regulatoryFilingCounters.computeIfAbsent(metricKey, key ->
                    Counter.builder("compliance.sar.filings.total")
                            .description("Total number of SAR filings to FinCEN")
                            .tag("status", status)
                            .tag("filing_method", filingMethod)
                            .tag("regulatory_requirement", "FINCEN_SAR")
                            .register(meterRegistry)
            );

            counter.increment();

            // Record processing time
            Timer timer = complianceResponseTimers.computeIfAbsent("sar_processing", key ->
                    Timer.builder("compliance.sar.processing.duration")
                            .description("SAR processing and filing duration")
                            .tag("regulatory_requirement", "FINCEN_SAR")
                            .register(meterRegistry)
            );

            timer.record(processingTimeMs, TimeUnit.MILLISECONDS);

            log.info("COMPLIANCE_METRIC: SAR filing recorded - Status: {}, Method: {}, ProcessingTime: {}ms",
                    status, filingMethod, processingTimeMs);

            // Track failures separately
            if ("FAILED".equals(status)) {
                activeIncidentGauges.computeIfAbsent("sar_filing_failures", k -> new AtomicInteger(0))
                        .incrementAndGet();
            }

        } catch (Exception e) {
            log.error("Failed to record SAR filing metric - Status: {}, Method: {}",
                    status, filingMethod, e);
        }
    }

    /**
     * Record Form 8300 filing event
     *
     * Tracks IRS Form 8300 filing for transactions exceeding $10,000.
     * Required for IRS compliance.
     *
     * @param status Filing status
     * @param transactionAmount Transaction amount that triggered filing
     * @param processingTimeMs Processing time
     */
    public void recordForm8300Filing(String status, java.math.BigDecimal transactionAmount, long processingTimeMs) {
        try {
            String metricKey = String.format("form8300_filing.%s", status);

            Counter counter = regulatoryFilingCounters.computeIfAbsent(metricKey, key ->
                    Counter.builder("compliance.form8300.filings.total")
                            .description("Total number of IRS Form 8300 filings")
                            .tag("status", status)
                            .tag("regulatory_requirement", "IRS_FORM_8300")
                            .register(meterRegistry)
            );

            counter.increment();

            // Record processing time
            Timer timer = complianceResponseTimers.computeIfAbsent("form8300_processing", key ->
                    Timer.builder("compliance.form8300.processing.duration")
                            .description("Form 8300 processing and filing duration")
                            .tag("regulatory_requirement", "IRS_FORM_8300")
                            .register(meterRegistry)
            );

            timer.record(processingTimeMs, TimeUnit.MILLISECONDS);

            log.info("COMPLIANCE_METRIC: Form 8300 filing recorded - Status: {}, Amount: {}, ProcessingTime: {}ms",
                    status, transactionAmount, processingTimeMs);

        } catch (Exception e) {
            log.error("Failed to record Form 8300 filing metric - Status: {}", status, e);
        }
    }

    /**
     * Record OFAC/sanctions screening result
     *
     * @param result Screening result (MATCH, NO_MATCH, ERROR)
     * @param listType List screened against (OFAC_SDN, EU_SANCTIONS, UN_SANCTIONS)
     * @param screeningTimeMs Time taken to perform screening
     */
    public void recordSanctionsScreening(String result, String listType, long screeningTimeMs) {
        try {
            String metricKey = String.format("sanctions_screening.%s.%s", result, listType);

            Counter counter = regulatoryFilingCounters.computeIfAbsent(metricKey, key ->
                    Counter.builder("compliance.sanctions.screenings.total")
                            .description("Total number of sanctions screenings performed")
                            .tag("result", result)
                            .tag("list_type", listType)
                            .register(meterRegistry)
            );

            counter.increment();

            // Record screening time
            Timer timer = complianceResponseTimers.computeIfAbsent("sanctions_screening", key ->
                    Timer.builder("compliance.sanctions.screening.duration")
                            .description("Sanctions screening duration")
                            .register(meterRegistry)
            );

            timer.record(screeningTimeMs, TimeUnit.MILLISECONDS);

            if ("MATCH".equals(result)) {
                log.warn("COMPLIANCE_METRIC: Sanctions match detected - List: {}, ScreeningTime: {}ms",
                        listType, screeningTimeMs);

                activeIncidentGauges.computeIfAbsent("sanctions_matches", k -> new AtomicInteger(0))
                        .incrementAndGet();
            }

        } catch (Exception e) {
            log.error("Failed to record sanctions screening metric - Result: {}, List: {}",
                    result, listType, e);
        }
    }

    /**
     * Record compliance incident resolution
     *
     * @param incidentType Type of incident
     * @param resolutionTimeMs Time to resolve incident
     * @param wasEscalated Whether incident was escalated
     */
    public void recordIncidentResolution(String incidentType, long resolutionTimeMs, boolean wasEscalated) {
        try {
            String metricKey = String.format("incident_resolution.%s.%s", incidentType, wasEscalated);

            Counter counter = freezeFailureCounters.computeIfAbsent(metricKey, key ->
                    Counter.builder("compliance.incidents.resolved.total")
                            .description("Total number of resolved compliance incidents")
                            .tag("incident_type", incidentType)
                            .tag("escalated", String.valueOf(wasEscalated))
                            .register(meterRegistry)
            );

            counter.increment();

            // Record resolution time
            Timer timer = complianceResponseTimers.computeIfAbsent("incident_resolution", key ->
                    Timer.builder("compliance.incident.resolution.duration")
                            .description("Compliance incident resolution time")
                            .register(meterRegistry)
            );

            timer.record(resolutionTimeMs, TimeUnit.MILLISECONDS);

            log.info("COMPLIANCE_METRIC: Incident resolved - Type: {}, ResolutionTime: {}ms, Escalated: {}",
                    incidentType, resolutionTimeMs, wasEscalated);

            // Decrement active incidents
            activeIncidentGauges.computeIfAbsent(incidentType, k -> new AtomicInteger(0))
                    .decrementAndGet();

        } catch (Exception e) {
            log.error("Failed to record incident resolution metric - Type: {}", incidentType, e);
        }
    }

    /**
     * Get current freeze failure rate
     *
     * @param freezeReason Freeze reason to check
     * @return Current failure count
     */
    public double getFreezeFailureCount(String freezeReason) {
        try {
            return freezeFailureCounters.values().stream()
                    .filter(counter -> {
                        String reasonTag = counter.getId().getTag("freeze_reason");
                        return freezeReason.equals(reasonTag);
                    })
                    .mapToDouble(Counter::count)
                    .sum();
        } catch (Exception e) {
            log.error("Failed to get freeze failure count - Reason: {}", freezeReason, e);
            return 0.0;
        }
    }

    /**
     * Get current active incident count
     *
     * @param incidentType Incident type
     * @return Current active count
     */
    public int getActiveIncidentCount(String incidentType) {
        AtomicInteger gauge = activeIncidentGauges.get(incidentType);
        return gauge != null ? gauge.get() : 0;
    }
}
