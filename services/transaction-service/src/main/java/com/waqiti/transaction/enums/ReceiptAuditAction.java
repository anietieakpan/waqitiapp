package com.waqiti.transaction.enums;

/**
 * Enumeration of receipt audit actions
 */
public enum ReceiptAuditAction {
    RECEIPT_GENERATED("Receipt generated"),
    RECEIPT_DOWNLOADED("Receipt downloaded"),
    RECEIPT_EMAILED("Receipt emailed"),
    RECEIPT_VERIFIED("Receipt verified"),
    RECEIPT_SHARED("Receipt shared"),
    RECEIPT_DELETED("Receipt deleted"),
    RECEIPT_ACCESSED_WITH_TOKEN("Receipt accessed with token"),
    SUSPICIOUS_ACTIVITY("Suspicious activity detected"),
    BULK_DOWNLOAD_REQUESTED("Bulk download requested"),
    SECURITY_VALIDATION_FAILED("Security validation failed"),
    COMPLIANCE_VIOLATION("Compliance violation detected"),
    PROOF_OF_PAYMENT_GENERATED("Proof of payment generated"),
    TAX_DOCUMENT_GENERATED("Tax document generated"),
    RECEIPT_EXPIRED("Receipt expired"),
    RECEIPT_ARCHIVED("Receipt archived");

    private final String description;

    ReceiptAuditAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}