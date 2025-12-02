package com.waqiti.kyc.enums;

/**
 * Enum representing categories of crimes relevant to financial services
 * 
 * @author Waqiti Compliance Team
 * @version 2.0.0
 * @since 2025-01-27
 */
public enum CrimeCategory {
    
    /**
     * Fraud-related crimes
     */
    FRAUD("Fraud, including wire fraud, mail fraud, securities fraud"),
    
    /**
     * Money laundering
     */
    MONEY_LAUNDERING("Money laundering and structuring"),
    
    /**
     * Embezzlement
     */
    EMBEZZLEMENT("Embezzlement and misappropriation of funds"),
    
    /**
     * Identity theft and related crimes
     */
    IDENTITY_THEFT("Identity theft, identity fraud"),
    
    /**
     * Forgery and counterfeiting
     */
    FORGERY("Forgery, counterfeiting, check fraud"),
    
    /**
     * Wire fraud
     */
    WIRE_FRAUD("Wire fraud, electronic fraud"),
    
    /**
     * Tax evasion
     */
    TAX_EVASION("Tax evasion, tax fraud"),
    
    /**
     * Racketeering and organized crime
     */
    RACKETEERING("RICO violations, organized crime"),
    
    /**
     * Bribery and corruption
     */
    BRIBERY("Bribery, corruption, FCPA violations"),
    
    /**
     * Securities violations
     */
    SECURITIES_VIOLATIONS("Securities fraud, insider trading"),
    
    /**
     * Bank fraud
     */
    BANK_FRAUD("Bank fraud, check kiting"),
    
    /**
     * Credit card fraud
     */
    CREDIT_CARD_FRAUD("Credit card fraud, payment card fraud"),
    
    /**
     * Terrorism financing
     */
    TERRORISM_FINANCING("Terrorism financing, material support"),
    
    /**
     * Cybercrime
     */
    CYBERCRIME("Hacking, computer fraud, data breach"),
    
    /**
     * Theft and robbery
     */
    THEFT("Theft, larceny, robbery");
    
    private final String description;
    
    CrimeCategory(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}