package com.waqiti.card.enums;

/**
 * Card brand/network enumeration
 * Represents the payment network for the card
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-09
 */
public enum CardBrand {
    /**
     * Visa payment network
     */
    VISA("Visa", "4"),

    /**
     * Mastercard payment network
     */
    MASTERCARD("Mastercard", "5"),

    /**
     * American Express
     */
    AMERICAN_EXPRESS("American Express", "3"),

    /**
     * Discover payment network
     */
    DISCOVER("Discover", "6"),

    /**
     * Verve (African payment network)
     */
    VERVE("Verve", "5061"),

    /**
     * UnionPay (Chinese payment network)
     */
    UNIONPAY("UnionPay", "62");

    private final String displayName;
    private final String binPrefix;

    CardBrand(String displayName, String binPrefix) {
        this.displayName = displayName;
        this.binPrefix = binPrefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBinPrefix() {
        return binPrefix;
    }

    /**
     * Determine card brand from card number
     *
     * @param cardNumber The card number
     * @return CardBrand or null if not recognized
     */
    public static CardBrand fromCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return null;
        }

        String prefix = cardNumber.substring(0, Math.min(4, cardNumber.length()));

        if (prefix.startsWith("4")) return VISA;
        if (prefix.startsWith("5") && !prefix.startsWith("5061")) return MASTERCARD;
        if (prefix.startsWith("5061")) return VERVE;
        if (prefix.startsWith("3")) return AMERICAN_EXPRESS;
        if (prefix.startsWith("6")) return DISCOVER;
        if (prefix.startsWith("62")) return UNIONPAY;

        return null;
    }
}
