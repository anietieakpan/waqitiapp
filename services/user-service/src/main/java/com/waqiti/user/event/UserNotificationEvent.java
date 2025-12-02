package com.waqiti.user.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Event for user notifications
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserNotificationEvent extends UserEvent {
    
    private String notificationType; // ALERT, INFO, WARNING, ERROR, SUCCESS, PROMOTIONAL
    private String notificationCategory; // SECURITY, ACCOUNT, TRANSACTION, MARKETING, SYSTEM
    private String title;
    private String message;
    private String priority; // LOW, MEDIUM, HIGH, URGENT
    private List<String> channels; // EMAIL, SMS, PUSH, IN_APP, WEBHOOK
    private String templateId;
    private Map<String, Object> templateData;
    private String recipientEmail;
    private String recipientPhone;
    private List<String> deviceTokens;
    private LocalDateTime scheduledTime;
    private LocalDateTime expiryTime;
    private boolean requiresAction;
    private String actionUrl;
    private String actionText;
    private Map<String, String> metadata;
    private String locale;
    private boolean personalized;
    
    public UserNotificationEvent() {
        super("USER_NOTIFICATION");
    }
    
    public static UserNotificationEvent securityAlert(String userId, String title, String message, 
                                                    String recipientEmail, List<String> channels) {
        UserNotificationEvent event = new UserNotificationEvent();
        event.setUserId(userId);
        event.setNotificationType("ALERT");
        event.setNotificationCategory("SECURITY");
        event.setTitle(title);
        event.setMessage(message);
        event.setPriority("HIGH");
        event.setRecipientEmail(recipientEmail);
        event.setChannels(channels);
        event.setRequiresAction(true);
        return event;
    }
    
    public static UserNotificationEvent transactionNotification(String userId, String title, String message, 
                                                              String recipientEmail, String recipientPhone) {
        UserNotificationEvent event = new UserNotificationEvent();
        event.setUserId(userId);
        event.setNotificationType("INFO");
        event.setNotificationCategory("TRANSACTION");
        event.setTitle(title);
        event.setMessage(message);
        event.setPriority("MEDIUM");
        event.setRecipientEmail(recipientEmail);
        event.setRecipientPhone(recipientPhone);
        event.setChannels(List.of("EMAIL", "SMS", "PUSH"));
        return event;
    }
    
    public static UserNotificationEvent systemNotification(String userId, String title, String message, 
                                                         String priority, LocalDateTime expiryTime) {
        UserNotificationEvent event = new UserNotificationEvent();
        event.setUserId(userId);
        event.setNotificationType("INFO");
        event.setNotificationCategory("SYSTEM");
        event.setTitle(title);
        event.setMessage(message);
        event.setPriority(priority);
        event.setExpiryTime(expiryTime);
        event.setChannels(List.of("IN_APP"));
        return event;
    }
}