package com.waqiti.common.client;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction Result Data Transfer Object
 */
@Data
@Builder
public class TransactionResultDTO {
    private UUID transactionId;
    private UUID walletId;
    private String transactionType;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String reference;
    private BigDecimal newBalance;
    private LocalDateTime timestamp;
}