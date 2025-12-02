package com.waqiti.frauddetection.kafka;

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
 * DLQ Handler for FraudAlertCriticalConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class FraudAlertCriticalConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public FraudAlertCriticalConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("FraudAlertCriticalConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.FraudAlertCriticalConsumer.dlq:FraudAlertCriticalConsumer.dlq}",
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
            // FIXED: Implement critical fraud alert recovery logic - HIGHEST PRIORITY
            log.error("Processing DLQ CRITICAL fraud alert - URGENT");

            String alertId = headers.getOrDefault("alertId", "").toString();
            String entityId = headers.getOrDefault("entityId", "").toString();
            String alertReason = headers.getOrDefault("alertReason", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.error("DLQ: Database error for CRITICAL fraud alert. Alert: {}. Retrying immediately.", alertId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("NotificationFailed")) {
                log.error("DLQ: CRITICAL - Failed to send critical fraud alert. Alert: {}. Retrying immediately.", alertId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadySent")) {
                log.info("DLQ: Critical alert already sent. Alert: {}. Marking as resolved.", alertId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("EscalationFailed")) {
                log.error("DLQ: CRITICAL - Failed to escalate fraud alert. Alert: {}. Manual intervention required.", alertId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in CRITICAL fraud alert. Alert: {}. URGENT manual review.", alertId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in CRITICAL fraud alert. Event: {}, Headers: {}. URGENT.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ CRITICAL fraud alert - URGENT", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "FraudAlertCriticalConsumer";
    }
}
