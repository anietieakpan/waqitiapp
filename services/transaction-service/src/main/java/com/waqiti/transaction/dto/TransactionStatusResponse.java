package com.waqiti.transaction.dto;

import com.waqiti.transaction.domain.TransactionStatus;
import com.waqiti.transaction.domain.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionStatusResponse {
    private UUID transactionId;
    private TransactionStatus status;
    private TransactionType transactionType;
    private BigDecimal amount;
    private String currency;
    private String sourceAccountId;
    private String targetAccountId;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private String failureReason;
}