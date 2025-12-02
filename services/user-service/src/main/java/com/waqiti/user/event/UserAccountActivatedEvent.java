package com.waqiti.user.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event for user account activation
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserAccountActivatedEvent extends UserEvent {
    
    private String email;
    private String username;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String activationMethod; // EMAIL_VERIFICATION, PHONE_VERIFICATION, ADMIN_ACTIVATION, AUTO_ACTIVATION
    private String activationCode;
    private String ipAddress;
    private String deviceId;
    private String userAgent;
    private LocalDateTime activatedAt;
    private boolean kycCompleted;
    private String kycStatus;
    private String accountType; // PERSONAL, BUSINESS, PREMIUM
    private String referralCode;
    private String referredBy;
    private Map<String, String> metadata;
    
    public UserAccountActivatedEvent() {
        super("USER_ACCOUNT_ACTIVATED");
    }
    
    public static UserAccountActivatedEvent emailActivation(String userId, String email, String username, 
                                                          String activationCode, String ipAddress) {
        UserAccountActivatedEvent event = new UserAccountActivatedEvent();
        event.setUserId(userId);
        event.setEmail(email);
        event.setUsername(username);
        event.setActivationMethod("EMAIL_VERIFICATION");
        event.setActivationCode(activationCode);
        event.setIpAddress(ipAddress);
        event.setActivatedAt(LocalDateTime.now());
        return event;
    }
    
    public static UserAccountActivatedEvent phoneActivation(String userId, String phoneNumber, String username, 
                                                          String activationCode, String deviceId) {
        UserAccountActivatedEvent event = new UserAccountActivatedEvent();
        event.setUserId(userId);
        event.setPhoneNumber(phoneNumber);
        event.setUsername(username);
        event.setActivationMethod("PHONE_VERIFICATION");
        event.setActivationCode(activationCode);
        event.setDeviceId(deviceId);
        event.setActivatedAt(LocalDateTime.now());
        return event;
    }
    
    public static UserAccountActivatedEvent adminActivation(String userId, String email, String username, 
                                                          String firstName, String lastName) {
        UserAccountActivatedEvent event = new UserAccountActivatedEvent();
        event.setUserId(userId);
        event.setEmail(email);
        event.setUsername(username);
        event.setFirstName(firstName);
        event.setLastName(lastName);
        event.setActivationMethod("ADMIN_ACTIVATION");
        event.setActivatedAt(LocalDateTime.now());
        return event;
    }
}