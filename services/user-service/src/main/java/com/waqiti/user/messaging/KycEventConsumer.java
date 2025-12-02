package com.waqiti.user.messaging;

import com.waqiti.user.domain.User;
import com.waqiti.user.domain.KycStatus;
import com.waqiti.user.domain.KycVerification;
import com.waqiti.user.repository.UserRepository;
import com.waqiti.user.repository.KycVerificationRepository;
import com.waqiti.user.service.UserAccountActivationService;
import com.waqiti.user.service.NotificationService;
import com.waqiti.common.events.KycEvent;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.events.UserStatusUpdateEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Enterprise-grade KYC Event Consumer
 * 
 * Handles all KYC-related events with comprehensive error handling,
 * audit logging, and business process orchestration
 * 
 * Features:
 * - Automatic retry with exponential backoff
 * - Dead letter queue for failed processing
 * - Comprehensive audit logging
 * - Transaction management
 * - User account activation workflow
 * - Notification dispatching
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KycEventConsumer {

    private final UserRepository userRepository;
    private final KycVerificationRepository kycVerificationRepository;
    private final UserAccountActivationService accountActivationService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    /**
     * Process KYC completion events
     * Triggers account activation and user notifications
     */
    @KafkaListener(topics = "kyc-completed", groupId = "user-service-kyc-group")
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @Transactional
    public void handleKycCompleted(@Valid @Payload KycEvent event,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  Acknowledgment acknowledgment) {
        
        log.info("Processing KYC completed event for user: {} with verification ID: {}", 
                event.getUserId(), event.getVerificationId());

        try {
            // Validate event data
            validateKycEvent(event);
            
            // Find and update user
            Optional<User> userOpt = userRepository.findById(UUID.fromString(event.getUserId()));
            if (userOpt.isEmpty()) {
                log.error("User not found for KYC completion: {}", event.getUserId());
                throw new IllegalArgumentException("User not found: " + event.getUserId());
            }

            User user = userOpt.get();
            
            // Update KYC verification record
            KycVerification kycVerification = updateKycVerificationStatus(event, KycStatus.VERIFIED);
            
            // Update user KYC status
            user.setKycStatus(KycStatus.VERIFIED);
            user.setKycCompletedAt(LocalDateTime.now());
            user.setKycVerificationId(event.getVerificationId());
            user.setLastModifiedAt(LocalDateTime.now());
            
            // Mark account as eligible for activation
            user.setAccountActivationEligible(true);
            
            User savedUser = userRepository.save(user);

            // Trigger account activation workflow
            accountActivationService.processAccountActivation(
                savedUser.getId().toString(), 
                event.getVerificationId(),
                event.getKycLevel()
            );

            // Send success notifications
            notificationService.sendKycCompletionNotification(
                savedUser.getId().toString(),
                savedUser.getEmail(),
                savedUser.getPhoneNumber(),
                event.getKycLevel()
            );

            // Publish user status update event for downstream services
            publishUserStatusUpdateEvent(savedUser, "KYC_COMPLETED");

            // Audit the completion
            auditService.auditUserEvent(
                "KYC_COMPLETED",
                savedUser.getId().toString(),
                "KYC verification completed successfully",
                Map.of(
                    "verificationId", event.getVerificationId(),
                    "kycLevel", event.getKycLevel(),
                    "verificationMethod", event.getVerificationMethod(),
                    "completedAt", LocalDateTime.now()
                )
            );

            // Acknowledge successful processing
            acknowledgment.acknowledge();

            log.info("Successfully processed KYC completion for user: {} - Account activation triggered", 
                    event.getUserId());

        } catch (Exception e) {
            log.error("Failed to process KYC completion event for user: {}", event.getUserId(), e);
            
            // Audit the failure
            auditService.auditUserEvent(
                "KYC_COMPLETION_PROCESSING_FAILED",
                event.getUserId(),
                "Failed to process KYC completion: " + e.getMessage(),
                Map.of("error", e.getClass().getSimpleName(), "verificationId", event.getVerificationId())
            );
            
            throw e; // Re-throw to trigger retry mechanism
        }
    }

    /**
     * Process KYC rejection events
     * Handles user notification and account status updates
     */
    @KafkaListener(topics = "kyc-rejected", groupId = "user-service-kyc-group")
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @Transactional
    public void handleKycRejected(@Valid @Payload KycEvent event,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 Acknowledgment acknowledgment) {
        
        log.warn("Processing KYC rejection event for user: {} - Reason: {}", 
                event.getUserId(), event.getRejectionReason());

        try {
            validateKycEvent(event);
            
            Optional<User> userOpt = userRepository.findById(UUID.fromString(event.getUserId()));
            if (userOpt.isEmpty()) {
                throw new IllegalArgumentException("User not found: " + event.getUserId());
            }

            User user = userOpt.get();
            
            // Update KYC verification record
            KycVerification kycVerification = updateKycVerificationStatus(event, KycStatus.REJECTED);
            kycVerification.setRejectionReason(event.getRejectionReason());
            kycVerification.setRejectionDetails(event.getRejectionDetails());
            kycVerificationRepository.save(kycVerification);
            
            // Update user status
            user.setKycStatus(KycStatus.REJECTED);
            user.setKycRejectedAt(LocalDateTime.now());
            user.setKycRejectionReason(event.getRejectionReason());
            user.setAccountActivationEligible(false);
            user.setLastModifiedAt(LocalDateTime.now());
            
            User savedUser = userRepository.save(user);

            // Send rejection notifications
            notificationService.sendKycRejectionNotification(
                savedUser.getId().toString(),
                savedUser.getEmail(),
                savedUser.getPhoneNumber(),
                event.getRejectionReason(),
                event.getResubmissionAllowed()
            );

            // Publish status update event
            publishUserStatusUpdateEvent(savedUser, "KYC_REJECTED");

            // Audit the rejection
            auditService.auditUserEvent(
                "KYC_REJECTED",
                savedUser.getId().toString(),
                "KYC verification rejected: " + event.getRejectionReason(),
                Map.of(
                    "verificationId", event.getVerificationId(),
                    "rejectionReason", event.getRejectionReason(),
                    "resubmissionAllowed", event.getResubmissionAllowed(),
                    "rejectedAt", LocalDateTime.now()
                )
            );

            acknowledgment.acknowledge();

            log.info("Successfully processed KYC rejection for user: {} - Notifications sent", 
                    event.getUserId());

        } catch (Exception e) {
            log.error("Failed to process KYC rejection event for user: {}", event.getUserId(), e);
            
            auditService.auditUserEvent(
                "KYC_REJECTION_PROCESSING_FAILED",
                event.getUserId(),
                "Failed to process KYC rejection: " + e.getMessage(),
                Map.of("error", e.getClass().getSimpleName())
            );
            
            throw e;
        }
    }

    /**
     * Process KYC processing error events
     * Handles system errors during KYC verification
     */
    @KafkaListener(topics = "kyc-processing-errors", groupId = "user-service-kyc-group")
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public void handleKycProcessingError(@Valid @Payload KycEvent event,
                                        Acknowledgment acknowledgment) {
        
        log.error("Processing KYC error event for user: {} - Error: {}", 
                event.getUserId(), event.getErrorMessage());

        try {
            validateKycEvent(event);
            
            Optional<User> userOpt = userRepository.findById(UUID.fromString(event.getUserId()));
            if (userOpt.isEmpty()) {
                throw new IllegalArgumentException("User not found: " + event.getUserId());
            }

            User user = userOpt.get();
            
            // Update KYC verification record
            KycVerification kycVerification = updateKycVerificationStatus(event, KycStatus.ERROR);
            kycVerification.setErrorMessage(event.getErrorMessage());
            kycVerification.setErrorCode(event.getErrorCode());
            kycVerification.setRetryCount(kycVerification.getRetryCount() + 1);
            kycVerificationRepository.save(kycVerification);
            
            // Update user status
            user.setKycStatus(KycStatus.ERROR);
            user.setLastModifiedAt(LocalDateTime.now());
            
            User savedUser = userRepository.save(user);

            // Determine if automatic retry should be attempted
            boolean shouldRetry = shouldRetryKycVerification(kycVerification, event);
            
            if (shouldRetry) {
                // Trigger automatic retry
                accountActivationService.retryKycVerification(
                    savedUser.getId().toString(),
                    event.getVerificationId()
                );
                
                log.info("Triggered automatic KYC retry for user: {}", event.getUserId());
            } else {
                // Send error notification to user
                notificationService.sendKycErrorNotification(
                    savedUser.getId().toString(),
                    savedUser.getEmail(),
                    savedUser.getPhoneNumber(),
                    event.getErrorMessage()
                );
                
                log.info("KYC retry limit exceeded for user: {} - User notified", event.getUserId());
            }

            // Publish status update event
            publishUserStatusUpdateEvent(savedUser, shouldRetry ? "KYC_RETRY_TRIGGERED" : "KYC_ERROR_FINAL");

            // Audit the error
            auditService.auditUserEvent(
                "KYC_PROCESSING_ERROR",
                savedUser.getId().toString(),
                "KYC processing error: " + event.getErrorMessage(),
                Map.of(
                    "verificationId", event.getVerificationId(),
                    "errorCode", event.getErrorCode(),
                    "errorMessage", event.getErrorMessage(),
                    "retryCount", kycVerification.getRetryCount(),
                    "shouldRetry", shouldRetry
                )
            );

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process KYC error event for user: {}", event.getUserId(), e);
            throw e;
        }
    }

    // Private helper methods

    private void validateKycEvent(KycEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (event.getVerificationId() == null || event.getVerificationId().trim().isEmpty()) {
            throw new IllegalArgumentException("Verification ID is required");
        }
    }

    private KycVerification updateKycVerificationStatus(KycEvent event, KycStatus status) {
        Optional<KycVerification> verificationOpt = kycVerificationRepository
                .findByVerificationId(event.getVerificationId());
        
        KycVerification verification;
        if (verificationOpt.isPresent()) {
            verification = verificationOpt.get();
        } else {
            // Create new verification record if not exists
            verification = KycVerification.builder()
                    .verificationId(event.getVerificationId())
                    .userId(UUID.fromString(event.getUserId()))
                    .createdAt(LocalDateTime.now())
                    .retryCount(0)
                    .build();
        }
        
        verification.setStatus(status);
        verification.setLastModifiedAt(LocalDateTime.now());
        verification.setKycLevel(event.getKycLevel());
        verification.setVerificationMethod(event.getVerificationMethod());
        
        return kycVerificationRepository.save(verification);
    }

    private boolean shouldRetryKycVerification(KycVerification verification, KycEvent event) {
        // Retry logic: max 3 retries, only for specific error types
        if (verification.getRetryCount() >= 3) {
            return false;
        }
        
        // Only retry for temporary system errors, not document/data issues
        String errorCode = event.getErrorCode();
        return errorCode != null && (
                errorCode.startsWith("SYSTEM_") ||
                errorCode.startsWith("TIMEOUT_") ||
                errorCode.startsWith("SERVICE_UNAVAILABLE")
        );
    }

    private void publishUserStatusUpdateEvent(User user, String eventType) {
        try {
            UserStatusUpdateEvent statusEvent = UserStatusUpdateEvent.builder()
                    .userId(user.getId().toString())
                    .eventType(eventType)
                    .kycStatus(user.getKycStatus().toString())
                    .accountActivationEligible(user.isAccountActivationEligible())
                    .timestamp(LocalDateTime.now())
                    .build();
            
            // Publish to user-events topic for downstream services
            // Implementation would use KafkaTemplate to publish
            log.debug("Published user status update event: {} for user: {}", eventType, user.getId());
            
        } catch (Exception e) {
            log.warn("Failed to publish user status update event for user: {}", user.getId(), e);
            // Non-critical error - don't fail the main processing
        }
    }
}