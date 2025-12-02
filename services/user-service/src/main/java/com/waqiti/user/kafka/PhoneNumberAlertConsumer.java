package com.waqiti.user.kafka;

import com.waqiti.user.event.PhoneNumberAlertEvent;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.PhoneVerificationService;
import com.waqiti.user.service.NotificationService;
import com.waqiti.user.service.SecurityAuditService;
import com.waqiti.user.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Production-grade Kafka consumer for phone number alert events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhoneNumberAlertConsumer {

    private final UserService userService;
    private final PhoneVerificationService phoneVerificationService;
    private final NotificationService notificationService;
    private final SecurityAuditService securityAuditService;
    private final FraudDetectionService fraudDetectionService;

    @KafkaListener(topics = "phone-number-alerts", groupId = "phone-alert-processor")
    public void processPhoneNumberAlert(@Payload PhoneNumberAlertEvent event,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                      @Header(KafkaHeaders.OFFSET) long offset,
                                      Acknowledgment acknowledgment) {
        try {
            log.info("Processing phone number alert for user: {} type: {} phone: {} country: {}", 
                    event.getUserId(), event.getAlertType(), 
                    maskPhoneNumber(event.getPhoneNumber()), event.getCountryCode());
            
            // Validate event
            validatePhoneAlertEvent(event);
            
            // Process based on alert type
            switch (event.getAlertType()) {
                case "PHONE_CHANGED" -> handlePhoneNumberChange(event);
                case "PHONE_ADDED" -> handlePhoneNumberAddition(event);
                case "PHONE_REMOVED" -> handlePhoneNumberRemoval(event);
                case "VERIFICATION_FAILED" -> handleVerificationFailure(event);
                case "VERIFICATION_EXPIRED" -> handleVerificationExpiry(event);
                case "SUSPICIOUS_CHANGE" -> handleSuspiciousChange(event);
                case "DUPLICATE_DETECTED" -> handleDuplicatePhone(event);
                case "INVALID_FORMAT" -> handleInvalidFormat(event);
                case "BLOCKED_NUMBER" -> handleBlockedNumber(event);
                case "SIM_SWAP_DETECTED" -> handleSimSwapDetection(event);
                default -> handleGenericPhoneAlert(event);
            }
            
            // Update phone verification status
            updateVerificationStatus(event);
            
            // Check for security implications
            if (hasSecurityImplications(event)) {
                handleSecurityImplications(event);
            }
            
            // Send notifications
            sendPhoneAlertNotifications(event);
            
            // Log phone alert for audit
            securityAuditService.logPhoneNumberAlert(
                event.getUserId(),
                event.getAlertType(),
                maskPhoneNumber(event.getPhoneNumber()),
                event.getPreviousPhoneNumber() != null ? maskPhoneNumber(event.getPreviousPhoneNumber()) : null,
                event.getChangedBy(),
                event.getChangedAt(),
                event.getIpAddress(),
                event.getDeviceId()
            );
            
            // Track metrics
            phoneVerificationService.trackPhoneAlertMetrics(
                event.getAlertType(),
                event.getCountryCode(),
                event.getCarrier()
            );
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed phone number alert for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to process phone number alert for user {}: {}", 
                    event.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Phone number alert processing failed", e);
        }
    }

    private void validatePhoneAlertEvent(PhoneNumberAlertEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required for phone alert");
        }
        
        if (event.getAlertType() == null || event.getAlertType().trim().isEmpty()) {
            throw new IllegalArgumentException("Alert type is required");
        }
        
        if (event.getPhoneNumber() == null || event.getPhoneNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number is required");
        }
    }

    private void handlePhoneNumberChange(PhoneNumberAlertEvent event) {
        // Validate phone change request
        boolean isValid = phoneVerificationService.validatePhoneChange(
            event.getUserId(),
            event.getPreviousPhoneNumber(),
            event.getPhoneNumber(),
            event.getVerificationCode()
        );
        
        if (!isValid) {
            handleInvalidPhoneChange(event);
            return;
        }
        
        // Update phone number
        userService.updatePhoneNumber(
            event.getUserId(),
            event.getPhoneNumber(),
            event.getCountryCode(),
            event.isVerified()
        );
        
        // Start verification if not verified
        if (!event.isVerified()) {
            phoneVerificationService.initiateVerification(
                event.getUserId(),
                event.getPhoneNumber(),
                event.getVerificationMethod()
            );
        }
        
        // Check for rapid changes
        int recentChanges = phoneVerificationService.getRecentPhoneChanges(
            event.getUserId(),
            30 // Last 30 days
        );
        
        if (recentChanges > 2) {
            fraudDetectionService.flagSuspiciousPhoneActivity(
                event.getUserId(),
                "FREQUENT_PHONE_CHANGES",
                recentChanges
            );
        }
        
        // Archive previous phone number
        phoneVerificationService.archivePhoneNumber(
            event.getUserId(),
            event.getPreviousPhoneNumber(),
            event.getChangedAt()
        );
    }

    private void handlePhoneNumberAddition(PhoneNumberAlertEvent event) {
        // Check if phone number already exists
        if (phoneVerificationService.isPhoneNumberInUse(event.getPhoneNumber())) {
            handleDuplicatePhone(event);
            return;
        }
        
        // Add phone number
        phoneVerificationService.addPhoneNumber(
            event.getUserId(),
            event.getPhoneNumber(),
            event.getCountryCode(),
            event.getPhoneType(),
            event.isPrimary()
        );
        
        // Initiate verification
        String verificationId = phoneVerificationService.initiateVerification(
            event.getUserId(),
            event.getPhoneNumber(),
            event.getVerificationMethod()
        );
        
        // Send verification code
        notificationService.sendPhoneVerificationCode(
            event.getPhoneNumber(),
            verificationId,
            event.getVerificationMethod()
        );
        
        // Set verification expiry
        phoneVerificationService.setVerificationExpiry(
            verificationId,
            LocalDateTime.now().plusMinutes(10)
        );
    }

    private void handlePhoneNumberRemoval(PhoneNumberAlertEvent event) {
        // Verify authorization
        if (!userService.verifyUserAuthorization(
                event.getUserId(),
                event.getAuthorizationToken(),
                "REMOVE_PHONE")) {
            log.error("Unauthorized phone removal attempt for user: {}", event.getUserId());
            securityAuditService.logUnauthorizedAction(
                event.getUserId(),
                "UNAUTHORIZED_PHONE_REMOVAL",
                event.getPhoneNumber()
            );
            return;
        }
        
        // Check if it's the last phone number
        int phoneCount = phoneVerificationService.getUserPhoneCount(event.getUserId());
        if (phoneCount <= 1 && event.isPrimary()) {
            log.warn("Cannot remove last primary phone for user: {}", event.getUserId());
            notificationService.sendPhoneRemovalError(
                event.getUserId(),
                "LAST_PHONE_CANNOT_BE_REMOVED"
            );
            return;
        }
        
        // Remove phone number
        phoneVerificationService.removePhoneNumber(
            event.getUserId(),
            event.getPhoneNumber()
        );
        
        // Update 2FA if phone was used for authentication
        if (event.isUsedForTwoFactor()) {
            userService.updateTwoFactorMethod(
                event.getUserId(),
                "PHONE_REMOVED",
                event.getAlternativeTwoFactorMethod()
            );
        }
    }

    private void handleVerificationFailure(PhoneNumberAlertEvent event) {
        // Increment failure count
        int failureCount = phoneVerificationService.incrementVerificationFailures(
            event.getUserId(),
            event.getPhoneNumber()
        );
        
        // Check if max attempts exceeded
        if (failureCount >= 5) {
            // Lock phone verification
            phoneVerificationService.lockPhoneVerification(
                event.getUserId(),
                event.getPhoneNumber(),
                "MAX_ATTEMPTS_EXCEEDED"
            );
            
            // Flag for security review
            securityAuditService.flagForSecurityReview(
                event.getUserId(),
                "PHONE_VERIFICATION_FAILURES",
                failureCount
            );
        } else {
            // Allow retry
            phoneVerificationService.scheduleVerificationRetry(
                event.getUserId(),
                event.getPhoneNumber(),
                failureCount
            );
        }
        
        // Log failure reason
        phoneVerificationService.logVerificationFailure(
            event.getUserId(),
            event.getPhoneNumber(),
            event.getFailureReason(),
            event.getAttemptedAt()
        );
    }

    private void handleVerificationExpiry(PhoneNumberAlertEvent event) {
        // Mark verification as expired
        phoneVerificationService.markVerificationExpired(
            event.getUserId(),
            event.getPhoneNumber(),
            event.getVerificationId()
        );
        
        // Send expiry notification
        notificationService.sendVerificationExpiryNotice(
            event.getUserId(),
            event.getPhoneNumber()
        );
        
        // Offer to resend verification
        phoneVerificationService.offerVerificationResend(
            event.getUserId(),
            event.getPhoneNumber()
        );
    }

    private void handleSuspiciousChange(PhoneNumberAlertEvent event) {
        // Analyze suspicious indicators
        Map<String, Object> suspiciousIndicators = event.getSuspiciousIndicators();
        
        // Calculate risk score
        int riskScore = fraudDetectionService.calculatePhoneChangeRiskScore(
            event.getUserId(),
            suspiciousIndicators
        );
        
        if (riskScore > 80) {
            // Block the change
            phoneVerificationService.blockPhoneChange(
                event.getUserId(),
                event.getPhoneNumber(),
                "HIGH_RISK_DETECTED"
            );
            
            // Require additional verification
            userService.requireAdditionalVerification(
                event.getUserId(),
                "SUSPICIOUS_PHONE_CHANGE"
            );
        } else if (riskScore > 50) {
            // Flag for review
            phoneVerificationService.flagForManualReview(
                event.getUserId(),
                event.getPhoneNumber(),
                suspiciousIndicators
            );
        }
        
        // Log suspicious activity
        securityAuditService.logSuspiciousPhoneActivity(
            event.getUserId(),
            event.getPhoneNumber(),
            suspiciousIndicators,
            riskScore
        );
    }

    private void handleDuplicatePhone(PhoneNumberAlertEvent event) {
        // Find existing user with this phone
        String existingUserId = phoneVerificationService.findUserByPhone(event.getPhoneNumber());
        
        if (existingUserId != null) {
            // Check if it's the same user
            if (!existingUserId.equals(event.getUserId())) {
                // Phone belongs to another user
                log.warn("Phone number {} already assigned to another user", 
                        maskPhoneNumber(event.getPhoneNumber()));
                
                // Investigate potential account linking
                fraudDetectionService.investigateAccountLinking(
                    event.getUserId(),
                    existingUserId,
                    event.getPhoneNumber()
                );
                
                // Notify both users
                notificationService.sendDuplicatePhoneAlert(
                    event.getUserId(),
                    existingUserId,
                    maskPhoneNumber(event.getPhoneNumber())
                );
            }
        }
    }

    private void handleInvalidFormat(PhoneNumberAlertEvent event) {
        // Log invalid format attempt
        phoneVerificationService.logInvalidFormatAttempt(
            event.getUserId(),
            event.getPhoneNumber(),
            event.getFormatError()
        );
        
        // Send format error notification
        notificationService.sendPhoneFormatError(
            event.getUserId(),
            event.getPhoneNumber(),
            event.getExpectedFormat()
        );
        
        // Suggest corrections if possible
        String correctedNumber = phoneVerificationService.suggestPhoneCorrection(
            event.getPhoneNumber(),
            event.getCountryCode()
        );
        
        if (correctedNumber != null) {
            notificationService.suggestPhoneCorrection(
                event.getUserId(),
                event.getPhoneNumber(),
                correctedNumber
            );
        }
    }

    private void handleBlockedNumber(PhoneNumberAlertEvent event) {
        // Check block reason
        String blockReason = phoneVerificationService.getBlockReason(event.getPhoneNumber());
        
        // Log blocked attempt
        securityAuditService.logBlockedPhoneAttempt(
            event.getUserId(),
            event.getPhoneNumber(),
            blockReason,
            event.getAttemptedAt()
        );
        
        // Handle based on block type
        switch (blockReason) {
            case "VOIP" -> {
                notificationService.sendVoipBlockNotice(
                    event.getUserId(),
                    "VOIP_NUMBERS_NOT_ALLOWED"
                );
            }
            case "DISPOSABLE" -> {
                notificationService.sendDisposablePhoneBlockNotice(
                    event.getUserId(),
                    "DISPOSABLE_NUMBERS_NOT_ALLOWED"
                );
            }
            case "BLACKLISTED" -> {
                fraudDetectionService.handleBlacklistedPhone(
                    event.getUserId(),
                    event.getPhoneNumber(),
                    event.getBlacklistReason()
                );
            }
            case "HIGH_RISK_COUNTRY" -> {
                phoneVerificationService.handleHighRiskCountry(
                    event.getUserId(),
                    event.getPhoneNumber(),
                    event.getCountryCode()
                );
            }
        }
    }

    private void handleSimSwapDetection(PhoneNumberAlertEvent event) {
        // Critical security event
        log.error("SIM swap detected for user: {} phone: {}", 
                event.getUserId(), maskPhoneNumber(event.getPhoneNumber()));
        
        // Immediate security response
        userService.initiateSimSwapProtocol(
            event.getUserId(),
            event.getPhoneNumber()
        );
        
        // Lock account
        userService.lockAccount(
            event.getUserId(),
            "SIM_SWAP_DETECTED"
        );
        
        // Disable phone-based authentication
        phoneVerificationService.disablePhoneAuthentication(
            event.getUserId(),
            event.getPhoneNumber()
        );
        
        // Send alerts through alternative channels
        notificationService.sendSimSwapAlertViaEmail(
            event.getUserId(),
            event.getPhoneNumber(),
            event.getDetectedAt()
        );
        
        // Create security incident
        securityAuditService.createSecurityIncident(
            event.getUserId(),
            "SIM_SWAP",
            event.getSimSwapDetails(),
            "CRITICAL"
        );
        
        // Notify carrier if possible
        phoneVerificationService.notifyCarrierOfSimSwap(
            event.getPhoneNumber(),
            event.getCarrier(),
            event.getSimSwapDetails()
        );
    }

    private void handleGenericPhoneAlert(PhoneNumberAlertEvent event) {
        // Log generic alert
        phoneVerificationService.logPhoneAlert(
            event.getUserId(),
            event.getAlertType(),
            event.getPhoneNumber(),
            event.getAlertDetails()
        );
        
        // Update phone status if needed
        if (event.getNewStatus() != null) {
            phoneVerificationService.updatePhoneStatus(
                event.getUserId(),
                event.getPhoneNumber(),
                event.getNewStatus()
            );
        }
    }

    private void handleInvalidPhoneChange(PhoneNumberAlertEvent event) {
        // Log invalid change attempt
        securityAuditService.logInvalidPhoneChange(
            event.getUserId(),
            event.getPreviousPhoneNumber(),
            event.getPhoneNumber(),
            event.getValidationError()
        );
        
        // Check for potential fraud
        if (event.isSuspiciousAttempt()) {
            fraudDetectionService.investigatePhoneFraud(
                event.getUserId(),
                event.getPhoneNumber(),
                event.getSuspiciousIndicators()
            );
        }
    }

    private void updateVerificationStatus(PhoneNumberAlertEvent event) {
        if (event.getVerificationStatus() != null) {
            phoneVerificationService.updateVerificationStatus(
                event.getUserId(),
                event.getPhoneNumber(),
                event.getVerificationStatus(),
                event.getVerifiedAt()
            );
            
            // Update user profile if primary phone
            if (event.isPrimary() && "VERIFIED".equals(event.getVerificationStatus())) {
                userService.markPhoneAsVerified(
                    event.getUserId(),
                    event.getPhoneNumber()
                );
            }
        }
    }

    private boolean hasSecurityImplications(PhoneNumberAlertEvent event) {
        return "SIM_SWAP_DETECTED".equals(event.getAlertType()) ||
               "SUSPICIOUS_CHANGE".equals(event.getAlertType()) ||
               "BLOCKED_NUMBER".equals(event.getAlertType()) ||
               event.getRiskScore() > 50 ||
               event.isSuspiciousAttempt();
    }

    private void handleSecurityImplications(PhoneNumberAlertEvent event) {
        // Increase user risk score
        fraudDetectionService.increaseRiskScore(
            event.getUserId(),
            "PHONE_SECURITY_ALERT",
            event.getRiskScore()
        );
        
        // Enable additional security measures
        if (event.getRiskScore() > 70) {
            userService.enableEnhancedSecurity(
                event.getUserId(),
                "HIGH_RISK_PHONE_ACTIVITY"
            );
        }
        
        // Require re-authentication
        if ("CRITICAL".equals(event.getSeverity())) {
            userService.requireReauthentication(
                event.getUserId(),
                "CRITICAL_PHONE_ALERT"
            );
        }
    }

    private void sendPhoneAlertNotifications(PhoneNumberAlertEvent event) {
        // Send primary notification
        if (event.isNotifyUser()) {
            notificationService.sendPhoneAlertNotification(
                event.getUserId(),
                event.getAlertType(),
                maskPhoneNumber(event.getPhoneNumber()),
                event.getAlertMessage()
            );
        }
        
        // Send to alternative contact if critical
        if ("CRITICAL".equals(event.getSeverity()) && event.getAlternativeContact() != null) {
            notificationService.notifyAlternativeContact(
                event.getAlternativeContact(),
                event.getUserId(),
                event.getAlertType(),
                "CRITICAL_PHONE_ALERT"
            );
        }
        
        // Send security team alert for high-risk events
        if (hasSecurityImplications(event)) {
            securityAuditService.alertSecurityTeam(
                event.getUserId(),
                event.getAlertType(),
                event.getSecurityDetails()
            );
        }
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        int visibleDigits = Math.min(4, phoneNumber.length() / 3);
        return "*".repeat(phoneNumber.length() - visibleDigits) + 
               phoneNumber.substring(phoneNumber.length() - visibleDigits);
    }
}