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
 * DLQ Handler for TradeExecutionEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class TradeExecutionEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public TradeExecutionEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("TradeExecutionEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.TradeExecutionEventsConsumer.dlq:TradeExecutionEventsConsumer.dlq}",
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
            log.error("DLQ: Trade execution recovery (FINANCIAL CRITICAL)");
            String tradeId = headers.getOrDefault("tradeId", "").toString();
            String accountId = headers.getOrDefault("accountId", "").toString();
            String executionStatus = headers.getOrDefault("executionStatus", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("settlement") || failureReason.contains("clearance")) {
                log.error("DLQ: Trade settlement failed: tradeId={}", tradeId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("market") || failureReason.contains("exchange")) {
                log.error("DLQ: Exchange/market issue: tradeId={}", tradeId);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }
            if (failureReason.contains("insufficient") || failureReason.contains("margin")) {
                log.error("DLQ: Insufficient funds for trade: tradeId={}", tradeId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("timeout")) {
                return DlqProcessingResult.RETRY;
            }
            log.error("DLQ: Trade execution failed: tradeId={}, status={}", tradeId, executionStatus);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        } catch (Exception e) {
            log.error("DLQ: Error in trade execution handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "TradeExecutionEventsConsumer";
    }
}
