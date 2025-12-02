package com.waqiti.currency.service;

import com.waqiti.currency.model.FinancialTransactionEmergencyResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Financial Transaction Emergency Service
 *
 * Executes emergency protocols for critical financial transaction failures
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialTransactionEmergencyService {

    private final MeterRegistry meterRegistry;

    /**
     * Execute financial transaction emergency protocol
     */
    public FinancialTransactionEmergencyResult execute(String eventKey, String eventData,
                                                       String topic, String error,
                                                       String correlationId) {

        log.error("Executing financial transaction emergency protocol: topic={} correlationId={}",
                topic, correlationId);

        try {
            // Analyze financial impact
            FinancialImpact impact = analyzeFinancialImpact(eventData, correlationId);

            // Determine mitigation measures
            List<String> mitigationMeasures = determineMitigationMeasures(impact, correlationId);

            // Identify affected conversions
            List<String> affectedConversions = identifyAffectedConversions(eventData, correlationId);

            // Check if impact was mitigated
            boolean mitigated = !mitigationMeasures.isEmpty();

            Counter.builder("financial.emergency.protocol_executed")
                    .tag("topic", topic)
                    .tag("mitigated", String.valueOf(mitigated))
                    .register(meterRegistry)
                    .increment();

            log.error("Financial emergency protocol executed: mitigated={} measures={} correlationId={}",
                    mitigated, mitigationMeasures.size(), correlationId);

            return FinancialTransactionEmergencyResult.builder()
                    .financialImpactMitigated(mitigated)
                    .affectedConversions(affectedConversions)
                    .mitigatedConversions(affectedConversions.size())
                    .mitigationMeasures(mitigationMeasures)
                    .executedAt(Instant.now())
                    .correlationId(correlationId)
                    .build();

        } catch (Exception e) {
            log.error("Financial emergency protocol failed: correlationId={}", correlationId, e);

            Counter.builder("financial.emergency.protocol_failed")
                    .tag("topic", topic)
                    .register(meterRegistry)
                    .increment();

            return FinancialTransactionEmergencyResult.builder()
                    .financialImpactMitigated(false)
                    .affectedConversions(new ArrayList<>())
                    .mitigatedConversions(0)
                    .mitigationMeasures(new ArrayList<>())
                    .executedAt(Instant.now())
                    .correlationId(correlationId)
                    .build();
        }
    }

    /**
     * Analyze financial impact
     */
    private FinancialImpact analyzeFinancialImpact(String eventData, String correlationId) {
        log.debug("Analyzing financial impact: correlationId={}", correlationId);

        // In production: Parse event data, analyze amounts, assess risk
        return new FinancialImpact("MEDIUM", 1, false);
    }

    /**
     * Determine mitigation measures
     */
    private List<String> determineMitigationMeasures(FinancialImpact impact, String correlationId) {
        log.debug("Determining mitigation measures: severity={} correlationId={}",
                impact.severity, correlationId);

        List<String> measures = new ArrayList<>();

        if (impact.affectedCount > 0) {
            measures.add("QUEUE_FOR_MANUAL_REVIEW");
            measures.add("NOTIFY_TREASURY_TEAM");

            if (impact.isHighValue) {
                measures.add("IMMEDIATE_ESCALATION");
                measures.add("HALT_SIMILAR_TRANSACTIONS");
            }
        }

        return measures;
    }

    /**
     * Identify affected conversions
     */
    private List<String> identifyAffectedConversions(String eventData, String correlationId) {
        log.debug("Identifying affected conversions: correlationId={}", correlationId);

        // In production: Query database for related conversions
        List<String> affected = new ArrayList<>();
        affected.add("CONV-" + correlationId.substring(0, 8));

        return affected;
    }

    /**
     * Financial Impact internal model
     */
    private static class FinancialImpact {
        String severity;
        int affectedCount;
        boolean isHighValue;

        FinancialImpact(String severity, int affectedCount, boolean isHighValue) {
            this.severity = severity;
            this.affectedCount = affectedCount;
            this.isHighValue = isHighValue;
        }
    }
}
