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
 * DLQ Handler for TerroristFinancingDetectionEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class TerroristFinancingDetectionEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public TerroristFinancingDetectionEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("TerroristFinancingDetectionEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.TerroristFinancingDetectionEventsConsumer.dlq:TerroristFinancingDetectionEventsConsumer.dlq}",
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
            log.error("DLQ: CRITICAL - Terrorist financing detection (PATRIOT ACT/BSA)");
            String detectionId = headers.getOrDefault("detectionId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String pattern = headers.getOrDefault("pattern", "").toString();
            String riskScore = headers.getOrDefault("riskScore", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Terrorist financing = PATRIOT Act requirement - immediate action

            // Strategy 1: High risk detection + freeze failure (HIGHEST PRIORITY)
            if ((riskScore.compareTo("80") > 0) && failureReason.contains("freeze failed")) {
                log.error("DLQ: CRITICAL - TF detection but freeze failed: userId={}, score={}", userId, riskScore);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Law enforcement notification failure
            if (failureReason.contains("law enforcement") || failureReason.contains("FinCEN alert")) {
                log.error("DLQ: Failed to notify authorities of TF: detectionId={}", detectionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 3: Transaction blocking failure
            if (failureReason.contains("transaction block") || failureReason.contains("funds hold")) {
                log.error("DLQ: Failed to block transactions for TF: userId={}", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 4: OFAC integration failure
            if (failureReason.contains("OFAC") || failureReason.contains("sanctions")) {
                log.error("DLQ: OFAC check failed during TF detection: userId={}", userId);
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

            log.error("DLQ: TF detection failure - NATIONAL SECURITY ESCALATION: detectionId={}", detectionId);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: CRITICAL - Error in TF detection handler", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    @Override
    protected String getServiceName() {
        return "TerroristFinancingDetectionEventsConsumer";
    }
}
