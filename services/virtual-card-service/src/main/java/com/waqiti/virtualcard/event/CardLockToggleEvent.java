package com.waqiti.virtualcard.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Event published when a card is locked or unlocked
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardLockToggleEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String cardId;
    private String userId;
    private boolean locked;
    private String reason;
    private String performedBy;
    private String sourceIp;
    private String deviceId;
    private String sessionId;
    private LocalDateTime timestamp;
    private LocalDateTime previousLockTimestamp;
    private String previousLockReason;
    private Integer lockCount;
    private String eventId;
    private String eventType = "CARD_LOCK_TOGGLE";
    private String source = "virtual-card-service";
    private String correlationId;
    private Map<String, String> metadata;
    private Integer version = 1;
    
    public CardLockToggleEvent(String cardId, String userId, boolean locked, 
                              String reason, LocalDateTime timestamp) {
        this.cardId = cardId;
        this.userId = userId;
        this.locked = locked;
        this.reason = reason;
        this.timestamp = timestamp;
        this.eventId = UUID.randomUUID().toString();
        this.correlationId = UUID.randomUUID().toString();
        this.eventType = locked ? "CARD_LOCKED" : "CARD_UNLOCKED";
    }
    
    /**
     * Create lock event with full context
     */
    public static CardLockToggleEvent createLockEvent(String cardId, String userId,
                                                     String reason, String performedBy,
                                                     String sourceIp, String deviceId) {
        return CardLockToggleEvent.builder()
            .cardId(cardId)
            .userId(userId)
            .locked(true)
            .reason(reason)
            .performedBy(performedBy)
            .sourceIp(sourceIp)
            .deviceId(deviceId)
            .timestamp(LocalDateTime.now())
            .eventId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .eventType("CARD_LOCKED")
            .build();
    }
    
    /**
     * Create unlock event with full context
     */
    public static CardLockToggleEvent createUnlockEvent(String cardId, String userId,
                                                       String reason, String performedBy,
                                                       String sourceIp, String deviceId) {
        return CardLockToggleEvent.builder()
            .cardId(cardId)
            .userId(userId)
            .locked(false)
            .reason(reason)
            .performedBy(performedBy)
            .sourceIp(sourceIp)
            .deviceId(deviceId)
            .timestamp(LocalDateTime.now())
            .eventId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .eventType("CARD_UNLOCKED")
            .build();
    }
    
    /**
     * Check if this is a security-related lock
     */
    public boolean isSecurityLock() {
        return reason != null && (
            reason.toLowerCase().contains("fraud") ||
            reason.toLowerCase().contains("security") ||
            reason.toLowerCase().contains("suspicious") ||
            reason.toLowerCase().contains("stolen") ||
            reason.toLowerCase().contains("lost")
        );
    }
    
    /**
     * Check if this is a user-initiated action
     */
    public boolean isUserInitiated() {
        return performedBy != null && performedBy.equals(userId);
    }
    
    /**
     * Check if this is an admin action
     */
    public boolean isAdminAction() {
        return performedBy != null && !performedBy.equals(userId);
    }
    
    /**
     * Get action description
     */
    public String getActionDescription() {
        return locked ? "Card locked" : "Card unlocked";
    }
}