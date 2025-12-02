package com.waqiti.user.service;

import com.waqiti.user.domain.User;
import com.waqiti.user.domain.UserStatus;
import com.waqiti.user.repository.UserRepository;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.events.UserAccountActivationEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Enterprise Account Activation Service
 * 
 * Handles the complete user account activation workflow including:
 * - Account status transitions
 * - Wallet creation triggering
 * - Service provisioning
 * - Audit logging
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAccountActivationService {
    
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Process account activation after KYC completion
     */
    @Transactional
    public void processAccountActivation(String userId, String verificationId, String kycLevel) {
        log.info("Processing account activation for user: {} with KYC level: {}", userId, kycLevel);
        
        try {
            Optional<User> userOpt = userRepository.findById(UUID.fromString(userId));
            if (userOpt.isEmpty()) {
                throw new IllegalArgumentException("User not found: " + userId);
            }
            
            User user = userOpt.get();
            
            // Validate user is eligible for activation
            if (!user.isAccountActivationEligible()) {
                log.warn("User {} is not eligible for account activation", userId);
                return;
            }
            
            // Update user status to active
            UserStatus previousStatus = user.getStatus();
            user.setStatus(UserStatus.ACTIVE);
            user.setAccountActivatedAt(LocalDateTime.now());
            user.setLastModifiedAt(LocalDateTime.now());
            
            User savedUser = userRepository.save(user);
            
            // Publish account activation event for downstream services
            publishAccountActivationEvent(savedUser, verificationId, kycLevel);
            
            // Audit the activation
            auditService.auditUserEvent(
                "USER_ACCOUNT_ACTIVATED",
                userId,
                String.format("User account activated - Status changed from %s to %s", 
                    previousStatus, UserStatus.ACTIVE),
                Map.of(
                    "verificationId", verificationId,
                    "kycLevel", kycLevel,
                    "previousStatus", previousStatus,
                    "activatedAt", LocalDateTime.now()
                )
            );
            
            log.info("Successfully activated account for user: {} - Downstream services notified", userId);
            
        } catch (Exception e) {
            log.error("Failed to activate account for user: {}", userId, e);
            
            auditService.auditUserEvent(
                "USER_ACCOUNT_ACTIVATION_FAILED",
                userId,
                "Account activation failed: " + e.getMessage(),
                Map.of("error", e.getClass().getSimpleName(), "verificationId", verificationId)
            );
            
            throw e;
        }
    }
    
    /**
     * Retry KYC verification for failed verifications
     */
    @Transactional
    public void retryKycVerification(String userId, String verificationId) {
        log.info("Initiating KYC verification retry for user: {}", userId);
        
        try {
            Optional<User> userOpt = userRepository.findById(UUID.fromString(userId));
            if (userOpt.isEmpty()) {
                throw new IllegalArgumentException("User not found: " + userId);
            }
            
            User user = userOpt.get();
            
            // Publish KYC retry request event
            publishKycRetryEvent(user, verificationId);
            
            // Audit the retry
            auditService.auditUserEvent(
                "KYC_VERIFICATION_RETRY_INITIATED",
                userId,
                "KYC verification retry initiated",
                Map.of("verificationId", verificationId, "retryInitiatedAt", LocalDateTime.now())
            );
            
            log.info("KYC verification retry initiated for user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to initiate KYC retry for user: {}", userId, e);
            throw e;
        }
    }
    
    /**
     * Deactivate user account (for compliance or security reasons)
     */
    @Transactional
    public void deactivateUserAccount(String userId, String reason, String initiatedBy) {
        log.warn("Deactivating account for user: {} - Reason: {}", userId, reason);
        
        try {
            Optional<User> userOpt = userRepository.findById(UUID.fromString(userId));
            if (userOpt.isEmpty()) {
                throw new IllegalArgumentException("User not found: " + userId);
            }
            
            User user = userOpt.get();
            UserStatus previousStatus = user.getStatus();
            
            user.setStatus(UserStatus.SUSPENDED);
            user.setAccountDeactivatedAt(LocalDateTime.now());
            user.setDeactivationReason(reason);
            user.setLastModifiedAt(LocalDateTime.now());
            
            User savedUser = userRepository.save(user);
            
            // Publish account deactivation event
            publishAccountDeactivationEvent(savedUser, reason, initiatedBy);
            
            // Audit the deactivation
            auditService.auditUserEvent(
                "USER_ACCOUNT_DEACTIVATED",
                userId,
                String.format("User account deactivated - Reason: %s, Initiated by: %s", reason, initiatedBy),
                Map.of(
                    "reason", reason,
                    "initiatedBy", initiatedBy,
                    "previousStatus", previousStatus,
                    "deactivatedAt", LocalDateTime.now()
                )
            );
            
            log.info("Successfully deactivated account for user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to deactivate account for user: {}", userId, e);
            throw e;
        }
    }
    
    // Private helper methods
    
    private void publishAccountActivationEvent(User user, String verificationId, String kycLevel) {
        try {
            UserAccountActivationEvent event = UserAccountActivationEvent.builder()
                .userId(user.getId().toString())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .kycLevel(kycLevel)
                .verificationId(verificationId)
                .activatedAt(LocalDateTime.now())
                .build();
            
            kafkaTemplate.send("user-account-activated", user.getId().toString(), event);
            
            log.debug("Published account activation event for user: {}", user.getId());
            
        } catch (Exception e) {
            log.warn("Failed to publish account activation event for user: {}", user.getId(), e);
            // Non-critical - don't fail the main process
        }
    }
    
    private void publishKycRetryEvent(User user, String verificationId) {
        try {
            Map<String, Object> retryEvent = Map.of(
                "userId", user.getId().toString(),
                "verificationId", verificationId,
                "retryInitiatedAt", LocalDateTime.now(),
                "email", user.getEmail()
            );
            
            kafkaTemplate.send("kyc-verification-retry", user.getId().toString(), retryEvent);
            
            log.debug("Published KYC retry event for user: {}", user.getId());
            
        } catch (Exception e) {
            log.warn("Failed to publish KYC retry event for user: {}", user.getId(), e);
        }
    }
    
    private void publishAccountDeactivationEvent(User user, String reason, String initiatedBy) {
        try {
            Map<String, Object> deactivationEvent = Map.of(
                "userId", user.getId().toString(),
                "reason", reason,
                "initiatedBy", initiatedBy,
                "deactivatedAt", LocalDateTime.now(),
                "previousStatus", "ACTIVE"
            );
            
            kafkaTemplate.send("user-account-deactivated", user.getId().toString(), deactivationEvent);
            
            log.debug("Published account deactivation event for user: {}", user.getId());
            
        } catch (Exception e) {
            log.warn("Failed to publish account deactivation event for user: {}", user.getId(), e);
        }
    }
}