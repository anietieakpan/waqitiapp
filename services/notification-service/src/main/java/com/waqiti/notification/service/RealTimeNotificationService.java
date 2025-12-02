package com.waqiti.notification.service;

import com.waqiti.notification.dto.*;
import com.waqiti.notification.model.*;
import com.waqiti.notification.repository.*;
import com.waqiti.notification.websocket.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealTimeNotificationService {
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRepository notificationRepository;
    private final UserDeviceRepository deviceRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final PushNotificationService pushNotificationService;
    private final EmailNotificationService emailNotificationService;
    private final SMSNotificationService smsNotificationService;
    private final NotificationTemplateService templateService;
    
    // Track online users for real-time delivery
    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();
    private final Map<String, UserPresence> userPresence = new ConcurrentHashMap<>();

    @Transactional
    public Mono<NotificationResult> sendNotification(NotificationRequest request) {
        return Mono.fromCallable(() -> {
            log.info("Sending notification to user: {} with type: {}", 
                    request.getUserId(), request.getType());

            // Check user preferences
            NotificationPreference preferences = preferenceRepository
                    .findByUserId(request.getUserId())
                    .orElse(NotificationPreference.defaultPreferences(request.getUserId()));

            if (!isNotificationEnabled(preferences, request.getType(), request.getChannel())) {
                log.info("Notification disabled by user preferences");
                return NotificationResult.skipped("Disabled by user preferences");
            }

            // Create notification record
            Notification notification = createNotification(request);
            notification = notificationRepository.save(notification);

            // Send through multiple channels based on preferences and priority
            List<NotificationChannel> channels = determineChannels(request, preferences);
            Map<NotificationChannel, DeliveryStatus> deliveryResults = new HashMap<>();

            for (NotificationChannel channel : channels) {
                DeliveryStatus status = sendThroughChannel(notification, channel, request);
                deliveryResults.put(channel, status);
            }

            // Update notification with delivery results
            notification.setDeliveryStatus(deliveryResults);
            notification.setDeliveredAt(
                    deliveryResults.values().stream()
                            .anyMatch(status -> status == DeliveryStatus.DELIVERED)
                            ? LocalDateTime.now()
                            : null
            );
            notificationRepository.save(notification);

            return NotificationResult.success(notification.getId(), deliveryResults);
        })
        .doOnError(error -> log.error("Error sending notification", error))
        .onErrorReturn(NotificationResult.failed("Internal error"));
    }

    @Transactional
    public Flux<NotificationResult> sendBulkNotifications(BulkNotificationRequest request) {
        return Flux.fromIterable(request.getUserIds())
                .parallel()
                .runOn(reactor.core.scheduler.Schedulers.parallel())
                .flatMap(userId -> {
                    NotificationRequest userRequest = NotificationRequest.builder()
                            .userId(userId)
                            .type(request.getType())
                            .title(request.getTitle())
                            .message(request.getMessage())
                            .data(request.getData())
                            .priority(request.getPriority())
                            .channel(request.getChannel())
                            .build();
                    return sendNotification(userRequest);
                })
                .sequential();
    }

    private DeliveryStatus sendThroughChannel(
            Notification notification, 
            NotificationChannel channel,
            NotificationRequest request) {
        
        try {
            switch (channel) {
                case IN_APP:
                    return sendInAppNotification(notification);
                    
                case PUSH:
                    return sendPushNotification(notification, request);
                    
                case EMAIL:
                    return sendEmailNotification(notification, request);
                    
                case SMS:
                    return sendSMSNotification(notification, request);
                    
                case WEBSOCKET:
                    return sendWebSocketNotification(notification);
                    
                default:
                    log.warn("Unknown notification channel: {}", channel);
                    return DeliveryStatus.FAILED;
            }
        } catch (Exception e) {
            log.error("Error sending notification through channel: {}", channel, e);
            return DeliveryStatus.FAILED;
        }
    }

    private DeliveryStatus sendInAppNotification(Notification notification) {
        // In-app notifications are stored in DB and shown when user opens app
        notification.getChannels().add(NotificationChannel.IN_APP);
        return DeliveryStatus.DELIVERED;
    }

    private DeliveryStatus sendWebSocketNotification(Notification notification) {
        String userId = notification.getUserId();
        
        // Check if user is online
        if (!isUserOnline(userId)) {
            log.debug("User {} is not online for WebSocket delivery", userId);
            return DeliveryStatus.PENDING;
        }

        // Send to all user's active sessions
        WebSocketNotification wsNotification = WebSocketNotification.builder()
                .id(notification.getId())
                .type(notification.getType().toString())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .data(notification.getData())
                .timestamp(notification.getCreatedAt())
                .priority(notification.getPriority().toString())
                .actions(notification.getActions())
                .build();

        messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/notifications",
                wsNotification
        );

        // Send presence update if needed
        updateUserPresence(userId, UserPresence.Status.ACTIVE);

        return DeliveryStatus.DELIVERED;
    }

    private DeliveryStatus sendPushNotification(Notification notification, NotificationRequest request) {
        List<UserDevice> devices = deviceRepository.findActiveDevicesByUserId(notification.getUserId());
        
        if (devices.isEmpty()) {
            log.info("No registered devices for user: {}", notification.getUserId());
            return DeliveryStatus.NO_DEVICE;
        }

        boolean anySuccess = false;
        for (UserDevice device : devices) {
            PushNotificationRequest pushRequest = PushNotificationRequest.builder()
                    .token(device.getPushToken())
                    .platform(device.getPlatform())
                    .title(notification.getTitle())
                    .body(notification.getMessage())
                    .data(notification.getData())
                    .badge(getUnreadCount(notification.getUserId()))
                    .sound(determineSoundForType(notification.getType()))
                    .priority(mapPriority(notification.getPriority()))
                    .build();

            try {
                pushNotificationService.sendPushNotification(pushRequest);
                anySuccess = true;
            } catch (Exception e) {
                log.error("Failed to send push to device: {}", device.getId(), e);
                // Mark device as inactive if token is invalid
                if (e.getMessage().contains("Invalid token")) {
                    device.setActive(false);
                    deviceRepository.save(device);
                }
            }
        }

        return anySuccess ? DeliveryStatus.DELIVERED : DeliveryStatus.FAILED;
    }

    private DeliveryStatus sendEmailNotification(Notification notification, NotificationRequest request) {
        // Get email template
        EmailTemplate template = templateService.getEmailTemplate(notification.getType());
        
        EmailNotificationRequest emailRequest = EmailNotificationRequest.builder()
                .to(getUserEmail(notification.getUserId()))
                .subject(template.renderSubject(notification))
                .body(template.renderBody(notification))
                .isHtml(true)
                .priority(notification.getPriority() == NotificationPriority.URGENT)
                .build();

        return emailNotificationService.sendEmail(emailRequest) 
                ? DeliveryStatus.DELIVERED 
                : DeliveryStatus.FAILED;
    }

    private DeliveryStatus sendSMSNotification(Notification notification, NotificationRequest request) {
        String phoneNumber = getUserPhoneNumber(notification.getUserId());
        if (phoneNumber == null) {
            return DeliveryStatus.NO_CONTACT;
        }

        SMSNotificationRequest smsRequest = SMSNotificationRequest.builder()
                .to(phoneNumber)
                .message(truncateForSMS(notification.getMessage()))
                .priority(notification.getPriority() == NotificationPriority.URGENT)
                .build();

        return smsNotificationService.sendSMS(smsRequest)
                ? DeliveryStatus.DELIVERED
                : DeliveryStatus.FAILED;
    }

    public void handleUserConnection(String userId, String sessionId) {
        log.info("User {} connected with session {}", userId, sessionId);
        
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
        
        updateUserPresence(userId, UserPresence.Status.ONLINE);
        
        // Send any pending notifications
        sendPendingNotifications(userId);
    }

    public void handleUserDisconnection(String userId, String sessionId) {
        log.info("User {} disconnected session {}", userId, sessionId);
        
        Set<String> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
                updateUserPresence(userId, UserPresence.Status.OFFLINE);
            }
        }
    }

    private void sendPendingNotifications(String userId) {
        List<Notification> pendingNotifications = notificationRepository
                .findPendingNotificationsByUserId(userId);
        
        for (Notification notification : pendingNotifications) {
            if (sendWebSocketNotification(notification) == DeliveryStatus.DELIVERED) {
                notification.getDeliveryStatus().put(NotificationChannel.WEBSOCKET, DeliveryStatus.DELIVERED);
                notification.setDeliveredAt(LocalDateTime.now());
                notificationRepository.save(notification);
            }
        }
    }

    public void updateUserPresence(String userId, UserPresence.Status status) {
        UserPresence presence = userPresence.computeIfAbsent(userId, k -> new UserPresence(userId));
        presence.setStatus(status);
        presence.setLastSeen(LocalDateTime.now());
        
        // Broadcast presence update to user's contacts
        broadcastPresenceUpdate(userId, presence);
    }

    private void broadcastPresenceUpdate(String userId, UserPresence presence) {
        PresenceUpdate update = PresenceUpdate.builder()
                .userId(userId)
                .status(presence.getStatus().toString())
                .lastSeen(presence.getLastSeen())
                .build();
        
        // Get user's contacts/friends
        Set<String> contacts = getUserContacts(userId);
        
        for (String contactId : contacts) {
            if (isUserOnline(contactId)) {
                messagingTemplate.convertAndSendToUser(
                        contactId,
                        "/queue/presence",
                        update
                );
            }
        }
    }

    @Transactional
    public void markAsRead(String userId, List<String> notificationIds) {
        List<Notification> notifications = notificationRepository.findAllById(notificationIds);
        
        for (Notification notification : notifications) {
            if (notification.getUserId().equals(userId) && !notification.isRead()) {
                notification.setRead(true);
                notification.setReadAt(LocalDateTime.now());
            }
        }
        
        notificationRepository.saveAll(notifications);
        
        // Send read receipt through WebSocket
        if (isUserOnline(userId)) {
            ReadReceipt receipt = ReadReceipt.builder()
                    .notificationIds(notificationIds)
                    .readAt(LocalDateTime.now())
                    .build();
            
            messagingTemplate.convertAndSendToUser(
                    userId,
                    "/queue/read-receipts",
                    receipt
            );
        }
    }

    private Notification createNotification(NotificationRequest request) {
        return Notification.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .title(request.getTitle())
                .message(request.getMessage())
                .data(request.getData())
                .priority(request.getPriority() != null ? request.getPriority() : NotificationPriority.MEDIUM)
                .channels(new HashSet<>())
                .actions(request.getActions())
                .expiresAt(request.getExpiresAt())
                .deliveryStatus(new HashMap<>())
                .build();
    }

    private List<NotificationChannel> determineChannels(
            NotificationRequest request, 
            NotificationPreference preferences) {
        
        List<NotificationChannel> channels = new ArrayList<>();
        
        // Always include in-app
        channels.add(NotificationChannel.IN_APP);
        
        // Add WebSocket if user is online
        if (isUserOnline(request.getUserId())) {
            channels.add(NotificationChannel.WEBSOCKET);
        }
        
        // For urgent notifications, use all available channels
        if (request.getPriority() == NotificationPriority.URGENT) {
            if (preferences.isPushEnabled()) channels.add(NotificationChannel.PUSH);
            if (preferences.isEmailEnabled()) channels.add(NotificationChannel.EMAIL);
            if (preferences.isSmsEnabled()) channels.add(NotificationChannel.SMS);
        } else {
            // Use channels based on user preferences
            if (preferences.isPushEnabled() && shouldSendPush(request.getType(), preferences)) {
                channels.add(NotificationChannel.PUSH);
            }
            if (preferences.isEmailEnabled() && shouldSendEmail(request.getType(), preferences)) {
                channels.add(NotificationChannel.EMAIL);
            }
            if (preferences.isSmsEnabled() && shouldSendSMS(request.getType(), preferences)) {
                channels.add(NotificationChannel.SMS);
            }
        }
        
        // Override with specific channel if requested
        if (request.getChannel() != null) {
            channels.clear();
            channels.add(request.getChannel());
            channels.add(NotificationChannel.IN_APP); // Always keep in-app
        }
        
        return channels;
    }

    private boolean isUserOnline(String userId) {
        return userSessions.containsKey(userId) && !userSessions.get(userId).isEmpty();
    }

    private int getUnreadCount(String userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    private String determineSoundForType(NotificationType type) {
        return switch (type) {
            case PAYMENT_RECEIVED -> "payment_received.wav";
            case PAYMENT_SENT -> "payment_sent.wav";
            case SECURITY_ALERT -> "security_alert.wav";
            default -> "default.wav";
        };
    }

    private String truncateForSMS(String message) {
        return message.length() > 160 ? message.substring(0, 157) + "..." : message;
    }

    private boolean isNotificationEnabled(
            NotificationPreference preferences,
            NotificationType type,
            NotificationChannel channel) {
        
        // Check global settings
        if (!preferences.isEnabled()) return false;
        
        // Check do not disturb
        if (preferences.isDoNotDisturbEnabled() && isInDoNotDisturbPeriod(preferences)) {
            return false;
        }
        
        // Check type-specific settings
        return preferences.getTypeSettings().getOrDefault(type, true);
    }

    private boolean isInDoNotDisturbPeriod(NotificationPreference preferences) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dndStart = preferences.getDoNotDisturbStart();
        LocalDateTime dndEnd = preferences.getDoNotDisturbEnd();
        
        if (dndStart == null || dndEnd == null) return false;
        
        // Handle overnight DND (e.g., 22:00 - 08:00)
        if (dndStart.isAfter(dndEnd)) {
            return now.isAfter(dndStart) || now.isBefore(dndEnd);
        } else {
            return now.isAfter(dndStart) && now.isBefore(dndEnd);
        }
    }

    // Helper methods
    private String getUserEmail(String userId) {
        // Implementation to fetch user email
        return userService.getUserById(userId)
                .map(User::getEmail)
                .orElse(null);
    }

    private String getUserPhoneNumber(String userId) {
        // Implementation to fetch user phone
        return userService.getUserById(userId)
                .map(User::getPhoneNumber)
                .orElse(null);
    }

    private Set<String> getUserContacts(String userId) {
        // Implementation to fetch user contacts/friends
        return contactService.getUserContacts(userId);
    }

    private boolean shouldSendPush(NotificationType type, NotificationPreference preferences) {
        return preferences.getPushSettings().getOrDefault(type, true);
    }

    private boolean shouldSendEmail(NotificationType type, NotificationPreference preferences) {
        return preferences.getEmailSettings().getOrDefault(type, false);
    }

    private boolean shouldSendSMS(NotificationType type, NotificationPreference preferences) {
        return preferences.getSmsSettings().getOrDefault(type, false);
    }

    private PushPriority mapPriority(NotificationPriority priority) {
        return switch (priority) {
            case LOW -> PushPriority.LOW;
            case MEDIUM -> PushPriority.NORMAL;
            case HIGH, URGENT -> PushPriority.HIGH;
        };
    }
}