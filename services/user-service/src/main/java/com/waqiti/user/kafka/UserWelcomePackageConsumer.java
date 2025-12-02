package com.waqiti.user.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.user.event.UserWelcomePackageEvent;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.NotificationService;
import com.waqiti.user.service.CustomerNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Production-grade Kafka consumer for user welcome package events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserWelcomePackageConsumer {

    private final UserService userService;
    private final NotificationService notificationService;
    private final CustomerNotificationService customerNotificationService;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "user-welcome-package", groupId = "welcome-package-processor")
    public void processWelcomePackage(ConsumerRecord<String, UserWelcomePackageEvent> record,
                                    @Payload UserWelcomePackageEvent event,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                    @Header(KafkaHeaders.OFFSET) long offset,
                                    Acknowledgment acknowledgment) {
        try {
            log.info("Processing welcome package for user: {} type: {} bonus: {}", 
                    event.getUserId(), event.getPackageType(), event.getWelcomeBonus());
            
            // Validate event
            validateWelcomePackageEvent(event);
            
            // Apply welcome package benefits
            applyWelcomePackageBenefits(event);
            
            // Send welcome communications
            sendWelcomeCommunications(event);
            
            // Setup initial configurations
            setupInitialConfigurations(event);
            
            // Schedule onboarding activities
            scheduleOnboardingActivities(event);
            
            // Process referral rewards if applicable
            if (event.getReferredBy() != null) {
                processReferralRewards(event);
            }
            
            // Track welcome package activation
            userService.trackWelcomePackageActivation(
                event.getUserId(),
                event.getPackageType(),
                event.getPackageActivatedAt()
            );
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed welcome package for user: {}", event.getUserId());
            
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

    private void validateWelcomePackageEvent(UserWelcomePackageEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required for welcome package");
        }
        
        if (event.getPackageType() == null || event.getPackageType().trim().isEmpty()) {
            throw new IllegalArgumentException("Package type is required");
        }
        
        if (event.getEmail() == null || event.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required for welcome package");
        }
    }

    private void applyWelcomePackageBenefits(UserWelcomePackageEvent event) {
        // Apply welcome bonus if present
        if (event.getWelcomeBonus() != null && event.getWelcomeBonus().compareTo(java.math.BigDecimal.ZERO) > 0) {
            userService.creditWelcomeBonus(
                event.getUserId(),
                event.getWelcomeBonus(),
                event.getCurrency()
            );
        }
        
        // Enable included features
        if (event.getIncludedFeatures() != null && !event.getIncludedFeatures().isEmpty()) {
            userService.enableFeatures(
                event.getUserId(),
                event.getIncludedFeatures(),
                event.getPackageExpiresAt()
            );
        }
        
        // Apply promotional offers
        if (event.getPromotions() != null && !event.getPromotions().isEmpty()) {
            userService.applyPromotions(
                event.getUserId(),
                event.getPromotions()
            );
        }
        
        // Set account benefits based on package type
        userService.setAccountBenefits(
            event.getUserId(),
            event.getPackageType(),
            event.getAccountType()
        );
    }

    private void sendWelcomeCommunications(UserWelcomePackageEvent event) {
        // Send welcome email
        if (!event.isEmailSent()) {
            notificationService.sendWelcomeEmail(
                event.getUserId(),
                event.getEmail(),
                event.getFirstName(),
                event.getPackageType(),
                event.getWelcomeBonus(),
                event.getIncludedFeatures()
            );
            event.setEmailSent(true);
        }
        
        // Send welcome SMS if phone available
        if (!event.isSmsSent() && event.getUsername() != null) {
            customerNotificationService.sendWelcomeSMS(
                event.getUserId(),
                event.getFirstName(),
                event.getPackageType()
            );
            event.setSmsSent(true);
        }
        
        // Send push notification for mobile users
        if (!event.isPushNotificationSent()) {
            customerNotificationService.sendWelcomePushNotification(
                event.getUserId(),
                event.getFirstName(),
                event.getPackageType()
            );
            event.setPushNotificationSent(true);
        }
        
        // Send personalized content
        if (event.getPersonalizedContent() != null) {
            notificationService.sendPersonalizedWelcomeContent(
                event.getUserId(),
                event.getEmail(),
                event.getPersonalizedContent()
            );
        }
    }

    private void setupInitialConfigurations(UserWelcomePackageEvent event) {
        // Setup default preferences
        userService.setupDefaultPreferences(
            event.getUserId(),
            event.getRegion(),
            event.getCurrency()
        );
        
        // Configure initial limits based on package
        userService.configureInitialLimits(
            event.getUserId(),
            event.getPackageType(),
            event.getAccountType()
        );
        
        // Enable tutorials if first-time user
        if (event.isFirstTimeUser() && event.getTutorialSteps() != null) {
            userService.enableTutorials(
                event.getUserId(),
                event.getTutorialSteps()
            );
        }
        
        // Setup dashboard widgets
        userService.setupDashboardWidgets(
            event.getUserId(),
            event.getPackageType()
        );
    }

    private void scheduleOnboardingActivities(UserWelcomePackageEvent event) {
        // Schedule onboarding emails
        notificationService.scheduleOnboardingEmailSeries(
            event.getUserId(),
            event.getEmail(),
            event.getFirstName(),
            event.getPackageType(),
            event.getPackageActivatedAt()
        );
        
        // Schedule feature discovery prompts
        userService.scheduleFeatureDiscovery(
            event.getUserId(),
            event.getIncludedFeatures(),
            event.getPackageActivatedAt()
        );
        
        // Schedule engagement check-ins
        customerNotificationService.scheduleEngagementCheckIns(
            event.getUserId(),
            event.getPackageActivatedAt()
        );
        
        // Schedule package expiry reminder
        if (event.getPackageExpiresAt() != null) {
            notificationService.schedulePackageExpiryReminder(
                event.getUserId(),
                event.getEmail(),
                event.getPackageExpiresAt()
            );
        }
    }

    private void processReferralRewards(UserWelcomePackageEvent event) {
        // Credit referral bonus to new user
        if (event.getReferralBonus() != null) {
            userService.creditReferralBonus(
                event.getUserId(),
                event.getReferralBonus(),
                event.getCurrency(),
                "REFERRED_USER_BONUS"
            );
        }
        
        // Credit referral reward to referrer
        userService.creditReferrerReward(
            event.getReferredBy(),
            event.getUserId(),
            event.getCurrency()
        );
        
        // Update referral tracking
        userService.updateReferralTracking(
            event.getReferralCode(),
            event.getUserId(),
            event.getReferredBy()
        );
        
        // Send referral success notifications
        notificationService.sendReferralSuccessNotifications(
            event.getUserId(),
            event.getReferredBy(),
            event.getReferralBonus()
        );
    }
}