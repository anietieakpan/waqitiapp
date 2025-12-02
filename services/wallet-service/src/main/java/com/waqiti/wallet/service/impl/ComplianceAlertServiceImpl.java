package com.waqiti.wallet.service.impl;

import com.waqiti.common.security.SensitiveDataMasker;
import com.waqiti.common.events.FraudDetectionEvent;
import com.waqiti.wallet.service.ComplianceAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of ComplianceAlertService.
 *
 * <p>Manages compliance alerts and case creation for fraud detection events.
 * Integrates with case management systems and regulatory reporting.
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceAlertServiceImpl implements ComplianceAlertService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String COMPLIANCE_ALERT_TOPIC = "compliance.critical.alerts";
    private static final String CASE_MANAGEMENT_TOPIC = "compliance.case.management";
    private static final String MANUAL_REVIEW_TOPIC = "compliance.manual.review.queue";

    @Override
    public void sendCriticalFraudAlert(FraudDetectionEvent event, int walletsAffected) {
        log.warn("COMPLIANCE: Sending critical fraud alert - Event: {}, User: {}, Wallets: {}, Risk: {}",
                event.getEventId(), SensitiveDataMasker.formatUserIdForLogging(event.getUserId()),
                walletsAffected, event.getRiskScore());

        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("alertType", "CRITICAL_FRAUD_DETECTION");
            alert.put("severity", "CRITICAL");
            alert.put("eventId", event.getEventId().toString());
            alert.put("userId", event.getUserId().toString());
            alert.put("walletsAffected", walletsAffected);
            alert.put("riskScore", event.getRiskScore());
            alert.put("riskLevel", event.getRiskLevel());
            alert.put("detectedPatterns", event.getDetectedPatterns());
            alert.put("fraudReason", event.getFraudReason());
            alert.put("requiresImmediateAction", true);
            alert.put("timestamp", LocalDateTime.now().toString());

            kafkaTemplate.send(COMPLIANCE_ALERT_TOPIC, event.getEventId().toString(), alert);

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to send critical fraud alert - Event: {}",
                    event.getEventId(), e);
        }
    }

    @Override
    public void createFraudReviewCase(FraudDetectionEvent event, int walletsAffected, int reviewSlaHours) {
        String caseId = "FRAUD-" + event.getEventId().toString().substring(0, 8);

        log.info("COMPLIANCE: Creating fraud review case - Case: {}, Event: {}, User: {}, SLA: {}h",
                caseId, event.getEventId(), SensitiveDataMasker.formatUserIdForLogging(event.getUserId()),
                reviewSlaHours);

        try {
            Map<String, Object> caseData = new HashMap<>();
            caseData.put("caseType", "FRAUD_REVIEW");
            caseData.put("caseId", caseId);
            caseData.put("eventId", event.getEventId().toString());
            caseData.put("userId", event.getUserId().toString());
            caseData.put("walletsAffected", walletsAffected);
            caseData.put("riskScore", event.getRiskScore());
            caseData.put("riskLevel", event.getRiskLevel());
            caseData.put("detectedPatterns", event.getDetectedPatterns());
            caseData.put("fraudReason", event.getFraudReason());
            caseData.put("reviewSlaHours", reviewSlaHours);
            caseData.put("priority", "HIGH");
            caseData.put("status", "PENDING_REVIEW");
            caseData.put("createdAt", LocalDateTime.now().toString());
            caseData.put("dueDate", LocalDateTime.now().plusHours(reviewSlaHours).toString());

            kafkaTemplate.send(CASE_MANAGEMENT_TOPIC, caseId, caseData);

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to create fraud review case - Event: {}",
                    event.getEventId(), e);
        }
    }

    @Override
    public void addToManualReviewQueue(FraudDetectionEvent event) {
        log.info("COMPLIANCE: Adding to manual review queue - Event: {}, User: {}, Risk: {}",
                event.getEventId(), SensitiveDataMasker.formatUserIdForLogging(event.getUserId()),
                event.getRiskScore());

        try {
            Map<String, Object> reviewItem = new HashMap<>();
            reviewItem.put("queueType", "FRAUD_MANUAL_REVIEW");
            reviewItem.put("eventId", event.getEventId().toString());
            reviewItem.put("userId", event.getUserId().toString());
            reviewItem.put("riskScore", event.getRiskScore());
            reviewItem.put("riskLevel", event.getRiskLevel());
            reviewItem.put("detectedPatterns", event.getDetectedPatterns());
            reviewItem.put("fraudReason", event.getFraudReason());
            reviewItem.put("priority", "MEDIUM");
            reviewItem.put("addedAt", LocalDateTime.now().toString());

            kafkaTemplate.send(MANUAL_REVIEW_TOPIC, event.getUserId().toString(), reviewItem);

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to add to manual review queue - Event: {}",
                    event.getEventId(), e);
        }
    }

    @Override
    public void createCriticalDltAlert(FraudDetectionEvent event, String errorMessage) {
        log.error("COMPLIANCE: Creating critical DLT alert - Event: {}, User: {}, Error: {}",
                event.getEventId(), SensitiveDataMasker.formatUserIdForLogging(event.getUserId()),
                errorMessage);

        try {
            Map<String, Object> dltAlert = new HashMap<>();
            dltAlert.put("alertType", "FRAUD_EVENT_DLT");
            dltAlert.put("severity", "CRITICAL");
            dltAlert.put("eventId", event.getEventId().toString());
            dltAlert.put("userId", event.getUserId().toString());
            dltAlert.put("riskScore", event.getRiskScore());
            dltAlert.put("riskLevel", event.getRiskLevel());
            dltAlert.put("errorMessage", errorMessage);
            dltAlert.put("requiresManualIntervention", true);
            dltAlert.put("timestamp", LocalDateTime.now().toString());

            kafkaTemplate.send(COMPLIANCE_ALERT_TOPIC, event.getEventId().toString(), dltAlert);

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to create DLT alert - Event: {}", event.getEventId(), e);
        }
    }
}
