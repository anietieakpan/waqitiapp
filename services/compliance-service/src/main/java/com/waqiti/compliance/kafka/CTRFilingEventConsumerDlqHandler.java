package com.waqiti.compliance.kafka;

import com.waqiti.common.kafka.BaseDlqConsumer;
import com.waqiti.compliance.dlq.DLQPriority;
import com.waqiti.compliance.dlq.DLQRecoveryService;
import com.waqiti.compliance.entity.DLQRecord;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;

/**
 * Production-Ready DLQ Handler for CTR Filing Events.
 *
 * Handles failed CTR (Currency Transaction Report) filing messages with
 * comprehensive recovery mechanisms.
 *
 * CRITICAL COMPLIANCE REQUIREMENT:
 * CTR filing failures MUST be recovered. Federal law requires CTRs for cash
 * transactions over $10,000 to be filed within 15 days. Any failure in the CTR
 * filing pipeline must be immediately escalated and manually reviewed.
 *
 * Recovery Strategy:
 * 1. Log failure to persistent DLQ record store
 * 2. Send CRITICAL alert to compliance team (PagerDuty + Email + Slack)
 * 3. Add to manual review queue for immediate investigation
 * 4. Create audit trail for regulatory compliance
 * 5. Require manual compliance officer intervention
 *
 * Regulatory Context:
 * - 31 CFR 1020.310 - CTR filing requirements
 * - 15-day filing deadline for transactions over $10,000
 * - FinCEN BSA E-Filing requirements
 *
 * @author Waqiti Compliance Engineering
 * @version 2.0 (Production)
 */
@Service
@Slf4j
public class CTRFilingEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final DLQRecoveryService dlqRecoveryService;

    public CTRFilingEventConsumerDlqHandler(
            MeterRegistry meterRegistry,
            DLQRecoveryService dlqRecoveryService) {
        super(meterRegistry);
        this.dlqRecoveryService = dlqRecoveryService;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CTRFilingEventConsumer");
        log.info("Initialized CRITICAL DLQ handler for CTR Filing with comprehensive recovery");
    }

    @KafkaListener(
        topics = "${kafka.topics.CTRFilingEventConsumer.dlq:CTRFilingEventConsumer.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq"
    )
    public void handleDlqMessage(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        processDlqMessage(event, topic, acknowledgment);
    }

    @Override
    protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
        try {
            log.error("CRITICAL: CTR Filing message failed and sent to DLQ. " +
                     "This is a REGULATORY COMPLIANCE FAILURE that requires immediate attention.");

            // Extract exception information from headers if available
            Exception exception = extractException(headers);
            if (exception == null) {
                exception = new RuntimeException("CTR filing failed - message sent to DLQ");
            }

            // Process through comprehensive DLQ recovery service
            // CRITICAL priority ensures immediate PagerDuty alert and manual review
            DLQRecord dlqRecord = dlqRecoveryService.processDLQMessage(
                "ctr-filing-events",
                event,
                headers,
                exception,
                DLQPriority.CRITICAL  // CRITICAL: CTR filing is regulatory requirement
            );

            log.info("CTR Filing DLQ record created: {} - Manual review required", dlqRecord.getId());

            // CRITICAL: Manual intervention is REQUIRED for CTR filing failures
            // Cannot auto-retry without compliance officer review
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("CRITICAL ERROR: Failed to process CTR Filing DLQ message. " +
                     "This requires IMMEDIATE escalation to compliance management.", e);

            // Even if DLQ processing fails, we must log this critical failure
            try {
                // Last-resort logging for catastrophic DLQ handler failure
                log.error("CATASTROPHIC FAILURE: CTR Filing DLQ handler failed. " +
                         "Event: {}, Headers: {}, Error: {}",
                         event, headers, e.getMessage());
            } catch (Exception loggingError) {
                // Cannot recover from this - system is in critical state
                System.err.println("SYSTEM CRITICAL: Unable to log CTR Filing DLQ failure");
            }

            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "CTRFilingEventConsumer";
    }

    /**
     * Extract exception from Kafka message headers if available.
     */
    private Exception extractException(Map<String, Object> headers) {
        try {
            if (headers.containsKey("kafka_exception_message")) {
                String message = (String) headers.get("kafka_exception_message");
                String stackTrace = headers.containsKey("kafka_exception_stacktrace")
                    ? (String) headers.get("kafka_exception_stacktrace")
                    : "";
                return new RuntimeException(message + "\n" + stackTrace);
            }

            if (headers.containsKey("springDeserializerExceptionValue")) {
                return new RuntimeException("Deserialization failure: " +
                    headers.get("springDeserializerExceptionValue"));
            }

            return null;
        } catch (Exception e) {
            log.warn("Could not extract exception from headers", e);
            return null;
        }
    }
}
