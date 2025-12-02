package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for debiting a wallet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletDebitRequest {
    private UUID walletId;
    private BigDecimal amount;
    private String transactionType;
    private String referenceId;
    private String description;
    private Map<String, String> metadata;
}