package com.waqiti.user.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event for user status updates
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserStatusUpdatedEvent extends UserEvent {
    
    private String previousStatus;
    private String newStatus; // ACTIVE, INACTIVE, SUSPENDED, PENDING_VERIFICATION, LOCKED, DELETED
    private String updateReason;
    private String updatedBy;
    private String updateType; // MANUAL, AUTOMATIC, SCHEDULED, TRIGGERED
    private LocalDateTime effectiveDate;
    private LocalDateTime expiryDate;
    private String kycStatus;
    private String riskLevel;
    private boolean emailVerified;
    private boolean phoneVerified;
    private boolean twoFactorEnabled;
    private String accountTier; // BASIC, STANDARD, PREMIUM, VIP
    private Map<String, String> statusFlags;
    private String complianceStatus;
    private String restrictionLevel;
    
    public UserStatusUpdatedEvent() {
        super("USER_STATUS_UPDATED");
    }
    
    public static UserStatusUpdatedEvent statusChange(String userId, String previousStatus, String newStatus, 
                                                    String reason, String updatedBy) {
        UserStatusUpdatedEvent event = new UserStatusUpdatedEvent();
        event.setUserId(userId);
        event.setPreviousStatus(previousStatus);
        event.setNewStatus(newStatus);
        event.setUpdateReason(reason);
        event.setUpdatedBy(updatedBy);
        event.setUpdateType("MANUAL");
        event.setEffectiveDate(LocalDateTime.now());
        return event;
    }
    
    public static UserStatusUpdatedEvent accountLocked(String userId, String previousStatus, String reason) {
        UserStatusUpdatedEvent event = new UserStatusUpdatedEvent();
        event.setUserId(userId);
        event.setPreviousStatus(previousStatus);
        event.setNewStatus("LOCKED");
        event.setUpdateReason(reason);
        event.setUpdatedBy("SYSTEM");
        event.setUpdateType("TRIGGERED");
        event.setEffectiveDate(LocalDateTime.now());
        return event;
    }
    
    public static UserStatusUpdatedEvent accountSuspended(String userId, String previousStatus, String reason, 
                                                        LocalDateTime expiryDate) {
        UserStatusUpdatedEvent event = new UserStatusUpdatedEvent();
        event.setUserId(userId);
        event.setPreviousStatus(previousStatus);
        event.setNewStatus("SUSPENDED");
        event.setUpdateReason(reason);
        event.setUpdatedBy("SYSTEM");
        event.setUpdateType("AUTOMATIC");
        event.setEffectiveDate(LocalDateTime.now());
        event.setExpiryDate(expiryDate);
        return event;
    }
    
    public static UserStatusUpdatedEvent tierUpgrade(String userId, String currentStatus, String previousTier, 
                                                   String newTier) {
        UserStatusUpdatedEvent event = new UserStatusUpdatedEvent();
        event.setUserId(userId);
        event.setPreviousStatus(currentStatus);
        event.setNewStatus(currentStatus);
        event.setAccountTier(newTier);
        event.setUpdateReason("Account tier upgrade from " + previousTier + " to " + newTier);
        event.setUpdatedBy("SYSTEM");
        event.setUpdateType("AUTOMATIC");
        event.setEffectiveDate(LocalDateTime.now());
        return event;
    }
}