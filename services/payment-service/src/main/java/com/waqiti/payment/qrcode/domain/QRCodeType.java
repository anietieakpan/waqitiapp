package com.waqiti.payment.qrcode.domain;

/**
 * Types of QR codes supported in the payment system
 */
public enum QRCodeType {
    /**
     * Static QR code for user - can receive any amount
     */
    USER_STATIC,
    
    /**
     * Dynamic QR code with fixed amount
     */
    USER_DYNAMIC,
    
    /**
     * Static merchant QR code - accepts any amount
     */
    MERCHANT_STATIC,
    
    /**
     * Dynamic merchant QR code - fixed amount for specific transaction
     */
    MERCHANT_DYNAMIC,
    
    /**
     * QR code for bill payment
     */
    BILL_PAYMENT,
    
    /**
     * QR code for group payment/split bill
     */
    GROUP_PAYMENT,
    
    /**
     * QR code for donation
     */
    DONATION,
    
    /**
     * QR code for event ticket payment
     */
    EVENT_TICKET,
    
    /**
     * QR code for subscription payment
     */
    SUBSCRIPTION,
    
    /**
     * QR code for instant checkout
     */
    INSTANT_CHECKOUT
}