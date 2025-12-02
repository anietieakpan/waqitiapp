package com.waqiti.audit.kafka;

import com.waqiti.common.kafka.RetryableKafkaListener;
import com.waqiti.audit.dto.AuditTransactionAggregation;
import com.waqiti.audit.service.AuditAggregationService;
import com.waqiti.audit.service.ComplianceReportingService;
import com.waqiti.common.exception.KafkaRetryException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Audit Transaction Aggregations Consumer
 *
 * PURPOSE: Process aggregated audit data for compliance reporting
 *
 * BUSINESS CRITICAL: Required for regulatory compliance (SOX, GDPR, PCI-DSS)
 * Missing this consumer means:
 * - Failed compliance audits ($100K-4M fines)
 * - No audit trail aggregations
 * - Missing regulatory reports
 * - Potential license suspension
 *
 * COMPLIANCE: SOX Section 404, GDPR Article 30, PCI-DSS Requirement 10.7
 *
 * IMPLEMENTATION PRIORITY: P0 CRITICAL
 *
 * @author Waqiti Compliance Team
 * @version 1.0.0
 * @since 2025-10-13
 */
@Service
@Slf4j
public class AuditTransactionAggregationsConsumer {

    private final AuditAggregationService aggregationService;
    private final ComplianceReportingService reportingService;
    private final Counter aggregationsProcessedCounter;
    private final Counter aggregationsFailedCounter;

    @Autowired
    public AuditTransactionAggregationsConsumer(
            AuditAggregationService aggregationService,
            ComplianceReportingService reportingService,
            MeterRegistry meterRegistry) {

        this.aggregationService = aggregationService;
        this.reportingService = reportingService;

        this.aggregationsProcessedCounter = Counter.builder("audit.aggregations.processed")
                .description("Number of audit aggregations processed")
                .register(meterRegistry);

        this.aggregationsFailedCounter = Counter.builder("audit.aggregations.failed")
                .description("Number of audit aggregations that failed")
                .register(meterRegistry);
    }

    /**
     * Process audit transaction aggregation
     */
    @RetryableKafkaListener(
        topics = "audit-transaction-aggregations",
        groupId = "audit-service-aggregations",
        containerFactory = "kafkaListenerContainerFactory",
        retries = 5,
        backoffMultiplier = 2.0,
        initialBackoff = 1000L
    )
    @Transactional
    public void handleAuditAggregation(
            @Payload AuditTransactionAggregation aggregation,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        Instant startTime = Instant.now();

        log.info("Processing audit transaction aggregation: aggregationId={}, period={}, entityType={}",
                aggregation.getAggregationId(),
                aggregation.getTimePeriod(),
                aggregation.getEntityType());

        try {
            // Step 1: Validate aggregation
            validateAggregation(aggregation);

            // Step 2: Check idempotency
            if (aggregationService.isAggregationAlreadyProcessed(aggregation.getAggregationId())) {
                log.info("Audit aggregation already processed (idempotent): aggregationId={}",
                        aggregation.getAggregationId());
                acknowledgment.acknowledge();
                return;
            }

            // Step 3: Store aggregation in time-series database
            aggregationService.storeAggregation(aggregation);

            // Step 4: Update compliance metrics
            reportingService.updateComplianceMetrics(
                    aggregation.getEntityType(),
                    aggregation.getTimePeriod(),
                    aggregation.getTotalTransactions(),
                    aggregation.getTotalAmount()
            );

            // Step 5: Check for anomalies in aggregated data
            if (aggregationService.detectAnomalies(aggregation)) {
                log.warn("Anomaly detected in audit aggregation: aggregationId={}, entityType={}",
                        aggregation.getAggregationId(), aggregation.getEntityType());

                reportingService.triggerAnomalyInvestigation(aggregation);
            }

            // Step 6: Update regulatory reporting dashboards
            reportingService.updateRegulatoryDashboard(
                    aggregation.getEntityType(),
                    aggregation.getTimePeriod(),
                    aggregation
            );

            // Step 7: Generate alerts if thresholds exceeded
            if (aggregation.getTotalAmount().compareTo(
                    new java.math.BigDecimal("10000000.00")) > 0) {
                reportingService.alertComplianceTeam(
                    "High-value aggregation detected",
                    aggregation
                );
            }

            // Step 8: Archive aggregation for long-term retention (7 years SOX requirement)
            aggregationService.archiveAggregation(aggregation);

            // Step 9: Mark as processed
            aggregationService.markAggregationProcessed(aggregation.getAggregationId());

            // Step 10: Acknowledge
            acknowledgment.acknowledge();

            // Metrics
            aggregationsProcessedCounter.increment();

            long processingTime = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            log.info("Audit aggregation processed successfully: aggregationId={}, processingTime={}ms",
                    aggregation.getAggregationId(), processingTime);

        } catch (Exception e) {
            log.error("Failed to process audit aggregation: aggregationId={}, will retry",
                    aggregation.getAggregationId(), e);

            aggregationsFailedCounter.increment();

            throw new KafkaRetryException(
                    "Failed to process audit aggregation",
                    e,
                    aggregation.getAggregationId().toString()
            );
        }
    }

    /**
     * Validate aggregation
     */
    private void validateAggregation(AuditTransactionAggregation aggregation) {
        if (aggregation == null) {
            throw new IllegalArgumentException("Aggregation cannot be null");
        }

        if (aggregation.getAggregationId() == null) {
            throw new IllegalArgumentException("Aggregation ID cannot be null");
        }

        if (aggregation.getEntityType() == null) {
            throw new IllegalArgumentException("Entity type cannot be null");
        }

        if (aggregation.getTimePeriod() == null) {
            throw new IllegalArgumentException("Time period cannot be null");
        }

        if (aggregation.getTotalTransactions() < 0) {
            throw new IllegalArgumentException("Total transactions cannot be negative");
        }

        if (aggregation.getTotalAmount() == null ||
            aggregation.getTotalAmount().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total amount must be non-negative");
        }
    }

    /**
     * Handle DLQ messages
     */
    @KafkaListener(topics = "audit-transaction-aggregations-audit-service-dlq")
    public void handleDLQMessage(@Payload AuditTransactionAggregation aggregation) {
        log.error("CRITICAL: Audit aggregation in DLQ - COMPLIANCE RISK: aggregationId={}, entityType={}",
                aggregation.getAggregationId(), aggregation.getEntityType());

        try {
            // Log to persistent storage (CRITICAL for compliance)
            aggregationService.logDLQAggregation(
                    aggregation.getAggregationId(),
                    aggregation,
                    "Audit aggregation processing failed permanently - COMPLIANCE RISK"
            );

            // CRITICAL ALERT - compliance team must be notified immediately
            reportingService.alertComplianceTeam(
                    "CRITICAL",
                    "Audit aggregation stuck in DLQ - potential compliance violation",
                    java.util.Map.of(
                            "aggregationId", aggregation.getAggregationId().toString(),
                            "entityType", aggregation.getEntityType(),
                            "timePeriod", aggregation.getTimePeriod().toString(),
                            "totalTransactions", String.valueOf(aggregation.getTotalTransactions()),
                            "totalAmount", aggregation.getTotalAmount().toString(),
                            "complianceRisk", "HIGH - Missing audit trail aggregation"
                    )
            );

            // Create compliance incident ticket
            reportingService.createComplianceIncident(
                    "AUDIT_AGGREGATION_FAILURE",
                    "P0 CRITICAL",
                    aggregation,
                    "Audit aggregation failed after all retries. " +
                    "Manual aggregation required for compliance reporting."
            );

            // Notify external auditors if material amount
            if (aggregation.getTotalAmount().compareTo(
                    new java.math.BigDecimal("1000000.00")) > 0) {
                reportingService.notifyExternalAuditors(aggregation);
            }

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process audit DLQ message - ESCALATE TO MANAGEMENT: aggregationId={}",
                    aggregation.getAggregationId(), e);

            // Last resort - write to emergency log file
            aggregationService.writeToEmergencyLog(aggregation, e);
        }
    }
}
