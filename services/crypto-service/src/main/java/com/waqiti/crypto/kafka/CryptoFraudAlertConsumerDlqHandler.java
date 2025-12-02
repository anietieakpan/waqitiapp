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
 * DLQ Handler for CryptoFraudAlertConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CryptoFraudAlertConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public CryptoFraudAlertConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CryptoFraudAlertConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CryptoFraudAlertConsumer.dlq:CryptoFraudAlertConsumer.dlq}",
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
            log.error("DLQ: Crypto fraud alert recovery (SECURITY CRITICAL)");
            String alertId = headers.getOrDefault("alertId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String transactionId = headers.getOrDefault("transactionId", "").toString();
            String fraudType = headers.getOrDefault("fraudType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Transaction blocking failure (CRITICAL)
            if (failureReason.contains("block") || failureReason.contains("stop")) {
                log.error("DLQ: Crypto fraud transaction not blocked: txnId={}", transactionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Wallet freeze failure
            if (failureReason.contains("freeze") || failureReason.contains("wallet")) {
                log.error("DLQ: Crypto wallet freeze failed: userId={}", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 3: Security team notification failure
            if (failureReason.contains("notification") || failureReason.contains("alert")) {
                log.error("DLQ: Crypto fraud alert notification failed: alertId={}", alertId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: Blockchain transaction reversal attempt failure
            if (fraudType.contains("double spend") || fraudType.contains("51% attack")) {
                log.error("DLQ: Critical blockchain fraud: alertId={}, type={}", alertId, fraudType);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 5: Duplicate alert
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 6: Case management creation failure
            if (failureReason.contains("case") || failureReason.contains("investigation")) {
                log.error("DLQ: Fraud case creation failed: alertId={}", alertId);
                return DlqProcessingResult.RETRY;
            }

            log.error("DLQ: Crypto fraud alert failed: alertId={}, type={}", alertId, fraudType);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: Error in crypto fraud alert handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "CryptoFraudAlertConsumer";
    }
}
