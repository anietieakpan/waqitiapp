package com.waqiti.account.kafka;

import com.fasterxml.jackson.databind.JsonNode;
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

@Service
@Slf4j
public class AccountUpdatedEventsConsumerDlqHandler extends BaseDlqHandler<Object> {
    private static final String HANDLER_NAME = "AccountUpdatedEventsConsumer";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    @PostConstruct
    public void init() {
        initializeMetrics();
        log.info("[DLQ-{}] Handler initialized", HANDLER_NAME);
    }

    @KafkaListener(topics = "${kafka.topics.account-updated-events.dlq:account.updated.events.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq")
    public void handleDlqMessage(@Payload Object event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition, @Header(KafkaHeaders.OFFSET) Long offset,
            @Header(value = "kafka_receivedMessageKey", required = false) String key, @Headers Map<String, Object> headers,
            Acknowledgment acknowledgment) {
        processDlqMessage(event, topic, partition, offset, key, headers, acknowledgment);
    }

    @Override
    protected RecoveryDecision classifyFailure(Object event, String exceptionMessage, String exceptionClass,
            Integer retryAttempt, Map<String, Object> headers) {
        if (exceptionMessage == null) return RecoveryDecision.retry("Unknown error");
        String lowerMessage = exceptionMessage.toLowerCase();
        if (containsAny(lowerMessage, "connection", "timeout", "deadlock")) return RecoveryDecision.forDatabaseError(exceptionMessage, retryAttempt);
        if (containsAny(lowerMessage, "duplicate", "already updated")) return RecoveryDecision.discard("Duplicate update");
        if (containsAny(lowerMessage, "deserialization", "json")) return RecoveryDecision.forCorruptedData(exceptionMessage);
        if (containsAny(lowerMessage, "validation", "invalid")) return RecoveryDecision.forDataValidationError(exceptionMessage);
        return retryAttempt < 3 ? RecoveryDecision.retry(exceptionMessage) : RecoveryDecision.highPriorityReview("Max retries: " + exceptionMessage);
    }

    @Override
    protected boolean attemptRecovery(DlqRetryRecord retryRecord) {
        log.warn("[DLQ-{}] Recovery not implemented", HANDLER_NAME);
        return false;
    }

    @Override
    protected String maskPii(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.isObject()) {
                ObjectNode node = (ObjectNode) root;
                maskField(node, "email", "*****@*****.***");
                maskField(node, "phone", "***-***-****");
                return objectMapper.writeValueAsString(node);
            }
        } catch (Exception e) { log.error("[DLQ-{}] PII masking failed", HANDLER_NAME, e); }
        return EMAIL_PATTERN.matcher(payload).replaceAll("*****@*****.***");
    }

    @Override
    protected String getHandlerName() { return HANDLER_NAME; }
    private void maskField(ObjectNode node, String field, String mask) { if (node.has(field)) node.put(field, mask); }
    private boolean containsAny(String text, String... substrings) {
        for (String s : substrings) if (text.contains(s)) return true;
        return false;
    }
}
