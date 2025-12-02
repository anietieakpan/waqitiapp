package com.waqiti.compliance.kafka;

import com.waqiti.common.eventsourcing.FraudDetectedEvent;
import com.waqiti.compliance.service.SarFilingService;
import com.waqiti.compliance.service.ComplianceReportingService;
import com.waqiti.compliance.service.ComplianceAlertService;
// CRITICAL P0 FIX: Add idempotency service
import com.waqiti.common.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Fraud Detected Event Consumer for Compliance Service
 *
 * CRITICAL COMPLIANCE: Handles fraud detection events to initiate regulatory
 * reporting and compliance workflows.
 *
 * Actions:
 * 1. Create SAR (Suspicious Activity Report) filing for FinCEN
 * 2. Log compliance incident
 * 3. Alert compliance team
 * 4. Initiate investigation workflow
 * 5. Document fraud indicators
 *
 * Compliance:
 * - BSA/AML SAR filing requirements (31 CFR 1020.320)
 * - FinCEN suspicious activity reporting
 * - FINRA Rule 4530 (Reporting Requirements)
 * - Bank Secrecy Act compliance
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FraudDetectedEventConsumer {

    private final SarFilingService sarFilingService;
    private final ComplianceReportingService complianceReportingService;
    private final ComplianceAlertService complianceAlertService;
    // CRITICAL P0 FIX: Add idempotency service for duplicate prevention
    private final IdempotencyService idempotencyService;

    @KafkaListener(
        topics = "fraud.detected.events",
        groupId = "compliance-service-fraud-reporting",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleFraudDetected(
        @Payload FraudDetectedEvent event,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        @Header(value = "idempotency-key", required = false) String headerIdempotencyKey,
        Acknowledgment acknowledgment
    ) {
        log.warn("COMPLIANCE ALERT: Fraud detected event received - FraudID: {}, TransactionID: {}, Type: {}, RiskLevel: {}",
                event.getFraudId(), event.getTransactionId(), event.getFraudType(), event.getRiskLevel());

        try {
            // CRITICAL P0 FIX: Generate idempotency key to prevent duplicate SAR filings
            // Format: fraud:{fraudId}:{userId}:{action}
            String userId = event.getUserId() != null ? event.getUserId() : "unknown";

            String idempotencyKey = headerIdempotencyKey != null ?
                headerIdempotencyKey :
                String.format("fraud:%s:%s:compliance-reporting", event.getFraudId(), userId);

            // CRITICAL: Execute with idempotency protection
            // This ensures compliance actions (especially SAR filing) execute exactly once
            // even if the event is delivered multiple times (Kafka at-least-once semantics)
            idempotencyService.executeIdempotentWithPersistence(
                "compliance-service",
                "fraud-compliance-reporting",
                idempotencyKey,
                () -> processFraudEvent(event),
                Duration.ofHours(24) // Keep idempotency record for 24 hours
            );

            acknowledgment.acknowledge();
            log.info("COMPLIANCE: Fraud compliance actions completed for fraud: {} (idempotency: {})",
                event.getFraudId(), idempotencyKey);

        } catch (Exception e) {
            log.error("COMPLIANCE CRITICAL: Failed to process fraud detected event - FraudID: {}, TransactionID: {}",
                     event.getFraudId(), event.getTransactionId(), e);

            // DO NOT acknowledge - this is critical compliance functionality
            throw new RuntimeException("Failed to process fraud detected event for compliance", e);
        }
    }

    /**
     * CRITICAL P0 FIX: Process fraud event with full business logic
     * This method is wrapped in idempotent execution to prevent duplicate SAR filings
     */
    @Transactional
    private Void processFraudEvent(FraudDetectedEvent event) {
        try {
            // Step 1: Determine if SAR filing is required
            if (requiresSarFiling(event)) {
                log.warn("COMPLIANCE: Initiating SAR filing for fraud: {} (TransactionID: {})",
                        event.getFraudId(), event.getTransactionId());

                String sarId = sarFilingService.fileForFraudDetection(
                    event.getFraudId(),
                    event.getTransactionId(),
                    event.getUserId(),
                    event.getFraudType(),
                    event.getRiskScore(),
                    event.getFraudIndicators()
                );

                log.info("COMPLIANCE: SAR filed successfully - SAR ID: {}, Fraud ID: {}", sarId, event.getFraudId());
            } else {
                log.info("COMPLIANCE: SAR filing not required for fraud: {} (RiskLevel: {}, RiskScore: {})",
                        event.getFraudId(), event.getRiskLevel(), event.getRiskScore());
            }

            // Step 2: Log compliance incident
            complianceReportingService.logFraudIncident(
                event.getFraudId(),
                event.getTransactionId(),
                event.getUserId(),
                event.getFraudType(),
                event.getRiskLevel(),
                event.getRiskScore(),
                event.getFraudIndicators(),
                event.getActionTaken()
            );

            // Step 3: Alert compliance team based on risk level
            if (isHighRisk(event.getRiskLevel(), event.getRiskScore())) {
                complianceAlertService.sendCriticalAlert(
                    "FRAUD_DETECTED",
                    String.format("High-risk fraud detected - FraudID: %s, Type: %s, RiskLevel: %s, RiskScore: %.2f",
                        event.getFraudId(), event.getFraudType(), event.getRiskLevel(), event.getRiskScore()),
                    event
                );
            } else {
                complianceAlertService.sendStandardAlert(
                    "FRAUD_DETECTED",
                    String.format("Fraud detected - FraudID: %s, Type: %s, RiskLevel: %s",
                        event.getFraudId(), event.getFraudType(), event.getRiskLevel()),
                    event
                );
            }

            // Step 4: Initiate investigation workflow
            complianceReportingService.initiateInvestigation(
                event.getFraudId(),
                event.getUserId(),
                event.getFraudType(),
                isHighRisk(event.getRiskLevel(), event.getRiskScore()) ? "HIGH_PRIORITY" : "STANDARD"
            );

            log.info("COMPLIANCE: Idempotent fraud processing completed for fraud: {}", event.getFraudId());
            return null; // Void return for idempotent execution

        } catch (Exception e) {
            log.error("COMPLIANCE CRITICAL: Failed to process fraud event - FraudID: {}, TransactionID: {}",
                     event.getFraudId(), event.getTransactionId(), e);
            throw new RuntimeException("Failed to process fraud event for compliance", e);
        }
    }

    /**
     * Determine if SAR filing is required for this fraud event.
     *
     * SAR filing criteria (31 CFR 1020.320):
     * - Known or suspected fraud >= $5,000
     * - Transactions >= $25,000 involving potential money laundering
     * - Any amount if suspected identity theft or computer intrusion
     */
    private boolean requiresSarFiling(FraudDetectedEvent event) {
        if (event == null) {
            return false;
        }

        // High-risk fraud always requires SAR
        if (isHighRisk(event.getRiskLevel(), event.getRiskScore())) {
            return true;
        }

        // Specific fraud types that require SAR regardless of amount
        if (event.getFraudType() != null) {
            String fraudType = event.getFraudType().toUpperCase();
            if (fraudType.contains("IDENTITY_THEFT") ||
                fraudType.contains("ACCOUNT_TAKEOVER") ||
                fraudType.contains("MONEY_LAUNDERING") ||
                fraudType.contains("TERRORIST_FINANCING")) {
                return true;
            }
        }

        // Medium risk with significant indicators
        if ("MEDIUM".equalsIgnoreCase(event.getRiskLevel()) &&
            event.getFraudIndicators() != null &&
            event.getFraudIndicators().size() >= 3) {
            return true;
        }

        return false;
    }

    /**
     * Determine if fraud is high risk.
     */
    private boolean isHighRisk(String riskLevel, Double riskScore) {
        if (riskLevel != null) {
            String level = riskLevel.toUpperCase();
            if ("HIGH".equals(level) || "CRITICAL".equals(level)) {
                return true;
            }
        }

        return riskScore != null && riskScore >= 80.0;
    }
}
