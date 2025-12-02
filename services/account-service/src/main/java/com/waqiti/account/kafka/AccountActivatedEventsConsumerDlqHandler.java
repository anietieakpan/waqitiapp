package com.waqiti.account.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.waqiti.account.entity.DlqRetryRecord;
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
import java.util.Map;
import java.util.regex.Pattern;

/**
 * DLQ Handler for AccountActivatedEventsConsumer
 *
 * <p>Handles failed account activation events with intelligent recovery:</p>
 * <ul>
 *   <li>Database errors → RETRY (max 3 attempts)</li>
 *   <li>External service failures → RETRY (max 3 attempts)</li>
 *   <li>Duplicate activations → DISCARD (idempotent)</li>
 *   <li>Invalid state (already active) → PERMANENT_FAILURE</li>
 *   <li>Notification failures → RETRY then HIGH priority review</li>
 * </ul>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Service
@Slf4j
public class AccountActivatedEventsConsumerDlqHandler extends BaseDlqHandler<Object> {

    private static final String HANDLER_NAME = "AccountActivatedEventsConsumer";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?\\d{10,15}");

    @PostConstruct
    public void init() {
        initializeMetrics();
        log.info("[DLQ-{}] Handler initialized", HANDLER_NAME);
    }

    @KafkaListener(
        topics = "${kafka.topics.account-activated-events.dlq:account.activated.events.dlq}",
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

        // Pattern 1: Database errors
        if (containsAny(lowerMessage, "connection", "timeout", "deadlock", "constraint")) {
            return RecoveryDecision.forDatabaseError(exceptionMessage, retryAttempt);
        }

        // Pattern 2: External service failures (notification-service)
        if (containsAny(lowerMessage, "notification", "service unavailable", "503", "feign")) {
            if (retryAttempt < 2) {
                return RecoveryDecision.retry("Notification service unavailable: " + exceptionMessage);
            } else {
                return RecoveryDecision.highPriorityReview(
                    "Notification service failure - account activated but user not notified: " + exceptionMessage);
            }
        }

        // Pattern 3: Duplicate activation (safe to discard)
        if (containsAny(lowerMessage, "already activated", "already active", "duplicate")) {
            return RecoveryDecision.discard("Duplicate activation - account already active");
        }

        // Pattern 4: Invalid state
        if (containsAny(lowerMessage, "invalid state", "cannot activate", "closed account")) {
            return RecoveryDecision.forInvalidState(exceptionMessage);
        }

        // Pattern 5: Data corruption
        if (containsAny(lowerMessage, "deserialization", "json", "parse")) {
            return RecoveryDecision.forCorruptedData(exceptionMessage);
        }

        // Default: retry with backoff
        if (retryAttempt < 3) {
            return RecoveryDecision.retry("Unknown error: " + exceptionMessage);
        } else {
            return RecoveryDecision.highPriorityReview(
                "Max retries exceeded: " + exceptionMessage);
        }
    }

    @Override
    protected boolean attemptRecovery(DlqRetryRecord retryRecord) {
        try {
            log.info("[DLQ-{}] Attempting recovery - recordId={}", HANDLER_NAME, retryRecord.getId());

            // TODO: Implement recovery logic
            // 1. Deserialize account activation event
            // 2. Verify account state
            // 3. Re-publish activation event if needed
            // 4. Send notification

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
                maskField(node, "phone", "***-***-****");
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

    private void maskField(ObjectNode node, String field, String mask) {
        if (node.has(field)) {
            node.put(field, mask);
        }
    }

    private String applyRegexMasking(String payload) {
        String masked = payload;
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("*****@*****.***");
        masked = PHONE_PATTERN.matcher(masked).replaceAll("***-***-****");
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
}
