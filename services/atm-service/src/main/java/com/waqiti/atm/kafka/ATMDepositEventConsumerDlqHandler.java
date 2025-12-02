package com.waqiti.atm.kafka;

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
 * DLQ Handler for ATMDepositEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class ATMDepositEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public ATMDepositEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("ATMDepositEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.ATMDepositEventConsumer.dlq:ATMDepositEventConsumer.dlq}",
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
            log.error("DLQ: ATM deposit recovery (FINANCIAL CRITICAL)");
            String depositId = headers.getOrDefault("depositId", "").toString();
            String atmId = headers.getOrDefault("atmId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String amount = headers.getOrDefault("amount", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Deposit credit failed (CRITICAL - customer funds)
            if (failureReason.contains("credit") || failureReason.contains("account update")) {
                log.error("DLQ: ATM deposit credit failed: depositId={}, amount={}", depositId, amount);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Cash counting discrepancy
            if (failureReason.contains("discrepancy") || failureReason.contains("counting")) {
                log.error("DLQ: ATM cash counting discrepancy: atmId={}, depositId={}", atmId, depositId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 3: ATM envelope/check processing failure
            if (failureReason.contains("envelope") || failureReason.contains("check")) {
                log.error("DLQ: ATM envelope processing failed: depositId={}", depositId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: Hold placement failure
            if (failureReason.contains("hold") || failureReason.contains("funds availability")) {
                log.error("DLQ: Deposit hold failed: depositId={}", depositId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 5: Duplicate deposit
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 6: Receipt generation failure (non-critical)
            if (failureReason.contains("receipt")) {
                log.warn("DLQ: ATM receipt failed (non-critical): depositId={}", depositId);
                return DlqProcessingResult.DISCARDED;
            }

            log.error("DLQ: ATM deposit failed: depositId={}, atmId={}, amount={}", depositId, atmId, amount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: Error in ATM deposit handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "ATMDepositEventConsumer";
    }
}
