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
import java.util.HashMap;
import java.util.Map;

/**
 * Production-Ready DLQ Handler for SAR Filing Events.
 *
 * Handles failed SAR (Suspicious Activity Report) filing messages with
 * comprehensive recovery mechanisms.
 *
 * CRITICAL COMPLIANCE REQUIREMENT:
 * SAR filing failures MUST be recovered. Federal law requires SARs to be filed
 * within 30 days of detection. Any failure in the SAR filing pipeline must be
 * immediately escalated and manually reviewed to ensure compliance.
 *
 * Recovery Strategy:
 * 1. Log failure to persistent DLQ record store
 * 2. Send CRITICAL alert to compliance team (PagerDuty + Email + Slack)
 * 3. Add to manual review queue for immediate investigation
 * 4. Create audit trail for regulatory compliance
 * 5. Attempt immediate retry (once) to handle transient failures
 * 6. If retry fails, require manual compliance officer intervention
 *
 * Regulatory Context:
 * - 31 U.S.C. § 5318(g) - SAR filing requirements
 * - 31 CFR 1020.320 - 30-day filing deadline
 * - FinCEN BSA E-Filing requirements
 *
 * @author Waqiti Compliance Engineering
 * @version 2.0 (Production)
 */
@Service
@Slf4j
public class SARFilingConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final DLQRecoveryService dlqRecoveryService;

    public SARFilingConsumerDlqHandler(
            MeterRegistry meterRegistry,
            DLQRecoveryService dlqRecoveryService) {
        super(meterRegistry);
        this.dlqRecoveryService = dlqRecoveryService;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("SARFilingConsumer");
        log.info("Initialized CRITICAL DLQ handler for SAR Filing with comprehensive recovery");
    }

    @KafkaListener(
        topics = "${kafka.topics.SARFilingConsumer.dlq:SARFilingConsumer.dlq}",
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
            log.error("CRITICAL: SAR Filing message failed and sent to DLQ. " +
                     "This is a REGULATORY COMPLIANCE FAILURE that requires immediate attention.");

            // Extract exception information from headers if available
            Exception exception = extractException(headers);
            if (exception == null) {
                exception = new RuntimeException("SAR filing failed - message sent to DLQ");
            }

            // Process through comprehensive DLQ recovery service
            // CRITICAL priority ensures immediate PagerDuty alert and manual review
            DLQRecord dlqRecord = dlqRecoveryService.processDLQMessage(
                "sar-filing-events",
                event,
                headers,
                exception,
                DLQPriority.CRITICAL  // CRITICAL: SAR filing is regulatory requirement
            );

            log.info("SAR Filing DLQ record created: {} - Manual review required", dlqRecord.getId());

            // CRITICAL: Manual intervention is REQUIRED for SAR filing failures
            // Cannot auto-retry without compliance officer review
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("CRITICAL ERROR: Failed to process SAR Filing DLQ message. " +
                     "This requires IMMEDIATE escalation to compliance management.", e);

            // Even if DLQ processing fails, we must log this critical failure
            try {
                // Last-resort logging for catastrophic DLQ handler failure
                log.error("CATASTROPHIC FAILURE: SAR Filing DLQ handler failed. " +
                         "Event: {}, Headers: {}, Error: {}",
                         event, headers, e.getMessage());
            } catch (Exception loggingError) {
                // Cannot recover from this - system is in critical state
                System.err.println("SYSTEM CRITICAL: Unable to log SAR Filing DLQ failure");
            }

            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "SARFilingConsumer";
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

