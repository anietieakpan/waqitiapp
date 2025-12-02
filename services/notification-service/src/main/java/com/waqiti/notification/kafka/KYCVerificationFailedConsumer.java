package com.waqiti.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.notification.domain.NotificationChannel;
import com.waqiti.notification.domain.NotificationPriority;
import com.waqiti.notification.domain.NotificationType;
import com.waqiti.notification.service.NotificationService;
import com.waqiti.notification.service.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FIX #4: KYCVerificationFailedConsumer
 *
 * PROBLEM SOLVED: KYC verification failures not communicated to users
 * - KYC service verifies identity documents
 * - Verification fails but no notification sent
 * - Events published to "kyc.verification.failed" topic
 * - NO consumer listening - users left in limbo
 * - Result: Poor UX, abandoned registrations, support burden
 *
 * IMPLEMENTATION:
 * - Listens to "kyc.verification.failed" events
 * - Sends detailed failure reason to user
 * - Provides instructions to fix issues
 * - Tracks failure patterns for improvement
 * - Escalates repeated failures
 *
 * COMPLIANCE REQUIREMENTS:
 * - KYC is mandatory for financial services
 * - Must clearly communicate failure reasons
 * - Must provide remediation path
 * - Must comply with GDPR (data processing transparency)
 *
 * BUSINESS IMPACT:
 * - Improves user conversion rate
 * - Reduces support ticket volume
 * - Ensures compliance with KYC regulations
 *
 * @author Waqiti Platform Team - Critical Fix
 * @since 2025-10-12
 * @priority HIGH
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KYCVerificationFailedConsumer {

    private final NotificationService notificationService;
    private final UserServiceClient userServiceClient;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;

    private static final String CONSUMER_GROUP = "notification-kyc-failed-processor";
    private static final String TOPIC = "kyc.verification.failed";
    private static final String IDEMPOTENCY_PREFIX = "notification:kyc:failed:";

    /**
     * Consumer for KYC verification failure events
     * Sends clear, actionable notifications to users
     *
     * CRITICAL USER EXPERIENCE FUNCTION:
     * - Explains why verification failed
     * - Provides step-by-step remediation instructions
     * - Includes support contact information
     * - Tracks failure patterns for service improvement
     */
    @KafkaListener(
        topics = TOPIC,
        groupId = CONSUMER_GROUP,
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "2"
    )
    @Retryable(
        value = {Exception.class},
        exclude = {BusinessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void handleKYCVerificationFailed(
            @Payload KYCVerificationFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String messageKey,
            @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        long startTime = System.currentTimeMillis();

        try {
            log.warn("KYC VERIFICATION FAILED: userId={}, verificationId={}, failureReason={}, attempt={}, partition={}, offset={}",
                event.getUserId(), event.getVerificationId(), event.getFailureReason(),
                event.getAttemptNumber(), partition, offset);

            metricsCollector.incrementCounter("notification.kyc.failed.received");
            metricsCollector.recordGauge("notification.kyc.attempt.number", event.getAttemptNumber());

            // Step 1: Idempotency check
            String idempotencyKey = IDEMPOTENCY_PREFIX + event.getVerificationId();
            if (!idempotencyService.tryAcquire(idempotencyKey, Duration.ofHours(24))) {
                log.warn("DUPLICATE KYC FAILED EVENT: verificationId={} - Skipping", event.getVerificationId());
                metricsCollector.incrementCounter("notification.kyc.failed.duplicate");
                acknowledgment.acknowledge();
                return;
            }

            // Step 2: Validate event
            validateKYCEvent(event);

            // Step 3: Get user information
            UserContactInfo userInfo = getUserContactInfo(event.getUserId());

            // Step 4: Determine failure severity and response
            KYCFailureCategory category = categorizeFailure(event.getFailureReason());

            // Step 5: Send appropriate notifications based on failure type
            sendKYCFailureNotifications(event, userInfo, category);

            // Step 6: Escalate if repeated failures
            if (event.getAttemptNumber() >= 3) {
                escalateRepeatedFailure(event, userInfo);
            }

            // Step 7: Track metrics
            long duration = System.currentTimeMillis() - startTime;
            metricsCollector.recordHistogram("notification.kyc.failed.duration.ms", duration);
            metricsCollector.incrementCounter("notification.kyc.failed.success");
            metricsCollector.incrementCounter("notification.kyc.failed." + category.name().toLowerCase());

            log.info("KYC FAILURE NOTIFICATION SENT: userId={}, verificationId={}, category={}, duration={}ms",
                event.getUserId(), event.getVerificationId(), category, duration);

            acknowledgment.acknowledge();

        } catch (BusinessException e) {
            log.error("Business exception processing KYC failure {}: {}", event.getVerificationId(), e.getMessage());
            metricsCollector.incrementCounter("notification.kyc.failed.business.error");
            handleBusinessException(event, e, acknowledgment);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to send KYC failure notification", e);
            metricsCollector.incrementCounter("notification.kyc.failed.critical.error");
            handleCriticalException(event, e, partition, offset, acknowledgment);
        }
    }

    /**
     * Validate KYC failure event
     */
    private void validateKYCEvent(KYCVerificationFailedEvent event) {
        if (event.getUserId() == null) {
            throw new BusinessException("User ID is required");
        }
        if (event.getVerificationId() == null || event.getVerificationId().isBlank()) {
            throw new BusinessException("Verification ID is required");
        }
        if (event.getFailureReason() == null || event.getFailureReason().isBlank()) {
            throw new BusinessException("Failure reason is required");
        }
    }

    /**
     * Get user contact information
     */
    private UserContactInfo getUserContactInfo(UUID userId) {
        try {
            return userServiceClient.getUserContactInfo(userId);
        } catch (Exception e) {
            log.error("Failed to get user contact info for userId={}", userId, e);
            throw new BusinessException("Failed to retrieve user contact information");
        }
    }

    /**
     * Categorize KYC failure for appropriate response
     */
    private KYCFailureCategory categorizeFailure(String failureReason) {
        String reason = failureReason.toLowerCase();

        if (reason.contains("document expired") || reason.contains("expiration")) {
            return KYCFailureCategory.DOCUMENT_EXPIRED;
        } else if (reason.contains("blurry") || reason.contains("unreadable") || reason.contains("quality")) {
            return KYCFailureCategory.POOR_IMAGE_QUALITY;
        } else if (reason.contains("mismatch") || reason.contains("doesn't match")) {
            return KYCFailureCategory.INFORMATION_MISMATCH;
        } else if (reason.contains("unsupported") || reason.contains("invalid document type")) {
            return KYCFailureCategory.UNSUPPORTED_DOCUMENT;
        } else if (reason.contains("incomplete") || reason.contains("missing")) {
            return KYCFailureCategory.INCOMPLETE_INFORMATION;
        } else if (reason.contains("address") || reason.contains("proof of residence")) {
            return KYCFailureCategory.ADDRESS_VERIFICATION;
        } else {
            return KYCFailureCategory.OTHER;
        }
    }

    /**
     * Send KYC failure notifications with specific instructions
     */
    private void sendKYCFailureNotifications(KYCVerificationFailedEvent event, UserContactInfo userInfo, KYCFailureCategory category) {
        Map<String, Object> templateData = buildTemplateData(event, userInfo, category);

        // Priority 1: Email (detailed explanation with images)
        try {
            String emailBody = buildKYCFailureEmail(event, userInfo, category);
            notificationService.sendNotification(
                event.getUserId(),
                NotificationType.KYC_VERIFICATION_FAILED,
                NotificationChannel.EMAIL,
                NotificationPriority.HIGH,
                "Action Required: Identity Verification Issue",
                emailBody,
                templateData
            );
            log.info("KYC failure email sent for verificationId={}", event.getVerificationId());
        } catch (Exception e) {
            log.error("Failed to send KYC failure email for verificationId={}", event.getVerificationId(), e);
        }

        // Priority 2: Push notification (if app installed)
        try {
            String pushMessage = buildPushMessage(category);
            notificationService.sendNotification(
                event.getUserId(),
                NotificationType.KYC_VERIFICATION_FAILED,
                NotificationChannel.PUSH,
                NotificationPriority.HIGH,
                "Identity Verification Required",
                pushMessage,
                templateData
            );
            log.info("KYC failure push notification sent for verificationId={}", event.getVerificationId());
        } catch (Exception e) {
            log.error("Failed to send KYC failure push notification", e);
        }

        // Priority 3: In-app notification (when user logs in)
        try {
            notificationService.sendNotification(
                event.getUserId(),
                NotificationType.KYC_VERIFICATION_FAILED,
                NotificationChannel.IN_APP,
                NotificationPriority.HIGH,
                "Complete Your Identity Verification",
                buildInAppMessage(category),
                templateData
            );
            log.info("KYC failure in-app notification created for verificationId={}", event.getVerificationId());
        } catch (Exception e) {
            log.error("Failed to create KYC failure in-app notification", e);
        }
    }

    /**
     * Build detailed email explaining KYC failure
     */
    private String buildKYCFailureEmail(KYCVerificationFailedEvent event, UserContactInfo userInfo, KYCFailureCategory category) {
        String instructions = getRemediationInstructions(category);
        String supportInfo = "Need help? Contact us at kyc@example.com or call 1-800-WAQITI";

        return String.format("""
            Dear %s,

            We were unable to verify your identity for your Waqiti account.

            VERIFICATION DETAILS:
            - Verification ID: %s
            - Submitted: %s
            - Attempt: %d of 5

            REASON FOR FAILURE:
            %s

            %s

            NEXT STEPS:
            1. Review the issue described above
            2. Prepare the correct documents
            3. Submit your verification again through the app
            4. Ensure all information matches exactly

            IMPORTANT REMINDERS:
            ✓ Use a clear, well-lit photo of your document
            ✓ Ensure all text is readable
            ✓ Make sure the document is not expired
            ✓ Use a government-issued ID (passport, driver's license, national ID)

            %s

            We're here to help you complete this process smoothly!

            Best regards,
            Waqiti Verification Team

            This is an automated notification. Please do not reply to this email.
            """,
            userInfo.getFullName(),
            event.getVerificationId(),
            event.getSubmittedAt(),
            event.getAttemptNumber(),
            event.getFailureReason(),
            instructions,
            supportInfo
        );
    }

    /**
     * Get specific remediation instructions based on failure category
     */
    private String getRemediationInstructions(KYCFailureCategory category) {
        return switch (category) {
            case DOCUMENT_EXPIRED ->
                """
                HOW TO FIX:
                Your document has expired. Please submit a valid, current government-issued ID.
                Acceptable documents:
                - Valid passport
                - Current driver's license
                - National ID card (not expired)
                """;

            case POOR_IMAGE_QUALITY ->
                """
                HOW TO FIX:
                The image quality was insufficient. Please:
                - Take a photo in good lighting
                - Ensure all text is clear and readable
                - Avoid shadows or glare
                - Use your phone's camera (not a scan of a photocopy)
                - Hold the camera steady
                """;

            case INFORMATION_MISMATCH ->
                """
                HOW TO FIX:
                The information on your document doesn't match your account details.
                Please ensure:
                - Your name matches exactly (including middle name)
                - Your date of birth matches
                - Your address is current
                Update your account information if needed, then resubmit.
                """;

            case UNSUPPORTED_DOCUMENT ->
                """
                HOW TO FIX:
                The document type you submitted is not accepted.
                Please submit ONE of the following:
                - Passport (preferred)
                - Driver's License
                - National ID Card
                - State ID Card
                Note: School IDs, library cards, and employee badges are not accepted.
                """;

            case INCOMPLETE_INFORMATION ->
                """
                HOW TO FIX:
                Some required information is missing. Please ensure:
                - Both front and back of ID are submitted (if applicable)
                - All corners of the document are visible
                - No information is cut off
                - The entire document is in frame
                """;

            case ADDRESS_VERIFICATION ->
                """
                HOW TO FIX:
                We need proof of your residential address. Please submit:
                - Utility bill (gas, electric, water) from last 3 months
                - Bank statement from last 3 months
                - Government letter or tax document
                - Lease agreement
                Note: Document must show your name and current address clearly.
                """;

            case OTHER ->
                """
                HOW TO FIX:
                Please review your submission and try again with:
                - A clear photo of a valid government ID
                - Accurate personal information
                - Current contact details
                If you continue to experience issues, please contact support.
                """;
        };
    }

    /**
     * Build push notification message
     */
    private String buildPushMessage(KYCFailureCategory category) {
        return switch (category) {
            case DOCUMENT_EXPIRED -> "Your ID has expired. Please submit a current document.";
            case POOR_IMAGE_QUALITY -> "We couldn't read your document. Please take a clearer photo.";
            case INFORMATION_MISMATCH -> "Your document info doesn't match your account. Please verify.";
            case UNSUPPORTED_DOCUMENT -> "Document type not accepted. Please use passport or driver's license.";
            case INCOMPLETE_INFORMATION -> "Missing information. Please submit complete documents.";
            case ADDRESS_VERIFICATION -> "Address verification failed. Please submit proof of residence.";
            default -> "Identity verification needs attention. Check your email for details.";
        };
    }

    /**
     * Build in-app notification message
     */
    private String buildInAppMessage(KYCFailureCategory category) {
        return "Identity verification incomplete. " + buildPushMessage(category) + " Tap to resubmit.";
    }

    /**
     * Build template data for notifications
     */
    private Map<String, Object> buildTemplateData(KYCVerificationFailedEvent event, UserContactInfo userInfo, KYCFailureCategory category) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", event.getUserId());
        data.put("userName", userInfo.getFullName());
        data.put("verificationId", event.getVerificationId());
        data.put("failureReason", event.getFailureReason());
        data.put("failureCategory", category.name());
        data.put("attemptNumber", event.getAttemptNumber());
        data.put("maxAttempts", 5);
        data.put("submittedAt", event.getSubmittedAt());
        data.put("supportEmail", "kyc@example.com");
        data.put("supportPhone", "1-800-927-8484");
        return data;
    }

    /**
     * Escalate repeated KYC failures
     */
    private void escalateRepeatedFailure(KYCVerificationFailedEvent event, UserContactInfo userInfo) {
        try {
            log.warn("REPEATED KYC FAILURE: userId={}, verificationId={}, attempts={}",
                event.getUserId(), event.getVerificationId(), event.getAttemptNumber());

            if (event.getAttemptNumber() >= 5) {
                // Max attempts reached - escalate to manual review
                log.error("MAX KYC ATTEMPTS REACHED: userId={}, verificationId={} - MANUAL REVIEW REQUIRED",
                    event.getUserId(), event.getVerificationId());

                notificationService.sendComplianceAlert(
                    "KYC Max Attempts Reached",
                    String.format("User %s (%s) has reached maximum KYC attempts. Manual review required. VerificationId: %s",
                        userInfo.getFullName(), event.getUserId(), event.getVerificationId())
                );

                metricsCollector.incrementCounter("notification.kyc.max.attempts.escalated");
            } else {
                // Warn user of limited remaining attempts
                notificationService.sendNotification(
                    event.getUserId(),
                    NotificationType.KYC_VERIFICATION_WARNING,
                    NotificationChannel.EMAIL,
                    NotificationPriority.HIGH,
                    "Important: Limited Verification Attempts Remaining",
                    String.format("You have %d attempt(s) remaining to complete identity verification. Need help? Contact us at kyc@example.com",
                        5 - event.getAttemptNumber()),
                    Map.of()
                );
            }

            metricsCollector.incrementCounter("notification.kyc.repeated.failure");
        } catch (Exception e) {
            log.error("Failed to escalate repeated KYC failure", e);
        }
    }

    /**
     * Handle business exceptions
     */
    private void handleBusinessException(KYCVerificationFailedEvent event, BusinessException e, Acknowledgment acknowledgment) {
        log.warn("Business validation failed for KYC failure {}: {}", event.getVerificationId(), e.getMessage());

        dlqHandler.sendToDLQ(
            TOPIC,
            event,
            e,
            "Business validation failed: " + e.getMessage()
        );

        acknowledgment.acknowledge();
    }

    /**
     * Handle critical exceptions
     */
    private void handleCriticalException(KYCVerificationFailedEvent event, Exception e, int partition, long offset, Acknowledgment acknowledgment) {
        log.error("CRITICAL: Failed to send KYC failure notification - sending to DLQ. userId={}, verificationId={}",
            event.getUserId(), event.getVerificationId(), e);

        dlqHandler.sendToDLQ(
            TOPIC,
            event,
            e,
            String.format("Critical failure at partition=%d, offset=%d: %s", partition, offset, e.getMessage())
        );

        acknowledgment.acknowledge();
    }

    // DTOs and Enums
    private static class KYCVerificationFailedEvent {
        private UUID userId;
        private String verificationId;
        private String failureReason;
        private int attemptNumber;
        private LocalDateTime submittedAt;
        private String documentType;

        // Getters
        public UUID getUserId() { return userId; }
        public String getVerificationId() { return verificationId; }
        public String getFailureReason() { return failureReason; }
        public int getAttemptNumber() { return attemptNumber; }
        public LocalDateTime getSubmittedAt() { return submittedAt; }
        public String getDocumentType() { return documentType; }
    }

    private static class UserContactInfo {
        private String fullName;
        private String email;
        private String phone;

        // Getters
        public String getFullName() { return fullName; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
    }

    private enum KYCFailureCategory {
        DOCUMENT_EXPIRED,
        POOR_IMAGE_QUALITY,
        INFORMATION_MISMATCH,
        UNSUPPORTED_DOCUMENT,
        INCOMPLETE_INFORMATION,
        ADDRESS_VERIFICATION,
        OTHER
    }
}
