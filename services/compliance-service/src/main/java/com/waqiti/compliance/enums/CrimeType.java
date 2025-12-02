package com.waqiti.compliance.enums;

/**
 * Financial Crime Type Enumeration
 *
 * Defines all types of financial crimes tracked by the platform
 * for regulatory reporting and law enforcement notification.
 *
 * Compliance: BSA/AML, FinCEN SAR Requirements, FBI IC3
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
public enum CrimeType {
    /**
     * General fraud - catch-all category
     */
    FRAUD,

    /**
     * Money laundering - BSA/AML violation
     * Requires immediate FinCEN SAR filing
     */
    MONEY_LAUNDERING,

    /**
     * Terrorism financing - OFAC/FinCEN violation
     * Requires immediate law enforcement notification
     */
    TERRORISM_FINANCING,

    /**
     * Securities fraud - SEC violation
     */
    SECURITIES_FRAUD,

    /**
     * Insider trading - SEC violation
     */
    INSIDER_TRADING,

    /**
     * Identity theft
     */
    IDENTITY_THEFT,

    /**
     * Embezzlement
     */
    EMBEZZLEMENT,

    /**
     * Wire fraud - federal crime
     */
    WIRE_FRAUD,

    /**
     * Tax evasion - IRS violation
     */
    TAX_EVASION,

    /**
     * Sanctions violation - OFAC
     */
    SANCTIONS_VIOLATION,

    /**
     * Bribery
     */
    BRIBERY,

    /**
     * Corruption
     */
    CORRUPTION,

    /**
     * Market manipulation - SEC violation
     */
    MARKET_MANIPULATION,

    /**
     * Ponzi scheme
     */
    PONZI_SCHEME,

    /**
     * Cryptocurrency fraud
     */
    CRYPTOCURRENCY_FRAUD,

    /**
     * Ransomware
     */
    RANSOMWARE,

    /**
     * Phishing
     */
    PHISHING,

    /**
     * Account takeover
     */
    ACCOUNT_TAKEOVER;

    /**
     * Check if crime type is high severity requiring immediate action
     *
     * @return true if high severity
     */
    public boolean isHighSeverity() {
        return this == TERRORISM_FINANCING ||
               this == MONEY_LAUNDERING ||
               this == SECURITIES_FRAUD ||
               this == SANCTIONS_VIOLATION;
    }

    /**
     * Check if crime is a federal offense requiring FBI notification
     *
     * @return true if federal crime
     */
    public boolean isFederalCrime() {
        return this == TERRORISM_FINANCING ||
               this == MONEY_LAUNDERING ||
               this == SECURITIES_FRAUD ||
               this == INSIDER_TRADING ||
               this == WIRE_FRAUD ||
               this == SANCTIONS_VIOLATION;
    }

    /**
     * Check if crime requires SEC notification
     *
     * @return true if securities-related
     */
    public boolean isSecuritiesCrime() {
        return this == SECURITIES_FRAUD ||
               this == INSIDER_TRADING ||
               this == MARKET_MANIPULATION;
    }

    /**
     * Check if crime requires immediate FinCEN SAR filing
     *
     * @return true if SAR required
     */
    public boolean requiresSAR() {
        return this == MONEY_LAUNDERING ||
               this == TERRORISM_FINANCING ||
               this == WIRE_FRAUD ||
               this == SANCTIONS_VIOLATION;
    }
}
