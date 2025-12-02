package com.waqiti.investment.kafka;

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
 * DLQ Handler for MutualFundOrderEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class MutualFundOrderEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public MutualFundOrderEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("MutualFundOrderEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.MutualFundOrderEventsConsumer.dlq:MutualFundOrderEventsConsumer.dlq}",
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
            log.warn("DLQ: Mutual fund order recovery");
            String orderId = headers.getOrDefault("orderId", "").toString();
            String fundSymbol = headers.getOrDefault("fundSymbol", "").toString();
            String orderType = headers.getOrDefault("orderType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("NAV") || failureReason.contains("pricing")) {
                log.warn("DLQ: NAV pricing issue: fundSymbol={}", fundSymbol);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("settlement") || failureReason.contains("T+1")) {
                log.error("DLQ: Fund settlement failed: orderId={}", orderId);
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
        return "MutualFundOrderEventsConsumer";
    }
}
