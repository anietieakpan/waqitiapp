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
 * DLQ Handler for CryptoTransactionBlockedConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CryptoTransactionBlockedConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public CryptoTransactionBlockedConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CryptoTransactionBlockedConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CryptoTransactionBlockedConsumer.dlq:CryptoTransactionBlockedConsumer.dlq}",
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
            log.warn("DLQ: Crypto transaction blocked recovery");
            String transactionId = headers.getOrDefault("transactionId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String cryptoType = headers.getOrDefault("cryptoType", "").toString();
            String blockReason = headers.getOrDefault("blockReason", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: AML/sanctions block (CRITICAL)
            if (blockReason.contains("AML") || blockReason.contains("sanction")) {
                log.error("DLQ: Crypto blocked due to AML/sanctions: userId={}", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Fraud detection block
            if (blockReason.contains("fraud") || blockReason.contains("suspicious")) {
                log.error("DLQ: Crypto transaction fraud block: txnId={}", transactionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 3: Wallet address validation failure
            if (blockReason.contains("address") || blockReason.contains("invalid wallet")) {
                log.error("DLQ: Invalid crypto wallet address: txnId={}", transactionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 4: Transaction limit exceeded
            if (blockReason.contains("limit") || blockReason.contains("exceeded")) {
                log.warn("DLQ: Crypto transaction limit exceeded: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 5: Notification failure (non-critical)
            if (failureReason.contains("notification")) {
                log.info("DLQ: Crypto block notification failed (non-critical): userId={}", userId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 6: Duplicate block event
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }

            log.warn("DLQ: Crypto transaction blocked: txnId={}, reason={}", transactionId, blockReason);
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("DLQ: Error in crypto transaction blocked handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "CryptoTransactionBlockedConsumer";
    }
}
