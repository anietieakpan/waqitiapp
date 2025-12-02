package com.waqiti.billingorchestrator.exception;

import java.util.UUID;

/**
 * Exception thrown when an invoice is not found
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
public class InvoiceNotFoundException extends BillingOrchestratorException {

    public InvoiceNotFoundException(UUID invoiceId) {
        super("Invoice not found with ID: " + invoiceId, "INVOICE_NOT_FOUND");
    }

    public InvoiceNotFoundException(String invoiceNumber) {
        super("Invoice not found with number: " + invoiceNumber, "INVOICE_NOT_FOUND");
    }
}
