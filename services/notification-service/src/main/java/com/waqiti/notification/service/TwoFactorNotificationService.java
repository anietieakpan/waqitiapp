// File: services/notification-service/src/main/java/com/waqiti/notification/service/TwoFactorNotificationService.java
package com.waqiti.notification.service;

import com.waqiti.notification.domain.Notification;
import com.waqiti.notification.domain.NotificationType;
import com.waqiti.notification.domain.DeliveryStatus;
import com.waqiti.notification.domain.NotificationTemplate;
import com.waqiti.notification.repository.NotificationRepository;
import com.waqiti.notification.service.provider.SmsProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwoFactorNotificationService {

    private final NotificationService notificationService;
    private final NotificationTemplateService templateService;
    private final NotificationRepository notificationRepository;
    private final SmsProvider smsProvider;

    private static final String TWO_FACTOR_TEMPLATE_CODE = "two_factor_code";
    private static final String TWO_FACTOR_CATEGORY = "SECURITY";

    /**
     * Sends a 2FA verification code via SMS
     *
     * @param userId The user ID
     * @param phoneNumber The phone number to send the code to
     * @param verificationCode The verification code
     * @return true if the SMS was sent successfully, false otherwise
     */
    @Transactional
    public boolean sendTwoFactorSms(UUID userId, String phoneNumber, String verificationCode) {
        log.info("Sending 2FA verification code to user: {}", userId);

        try {
            // Try to get the template
            NotificationTemplate template;
            try {
                template = templateService.getTemplateByCode(TWO_FACTOR_TEMPLATE_CODE);
            } catch (IllegalArgumentException e) {
                // Template doesn't exist, use a default message
                log.warn("2FA template not found, using default message");
                return sendDirectTwoFactorSms(userId, phoneNumber, verificationCode);
            }

            // Prepare parameters for template
            Map<String, Object> params = new HashMap<>();
            params.put("code", verificationCode);

            // Create notification object
            Notification notification = Notification.create(
                    userId,
                    "Verification Code",
                    "Your verification code is: " + verificationCode,
                    NotificationType.SMS,
                    TWO_FACTOR_CATEGORY
            );

            // Set expiry to 10 minutes from now
            notification.setExpiryDate(LocalDateTime.now().plusMinutes(10));

            // Save notification
            notification = notificationRepository.save(notification);

            // Render SMS text from template
            String smsText;
            if (template.getSmsTemplate() != null) {
                smsText = templateService.renderTemplate(template.getSmsTemplate(), params);
            } else {
                smsText = "Your Waqiti verification code is: " + verificationCode;
            }

            // Send SMS directly through provider
            String messageSid = smsProvider.sendSms(phoneNumber, smsText);

            // Update notification status
            if (messageSid != null) {
                notification.updateDeliveryStatus(DeliveryStatus.SENT, null);
                notificationRepository.save(notification);
                return true;
            } else {
                notification.updateDeliveryStatus(DeliveryStatus.FAILED, "Failed to send SMS");
                notificationRepository.save(notification);
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending 2FA SMS notification to user: {}. Error: {}",
                    userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sends a 2FA verification code directly via SMS without using templates
     */
    private boolean sendDirectTwoFactorSms(UUID userId, String phoneNumber, String verificationCode) {
        try {
            // Create a simple message
            String message = "Your Waqiti verification code is: " + verificationCode;

            // Create notification object
            Notification notification = Notification.create(
                    userId,
                    "Verification Code",
                    message,
                    NotificationType.SMS,
                    TWO_FACTOR_CATEGORY
            );

            // Set expiry to 10 minutes from now
            notification.setExpiryDate(LocalDateTime.now().plusMinutes(10));

            // Save notification
            notification = notificationRepository.save(notification);

            // Send SMS directly through provider
            String messageSid = smsProvider.sendSms(phoneNumber, message);

            // Update notification status
            if (messageSid != null) {
                notification.updateDeliveryStatus(DeliveryStatus.SENT, null);
                notificationRepository.save(notification);
                return true;
            } else {
                notification.updateDeliveryStatus(DeliveryStatus.FAILED, "Failed to send SMS");
                notificationRepository.save(notification);
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending direct 2FA SMS notification to user: {}. Error: {}",
                    userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sends a 2FA verification code via email
     *
     * @param userId The user ID
     * @param email The email address to send the code to
     * @param verificationCode The verification code
     * @return true if the email was sent successfully, false otherwise
     */
    @Transactional
    public boolean sendTwoFactorEmail(UUID userId, String email, String verificationCode) {
        log.info("Sending 2FA verification code via email to user: {}", userId);

        // Create parameters for the notification
        Map<String, Object> params = new HashMap<>();
        params.put("code", verificationCode);

        // Use the notification service to send an email based on the template
        try {
            // Send notification using the template
            notificationService.sendNotification(
                    buildTwoFactorEmailRequest(userId, email, verificationCode)
            );
            return true;
        } catch (Exception e) {
            log.error("Error sending 2FA email notification to user: {}. Error: {}",
                    userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Builds a SendNotificationRequest for 2FA emails
     */
    private com.waqiti.notification.dto.SendNotificationRequest buildTwoFactorEmailRequest(
            UUID userId, String email, String verificationCode) {

        Map<String, Object> params = new HashMap<>();
        params.put("code", verificationCode);
        params.put("email", email);

        return com.waqiti.notification.dto.SendNotificationRequest.builder()
                .userId(userId)
                .templateCode("two_factor_email")
                .parameters(params)
                .types(new String[] { "EMAIL" })
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
    }
}