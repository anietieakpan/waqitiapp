package com.waqiti.crypto.kafka;

import com.waqiti.common.kafka.BaseDlqConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DLQ Handler for CentralBankDigitalCurrencyEventConsumer
 *
 * Handles failed messages from the dead letter topic for CBDC (Central Bank Digital Currency) events
 * (e.g., Digital Yuan, Digital Euro, Digital Dollar transactions)
 *
 * CRITICAL: CBDC events involve government-backed currencies and may have regulatory reporting requirements
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CentralBankDigitalCurrencyEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public CentralBankDigitalCurrencyEventConsumerDlqHandler(
            MeterRegistry meterRegistry,
            KafkaTemplate<String, Object> kafkaTemplate) {
        super(meterRegistry);
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CentralBankDigitalCurrencyEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CentralBankDigitalCurrencyEventConsumer.dlq:CentralBankDigitalCurrencyEventConsumer.dlq}",
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
            log.warn("Processing CBDC DLQ event (GOVERNMENT CURRENCY - HIGH COMPLIANCE): {}", event);

            Integer retryCount = (Integer) headers.getOrDefault("retry_count", 0);
            String failureReason = (String) headers.get("failure_reason");
            String transactionId = (String) headers.get("transactionId");
            String cbdcType = (String) headers.get("cbdcType"); // e.g., "eYuan", "DigitalEuro"
            String amount = (String) headers.get("amount");
            String userId = (String) headers.get("userId");

            log.warn("CBDC DLQ - TxID: {}, Type: {}, Amount: {}, User: {}, Retry: {}, Failure: {}",
                    transactionId, cbdcType, amount, userId, retryCount, failureReason);

            DlqProcessingResult result = classifyAndRecover(event, failureReason, retryCount, headers);
            updateRecoveryMetrics(result);

            return result;

        } catch (Exception e) {
            log.error("CRITICAL: Error handling CBDC DLQ event", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Classify error and determine recovery strategy for CBDC events
     * CBDC is government currency - failures may have regulatory implications
     */
    private DlqProcessingResult classifyAndRecover(Object event, String failureReason,
                                                     Integer retryCount, Map<String, Object> headers) {
        final int MAX_RETRIES = 8; // Higher for government currencies

        if (retryCount >= MAX_RETRIES) {
            log.error("REGULATORY: Max retries ({}) exceeded for CBDC event. ESCALATION REQUIRED", MAX_RETRIES);
            createCBDCEscalation(event, failureReason, retryCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        if (failureReason == null) {
            return retryWithBackoff(event, retryCount, headers);
        }

        // Central bank API connectivity errors - retry
        if (isCentralBankAPIError(failureReason)) {
            log.warn("Central bank API error for CBDC: {}. Retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Settlement/clearing errors - CRITICAL (affects finality)
        if (isSettlementError(failureReason)) {
            log.error("CRITICAL: CBDC settlement error: {}. Escalation required", failureReason);
            createCBDCEscalation(event, failureReason, retryCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        // Programmable money/smart contract errors - may need investigation
        if (isProgrammableMoneyError(failureReason)) {
            log.warn("CBDC programmable money error: {}. Retry with investigation", failureReason);
            return retryWithBackoff(event, retryCount, headers);
        }

        // KYC/identity verification errors - retry
        if (isIdentityError(failureReason)) {
            log.warn("CBDC identity verification error: {}. Retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Cross-border CBDC errors - may involve multiple central banks
        if (isCrossBorderError(failureReason)) {
            log.error("Cross-border CBDC error: {}. Extended retry with escalation", failureReason);
            return retryWithExtendedBackoff(event, retryCount, headers);
        }

        // Database errors - retry
        if (isDatabaseError(failureReason)) {
            log.warn("Database error for CBDC event: {}. Retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Duplicate transaction
        if (isDuplicateError(failureReason)) {
            log.info("Duplicate CBDC event detected: {}. Discarding", failureReason);
            return DlqProcessingResult.DISCARDED;
        }

        // Offline/offline-first transaction errors
        if (isOfflineTransactionError(failureReason)) {
            log.warn("CBDC offline transaction sync error: {}. Retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Privacy/anonymity tier errors
        if (isPrivacyTierError(failureReason)) {
            log.warn("CBDC privacy tier error: {}. May require manual review", failureReason);
            createCBDCEscalation(event, failureReason, retryCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        // Interoperability errors with other payment systems
        if (isInteroperabilityError(failureReason)) {
            log.warn("CBDC interoperability error: {}. Retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Unknown error - retry with logging
        log.warn("Unknown CBDC error: {}. Conservative retry #{}", failureReason, retryCount + 1);
        return retryWithBackoff(event, retryCount, headers);
    }

    private boolean isCentralBankAPIError(String failureReason) {
        return failureReason.contains("central bank") ||
               failureReason.contains("CBDC API") ||
               failureReason.contains("monetary authority") ||
               failureReason.contains("reserve bank");
    }

    private boolean isSettlementError(String failureReason) {
        return failureReason.contains("settlement") ||
               failureReason.contains("clearing") ||
               failureReason.contains("finality") ||
               failureReason.contains("ledger update");
    }

    private boolean isProgrammableMoneyError(String failureReason) {
        return failureReason.contains("programmable") ||
               failureReason.contains("smart contract") ||
               failureReason.contains("conditional") ||
               failureReason.contains("expiry");
    }

    private boolean isIdentityError(String failureReason) {
        return failureReason.contains("identity") ||
               failureReason.contains("KYC") ||
               failureReason.contains("verification") ||
               failureReason.contains("wallet registration");
    }

    private boolean isCrossBorderError(String failureReason) {
        return failureReason.contains("cross-border") ||
               failureReason.contains("foreign") ||
               failureReason.contains("mBridge") ||
               failureReason.contains("international");
    }

    private boolean isDatabaseError(String failureReason) {
        return failureReason.contains("database") ||
               failureReason.contains("connection") ||
               failureReason.contains("SQL");
    }

    private boolean isDuplicateError(String failureReason) {
        return failureReason.contains("duplicate") ||
               failureReason.contains("already processed");
    }

    private boolean isOfflineTransactionError(String failureReason) {
        return failureReason.contains("offline") ||
               failureReason.contains("sync") ||
               failureReason.contains("reconciliation");
    }

    private boolean isPrivacyTierError(String failureReason) {
        return failureReason.contains("privacy") ||
               failureReason.contains("anonymity") ||
               failureReason.contains("tier") ||
               failureReason.contains("spending limit");
    }

    private boolean isInteroperabilityError(String failureReason) {
        return failureReason.contains("interoperability") ||
               failureReason.contains("bridge") ||
               failureReason.contains("legacy system") ||
               failureReason.contains("payment rail");
    }

    private DlqProcessingResult retryWithBackoff(Object event, Integer retryCount, Map<String, Object> headers) {
        try {
            long delaySeconds = (long) Math.pow(2, retryCount) * 60;
            log.info("Scheduling CBDC event retry #{} with {}s delay", retryCount + 1, delaySeconds);

            headers.put("retry_count", retryCount + 1);
            headers.put("retry_scheduled_at", LocalDateTime.now());
            headers.put("retry_delay_seconds", delaySeconds);

            // In production: kafkaTemplate.send("cbdc-event-retry", event);
            return DlqProcessingResult.RETRY_SCHEDULED;

        } catch (Exception e) {
            log.error("Failed to schedule CBDC event retry - escalating", e);
            createCBDCEscalation(event, "Retry scheduling failed: " + e.getMessage(), retryCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    private DlqProcessingResult retryWithExtendedBackoff(Object event, Integer retryCount, Map<String, Object> headers) {
        try {
            // Extended backoff for cross-border/complex CBDC operations (5min, 10min, 20min...)
            long delaySeconds = (long) Math.pow(2, retryCount) * 300;
            log.info("Scheduling CBDC event retry with extended backoff #{} with {}s delay", retryCount + 1, delaySeconds);

            headers.put("retry_count", retryCount + 1);
            headers.put("retry_scheduled_at", LocalDateTime.now());
            headers.put("retry_delay_seconds", delaySeconds);
            headers.put("extended_backoff", true);

            // In production: kafkaTemplate.send("cbdc-event-retry", event);
            return DlqProcessingResult.RETRY_SCHEDULED;

        } catch (Exception e) {
            log.error("Failed to schedule CBDC event retry with extended backoff", e);
            createCBDCEscalation(event, "Extended retry scheduling failed: " + e.getMessage(), retryCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Create CBDC escalation for government currency issues
     */
    private void createCBDCEscalation(Object event, String failureReason, Integer retryCount) {
        try {
            log.error("CBDC ESCALATION: Creating review task for government digital currency failure. " +
                      "Failure: {}, Retries: {}", failureReason, retryCount);

            // In production:
            // 1. Create high-priority ticket for CBDC operations team
            // 2. Notify treasury/payments team
            // 3. Log to regulatory audit trail
            // 4. Consider reporting to central bank if required

            sendCBDCAlert(event, failureReason, retryCount);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to create CBDC escalation", e);
        }
    }

    private void sendCBDCAlert(Object event, String failureReason, Integer retryCount) {
        log.error("CBDC ALERT [GOVERNMENT CURRENCY]: CBDC DLQ event requires intervention. " +
                  "Event: {}, Failure: {}, Retries: {}", event, failureReason, retryCount);

        // In production, integrate with:
        // - Treasury operations dashboard
        // - Regulatory compliance team notifications
        // - Central bank liaison alerts (if applicable)
    }

    private void updateRecoveryMetrics(DlqProcessingResult result) {
        try {
            log.debug("Updating CBDC DLQ recovery metrics: {}", result.name().toLowerCase());
            // Metrics are automatically updated by BaseDlqConsumer
        } catch (Exception e) {
            log.warn("Failed to update CBDC recovery metrics", e);
        }
    }

    @Override
    protected String getServiceName() {
        return "CentralBankDigitalCurrencyEventConsumer";
    }
}
