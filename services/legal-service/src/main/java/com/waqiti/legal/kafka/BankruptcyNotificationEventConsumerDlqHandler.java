package com.waqiti.legal.kafka;

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
 * DLQ Handler for BankruptcyNotificationEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class BankruptcyNotificationEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public BankruptcyNotificationEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("BankruptcyNotificationEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.BankruptcyNotificationEventConsumer.dlq:BankruptcyNotificationEventConsumer.dlq}",
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
            log.error("DLQ: Bankruptcy notification recovery (LEGAL CRITICAL)");
            String bankruptcyId = headers.getOrDefault("bankruptcyId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String caseNumber = headers.getOrDefault("caseNumber", "").toString();
            String bankruptcyType = headers.getOrDefault("bankruptcyType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Account freeze failure (CRITICAL - legal requirement)
            if (failureReason.contains("freeze") || failureReason.contains("account lock")) {
                log.error("DLQ: Bankruptcy account freeze failed: userId={}, case={}", userId, caseNumber);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Automatic stay violation (CRITICAL - legal violation)
            if (failureReason.contains("collection") || failureReason.contains("stay")) {
                log.error("DLQ: Collections not stopped (automatic stay): userId={}", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 3: Trustee notification failure
            if (failureReason.contains("trustee") || failureReason.contains("court")) {
                log.error("DLQ: Trustee notification failed: case={}", caseNumber);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: Discharge processing failure
            if (bankruptcyType.contains("discharge") && failureReason.contains("processing")) {
                log.error("DLQ: Discharge processing failed: userId={}", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 5: Duplicate bankruptcy notification
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 6: Reporting failure
            if (failureReason.contains("credit bureau")) {
                log.warn("DLQ: Bankruptcy credit reporting failed: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            log.error("DLQ: Bankruptcy notification failed: case={}, type={}", caseNumber, bankruptcyType);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: Error in bankruptcy notification handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "BankruptcyNotificationEventConsumer";
    }
}
