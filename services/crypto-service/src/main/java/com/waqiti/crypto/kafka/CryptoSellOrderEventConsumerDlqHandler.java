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
 * DLQ Handler for CryptoSellOrderEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CryptoSellOrderEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public CryptoSellOrderEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CryptoSellOrderEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CryptoSellOrderEventConsumer.dlq:CryptoSellOrderEventConsumer.dlq}",
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
            log.warn("DLQ: Crypto sell order recovery");
            String orderId = headers.getOrDefault("orderId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String orderStatus = headers.getOrDefault("orderStatus", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("settlement") || failureReason.contains("payout")) {
                log.error("DLQ: Crypto sell settlement failed: orderId={}", orderId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("liquidity") || failureReason.contains("market")) {
                log.warn("DLQ: Market liquidity issue for sell: orderId={}", orderId);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }
            if (failureReason.contains("wallet") || failureReason.contains("insufficient crypto")) {
                log.error("DLQ: Insufficient crypto for sell: orderId={}", orderId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("timeout")) {
                return DlqProcessingResult.RETRY;
            }
            log.warn("DLQ: Crypto sell order failed: orderId={}, status={}", orderId, orderStatus);
            return DlqProcessingResult.RETRY;
        } catch (Exception e) {
            log.error("DLQ: Error in crypto sell order handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "CryptoSellOrderEventConsumer";
    }
}
