package com.waqiti.payment.entity;

/**
 * Enumeration of QR code payment statuses
 */
public enum QRCodePaymentStatus {
    PENDING,
    SCANNED,
    PROCESSING,
    COMPLETED,
    EXPIRED,
    CANCELLED,
    REJECTED,
    FAILED,
    NOT_FOUND
}