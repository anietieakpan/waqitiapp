package com.waqiti.payment.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.model.*;
import com.waqiti.payment.repository.SavedPaymentMethodRepository;
import com.waqiti.payment.service.*;
import com.waqiti.common.notification.service.NotificationService;
import com.waqiti.common.audit.service.AuditService;
import com.waqiti.common.metrics.service.MetricsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade Kafka consumer for saved payment method events
 * Handles card storage, PCI compliance, expiry detection, and automatic updates
 * 
 * Critical for: Card-on-file management, PCI compliance, customer experience
 * SLA: Must process card updates within 2 seconds for real-time validation
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SavedPaymentMethodEventsConsumer {

    private final SavedPaymentMethodRepository savedPaymentMethodRepository;
    private final PaymentMethodService paymentMethodService;
    private final PCIComplianceService pciComplianceService;
    private final TokenizationService tokenizationService;
    private final CardUpdateService cardUpdateService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final AlertingService alertingService;
    private final SecurityService securityService;
    private final UniversalDLQHandler dlqHandler;

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long SLA_THRESHOLD_MS = 2000; // 2 seconds
    private static final int EXPIRY_WARNING_DAYS = 30;
    private static final Set<String> PCI_SENSITIVE_FIELDS = Set.of(
        "cardNumber", "cvv", "pin", "fullTrack", "magneticStripe"
    );

    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(
        topics = {"saved-payment-method-events", "card-on-file-events"},
        groupId = "saved-payment-method-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CircuitBreaker(name = "saved-payment-method-processor", fallbackMethod = "handlePaymentMethodFailure")
    @Retry(name = "saved-payment-method-processor")
    public void processSavedPaymentMethodEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        long startTime = System.currentTimeMillis();
        
        log.info("Processing saved payment method event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        try {
            Map<String, Object> payload = event.getPayload();
            PaymentMethodEvent paymentMethodEvent = extractPaymentMethodEvent(payload);
            
            // Validate payment method event
            validatePaymentMethodEvent(paymentMethodEvent);
            
            // Check for duplicate processing
            if (isDuplicateEvent(paymentMethodEvent)) {
                log.warn("Duplicate payment method event detected: {}, skipping", 
                        paymentMethodEvent.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Validate PCI compliance requirements
            validatePCICompliance(paymentMethodEvent);
            
            // Process event based on type
            processEventByType(paymentMethodEvent);
            
            // Handle card-specific processing
            handleCardSpecificProcessing(paymentMethodEvent);
            
            // Update payment method status
            updatePaymentMethodStatus(paymentMethodEvent);
            
            // Handle expiry detection and warnings
            handleExpiryDetection(paymentMethodEvent);
            
            // Process automatic updates from networks
            handleAutomaticUpdates(paymentMethodEvent);
            
            // Send notifications
            sendPaymentMethodNotifications(paymentMethodEvent);
            
            // Record compliance audit trail
            recordComplianceAuditTrail(paymentMethodEvent);
            
            // Update metrics
            updatePaymentMethodMetrics(paymentMethodEvent, startTime);
            
            acknowledgment.acknowledge();
            
            log.info("Successfully processed payment method event: {} type: {} in {}ms", 
                    paymentMethodEvent.getEventId(), paymentMethodEvent.getEventType(),
                    System.currentTimeMillis() - startTime);
            
        } catch (PCIComplianceException e) {
            log.error("PCI compliance violation for event: {}", eventId, e);
            handlePCIComplianceError(event, e);
            acknowledgment.acknowledge();
            
        } catch (PaymentMethodValidationException e) {
            log.error("Payment method validation failed for event: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (TokenizationException e) {
            log.error("Tokenization error for event: {}", eventId, e);
            handleTokenizationError(event, e, acknowledgment);
            
        } catch (Exception e) {
            log.error("Error processing payment method event: topic={}, partition={}, offset={}, error={}",
                topic, partition, offset, e.getMessage(), e);

            handleProcessingError(event, e, acknowledgment);

            dlqHandler.handleFailedMessage(org.apache.kafka.clients.consumer.ConsumerRecord.class.cast(event), e)
                .thenAccept(result -> log.info("Message sent to DLQ: topic={}, offset={}, destination={}, category={}",
                        topic, offset, result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed - MESSAGE MAY BE LOST! " +
                            "topic={}, partition={}, offset={}, error={}",
                            topic, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Payment method event processing failed", e);
        }
    }

    private PaymentMethodEvent extractPaymentMethodEvent(Map<String, Object> payload) {
        return PaymentMethodEvent.builder()
            .eventId(extractString(payload, "eventId", UUID.randomUUID().toString()))
            .eventType(extractString(payload, "eventType", "unknown"))
            .customerId(extractString(payload, "customerId", null))
            .paymentMethodId(extractString(payload, "paymentMethodId", null))
            .tokenId(extractString(payload, "tokenId", null))
            .cardType(extractString(payload, "cardType", null))
            .lastFourDigits(extractString(payload, "lastFourDigits", null))
            .expiryMonth(extractInteger(payload, "expiryMonth", null))
            .expiryYear(extractInteger(payload, "expiryYear", null))
            .cardHolderName(extractString(payload, "cardHolderName", null))
            .billingAddress(extractMap(payload, "billingAddress"))
            .isDefault(extractBoolean(payload, "isDefault", false))
            .isActive(extractBoolean(payload, "isActive", true))
            .gatewayTokens(extractMap(payload, "gatewayTokens"))
            .metadata(extractMap(payload, "metadata"))
            .source(extractString(payload, "source", "CUSTOMER"))
            .ipAddress(extractString(payload, "ipAddress", null))
            .userAgent(extractString(payload, "userAgent", null))
            .timestamp(extractInstant(payload, "timestamp"))
            .build();
    }

    private void validatePaymentMethodEvent(PaymentMethodEvent event) {
        if (event.getEventId() == null || event.getEventId().isEmpty()) {
            throw new PaymentMethodValidationException("Event ID is required");
        }
        
        if (event.getEventType() == null || event.getEventType().isEmpty()) {
            throw new PaymentMethodValidationException("Event type is required");
        }
        
        if (event.getCustomerId() == null || event.getCustomerId().isEmpty()) {
            throw new PaymentMethodValidationException("Customer ID is required");
        }
        
        // Validate event type
        if (!isValidEventType(event.getEventType())) {
            throw new PaymentMethodValidationException("Invalid event type: " + event.getEventType());
        }
        
        // Validate customer exists
        if (!paymentMethodService.customerExists(event.getCustomerId())) {
            throw new PaymentMethodValidationException("Customer not found: " + event.getCustomerId());
        }
        
        // Validate card data if present
        if (event.getEventType().contains("CARD") && event.getCardType() != null) {
            validateCardData(event);
        }
    }

    private boolean isValidEventType(String eventType) {
        return Arrays.asList(
            "PAYMENT_METHOD_ADDED", "PAYMENT_METHOD_UPDATED", "PAYMENT_METHOD_DELETED",
            "CARD_ADDED", "CARD_UPDATED", "CARD_EXPIRED", "CARD_EXPIRING",
            "TOKEN_CREATED", "TOKEN_UPDATED", "TOKEN_EXPIRED",
            "NETWORK_UPDATE_RECEIVED", "AUTOMATIC_UPDATE_APPLIED",
            "PAYMENT_METHOD_VERIFIED", "PAYMENT_METHOD_FAILED_VERIFICATION"
        ).contains(eventType);
    }

    private void validateCardData(PaymentMethodEvent event) {
        if (event.getLastFourDigits() != null && event.getLastFourDigits().length() != 4) {
            throw new PaymentMethodValidationException("Invalid last four digits format");
        }
        
        if (event.getExpiryMonth() != null && (event.getExpiryMonth() < 1 || event.getExpiryMonth() > 12)) {
            throw new PaymentMethodValidationException("Invalid expiry month: " + event.getExpiryMonth());
        }
        
        if (event.getExpiryYear() != null && event.getExpiryYear() < LocalDate.now().getYear()) {
            log.warn("Card already expired for customer: {}", event.getCustomerId());
        }
    }

    private boolean isDuplicateEvent(PaymentMethodEvent event) {
        return savedPaymentMethodRepository.existsByEventIdAndTimestampAfter(
            event.getEventId(),
            Instant.now().minus(5, ChronoUnit.MINUTES)
        );
    }

    private void validatePCICompliance(PaymentMethodEvent event) {
        // Check if event contains PCI sensitive data
        Map<String, Object> metadata = event.getMetadata();
        for (String sensitiveField : PCI_SENSITIVE_FIELDS) {
            if (metadata.containsKey(sensitiveField)) {
                throw new PCIComplianceException(
                    "PCI sensitive data detected in event: " + sensitiveField);
            }
        }
        
        // Validate tokenization requirements
        if (event.getEventType().contains("ADDED") || event.getEventType().contains("UPDATED")) {
            if (event.getTokenId() == null || event.getTokenId().isEmpty()) {
                throw new PCIComplianceException(
                    "Payment method must be tokenized before storage");
            }
        }
        
        // Validate encryption requirements
        if (!pciComplianceService.isDataProperlyEncrypted(event)) {
            throw new PCIComplianceException(
                "Payment method data is not properly encrypted");
        }
    }

    private void processEventByType(PaymentMethodEvent event) {
        switch (event.getEventType()) {
            case "PAYMENT_METHOD_ADDED":
            case "CARD_ADDED":
                handlePaymentMethodAdded(event);
                break;
                
            case "PAYMENT_METHOD_UPDATED":
            case "CARD_UPDATED":
                handlePaymentMethodUpdated(event);
                break;
                
            case "PAYMENT_METHOD_DELETED":
                handlePaymentMethodDeleted(event);
                break;
                
            case "CARD_EXPIRED":
                handleCardExpired(event);
                break;
                
            case "CARD_EXPIRING":
                handleCardExpiring(event);
                break;
                
            case "TOKEN_CREATED":
                handleTokenCreated(event);
                break;
                
            case "TOKEN_UPDATED":
                handleTokenUpdated(event);
                break;
                
            case "TOKEN_EXPIRED":
                handleTokenExpired(event);
                break;
                
            case "NETWORK_UPDATE_RECEIVED":
                handleNetworkUpdateReceived(event);
                break;
                
            case "AUTOMATIC_UPDATE_APPLIED":
                handleAutomaticUpdateApplied(event);
                break;
                
            case "PAYMENT_METHOD_VERIFIED":
                handlePaymentMethodVerified(event);
                break;
                
            case "PAYMENT_METHOD_FAILED_VERIFICATION":
                handlePaymentMethodFailedVerification(event);
                break;
                
            default:
                handleUnknownEventType(event);
        }
    }

    private void handlePaymentMethodAdded(PaymentMethodEvent event) {
        // Create new saved payment method
        SavedPaymentMethod paymentMethod = SavedPaymentMethod.builder()
            .paymentMethodId(event.getPaymentMethodId() != null ? 
                event.getPaymentMethodId() : UUID.randomUUID().toString())
            .customerId(event.getCustomerId())
            .tokenId(event.getTokenId())
            .cardType(event.getCardType())
            .lastFourDigits(event.getLastFourDigits())
            .expiryMonth(event.getExpiryMonth())
            .expiryYear(event.getExpiryYear())
            .cardHolderName(event.getCardHolderName())
            .billingAddress(event.getBillingAddress())
            .isDefault(event.isDefault())
            .isActive(true)
            .isVerified(false)
            .gatewayTokens(event.getGatewayTokens())
            .createdAt(event.getTimestamp())
            .lastUsedAt(null)
            .metadata(event.getMetadata())
            .build();
        
        // Handle default payment method logic
        if (event.isDefault()) {
            unsetOtherDefaultMethods(event.getCustomerId());
        }
        
        // Save payment method
        SavedPaymentMethod saved = savedPaymentMethodRepository.save(paymentMethod);
        
        // Schedule verification if required
        if (requiresVerification(event)) {
            schedulePaymentMethodVerification(saved);
        }
        
        // Update customer payment methods count
        paymentMethodService.updateCustomerPaymentMethodsCount(event.getCustomerId());
        
        log.info("Payment method added for customer: {} method: {}", 
                event.getCustomerId(), saved.getPaymentMethodId());
    }

    private void handlePaymentMethodUpdated(PaymentMethodEvent event) {
        SavedPaymentMethod existing = findPaymentMethod(event);
        if (existing == null) {
            log.warn("Payment method not found for update: {}", event.getPaymentMethodId());
            return;
        }
        
        // Track changes for audit
        List<String> changes = new ArrayList<>();
        
        // Update fields
        if (event.getExpiryMonth() != null && !event.getExpiryMonth().equals(existing.getExpiryMonth())) {
            changes.add("expiryMonth: " + existing.getExpiryMonth() + " -> " + event.getExpiryMonth());
            existing.setExpiryMonth(event.getExpiryMonth());
        }
        
        if (event.getExpiryYear() != null && !event.getExpiryYear().equals(existing.getExpiryYear())) {
            changes.add("expiryYear: " + existing.getExpiryYear() + " -> " + event.getExpiryYear());
            existing.setExpiryYear(event.getExpiryYear());
        }
        
        if (event.getCardHolderName() != null && !event.getCardHolderName().equals(existing.getCardHolderName())) {
            changes.add("cardHolderName updated");
            existing.setCardHolderName(event.getCardHolderName());
        }
        
        if (event.getBillingAddress() != null && !event.getBillingAddress().equals(existing.getBillingAddress())) {
            changes.add("billingAddress updated");
            existing.setBillingAddress(event.getBillingAddress());
        }
        
        if (event.isDefault() != existing.isDefault()) {
            changes.add("isDefault: " + existing.isDefault() + " -> " + event.isDefault());
            existing.setDefault(event.isDefault());
            
            if (event.isDefault()) {
                unsetOtherDefaultMethods(event.getCustomerId());
            }
        }
        
        existing.setUpdatedAt(event.getTimestamp());
        existing.setMetadata(mergeMetadata(existing.getMetadata(), event.getMetadata()));
        
        // Save updated payment method
        savedPaymentMethodRepository.save(existing);
        
        // Audit changes
        if (!changes.isEmpty()) {
            auditService.auditPaymentMethodChanges(
                existing.getPaymentMethodId(),
                event.getCustomerId(),
                changes,
                event.getEventId()
            );
        }
        
        log.info("Payment method updated for customer: {} method: {} changes: {}", 
                event.getCustomerId(), existing.getPaymentMethodId(), changes.size());
    }

    private void handlePaymentMethodDeleted(PaymentMethodEvent event) {
        SavedPaymentMethod existing = findPaymentMethod(event);
        if (existing == null) {
            log.warn("Payment method not found for deletion: {}", event.getPaymentMethodId());
            return;
        }
        
        // Soft delete - mark as inactive
        existing.setActive(false);
        existing.setDeletedAt(event.getTimestamp());
        existing.setMetadata(mergeMetadata(existing.getMetadata(), 
            Map.of("deletionReason", event.getSource())));
        
        savedPaymentMethodRepository.save(existing);
        
        // If this was the default, set another as default
        if (existing.isDefault()) {
            setNewDefaultPaymentMethod(event.getCustomerId(), existing.getPaymentMethodId());
        }
        
        // Revoke gateway tokens
        revokeGatewayTokens(existing);
        
        // Update customer payment methods count
        paymentMethodService.updateCustomerPaymentMethodsCount(event.getCustomerId());
        
        log.info("Payment method deleted for customer: {} method: {}", 
                event.getCustomerId(), existing.getPaymentMethodId());
    }

    private void handleCardExpired(PaymentMethodEvent event) {
        SavedPaymentMethod existing = findPaymentMethod(event);
        if (existing == null) return;
        
        // Mark as expired
        existing.setActive(false);
        existing.setExpired(true);
        existing.setExpiredAt(event.getTimestamp());
        
        savedPaymentMethodRepository.save(existing);
        
        // Check for automatic update from networks
        checkForAutomaticCardUpdate(existing);
        
        // Notify customer of expired card
        notificationService.sendCardExpiredNotification(
            event.getCustomerId(),
            existing.getLastFourDigits(),
            existing.getCardType()
        );
        
        // If this was the default, notify about setting new default
        if (existing.isDefault()) {
            notificationService.sendDefaultCardExpiredNotification(event.getCustomerId());
        }
        
        log.info("Card expired for customer: {} method: {}", 
                event.getCustomerId(), existing.getPaymentMethodId());
    }

    private void handleCardExpiring(PaymentMethodEvent event) {
        SavedPaymentMethod existing = findPaymentMethod(event);
        if (existing == null) return;
        
        // Calculate days until expiry
        LocalDate expiryDate = LocalDate.of(existing.getExpiryYear(), existing.getExpiryMonth(), 1)
            .plusMonths(1).minusDays(1);
        long daysUntilExpiry = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
        
        // Send expiry warning
        notificationService.sendCardExpiringNotification(
            event.getCustomerId(),
            existing.getLastFourDigits(),
            existing.getCardType(),
            (int) daysUntilExpiry
        );
        
        // Update metadata with warning sent
        Map<String, Object> metadata = existing.getMetadata();
        metadata.put("expiryWarningSent", event.getTimestamp().toString());
        metadata.put("daysUntilExpiry", daysUntilExpiry);
        existing.setMetadata(metadata);
        
        savedPaymentMethodRepository.save(existing);
        
        log.info("Card expiring warning sent for customer: {} method: {} days: {}", 
                event.getCustomerId(), existing.getPaymentMethodId(), daysUntilExpiry);
    }

    private void handleTokenCreated(PaymentMethodEvent event) {
        // Update payment method with new token
        SavedPaymentMethod existing = findPaymentMethod(event);
        if (existing != null) {
            existing.setTokenId(event.getTokenId());
            existing.setTokenCreatedAt(event.getTimestamp());
            
            // Update gateway tokens
            Map<String, Object> gatewayTokens = existing.getGatewayTokens();
            gatewayTokens.putAll(event.getGatewayTokens());
            existing.setGatewayTokens(gatewayTokens);
            
            savedPaymentMethodRepository.save(existing);
        }
        
        // Record tokenization metrics
        metricsService.recordTokenizationEvent(
            event.getCustomerId(),
            event.getCardType(),
            "TOKEN_CREATED"
        );
        
        log.info("Token created for customer: {} method: {} token: {}", 
                event.getCustomerId(), event.getPaymentMethodId(), event.getTokenId());
    }

    private void handleTokenUpdated(PaymentMethodEvent event) {
        SavedPaymentMethod existing = findPaymentMethod(event);
        if (existing == null) return;
        
        // Update token information
        existing.setTokenId(event.getTokenId());
        existing.setTokenUpdatedAt(event.getTimestamp());
        existing.setGatewayTokens(event.getGatewayTokens());
        
        savedPaymentMethodRepository.save(existing);
        
        log.info("Token updated for customer: {} method: {} token: {}", 
                event.getCustomerId(), existing.getPaymentMethodId(), event.getTokenId());
    }

    private void handleTokenExpired(PaymentMethodEvent event) {
        SavedPaymentMethod existing = findPaymentMethod(event);
        if (existing == null) return;
        
        // Mark token as expired
        existing.setTokenExpired(true);
        existing.setTokenExpiredAt(event.getTimestamp());
        existing.setActive(false);
        
        savedPaymentMethodRepository.save(existing);
        
        // Attempt to re-tokenize
        scheduleReTokenization(existing);
        
        log.info("Token expired for customer: {} method: {}", 
                event.getCustomerId(), existing.getPaymentMethodId());
    }

    private void handleNetworkUpdateReceived(PaymentMethodEvent event) {
        SavedPaymentMethod existing = findPaymentMethod(event);
        if (existing == null) return;
        
        // Process network update (Account Updater service)
        NetworkUpdate update = NetworkUpdate.builder()
            .paymentMethodId(existing.getPaymentMethodId())
            .customerId(event.getCustomerId())
            .updateType(event.getMetadata().get("updateType").toString())
            .oldExpiryMonth(existing.getExpiryMonth())
            .oldExpiryYear(existing.getExpiryYear())
            .newExpiryMonth(event.getExpiryMonth())
            .newExpiryYear(event.getExpiryYear())
            .newLastFourDigits(event.getLastFourDigits())
            .networkSource(event.getMetadata().get("networkSource").toString())
            .updateReason(event.getMetadata().get("updateReason").toString())
            .receivedAt(event.getTimestamp())
            .applied(false)
            .build();
        
        // Store network update for review
        paymentMethodService.storeNetworkUpdate(update);
        
        // Auto-apply if configured and safe
        if (shouldAutoApplyNetworkUpdate(update)) {
            applyNetworkUpdate(existing, update);
        } else {
            // Queue for manual review
            paymentMethodService.queueForManualReview(update);
        }
        
        log.info("Network update received for customer: {} method: {} type: {}", 
                event.getCustomerId(), existing.getPaymentMethodId(), update.getUpdateType());
    }

    private void handleAutomaticUpdateApplied(PaymentMethodEvent event) {
        SavedPaymentMethod existing = findPaymentMethod(event);
        if (existing == null) return;
        
        // Apply automatic update
        existing.setExpiryMonth(event.getExpiryMonth());
        existing.setExpiryYear(event.getExpiryYear());
        if (event.getLastFourDigits() != null) {
            existing.setLastFourDigits(event.getLastFourDigits());
        }
        
        existing.setAutomaticallyUpdated(true);
        existing.setLastUpdateSource("NETWORK_UPDATE");
        existing.setUpdatedAt(event.getTimestamp());
        
        savedPaymentMethodRepository.save(existing);
        
        // Notify customer of automatic update
        notificationService.sendCardAutomaticUpdateNotification(
            event.getCustomerId(),
            existing.getLastFourDigits(),
            existing.getCardType()
        );
        
        log.info("Automatic update applied for customer: {} method: {}", 
                event.getCustomerId(), existing.getPaymentMethodId());
    }

    private void handlePaymentMethodVerified(PaymentMethodEvent event) {
        SavedPaymentMethod existing = findPaymentMethod(event);
        if (existing == null) return;
        
        existing.setVerified(true);
        existing.setVerifiedAt(event.getTimestamp());
        existing.setVerificationMethod(event.getMetadata().get("verificationMethod").toString());
        
        savedPaymentMethodRepository.save(existing);
        
        // Send verification success notification
        notificationService.sendPaymentMethodVerifiedNotification(
            event.getCustomerId(),
            existing.getLastFourDigits(),
            existing.getCardType()
        );
        
        log.info("Payment method verified for customer: {} method: {}", 
                event.getCustomerId(), existing.getPaymentMethodId());
    }

    private void handlePaymentMethodFailedVerification(PaymentMethodEvent event) {
        SavedPaymentMethod existing = findPaymentMethod(event);
        if (existing == null) return;
        
        existing.setVerified(false);
        existing.setVerificationFailedAt(event.getTimestamp());
        existing.setVerificationFailureReason(event.getMetadata().get("failureReason").toString());
        existing.setActive(false); // Deactivate unverified methods
        
        savedPaymentMethodRepository.save(existing);
        
        // Send verification failure notification
        notificationService.sendPaymentMethodVerificationFailedNotification(
            event.getCustomerId(),
            existing.getLastFourDigits(),
            existing.getCardType(),
            existing.getVerificationFailureReason()
        );
        
        log.info("Payment method verification failed for customer: {} method: {} reason: {}", 
                event.getCustomerId(), existing.getPaymentMethodId(), 
                existing.getVerificationFailureReason());
    }

    private void handleUnknownEventType(PaymentMethodEvent event) {
        log.warn("Unknown payment method event type: {} for customer: {}", 
                event.getEventType(), event.getCustomerId());
        
        // Store for manual review
        paymentMethodService.markForManualReview(
            event.getEventId(),
            "Unknown event type: " + event.getEventType()
        );
    }

    private SavedPaymentMethod findPaymentMethod(PaymentMethodEvent event) {
        if (event.getPaymentMethodId() != null) {
            return savedPaymentMethodRepository.findByPaymentMethodId(event.getPaymentMethodId())
                .orElse(null);
        }
        
        if (event.getTokenId() != null) {
            return savedPaymentMethodRepository.findByTokenId(event.getTokenId())
                .orElse(null);
        }
        
        return null;
    }

    private void handleCardSpecificProcessing(PaymentMethodEvent event) {
        if (!event.getEventType().contains("CARD")) return;
        
        // Update card network information
        if (event.getCardType() != null) {
            updateCardNetworkInfo(event);
        }
        
        // Check for fraud indicators
        checkForFraudIndicators(event);
        
        // Update BIN information
        if (event.getLastFourDigits() != null) {
            updateBINInformation(event);
        }
    }

    private void updateCardNetworkInfo(PaymentMethodEvent event) {
        CardNetworkInfo networkInfo = cardUpdateService.getCardNetworkInfo(event.getCardType());
        if (networkInfo != null) {
            Map<String, Object> metadata = event.getMetadata();
            metadata.put("networkInfo", networkInfo);
            metadata.put("supportedFeatures", networkInfo.getSupportedFeatures());
            metadata.put("updateServiceEnabled", networkInfo.isUpdateServiceEnabled());
        }
    }

    private void checkForFraudIndicators(PaymentMethodEvent event) {
        // Check for suspicious patterns
        if (event.getIpAddress() != null) {
            boolean isSuspiciousIP = securityService.isSuspiciousIP(event.getIpAddress());
            if (isSuspiciousIP) {
                alertingService.createSecurityAlert(
                    "SUSPICIOUS_IP_PAYMENT_METHOD",
                    "Payment method added from suspicious IP: " + event.getIpAddress(),
                    "MEDIUM"
                );
            }
        }
        
        // Check velocity (too many cards added recently)
        int recentCardsAdded = paymentMethodService.getRecentCardsAddedCount(
            event.getCustomerId(),
            Instant.now().minus(1, ChronoUnit.HOURS)
        );
        
        if (recentCardsAdded > 5) {
            alertingService.createSecurityAlert(
                "HIGH_VELOCITY_CARD_ADDITION",
                "High velocity card addition detected for customer: " + event.getCustomerId(),
                "HIGH"
            );
        }
    }

    private void updateBINInformation(PaymentMethodEvent event) {
        if (event.getLastFourDigits() == null) return;
        
        // Update BIN data for analytics
        CompletableFuture.runAsync(() -> {
            try {
                BINData binData = cardUpdateService.getBINData(event.getLastFourDigits());
                if (binData != null) {
                    paymentMethodService.updateBINData(
                        event.getPaymentMethodId(),
                        binData
                    );
                }
            } catch (Exception e) {
                log.warn("Failed to update BIN data for payment method: {}", 
                        event.getPaymentMethodId(), e);
            }
        });
    }

    private void updatePaymentMethodStatus(PaymentMethodEvent event) {
        // Update payment method usage statistics
        paymentMethodService.updateUsageStatistics(
            event.getCustomerId(),
            event.getEventType()
        );
        
        // Update customer payment profile
        paymentMethodService.updateCustomerPaymentProfile(event.getCustomerId());
    }

    private void handleExpiryDetection(PaymentMethodEvent event) {
        if (event.getExpiryMonth() == null || event.getExpiryYear() == null) return;
        
        LocalDate expiryDate = LocalDate.of(event.getExpiryYear(), event.getExpiryMonth(), 1)
            .plusMonths(1).minusDays(1);
        LocalDate now = LocalDate.now();
        
        // Check if card is already expired
        if (expiryDate.isBefore(now)) {
            triggerCardExpiredEvent(event);
            return;
        }
        
        // Check if card is expiring soon
        long daysUntilExpiry = ChronoUnit.DAYS.between(now, expiryDate);
        if (daysUntilExpiry <= EXPIRY_WARNING_DAYS) {
            triggerCardExpiringEvent(event, (int) daysUntilExpiry);
        }
    }

    private void triggerCardExpiredEvent(PaymentMethodEvent event) {
        PaymentMethodEvent expiredEvent = PaymentMethodEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("CARD_EXPIRED")
            .customerId(event.getCustomerId())
            .paymentMethodId(event.getPaymentMethodId())
            .tokenId(event.getTokenId())
            .cardType(event.getCardType())
            .lastFourDigits(event.getLastFourDigits())
            .source("SYSTEM")
            .timestamp(Instant.now())
            .build();
        
        // Process expired event
        handleCardExpired(expiredEvent);
    }

    private void triggerCardExpiringEvent(PaymentMethodEvent event, int daysUntilExpiry) {
        PaymentMethodEvent expiringEvent = PaymentMethodEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("CARD_EXPIRING")
            .customerId(event.getCustomerId())
            .paymentMethodId(event.getPaymentMethodId())
            .tokenId(event.getTokenId())
            .cardType(event.getCardType())
            .lastFourDigits(event.getLastFourDigits())
            .metadata(Map.of("daysUntilExpiry", daysUntilExpiry))
            .source("SYSTEM")
            .timestamp(Instant.now())
            .build();
        
        // Process expiring event
        handleCardExpiring(expiringEvent);
    }

    private void handleAutomaticUpdates(PaymentMethodEvent event) {
        if (!event.getEventType().equals("NETWORK_UPDATE_RECEIVED")) return;
        
        // Check if automatic updates are enabled for this customer
        if (!paymentMethodService.isAutomaticUpdatesEnabled(event.getCustomerId())) {
            log.info("Automatic updates disabled for customer: {}", event.getCustomerId());
            return;
        }
        
        // Process automatic update from card networks
        SavedPaymentMethod paymentMethod = findPaymentMethod(event);
        if (paymentMethod != null) {
            processAutomaticCardUpdate(paymentMethod, event);
        }
    }

    private void processAutomaticCardUpdate(SavedPaymentMethod paymentMethod, PaymentMethodEvent event) {
        // Validate the update
        if (!isValidAutomaticUpdate(event)) {
            log.warn("Invalid automatic update for payment method: {}", paymentMethod.getPaymentMethodId());
            return;
        }
        
        // Apply the update
        PaymentMethodEvent updateEvent = PaymentMethodEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("AUTOMATIC_UPDATE_APPLIED")
            .customerId(event.getCustomerId())
            .paymentMethodId(paymentMethod.getPaymentMethodId())
            .tokenId(paymentMethod.getTokenId())
            .expiryMonth(event.getExpiryMonth())
            .expiryYear(event.getExpiryYear())
            .lastFourDigits(event.getLastFourDigits())
            .metadata(Map.of(
                "originalUpdate", event.getEventId(),
                "updateSource", "NETWORK_AUTOMATIC"
            ))
            .source("NETWORK")
            .timestamp(Instant.now())
            .build();
        
        handleAutomaticUpdateApplied(updateEvent);
    }

    private boolean isValidAutomaticUpdate(PaymentMethodEvent event) {
        // Validate expiry date is in the future
        if (event.getExpiryMonth() != null && event.getExpiryYear() != null) {
            LocalDate newExpiryDate = LocalDate.of(event.getExpiryYear(), event.getExpiryMonth(), 1);
            if (newExpiryDate.isBefore(LocalDate.now())) {
                return false;
            }
        }
        
        // Validate last four digits if provided
        if (event.getLastFourDigits() != null && event.getLastFourDigits().length() != 4) {
            return false;
        }
        
        return true;
    }

    private void unsetOtherDefaultMethods(String customerId) {
        List<SavedPaymentMethod> existingDefaults = 
            savedPaymentMethodRepository.findByCustomerIdAndIsDefaultTrue(customerId);
        
        for (SavedPaymentMethod existing : existingDefaults) {
            existing.setDefault(false);
            savedPaymentMethodRepository.save(existing);
        }
    }

    private void setNewDefaultPaymentMethod(String customerId, String excludePaymentMethodId) {
        Optional<SavedPaymentMethod> newDefault = 
            savedPaymentMethodRepository.findFirstByCustomerIdAndIsActiveTrueAndPaymentMethodIdNot(
                customerId, excludePaymentMethodId);
        
        if (newDefault.isPresent()) {
            newDefault.get().setDefault(true);
            savedPaymentMethodRepository.save(newDefault.get());
            
            notificationService.sendNewDefaultPaymentMethodNotification(
                customerId,
                newDefault.get().getLastFourDigits(),
                newDefault.get().getCardType()
            );
        }
    }

    private boolean requiresVerification(PaymentMethodEvent event) {
        // Require verification for high-risk scenarios
        if (event.getIpAddress() != null && securityService.isHighRiskIP(event.getIpAddress())) {
            return true;
        }
        
        // Require verification for new customers
        if (paymentMethodService.isNewCustomer(event.getCustomerId())) {
            return true;
        }
        
        // Require verification for high-value cards
        if (event.getCardType() != null && isHighValueCardType(event.getCardType())) {
            return true;
        }
        
        return false;
    }

    private boolean isHighValueCardType(String cardType) {
        return Arrays.asList("AMEX", "DINERS", "CORPORATE").contains(cardType.toUpperCase());
    }

    private void schedulePaymentMethodVerification(SavedPaymentMethod paymentMethod) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(5000); // Wait 5 seconds before verification
                paymentMethodService.initiateVerification(paymentMethod.getPaymentMethodId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Payment method verification interrupted for: {}", 
                        paymentMethod.getPaymentMethodId());
            } catch (Exception e) {
                log.error("Failed to verify payment method: {}", 
                        paymentMethod.getPaymentMethodId(), e);
            }
        });
    }

    private void checkForAutomaticCardUpdate(SavedPaymentMethod paymentMethod) {
        // Check with card networks for updated information
        CompletableFuture.runAsync(() -> {
            try {
                cardUpdateService.requestAccountUpdaterInfo(
                    paymentMethod.getCustomerId(),
                    paymentMethod.getPaymentMethodId(),
                    paymentMethod.getCardType()
                );
            } catch (Exception e) {
                log.warn("Failed to request account updater info for: {}", 
                        paymentMethod.getPaymentMethodId(), e);
            }
        });
    }

    private boolean shouldAutoApplyNetworkUpdate(NetworkUpdate update) {
        // Auto-apply safe updates like expiry date changes
        if ("EXPIRY_UPDATE".equals(update.getUpdateType())) {
            return true;
        }
        
        // Don't auto-apply PAN changes
        if ("PAN_UPDATE".equals(update.getUpdateType())) {
            return false;
        }
        
        return false;
    }

    private void applyNetworkUpdate(SavedPaymentMethod paymentMethod, NetworkUpdate update) {
        PaymentMethodEvent updateEvent = PaymentMethodEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("AUTOMATIC_UPDATE_APPLIED")
            .customerId(paymentMethod.getCustomerId())
            .paymentMethodId(paymentMethod.getPaymentMethodId())
            .expiryMonth(update.getNewExpiryMonth())
            .expiryYear(update.getNewExpiryYear())
            .lastFourDigits(update.getNewLastFourDigits())
            .metadata(Map.of(
                "networkUpdate", update.getUpdateId(),
                "updateType", update.getUpdateType(),
                "networkSource", update.getNetworkSource()
            ))
            .source("NETWORK")
            .timestamp(Instant.now())
            .build();
        
        handleAutomaticUpdateApplied(updateEvent);
        
        // Mark network update as applied
        update.setApplied(true);
        update.setAppliedAt(Instant.now());
        paymentMethodService.updateNetworkUpdate(update);
    }

    private void scheduleReTokenization(SavedPaymentMethod paymentMethod) {
        CompletableFuture.runAsync(() -> {
            try {
                tokenizationService.reTokenizePaymentMethod(
                    paymentMethod.getCustomerId(),
                    paymentMethod.getPaymentMethodId()
                );
            } catch (Exception e) {
                log.error("Failed to re-tokenize payment method: {}", 
                        paymentMethod.getPaymentMethodId(), e);
                
                // Mark as requiring manual intervention
                paymentMethod.setRequiresManualIntervention(true);
                savedPaymentMethodRepository.save(paymentMethod);
            }
        });
    }

    private void revokeGatewayTokens(SavedPaymentMethod paymentMethod) {
        Map<String, Object> gatewayTokens = paymentMethod.getGatewayTokens();
        
        for (Map.Entry<String, Object> entry : gatewayTokens.entrySet()) {
            String gateway = entry.getKey();
            String token = entry.getValue().toString();
            
            CompletableFuture.runAsync(() -> {
                try {
                    paymentMethodService.revokeGatewayToken(gateway, token);
                } catch (Exception e) {
                    log.warn("Failed to revoke token from gateway: {} token: {}", gateway, token, e);
                }
            });
        }
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> existing, Map<String, Object> newData) {
        Map<String, Object> merged = new HashMap<>(existing);
        merged.putAll(newData);
        return merged;
    }

    private void sendPaymentMethodNotifications(PaymentMethodEvent event) {
        // Send notifications based on event type
        switch (event.getEventType()) {
            case "PAYMENT_METHOD_ADDED":
            case "CARD_ADDED":
                if (shouldNotifyForAddition(event)) {
                    notificationService.sendPaymentMethodAddedNotification(
                        event.getCustomerId(),
                        event.getLastFourDigits(),
                        event.getCardType()
                    );
                }
                break;
                
            case "PAYMENT_METHOD_DELETED":
                notificationService.sendPaymentMethodDeletedNotification(
                    event.getCustomerId(),
                    event.getLastFourDigits(),
                    event.getCardType()
                );
                break;
        }
    }

    private boolean shouldNotifyForAddition(PaymentMethodEvent event) {
        // Don't notify for system-generated additions
        if ("SYSTEM".equals(event.getSource())) {
            return false;
        }
        
        // Check customer notification preferences
        return paymentMethodService.shouldNotifyCustomer(
            event.getCustomerId(),
            "PAYMENT_METHOD_ADDED"
        );
    }

    private void recordComplianceAuditTrail(PaymentMethodEvent event) {
        auditService.auditPaymentMethodEvent(
            event.getEventId(),
            event.getEventType(),
            event.getCustomerId(),
            event.getPaymentMethodId(),
            event.getSource(),
            event.getIpAddress(),
            event.getUserAgent()
        );
        
        // Record PCI compliance audit
        pciComplianceService.recordComplianceEvent(
            event.getCustomerId(),
            event.getEventType(),
            event.getTimestamp()
        );
    }

    private void updatePaymentMethodMetrics(PaymentMethodEvent event, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordPaymentMethodMetrics(
            event.getEventType(),
            event.getCardType(),
            processingTime,
            processingTime <= SLA_THRESHOLD_MS
        );
        
        // Record customer metrics
        metricsService.recordCustomerPaymentMethodActivity(
            event.getCustomerId(),
            event.getEventType()
        );
    }

    // Error handling methods
    private void handlePCIComplianceError(GenericKafkaEvent event, PCIComplianceException e) {
        securityService.recordPCIViolation(
            event.getEventId(),
            e.getMessage(),
            event.getPayload()
        );
        
        alertingService.createCriticalAlert(
            "PCI_COMPLIANCE_VIOLATION",
            "PCI compliance violation detected: " + e.getMessage()
        );
        
        auditService.auditSecurityViolation(
            "PCI_COMPLIANCE_VIOLATION",
            event.getEventId(),
            e.getMessage()
        );
    }

    private void handleValidationError(GenericKafkaEvent event, PaymentMethodValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        
        kafkaTemplate.send("payment-method-validation-errors", event);
    }

    private void handleTokenizationError(GenericKafkaEvent event, TokenizationException e, 
                                       Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying tokenization for event {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            // Don't acknowledge - let retry mechanism handle it
            return;
        } else {
            log.error("Max retries exceeded for tokenization event {}, sending to DLQ", eventId);
            sendPaymentMethodToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            log.warn("Retrying payment method event {} (attempt {})", eventId, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            // Don't acknowledge - let retry mechanism handle it
            return;
        } else {
            log.error("Max retries exceeded for payment method event {}, sending to DLQ", eventId);
            sendPaymentMethodToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendPaymentMethodToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "saved-payment-method-events");
        
        kafkaTemplate.send("saved-payment-method-events.DLQ", event);
        
        alertingService.createDLQAlert(
            "saved-payment-method-events",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handlePaymentMethodFailure(GenericKafkaEvent event, String topic, int partition,
                                         long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for payment method processing: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "Payment Method Processing Circuit Breaker Open",
            "Payment method processing is failing. Customer card management may be impacted."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private Integer extractInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private Boolean extractBoolean(Map<String, Object> map, String key, Boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return Instant.now();
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    // Custom exceptions
    public static class PaymentMethodValidationException extends RuntimeException {
        public PaymentMethodValidationException(String message) {
            super(message);
        }
    }

    public static class PCIComplianceException extends RuntimeException {
        public PCIComplianceException(String message) {
            super(message);
        }
    }

    public static class TokenizationException extends RuntimeException {
        public TokenizationException(String message) {
            super(message);
        }
    }
}