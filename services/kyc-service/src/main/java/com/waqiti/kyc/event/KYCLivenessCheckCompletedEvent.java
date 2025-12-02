package com.waqiti.kyc.event;

import com.waqiti.common.event.DomainEvent;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Event published when a liveness check is completed
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class KYCLivenessCheckCompletedEvent extends DomainEvent {
    private String checkId;
    private String applicantId;
    private String checkType;
    private LocalDateTime completedAt;
    
    @Override
    public String getEventType() {
        return "kyc.liveness_check.completed";
    }
}