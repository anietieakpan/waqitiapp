package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Provider capabilities model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderCapabilities {
    
    private Set<PaymentType> supportedPaymentTypes;
    private Set<String> supportedCurrencies;
    private Set<String> supportedCountries;
    
    private boolean supportsRefunds;
    private boolean supportsCancellation;
    private boolean supportsRecurring;
    private boolean supportsInstantTransfer;
    private boolean supportsInstantSettlement;
    private boolean supportsPartialRefunds;
    private boolean supportsMultiParty;
    private boolean supportsEscrow;
    private boolean supportsDisputes;
    private boolean supportsInternational;
    private boolean supportsInStore;
    private boolean supportsPayPal;
    private boolean supportsMultiCurrency;
    private boolean supports3DS;
    private boolean supportsBiometricAuth;
    private boolean supportsContactless;
    
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private BigDecimal minTransactionAmount;
    private BigDecimal maxTransactionAmount;
    private BigDecimal maxDailyVolume;
    private BigDecimal maxMonthlyVolume;
    
    private int maxTransactionsPerMinute;
    private int maxTransactionsPerDay;
    
    private String achProcessingTime;
    private String estimatedSettlement;
    
    private boolean requiresKyc;
    private boolean supportsTokenization;
    private boolean supportsWebhooks;

    // Additional capabilities
    private BigDecimal minimumAmount;
    private BigDecimal maximumAmount;
    private java.util.List<String> supportedNetworks;
    private String settlementTime;
    
    public boolean canHandle(PaymentType paymentType) {
        return supportedPaymentTypes != null && supportedPaymentTypes.contains(paymentType);
    }
    
    public boolean supportsCurrency(String currency) {
        return supportedCurrencies != null && supportedCurrencies.contains(currency);
    }
    
    public boolean supportsCountry(String country) {
        return supportedCountries != null && supportedCountries.contains(country);
    }
    
    public boolean isAmountWithinLimits(BigDecimal amount) {
        if (minTransactionAmount != null && amount.compareTo(minTransactionAmount) < 0) {
            return false;
        }
        if (maxTransactionAmount != null && amount.compareTo(maxTransactionAmount) > 0) {
            return false;
        }
        return true;
    }
}