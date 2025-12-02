package com.waqiti.card.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.card.model.*;
import com.waqiti.card.repository.CardActivationRepository;
import com.waqiti.card.service.*;
import com.waqiti.common.audit.AuditService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Kafka consumer for card activation status events
 * Tracks card activation status changes with enterprise patterns
 * 
 * Critical for: Card lifecycle management, customer onboarding, security
 * SLA: Must process activation status within 30 seconds for customer experience
 */
@Component
@Slf4j
public class CardActivationStatusConsumer {

    private final CardActivationRepository cardActivationRepository;
    private final CardService cardService;
    private final CardSecurityService cardSecurityService;
    private final CustomerOnboardingService customerOnboardingService;
    private final NotificationService notificationService;
    private final FraudDetectionService fraudDetectionService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter cardActivationStatusCounter;
    private final Counter suspiciousActivationCounter;
    private final Counter failedActivationCounter;
    private final Timer activationProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public CardActivationStatusConsumer(
            CardActivationRepository cardActivationRepository,
            CardService cardService,
            CardSecurityService cardSecurityService,
            CustomerOnboardingService customerOnboardingService,
            NotificationService notificationService,
            FraudDetectionService fraudDetectionService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.cardActivationRepository = cardActivationRepository;
        this.cardService = cardService;
        this.cardSecurityService = cardSecurityService;
        this.customerOnboardingService = customerOnboardingService;
        this.notificationService = notificationService;
        this.fraudDetectionService = fraudDetectionService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.cardActivationStatusCounter = Counter.builder("card.activation.status.events")
            .description("Count of card activation status events")
            .register(meterRegistry);
        
        this.suspiciousActivationCounter = Counter.builder("card.activation.status.suspicious.events")
            .description("Count of suspicious activation events")
            .register(meterRegistry);
        
        this.failedActivationCounter = Counter.builder("card.activation.status.failed.events")
            .description("Count of failed activation events")
            .register(meterRegistry);
        
        this.activationProcessingTimer = Timer.builder("card.activation.status.processing.duration")
            .description("Time taken to process activation status events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "card-activation-status",
        groupId = "card-activation-status-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "card-activation-status-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleCardActivationStatusEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Received card activation status event - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String cardId = (String) eventData.get("cardId");
            String userId = (String) eventData.get("userId");
            String activationId = (String) eventData.get("activationId");
            String previousStatus = (String) eventData.get("previousStatus");
            String newStatus = (String) eventData.get("newStatus");
            String statusChangeReason = (String) eventData.get("statusChangeReason");
            String activationMethod = (String) eventData.get("activationMethod");
            String activationChannel = (String) eventData.get("activationChannel");
            String deviceInfo = (String) eventData.get("deviceInfo");
            String ipAddress = (String) eventData.get("ipAddress");
            String location = (String) eventData.get("location");
            Integer attemptCount = (Integer) eventData.getOrDefault("attemptCount", 1);
            Boolean isFirstActivation = (Boolean) eventData.getOrDefault("isFirstActivation", false);
            Boolean requiresVerification = (Boolean) eventData.getOrDefault("requiresVerification", false);
            
            String correlationId = String.format("card-activation-status-%s-%d", 
                cardId, System.currentTimeMillis());
            
            log.info("Processing card activation status - cardId: {}, userId: {}, status: {} -> {}, correlationId: {}", 
                cardId, userId, previousStatus, newStatus, correlationId);
            
            cardActivationStatusCounter.increment();
            
            processCardActivationStatus(cardId, userId, activationId, previousStatus, newStatus,
                statusChangeReason, activationMethod, activationChannel, deviceInfo, ipAddress,
                location, attemptCount, isFirstActivation, requiresVerification, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(activationProcessingTimer);
            
            log.info("Successfully processed card activation status event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process card activation status event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Card activation status processing failed", e);
        }
    }

    @CircuitBreaker(name = "card-activation", fallbackMethod = "processCardActivationStatusFallback")
    @Retry(name = "card-activation")
    private void processCardActivationStatus(
            String cardId,
            String userId,
            String activationId,
            String previousStatus,
            String newStatus,
            String statusChangeReason,
            String activationMethod,
            String activationChannel,
            String deviceInfo,
            String ipAddress,
            String location,
            Integer attemptCount,
            Boolean isFirstActivation,
            Boolean requiresVerification,
            Map<String, Object> eventData,
            String correlationId) {
        
        // Create card activation status record
        CardActivationStatus cardActivationStatus = CardActivationStatus.builder()
            .id(java.util.UUID.randomUUID().toString())
            .cardId(cardId)
            .userId(userId)
            .activationId(activationId)
            .previousStatus(CardStatus.fromString(previousStatus))
            .newStatus(CardStatus.fromString(newStatus))
            .statusChangeReason(statusChangeReason)
            .activationMethod(activationMethod)
            .activationChannel(activationChannel)
            .deviceInfo(deviceInfo)
            .ipAddress(ipAddress)
            .location(location)
            .attemptCount(attemptCount)
            .isFirstActivation(isFirstActivation)
            .requiresVerification(requiresVerification)
            .eventTimestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        cardActivationRepository.save(cardActivationStatus);
        
        // Handle different activation status changes
        switch (newStatus) {
            case "ACTIVATED" -> handleCardActivated(cardActivationStatus, correlationId);
            case "ACTIVATION_FAILED" -> handleActivationFailed(cardActivationStatus, correlationId);
            case "ACTIVATION_PENDING" -> handleActivationPending(cardActivationStatus, correlationId);
            case "ACTIVATION_BLOCKED" -> handleActivationBlocked(cardActivationStatus, correlationId);
            case "REQUIRES_VERIFICATION" -> handleRequiresVerification(cardActivationStatus, correlationId);
        }
        
        // Perform security checks
        performSecurityChecks(cardActivationStatus, correlationId);
        
        // Update card lifecycle
        updateCardLifecycle(cardActivationStatus, correlationId);
        
        // Send notifications
        sendActivationNotifications(cardActivationStatus, correlationId);
        
        // Update customer onboarding
        updateCustomerOnboarding(cardActivationStatus, correlationId);
        
        // Publish activation analytics
        publishActivationAnalytics(cardActivationStatus, correlationId);
        
        // Audit the activation status change
        auditService.logCardEvent(
            "CARD_ACTIVATION_STATUS_CHANGED",
            cardActivationStatus.getId(),
            Map.of(
                "cardId", cardId,
                "userId", userId,
                "activationId", activationId,
                "previousStatus", previousStatus,
                "newStatus", newStatus,
                "statusChangeReason", statusChangeReason,
                "activationMethod", activationMethod,
                "activationChannel", activationChannel,
                "attemptCount", attemptCount,
                "isFirstActivation", isFirstActivation,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        log.info("Card activation status processed - cardId: {}, status: {} -> {}, correlationId: {}", 
            cardId, previousStatus, newStatus, correlationId);
    }

    private void handleCardActivated(CardActivationStatus cardActivationStatus, String correlationId) {
        log.info("CARD ACTIVATED: Card successfully activated - cardId: {}, userId: {}, correlationId: {}", 
            cardActivationStatus.getCardId(), cardActivationStatus.getUserId(), correlationId);
        
        // Update card service
        cardService.updateCardStatus(cardActivationStatus.getCardId(), CardStatus.ACTIVE);
        
        // Send activation success events
        kafkaTemplate.send("card-activation-success", Map.of(
            "cardId", cardActivationStatus.getCardId(),
            "userId", cardActivationStatus.getUserId(),
            "activationId", cardActivationStatus.getActivationId(),
            "activationMethod", cardActivationStatus.getActivationMethod(),
            "activationChannel", cardActivationStatus.getActivationChannel(),
            "isFirstActivation", cardActivationStatus.getIsFirstActivation(),
            "eventType", "CARD_ACTIVATION_SUCCESS",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Enable card features
        cardService.enableCardFeatures(cardActivationStatus.getCardId());
        
        // Send welcome notification
        notificationService.sendCardActivationWelcome(
            cardActivationStatus.getUserId(),
            "Card Activated Successfully",
            "Your card has been activated and is ready to use!",
            Map.of(
                "cardId", cardActivationStatus.getCardId(),
                "activationDate", LocalDateTime.now().toString(),
                "correlationId", correlationId
            )
        );
    }

    private void handleActivationFailed(CardActivationStatus cardActivationStatus, String correlationId) {
        failedActivationCounter.increment();
        
        log.error("CARD ACTIVATION FAILED: Card activation failed - cardId: {}, userId: {}, reason: {}, correlationId: {}", 
            cardActivationStatus.getCardId(), cardActivationStatus.getUserId(), 
            cardActivationStatus.getStatusChangeReason(), correlationId);
        
        // Send activation failure events
        kafkaTemplate.send("card-activation-failure", Map.of(
            "cardId", cardActivationStatus.getCardId(),
            "userId", cardActivationStatus.getUserId(),
            "activationId", cardActivationStatus.getActivationId(),
            "failureReason", cardActivationStatus.getStatusChangeReason(),
            "attemptCount", cardActivationStatus.getAttemptCount(),
            "activationMethod", cardActivationStatus.getActivationMethod(),
            "eventType", "CARD_ACTIVATION_FAILURE",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Check if maximum attempts exceeded
        if (cardActivationStatus.getAttemptCount() >= 5) {
            // Block card temporarily
            cardService.temporaryBlockCard(
                cardActivationStatus.getCardId(),
                "ACTIVATION_ATTEMPTS_EXCEEDED",
                correlationId
            );
            
            // Send security alert
            kafkaTemplate.send("card-security-alerts", Map.of(
                "alertType", "ACTIVATION_ATTEMPTS_EXCEEDED",
                "cardId", cardActivationStatus.getCardId(),
                "userId", cardActivationStatus.getUserId(),
                "attemptCount", cardActivationStatus.getAttemptCount(),
                "severity", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
        
        // Send failure notification
        notificationService.sendCardActivationFailure(
            cardActivationStatus.getUserId(),
            "Card Activation Failed",
            String.format("Your card activation failed: %s. Please try again or contact support.", 
                cardActivationStatus.getStatusChangeReason()),
            Map.of(
                "cardId", cardActivationStatus.getCardId(),
                "failureReason", cardActivationStatus.getStatusChangeReason(),
                "attemptCount", cardActivationStatus.getAttemptCount(),
                "correlationId", correlationId
            )
        );
    }

    private void handleActivationPending(CardActivationStatus cardActivationStatus, String correlationId) {
        log.info("CARD ACTIVATION PENDING: Card activation pending - cardId: {}, userId: {}, correlationId: {}", 
            cardActivationStatus.getCardId(), cardActivationStatus.getUserId(), correlationId);
        
        // Create pending activation workflow
        kafkaTemplate.send("card-activation-pending-workflows", Map.of(
            "workflowType", "PENDING_CARD_ACTIVATION",
            "cardId", cardActivationStatus.getCardId(),
            "userId", cardActivationStatus.getUserId(),
            "activationId", cardActivationStatus.getActivationId(),
            "pendingReason", cardActivationStatus.getStatusChangeReason(),
            "priority", "MEDIUM",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleActivationBlocked(CardActivationStatus cardActivationStatus, String correlationId) {
        log.error("CARD ACTIVATION BLOCKED: Card activation blocked - cardId: {}, userId: {}, reason: {}, correlationId: {}", 
            cardActivationStatus.getCardId(), cardActivationStatus.getUserId(), 
            cardActivationStatus.getStatusChangeReason(), correlationId);
        
        // Send security alert for blocked activation
        kafkaTemplate.send("card-security-alerts", Map.of(
            "alertType", "ACTIVATION_BLOCKED",
            "cardId", cardActivationStatus.getCardId(),
            "userId", cardActivationStatus.getUserId(),
            "blockReason", cardActivationStatus.getStatusChangeReason(),
            "severity", "HIGH",
            "requiresInvestigation", true,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Notify security team
        notificationService.sendSecurityAlert(
            "Card Activation Blocked",
            String.format("Card activation blocked for card %s: %s", 
                cardActivationStatus.getCardId(), cardActivationStatus.getStatusChangeReason()),
            Map.of(
                "cardId", cardActivationStatus.getCardId(),
                "userId", cardActivationStatus.getUserId(),
                "blockReason", cardActivationStatus.getStatusChangeReason(),
                "correlationId", correlationId
            )
        );
    }

    private void handleRequiresVerification(CardActivationStatus cardActivationStatus, String correlationId) {
        log.info("CARD ACTIVATION REQUIRES VERIFICATION: Card activation requires verification - cardId: {}, userId: {}, correlationId: {}", 
            cardActivationStatus.getCardId(), cardActivationStatus.getUserId(), correlationId);
        
        // Create verification workflow
        kafkaTemplate.send("card-verification-required", Map.of(
            "verificationType", "CARD_ACTIVATION_VERIFICATION",
            "cardId", cardActivationStatus.getCardId(),
            "userId", cardActivationStatus.getUserId(),
            "activationId", cardActivationStatus.getActivationId(),
            "verificationReason", cardActivationStatus.getStatusChangeReason(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Send verification notification
        notificationService.sendVerificationRequired(
            cardActivationStatus.getUserId(),
            "Verification Required",
            "Additional verification is required to activate your card. Please check your app for next steps.",
            Map.of(
                "cardId", cardActivationStatus.getCardId(),
                "verificationReason", cardActivationStatus.getStatusChangeReason(),
                "correlationId", correlationId
            )
        );
    }

    private void performSecurityChecks(CardActivationStatus cardActivationStatus, String correlationId) {
        // Perform fraud detection checks
        FraudCheckResult fraudResult = fraudDetectionService.checkActivationFraud(
            cardActivationStatus.getCardId(),
            cardActivationStatus.getUserId(),
            cardActivationStatus.getIpAddress(),
            cardActivationStatus.getDeviceInfo(),
            cardActivationStatus.getLocation(),
            cardActivationStatus.getAttemptCount()
        );
        
        if (fraudResult.isSuspicious()) {
            suspiciousActivationCounter.increment();
            
            kafkaTemplate.send("suspicious-activation-alerts", Map.of(
                "cardId", cardActivationStatus.getCardId(),
                "userId", cardActivationStatus.getUserId(),
                "fraudScore", fraudResult.getFraudScore(),
                "suspiciousFactors", fraudResult.getSuspiciousFactors(),
                "ipAddress", cardActivationStatus.getIpAddress(),
                "location", cardActivationStatus.getLocation(),
                "alertType", "SUSPICIOUS_CARD_ACTIVATION",
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
            
            // Enhanced monitoring for suspicious activations
            cardSecurityService.enableEnhancedMonitoring(
                cardActivationStatus.getCardId(),
                "SUSPICIOUS_ACTIVATION",
                correlationId
            );
        }
    }

    private void updateCardLifecycle(CardActivationStatus cardActivationStatus, String correlationId) {
        // Update card lifecycle tracking
        cardService.updateCardLifecycleStage(
            cardActivationStatus.getCardId(),
            "ACTIVATION_STATUS_CHANGE",
            cardActivationStatus.getNewStatus().toString(),
            correlationId
        );
    }

    private void sendActivationNotifications(CardActivationStatus cardActivationStatus, String correlationId) {
        // Send appropriate notifications based on status
        switch (cardActivationStatus.getNewStatus()) {
            case ACTIVE -> {
                // Success notification already sent in handleCardActivated
            }
            case ACTIVATION_FAILED -> {
                // Failure notification already sent in handleActivationFailed
            }
            case ACTIVATION_PENDING -> {
                notificationService.sendActivationPending(
                    cardActivationStatus.getUserId(),
                    "Card Activation Pending",
                    "Your card activation is being processed. You'll be notified once complete.",
                    Map.of("cardId", cardActivationStatus.getCardId(), "correlationId", correlationId)
                );
            }
        }
    }

    private void updateCustomerOnboarding(CardActivationStatus cardActivationStatus, String correlationId) {
        // Update customer onboarding progress
        if (cardActivationStatus.getIsFirstActivation()) {
            customerOnboardingService.updateOnboardingProgress(
                cardActivationStatus.getUserId(),
                "CARD_ACTIVATION",
                cardActivationStatus.getNewStatus().toString(),
                correlationId
            );
        }
    }

    private void publishActivationAnalytics(CardActivationStatus cardActivationStatus, String correlationId) {
        // Publish activation analytics
        kafkaTemplate.send("card-activation-analytics", Map.of(
            "activationStatusId", cardActivationStatus.getId(),
            "cardId", cardActivationStatus.getCardId(),
            "userId", cardActivationStatus.getUserId(),
            "previousStatus", cardActivationStatus.getPreviousStatus().toString(),
            "newStatus", cardActivationStatus.getNewStatus().toString(),
            "activationMethod", cardActivationStatus.getActivationMethod(),
            "activationChannel", cardActivationStatus.getActivationChannel(),
            "attemptCount", cardActivationStatus.getAttemptCount(),
            "isFirstActivation", cardActivationStatus.getIsFirstActivation(),
            "location", cardActivationStatus.getLocation(),
            "eventType", "CARD_ACTIVATION_STATUS_ANALYTICS",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void processCardActivationStatusFallback(
            String cardId,
            String userId,
            String activationId,
            String previousStatus,
            String newStatus,
            String statusChangeReason,
            String activationMethod,
            String activationChannel,
            String deviceInfo,
            String ipAddress,
            String location,
            Integer attemptCount,
            Boolean isFirstActivation,
            Boolean requiresVerification,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for card activation status - cardId: {}, correlationId: {}, error: {}", 
            cardId, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("cardId", cardId);
        fallbackEvent.put("userId", userId);
        fallbackEvent.put("activationId", activationId);
        fallbackEvent.put("previousStatus", previousStatus);
        fallbackEvent.put("newStatus", newStatus);
        fallbackEvent.put("statusChangeReason", statusChangeReason);
        fallbackEvent.put("activationMethod", activationMethod);
        fallbackEvent.put("activationChannel", activationChannel);
        fallbackEvent.put("deviceInfo", deviceInfo);
        fallbackEvent.put("ipAddress", ipAddress);
        fallbackEvent.put("location", location);
        fallbackEvent.put("attemptCount", attemptCount);
        fallbackEvent.put("isFirstActivation", isFirstActivation);
        fallbackEvent.put("requiresVerification", requiresVerification);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("card-activation-status-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Card activation status message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
            topic, partition, offset, exceptionMessage);
        
        try {
            Map<String, Object> dltEvent = Map.of(
                "originalTopic", topic,
                "partition", partition,
                "offset", offset,
                "message", message,
                "error", exceptionMessage,
                "timestamp", Instant.now().toString(),
                "dltReason", "MAX_RETRIES_EXCEEDED"
            );
            
            kafkaTemplate.send("card-activation-status-processing-failures", dltEvent);
            
            notificationService.sendCriticalOperationalAlert(
                "Card Activation Status Processing Failed",
                String.format("CRITICAL: Failed to process card activation status after max retries. Error: %s", exceptionMessage),
                dltEvent
            );
            
        } catch (Exception e) {
            log.error("Failed to process DLT message: {}", e.getMessage(), e);
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        return processedEvents.containsKey(eventId);
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, String.valueOf(System.currentTimeMillis()));
        
        // Cleanup old entries
        processedEvents.entrySet().removeIf(entry -> {
            long timestamp = Long.parseLong(entry.getValue());
            return System.currentTimeMillis() - timestamp > IDEMPOTENCY_TTL_MS;
        });
    }
}