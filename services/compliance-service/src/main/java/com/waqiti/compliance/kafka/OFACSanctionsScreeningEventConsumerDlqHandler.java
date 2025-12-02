package com.waqiti.compliance.kafka;

import com.waqiti.common.kafka.BaseDlqConsumer;
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
 * DLQ Handler for OFACSanctionsScreeningEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class OFACSanctionsScreeningEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public OFACSanctionsScreeningEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("OFACSanctionsScreeningEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.OFACSanctionsScreeningEventConsumer.dlq:OFACSanctionsScreeningEventConsumer.dlq}",
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
            log.error("DLQ: CRITICAL - OFAC sanctions screening (LEGAL/REGULATORY)");
            String screeningId = headers.getOrDefault("screeningId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String entityName = headers.getOrDefault("entityName", "").toString();
            String matchStatus = headers.getOrDefault("matchStatus", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // OFAC screening MUST complete - federal law requirement

            // Strategy 1: Sanctions match detected (HIGHEST PRIORITY)
            if (matchStatus.contains("MATCH") || matchStatus.contains("HIT")) {
                log.error("DLQ: CRITICAL - OFAC MATCH IN DLQ: userId={}, entity={}", userId, entityName);
                // Immediate freeze + escalation
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Screening service unavailable
            if (failureReason.contains("OFAC service") || failureReason.contains("sanctions API")) {
                log.error("DLQ: OFAC screening service unavailable, retrying: userId={}", userId);
                // MUST retry - cannot process without screening
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Duplicate screening
            if (failureReason.contains("duplicate") || failureReason.contains("already screened")) {
                log.info("DLQ: Duplicate OFAC screening: userId={}", userId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 4: Account freeze failure after match
            if (failureReason.contains("freeze failed") && matchStatus.contains("MATCH")) {
                log.error("DLQ: CRITICAL - Failed to freeze account with OFAC match: userId={}", userId);
                // HIGHEST PRIORITY
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 5: Incomplete screening data
            if (failureReason.contains("incomplete data") || failureReason.contains("missing info")) {
                log.warn("DLQ: Incomplete data for OFAC screening: userId={}", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 6: Regulatory reporting failure
            if (failureReason.contains("reporting") || failureReason.contains("filing")) {
                log.error("DLQ: OFAC match reporting failed: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 7: Transient
            if (failureReason.contains("timeout") || failureReason.contains("network")) {
                log.warn("DLQ: Transient error in OFAC screening, retrying");
                return DlqProcessingResult.RETRY;
            }

            // Default: CRITICAL - cannot miss OFAC screenings
            log.error("DLQ: OFAC screening failure - COMPLIANCE ESCALATION: screeningId={}", screeningId);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: CRITICAL - Error in OFAC screening handler", e);
            // Never discard OFAC screenings
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    @Override
    protected String getServiceName() {
        return "OFACSanctionsScreeningEventConsumer";
    }
}
