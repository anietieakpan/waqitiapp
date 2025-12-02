package com.waqiti.transaction.service;

import com.waqiti.transaction.dto.ReceiptSecurityValidation;

import java.util.UUID;

/**
 * Service for receipt security features including validation, signing, and fraud detection
 */
public interface ReceiptSecurityService {

    /**
     * Generate digital signature for receipt
     */
    String generateDigitalSignature(byte[] receiptData);

    /**
     * Verify digital signature
     */
    boolean verifyDigitalSignature(byte[] receiptData, String signature);

    /**
     * Generate tamper-evident hash
     */
    String generateTamperHash(byte[] receiptData, UUID transactionId);

    /**
     * Validate receipt integrity
     */
    ReceiptSecurityValidation validateReceiptIntegrity(byte[] receiptData, UUID transactionId, String expectedHash);

    /**
     * Add watermark to PDF
     */
    byte[] addSecurityWatermark(byte[] pdfData, String watermarkText);

    /**
     * Generate QR code for receipt verification
     */
    byte[] generateVerificationQrCode(UUID transactionId, String hash);

    /**
     * Validate QR code
     */
    boolean validateVerificationQrCode(byte[] qrCode, UUID transactionId);

    /**
     * Check for suspicious receipt generation patterns
     */
    boolean detectSuspiciousActivity(UUID transactionId, String userAgent, String ipAddress);

    /**
     * Encrypt sensitive receipt data
     */
    byte[] encryptReceiptData(byte[] receiptData);

    /**
     * Decrypt receipt data
     */
    byte[] decryptReceiptData(byte[] encryptedData);

    /**
     * Generate receipt access token
     */
    String generateAccessToken(UUID transactionId, String userEmail, long validityDurationMinutes);

    /**
     * Validate receipt access token
     */
    boolean validateAccessToken(String token, UUID transactionId);

    /**
     * Calculate comprehensive security score for receipt
     */
    int calculateSecurityScore(byte[] receiptData, UUID transactionId, String userId);

    /**
     * Generate secure access token with expiration and access level
     */
    String generateSecureAccessToken(UUID transactionId, String userId, int expirationMinutes, String accessLevel);

    /**
     * Validate access token and return transaction ID if valid
     */
    UUID validateAndGetTransactionId(String accessToken);
}