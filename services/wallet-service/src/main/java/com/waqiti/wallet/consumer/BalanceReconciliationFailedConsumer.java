package com.waqiti.wallet.consumer;

import com.waqiti.common.alerting.UnifiedAlertingService;
import com.waqiti.common.events.BalanceReconciliationFailedEvent;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.wallet.exception.ReconciliationException;
import com.waqiti.wallet.service.ReconciliationService;
import com.waqiti.wallet.service.WalletFreezeService;
import com.waqiti.wallet.service.NotificationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Production-grade consumer for balance reconciliation failure events.
 *
 * This consumer handles CRITICAL financial integrity issues when wallet balances
 * don't match expected values. This is a P0 scenario requiring immediate action.
 *
 * CRITICAL ACTIONS TAKEN:
 * 1. IMMEDIATE wallet freeze (prevent further corruption)
 * 2. P0 incident creation (CFO/CTO notification)
 * 3. PagerDuty alert (24/7 on-call)
 * 4. Slack critical channel notification
 * 5. Customer notification (generic message)
 * 6. Regulatory compliance logging
 *
 * REGULATORY COMPLIANCE:
 * - SOX Section 404 (Internal Controls)
 * - PCI-DSS Requirement 10.2 (Audit Trail)
 * - FFIEC IT Examination Handbook (Reconciliation)
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BalanceReconciliationFailedConsumer {

    private final IdempotencyService idempotencyService;
    private final ReconciliationService reconciliationService;
    private final UnifiedAlertingService alertingService;
    private final WalletFreezeService freezeService;
    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;

    private static final String METRIC_PREFIX = "wallet.reconciliation.failed.consumer";
    private static final Duration FREEZE_DURATION = Duration.ofDays(30); // 30-day freeze for manual review

    /**
     * Handles balance reconciliation failure events.
     *
     * This is a CRITICAL event that indicates financial data corruption.
     * Immediate action is required to prevent further issues.
     *
     * Idempotency Key: "reconciliation-failed:{walletId}:{timestamp}"
     * TTL: 30 days (regulatory requirement)
     *
     * @param event Balance reconciliation failure event
     * @param acknowledgment Kafka manual acknowledgment
     */
    @KafkaListener(
        topics = "${kafka.topics.balance-reconciliation-failed:balance-reconciliation-failed}",
        groupId = "${kafka.consumer-groups.reconciliation-failed:reconciliation-failed-group}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "${kafka.consumers.reconciliation-failed.concurrency:1}" // Single thread for critical events
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 60, rollbackFor = Exception.class)
    public void handleReconciliationFailure(
            @Payload BalanceReconciliationFailedEvent event,
            Acknowledgment acknowledgment) {

        Timer.Sample timer = Timer.start(meterRegistry);
        String idempotencyKey = String.format("reconciliation-failed:%s:%s",
                event.getWalletId(), event.getTimestamp());
        UUID operationId = UUID.randomUUID();

        log.error("🚨🚨🚨 CRITICAL: Balance reconciliation FAILED for wallet: walletId={}, " +
                "expected={}, actual={}, difference={}, customerId={}",
                event.getWalletId(), event.getExpectedBalance(), event.getActualBalance(),
                event.getDifference(), event.getCustomerId());

        try {
            // Check idempotency
            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(30))) {
                log.warn("⚠️ DUPLICATE - Reconciliation failure already processed: walletId={}",
                        event.getWalletId());
                recordMetric("duplicate", event);
                acknowledgment.acknowledge();
                return;
            }

            // Validate event
            validateEvent(event);

            // Process the reconciliation failure
            processReconciliationFailure(event, operationId);

            // Mark operation complete
            idempotencyService.completeOperation(
                idempotencyKey,
                operationId,
                Map.of(
                    "status", "PROCESSED",
                    "walletId", event.getWalletId().toString(),
                    "difference", event.getDifference().toString(),
                    "frozen", "true",
                    "incidentCreated", "true"
                ),
                Duration.ofDays(30)
            );

            acknowledgment.acknowledge();
            recordMetric("success", event);
            timer.stop(meterRegistry.timer(METRIC_PREFIX + ".processing.time", "result", "success"));

            log.info("✅ Reconciliation failure processed and wallet frozen: walletId={}",
                    event.getWalletId());

        } catch (Exception e) {
            log.error("❌ CRITICAL: Failed to handle reconciliation failure event: walletId={}, error={}",
                    event.getWalletId(), e.getMessage(), e);

            idempotencyService.failOperation(idempotencyKey, operationId, e.getMessage());
            recordMetric("failure", event);
            timer.stop(meterRegistry.timer(METRIC_PREFIX + ".processing.time", "result", "failure"));

            // DO NOT acknowledge - needs DLQ processing
            throw new ReconciliationException(
                "Failed to process reconciliation failure event", e);
        }
    }

    /**
     * Core processing logic for reconciliation failures.
     *
     * This executes the critical path for financial integrity issues:
     * 1. FREEZE wallet immediately
     * 2. Create P0 incident ticket
     * 3. Alert financial operations team
     * 4. Notify compliance team
     * 5. Send customer notification
     * 6. Create audit trail
     */
    private void processReconciliationFailure(
            BalanceReconciliationFailedEvent event,
            UUID operationId) {

        BigDecimal difference = event.getDifference();
        boolean isSignificant = difference.abs().compareTo(new BigDecimal("100.00")) > 0;

        log.info("Processing reconciliation failure: walletId={}, isSignificant={}, difference={}",
                event.getWalletId(), isSignificant, difference);

        // STEP 1: IMMEDIATE WALLET FREEZE
        log.info("Step 1/6: Freezing wallet immediately: walletId={}", event.getWalletId());

        String freezeReason = String.format(
            "Balance reconciliation failed. Expected: %s, Actual: %s, Difference: %s. " +
            "Wallet frozen for manual review per SOX 404 compliance.",
            event.getExpectedBalance(),
            event.getActualBalance(),
            difference
        );

        UUID freezeId = freezeService.freezeWallet(
            event.getWalletId(),
            "RECONCILIATION_FAILURE",
            freezeReason,
            FREEZE_DURATION
        );

        log.info("✅ Wallet frozen successfully: walletId={}, freezeId={}", event.getWalletId(), freezeId);

        // STEP 2: CREATE P0 INCIDENT
        log.info("Step 2/6: Creating P0 incident: walletId={}", event.getWalletId());

        String incidentId = reconciliationService.createReconciliationIncident(
            event.getWalletId(),
            event.getCustomerId(),
            event.getExpectedBalance(),
            event.getActualBalance(),
            difference,
            event.getReconciliationType(),
            event.getLastSuccessfulReconciliation()
        );

        log.info("✅ P0 incident created: incidentId={}, walletId={}", incidentId, event.getWalletId());

        // STEP 3: SEND CRITICAL ALERTS
        log.info("Step 3/6: Sending critical alerts: walletId={}", event.getWalletId());

        Map<String, Object> alertContext = Map.of(
            "walletId", event.getWalletId().toString(),
            "customerId", event.getCustomerId().toString(),
            "expectedBalance", event.getExpectedBalance().toString(),
            "actualBalance", event.getActualBalance().toString(),
            "difference", difference.toString(),
            "differenceAbs", difference.abs().toString(),
            "reconciliationType", event.getReconciliationType(),
            "incidentId", incidentId,
            "freezeId", freezeId.toString(),
            "severity", isSignificant ? "CRITICAL" : "HIGH"
        );

        // PagerDuty alert (triggers on-call)
        alertingService.sendPagerDutyAlert(
            isSignificant ? "critical" : "error",
            "BALANCE_RECONCILIATION_FAILURE",
            "Balance reconciliation failed for wallet: " + event.getWalletId() +
                " | Difference: " + difference + " | Incident: " + incidentId,
            alertContext
        );

        // Slack alert to multiple channels
        alertingService.sendSlackAlert(
            "critical", // #waqiti-critical channel
            "🚨🚨🚨 BALANCE RECONCILIATION FAILURE",
            buildSlackMessage(event, incidentId, freezeId, isSignificant),
            alertContext
        );

        // Finance team alert
        alertingService.sendSlackAlert(
            "finance",
            "💰 Balance Discrepancy Alert",
            buildFinanceTeamMessage(event, incidentId),
            alertContext
        );

        log.info("✅ Critical alerts sent: walletId={}", event.getWalletId());

        // STEP 4: NOTIFY COMPLIANCE TEAM
        log.info("Step 4/6: Notifying compliance team: walletId={}", event.getWalletId());

        reconciliationService.notifyComplianceTeam(
            event.getWalletId(),
            incidentId,
            difference,
            "Balance reconciliation failure requires regulatory review"
        );

        // STEP 5: NOTIFY CUSTOMER (GENERIC MESSAGE)
        log.info("Step 5/6: Sending customer notification: customerId={}", event.getCustomerId());

        notificationService.sendNotification(
            event.getCustomerId(),
            "Account Under Review",
            "We're reviewing your account for accuracy. " +
                "Your account is temporarily unavailable. " +
                "Our team will contact you within 24 hours.",
            Map.of(
                "type", "ACCOUNT_FREEZE",
                "reason", "ROUTINE_REVIEW", // Don't reveal actual reason to customer
                "supportUrl", "https://support.example.com/account-review"
            )
        );

        // STEP 6: CREATE AUDIT TRAIL
        log.info("Step 6/6: Creating audit trail: walletId={}", event.getWalletId());

        reconciliationService.createAuditTrail(
            event.getWalletId(),
            event.getCustomerId(),
            "RECONCILIATION_FAILURE_PROCESSED",
            Map.of(
                "incidentId", incidentId,
                "freezeId", freezeId.toString(),
                "difference", difference.toString(),
                "operationId", operationId.toString(),
                "timestamp", event.getTimestamp().toString()
            )
        );

        log.info("✅ All reconciliation failure steps completed: walletId={}, incidentId={}",
                event.getWalletId(), incidentId);
    }

    /**
     * Builds detailed Slack message for engineering team.
     */
    private String buildSlackMessage(
            BalanceReconciliationFailedEvent event,
            String incidentId,
            UUID freezeId,
            boolean isSignificant) {

        return String.format("""
            *BALANCE RECONCILIATION FAILURE* %s

            *Wallet ID:* `%s`
            *Customer ID:* `%s`
            *Incident ID:* `%s`

            *Financial Details:*
            • Expected Balance: `%s`
            • Actual Balance: `%s`
            • **Difference: `%s`** ⚠️

            *Actions Taken:*
            ✅ Wallet frozen (Freeze ID: `%s`)
            ✅ P0 incident created
            ✅ On-call engineer paged
            ✅ Customer notified (generic message)

            *Next Steps:*
            1. Review transaction history
            2. Investigate balance calculation logic
            3. Manual reconciliation required
            4. CFO approval needed to unfreeze

            *Severity:* %s
            *Reconciliation Type:* %s
            """,
            isSignificant ? "🔴" : "🟡",
            event.getWalletId(),
            event.getCustomerId(),
            incidentId,
            event.getExpectedBalance(),
            event.getActualBalance(),
            event.getDifference(),
            freezeId,
            isSignificant ? "CRITICAL (>$100)" : "HIGH",
            event.getReconciliationType()
        );
    }

    /**
     * Builds message for finance team.
     */
    private String buildFinanceTeamMessage(
            BalanceReconciliationFailedEvent event,
            String incidentId) {

        return String.format("""
            *Balance Discrepancy Detected*

            A balance reconciliation has failed and requires manual review.

            *Incident:* %s
            *Wallet:* %s
            *Difference:* %s

            Please review the incident and coordinate with engineering for resolution.
            """,
            incidentId,
            event.getWalletId(),
            event.getDifference()
        );
    }

    /**
     * Validates the reconciliation failure event.
     */
    private void validateEvent(BalanceReconciliationFailedEvent event) {
        if (event.getWalletId() == null) {
            throw new IllegalArgumentException("Wallet ID cannot be null");
        }
        if (event.getCustomerId() == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }
        if (event.getExpectedBalance() == null) {
            throw new IllegalArgumentException("Expected balance cannot be null");
        }
        if (event.getActualBalance() == null) {
            throw new IllegalArgumentException("Actual balance cannot be null");
        }
        if (event.getDifference() == null) {
            throw new IllegalArgumentException("Difference cannot be null");
        }
        if (event.getReconciliationType() == null || event.getReconciliationType().trim().isEmpty()) {
            throw new IllegalArgumentException("Reconciliation type cannot be null or empty");
        }
    }

    /**
     * Records Prometheus metrics.
     */
    private void recordMetric(String result, BalanceReconciliationFailedEvent event) {
        Counter.builder(METRIC_PREFIX + ".processed")
            .tag("result", result)
            .tag("type", event.getReconciliationType())
            .tag("significant", String.valueOf(
                event.getDifference().abs().compareTo(new BigDecimal("100.00")) > 0))
            .description("Balance reconciliation failure events processed")
            .register(meterRegistry)
            .increment();
    }
}
