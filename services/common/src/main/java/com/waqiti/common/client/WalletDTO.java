package com.waqiti.common.client;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Wallet Data Transfer Object for inter-service communication
 */
@Data
@Builder
public class WalletDTO {
    private UUID id;
    private UUID userId;
    private String walletType;
    private String currency;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private BigDecimal reservedBalance;
    private String status;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String walletNumber;
    private String accountNumber;
    private String routingNumber;
}