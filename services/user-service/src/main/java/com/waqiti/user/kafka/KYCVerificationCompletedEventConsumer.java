package com.waqiti.user.kafka;

import com.waqiti.common.events.KYCVerificationCompletedEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.user.domain.User;
import com.waqiti.user.domain.KYCStatus;
import com.waqiti.user.domain.VerificationLevel;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.AccountLimitService;
import com.waqiti.user.service.UserAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FIX: Consumer for KYCVerificationCompletedEvent
 * This was missing and causing user KYC status to not be updated
 * 
 * Responsibilities:
 * - Update user KYC status based on verification results
 * - Adjust account limits based on verification level
 * - Unlock restricted features for verified users
 * - Send notifications to users
 * - Audit KYC status changes
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KYCVerificationCompletedEventConsumer {
    
    private final UserService userService;
    private final AccountLimitService limitService;
    private final UserAuditService auditService;
    private final UniversalDLQHandler dlqHandler;

    /**
     * Process KYC verification completed events
     *
     * CRITICAL: This updates user account status and unlocks features
     * based on KYC verification level
     */
    @KafkaListener(
        topics = "kyc-verification-completed-events",
        groupId = "user-service-kyc-completed-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"
    )
    @Transactional
    public void handleKYCVerificationCompleted(
            ConsumerRecord<String, KYCVerificationCompletedEvent> record,
            @Payload KYCVerificationCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("kyc-completed-%s-p%d-o%d",
            event.getUserId(), partition, offset);
        
        log.info("Processing KYC verification completed event: userId={}, status={}, level={}, correlation={}",
            event.getUserId(), event.getVerificationStatus(), event.getVerificationLevel(), correlationId);
        
        try {
            // Check for duplicate processing
            if (userService.isEventProcessed(event.getEventId())) {
                log.debug("Event already processed: {}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Get user
            User user = userService.findById(UUID.fromString(event.getUserId()))
                .orElseThrow(() -> new IllegalStateException("User not found: " + event.getUserId()));
            
            // Store previous status for audit
            KYCStatus previousStatus = user.getKycStatus();
            VerificationLevel previousLevel = user.getVerificationLevel();
            
            // Update user KYC status
            updateUserKYCStatus(user, event);
            
            // Update account limits based on verification level
            updateAccountLimits(user, event);
            
            // Unlock features based on verification level
            unlockFeatures(user, event);
            
            // Save user
            user = userService.save(user);
            
            // Audit the KYC status change
            auditKYCStatusChange(user, previousStatus, previousLevel, event);
            
            // Send notification to user
            sendUserNotification(user, event);
            
            // Mark event as processed
            userService.markEventProcessed(event.getEventId());
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed KYC verification completed event: userId={}, newStatus={}, newLevel={}",
                event.getUserId(), user.getKycStatus(), user.getVerificationLevel());
            
        } catch (Exception e) {
            log.error("Error processing event: topic={}, partition={}, offset={}, error={}",
                    topic, partition, offset, e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Sent to DLQ: {}", result.getDestinationTopic()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ failed", dlqError);
                    return null;
                });

            throw new RuntimeException("Processing failed", e);
        }
    }
    
    /**
     * Update user KYC status based on verification result
     */
    private void updateUserKYCStatus(User user, KYCVerificationCompletedEvent event) {
        // Map verification status to KYC status
        KYCStatus newStatus;
        switch (event.getVerificationStatus()) {
            case "VERIFIED":
                newStatus = KYCStatus.VERIFIED;
                break;
            case "REJECTED":
                newStatus = KYCStatus.REJECTED;
                break;
            case "PENDING_REVIEW":
                newStatus = KYCStatus.PENDING_REVIEW;
                break;
            case "REQUIRES_ADDITIONAL_INFO":
                newStatus = KYCStatus.PENDING;
                break;
            default:
                newStatus = KYCStatus.PENDING;
        }
        
        user.setKycStatus(newStatus);
        
        // Set verification level
        VerificationLevel level = mapVerificationLevel(event.getVerificationLevel());
        user.setVerificationLevel(level);
        
        // Set verification scores
        user.setKycScore(event.getVerificationScore());
        user.setKycVerifiedAt(LocalDateTime.now());
        user.setKycVerificationId(UUID.fromString(event.getVerificationId()));
        
        // Set rejection reason if applicable
        if (newStatus == KYCStatus.REJECTED) {
            user.setKycRejectionReason(event.getRejectionReason());
        }
        
        log.info("Updated user KYC status: userId={}, status={}, level={}, score={}",
            user.getId(), newStatus, level, event.getVerificationScore());
    }
    
    /**
     * Update account limits based on verification level
     */
    private void updateAccountLimits(User user, KYCVerificationCompletedEvent event) {
        VerificationLevel level = mapVerificationLevel(event.getVerificationLevel());
        
        BigDecimal dailyLimit;
        BigDecimal monthlyLimit;
        BigDecimal transactionLimit;
        
        switch (level) {
            case BASIC:
                dailyLimit = new BigDecimal("500.00");
                monthlyLimit = new BigDecimal("2000.00");
                transactionLimit = new BigDecimal("100.00");
                break;
            case ENHANCED:
                dailyLimit = new BigDecimal("5000.00");
                monthlyLimit = new BigDecimal("20000.00");
                transactionLimit = new BigDecimal("1000.00");
                break;
            case PREMIUM:
                dailyLimit = new BigDecimal("50000.00");
                monthlyLimit = new BigDecimal("200000.00");
                transactionLimit = new BigDecimal("10000.00");
                break;
            case NONE:
            default:
                dailyLimit = new BigDecimal("100.00");
                monthlyLimit = new BigDecimal("500.00");
                transactionLimit = new BigDecimal("50.00");
                break;
        }
        
        // Update user limits
        limitService.updateUserLimits(
            user.getId(),
            dailyLimit,
            monthlyLimit,
            transactionLimit
        );
        
        log.info("Updated account limits for user {}: daily={}, monthly={}, transaction={}",
            user.getId(), dailyLimit, monthlyLimit, transactionLimit);
    }
    
    /**
     * Unlock features based on verification level
     */
    private void unlockFeatures(User user, KYCVerificationCompletedEvent event) {
        VerificationLevel level = mapVerificationLevel(event.getVerificationLevel());
        
        Map<String, Boolean> features = new HashMap<>();
        
        switch (level) {
            case BASIC:
                features.put("domestic_transfers", true);
                features.put("bill_payments", true);
                features.put("mobile_deposits", true);
                features.put("international_transfers", false);
                features.put("investment_accounts", false);
                features.put("credit_products", false);
                break;
            case ENHANCED:
                features.put("domestic_transfers", true);
                features.put("bill_payments", true);
                features.put("mobile_deposits", true);
                features.put("international_transfers", true);
                features.put("investment_accounts", true);
                features.put("credit_products", false);
                break;
            case PREMIUM:
                features.put("domestic_transfers", true);
                features.put("bill_payments", true);
                features.put("mobile_deposits", true);
                features.put("international_transfers", true);
                features.put("investment_accounts", true);
                features.put("credit_products", true);
                features.put("premium_support", true);
                features.put("custom_limits", true);
                break;
            case NONE:
            default:
                features.put("domestic_transfers", false);
                features.put("bill_payments", false);
                features.put("mobile_deposits", false);
                features.put("international_transfers", false);
                features.put("investment_accounts", false);
                features.put("credit_products", false);
                break;
        }
        
        // Update user features
        userService.updateUserFeatures(user.getId(), features);
        
        log.info("Updated features for user {}: level={}, features={}",
            user.getId(), level, features.keySet());
    }
    
    /**
     * Map verification level string to enum
     */
    private VerificationLevel mapVerificationLevel(String level) {
        if (level == null) {
            return VerificationLevel.NONE;
        }
        
        try {
            return VerificationLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown verification level: {}, defaulting to NONE", level);
            return VerificationLevel.NONE;
        }
    }
    
    /**
     * Audit KYC status change
     */
    private void auditKYCStatusChange(User user, KYCStatus previousStatus, 
                                     VerificationLevel previousLevel,
                                     KYCVerificationCompletedEvent event) {
        auditService.auditKYCStatusChange(
            user.getId(),
            previousStatus,
            user.getKycStatus(),
            previousLevel,
            user.getVerificationLevel(),
            event.getVerificationScore(),
            event.getVerificationId(),
            Map.of(
                "verificationMethod", event.getVerificationMethod(),
                "documentsVerified", event.getDocumentsVerified(),
                "biometricVerified", event.isBiometricVerified()
            )
        );
        
        log.info("Audited KYC status change for user {}: {} -> {}, {} -> {}",
            user.getId(), previousStatus, user.getKycStatus(), 
            previousLevel, user.getVerificationLevel());
    }
    
    /**
     * Send notification to user about KYC result
     */
    private void sendUserNotification(User user, KYCVerificationCompletedEvent event) {
        try {
            String message;
            String title;
            
            switch (event.getVerificationStatus()) {
                case "VERIFIED":
                    title = "Identity Verified!";
                    message = String.format(
                        "Congratulations! Your identity has been verified. " +
                        "You now have access to %s level features.",
                        event.getVerificationLevel()
                    );
                    break;
                case "REJECTED":
                    title = "Verification Failed";
                    message = String.format(
                        "We were unable to verify your identity. Reason: %s. " +
                        "Please contact support for assistance.",
                        event.getRejectionReason()
                    );
                    break;
                case "PENDING_REVIEW":
                    title = "Manual Review Required";
                    message = "Your verification is under manual review. " +
                        "We'll notify you once the review is complete.";
                    break;
                default:
                    title = "Verification Update";
                    message = "There's an update on your identity verification. " +
                        "Please check your account.";
            }
            
            userService.sendNotification(
                user.getId(),
                title,
                message,
                Map.of(
                    "type", "KYC_VERIFICATION_RESULT",
                    "verificationId", event.getVerificationId(),
                    "status", event.getVerificationStatus()
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to send KYC notification to user {}", user.getId(), e);
            // Don't throw - notification failure shouldn't fail the entire process
        }
    }
    
}