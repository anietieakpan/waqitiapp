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
 * DLQ Handler for P2PLendingEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class P2PLendingEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public P2PLendingEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("P2PLendingEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.P2PLendingEventsConsumer.dlq:P2PLendingEventsConsumer.dlq}",
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
            log.warn("DLQ: P2P lending recovery");
            String lendingId = headers.getOrDefault("lendingId", "").toString();
            String borrowerId = headers.getOrDefault("borrowerId", "").toString();
            String lenderId = headers.getOrDefault("lenderId", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("matching") || failureReason.contains("algorithm")) {
                log.error("DLQ: P2P matching failed: lendingId={}", lendingId);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("funding") || failureReason.contains("escrow")) {
                log.error("DLQ: P2P funding failed: lendingId={}", lendingId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("duplicate")) return DlqProcessingResult.DISCARDED;
            if (failureReason.contains("risk") || failureReason.contains("credit")) {
                log.warn("DLQ: P2P risk assessment failed: borrowerId={}", borrowerId);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("timeout")) return DlqProcessingResult.RETRY;
            log.warn("DLQ: P2P lending failed: lendingId={}", lendingId);
            return DlqProcessingResult.RETRY;
        } catch (Exception e) {
            log.error("DLQ: Error in P2P lending handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "P2PLendingEventsConsumer";
    }
}
