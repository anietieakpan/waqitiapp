package com.waqiti.virtualcard.domain.enums;

/**
 * Card type enumeration
 */
public enum CardType {
    /**
     * Virtual card - exists only digitally
     */
    VIRTUAL,
    
    /**
     * Physical card - actual plastic card
     */
    PHYSICAL,
    
    /**
     * Digital wallet card - for mobile payments
     */
    DIGITAL_WALLET
}