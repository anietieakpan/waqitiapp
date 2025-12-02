package com.waqiti.websocket.controller;

import com.waqiti.websocket.dto.*;
import com.waqiti.websocket.service.NotificationBroadcastService;
import com.waqiti.websocket.service.PresenceService;
import com.waqiti.websocket.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket controller for handling real-time messaging
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@Validated
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationBroadcastService notificationService;
    private final PresenceService presenceService;
    private final RealtimeEventService eventService;
    
    // Track active subscriptions per user
    private final Map<String, Set<String>> userSubscriptions = new ConcurrentHashMap<>();
    
    /**
     * Handle user connection
     */
    @MessageMapping("/connect")
    @SendToUser("/queue/connected")
    public ConnectedMessage handleConnect(@AuthenticationPrincipal Principal principal,
                                        @Header("deviceId") String deviceId,
                                        @Header("platform") String platform) {
        String userId = principal.getName();
        log.info("User {} connected from {} device {}", userId, platform, deviceId);
        
        // Update presence
        presenceService.setUserOnline(userId, deviceId, platform);
        
        // Broadcast presence update
        broadcastPresenceUpdate(userId, true);
        
        return ConnectedMessage.builder()
            .userId(userId)
            .timestamp(Instant.now())
            .sessionId(deviceId)
            .build();
    }
    
    /**
     * Handle user disconnection
     */
    @MessageMapping("/disconnect")
    public void handleDisconnect(@AuthenticationPrincipal Principal principal,
                               @Header("deviceId") String deviceId) {
        String userId = principal.getName();
        log.info("User {} disconnected from device {}", userId, deviceId);
        
        // Update presence
        presenceService.setUserOffline(userId, deviceId);
        
        // Broadcast presence update
        broadcastPresenceUpdate(userId, false);
        
        // Clean up subscriptions
        userSubscriptions.remove(userId);
    }
    
    /**
     * Subscribe to real-time notifications
     */
    @SubscribeMapping("/queue/notifications")
    public void subscribeToNotifications(@AuthenticationPrincipal Principal principal) {
        String userId = principal.getName();
        log.debug("User {} subscribed to notifications", userId);
        
        // Send any pending notifications
        notificationService.sendPendingNotifications(userId);
    }
    
    /**
     * Subscribe to transaction updates
     */
    @SubscribeMapping("/queue/transactions")
    public void subscribeToTransactions(@AuthenticationPrincipal Principal principal) {
        String userId = principal.getName();
        log.debug("User {} subscribed to transaction updates", userId);
        
        addUserSubscription(userId, "transactions");
    }
    
    /**
     * Subscribe to payment request updates
     */
    @SubscribeMapping("/queue/payment-requests")
    public void subscribeToPaymentRequests(@AuthenticationPrincipal Principal principal) {
        String userId = principal.getName();
        log.debug("User {} subscribed to payment request updates", userId);
        
        addUserSubscription(userId, "payment-requests");
    }
    
    /**
     * Subscribe to friend presence updates
     */
    @SubscribeMapping("/topic/presence/{friendId}")
    public PresenceUpdate subscribeToFriendPresence(@AuthenticationPrincipal Principal principal,
                                                   @DestinationVariable String friendId) {
        String userId = principal.getName();
        log.debug("User {} subscribed to presence updates for {}", userId, friendId);
        
        // Return current presence status
        return presenceService.getUserPresence(friendId);
    }
    
    /**
     * Handle typing indicators for chat
     */
    @MessageMapping("/chat/typing")
    public void handleTyping(@AuthenticationPrincipal Principal principal,
                           @Valid @Payload TypingIndicator typing) {
        String userId = principal.getName();
        
        // Broadcast typing indicator to conversation participants
        messagingTemplate.convertAndSendToUser(
            typing.getRecipientId(),
            "/queue/typing",
            TypingUpdate.builder()
                .userId(userId)
                .conversationId(typing.getConversationId())
                .isTyping(typing.isTyping())
                .timestamp(Instant.now())
                .build()
        );
    }
    
    /**
     * Handle real-time chat messages
     */
    @MessageMapping("/chat/send")
    @SendToUser("/queue/messages")
    public ChatMessage handleChatMessage(@AuthenticationPrincipal Principal principal,
                                       @Valid @Payload ChatMessage message) {
        String userId = principal.getName();
        message.setSenderId(userId);
        message.setTimestamp(Instant.now());
        
        log.debug("Processing chat message from {} to {}", userId, message.getRecipientId());
        
        // Send to recipient
        messagingTemplate.convertAndSendToUser(
            message.getRecipientId(),
            "/queue/messages",
            message
        );
        
        // Send delivery confirmation back to sender
        return message.toBuilder()
            .status("delivered")
            .build();
    }
    
    /**
     * Handle payment status updates
     */
    @MessageMapping("/payment/status")
    public void handlePaymentStatusUpdate(@AuthenticationPrincipal Principal principal,
                                        @Valid @Payload PaymentStatusUpdate update) {
        String userId = principal.getName();
        
        // Verify user has permission to update this payment
        if (eventService.canUpdatePayment(userId, update.getPaymentId())) {
            // Broadcast to all participants
            update.getParticipantIds().forEach(participantId -> {
                messagingTemplate.convertAndSendToUser(
                    participantId,
                    "/queue/payment-updates",
                    update
                );
            });
        }
    }
    
    /**
     * Handle location sharing
     */
    @MessageMapping("/location/share")
    public void handleLocationShare(@AuthenticationPrincipal Principal principal,
                                  @Valid @Payload LocationShare location) {
        String userId = principal.getName();
        location.setUserId(userId);
        location.setTimestamp(Instant.now());
        
        // Broadcast to specified recipients
        location.getRecipientIds().forEach(recipientId -> {
            messagingTemplate.convertAndSendToUser(
                recipientId,
                "/queue/location-updates",
                location
            );
        });
    }
    
    /**
     * Handle group payment updates
     */
    @MessageMapping("/group/payment/update")
    public void handleGroupPaymentUpdate(@AuthenticationPrincipal Principal principal,
                                       @Valid @Payload GroupPaymentUpdate update) {
        String userId = principal.getName();
        
        // Broadcast to all group members
        messagingTemplate.convertAndSend(
            "/topic/group/" + update.getGroupId() + "/payments",
            update
        );
    }
    
    /**
     * Get active users (for admin monitoring)
     */
    @MessageMapping("/admin/active-users")
    @SendToUser("/queue/admin/active-users")
    public ActiveUsersResponse getActiveUsers(@AuthenticationPrincipal Principal principal) {
        String userId = principal.getName();
        
        // Verify admin privileges
        if (eventService.isAdmin(userId)) {
            return ActiveUsersResponse.builder()
                .activeUsers(presenceService.getActiveUsers())
                .totalCount(presenceService.getActiveUserCount())
                .timestamp(Instant.now())
                .build();
        }
        
        throw new SecurityException("Unauthorized access to admin endpoint");
    }
    
    /**
     * Handle real-time analytics events
     */
    @MessageMapping("/analytics/event")
    public void handleAnalyticsEvent(@AuthenticationPrincipal Principal principal,
                                   @Valid @Payload AnalyticsEvent event) {
        String userId = principal.getName();
        event.setUserId(userId);
        event.setTimestamp(Instant.now());
        
        // Process analytics event
        eventService.processAnalyticsEvent(event);
    }
    
    /**
     * Exception handler for WebSocket errors
     */
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public ErrorMessage handleException(Principal principal, Exception exception) {
        log.error("WebSocket error for user {}: {}", 
            principal != null ? principal.getName() : "anonymous", 
            exception.getMessage(), 
            exception);
        
        return ErrorMessage.builder()
            .error(exception.getMessage())
            .timestamp(Instant.now())
            .build();
    }
    
    private void broadcastPresenceUpdate(String userId, boolean isOnline) {
        PresenceUpdate update = PresenceUpdate.builder()
            .userId(userId)
            .isOnline(isOnline)
            .lastSeen(Instant.now())
            .build();
        
        // Get user's friends and broadcast to them
        Set<String> friends = presenceService.getUserFriends(userId);
        friends.forEach(friendId -> {
            messagingTemplate.convertAndSendToUser(
                friendId,
                "/queue/presence-updates",
                update
            );
        });
    }
    
    private void addUserSubscription(String userId, String subscription) {
        userSubscriptions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
            .add(subscription);
    }
}