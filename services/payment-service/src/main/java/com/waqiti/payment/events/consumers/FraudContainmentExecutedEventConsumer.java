package com.waqiti.payment.events.consumers;

import com.waqiti.payment.events.FraudContainmentExecutedEvent;
import com.waqiti.payment.client.NotificationServiceClient;
import com.waqiti.payment.client.RiskServiceClient;
import com.waqiti.payment.client.ComplianceServiceClient;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.service.IdempotencyService;
import com.waqiti.payment.service.AuditService;
import com.waqiti.payment.exception.ServiceIntegrationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class FraudContainmentExecutedEventConsumer {

    private final RiskServiceClient riskServiceClient;
    private final ComplianceServiceClient complianceServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;

    @KafkaListener(
        topics = "${kafka.topics.fraud-containment-executed:fraud-containment-executed}",
        groupId = "${kafka.consumer.group-id:payment-service-consumer-group}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "${kafka.consumer.concurrency:3}"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        include = {ServiceIntegrationException.class, Exception.class},
        dltTopicSuffix = "-dlt",
        autoCreateTopics = "true",
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRED, timeout = 30, rollbackFor = Exception.class)
    public void handleFraudContainmentExecuted(
            @Payload FraudContainmentExecutedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {

        Instant startTime = Instant.now();
        String correlationId = event.getCorrelationId() != null ? 
            event.getCorrelationId() : UUID.randomUUID().toString();

        log.info("FRAUD_CONTAINMENT: Processing fraud containment executed event - " +
                "eventId={}, alertId={}, userId={}, transactionId={}, fraudType={}, " +
                "containmentActions={}, topic={}, partition={}, offset={}",
                event.getEventId(), event.getAlertId(), event.getUserId(), 
                event.getTransactionId(), event.getFraudType(), 
                event.getContainmentActionsCount(), topic, partition, offset);

        try {
            validateEvent(event);

            String idempotencyKey = String.format("fraud-containment-executed:%s:%s", 
                event.getEventId(), event.getAlertId());
            
            boolean processed = idempotencyService.executeIdempotent(idempotencyKey, () -> {
                processFraudContainmentExecuted(event, correlationId, startTime);
                return true;
            }, Duration.ofHours(24));

            if (!processed) {
                log.info("FRAUD_CONTAINMENT: Duplicate event detected and skipped - eventId={}, alertId={}",
                    event.getEventId(), event.getAlertId());
            }

            acknowledgment.acknowledge();
            
            long processingTimeMs = Duration.between(startTime, Instant.now()).toMillis();
            log.info("FRAUD_CONTAINMENT: Successfully processed fraud containment executed event - " +
                    "eventId={}, alertId={}, userId={}, processingTimeMs={}",
                    event.getEventId(), event.getAlertId(), event.getUserId(), processingTimeMs);

        } catch (Exception e) {
            log.error("FRAUD_CONTAINMENT: Failed to process fraud containment executed event - " +
                    "eventId={}, alertId={}, userId={}, error={}",
                    event.getEventId(), event.getAlertId(), event.getUserId(), e.getMessage(), e);
            
            auditService.logEventProcessingFailure(
                "FraudContainmentExecutedEvent",
                event.getEventId(),
                event.getUserId(),
                e.getMessage(),
                Map.of(
                    "alertId", event.getAlertId(),
                    "transactionId", event.getTransactionId() != null ? event.getTransactionId() : "N/A",
                    "fraudType", event.getFraudType() != null ? event.getFraudType() : "UNKNOWN",
                    "containmentActions", event.getContainmentActionsCount() != null ? 
                        event.getContainmentActionsCount().toString() : "0"
                )
            );
            
            throw new ServiceIntegrationException(
                "Failed to process fraud containment executed event: " + e.getMessage(), e);
        }
    }

    private void processFraudContainmentExecuted(
            FraudContainmentExecutedEvent event, 
            String correlationId,
            Instant startTime) {

        log.info("FRAUD_CONTAINMENT: Starting fraud containment processing pipeline - " +
                "eventId={}, alertId={}, userId={}",
                event.getEventId(), event.getAlertId(), event.getUserId());

        updateRiskScores(event, correlationId);

        createComplianceAlert(event, correlationId);

        sendSecurityNotifications(event, correlationId);

        logAuditEntry(event, correlationId, startTime);

        log.info("FRAUD_CONTAINMENT: Completed fraud containment processing pipeline - " +
                "eventId={}, alertId={}, userId={}",
                event.getEventId(), event.getAlertId(), event.getUserId());
    }

    private void updateRiskScores(FraudContainmentExecutedEvent event, String correlationId) {
        try {
            log.info("FRAUD_CONTAINMENT: Updating risk scores for user - userId={}, alertId={}, fraudScore={}",
                event.getUserId(), event.getAlertId(), event.getFraudScore());

            UpdateUserRiskScoreRequest request = UpdateUserRiskScoreRequest.builder()
                .userId(event.getUserId())
                .alertId(event.getAlertId())
                .fraudType(event.getFraudType())
                .fraudScore(event.getFraudScore())
                .riskLevel(event.getRiskLevel())
                .severity(event.getSeverity())
                .containmentActions(event.getContainmentActions())
                .accountSuspended(event.getAccountSuspended() != null && event.getAccountSuspended())
                .cardsBlocked(event.getCardsBlocked() != null && event.getCardsBlocked())
                .enhancedMonitoringEnabled(event.getEnhancedMonitoringEnabled() != null && 
                    event.getEnhancedMonitoringEnabled())
                .transactionId(event.getTransactionId())
                .amount(event.getTransactionAmount())
                .currency(event.getCurrency())
                .fraudIndicators(event.getFraudIndicators())
                .detectedAt(event.getDetectedAt())
                .executedAt(event.getExecutedAt())
                .correlationId(correlationId)
                .build();

            updateUserRiskScoreWithRetry(request);

            log.info("FRAUD_CONTAINMENT: Successfully updated risk scores - userId={}, alertId={}",
                event.getUserId(), event.getAlertId());

        } catch (Exception e) {
            log.error("FRAUD_CONTAINMENT: Failed to update risk scores (non-critical) - userId={}, alertId={}, error={}",
                event.getUserId(), event.getAlertId(), e.getMessage(), e);
        }
    }

    @CircuitBreaker(name = "risk-service", fallbackMethod = "updateRiskScoresFallback")
    @Retry(name = "risk-service")
    @TimeLimiter(name = "risk-service")
    private void updateUserRiskScoreWithRetry(UpdateUserRiskScoreRequest request) {
        riskServiceClient.updateUserRiskScore(request);
    }

    private void updateRiskScoresFallback(UpdateUserRiskScoreRequest request, Exception e) {
        log.warn("FRAUD_CONTAINMENT: Risk score update fallback triggered - userId={}, reason={}. " +
                "Risk scores will be updated via batch reconciliation job.",
                request.getUserId(), e.getMessage());
    }

    private void createComplianceAlert(FraudContainmentExecutedEvent event, String correlationId) {
        try {
            log.info("FRAUD_CONTAINMENT: Creating compliance alert - userId={}, alertId={}, fraudType={}",
                event.getUserId(), event.getAlertId(), event.getFraudType());

            CreateComplianceAlertRequest request = CreateComplianceAlertRequest.builder()
                .alertType("FRAUD_CONTAINMENT_EXECUTED")
                .alertId(event.getAlertId())
                .userId(event.getUserId())
                .transactionId(event.getTransactionId())
                .fraudType(event.getFraudType())
                .severity(event.getSeverity())
                .riskLevel(event.getRiskLevel())
                .fraudScore(event.getFraudScore())
                .containmentActions(event.getContainmentActions())
                .containmentReason(event.getContainmentReason())
                .affectedAccounts(event.getAffectedAccounts())
                .affectedCards(event.getAffectedCards())
                .affectedTransactions(event.getAffectedTransactions())
                .transactionAmount(event.getTransactionAmount())
                .currency(event.getCurrency())
                .accountStatus(event.getAccountStatus())
                .requiresRegulatorNotification(shouldNotifyRegulator(event))
                .requiresInvestigation(true)
                .priority(determinePriority(event))
                .detectedAt(event.getDetectedAt())
                .executedAt(event.getExecutedAt())
                .executedBy(event.getExecutedBy())
                .executionSource(event.getExecutionSource())
                .metadata(buildComplianceMetadata(event))
                .correlationId(correlationId)
                .build();

            createComplianceAlertWithRetry(request);

            log.info("FRAUD_CONTAINMENT: Successfully created compliance alert - userId={}, alertId={}",
                event.getUserId(), event.getAlertId());

        } catch (Exception e) {
            log.error("FRAUD_CONTAINMENT: CRITICAL - Failed to create compliance alert - " +
                    "userId={}, alertId={}, error={}. Manual compliance notification required.",
                    event.getUserId(), event.getAlertId(), e.getMessage(), e);
            
            throw new ServiceIntegrationException(
                "CRITICAL: Failed to create compliance alert for fraud containment", e);
        }
    }

    @CircuitBreaker(name = "compliance-service", fallbackMethod = "createComplianceAlertFallback")
    @Retry(name = "compliance-service")
    @TimeLimiter(name = "compliance-service")
    private void createComplianceAlertWithRetry(CreateComplianceAlertRequest request) {
        complianceServiceClient.createComplianceAlert(request);
    }

    private void createComplianceAlertFallback(CreateComplianceAlertRequest request, Exception e) {
        log.error("FRAUD_CONTAINMENT: CRITICAL - Compliance alert creation failed after retries - " +
                "userId={}, alertId={}. Escalating to manual intervention queue.",
                request.getUserId(), request.getAlertId());
        
        auditService.logCriticalComplianceFailure(
            "FRAUD_CONTAINMENT_COMPLIANCE_ALERT_FAILED",
            request.getUserId(),
            request.getAlertId(),
            request.getFraudType(),
            "Failed to create compliance alert: " + e.getMessage()
        );
        
        throw new ServiceIntegrationException(
            "CRITICAL: Compliance alert creation failed - manual intervention required", e);
    }

    private void sendSecurityNotifications(FraudContainmentExecutedEvent event, String correlationId) {
        try {
            log.info("FRAUD_CONTAINMENT: Sending security notifications - userId={}, alertId={}",
                event.getUserId(), event.getAlertId());

            sendUserSecurityAlert(event, correlationId);

            sendSecurityTeamAlert(event, correlationId);

            if (shouldNotifyRegulator(event)) {
                sendRegulatoryNotification(event, correlationId);
            }

            log.info("FRAUD_CONTAINMENT: Successfully sent all security notifications - userId={}, alertId={}",
                event.getUserId(), event.getAlertId());

        } catch (Exception e) {
            log.error("FRAUD_CONTAINMENT: Failed to send security notifications (non-critical) - " +
                    "userId={}, alertId={}, error={}",
                    event.getUserId(), event.getAlertId(), e.getMessage(), e);
        }
    }

    private void sendUserSecurityAlert(FraudContainmentExecutedEvent event, String correlationId) {
        try {
            FraudAlertNotificationRequest request = FraudAlertNotificationRequest.builder()
                .userId(event.getUserId())
                .alertId(event.getAlertId())
                .alertType("FRAUD_CONTAINMENT_EXECUTED")
                .fraudType(event.getFraudType())
                .severity(event.getSeverity())
                .containmentActions(event.getContainmentActions())
                .accountSuspended(event.getAccountSuspended() != null && event.getAccountSuspended())
                .cardsBlocked(event.getCardsBlocked() != null && event.getCardsBlocked())
                .transactionBlocked(event.getTransactionBlocked() != null && event.getTransactionBlocked())
                .message(buildUserNotificationMessage(event))
                .actionRequired(buildActionRequiredMessage(event))
                .channels(determineNotificationChannels(event))
                .priority("HIGH")
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build();

            sendUserAlertWithRetry(request);

        } catch (Exception e) {
            log.warn("FRAUD_CONTAINMENT: Failed to send user security alert - userId={}, error={}",
                event.getUserId(), e.getMessage());
        }
    }

    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendUserAlertFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    private void sendUserAlertWithRetry(FraudAlertNotificationRequest request) {
        notificationServiceClient.sendFraudAlertNotification(request);
    }

    private void sendUserAlertFallback(FraudAlertNotificationRequest request, Exception e) {
        log.warn("FRAUD_CONTAINMENT: User notification fallback - userId={}. " +
                "Notification will be sent via batch job.",
                request.getUserId());
    }

    private void sendSecurityTeamAlert(FraudContainmentExecutedEvent event, String correlationId) {
        try {
            SecurityTeamAlertRequest request = SecurityTeamAlertRequest.builder()
                .alertType("FRAUD_CONTAINMENT_EXECUTED")
                .alertId(event.getAlertId())
                .userId(event.getUserId())
                .transactionId(event.getTransactionId())
                .fraudType(event.getFraudType())
                .severity(event.getSeverity())
                .fraudScore(event.getFraudScore())
                .containmentActions(event.getContainmentActions())
                .containmentActionsCount(event.getContainmentActionsCount())
                .transactionAmount(event.getTransactionAmount())
                .currency(event.getCurrency())
                .accountStatus(event.getAccountStatus())
                .affectedAccounts(event.getAffectedAccounts())
                .affectedCards(event.getAffectedCards())
                .affectedTransactions(event.getAffectedTransactions())
                .responseTimeMs(event.getResponseTimeMs())
                .detectedAt(event.getDetectedAt())
                .executedAt(event.getExecutedAt())
                .requiresInvestigation(true)
                .priority("HIGH")
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build();

            sendSecurityTeamAlertWithRetry(request);

        } catch (Exception e) {
            log.warn("FRAUD_CONTAINMENT: Failed to send security team alert - alertId={}, error={}",
                event.getAlertId(), e.getMessage());
        }
    }

    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendSecurityTeamAlertFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    private void sendSecurityTeamAlertWithRetry(SecurityTeamAlertRequest request) {
        notificationServiceClient.sendSecurityTeamAlert(request);
    }

    private void sendSecurityTeamAlertFallback(SecurityTeamAlertRequest request, Exception e) {
        log.warn("FRAUD_CONTAINMENT: Security team notification fallback - alertId={}. " +
                "Alert will be sent via backup channel.",
                request.getAlertId());
    }

    private void sendRegulatoryNotification(FraudContainmentExecutedEvent event, String correlationId) {
        try {
            log.warn("FRAUD_CONTAINMENT: Sending regulatory notification - userId={}, alertId={}, fraudType={}",
                event.getUserId(), event.getAlertId(), event.getFraudType());

            RegulatoryNotificationRequest request = RegulatoryNotificationRequest.builder()
                .notificationType("FRAUD_CONTAINMENT_EXECUTED")
                .alertId(event.getAlertId())
                .userId(event.getUserId())
                .transactionId(event.getTransactionId())
                .fraudType(event.getFraudType())
                .severity(event.getSeverity())
                .transactionAmount(event.getTransactionAmount())
                .currency(event.getCurrency())
                .containmentActions(event.getContainmentActions())
                .detectedAt(event.getDetectedAt())
                .executedAt(event.getExecutedAt())
                .correlationId(correlationId)
                .build();

            sendRegulatoryNotificationWithRetry(request);

        } catch (Exception e) {
            log.error("FRAUD_CONTAINMENT: Failed to send regulatory notification - " +
                    "userId={}, alertId={}, error={}",
                    event.getUserId(), event.getAlertId(), e.getMessage());
        }
    }

    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendRegulatoryNotificationFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    private void sendRegulatoryNotificationWithRetry(RegulatoryNotificationRequest request) {
        notificationServiceClient.sendRegulatoryNotification(request);
    }

    private void sendRegulatoryNotificationFallback(RegulatoryNotificationRequest request, Exception e) {
        log.error("FRAUD_CONTAINMENT: CRITICAL - Regulatory notification failed - alertId={}. " +
                "Manual regulatory notification required.",
                request.getAlertId());
        
        auditService.logCriticalRegulatoryNotificationFailure(
            request.getAlertId(),
            request.getUserId(),
            request.getFraudType(),
            "Failed to send regulatory notification: " + e.getMessage()
        );
    }

    private void logAuditEntry(FraudContainmentExecutedEvent event, String correlationId, Instant startTime) {
        try {
            long processingTimeMs = Duration.between(startTime, Instant.now()).toMillis();
            
            auditService.logFraudContainmentExecuted(
                event.getEventId(),
                event.getAlertId(),
                event.getUserId(),
                event.getTransactionId(),
                event.getFraudType(),
                event.getFraudScore(),
                event.getRiskLevel(),
                event.getSeverity(),
                event.getContainmentActions(),
                event.getContainmentActionsCount(),
                event.getTransactionAmount(),
                event.getCurrency(),
                event.getAccountStatus(),
                event.getAccountSuspended(),
                event.getCardsBlocked(),
                event.getTransactionBlocked(),
                event.getEnhancedMonitoringEnabled(),
                event.getContainmentReason(),
                event.getAffectedAccounts(),
                event.getAffectedCards(),
                event.getAffectedTransactions(),
                event.getExecutedBy(),
                event.getExecutionSource(),
                event.getDetectedAt(),
                event.getExecutedAt(),
                event.getResponseTimeMs(),
                processingTimeMs,
                correlationId,
                Map.of(
                    "fraudIndicators", event.getFraudIndicators() != null ? event.getFraudIndicators() : Map.of(),
                    "containmentDetails", event.getContainmentDetails() != null ? event.getContainmentDetails() : Map.of(),
                    "eventProcessedAt", Instant.now()
                )
            );

            log.debug("FRAUD_CONTAINMENT: Audit entry logged - eventId={}, alertId={}, processingTimeMs={}",
                event.getEventId(), event.getAlertId(), processingTimeMs);

        } catch (Exception e) {
            log.error("FRAUD_CONTAINMENT: Failed to log audit entry - eventId={}, error={}",
                event.getEventId(), e.getMessage(), e);
        }
    }

    private void validateEvent(FraudContainmentExecutedEvent event) {
        List<String> errors = new ArrayList<>();

        if (event == null) {
            throw new IllegalArgumentException("FraudContainmentExecutedEvent cannot be null");
        }

        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            errors.add("eventId is required");
        }

        if (event.getAlertId() == null || event.getAlertId().trim().isEmpty()) {
            errors.add("alertId is required");
        }

        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            errors.add("userId is required");
        }

        if (event.getFraudType() == null || event.getFraudType().trim().isEmpty()) {
            errors.add("fraudType is required");
        }

        if (event.getExecutedAt() == null) {
            errors.add("executedAt timestamp is required");
        }

        if (!errors.isEmpty()) {
            String errorMessage = "Invalid FraudContainmentExecutedEvent: " + String.join(", ", errors);
            log.error("FRAUD_CONTAINMENT: Event validation failed - eventId={}, errors={}",
                event.getEventId(), errors);
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private boolean shouldNotifyRegulator(FraudContainmentExecutedEvent event) {
        if ("CRITICAL".equals(event.getSeverity())) {
            return true;
        }

        if (event.getFraudScore() != null && event.getFraudScore() >= 0.90) {
            return true;
        }

        if ("MONEY_LAUNDERING".equals(event.getFraudType()) || 
            "TERRORIST_FINANCING".equals(event.getFraudType())) {
            return true;
        }

        if (event.getTransactionAmount() != null && 
            event.getTransactionAmount().doubleValue() >= 10000.0) {
            return true;
        }

        return false;
    }

    private String determinePriority(FraudContainmentExecutedEvent event) {
        if ("CRITICAL".equals(event.getSeverity())) {
            return "CRITICAL";
        }

        if (shouldNotifyRegulator(event)) {
            return "HIGH";
        }

        if ("HIGH".equals(event.getSeverity())) {
            return "HIGH";
        }

        return "MEDIUM";
    }

    private Map<String, Object> buildComplianceMetadata(FraudContainmentExecutedEvent event) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("eventId", event.getEventId());
        metadata.put("version", event.getVersion() != null ? event.getVersion() : "1.0");
        metadata.put("fraudIndicators", event.getFraudIndicators() != null ? event.getFraudIndicators() : Map.of());
        metadata.put("containmentDetails", event.getContainmentDetails() != null ? event.getContainmentDetails() : Map.of());
        metadata.put("responseTimeMs", event.getResponseTimeMs());
        metadata.put("processedAt", Instant.now());
        return metadata;
    }

    private String buildUserNotificationMessage(FraudContainmentExecutedEvent event) {
        StringBuilder message = new StringBuilder();
        message.append("We have detected suspicious activity on your account and have taken immediate action to protect you. ");

        if (event.getAccountSuspended() != null && event.getAccountSuspended()) {
            message.append("Your account has been temporarily suspended. ");
        }

        if (event.getCardsBlocked() != null && event.getCardsBlocked()) {
            message.append("Your cards have been blocked. ");
        }

        if (event.getTransactionBlocked() != null && event.getTransactionBlocked()) {
            message.append("A suspicious transaction has been blocked. ");
        }

        message.append("Please contact our security team immediately to verify your identity and restore access to your account.");

        return message.toString();
    }

    private String buildActionRequiredMessage(FraudContainmentExecutedEvent event) {
        StringBuilder message = new StringBuilder();
        message.append("Action Required: ");

        if (event.getAccountSuspended() != null && event.getAccountSuspended()) {
            message.append("Verify your identity to restore account access. ");
        }

        if (event.getCardsBlocked() != null && event.getCardsBlocked()) {
            message.append("Contact support to request new cards. ");
        }

        message.append("Call our 24/7 security hotline: 1-800-WAQITI-SEC");

        return message.toString();
    }

    private List<String> determineNotificationChannels(FraudContainmentExecutedEvent event) {
        List<String> channels = new ArrayList<>();
        channels.add("EMAIL");
        channels.add("SMS");
        channels.add("PUSH_NOTIFICATION");

        if ("CRITICAL".equals(event.getSeverity())) {
            channels.add("PHONE_CALL");
        }

        return channels;
    }

    @KafkaListener(
        topics = "${kafka.topics.fraud-containment-executed-dlt:fraud-containment-executed-dlt}",
        groupId = "${kafka.consumer.group-id:payment-service-consumer-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDeadLetterTopic(
            @Payload FraudContainmentExecutedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        log.error("FRAUD_CONTAINMENT: DLT - Event processing failed after all retries - " +
                "eventId={}, alertId={}, userId={}, error={}. Manual intervention required.",
                event.getEventId(), event.getAlertId(), event.getUserId(), exceptionMessage);

        auditService.logDeadLetterEvent(
            "FraudContainmentExecutedEvent",
            event.getEventId(),
            event.getUserId(),
            exceptionMessage,
            Map.of(
                "alertId", event.getAlertId(),
                "fraudType", event.getFraudType() != null ? event.getFraudType() : "UNKNOWN",
                "severity", event.getSeverity() != null ? event.getSeverity() : "UNKNOWN",
                "containmentActions", event.getContainmentActionsCount() != null ? 
                    event.getContainmentActionsCount().toString() : "0",
                "dltTopic", topic
            )
        );
    }
}