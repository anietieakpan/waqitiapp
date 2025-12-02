package com.waqiti.account.kafka.dlq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.waqiti.account.entity.DlqRetryRecord;
import com.waqiti.account.entity.ManualReviewRecord;
import com.waqiti.account.entity.PermanentFailureRecord;
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
 * DLQ Handler for AccountCreatedEventsConsumer
 *
 * <p>Handles failed account creation events with intelligent recovery:</p>
 * <ul>
 *   <li>Database errors → RETRY (max 3 attempts)</li>
 *   <li>External service failures → RETRY (max 3 attempts)</li>
 *   <li>KYC/Compliance failures → RETRY once, then CRITICAL manual review</li>
 *   <li>Validation errors → PERMANENT_FAILURE</li>
 *   <li>Duplicate accounts → DISCARD</li>
 *   <li>Data corruption → PERMANENT_FAILURE</li>
 * </ul>
 *
 * <p><b>Critical Priority (15min SLA):</b> First account creation failures
 * have CRITICAL impact - customer is waiting for account activation.</p>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Service
@Slf4j
public class AccountCreatedEventsConsumerDlqHandler extends BaseDlqHandler<Object> {

    private static final String HANDLER_NAME = "AccountCreatedEventsConsumer";

    // PII field patterns for masking
    private static final Pattern SSN_PATTERN = Pattern.compile("\\d{3}-\\d{2}-\\d{4}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?\\d{10,15}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    @PostConstruct
    public void init() {
        initializeMetrics();
        log.info("[DLQ-{}] Handler initialized", HANDLER_NAME);
    }

    @KafkaListener(
        topics = "${kafka.topics.account-created-events.dlq:account.created.events.dlq}",
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

        // Pattern 1: Database connectivity/transient errors
        if (containsAny(lowerMessage, "connection", "timeout", "deadlock", "lock",
                        "optimistic", "pessimistic", "constraint violation")) {
            return RecoveryDecision.forDatabaseError(exceptionMessage, retryAttempt);
        }

        // Pattern 2: External service failures (user-service, notification-service)
        if (containsAny(lowerMessage, "service unavailable", "503", "feign",
                        "circuit", "hystrix", "timeout", "read timed out")) {
            String serviceName = extractServiceName(exceptionMessage);
            return RecoveryDecision.forExternalServiceError(
                serviceName, exceptionMessage, retryAttempt);
        }

        // Pattern 3: KYC/Compliance failures (CRITICAL - customer waiting)
        if (containsAny(lowerMessage, "kyc", "compliance", "sanctioned",
                        "pep", "aml", "cip", "cdd")) {
            if (retryAttempt < 2) {
                return RecoveryDecision.retry(
                    "Compliance check failure - retrying: " + exceptionMessage);
            } else {
                // CRITICAL priority - first account creation
                return RecoveryDecision.criticalReview(
                    "Compliance check failed after retries - customer waiting for account: " + exceptionMessage);
            }
        }

        // Pattern 4: Duplicate account (idempotency - safe to discard)
        if (containsAny(lowerMessage, "duplicate", "already exists", "unique constraint",
                        "duplicate key value", "duplicate entry")) {
            return RecoveryDecision.discard("Duplicate account creation - idempotency violation");
        }

        // Pattern 5: Validation/business rule failures
        if (containsAny(lowerMessage, "validation", "invalid", "illegal argument",
                        "max accounts exceeded", "age restriction", "unsupported country")) {
            return RecoveryDecision.permanentFailure(
                PermanentFailureRecord.FailureCategory.BUSINESS_RULE_VIOLATION,
                PermanentFailureRecord.BusinessImpact.MEDIUM,
                "Business rule validation failed: " + exceptionMessage);
        }

        // Pattern 6: Data validation errors
        if (containsAny(lowerMessage, "email format", "phone format", "invalid ssn",
                        "invalid date", "missing required field", "null pointer")) {
            return RecoveryDecision.forDataValidationError(exceptionMessage);
        }

        // Pattern 7: Deserialization/corruption errors
        if (containsAny(lowerMessage, "deserialization", "json", "parse",
                        "corrupt", "malformed", "unrecognized field")) {
            return RecoveryDecision.forCorruptedData(exceptionMessage);
        }

        // Pattern 8: Resource not found (user profile, tier config, etc.)
        if (containsAny(lowerMessage, "not found", "does not exist", "no such")) {
            String resourceType = extractResourceType(exceptionMessage);
            String resourceId = extractResourceId(exceptionMessage);
            return RecoveryDecision.forResourceNotFound(resourceType, resourceId);
        }

        // Pattern 9: Invalid state (account already active, etc.)
        if (containsAny(lowerMessage, "invalid state", "state transition",
                        "already active", "already closed")) {
            return RecoveryDecision.forInvalidState(exceptionMessage);
        }

        // Default: Unknown error - retry with standard backoff
        if (retryAttempt < MAX_RETRY_ATTEMPTS) {
            return RecoveryDecision.retry("Unknown error pattern: " + exceptionMessage);
        } else {
            // Max retries exceeded - manual review required
            return RecoveryDecision.highPriorityReview(
                "Max retries exceeded - unknown error: " + exceptionMessage);
        }
    }

    @Override
    protected boolean attemptRecovery(DlqRetryRecord retryRecord) {
        try {
            log.info("[DLQ-{}] Attempting recovery - attempt={}/{}, recordId={}",
                HANDLER_NAME, retryRecord.getRetryAttempt(), retryRecord.getMaxRetryAttempts(),
                retryRecord.getId());

            // Deserialize payload
            Object event = objectMapper.readValue(retryRecord.getPayload(), Object.class);

            // TODO: Implement actual recovery logic
            // This would typically involve:
            // 1. Re-processing the account creation event
            // 2. Calling AccountService.createAccount() with proper error handling
            // 3. Verifying account was created successfully
            // 4. Publishing success event

            // For now, return false to indicate recovery not yet implemented
            log.warn("[DLQ-{}] Recovery logic not yet implemented - recordId={}",
                HANDLER_NAME, retryRecord.getId());

            return false;

        } catch (Exception e) {
            log.error("[DLQ-{}] Recovery attempt failed - recordId={}",
                HANDLER_NAME, retryRecord.getId(), e);
            return false;
        }
    }

    @Override
    protected String maskPii(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            if (root.isObject()) {
                ObjectNode objectNode = (ObjectNode) root;

                // Mask common PII fields
                maskField(objectNode, "ssn", "***-**-****");
                maskField(objectNode, "socialSecurityNumber", "***-**-****");
                maskField(objectNode, "taxId", "***-**-****");
                maskField(objectNode, "phoneNumber", "***-***-****");
                maskField(objectNode, "phone", "***-***-****");
                maskField(objectNode, "email", "*****@*****.***");
                maskField(objectNode, "password", "********");
                maskField(objectNode, "pin", "****");
                maskField(objectNode, "dateOfBirth", "****-**-**");
                maskField(objectNode, "dob", "****-**-**");
                maskField(objectNode, "address", "[REDACTED]");
                maskField(objectNode, "street", "[REDACTED]");
                maskField(objectNode, "city", "[REDACTED]");
                maskField(objectNode, "postalCode", "[REDACTED]");
                maskField(objectNode, "zipCode", "[REDACTED]");

                // Mask nested customer data
                if (objectNode.has("customer")) {
                    JsonNode customer = objectNode.get("customer");
                    if (customer.isObject()) {
                        ObjectNode customerNode = (ObjectNode) customer;
                        maskField(customerNode, "ssn", "***-**-****");
                        maskField(customerNode, "phoneNumber", "***-***-****");
                        maskField(customerNode, "email", "*****@*****.***");
                        maskField(customerNode, "dateOfBirth", "****-**-**");
                    }
                }

                // Mask nested account holder data
                if (objectNode.has("accountHolder")) {
                    JsonNode holder = objectNode.get("accountHolder");
                    if (holder.isObject()) {
                        ObjectNode holderNode = (ObjectNode) holder;
                        maskField(holderNode, "ssn", "***-**-****");
                        maskField(holderNode, "phoneNumber", "***-***-****");
                        maskField(holderNode, "email", "*****@*****.***");
                    }
                }

                return objectMapper.writeValueAsString(objectNode);
            }

            // If not JSON object, apply regex-based masking
            return applyRegexMasking(payload);

        } catch (Exception e) {
            log.error("[DLQ-{}] Failed to parse payload for PII masking, applying regex fallback",
                HANDLER_NAME, e);
            return applyRegexMasking(payload);
        }
    }

    @Override
    protected String getHandlerName() {
        return HANDLER_NAME;
    }

    // ========== HELPER METHODS ==========

    /**
     * Mask specific field in JSON object
     */
    private void maskField(ObjectNode node, String fieldName, String maskValue) {
        if (node.has(fieldName)) {
            node.put(fieldName, maskValue);
        }
    }

    /**
     * Apply regex-based PII masking for non-JSON payloads
     */
    private String applyRegexMasking(String payload) {
        String masked = payload;
        masked = SSN_PATTERN.matcher(masked).replaceAll("***-**-****");
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("*****@*****.***");
        masked = PHONE_PATTERN.matcher(masked).replaceAll("***-***-****");
        return masked;
    }

    /**
     * Check if string contains any of the given substrings
     */
    private boolean containsAny(String text, String... substrings) {
        for (String substring : substrings) {
            if (text.contains(substring)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract service name from exception message
     */
    private String extractServiceName(String exceptionMessage) {
        if (exceptionMessage.contains("user-service")) return "user-service";
        if (exceptionMessage.contains("notification-service")) return "notification-service";
        if (exceptionMessage.contains("kyc-service")) return "kyc-service";
        if (exceptionMessage.contains("tier-service")) return "tier-service";
        return "unknown-service";
    }

    /**
     * Extract resource type from exception message
     */
    private String extractResourceType(String exceptionMessage) {
        String lower = exceptionMessage.toLowerCase();
        if (lower.contains("user")) return "User";
        if (lower.contains("customer")) return "Customer";
        if (lower.contains("tier")) return "Tier";
        if (lower.contains("account")) return "Account";
        return "Resource";
    }

    /**
     * Extract resource ID from exception message
     */
    private String extractResourceId(String exceptionMessage) {
        // Try to extract UUID pattern
        Pattern uuidPattern = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
        var matcher = uuidPattern.matcher(exceptionMessage);
        if (matcher.find()) {
            return matcher.group();
        }

        // Try to extract numeric ID
        Pattern numericPattern = Pattern.compile("id[=:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE);
        matcher = numericPattern.matcher(exceptionMessage);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return "unknown";
    }
}
