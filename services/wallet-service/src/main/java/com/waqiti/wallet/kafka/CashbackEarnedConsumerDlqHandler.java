package com.waqiti.wallet.kafka;

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
 * DLQ Handler for CashbackEarnedConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CashbackEarnedConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public CashbackEarnedConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CashbackEarnedConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CashbackEarnedConsumer.dlq:CashbackEarnedConsumer.dlq}",
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
            log.info("DLQ: Cashback earned recovery");
            String cashbackId = headers.getOrDefault("cashbackId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String amount = headers.getOrDefault("amount", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("credit") || failureReason.contains("wallet")) {
                log.error("DLQ: Cashback credit failed: userId={}, amount={}", userId, amount);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("calculation") || failureReason.contains("percentage")) {
                log.warn("DLQ: Cashback calc failed: cashbackId={}", cashbackId);
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
        return "CashbackEarnedConsumer";
    }
}
