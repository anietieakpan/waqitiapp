package com.waqiti.card.dto;

import com.waqiti.card.enums.TransactionStatus;
import com.waqiti.card.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CardTransactionResponse DTO - Transaction details response
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardTransactionResponse {
    private UUID id;
    private String transactionId;
    private UUID cardId;
    private UUID userId;
    private TransactionType transactionType;
    private TransactionStatus transactionStatus;
    private LocalDateTime transactionDate;
    private BigDecimal amount;
    private String currencyCode;
    private BigDecimal billingAmount;
    private String billingCurrencyCode;
    private String merchantId;
    private String merchantName;
    private String merchantCategoryCode;
    private String merchantCountry;
    private String authorizationCode;
    private Boolean isInternational;
    private Boolean isOnline;
    private Boolean isContactless;
    private BigDecimal fraudScore;
    private String riskLevel;
    private Boolean isReversed;
    private Boolean isDisputed;
    private LocalDateTime createdAt;
}
