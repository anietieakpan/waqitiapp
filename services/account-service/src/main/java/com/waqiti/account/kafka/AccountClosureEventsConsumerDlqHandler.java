package com.waqiti.account.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.waqiti.account.entity.DlqRetryRecord;
import com.waqiti.account.entity.PermanentFailureRecord;
import com.waqiti.account.kafka.dlq.BaseDlqHandler;
import com.waqiti.account.kafka.dlq.RecoveryDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * DLQ Handler for AccountClosureEventsConsumer
 *
 * <p>Handles failed account closure events with CRITICAL priority:</p>
 * <ul>
 *   <li>Account closures have financial/regulatory implications</li>
 *   <li>Database errors → RETRY (max 3 attempts)</li>
 *   <li>Balance not zero → CRITICAL manual review (regulatory requirement)</li>
 *   <li>Already closed → DISCARD (idempotent)</li>
 *   <li>External service failures → HIGH priority review</li>
 * </ul>
 *
 * <p><b>⚠️ CRITICAL:</b> Account closures may have non-zero balances requiring
 * regulatory review and customer refund processing.</p>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Service
@Slf4j
public class AccountClosureEventsConsumerDlqHandler extends BaseDlqHandler<Object> {

    private static final String HANDLER_NAME = "AccountClosureEventsConsumer";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern BALANCE_PATTERN = Pattern.compile("balance[=:\\s]+([\\d.]+)", Pattern.CASE_INSENSITIVE);

    @PostConstruct
    public void init() {
        initializeMetrics();
        log.info("[DLQ-{}] Handler initialized", HANDLER_NAME);
    }

    @KafkaListener(
        topics = "${kafka.topics.account-closure-events.dlq:account.closure.events.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq"
    )
    public void handleDlqMessage(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.OFFSET) Long offset,
            @Header(value = "kafka_receivedMessageKey", required = false) String key,
            @Headers Map<String, Object> headers,
            Acknowledgment acknowledgment) {

        processDlqMessage(event, topic, partition, offset, key, headers, acknowledgment);
    }

    @Override
    protected RecoveryDecision classifyFailure(
            Object event,
            String exceptionMessage,
            String exceptionClass,
            Integer retryAttempt,
            Map<String, Object> headers) {

        if (exceptionMessage == null) {
            return RecoveryDecision.retry("Unknown error - no exception message");
        }

        String lowerMessage = exceptionMessage.toLowerCase();

        // Pattern 1: Balance not zero - CRITICAL regulatory requirement
        if (containsAny(lowerMessage, "balance not zero", "non-zero balance",
                        "outstanding balance", "balance remaining")) {

            BigDecimal balance = extractBalance(exceptionMessage);

            return RecoveryDecision.permanentFailureWithImpact(
                PermanentFailureRecord.FailureCategory.COMPLIANCE_BLOCK,
                PermanentFailureRecord.BusinessImpact.CRITICAL,
                "Account closure attempted with non-zero balance - regulatory review required",
                "Customer refund required before account closure can complete",
                balance);
        }

        // Pattern 2: Database errors
        if (containsAny(lowerMessage, "connection", "timeout", "deadlock")) {
            return RecoveryDecision.forDatabaseError(exceptionMessage, retryAttempt);
        }

        // Pattern 3: Already closed (idempotent - safe to discard)
        if (containsAny(lowerMessage, "already closed", "account closed", "duplicate closure")) {
            return RecoveryDecision.discard("Account already closed - idempotent operation");
        }

        // Pattern 4: Pending transactions
        if (containsAny(lowerMessage, "pending transaction", "transaction in progress",
                        "settlement pending")) {
            if (retryAttempt < 2) {
                return RecoveryDecision.retry(
                    "Pending transactions - waiting for settlement: " + exceptionMessage);
            } else {
                return RecoveryDecision.criticalReview(
                    "Account closure blocked by pending transactions - manual investigation required: " + exceptionMessage);
            }
        }

        // Pattern 5: External service failures (refund-service, notification-service)
        if (containsAny(lowerMessage, "refund service", "notification service",
                        "service unavailable", "503", "feign")) {
            if (retryAttempt < 3) {
                return RecoveryDecision.retry("External service unavailable: " + exceptionMessage);
            } else {
                return RecoveryDecision.highPriorityReview(
                    "External service failure during closure - account may be in inconsistent state: " + exceptionMessage);
            }
        }

        // Pattern 6: Linked accounts exist
        if (containsAny(lowerMessage, "linked account", "associated account", "child account")) {
            return RecoveryDecision.permanentFailure(
                PermanentFailureRecord.FailureCategory.BUSINESS_RULE_VIOLATION,
                PermanentFailureRecord.BusinessImpact.HIGH,
                "Cannot close account with linked accounts: " + exceptionMessage);
        }

        // Pattern 7: Data corruption
        if (containsAny(lowerMessage, "deserialization", "json", "parse")) {
            return RecoveryDecision.forCorruptedData(exceptionMessage);
        }

        // Default: High priority review (closures are critical)
        if (retryAttempt < 3) {
            return RecoveryDecision.retry("Unknown error during account closure: " + exceptionMessage);
        } else {
            return RecoveryDecision.highPriorityReview(
                "Account closure failed after max retries - manual investigation required: " + exceptionMessage);
        }
    }

    @Override
    protected boolean attemptRecovery(DlqRetryRecord retryRecord) {
        try {
            log.info("[DLQ-{}] Attempting recovery - recordId={}", HANDLER_NAME, retryRecord.getId());

            // TODO: Implement recovery logic
            // 1. Deserialize account closure event
            // 2. Verify account state and balance
            // 3. Check for pending transactions
            // 4. Retry closure process
            // 5. Send closure confirmation

            log.warn("[DLQ-{}] Recovery logic not yet implemented", HANDLER_NAME);
            return false;

        } catch (Exception e) {
            log.error("[DLQ-{}] Recovery failed", HANDLER_NAME, e);
            return false;
        }
    }

    @Override
    protected String maskPii(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.isObject()) {
                ObjectNode node = (ObjectNode) root;
                maskField(node, "email", "*****@*****.***");
                maskField(node, "phoneNumber", "***-***-****");
                maskField(node, "customerId", "MASKED");
                maskField(node, "accountNumber", "****" + extractLast4(node, "accountNumber"));

                // Mask nested customer data
                if (node.has("customer")) {
                    JsonNode customer = node.get("customer");
                    if (customer.isObject()) {
                        ObjectNode customerNode = (ObjectNode) customer;
                        maskField(customerNode, "email", "*****@*****.***");
                        maskField(customerNode, "ssn", "***-**-****");
                    }
                }

                return objectMapper.writeValueAsString(node);
            }
            return applyRegexMasking(payload);
        } catch (Exception e) {
            log.error("[DLQ-{}] PII masking failed, applying regex", HANDLER_NAME, e);
            return applyRegexMasking(payload);
        }
    }

    @Override
    protected String getHandlerName() {
        return HANDLER_NAME;
    }

    // ========== HELPER METHODS ==========

    private void maskField(ObjectNode node, String field, String mask) {
        if (node.has(field)) {
            node.put(field, mask);
        }
    }

    private String extractLast4(ObjectNode node, String field) {
        if (node.has(field)) {
            String value = node.get(field).asText();
            if (value.length() >= 4) {
                return value.substring(value.length() - 4);
            }
        }
        return "****";
    }

    private String applyRegexMasking(String payload) {
        String masked = payload;
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("*****@*****.***");
        return masked;
    }

    private boolean containsAny(String text, String... substrings) {
        for (String substring : substrings) {
            if (text.contains(substring)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal extractBalance(String message) {
        try {
            var matcher = BALANCE_PATTERN.matcher(message);
            if (matcher.find()) {
                return new BigDecimal(matcher.group(1));
            }
        } catch (Exception e) {
            log.debug("Could not extract balance from message", e);
        }
        return BigDecimal.ZERO;
    }
}
