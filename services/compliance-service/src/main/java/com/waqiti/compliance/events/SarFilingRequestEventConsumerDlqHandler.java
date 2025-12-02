package com.waqiti.compliance.events;

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
 * DLQ Handler for SarFilingRequestEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class SarFilingRequestEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public SarFilingRequestEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("SarFilingRequestEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.SarFilingRequestEventConsumer.dlq:SarFilingRequestEventConsumer.dlq}",
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
            log.error("DLQ: CRITICAL - SAR filing (Suspicious Activity Report - FEDERAL LAW)");
            String sarId = headers.getOrDefault("sarId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String activityType = headers.getOrDefault("activityType", "").toString();
            String deadline = headers.getOrDefault("deadline", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // SAR filing is MANDATORY under Bank Secrecy Act - cannot be missed

            // Strategy 1: Filing deadline approaching/missed (HIGHEST PRIORITY)
            if (failureReason.contains("deadline") || deadline.contains("URGENT")) {
                log.error("DLQ: CRITICAL - SAR filing deadline risk: sarId={}, deadline={}", sarId, deadline);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: FinCEN BSA E-Filing system unavailable
            if (failureReason.contains("FinCEN") || failureReason.contains("E-Filing")) {
                log.error("DLQ: FinCEN filing system unavailable, retrying: sarId={}", sarId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Incomplete SAR data
            if (failureReason.contains("incomplete") || failureReason.contains("missing data")) {
                log.error("DLQ: SAR has incomplete data: sarId={}", sarId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 4: Duplicate SAR
            if (failureReason.contains("duplicate") || failureReason.contains("already filed")) {
                log.info("DLQ: Duplicate SAR filing: sarId={}", sarId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 5: Compliance officer notification failure
            if (failureReason.contains("notification") || failureReason.contains("alert")) {
                log.error("DLQ: Failed to notify compliance officer of SAR: sarId={}", sarId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 6: Record retention failure
            if (failureReason.contains("retention") || failureReason.contains("archive")) {
                log.error("DLQ: SAR record retention failed: sarId={}", sarId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 7: Transient
            if (failureReason.contains("timeout")) {
                return DlqProcessingResult.RETRY;
            }

            log.error("DLQ: SAR filing failure - LEGAL/COMPLIANCE ESCALATION: sarId={}", sarId);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: CRITICAL - Error in SAR filing handler", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    @Override
    protected String getServiceName() {
        return "SarFilingRequestEventConsumer";
    }
}
