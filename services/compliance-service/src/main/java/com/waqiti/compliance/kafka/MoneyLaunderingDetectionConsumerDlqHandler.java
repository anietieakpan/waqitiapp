package com.waqiti.compliance.kafka;

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
 * DLQ Handler for MoneyLaunderingDetectionConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class MoneyLaunderingDetectionConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public MoneyLaunderingDetectionConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("MoneyLaunderingDetectionConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.MoneyLaunderingDetectionConsumer.dlq:MoneyLaunderingDetectionConsumer.dlq}",
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
            log.error("DLQ: Money laundering detection (AML CRITICAL)");
            String detectionId = headers.getOrDefault("detectionId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String pattern = headers.getOrDefault("pattern", "").toString();
            String riskScore = headers.getOrDefault("riskScore", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: High-risk detection + account freeze failure (CRITICAL)
            if ((riskScore.compareTo("80") > 0) && failureReason.contains("freeze")) {
                log.error("DLQ: ML detected but freeze failed: userId={}, score={}", userId, riskScore);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: SAR filing trigger failure
            if (failureReason.contains("SAR") || failureReason.contains("filing")) {
                log.error("DLQ: SAR filing not triggered for ML: userId={}", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 3: Transaction blocking failure
            if (failureReason.contains("block") || failureReason.contains("stop")) {
                log.error("DLQ: ML transaction not blocked: userId={}", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 4: Compliance investigation trigger failure
            if (failureReason.contains("investigation")) {
                log.error("DLQ: ML investigation not triggered: detectionId={}", detectionId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 5: Duplicate detection
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 6: Transient
            if (failureReason.contains("timeout")) {
                return DlqProcessingResult.RETRY;
            }

            log.error("DLQ: ML detection failed: detectionId={}, pattern={}", detectionId, pattern);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: Error in money laundering handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "MoneyLaunderingDetectionConsumer";
    }
}
