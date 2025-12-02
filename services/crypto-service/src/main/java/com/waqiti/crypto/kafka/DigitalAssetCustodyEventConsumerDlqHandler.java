package com.waqiti.crypto.kafka;

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
 * DLQ Handler for DigitalAssetCustodyEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class DigitalAssetCustodyEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public DigitalAssetCustodyEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("DigitalAssetCustodyEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.DigitalAssetCustodyEventConsumer.dlq:DigitalAssetCustodyEventConsumer.dlq}",
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
            log.error("DLQ: Digital asset custody recovery (CRITICAL)");
            String custodyId = headers.getOrDefault("custodyId", "").toString();
            String assetId = headers.getOrDefault("assetId", "").toString();
            String eventType = headers.getOrDefault("eventType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("cold storage") || failureReason.contains("vault")) {
                log.error("DLQ: Cold storage operation failed: custodyId={}", custodyId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("multi-sig") || failureReason.contains("signature")) {
                log.error("DLQ: Multi-sig approval failed: custodyId={}", custodyId);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("duplicate")) return DlqProcessingResult.DISCARDED;
            if (failureReason.contains("timeout")) return DlqProcessingResult.RETRY;
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("Error handling DLQ event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "DigitalAssetCustodyEventConsumer";
    }
}
