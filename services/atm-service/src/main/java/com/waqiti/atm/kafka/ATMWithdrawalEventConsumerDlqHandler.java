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
 * DLQ Handler for ATMWithdrawalEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class ATMWithdrawalEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public ATMWithdrawalEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("ATMWithdrawalEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.ATMWithdrawalEventConsumer.dlq:ATMWithdrawalEventConsumer.dlq}",
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
            log.info("DLQ: Processing ATM withdrawal recovery");
            String atmId = headers.getOrDefault("atmId", "").toString();
            String cardNumber = headers.getOrDefault("cardNumber", "").toString();
            String amount = headers.getOrDefault("amount", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Insufficient ATM cash
            if (failureReason.contains("insufficient ATM cash")) {
                log.error("DLQ: ATM out of cash: atmId={}, amount={}", atmId, amount);
                // Alert operations to refill ATM
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Insufficient account balance
            if (failureReason.contains("insufficient balance")) {
                log.info("DLQ: Insufficient balance for ATM withdrawal: card={}", cardNumber);
                // User already notified at ATM
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 3: Card blocked/frozen
            if (failureReason.contains("card blocked") || failureReason.contains("frozen")) {
                log.info("DLQ: Card blocked, ATM withdrawal correctly denied: card={}", cardNumber);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 4: Daily withdrawal limit exceeded
            if (failureReason.contains("limit exceeded") || failureReason.contains("daily limit")) {
                log.info("DLQ: Withdrawal limit exceeded: card={}, amount={}", cardNumber, amount);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 5: ATM hardware malfunction
            if (failureReason.contains("hardware") || failureReason.contains("dispenser")) {
                log.error("DLQ: ATM hardware malfunction: atmId={}", atmId);
                // Reverse wallet debit if funds were deducted
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 6: Network connectivity issue
            if (failureReason.contains("network") || failureReason.contains("timeout")) {
                log.warn("DLQ: ATM network issue: atmId={}", atmId);
                // Check if withdrawal completed, may need reconciliation
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 7: Duplicate withdrawal attempt
            if (failureReason.contains("duplicate")) {
                log.info("DLQ: Duplicate ATM withdrawal prevented: card={}", cardNumber);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 8: Fraud detection block
            if (failureReason.contains("fraud")) {
                log.warn("DLQ: ATM withdrawal blocked by fraud detection: card={}", cardNumber);
                return DlqProcessingResult.DISCARDED;
            }

            // Default: Manual review (ATM transactions are critical)
            log.error("DLQ: Unknown ATM withdrawal failure, requires reconciliation: {}", failureReason);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: Error handling ATM withdrawal event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "ATMWithdrawalEventConsumer";
    }
}
