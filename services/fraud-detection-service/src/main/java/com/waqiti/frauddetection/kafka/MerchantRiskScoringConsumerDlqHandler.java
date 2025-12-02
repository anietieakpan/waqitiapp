package com.waqiti.frauddetection.kafka;

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
 * DLQ Handler for MerchantRiskScoringConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class MerchantRiskScoringConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public MerchantRiskScoringConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("MerchantRiskScoringConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.MerchantRiskScoringConsumer.dlq:MerchantRiskScoringConsumer.dlq}",
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
            // FIXED: Implement merchant risk scoring recovery logic
            log.warn("Processing DLQ merchant risk scoring event");

            String scoringId = headers.getOrDefault("scoringId", "").toString();
            String merchantId = headers.getOrDefault("merchantId", "").toString();
            String riskScore = headers.getOrDefault("riskScore", "0").toString();
            String riskCategory = headers.getOrDefault("riskCategory", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error, marking for retry. Scoring: {}", scoringId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("MerchantNotFound")) {
                log.warn("DLQ: Merchant not found for risk scoring. Merchant: {}. Retrying.", merchantId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyScored")) {
                log.info("DLQ: Merchant already scored. Merchant: {}. Marking as resolved.", merchantId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("HighRiskNotFlagged") && Double.parseDouble(riskScore) > 85) {
                log.error("DLQ: High-risk merchant not flagged. Merchant: {}, Score: {}. Retrying.", merchantId, riskScore);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("RiskModelUnavailable")) {
                log.warn("DLQ: Risk scoring model unavailable. Merchant: {}. Retrying.", merchantId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in merchant risk scoring. Merchant: {}. Manual review required.", merchantId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in merchant risk scoring. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ merchant risk scoring event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "MerchantRiskScoringConsumer";
    }
}
