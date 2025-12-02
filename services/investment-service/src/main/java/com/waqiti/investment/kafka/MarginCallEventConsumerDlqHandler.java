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
 * DLQ Handler for MarginCallEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class MarginCallEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public MarginCallEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("MarginCallEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.MarginCallEventConsumer.dlq:MarginCallEventConsumer.dlq}",
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
            log.error("DLQ: Margin call recovery (CRITICAL)");
            String marginCallId = headers.getOrDefault("marginCallId", "").toString();
            String accountId = headers.getOrDefault("accountId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String deficitAmount = headers.getOrDefault("deficitAmount", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Margin call notification failure (CRITICAL - customer must know)
            if (failureReason.contains("notification") || failureReason.contains("alert")) {
                log.error("DLQ: Margin call notification failed: accountId={}, deficit={}", accountId, deficitAmount);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Forced liquidation trigger failed
            if (failureReason.contains("liquidation") || failureReason.contains("force sell")) {
                log.error("DLQ: Forced liquidation trigger failed: accountId={}", accountId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 3: Margin level calculation failure
            if (failureReason.contains("calculation") || failureReason.contains("margin level")) {
                log.error("DLQ: Margin calculation failed: accountId={}", accountId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: Duplicate margin call
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 5: Position freeze failure
            if (failureReason.contains("freeze") || failureReason.contains("position lock")) {
                log.error("DLQ: Position freeze failed during margin call: accountId={}", accountId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 6: Transient
            if (failureReason.contains("timeout")) {
                return DlqProcessingResult.RETRY;
            }

            log.error("DLQ: Margin call failed: marginCallId={}, accountId={}", marginCallId, accountId);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: Error in margin call handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "MarginCallEventConsumer";
    }
}
