package com.waqiti.card.enums;

/**
 * Card type enumeration
 * Defines the different types of cards that can be issued
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-09
 */
public enum CardType {
    /**
     * Debit card - linked to checking/savings account
     */
    DEBIT,

    /**
     * Credit card - revolving credit facility
     */
    CREDIT,

    /**
     * Prepaid card - preloaded with funds
     */
    PREPAID,

    /**
     * Virtual card - digital only, no physical card
     */
    VIRTUAL,

    /**
     * Charge card - full balance due each month
     */
    CHARGE
}
