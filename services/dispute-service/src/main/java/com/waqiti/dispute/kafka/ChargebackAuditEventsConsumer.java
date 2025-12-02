package com.waqiti.dispute.kafka;

import com.waqiti.common.kafka.RetryableKafkaListener;
import com.waqiti.dispute.dto.ChargebackAuditEvent;
import com.waqiti.dispute.service.ChargebackComplianceService;
import com.waqiti.dispute.service.DisputeAuditService;
import com.waqiti.common.exception.KafkaRetryException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Chargeback Audit Events Consumer
 *
 * PURPOSE: Process chargeback audit events for compliance and fraud prevention
 *
 * BUSINESS CRITICAL: Chargebacks cost $50K-200K/month if not properly managed
 * Missing this consumer means:
 * - No chargeback audit trail (Visa/Mastercard compliance failure)
 * - Failed dispute evidence collection
 * - Merchant account termination risk
 * - Lost chargeback defenses (automatic merchant liability)
 *
 * COMPLIANCE: Visa Core Rules, Mastercard Chargeback Guide, PCI-DSS 12.5
 *
 * IMPLEMENTATION PRIORITY: P0 CRITICAL
 *
 * @author Waqiti Dispute Team
 * @version 1.0.0
 * @since 2025-10-13
 */
@Service
@Slf4j
public class ChargebackAuditEventsConsumer {

    private final ChargebackComplianceService complianceService;
    private final DisputeAuditService auditService;
    private final Counter chargebacksAuditedCounter;
    private final Counter chargebacksFailedCounter;

    @Autowired
    public ChargebackAuditEventsConsumer(
            ChargebackComplianceService complianceService,
            DisputeAuditService auditService,
            MeterRegistry meterRegistry) {

        this.complianceService = complianceService;
        this.auditService = auditService;

        this.chargebacksAuditedCounter = Counter.builder("chargeback.audit.processed")
                .description("Number of chargeback audit events processed")
                .register(meterRegistry);

        this.chargebacksFailedCounter = Counter.builder("chargeback.audit.failed")
                .description("Number of chargeback audit events that failed")
                .register(meterRegistry);
    }

    /**
     * Process chargeback audit event
     */
    @RetryableKafkaListener(
        topics = "chargeback-audit-events",
        groupId = "dispute-service-chargeback-audit",
        containerFactory = "kafkaListenerContainerFactory",
        retries = 5,
        backoffMultiplier = 2.0,
        initialBackoff = 1000L
    )
    @Transactional
    public void handleChargebackAuditEvent(
            @Payload ChargebackAuditEvent event,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        Instant startTime = Instant.now();

        log.info("Processing chargeback audit event: chargebackId={}, merchantId={}, amount={}, reason={}",
                event.getChargebackId(),
                event.getMerchantId(),
                event.getAmount(),
                event.getReasonCode());

        try {
            // Step 1: Validate event
            validateEvent(event);

            // Step 2: Check idempotency
            if (auditService.isChargebackAlreadyAudited(event.getChargebackId())) {
                log.info("Chargeback already audited (idempotent): chargebackId={}",
                        event.getChargebackId());
                acknowledgment.acknowledge();
                return;
            }

            // Step 3: Create audit trail entry (immutable, tamper-proof)
            auditService.createAuditTrail(
                    event.getChargebackId(),
                    event.getTransactionId(),
                    event.getMerchantId(),
                    event.getAmount(),
                    event.getReasonCode(),
                    event.getCardNetwork(),
                    event.getDisputeDate()
            );

            // Step 4: Calculate chargeback deadline (Visa: 30 days, Mastercard: 45 days)
            Instant deadline = calculateChargebackDeadline(
                    event.getCardNetwork(),
                    event.getDisputeDate()
            );
            auditService.setChargebackDeadline(event.getChargebackId(), deadline);

            // Step 5: Collect evidence for defense
            complianceService.initiateEvidenceCollection(
                    event.getChargebackId(),
                    event.getTransactionId(),
                    event.getMerchantId()
            );

            // Step 6: Check merchant chargeback ratio (critical for account health)
            double chargebackRatio = complianceService.calculateMerchantChargebackRatio(
                    event.getMerchantId()
            );

            // Alert if exceeding card network thresholds (Visa: 0.9%, Mastercard: 1.5%)
            if (chargebackRatio > 0.009) { // 0.9% threshold
                log.warn("Merchant exceeding chargeback threshold: merchantId={}, ratio={}%",
                        event.getMerchantId(), chargebackRatio * 100);

                complianceService.alertMerchantRiskTeam(
                        event.getMerchantId(),
                        chargebackRatio,
                        "Chargeback ratio exceeds Visa threshold - account termination risk"
                );
            }

            // Step 7: Categorize chargeback by reason code
            String category = complianceService.categorizeChargeback(event.getReasonCode());
            auditService.recordChargebackCategory(event.getChargebackId(), category);

            // Step 8: Check for fraud patterns (same IP, device, BIN)
            if (complianceService.detectFraudPattern(event)) {
                log.warn("Fraud pattern detected in chargeback: chargebackId={}, merchantId={}",
                        event.getChargebackId(), event.getMerchantId());

                complianceService.escalateToFraudTeam(event);
            }

            // Step 9: Update merchant analytics
            complianceService.updateMerchantChargebackMetrics(
                    event.getMerchantId(),
                    event.getAmount(),
                    category
            );

            // Step 10: Schedule deadline reminder (3 days before deadline)
            complianceService.scheduleDeadlineReminder(
                    event.getChargebackId(),
                    deadline.minus(3, ChronoUnit.DAYS)
            );

            // Step 11: Mark as audited
            auditService.markChargebackAudited(event.getChargebackId());

            // Step 12: Acknowledge
            acknowledgment.acknowledge();

            // Metrics
            chargebacksAuditedCounter.increment();

            long processingTime = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            log.info("Chargeback audit completed: chargebackId={}, processingTime={}ms",
                    event.getChargebackId(), processingTime);

        } catch (Exception e) {
            log.error("Failed to audit chargeback: chargebackId={}, will retry",
                    event.getChargebackId(), e);

            chargebacksFailedCounter.increment();

            throw new KafkaRetryException(
                    "Failed to audit chargeback",
                    e,
                    event.getChargebackId().toString()
            );
        }
    }

    /**
     * Calculate chargeback response deadline based on card network
     */
    private Instant calculateChargebackDeadline(String cardNetwork, Instant disputeDate) {
        return switch (cardNetwork.toUpperCase()) {
            case "VISA" -> disputeDate.plus(30, ChronoUnit.DAYS);
            case "MASTERCARD" -> disputeDate.plus(45, ChronoUnit.DAYS);
            case "AMEX" -> disputeDate.plus(20, ChronoUnit.DAYS);
            case "DISCOVER" -> disputeDate.plus(30, ChronoUnit.DAYS);
            default -> disputeDate.plus(30, ChronoUnit.DAYS); // Default to Visa rules
        };
    }

    /**
     * Validate event
     */
    private void validateEvent(ChargebackAuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        if (event.getChargebackId() == null) {
            throw new IllegalArgumentException("Chargeback ID cannot be null");
        }

        if (event.getTransactionId() == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }

        if (event.getMerchantId() == null) {
            throw new IllegalArgumentException("Merchant ID cannot be null");
        }

        if (event.getAmount() == null || event.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (event.getReasonCode() == null || event.getReasonCode().isBlank()) {
            throw new IllegalArgumentException("Reason code cannot be null or empty");
        }

        if (event.getCardNetwork() == null || event.getCardNetwork().isBlank()) {
            throw new IllegalArgumentException("Card network cannot be null or empty");
        }

        if (event.getDisputeDate() == null) {
            throw new IllegalArgumentException("Dispute date cannot be null");
        }
    }

    /**
     * Handle DLQ messages
     */
    @KafkaListener(topics = "chargeback-audit-events-dispute-service-dlq")
    public void handleDLQMessage(@Payload ChargebackAuditEvent event) {
        log.error("CRITICAL: Chargeback audit in DLQ - COMPLIANCE FAILURE: chargebackId={}, merchantId={}",
                event.getChargebackId(), event.getMerchantId());

        try {
            // Log to persistent storage (CRITICAL for compliance)
            auditService.logDLQChargeback(
                    event.getChargebackId(),
                    event,
                    "Chargeback audit failed permanently - COMPLIANCE RISK"
            );

            // CRITICAL ALERT - immediate intervention required
            complianceService.alertComplianceTeam(
                    "CRITICAL",
                    "Chargeback audit stuck in DLQ - missing audit trail",
                    java.util.Map.of(
                            "chargebackId", event.getChargebackId().toString(),
                            "merchantId", event.getMerchantId().toString(),
                            "amount", event.getAmount().toString(),
                            "reasonCode", event.getReasonCode(),
                            "cardNetwork", event.getCardNetwork(),
                            "riskLevel", "HIGH - Missing required audit trail for card network compliance"
                    )
            );

            // Calculate time until deadline
            Instant deadline = calculateChargebackDeadline(
                    event.getCardNetwork(),
                    event.getDisputeDate()
            );
            long hoursUntilDeadline = ChronoUnit.HOURS.between(Instant.now(), deadline);

            // URGENT if less than 72 hours to deadline
            if (hoursUntilDeadline < 72) {
                complianceService.escalateToManagement(
                        "URGENT: Chargeback audit failure with approaching deadline",
                        event,
                        String.format("Only %d hours until deadline. Manual audit required immediately.",
                                hoursUntilDeadline)
                );
            }

            // Create incident ticket for manual processing
            complianceService.createChargebackIncident(
                    event.getChargebackId(),
                    "CHARGEBACK_AUDIT_FAILURE",
                    "P0 CRITICAL",
                    String.format("Chargeback audit failed after all retries. " +
                            "Manual audit required. Deadline: %s (%d hours)",
                            deadline, hoursUntilDeadline)
            );

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process chargeback DLQ message - ESCALATE: chargebackId={}",
                    event.getChargebackId(), e);

            // Emergency fallback - write to file system
            auditService.writeToEmergencyLog(event, e);
        }
    }
}
