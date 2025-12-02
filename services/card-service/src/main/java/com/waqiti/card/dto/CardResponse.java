package com.waqiti.card.dto;

import com.waqiti.card.enums.CardBrand;
import com.waqiti.card.enums.CardStatus;
import com.waqiti.card.enums.CardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CardResponse DTO - Card details response
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardResponse {
    private UUID id;
    private String cardId;
    private String maskedCardNumber;
    private String cardNumberLastFour;
    private CardType cardType;
    private CardBrand cardBrand;
    private CardStatus cardStatus;
    private String productId;
    private UUID userId;
    private UUID accountId;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String embossedName;
    private BigDecimal creditLimit;
    private BigDecimal availableCredit;
    private BigDecimal outstandingBalance;
    private BigDecimal ledgerBalance;
    private LocalDate paymentDueDate;
    private BigDecimal minimumPayment;
    private Boolean isContactless;
    private Boolean isVirtual;
    private Boolean isInternationalEnabled;
    private Boolean isOnlineEnabled;
    private LocalDateTime activatedAt;
    private Boolean isExpired;
    private Boolean isUsable;
    private Boolean isBlocked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
