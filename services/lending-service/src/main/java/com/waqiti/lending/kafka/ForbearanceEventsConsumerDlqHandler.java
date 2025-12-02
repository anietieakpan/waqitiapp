package com.waqiti.lending.kafka;

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
 * DLQ Handler for ForbearanceEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class ForbearanceEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public ForbearanceEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("ForbearanceEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.ForbearanceEventsConsumer.dlq:ForbearanceEventsConsumer.dlq}",
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
            log.warn("DLQ: Forbearance recovery");
            String loanId = headers.getOrDefault("loanId", "").toString();
            String forbearanceType = headers.getOrDefault("forbearanceType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("approval") || failureReason.contains("grant")) {
                log.error("DLQ: Forbearance approval failed: loanId={}", loanId);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("payment") || failureReason.contains("schedule")) {
                log.warn("DLQ: Payment schedule update failed: loanId={}", loanId);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("duplicate")) return DlqProcessingResult.DISCARDED;
            if (failureReason.contains("timeout")) return DlqProcessingResult.RETRY;
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("Error handling DLQ event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "ForbearanceEventsConsumer";
    }
}
