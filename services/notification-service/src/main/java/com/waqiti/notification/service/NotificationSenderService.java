/**
 * File: src/main/java/com/waqiti/notification/service/NotificationSenderService.java
 */
package com.waqiti.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.waqiti.notification.domain.Notification;
import com.waqiti.notification.domain.NotificationPreferences;
import com.waqiti.notification.repository.NotificationPreferencesRepository;
import com.waqiti.notification.service.provider.SmsProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class NotificationSenderService {
    private final NotificationPreferencesRepository preferencesRepository;
    private final JavaMailSender mailSender;
    private final PushNotificationService pushNotificationService;

    // Not final anymore to allow for testing
    private FirebaseMessaging firebaseMessaging;

    @Autowired
    public NotificationSenderService(
            NotificationPreferencesRepository preferencesRepository,
            JavaMailSender mailSender,
            PushNotificationService pushNotificationService,
            @Nullable FirebaseMessaging firebaseMessaging) {
        this.preferencesRepository = preferencesRepository;
        this.mailSender = mailSender;
        this.pushNotificationService = pushNotificationService;
        this.firebaseMessaging = firebaseMessaging;
    }

    /**
     * Setter for Firebase messaging - allows for testing with mocks
     * This method is package-private for testing purposes but not meant for general use
     */
    /* package */ void setFirebaseMessaging(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    /**
     * Sends an email notification
     */
    public boolean sendEmailNotification(Notification notification, String subject, String body) {
        log.info("Attempting to send email notification for notification ID: {}",
                notification.getId() != null ? notification.getId() : "N/A");

        // Fetch user preferences
        NotificationPreferences preferences = preferencesRepository.findById(notification.getUserId())
                .orElse(null);

        // Validate preferences and email settings
        if (preferences == null || !preferences.isEmailNotificationsEnabled()) {
            log.warn("Email notifications disabled or no preferences found for user: {}",
                    notification.getUserId());
            return false;
        }

        // Validate email address
        if (preferences.getEmail() == null || preferences.getEmail().isEmpty()) {
            log.warn("No email address found for user: {}", notification.getUserId());
            return false;
        }

        try {
            // If subject or body is null, use title/message from notification
            String finalSubject = subject != null ? subject : notification.getTitle();
            String finalBody = body != null ? body : notification.getMessage();

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(preferences.getEmail());
            helper.setSubject(finalSubject);
            helper.setText(finalBody, true); // true = HTML content

            mailSender.send(message);

            log.info("Email notification sent successfully: {}", notification.getId());
            return true;
        } catch (Exception e) {
            log.error("Error sending email notification: {}. Error: {}",
                    notification.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sends an SMS notification
     */
    public boolean sendSmsNotification(Notification notification, String smsText) {
        log.info("Attempting to send SMS notification for notification ID: {}",
                notification.getId() != null ? notification.getId() : "N/A");

        // Fetch user preferences
        NotificationPreferences preferences = preferencesRepository.findById(notification.getUserId())
                .orElse(null);

        // Validate preferences and SMS settings
        if (preferences == null || !preferences.isSmsNotificationsEnabled()) {
            log.warn("SMS notifications disabled or no preferences found for user: {}",
                    notification.getUserId());
            return false;
        }

        // Validate phone number
        if (preferences.getPhoneNumber() == null || preferences.getPhoneNumber().isEmpty()) {
            log.warn("No phone number found for user: {}", notification.getUserId());
            return false;
        }

        try {
            // If smsText is null, use message from notification
            String finalSmsText = smsText != null ? smsText : notification.getMessage();

            // Send SMS through the provider
            SmsProvider smsProvider = new SmsProvider();
            String messageSid = smsProvider.sendSms(preferences.getPhoneNumber(), finalSmsText);

            // Check if the message was sent successfully
            boolean success = messageSid != null;

            if (success) {
                log.info("SMS notification sent successfully: {}, SID: {}",
                        notification.getId(), messageSid);
            } else {
                log.error("Failed to send SMS notification: {}", notification.getId());
            }

            return success;
        } catch (Exception e) {
            log.error("Error sending SMS notification: {}. Error: {}",
                    notification.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sends a push notification using the comprehensive PushNotificationService
     */
    public boolean sendPushNotification(Notification notification) {
        log.info("Attempting to send push notification for notification ID: {}",
                notification.getId() != null ? notification.getId() : "N/A");

        try {
            // Use the comprehensive push notification service
            return pushNotificationService.sendPushNotification(
                notification.getUserId(), 
                notification.getTitle(),
                notification.getContent(),
                null, // data payload
                notification.getPriority() != null ? notification.getPriority().toString() : "normal"
            );
        } catch (Exception e) {
            log.error("Error sending push notification: {}. Error: {}",
                    notification.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Legacy push notification method - will be replaced with PushNotificationService integration
     */
    private boolean sendPushNotificationLegacy(Notification notification) {
        // Fetch user preferences
        NotificationPreferences preferences = preferencesRepository.findById(notification.getUserId())
                .orElse(null);

        // Validate preferences and push notification settings
        if (preferences == null) {
            log.warn("No preferences found for user: {}", notification.getUserId());
            return false;
        }

        // Check if push notifications are enabled for this user
        if (!preferences.isPushNotificationsEnabled()) {
            log.info("Push notifications disabled for user: {}", notification.getUserId());
            return false;
        }

        // Validate device token
        if (preferences.getDeviceToken() == null || preferences.getDeviceToken().isEmpty()) {
            log.warn("No device token found for user: {}", notification.getUserId());
            return false;
        }

        // Validate Firebase messaging
        if (firebaseMessaging == null) {
            log.error("Firebase Messaging is not initialized. Push notification cannot be sent.");
            return false;
        }

        try {
            // Prepare notification data
            Map<String, String> data = prepareNotificationData(notification);

            // Create Firebase message
            Message message = buildFirebaseMessage(preferences.getDeviceToken(), notification, data);

            // Send message
            String messageId = firebaseMessaging.send(message);

            log.info("Push notification sent successfully. Message ID: {}, Notification ID: {}",
                    messageId, notification.getId());
            return true;
        } catch (FirebaseMessagingException e) {
            log.error("Firebase Messaging error when sending push notification for notification ID: {}. Error: {}",
                    notification.getId(), e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("Unexpected error when sending push notification for notification ID: {}. Error: {}",
                    notification.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Prepares notification data for Firebase message
     */
    private Map<String, String> prepareNotificationData(Notification notification) {
        Map<String, String> data = new HashMap<>();
        data.put("notificationId",
                notification.getId() != null ? notification.getId().toString() : "");
        data.put("title", notification.getTitle());
        data.put("body", notification.getMessage());
        data.put("type", notification.getType().toString());
        data.put("category", notification.getCategory());

        if (notification.getReferenceId() != null) {
            data.put("referenceId", notification.getReferenceId());
        }

        if (notification.getActionUrl() != null) {
            data.put("actionUrl", notification.getActionUrl());
        }

        return data;
    }

    /**
     * Builds Firebase message with given device token, notification, and data
     */
    private Message buildFirebaseMessage(String deviceToken, Notification notification, Map<String, String> data) {
        return Message.builder()
                .setToken(deviceToken)
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(notification.getTitle())
                        .setBody(notification.getMessage())
                        .build())
                .putAllData(data)
                .build();
    }
}