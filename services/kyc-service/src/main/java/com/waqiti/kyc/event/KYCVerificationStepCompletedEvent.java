package com.waqiti.kyc.event;

import com.waqiti.common.event.DomainEvent;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Event published when a verification step is completed
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class KYCVerificationStepCompletedEvent extends DomainEvent {
    private String verificationId;
    private String stepName;
    private LocalDateTime completedAt;
    
    @Override
    public String getEventType() {
        return "kyc.verification.step_completed";
    }
}