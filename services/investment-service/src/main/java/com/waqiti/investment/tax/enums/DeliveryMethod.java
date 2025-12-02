package com.waqiti.investment.tax.enums;

/**
 * Tax Document Delivery Methods
 *
 * @author Waqiti Platform Team
 * @since 2025-10-01
 */
public enum DeliveryMethod {
    /**
     * Email delivery with PDF attachment
     */
    EMAIL,

    /**
     * Physical mail via USPS
     */
    POSTAL_MAIL,

    /**
     * Available in customer's online portal
     */
    ONLINE_PORTAL,

    /**
     * Secure download link
     */
    SECURE_DOWNLOAD
}
