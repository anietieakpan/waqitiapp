package com.waqiti.transaction.service;

import com.waqiti.transaction.dto.ReceiptGenerationOptions;
import com.waqiti.transaction.dto.ReceiptMetadata;
import com.waqiti.transaction.entity.Transaction;

import java.util.UUID;

/**
 * Service for generating and managing transaction receipts and proof of payment documents.
 * Provides secure PDF generation, storage, and delivery capabilities.
 */
public interface ReceiptService {

    /**
     * Generate a PDF receipt for a transaction
     * @param transaction The transaction to generate receipt for
     * @return PDF receipt as byte array
     */
    byte[] generateReceipt(Transaction transaction);

    /**
     * Generate a PDF receipt with custom options
     * @param transaction The transaction to generate receipt for
     * @param options Custom receipt generation options
     * @return PDF receipt as byte array
     */
    byte[] generateReceipt(Transaction transaction, ReceiptGenerationOptions options);

    /**
     * Generate and store receipt, returning metadata
     * @param transaction The transaction to generate receipt for
     * @return Receipt metadata including storage location and security hash
     */
    ReceiptMetadata generateAndStoreReceipt(Transaction transaction);

    /**
     * Retrieve stored receipt by transaction ID
     * @param transactionId Transaction ID
     * @return PDF receipt as byte array, or null if not found
     */
    byte[] getStoredReceipt(UUID transactionId);

    /**
     * Verify receipt integrity using security hash
     * @param receiptData Receipt PDF data
     * @param expectedHash Expected security hash
     * @return true if receipt is valid and unmodified
     */
    boolean verifyReceiptIntegrity(byte[] receiptData, String expectedHash);

    /**
     * Send receipt via email
     * @param transactionId Transaction ID
     * @param recipientEmail Email address to send to
     * @return true if email was sent successfully
     */
    boolean emailReceipt(UUID transactionId, String recipientEmail);

    /**
     * Generate proof of payment document for compliance
     * @param transaction The transaction to generate proof for
     * @return Proof of payment PDF as byte array
     */
    byte[] generateProofOfPayment(Transaction transaction);

    /**
     * Delete stored receipt (for privacy compliance)
     * @param transactionId Transaction ID
     * @return true if receipt was deleted successfully
     */
    boolean deleteStoredReceipt(UUID transactionId);
}