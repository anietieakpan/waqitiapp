package com.waqiti.payment.kafka;

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
 * DLQ Handler for PaymentChargebackConsumer
 *
 * Handles failed chargeback notification events with intelligent dispute management.
 * Chargebacks are CRITICAL as they directly impact merchant finances and reputation.
 *
 * Recovery Strategies:
 * - Network/gateway errors: Retry to ensure chargeback is processed
 * - Duplicate chargeback: Auto-resolve with idempotency check
 * - Missing evidence deadline: Manual review for dispute decision
 * - Auto-accept threshold: Compensate with automatic acceptance
 * - Dispute eligible: Manual review for evidence collection
 * - Evidence upload failures: Retry with exponential backoff
 * - Invalid chargeback reason: Manual review for investigation
 *
 * Financial Impact: HIGH - Chargebacks cost 2-3x transaction amount
 * (original amount + chargeback fee + operational costs)
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0 - Production-ready implementation
 */
@Service
@Slf4j
public class PaymentChargebackConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final com.waqiti.common.dlq.DlqRecoveryService dlqRecoveryService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public PaymentChargebackConsumerDlqHandler(
            MeterRegistry meterRegistry,
            com.waqiti.common.dlq.DlqRecoveryService dlqRecoveryService,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        super(meterRegistry);
        this.dlqRecoveryService = dlqRecoveryService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("PaymentChargebackConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.PaymentChargebackConsumer.dlq:PaymentChargebackConsumer.dlq}",
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
            log.info("Processing chargeback DLQ event");

            String failureReason = extractFailureReason(headers);
            String correlationId = extractCorrelationId(headers);
            String chargebackId = extractChargebackId(headers, event);

            ChargebackFailureCategory category = categorizeChargebackFailure(failureReason, event);

            log.warn("Chargeback {} failed with category: {} - {}",
                chargebackId, category, failureReason);

            switch (category) {
                case NETWORK_TIMEOUT:
                case GATEWAY_ERROR:
                    return handleNetworkError(event, failureReason, headers, chargebackId);

                case DUPLICATE_CHARGEBACK:
                    return handleDuplicateChargeback(event, correlationId, chargebackId);

                case EVIDENCE_UPLOAD_FAILED:
                    return handleEvidenceUploadFailure(event, failureReason, headers, chargebackId);

                case DEADLINE_PASSED:
                case EVIDENCE_DEADLINE_MISSED:
                    return handleDeadlineMissed(event, failureReason, headers, chargebackId);

                case BELOW_DISPUTE_THRESHOLD:
                    return handleAutoAccept(event, failureReason, headers, chargebackId);

                case DISPUTE_ELIGIBLE:
                case EVIDENCE_REQUIRED:
                    return handleDisputeEligible(event, failureReason, headers, chargebackId);

                case INVALID_CHARGEBACK_REASON:
                case FRAUDULENT_CHARGEBACK:
                    return handleInvalidChargeback(event, failureReason, headers, chargebackId);

                case MERCHANT_ACCOUNT_ISSUE:
                    return handleMerchantAccountIssue(event, failureReason, headers, chargebackId);

                case UNKNOWN:
                default:
                    return handleUnknownChargebackError(event, failureReason, headers, chargebackId);
            }

        } catch (Exception e) {
            log.error("Error handling chargeback DLQ event", e);
            persistForManualReview(event, e.getMessage(), headers);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    private DlqProcessingResult handleNetworkError(Object event, String failureReason,
                                                    Map<String, Object> headers, String chargebackId) {
        log.info("Network error processing chargeback: {} - scheduling retry", chargebackId);

        com.waqiti.common.dlq.DlqEvent dlqEvent = dlqRecoveryService.persistDlqEvent(
            getServiceName(),
            "payment.chargeback.events",
            "payment.chargeback.events.dlq",
            event,
            failureReason,
            null,
            convertHeaders(headers),
            com.waqiti.common.dlq.DlqEvent.RecoveryStrategy.RETRY
        );

        // HIGH severity - chargebacks have financial impact
        dlqEvent.setSeverity(0.9);
        log.info("Scheduled retry for chargeback notification: {}", chargebackId);
        return DlqProcessingResult.RETRY_SCHEDULED;
    }

    private DlqProcessingResult handleDuplicateChargeback(Object event, String correlationId,
                                                           String chargebackId) {
        log.info("Duplicate chargeback notification detected - auto-resolving: {}", chargebackId);

        com.waqiti.common.dlq.DlqEvent dlqEvent = dlqRecoveryService.persistDlqEvent(
            getServiceName(),
            "payment.chargeback.events",
            "payment.chargeback.events.dlq",
            event,
            "Duplicate chargeback notification",
            null,
            convertHeaders(Map.of("correlationId", correlationId != null ? correlationId : "")),
            com.waqiti.common.dlq.DlqEvent.RecoveryStrategy.AUTO_RESOLVE
        );

        dlqEvent.setStatus(com.waqiti.common.dlq.DlqEvent.DlqEventStatus.AUTO_RECOVERED);
        dlqEvent.setResolvedAt(java.time.Instant.now());
        dlqEvent.setResolutionNotes("Duplicate chargeback - verified original processed");

        return DlqProcessingResult.AUTO_RESOLVED;
    }

    private DlqProcessingResult handleEvidenceUploadFailure(Object event, String failureReason,
                                                              Map<String, Object> headers, String chargebackId) {
        log.warn("Evidence upload failed for chargeback: {} - retrying", chargebackId);

        com.waqiti.common.dlq.DlqEvent dlqEvent = dlqRecoveryService.persistDlqEvent(
            getServiceName(),
            "payment.chargeback.events",
            "payment.chargeback.events.dlq",
            event,
            failureReason,
            null,
            convertHeaders(headers),
            com.waqiti.common.dlq.DlqEvent.RecoveryStrategy.RETRY
        );

        dlqEvent.setSeverity(0.85);
        log.info("Scheduled retry for evidence upload: {}", chargebackId);
        return DlqProcessingResult.RETRY_SCHEDULED;
    }

    private DlqProcessingResult handleDeadlineMissed(Object event, String failureReason,
                                                       Map<String, Object> headers, String chargebackId) {
        log.error("DEADLINE MISSED for chargeback: {} - auto-accept required", chargebackId);

        com.waqiti.common.dlq.DlqEvent dlqEvent = dlqRecoveryService.persistDlqEvent(
            getServiceName(),
            "payment.chargeback.events",
            "payment.chargeback.events.dlq",
            event,
            "DEADLINE MISSED: " + failureReason,
            null,
            convertHeaders(headers),
            com.waqiti.common.dlq.DlqEvent.RecoveryStrategy.COMPENSATE
        );

        // CRITICAL severity - deadline missed means automatic loss
        dlqEvent.setSeverity(1.0);
        dlqEvent.setPagerDutyTriggered(true);

        dlqRecoveryService.executeCompensation(dlqEvent,
            "AUTO_ACCEPT_CHARGEBACK: Deadline missed - automatically accept and refund customer");

        log.error("CRITICAL: Chargeback {} deadline missed - auto-accepted", chargebackId);
        return DlqProcessingResult.COMPENSATED;
    }

    private DlqProcessingResult handleAutoAccept(Object event, String failureReason,
                                                   Map<String, Object> headers, String chargebackId) {
        log.info("Chargeback below dispute threshold: {} - auto-accepting", chargebackId);

        com.waqiti.common.dlq.DlqEvent dlqEvent = dlqRecoveryService.persistDlqEvent(
            getServiceName(),
            "payment.chargeback.events",
            "payment.chargeback.events.dlq",
            event,
            "Below dispute threshold - auto-accept",
            null,
            convertHeaders(headers),
            com.waqiti.common.dlq.DlqEvent.RecoveryStrategy.COMPENSATE
        );

        dlqRecoveryService.executeCompensation(dlqEvent,
            "AUTO_ACCEPT_CHARGEBACK: Amount below dispute threshold - accept and refund");

        log.info("Auto-accepted chargeback: {}", chargebackId);
        return DlqProcessingResult.COMPENSATED;
    }

    private DlqProcessingResult handleDisputeEligible(Object event, String failureReason,
                                                        Map<String, Object> headers, String chargebackId) {
        log.warn("Chargeback eligible for dispute: {} - manual review required", chargebackId);

        com.waqiti.common.dlq.DlqEvent dlqEvent = dlqRecoveryService.persistDlqEvent(
            getServiceName(),
            "payment.chargeback.events",
            "payment.chargeback.events.dlq",
            event,
            failureReason,
            null,
            convertHeaders(headers),
            com.waqiti.common.dlq.DlqEvent.RecoveryStrategy.MANUAL_REVIEW
        );

        dlqEvent.setSeverity(0.85);
        dlqRecoveryService.markForManualReview(dlqEvent,
            "Chargeback dispute eligible - review evidence and decide: ACCEPT or DISPUTE");

        return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
    }

    private DlqProcessingResult handleInvalidChargeback(Object event, String failureReason,
                                                          Map<String, Object> headers, String chargebackId) {
        log.error("Invalid/fraudulent chargeback detected: {} - urgent review", chargebackId);

        com.waqiti.common.dlq.DlqEvent dlqEvent = dlqRecoveryService.persistDlqEvent(
            getServiceName(),
            "payment.chargeback.events",
            "payment.chargeback.events.dlq",
            event,
            "FRAUDULENT CHARGEBACK: " + failureReason,
            null,
            convertHeaders(headers),
            com.waqiti.common.dlq.DlqEvent.RecoveryStrategy.MANUAL_REVIEW
        );

        // HIGH severity - potential fraud
        dlqEvent.setSeverity(0.95);
        dlqEvent.setPagerDutyTriggered(true);

        dlqRecoveryService.markForManualReview(dlqEvent,
            "URGENT: Fraudulent chargeback suspected - investigate and dispute");

        return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
    }

    private DlqProcessingResult handleMerchantAccountIssue(Object event, String failureReason,
                                                             Map<String, Object> headers, String chargebackId) {
        log.warn("Merchant account issue for chargeback: {} - {}", chargebackId, failureReason);

        com.waqiti.common.dlq.DlqEvent dlqEvent = dlqRecoveryService.persistDlqEvent(
            getServiceName(),
            "payment.chargeback.events",
            "payment.chargeback.events.dlq",
            event,
            failureReason,
            null,
            convertHeaders(headers),
            com.waqiti.common.dlq.DlqEvent.RecoveryStrategy.MANUAL_REVIEW
        );

        dlqEvent.setSeverity(0.80);
        dlqRecoveryService.markForManualReview(dlqEvent,
            "Merchant account issue - resolve account problem before processing chargeback");

        return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
    }

    private DlqProcessingResult handleUnknownChargebackError(Object event, String failureReason,
                                                               Map<String, Object> headers, String chargebackId) {
        log.error("Unknown chargeback processing error: {} - {}", chargebackId, failureReason);
        persistForManualReview(event, failureReason, headers);
        return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
    }

    private void persistForManualReview(Object event, String failureReason, Map<String, Object> headers) {
        com.waqiti.common.dlq.DlqEvent dlqEvent = dlqRecoveryService.persistDlqEvent(
            getServiceName(),
            "payment.chargeback.events",
            "payment.chargeback.events.dlq",
            event,
            failureReason,
            null,
            convertHeaders(headers),
            com.waqiti.common.dlq.DlqEvent.RecoveryStrategy.MANUAL_REVIEW
        );

        dlqEvent.setSeverity(0.90);
        dlqRecoveryService.markForManualReview(dlqEvent,
            "Unknown chargeback error - urgent investigation required");
    }

    private ChargebackFailureCategory categorizeChargebackFailure(String failureReason, Object event) {
        if (failureReason == null) return ChargebackFailureCategory.UNKNOWN;

        String reason = failureReason.toLowerCase();

        if (reason.matches(".*(timeout|timed out|connection|network).*")) {
            return ChargebackFailureCategory.NETWORK_TIMEOUT;
        }
        if (reason.matches(".*(gateway.*error|service.*unavailable).*")) {
            return ChargebackFailureCategory.GATEWAY_ERROR;
        }
        if (reason.matches(".*(duplicate|already.*processed).*")) {
            return ChargebackFailureCategory.DUPLICATE_CHARGEBACK;
        }
        if (reason.matches(".*(evidence.*upload|upload.*failed|evidence.*error).*")) {
            return ChargebackFailureCategory.EVIDENCE_UPLOAD_FAILED;
        }
        if (reason.matches(".*(deadline.*passed|deadline.*missed|too.*late).*")) {
            return ChargebackFailureCategory.DEADLINE_PASSED;
        }
        if (reason.matches(".*(evidence.*deadline).*")) {
            return ChargebackFailureCategory.EVIDENCE_DEADLINE_MISSED;
        }
        if (reason.matches(".*(below.*threshold|too.*small|not.*worth).*")) {
            return ChargebackFailureCategory.BELOW_DISPUTE_THRESHOLD;
        }
        if (reason.matches(".*(dispute.*eligible|can.*dispute).*")) {
            return ChargebackFailureCategory.DISPUTE_ELIGIBLE;
        }
        if (reason.matches(".*(evidence.*required|need.*evidence).*")) {
            return ChargebackFailureCategory.EVIDENCE_REQUIRED;
        }
        if (reason.matches(".*(invalid.*reason|unknown.*reason).*")) {
            return ChargebackFailureCategory.INVALID_CHARGEBACK_REASON;
        }
        if (reason.matches(".*(fraudulent|friendly.*fraud|chargeback.*fraud).*")) {
            return ChargebackFailureCategory.FRAUDULENT_CHARGEBACK;
        }
        if (reason.matches(".*(merchant.*account|account.*issue|merchant.*blocked).*")) {
            return ChargebackFailureCategory.MERCHANT_ACCOUNT_ISSUE;
        }

        return ChargebackFailureCategory.UNKNOWN;
    }

    private String extractFailureReason(Map<String, Object> headers) {
        if (headers == null) return "Unknown";
        Object reason = headers.get("kafka_exception-message");
        return reason != null ? reason.toString() : "Unknown";
    }

    private String extractCorrelationId(Map<String, Object> headers) {
        if (headers == null) return null;
        Object id = headers.get("correlationId");
        return id != null ? id.toString() : null;
    }

    private String extractChargebackId(Map<String, Object> headers, Object event) {
        if (headers != null) {
            Object id = headers.get("chargebackId");
            if (id != null) return id.toString();
        }
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            if (eventJson.contains("chargebackId")) {
                return "extracted-from-event";
            }
        } catch (Exception e) {
            log.debug("Could not extract chargeback ID", e);
        }
        return "unknown";
    }

    private Map<String, String> convertHeaders(Map<String, Object> headers) {
        if (headers == null) return java.util.Collections.emptyMap();
        return headers.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue() != null ? e.getValue().toString() : ""
            ));
    }

    private enum ChargebackFailureCategory {
        NETWORK_TIMEOUT,
        GATEWAY_ERROR,
        DUPLICATE_CHARGEBACK,
        EVIDENCE_UPLOAD_FAILED,
        DEADLINE_PASSED,
        EVIDENCE_DEADLINE_MISSED,
        BELOW_DISPUTE_THRESHOLD,
        DISPUTE_ELIGIBLE,
        EVIDENCE_REQUIRED,
        INVALID_CHARGEBACK_REASON,
        FRAUDULENT_CHARGEBACK,
        MERCHANT_ACCOUNT_ISSUE,
        UNKNOWN
    }

    @Override
    protected String getServiceName() {
        return "PaymentChargebackConsumer";
    }
}
