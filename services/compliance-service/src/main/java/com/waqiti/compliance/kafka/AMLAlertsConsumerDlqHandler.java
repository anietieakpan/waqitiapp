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
 * DLQ Handler for AMLAlertsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AMLAlertsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AMLAlertsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AMLAlertsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AMLAlertsConsumer.dlq:AMLAlertsConsumer.dlq}",
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
            log.error("DLQ: CRITICAL - Processing AML alert recovery");
            String alertId = headers.getOrDefault("alertId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String alertType = headers.getOrDefault("alertType", "").toString();
            String severity = headers.getOrDefault("severity", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // ALL AML alerts are CRITICAL - must not be lost
            log.error("DLQ: AML Alert - alertId={}, user={}, type={}, severity={}",
                alertId, userId, alertType, severity);

            // Strategy 1: Duplicate alert
            if (failureReason.contains("duplicate")) {
                log.info("DLQ: Duplicate AML alert: alertId={}", alertId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 2: Case management system failure
            if (failureReason.contains("case management") || failureReason.contains("ticketing")) {
                log.error("DLQ: Failed to create AML case: alertId={}", alertId);
                // CRITICAL - must create case for investigation
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Compliance team notification failure
            if (failureReason.contains("notification") || failureReason.contains("email")) {
                log.error("DLQ: Failed to notify compliance team: alertId={}", alertId);
                // CRITICAL - team must be notified
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: Account freeze failure
            if (failureReason.contains("freeze") || failureReason.contains("suspension")) {
                log.error("DLQ: CRITICAL - Failed to freeze account for AML: userId={}", userId);
                // HIGHEST PRIORITY - account must be frozen
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 5: Regulatory reporting failure
            if (failureReason.contains("SAR") || failureReason.contains("regulatory report")) {
                log.error("DLQ: Failed to create regulatory report: alertId={}", alertId);
                // Legal requirement - must file report
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 6: High severity alerts - immediate escalation
            if (severity.contains("HIGH") || severity.contains("CRITICAL")) {
                log.error("DLQ: HIGH SEVERITY AML alert in DLQ: alertId={}, user={}", alertId, userId);
                // Escalate to management immediately
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 7: Database/transient error
            if (failureReason.contains("database") || failureReason.contains("timeout")) {
                log.warn("DLQ: Transient error for AML alert, retrying: alertId={}", alertId);
                return DlqProcessingResult.RETRY;
            }

            // Default: CRITICAL - all AML alerts require manual review
            log.error("DLQ: Unknown AML alert failure - COMPLIANCE REVIEW REQUIRED: alertId={}, reason={}",
                alertId, failureReason);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: CRITICAL - Error handling AML alert event", e);
            // Never lose AML alerts - escalate
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    @Override
    protected String getServiceName() {
        return "AMLAlertsConsumer";
    }
}
