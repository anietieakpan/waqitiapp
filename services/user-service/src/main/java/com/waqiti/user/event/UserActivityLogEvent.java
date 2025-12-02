package com.waqiti.user.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event for user activity logging
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserActivityLogEvent extends UserEvent {
    
    private String activityType; // LOGIN, LOGOUT, PASSWORD_CHANGE, PROFILE_UPDATE, TRANSACTION, SETTINGS_CHANGE
    private String activityCategory; // AUTHENTICATION, ACCOUNT, SECURITY, TRANSACTION, NAVIGATION
    private String activityDescription;
    private String ipAddress;
    private String deviceId;
    private String deviceType;
    private String userAgent;
    private String location;
    private String country;
    private String city;
    private LocalDateTime activityTime;
    private boolean successful;
    private String failureReason;
    private String resourceAccessed;
    private String httpMethod;
    private String apiEndpoint;
    private int responseCode;
    private long responseTimeMs;
    private Map<String, Object> activityMetadata;
    private String riskScore;
    private boolean suspicious;
    private String suspiciousReason;
    
    public UserActivityLogEvent() {
        super("USER_ACTIVITY_LOG");
    }
    
    public static UserActivityLogEvent loginActivity(String userId, String ipAddress, String deviceId, 
                                                   boolean successful, String location) {
        UserActivityLogEvent event = new UserActivityLogEvent();
        event.setUserId(userId);
        event.setActivityType("LOGIN");
        event.setActivityCategory("AUTHENTICATION");
        event.setActivityDescription(successful ? "Successful login" : "Failed login attempt");
        event.setIpAddress(ipAddress);
        event.setDeviceId(deviceId);
        event.setLocation(location);
        event.setSuccessful(successful);
        event.setActivityTime(LocalDateTime.now());
        return event;
    }
    
    public static UserActivityLogEvent transactionActivity(String userId, String activityType, String description, 
                                                         String ipAddress, boolean successful) {
        UserActivityLogEvent event = new UserActivityLogEvent();
        event.setUserId(userId);
        event.setActivityType(activityType);
        event.setActivityCategory("TRANSACTION");
        event.setActivityDescription(description);
        event.setIpAddress(ipAddress);
        event.setSuccessful(successful);
        event.setActivityTime(LocalDateTime.now());
        return event;
    }
    
    public static UserActivityLogEvent securityActivity(String userId, String activityType, String description, 
                                                      String ipAddress, String deviceId) {
        UserActivityLogEvent event = new UserActivityLogEvent();
        event.setUserId(userId);
        event.setActivityType(activityType);
        event.setActivityCategory("SECURITY");
        event.setActivityDescription(description);
        event.setIpAddress(ipAddress);
        event.setDeviceId(deviceId);
        event.setActivityTime(LocalDateTime.now());
        event.setSuccessful(true);
        return event;
    }
    
    public static UserActivityLogEvent suspiciousActivity(String userId, String activityType, String description, 
                                                        String ipAddress, String reason, String riskScore) {
        UserActivityLogEvent event = new UserActivityLogEvent();
        event.setUserId(userId);
        event.setActivityType(activityType);
        event.setActivityCategory("SECURITY");
        event.setActivityDescription(description);
        event.setIpAddress(ipAddress);
        event.setSuspicious(true);
        event.setSuspiciousReason(reason);
        event.setRiskScore(riskScore);
        event.setActivityTime(LocalDateTime.now());
        event.setSuccessful(false);
        return event;
    }
}