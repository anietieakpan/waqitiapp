package com.waqiti.card.dto;

import com.waqiti.card.enums.CardBrand;
import com.waqiti.card.enums.CardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * CardProductResponse DTO - Card product details response
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardProductResponse {
    private String productId;
    private String productName;
    private String productDescription;
    private CardType productType;
    private CardBrand cardNetwork;
    private String issuerName;
    private String currencyCode;
    private BigDecimal defaultCreditLimit;
    private BigDecimal minimumCreditLimit;
    private BigDecimal maximumCreditLimit;
    private BigDecimal annualFee;
    private BigDecimal monthlyFee;
    private BigDecimal issuanceFee;
    private BigDecimal replacementFee;
    private BigDecimal defaultInterestRate;
    private Boolean rewardsEnabled;
    private BigDecimal cashbackRate;
    private Boolean contactlessEnabled;
    private Boolean virtualCardEnabled;
    private Boolean internationalEnabled;
    private Boolean onlineTransactionsEnabled;
    private Integer cardValidityYears;
    private Boolean isActive;
}
