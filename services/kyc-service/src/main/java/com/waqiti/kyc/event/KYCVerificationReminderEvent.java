package com.waqiti.kyc.event;

import com.waqiti.common.event.DomainEvent;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Event published when a verification reminder is sent
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class KYCVerificationReminderEvent extends DomainEvent {
    private String verificationId;
    private String userId;
    private LocalDateTime reminderSentAt;
    
    @Override
    public String getEventType() {
        return "kyc.verification.reminder_sent";
    }
}