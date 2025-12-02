package com.waqiti.currency.service;

import com.waqiti.currency.model.FinancialImpactAssessment;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Financial Impact Assessment Service
 *
 * Creates and manages financial impact assessments for failed conversions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialImpactAssessmentService {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, FinancialImpactAssessment> assessments = new ConcurrentHashMap<>();

    /**
     * Create financial impact assessment
     */
    public String create(FinancialImpactAssessment assessment) {
        log.info("Creating financial impact assessment: conversionId={} impactType={} priority={} correlationId={}",
                assessment.getConversionId(), assessment.getImpactType(), assessment.getPriority(),
                assessment.getCorrelationId());

        // Store assessment
        assessments.put(assessment.getConversionId(), assessment);

        // In production: Persist to database
        Counter.builder("financial.impact_assessment.created")
                .tag("impactType", assessment.getImpactType().name())
                .tag("priority", assessment.getPriority().name())
                .register(meterRegistry)
                .increment();

        log.info("Financial impact assessment created: conversionId={} correlationId={}",
                assessment.getConversionId(), assessment.getCorrelationId());

        return assessment.getConversionId();
    }

    /**
     * Update assessment status
     */
    public void updateStatus(String conversionId, String status, String correlationId) {
        FinancialImpactAssessment assessment = assessments.get(conversionId);

        if (assessment != null) {
            assessment.setStatus(status);
            log.info("Updated financial impact assessment status: conversionId={} status={} correlationId={}",
                    conversionId, status, correlationId);
        }
    }

    /**
     * Get assessment
     */
    public FinancialImpactAssessment getAssessment(String conversionId) {
        return assessments.get(conversionId);
    }

    /**
     * Get pending assessments count
     */
    public long getPendingCount() {
        return assessments.values().stream()
                .filter(a -> "PENDING_REVIEW".equals(a.getStatus()))
                .count();
    }
}
