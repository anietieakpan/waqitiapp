package com.waqiti.payment.cash.dto;

import lombok.Getter;

/**
 * Supported cash deposit networks
 */
@Getter
public enum CashDepositNetwork {
    
    MONEYGRAM("moneygram", "MoneyGram", "MoneyGram International", true, 0.02, 3.99, 5000.00),
    WESTERN_UNION("western_union", "Western Union", "Western Union Financial Services", true, 0.015, 4.99, 7500.00),
    PAYPAL_CASH("paypal_cash", "PayPal Cash", "PayPal Cash Network", true, 0.025, 2.99, 2000.00),
    CASHAPP_PAPER("cashapp_paper", "Cash App Paper Money", "Block Inc Paper Money Network", true, 0.03, 1.99, 1000.00),
    VENMO_CASH("venmo_cash", "Venmo Cash", "Venmo Cash Partner Network", false, 0.02, 2.49, 1500.00),
    GREEN_DOT("green_dot", "Green Dot", "Green Dot Retail Network", true, 0.025, 3.74, 3000.00),
    WALMART_MONEY_CENTER("walmart", "Walmart Money Center", "Walmart Money Services", true, 0.01, 3.00, 6000.00),
    CVS_PHARMACY("cvs", "CVS Pharmacy", "CVS Money Services", true, 0.02, 4.25, 2500.00);

    private final String code;
    private final String displayName;
    private final String fullName;
    private final boolean isActive;
    private final double feePercentage;
    private final double minimumFee;
    private final double dailyLimit;

    CashDepositNetwork(String code, String displayName, String fullName, 
                      boolean isActive, double feePercentage, double minimumFee, double dailyLimit) {
        this.code = code;
        this.displayName = displayName;
        this.fullName = fullName;
        this.isActive = isActive;
        this.feePercentage = feePercentage;
        this.minimumFee = minimumFee;
        this.dailyLimit = dailyLimit;
    }

    public static CashDepositNetwork fromCode(String code) {
        for (CashDepositNetwork network : values()) {
            if (network.code.equalsIgnoreCase(code)) {
                return network;
            }
        }
        throw new IllegalArgumentException("Unknown cash deposit network: " + code);
    }
}