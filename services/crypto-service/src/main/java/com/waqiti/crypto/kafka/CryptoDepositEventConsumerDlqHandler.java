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
 * DLQ Handler for CryptoDepositEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CryptoDepositEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public CryptoDepositEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CryptoDepositEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CryptoDepositEventConsumer.dlq:CryptoDepositEventConsumer.dlq}",
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
            log.error("DLQ: Crypto deposit recovery");
            String depositId = headers.getOrDefault("depositId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String amount = headers.getOrDefault("amount", "").toString();
            String confirmations = headers.getOrDefault("confirmations", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("credit") || failureReason.contains("account update")) {
                log.error("DLQ: Crypto deposit credit failed: depositId={}", depositId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("confirmations") || Integer.parseInt(confirmations) < 6) {
                log.warn("DLQ: Insufficient blockchain confirmations: depositId={}", depositId);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }
            if (failureReason.contains("fraud") || failureReason.contains("suspicious")) {
                log.error("DLQ: Crypto deposit fraud alert: depositId={}", depositId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("timeout")) {
                return DlqProcessingResult.RETRY;
            }
            log.error("DLQ: Crypto deposit failed: depositId={}, amount={}", depositId, amount);
            return DlqProcessingResult.RETRY;
        } catch (Exception e) {
            log.error("DLQ: Error in crypto deposit handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "CryptoDepositEventConsumer";
    }
}
