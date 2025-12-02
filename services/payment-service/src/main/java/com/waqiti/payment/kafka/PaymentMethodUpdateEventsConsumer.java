package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentMethodUpdateEvent;
import com.waqiti.common.events.CardExpiryEvent;
import com.waqiti.payment.domain.SavedPaymentMethod;
import com.waqiti.payment.domain.PaymentMethodStatus;
import com.waqiti.payment.repository.SavedPaymentMethodRepository;
import com.waqiti.payment.service.PaymentMethodUpdateService;
import com.waqiti.payment.service.CardExpiryService;
import com.waqiti.payment.exception.PaymentMethodException;
import com.waqiti.payment.metrics.PaymentMethodMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.YearMonth;
import java.util.*;

/**
 * CRITICAL Consumer for Payment Method Update Events
 * 
 * Handles saved payment method lifecycle including:
 * - Card expiry detection and handling
 * - Automatic payment method updates
 * - Customer notification for expiring cards
 * - Token refresh and re-tokenization
 * - Payment method validation and verification
 * - Account updater service integration
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentMethodUpdateEventsConsumer {
    
    private final SavedPaymentMethodRepository paymentMethodRepository;
    private final PaymentMethodUpdateService updateService;
    private final CardExpiryService expiryService;
    private final PaymentMethodMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = "payment-method-update-events",
        groupId = "payment-method-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handlePaymentMethodUpdateEvent(
            @Payload PaymentMethodUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("pm-update-%s-p%d-o%d", 
            event.getPaymentMethodId(), partition, offset);
        
        log.info("Processing payment method update: methodId={}, updateType={}, correlation={}",
            event.getPaymentMethodId(), event.getUpdateType(), correlationId);
        
        try {
            securityContext.validateFinancialOperation(event.getPaymentMethodId(), "PAYMENT_METHOD_UPDATE");
            validateUpdateEvent(event);
            
            switch (event.getUpdateType()) {
                case CARD_EXPIRY_CHECK:
                    processCardExpiryCheck(event, correlationId);
                    break;
                case AUTOMATIC_UPDATE:
                    processAutomaticUpdate(event, correlationId);
                    break;
                case MANUAL_UPDATE:
                    processManualUpdate(event, correlationId);
                    break;
                case EXPIRY_NOTIFICATION:
                    processExpiryNotification(event, correlationId);
                    break;
                case METHOD_VERIFICATION:
                    processMethodVerification(event, correlationId);
                    break;
                case TOKEN_REFRESH:
                    processTokenRefresh(event, correlationId);
                    break;
                default:
                    log.warn("Unknown update type: {}", event.getUpdateType());
                    break;
            }
            
            auditService.logFinancialEvent(
                "PAYMENT_METHOD_UPDATE_PROCESSED",
                event.getPaymentMethodId(),
                Map.of(
                    "updateType", event.getUpdateType(),
                    "customerId", event.getCustomerId(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process payment method update: methodId={}, error={}",
                event.getPaymentMethodId(), e.getMessage(), e);
            handleUpdateEventError(event, e, correlationId);
            acknowledgment.acknowledge();
        }
    }
    
    private void processCardExpiryCheck(PaymentMethodUpdateEvent event, String correlationId) {
        SavedPaymentMethod paymentMethod = getPaymentMethodById(event.getPaymentMethodId());
        
        log.info("Checking card expiry: methodId={}, currentExpiry={}/{}", 
            paymentMethod.getId(), paymentMethod.getExpiryMonth(), paymentMethod.getExpiryYear());
        
        YearMonth expiryDate = YearMonth.of(paymentMethod.getExpiryYear(), paymentMethod.getExpiryMonth());
        YearMonth currentMonth = YearMonth.now();
        
        if (expiryDate.isBefore(currentMonth.plusMonths(3))) {
            // Card expires within 3 months
            if (expiryDate.isBefore(currentMonth)) {
                // Already expired
                handleExpiredCard(paymentMethod, correlationId);
            } else {
                // Expiring soon
                handleExpiringCard(paymentMethod, expiryDate, correlationId);
            }
        }
        
        paymentMethod.setLastExpiryCheckAt(LocalDateTime.now());
        paymentMethodRepository.save(paymentMethod);
        
        metricsService.recordExpiryCheck(paymentMethod.getCardBrand());
    }
    
    private void processAutomaticUpdate(PaymentMethodUpdateEvent event, String correlationId) {
        SavedPaymentMethod paymentMethod = getPaymentMethodById(event.getPaymentMethodId());
        
        log.info("Processing automatic update: methodId={}, newExpiry={}/{}", 
            paymentMethod.getId(), event.getNewExpiryMonth(), event.getNewExpiryYear());
        
        // Update payment method with new details
        if (event.getNewExpiryMonth() != null && event.getNewExpiryYear() != null) {
            paymentMethod.setExpiryMonth(event.getNewExpiryMonth());
            paymentMethod.setExpiryYear(event.getNewExpiryYear());
        }
        
        if (event.getNewLastFourDigits() != null) {
            paymentMethod.setLastFourDigits(event.getNewLastFourDigits());
        }
        
        paymentMethod.setUpdatedAt(LocalDateTime.now());
        paymentMethod.setAutoUpdated(true);
        paymentMethod.setUpdateSource("ACCOUNT_UPDATER_SERVICE");
        paymentMethodRepository.save(paymentMethod);
        
        // Notify customer of automatic update
        sendAutomaticUpdateNotification(paymentMethod);
        
        metricsService.recordAutomaticUpdate(paymentMethod.getCardBrand());
        
        log.info("Automatic update completed: methodId={}", paymentMethod.getId());
    }
    
    private void processManualUpdate(PaymentMethodUpdateEvent event, String correlationId) {
        SavedPaymentMethod paymentMethod = getPaymentMethodById(event.getPaymentMethodId());
        
        log.info("Processing manual update: methodId={}", paymentMethod.getId());
        
        // Apply manual updates
        boolean updated = false;
        
        if (event.getNewExpiryMonth() != null && event.getNewExpiryYear() != null) {
            paymentMethod.setExpiryMonth(event.getNewExpiryMonth());
            paymentMethod.setExpiryYear(event.getNewExpiryYear());
            updated = true;
        }
        
        if (event.getNewLastFourDigits() != null) {
            paymentMethod.setLastFourDigits(event.getNewLastFourDigits());
            updated = true;
        }
        
        if (event.getNewCardholderName() != null) {
            paymentMethod.setCardholderName(event.getNewCardholderName());
            updated = true;
        }
        
        if (updated) {
            paymentMethod.setUpdatedAt(LocalDateTime.now());
            paymentMethod.setAutoUpdated(false);
            paymentMethod.setUpdateSource("MANUAL");
            paymentMethodRepository.save(paymentMethod);
            
            // Send confirmation
            sendManualUpdateConfirmation(paymentMethod);
            
            metricsService.recordManualUpdate(paymentMethod.getCardBrand());
        }
        
        log.info("Manual update completed: methodId={}, updated={}", paymentMethod.getId(), updated);
    }
    
    private void processExpiryNotification(PaymentMethodUpdateEvent event, String correlationId) {
        SavedPaymentMethod paymentMethod = getPaymentMethodById(event.getPaymentMethodId());
        
        log.info("Sending expiry notification: methodId={}", paymentMethod.getId());
        
        YearMonth expiryDate = YearMonth.of(paymentMethod.getExpiryYear(), paymentMethod.getExpiryMonth());
        long monthsUntilExpiry = YearMonth.now().until(expiryDate, java.time.temporal.ChronoUnit.MONTHS);
        
        String notificationMessage;
        NotificationService.Priority priority;
        
        if (monthsUntilExpiry <= 0) {
            notificationMessage = String.format(
                "Your payment method ending in %s has expired. Please update your payment information.",
                paymentMethod.getLastFourDigits()
            );
            priority = NotificationService.Priority.HIGH;
        } else if (monthsUntilExpiry == 1) {
            notificationMessage = String.format(
                "Your payment method ending in %s expires this month. Please update your payment information.",
                paymentMethod.getLastFourDigits()
            );
            priority = NotificationService.Priority.HIGH;
        } else {
            notificationMessage = String.format(
                "Your payment method ending in %s expires in %d months. Please update your payment information.",
                paymentMethod.getLastFourDigits(), monthsUntilExpiry
            );
            priority = NotificationService.Priority.MEDIUM;
        }
        
        notificationService.sendCustomerNotification(
            paymentMethod.getCustomerId(),
            "Payment Method Expiring",
            notificationMessage,
            priority
        );
        
        paymentMethod.setLastNotificationAt(LocalDateTime.now());
        paymentMethodRepository.save(paymentMethod);
        
        metricsService.recordExpiryNotification(paymentMethod.getCardBrand());
    }
    
    private void processMethodVerification(PaymentMethodUpdateEvent event, String correlationId) {
        SavedPaymentMethod paymentMethod = getPaymentMethodById(event.getPaymentMethodId());
        
        log.info("Verifying payment method: methodId={}", paymentMethod.getId());
        
        try {
            boolean verificationResult = updateService.verifyPaymentMethod(
                paymentMethod.getId(), 
                correlationId
            );
            
            paymentMethod.setVerified(verificationResult);
            paymentMethod.setLastVerificationAt(LocalDateTime.now());
            
            if (!verificationResult) {
                paymentMethod.setStatus(PaymentMethodStatus.VERIFICATION_FAILED);
                paymentMethod.setVerificationFailureReason(event.getVerificationFailureReason());
                
                // Notify customer of verification failure
                sendVerificationFailureNotification(paymentMethod);
            }
            
            paymentMethodRepository.save(paymentMethod);
            
            metricsService.recordVerification(paymentMethod.getCardBrand(), verificationResult);
            
            log.info("Payment method verification completed: methodId={}, result={}", 
                paymentMethod.getId(), verificationResult);
            
        } catch (Exception e) {
            log.error("Payment method verification failed: methodId={}, error={}",
                paymentMethod.getId(), e.getMessage(), e);
            
            paymentMethod.setStatus(PaymentMethodStatus.VERIFICATION_ERROR);
            paymentMethod.setVerificationFailureReason(e.getMessage());
            paymentMethodRepository.save(paymentMethod);
        }
    }
    
    private void processTokenRefresh(PaymentMethodUpdateEvent event, String correlationId) {
        SavedPaymentMethod paymentMethod = getPaymentMethodById(event.getPaymentMethodId());
        
        log.info("Refreshing token: methodId={}, currentToken={}", 
            paymentMethod.getId(), paymentMethod.getTokenId());
        
        try {
            String newTokenId = updateService.refreshPaymentMethodToken(
                paymentMethod.getId(), 
                correlationId
            );
            
            String oldTokenId = paymentMethod.getTokenId();
            paymentMethod.setTokenId(newTokenId);
            paymentMethod.setTokenRefreshedAt(LocalDateTime.now());
            paymentMethodRepository.save(paymentMethod);
            
            // Revoke old token
            if (oldTokenId != null) {
                revokeOldToken(oldTokenId, correlationId);
            }
            
            metricsService.recordTokenRefresh(paymentMethod.getCardBrand());
            
            log.info("Token refresh completed: methodId={}, oldToken={}, newToken={}", 
                paymentMethod.getId(), oldTokenId, newTokenId);
            
        } catch (Exception e) {
            log.error("Token refresh failed: methodId={}, error={}",
                paymentMethod.getId(), e.getMessage(), e);
            
            paymentMethod.setTokenRefreshFailureReason(e.getMessage());
            paymentMethodRepository.save(paymentMethod);
        }
    }
    
    @Scheduled(cron = "0 0 8 * * ?") // Daily at 8 AM
    public void checkExpiringPaymentMethods() {
        log.info("Checking for expiring payment methods...");
        
        YearMonth threeMonthsFromNow = YearMonth.now().plusMonths(3);
        List<SavedPaymentMethod> expiringMethods = paymentMethodRepository
            .findExpiringBefore(threeMonthsFromNow.getYear(), threeMonthsFromNow.getMonthValue());
        
        for (SavedPaymentMethod method : expiringMethods) {
            try {
                PaymentMethodUpdateEvent expiryEvent = PaymentMethodUpdateEvent.builder()
                    .paymentMethodId(method.getId())
                    .customerId(method.getCustomerId())
                    .updateType("CARD_EXPIRY_CHECK")
                    .timestamp(Instant.now())
                    .build();
                
                kafkaTemplate.send("payment-method-update-events", expiryEvent);
                
            } catch (Exception e) {
                log.error("Failed to process expiring payment method: methodId={}, error={}",
                    method.getId(), e.getMessage());
            }
        }
        
        if (!expiringMethods.isEmpty()) {
            log.info("Processed {} expiring payment methods", expiringMethods.size());
        }
    }
    
    private void handleExpiredCard(SavedPaymentMethod paymentMethod, String correlationId) {
        log.warn("Card has expired: methodId={}", paymentMethod.getId());
        
        paymentMethod.setStatus(PaymentMethodStatus.EXPIRED);
        paymentMethod.setExpiredAt(LocalDateTime.now());
        paymentMethodRepository.save(paymentMethod);
        
        // Try automatic update first
        if (expiryService.canAttemptAutomaticUpdate(paymentMethod)) {
            requestAutomaticUpdate(paymentMethod, correlationId);
        } else {
            // Send urgent notification to customer
            sendExpiredCardNotification(paymentMethod);
        }
        
        metricsService.recordCardExpired(paymentMethod.getCardBrand());
    }
    
    private void handleExpiringCard(SavedPaymentMethod paymentMethod, YearMonth expiryDate, 
            String correlationId) {
        
        long monthsUntilExpiry = YearMonth.now().until(expiryDate, java.time.temporal.ChronoUnit.MONTHS);
        
        log.info("Card expiring soon: methodId={}, monthsLeft={}", 
            paymentMethod.getId(), monthsUntilExpiry);
        
        // Try automatic update for cards expiring within 1 month
        if (monthsUntilExpiry <= 1 && expiryService.canAttemptAutomaticUpdate(paymentMethod)) {
            requestAutomaticUpdate(paymentMethod, correlationId);
        }
        
        // Send notification based on expiry timeline
        if (shouldSendExpiryNotification(paymentMethod, monthsUntilExpiry)) {
            sendExpiryNotification(paymentMethod, monthsUntilExpiry);
        }
        
        metricsService.recordCardExpiring(paymentMethod.getCardBrand(), (int) monthsUntilExpiry);
    }
    
    private void requestAutomaticUpdate(SavedPaymentMethod paymentMethod, String correlationId) {
        log.info("Requesting automatic update: methodId={}", paymentMethod.getId());
        
        PaymentMethodUpdateEvent updateEvent = PaymentMethodUpdateEvent.builder()
            .paymentMethodId(paymentMethod.getId())
            .customerId(paymentMethod.getCustomerId())
            .updateType("AUTOMATIC_UPDATE")
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("payment-method-update-events", updateEvent);
    }
    
    private boolean shouldSendExpiryNotification(SavedPaymentMethod paymentMethod, long monthsUntilExpiry) {
        LocalDateTime lastNotification = paymentMethod.getLastNotificationAt();
        
        // Send notification if:
        // 1. Never sent before, OR
        // 2. Last notification was more than 30 days ago for 3+ month expiry, OR
        // 3. Last notification was more than 7 days ago for 1-2 month expiry, OR
        // 4. Last notification was more than 1 day ago for current month expiry
        
        if (lastNotification == null) {
            return true;
        }
        
        LocalDateTime threshold;
        if (monthsUntilExpiry >= 3) {
            threshold = LocalDateTime.now().minusDays(30);
        } else if (monthsUntilExpiry >= 1) {
            threshold = LocalDateTime.now().minusDays(7);
        } else {
            threshold = LocalDateTime.now().minusDays(1);
        }
        
        return lastNotification.isBefore(threshold);
    }
    
    private SavedPaymentMethod getPaymentMethodById(String paymentMethodId) {
        return paymentMethodRepository.findById(paymentMethodId)
            .orElseThrow(() -> new PaymentMethodException(
                "Payment method not found: " + paymentMethodId));
    }
    
    private void validateUpdateEvent(PaymentMethodUpdateEvent event) {
        if (event.getPaymentMethodId() == null || event.getPaymentMethodId().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment method ID is required");
        }
        
        if (event.getUpdateType() == null || event.getUpdateType().trim().isEmpty()) {
            throw new IllegalArgumentException("Update type is required");
        }
        
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }
    }
    
    private void sendAutomaticUpdateNotification(SavedPaymentMethod paymentMethod) {
        notificationService.sendCustomerNotification(
            paymentMethod.getCustomerId(),
            "Payment Method Updated",
            String.format("Your payment method ending in %s has been automatically updated with new expiry information.",
                paymentMethod.getLastFourDigits()),
            NotificationService.Priority.MEDIUM
        );
    }
    
    private void sendManualUpdateConfirmation(SavedPaymentMethod paymentMethod) {
        notificationService.sendCustomerNotification(
            paymentMethod.getCustomerId(),
            "Payment Method Updated",
            String.format("Your payment method ending in %s has been successfully updated.",
                paymentMethod.getLastFourDigits()),
            NotificationService.Priority.LOW
        );
    }
    
    private void sendVerificationFailureNotification(SavedPaymentMethod paymentMethod) {
        notificationService.sendCustomerNotification(
            paymentMethod.getCustomerId(),
            "Payment Method Verification Failed",
            String.format("We couldn't verify your payment method ending in %s. Please update your payment information.",
                paymentMethod.getLastFourDigits()),
            NotificationService.Priority.HIGH
        );
    }
    
    private void sendExpiredCardNotification(SavedPaymentMethod paymentMethod) {
        notificationService.sendCustomerNotification(
            paymentMethod.getCustomerId(),
            "Payment Method Expired",
            String.format("Your payment method ending in %s has expired. Please add a new payment method to continue using our services.",
                paymentMethod.getLastFourDigits()),
            NotificationService.Priority.HIGH
        );
    }
    
    private void sendExpiryNotification(SavedPaymentMethod paymentMethod, long monthsUntilExpiry) {
        PaymentMethodUpdateEvent notificationEvent = PaymentMethodUpdateEvent.builder()
            .paymentMethodId(paymentMethod.getId())
            .customerId(paymentMethod.getCustomerId())
            .updateType("EXPIRY_NOTIFICATION")
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("payment-method-update-events", notificationEvent);
    }
    
    private void revokeOldToken(String oldTokenId, String correlationId) {
        try {
            kafkaTemplate.send("payment-tokenization-events", Map.of(
                "tokenId", oldTokenId,
                "tokenAction", "REVOKE_TOKEN",
                "revocationReason", "TOKEN_REFRESH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } catch (Exception e) {
            log.error("Failed to revoke old token: tokenId={}, error={}",
                oldTokenId, e.getMessage());
        }
    }
    
    private void handleUpdateEventError(PaymentMethodUpdateEvent event, Exception error, String correlationId) {
        Map<String, Object> dlqPayload = Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("payment-method-update-events-dlq", dlqPayload);
        
        notificationService.sendOperationalAlert(
            "Payment Method Update Failed",
            String.format("Failed to process payment method update: %s", error.getMessage()),
            NotificationService.Priority.HIGH
        );
        
        metricsService.incrementUpdateEventError(event.getUpdateType());
    }
}