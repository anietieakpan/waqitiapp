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
 * DLQ Handler for RegEComplianceEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class RegEComplianceEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public RegEComplianceEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("RegEComplianceEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.RegEComplianceEventsConsumer.dlq:RegEComplianceEventsConsumer.dlq}",
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
            log.error("DLQ: CRITICAL - Reg E compliance event (regulatory requirement)");
            String eventId = headers.getOrDefault("eventId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String eventType = headers.getOrDefault("eventType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Reg E = Electronic Fund Transfer Act compliance - CRITICAL

            // Strategy 1: Error resolution reporting
            if (eventType.contains("ERROR_RESOLUTION") || eventType.contains("DISPUTE")) {
                log.error("DLQ: Reg E error resolution tracking failed: eventId={}", eventId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Disclosure failure (legal requirement)
            if (failureReason.contains("disclosure") || failureReason.contains("terms")) {
                log.error("DLQ: Reg E disclosure delivery failed: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Consumer rights notification
            if (eventType.contains("CONSUMER_RIGHTS") || eventType.contains("NOTIFICATION")) {
                log.error("DLQ: Consumer rights notification failed: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: Dispute handling
            if (eventType.contains("DISPUTE") || eventType.contains("CLAIM")) {
                log.error("DLQ: Dispute handling failed - LEGAL DEADLINE RISK: eventId={}", eventId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 5: Compliance deadline tracking
            if (failureReason.contains("deadline") || failureReason.contains("overdue")) {
                log.error("DLQ: CRITICAL - Compliance deadline missed: eventId={}", eventId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 6: Audit trail
            if (failureReason.contains("audit") || failureReason.contains("log")) {
                log.error("DLQ: Compliance audit trail failure: eventId={}", eventId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 7: Regulatory reporting
            if (failureReason.contains("report") || failureReason.contains("filing")) {
                log.error("DLQ: Regulatory report filing failed: eventId={}", eventId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 8: Transient
            if (failureReason.contains("timeout") || failureReason.contains("database")) {
                return DlqProcessingResult.RETRY;
            }

            // Default: CRITICAL - all Reg E events require completion
            log.error("DLQ: Reg E compliance failure - ESCALATE TO LEGAL: eventId={}", eventId);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: CRITICAL - Error handling Reg E compliance event", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    @Override
    protected String getServiceName() {
        return "RegEComplianceEventsConsumer";
    }
}
