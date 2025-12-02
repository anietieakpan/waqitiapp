package com.waqiti.user.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Event for user account deactivation
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserAccountDeactivatedEvent extends UserEvent {
    
    private String email;
    private String username;
    private String deactivationReason; // USER_REQUEST, SECURITY_VIOLATION, COMPLIANCE_ISSUE, FRAUD, INACTIVITY
    private String deactivationType; // TEMPORARY, PERMANENT
    private String deactivatedBy; // USER_SELF, ADMIN, SYSTEM, COMPLIANCE_TEAM
    private LocalDateTime deactivatedAt;
    private LocalDateTime scheduledReactivation;
    private boolean preserveData;
    private boolean refundPending;
    private String finalBalance;
    private String currency;
    private String ipAddress;
    private String deviceId;
    private String notes;
    private String ticketId;
    
    public UserAccountDeactivatedEvent() {
        super("USER_ACCOUNT_DEACTIVATED");
    }
    
    public static UserAccountDeactivatedEvent userRequested(String userId, String email, String username, 
                                                          String reason, String ipAddress) {
        UserAccountDeactivatedEvent event = new UserAccountDeactivatedEvent();
        event.setUserId(userId);
        event.setEmail(email);
        event.setUsername(username);
        event.setDeactivationReason(reason);
        event.setDeactivationType("TEMPORARY");
        event.setDeactivatedBy("USER_SELF");
        event.setIpAddress(ipAddress);
        event.setDeactivatedAt(LocalDateTime.now());
        event.setPreserveData(true);
        return event;
    }
    
    public static UserAccountDeactivatedEvent securityViolation(String userId, String email, String username, 
                                                              String reason, String ticketId) {
        UserAccountDeactivatedEvent event = new UserAccountDeactivatedEvent();
        event.setUserId(userId);
        event.setEmail(email);
        event.setUsername(username);
        event.setDeactivationReason("SECURITY_VIOLATION");
        event.setDeactivationType("TEMPORARY");
        event.setDeactivatedBy("SYSTEM");
        event.setNotes(reason);
        event.setTicketId(ticketId);
        event.setDeactivatedAt(LocalDateTime.now());
        event.setPreserveData(true);
        return event;
    }
    
    public static UserAccountDeactivatedEvent complianceIssue(String userId, String email, String username, 
                                                            String reason, boolean permanent) {
        UserAccountDeactivatedEvent event = new UserAccountDeactivatedEvent();
        event.setUserId(userId);
        event.setEmail(email);
        event.setUsername(username);
        event.setDeactivationReason("COMPLIANCE_ISSUE");
        event.setDeactivationType(permanent ? "PERMANENT" : "TEMPORARY");
        event.setDeactivatedBy("COMPLIANCE_TEAM");
        event.setNotes(reason);
        event.setDeactivatedAt(LocalDateTime.now());
        event.setPreserveData(!permanent);
        return event;
    }
    
    public static UserAccountDeactivatedEvent inactivity(String userId, String email, String username) {
        UserAccountDeactivatedEvent event = new UserAccountDeactivatedEvent();
        event.setUserId(userId);
        event.setEmail(email);
        event.setUsername(username);
        event.setDeactivationReason("INACTIVITY");
        event.setDeactivationType("TEMPORARY");
        event.setDeactivatedBy("SYSTEM");
        event.setDeactivatedAt(LocalDateTime.now());
        event.setScheduledReactivation(LocalDateTime.now().plusDays(30));
        event.setPreserveData(true);
        return event;
    }
}