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
 * DLQ Handler for IPBlacklistEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class IPBlacklistEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public IPBlacklistEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("IPBlacklistEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.IPBlacklistEventsConsumer.dlq:IPBlacklistEventsConsumer.dlq}",
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
            // FIXED: Implement IP blacklist recovery logic
            log.warn("Processing DLQ IP blacklist event");

            String blacklistId = headers.getOrDefault("blacklistId", "").toString();
            String ipAddress = headers.getOrDefault("ipAddress", "").toString();
            String reason = headers.getOrDefault("reason", "").toString();
            String severity = headers.getOrDefault("severity", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. IP: {}", ipAddress);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyBlacklisted")) {
                log.info("DLQ: IP already blacklisted. IP: {}. Marking as resolved.", ipAddress);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("FirewallUpdateFailed") && "HIGH".equals(severity)) {
                log.error("DLQ: Failed to update firewall for high-severity IP. IP: {}. Retrying.", ipAddress);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in IP blacklist. IP: {}. Manual review required.", ipAddress);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in IP blacklist. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ IP blacklist event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "IPBlacklistEventsConsumer";
    }
}
