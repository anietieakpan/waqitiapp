package com.waqiti.payment.kafka;

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
 * DLQ Handler for BankErrorHandlingConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class BankErrorHandlingConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public BankErrorHandlingConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("BankErrorHandlingConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.BankErrorHandlingConsumer.dlq:BankErrorHandlingConsumer.dlq}",
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
            log.error("DLQ: Bank error handling recovery");
            String errorCode = headers.getOrDefault("errorCode", "").toString();
            String bankName = headers.getOrDefault("bankName", "").toString();
            String transactionId = headers.getOrDefault("transactionId", "").toString();
            String errorType = headers.getOrDefault("errorType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Insufficient funds (R01 ACH code)
            if (errorCode.contains("R01") || errorType.contains("insufficient")) {
                log.warn("DLQ: Bank insufficient funds error: txnId={}", transactionId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 2: Account closed (R02 ACH code)
            if (errorCode.contains("R02") || errorType.contains("closed")) {
                log.error("DLQ: Bank account closed: txnId={}", transactionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 3: Invalid account (R03 ACH code)
            if (errorCode.contains("R03") || errorType.contains("invalid account")) {
                log.error("DLQ: Invalid bank account: txnId={}", transactionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 4: Bank timeout/unavailable
            if (failureReason.contains("timeout") || failureReason.contains("unavailable")) {
                log.warn("DLQ: Bank service unavailable: bank={}", bankName);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 5: Duplicate transaction
            if (errorCode.contains("duplicate") || failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 6: Authorization revoked (R07 ACH code)
            if (errorCode.contains("R07") || errorType.contains("authorization")) {
                log.error("DLQ: Bank authorization revoked: txnId={}", transactionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            log.error("DLQ: Bank error: code={}, bank={}, txnId={}", errorCode, bankName, transactionId);
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("DLQ: Error in bank error handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "BankErrorHandlingConsumer";
    }
}
