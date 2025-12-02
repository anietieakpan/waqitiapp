package com.waqiti.transaction.dto;

import com.waqiti.transaction.domain.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lightweight DTO for pending transactions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingTransactionDto {
    private UUID id;
    private TransactionType type;
    private BigDecimal amount;
    private String currency;
    private String description;
    private LocalDateTime createdAt;
    private long ageInMinutes;
    
    // Constructor for JPQL projection
    public PendingTransactionDto(UUID id, TransactionType type, BigDecimal amount,
                               String currency, String description, LocalDateTime createdAt) {
        this.id = id;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.createdAt = createdAt;
        this.ageInMinutes = java.time.Duration.between(createdAt, LocalDateTime.now()).toMinutes();
    }
}