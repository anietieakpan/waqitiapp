package com.waqiti.card.kafka;

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
 * DLQ Handler for CardActivationEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CardActivationEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public CardActivationEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CardActivationEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CardActivationEventConsumer.dlq:CardActivationEventConsumer.dlq}",
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
            log.warn("DLQ: Card activation recovery");
            String cardId = headers.getOrDefault("cardId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("activation") || failureReason.contains("status update")) {
                log.error("DLQ: Card activation failed: cardId={}", cardId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("PIN") || failureReason.contains("security")) {
                log.warn("DLQ: PIN setup failed: cardId={}", cardId);
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
        return "CardActivationEventConsumer";
    }
}
