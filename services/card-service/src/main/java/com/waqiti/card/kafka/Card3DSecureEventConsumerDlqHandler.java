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
 * DLQ Handler for Card3DSecureEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class Card3DSecureEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public Card3DSecureEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("Card3DSecureEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.Card3DSecureEventConsumer.dlq:Card3DSecureEventConsumer.dlq}",
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
            log.warn("DLQ: Card 3DS recovery");
            String authId = headers.getOrDefault("authId", "").toString();
            String cardId = headers.getOrDefault("cardId", "").toString();
            String authStatus = headers.getOrDefault("authStatus", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("authentication") && authStatus.contains("SUCCESS")) {
                log.error("DLQ: 3DS success but callback failed: authId={}", authId);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("issuer") || failureReason.contains("ACS")) {
                log.warn("DLQ: 3DS issuer unavailable: authId={}", authId);
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
        return "Card3DSecureEventConsumer";
    }
}
