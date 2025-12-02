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
 * DLQ Handler for CryptoBuyOrderEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CryptoBuyOrderEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public CryptoBuyOrderEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CryptoBuyOrderEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CryptoBuyOrderEventConsumer.dlq:CryptoBuyOrderEventConsumer.dlq}",
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
            log.warn("DLQ: Crypto buy order recovery");
            String orderId = headers.getOrDefault("orderId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String orderStatus = headers.getOrDefault("orderStatus", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("payment") || failureReason.contains("insufficient")) {
                log.error("DLQ: Crypto buy payment failed: orderId={}", orderId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("liquidity") || failureReason.contains("market")) {
                log.warn("DLQ: Market liquidity issue: orderId={}", orderId);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }
            if (failureReason.contains("limit") || failureReason.contains("KYC")) {
                log.error("DLQ: Buy order compliance issue: orderId={}", orderId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("timeout")) {
                return DlqProcessingResult.RETRY;
            }
            log.warn("DLQ: Crypto buy order failed: orderId={}, status={}", orderId, orderStatus);
            return DlqProcessingResult.RETRY;
        } catch (Exception e) {
            log.error("DLQ: Error in crypto buy order handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "CryptoBuyOrderEventConsumer";
    }
}
