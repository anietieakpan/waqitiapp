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
 * DLQ Handler for CryptoWithdrawalEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CryptoWithdrawalEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public CryptoWithdrawalEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CryptoWithdrawalEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CryptoWithdrawalEventConsumer.dlq:CryptoWithdrawalEventConsumer.dlq}",
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
            log.error("DLQ: Crypto withdrawal recovery (FINANCIAL CRITICAL)");
            String withdrawalId = headers.getOrDefault("withdrawalId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String amount = headers.getOrDefault("amount", "").toString();
            String cryptoType = headers.getOrDefault("cryptoType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Blockchain broadcast failure (CRITICAL - funds in limbo)
            if (failureReason.contains("broadcast") || failureReason.contains("blockchain")) {
                log.error("DLQ: Blockchain broadcast failed: withdrawalId={}, amount={}", withdrawalId, amount);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Insufficient hot wallet balance
            if (failureReason.contains("insufficient") || failureReason.contains("balance")) {
                log.error("DLQ: Hot wallet insufficient balance: withdrawalId={}", withdrawalId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Invalid wallet address
            if (failureReason.contains("address") || failureReason.contains("invalid")) {
                log.error("DLQ: Invalid crypto address: withdrawalId={}", withdrawalId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 4: AML/sanctions block
            if (failureReason.contains("AML") || failureReason.contains("sanction")) {
                log.error("DLQ: Crypto withdrawal blocked by AML: userId={}", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 5: Network fee calculation failure
            if (failureReason.contains("fee") || failureReason.contains("gas")) {
                log.warn("DLQ: Crypto fee calculation failed: withdrawalId={}", withdrawalId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 6: Duplicate withdrawal
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }

            log.error("DLQ: Crypto withdrawal failed: withdrawalId={}, crypto={}", withdrawalId, cryptoType);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: Error in crypto withdrawal handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "CryptoWithdrawalEventConsumer";
    }
}
