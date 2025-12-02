package com.waqiti.virtualcard.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Event published when a card is terminated
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardTerminatedEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String cardId;
    private String userId;
    private String reason;
    private String terminatedBy;
    private TerminationType terminationType;
    private LocalDateTime timestamp;
    private LocalDateTime cardCreatedAt;
    private LocalDateTime cardLastUsedAt;
    private Long totalTransactions;
    private BigDecimal totalSpent;
    private BigDecimal outstandingBalance;
    private boolean replacementCardRequested;
    private String replacementCardId;
    private String sourceIp;
    private String deviceId;
    private String sessionId;
    private String eventId;
    private String eventType = "CARD_TERMINATED";
    private String source = "virtual-card-service";
    private String correlationId;
    private Map<String, String> metadata;
    private Integer version = 1;
    
    public enum TerminationType {
        USER_REQUESTED,
        ADMIN_ACTION,
        SECURITY_ISSUE,
        CARD_EXPIRED,
        ACCOUNT_CLOSED,
        FRAUD_DETECTED,
        LOST_OR_STOLEN,
        DAMAGED,
        REPLACED,
        COMPLIANCE_REQUIREMENT,
        SYSTEM_ACTION
    }
    
    public CardTerminatedEvent(String cardId, String userId, String reason, LocalDateTime timestamp) {
        this.cardId = cardId;
        this.userId = userId;
        this.reason = reason;
        this.timestamp = timestamp;
        this.eventId = UUID.randomUUID().toString();
        this.correlationId = UUID.randomUUID().toString();
        this.terminationType = TerminationType.USER_REQUESTED;
    }
    
    /**
     * Create termination event with full context
     */
    public static CardTerminatedEvent create(String cardId, String userId,
                                            String reason, TerminationType type,
                                            String terminatedBy, CardStatistics stats) {
        return CardTerminatedEvent.builder()
            .cardId(cardId)
            .userId(userId)
            .reason(reason)
            .terminationType(type)
            .terminatedBy(terminatedBy)
            .timestamp(LocalDateTime.now())
            .cardCreatedAt(stats.getCreatedAt())
            .cardLastUsedAt(stats.getLastUsedAt())
            .totalTransactions(stats.getTotalTransactions())
            .totalSpent(stats.getTotalSpent())
            .outstandingBalance(stats.getOutstandingBalance())
            .eventId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .build();
    }
    
    /**
     * Check if this is a security-related termination
     */
    public boolean isSecurityTermination() {
        return terminationType == TerminationType.SECURITY_ISSUE ||
               terminationType == TerminationType.FRAUD_DETECTED ||
               terminationType == TerminationType.LOST_OR_STOLEN;
    }
    
    /**
     * Check if this is a normal termination
     */
    public boolean isNormalTermination() {
        return terminationType == TerminationType.USER_REQUESTED ||
               terminationType == TerminationType.CARD_EXPIRED ||
               terminationType == TerminationType.REPLACED;
    }
    
    /**
     * Check if card had activity
     */
    public boolean hadActivity() {
        return totalTransactions != null && totalTransactions > 0;
    }
    
    /**
     * Check if there's outstanding balance
     */
    public boolean hasOutstandingBalance() {
        return outstandingBalance != null && outstandingBalance.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Get termination severity
     */
    public TerminationSeverity getSeverity() {
        if (isSecurityTermination()) {
            return TerminationSeverity.HIGH;
        } else if (terminationType == TerminationType.COMPLIANCE_REQUIREMENT ||
                   terminationType == TerminationType.ADMIN_ACTION) {
            return TerminationSeverity.MEDIUM;
        } else {
            return TerminationSeverity.LOW;
        }
    }
    
    public enum TerminationSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    /**
     * Helper class for card statistics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardStatistics {
        private LocalDateTime createdAt;
        private LocalDateTime lastUsedAt;
        private Long totalTransactions;
        private BigDecimal totalSpent;
        private BigDecimal outstandingBalance;
    }
}