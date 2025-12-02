package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatusResponse {

    private UUID accountId;
    
    private String accountNumber;
    
    private AccountStatusCode statusCode;
    
    private String statusDescription;
    
    private boolean isActive;
    
    private boolean isFrozen;
    
    private boolean isDormant;
    
    private boolean isClosed;
    
    private List<StatusFlag> statusFlags;
    
    private LocalDateTime lastStatusChange;
    
    private String statusChangedBy;
    
    private String statusChangeReason;

    public enum AccountStatusCode {
        ACTIVE,
        INACTIVE,
        FROZEN,
        SUSPENDED,
        DORMANT,
        CLOSED,
        BLOCKED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusFlag {
        private String flagType;
        private String flagDescription;
        private boolean isActive;
        private LocalDateTime setDate;
        private LocalDateTime expiryDate;
    }

    public boolean isOperational() {
        return isActive && !isFrozen && !isClosed;
    }

    public boolean hasRestrictions() {
        return isFrozen || isDormant || 
               (statusFlags != null && statusFlags.stream().anyMatch(StatusFlag::isActive));
    }

    public boolean requiresAttention() {
        return isFrozen || isDormant || hasActiveFlags();
    }

    private boolean hasActiveFlags() {
        return statusFlags != null && 
               statusFlags.stream().anyMatch(StatusFlag::isActive);
    }
}