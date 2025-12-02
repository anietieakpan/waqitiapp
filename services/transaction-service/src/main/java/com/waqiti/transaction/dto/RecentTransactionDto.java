package com.waqiti.transaction.dto;

import com.waqiti.transaction.domain.TransactionStatus;
import com.waqiti.transaction.domain.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for recent transactions with user details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentTransactionDto {
    private UUID id;
    private TransactionType type;
    private BigDecimal amount;
    private String currency;
    private TransactionStatus status;
    private UUID senderId;
    private UUID receiverId;
    private String description;
    private LocalDateTime createdAt;
    private String otherPartyName;
    private boolean isOutgoing;
    
    // Constructor for JPQL projection
    public RecentTransactionDto(UUID id, TransactionType type, BigDecimal amount,
                              String currency, TransactionStatus status,
                              UUID senderId, UUID receiverId, String description,
                              LocalDateTime createdAt, String otherPartyName) {
        this.id = id;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.description = description;
        this.createdAt = createdAt;
        this.otherPartyName = otherPartyName;
        // This will be set by the service based on the current user
        this.isOutgoing = false;
    }
    
    public void determineDirection(UUID currentUserId) {
        this.isOutgoing = currentUserId.equals(senderId);
    }
}