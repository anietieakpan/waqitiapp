package com.waqiti.payment.businessprofile;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Set;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSettings {
    
    @Column(name = "accepted_payment_methods", columnDefinition = "TEXT")
    private Set<String> acceptedPaymentMethods;
    
    @Column(name = "accepted_currencies", columnDefinition = "TEXT")
    private Set<String> acceptedCurrencies;
    
    @Column(name = "min_transaction_amount", precision = 19, scale = 4)
    private BigDecimal minTransactionAmount;
    
    @Column(name = "max_transaction_amount", precision = 19, scale = 4)
    private BigDecimal maxTransactionAmount;
    
    @Column(name = "daily_limit", precision = 19, scale = 4)
    private BigDecimal dailyLimit;
    
    @Column(name = "transaction_fee_percentage", precision = 5, scale = 4)
    private BigDecimal transactionFeePercentage;
    
    @Column(name = "fixed_transaction_fee", precision = 19, scale = 4)
    private BigDecimal fixedTransactionFee;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_frequency")
    private SettlementFrequency settlementFrequency;
    
    @Column(name = "settlement_account", length = 255)
    private String settlementAccount;
    
    @Column(name = "instant_payout_enabled")
    private boolean instantPayoutEnabled;
    
    @Column(name = "refunds_enabled")
    private boolean refundsEnabled;
    
    @Column(name = "partial_refunds_enabled")
    private boolean partialRefundsEnabled;
}

