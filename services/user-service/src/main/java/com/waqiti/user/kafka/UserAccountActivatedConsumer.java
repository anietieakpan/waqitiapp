package com.waqiti.user.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.user.event.UserAccountActivatedEvent;
import com.waqiti.user.service.UserAccountActivationService;
import com.waqiti.user.service.NotificationService;
import com.waqiti.user.service.UserActivityLogService;
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
 * Production-grade Kafka consumer for user account activation events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAccountActivatedConsumer {

    private final UserAccountActivationService activationService;
    private final NotificationService notificationService;
    private final UserActivityLogService activityLogService;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "user-account-activated", groupId = "user-activation-processor")
    public void processAccountActivation(ConsumerRecord<String, UserAccountActivatedEvent> record,
                                       @Payload UserAccountActivatedEvent event,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                       @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                       @Header(KafkaHeaders.OFFSET) long offset,
                                       Acknowledgment acknowledgment) {
        try {
            log.info("Processing account activation event for user: {} method: {}", 
                    event.getUserId(), event.getActivationMethod());
            
            // Validate event
            validateActivationEvent(event);
            
            // Process activation
            activationService.processAccountActivation(
                event.getUserId(),
                event.getEmail(),
                event.getUsername(),
                event.getActivationMethod(),
                event.getActivatedAt()
            );
            
            // Update user profile
            activationService.updateUserProfile(
                event.getUserId(),
                event.getFirstName(),
                event.getLastName(),
                event.getPhoneNumber()
            );
            
            // Handle KYC status
            if (event.isKycCompleted()) {
                activationService.updateKycStatus(
                    event.getUserId(),
                    event.getKycStatus()
                );
            }
            
            // Apply referral benefits if applicable
            if (event.getReferralCode() != null) {
                activationService.processReferralRewards(
                    event.getUserId(),
                    event.getReferralCode(),
                    event.getReferredBy()
                );
            }
            
            // Send welcome notifications
            notificationService.sendWelcomeEmail(
                event.getUserId(),
                event.getEmail(),
                event.getFirstName(),
                event.getAccountType()
            );
            
            // Send onboarding materials
            notificationService.sendOnboardingMaterials(
                event.getUserId(),
                event.getEmail(),
                event.getAccountType()
            );
            
            // Log activation activity
            activityLogService.logAccountActivation(
                event.getUserId(),
                event.getActivationMethod(),
                event.getIpAddress(),
                event.getDeviceId(),
                event.getActivatedAt()
            );
            
            // Schedule follow-up engagement
            activationService.scheduleEngagementCampaign(
                event.getUserId(),
                event.getEmail(),
                event.getAccountType()
            );
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed account activation for user: {}", event.getUserId());
            
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

    private void validateActivationEvent(UserAccountActivatedEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required for account activation");
        }
        
        if (event.getActivationMethod() == null || event.getActivationMethod().trim().isEmpty()) {
            throw new IllegalArgumentException("Activation method is required");
        }
        
        if (event.getEmail() == null && event.getPhoneNumber() == null) {
            throw new IllegalArgumentException("Either email or phone number is required for activation");
        }
    }
}