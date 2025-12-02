package com.waqiti.user.events;

import com.waqiti.user.service.UserService;
import com.waqiti.user.dto.UserStatusUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User Status Update Event Consumer
 * 
 * Processes user status update events and triggers appropriate actions
 * Handles events from user-status-updated topic
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserStatusUpdateEventConsumer {

    private final UserService userService;
    private final ObjectMapper objectMapper;

    /**
     * Processes user status update events
     */
    @KafkaListener(
        topics = "user-status-updated",
        groupId = "user-service-status-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleUserStatusUpdate(
            @Payload UserStatusUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Processing user status update event: {} for user: {}", 
            event.getEventType(), event.getUserId());

        try {
            switch (event.getEventType()) {
                case "USER_ACTIVATED":
                    handleUserActivation(event);
                    break;
                case "USER_DEACTIVATED":
                    handleUserDeactivation(event);
                    break;
                case "USER_SUSPENDED":
                    handleUserSuspension(event);
                    break;
                case "USER_VERIFIED":
                    handleUserVerification(event);
                    break;
                case "USER_BLOCKED":
                    handleUserBlocking(event);
                    break;
                default:
                    log.warn("Unknown user status event type: {}", event.getEventType());
            }

            // Acknowledge successful processing
            acknowledgment.acknowledge();
            log.debug("Successfully processed user status update for user: {}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to process user status update event for user: {}", 
                event.getUserId(), e);
            // Don't acknowledge - this will trigger retry
            throw e;
        }
    }

    /**
     * Handles user activation events
     */
    private void handleUserActivation(UserStatusUpdateEvent event) {
        log.info("Activating user: {}", event.getUserId());
        
        userService.activateUser(event.getUserId());
        
        // Trigger welcome email or onboarding flow
        userService.triggerWelcomeFlow(event.getUserId());
        
        // Update user analytics
        userService.recordUserStatusChange(event.getUserId(), "ACTIVATED", event.getMetadata());
    }

    /**
     * Handles user deactivation events
     */
    private void handleUserDeactivation(UserStatusUpdateEvent event) {
        log.info("Deactivating user: {}", event.getUserId());
        
        userService.deactivateUser(event.getUserId());
        
        // Cancel active sessions
        userService.cancelUserSessions(event.getUserId());
        
        // Send deactivation confirmation
        userService.sendDeactivationNotification(event.getUserId());
        
        // Update user analytics
        userService.recordUserStatusChange(event.getUserId(), "DEACTIVATED", event.getMetadata());
    }

    /**
     * Handles user suspension events
     */
    private void handleUserSuspension(UserStatusUpdateEvent event) {
        log.warn("Suspending user: {} for reason: {}", 
            event.getUserId(), event.getMetadata().get("reason"));
        
        userService.suspendUser(event.getUserId(), (String) event.getMetadata().get("reason"));
        
        // Cancel active sessions immediately
        userService.cancelUserSessions(event.getUserId());
        
        // Freeze user assets if applicable
        userService.freezeUserAssets(event.getUserId());
        
        // Send suspension notification
        userService.sendSuspensionNotification(event.getUserId(), 
            (String) event.getMetadata().get("reason"));
        
        // Record compliance event
        userService.recordComplianceEvent(event.getUserId(), "USER_SUSPENDED", event.getMetadata());
    }

    /**
     * Handles user verification events
     */
    private void handleUserVerification(UserStatusUpdateEvent event) {
        log.info("Verifying user: {}", event.getUserId());
        
        userService.verifyUser(event.getUserId());
        
        // Unlock additional features
        userService.unlockVerifiedUserFeatures(event.getUserId());
        
        // Send verification confirmation
        userService.sendVerificationConfirmation(event.getUserId());
        
        // Update user tier/limits
        userService.updateUserLimits(event.getUserId());
        
        // Record verification event
        userService.recordUserStatusChange(event.getUserId(), "VERIFIED", event.getMetadata());
    }

    /**
     * Handles user blocking events
     */
    private void handleUserBlocking(UserStatusUpdateEvent event) {
        log.warn("Blocking user: {} for reason: {}", 
            event.getUserId(), event.getMetadata().get("reason"));
        
        userService.blockUser(event.getUserId(), (String) event.getMetadata().get("reason"));
        
        // Immediate session termination
        userService.terminateAllUserSessions(event.getUserId());
        
        // Freeze all user accounts
        userService.freezeAllUserAccounts(event.getUserId());
        
        // Send blocking notification
        userService.sendBlockingNotification(event.getUserId(), 
            (String) event.getMetadata().get("reason"));
        
        // Alert security team
        userService.alertSecurityTeam(event.getUserId(), "USER_BLOCKED", event.getMetadata());
        
        // Record security event
        userService.recordSecurityEvent(event.getUserId(), "USER_BLOCKED", event.getMetadata());
    }
}