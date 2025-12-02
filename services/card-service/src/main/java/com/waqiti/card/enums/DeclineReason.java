package com.waqiti.card.enums;

/**
 * Decline reason enumeration
 * Specific reasons why a transaction was declined
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-09
 */
public enum DeclineReason {
    /**
     * Insufficient funds or credit limit exceeded
     */
    INSUFFICIENT_FUNDS("Insufficient funds", "51"),

    /**
     * Card is expired
     */
    EXPIRED_CARD("Expired card", "54"),

    /**
     * Incorrect PIN entered
     */
    INCORRECT_PIN("Incorrect PIN", "55"),

    /**
     * Card is blocked or restricted
     */
    CARD_BLOCKED("Card blocked", "04"),

    /**
     * Transaction suspected as fraudulent
     */
    SUSPECTED_FRAUD("Suspected fraud", "59"),

    /**
     * Card reported lost or stolen
     */
    LOST_STOLEN_CARD("Lost or stolen card", "43"),

    /**
     * Transaction exceeds withdrawal/spending limit
     */
    LIMIT_EXCEEDED("Limit exceeded", "61"),

    /**
     * Transaction not permitted for this card type
     */
    TRANSACTION_NOT_PERMITTED("Transaction not permitted", "57"),

    /**
     * Invalid card number
     */
    INVALID_CARD("Invalid card number", "14"),

    /**
     * CVV mismatch
     */
    CVV_MISMATCH("CVV verification failed", "82"),

    /**
     * Velocity limit exceeded (too many transactions)
     */
    VELOCITY_LIMIT_EXCEEDED("Too many transactions", "65"),

    /**
     * Geographic restriction (card not allowed in this country)
     */
    GEOGRAPHIC_RESTRICTION("Geographic restriction", "96"),

    /**
     * Merchant category restricted
     */
    MERCHANT_RESTRICTION("Merchant category restricted", "58"),

    /**
     * Do not honor (generic decline)
     */
    DO_NOT_HONOR("Transaction declined", "05"),

    /**
     * Card not activated
     */
    CARD_NOT_ACTIVATED("Card not activated", "78"),

    /**
     * Online transactions not enabled
     */
    ONLINE_NOT_ENABLED("Online transactions not enabled", "79"),

    /**
     * International transactions not enabled
     */
    INTERNATIONAL_NOT_ENABLED("International transactions not enabled", "80"),

    /**
     * 3DS authentication failed
     */
    THREE_DS_FAILED("3DS authentication failed", "86"),

    /**
     * System error or malfunction
     */
    SYSTEM_ERROR("System error", "96"),

    /**
     * Unknown/other reason
     */
    UNKNOWN("Unknown reason", "00");

    private final String description;
    private final String responseCode;

    DeclineReason(String description, String responseCode) {
        this.description = description;
        this.responseCode = responseCode;
    }

    public String getDescription() {
        return description;
    }

    public String getResponseCode() {
        return responseCode;
    }

    /**
     * Get decline reason from response code
     *
     * @param responseCode The response code
     * @return DeclineReason
     */
    public static DeclineReason fromResponseCode(String responseCode) {
        if (responseCode == null) return UNKNOWN;

        for (DeclineReason reason : values()) {
            if (reason.responseCode.equals(responseCode)) {
                return reason;
            }
        }
        return UNKNOWN;
    }
}
