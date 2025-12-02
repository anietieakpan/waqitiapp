package com.waqiti.investment.kafka;

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
 * DLQ Handler for DividendPaymentEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class DividendPaymentEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public DividendPaymentEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("DividendPaymentEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.DividendPaymentEventConsumer.dlq:DividendPaymentEventConsumer.dlq}",
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
            log.error("DLQ: Dividend payment recovery (FINANCIAL CRITICAL)");
            String dividendId = headers.getOrDefault("dividendId", "").toString();
            String accountId = headers.getOrDefault("accountId", "").toString();
            String amount = headers.getOrDefault("amount", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("credit") || failureReason.contains("payment failed")) {
                log.error("DLQ: Dividend payment credit failed: dividendId={}, amount={}", dividendId, amount);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("account") || failureReason.contains("closed")) {
                log.error("DLQ: Account issue for dividend: accountId={}", accountId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }
            if (failureReason.contains("tax") || failureReason.contains("withholding")) {
                log.error("DLQ: Tax calculation failed for dividend: dividendId={}", dividendId);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("timeout")) {
                return DlqProcessingResult.RETRY;
            }
            log.error("DLQ: Dividend payment failed: dividendId={}, accountId={}", dividendId, accountId);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        } catch (Exception e) {
            log.error("DLQ: Error in dividend payment handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "DividendPaymentEventConsumer";
    }
}
