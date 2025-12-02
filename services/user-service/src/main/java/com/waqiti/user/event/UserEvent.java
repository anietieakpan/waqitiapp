package com.waqiti.user.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Base class for all user-related events
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "eventType"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = UserAccountActivatedEvent.class, name = "USER_ACCOUNT_ACTIVATED"),
    @JsonSubTypes.Type(value = UserAccountDeactivatedEvent.class, name = "USER_ACCOUNT_DEACTIVATED"),
    @JsonSubTypes.Type(value = UserStatusUpdatedEvent.class, name = "USER_STATUS_UPDATED"),
    @JsonSubTypes.Type(value = UserKycRejectedEvent.class, name = "USER_KYC_REJECTED"),
    @JsonSubTypes.Type(value = UserWelcomePackageEvent.class, name = "USER_WELCOME_PACKAGE"),
    @JsonSubTypes.Type(value = UserActivityLogEvent.class, name = "USER_ACTIVITY_LOG"),
    @JsonSubTypes.Type(value = UserNotificationEvent.class, name = "USER_NOTIFICATION"),
    @JsonSubTypes.Type(value = UserPreferenceChangeEvent.class, name = "USER_PREFERENCE_CHANGE"),
    @JsonSubTypes.Type(value = TokenRevocationEvent.class, name = "TOKEN_REVOCATION"),
    @JsonSubTypes.Type(value = AccountStatusChangeEvent.class, name = "ACCOUNT_STATUS_CHANGE"),
    @JsonSubTypes.Type(value = AccountMonitoringEvent.class, name = "ACCOUNT_MONITORING"),
    @JsonSubTypes.Type(value = PhoneNumberAlertEvent.class, name = "PHONE_NUMBER_ALERT")
})
@Data
public abstract class UserEvent {
    private String eventId;
    private String eventType;
    private String userId;
    private LocalDateTime timestamp;
    private String source;
    private String version;
    private String correlationId;
    private String sessionId;
    
    protected UserEvent(String eventType) {
        this.eventType = eventType;
        this.timestamp = LocalDateTime.now();
        this.version = "1.0";
    }
}