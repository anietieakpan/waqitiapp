package com.waqiti.payment.nfc;

import com.waqiti.payment.dto.PaymentRequest;
import com.waqiti.payment.dto.PaymentResponse;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for NFC (Near Field Communication) Payment Service
 * Handles contactless payments and mobile wallet integrations
 */
public interface NFCPaymentService {
    
    /**
     * Process NFC payment
     */
    CompletableFuture<PaymentResponse> processNFCPayment(PaymentRequest request, String nfcToken);
    
    /**
     * Initialize NFC payment session
     */
    CompletableFuture<Map<String, Object>> initializeSession(UUID userId, BigDecimal amount);
    
    /**
     * Validate NFC token
     */
    CompletableFuture<Boolean> validateNFCToken(String nfcToken);
    
    /**
     * Register NFC device
     */
    CompletableFuture<String> registerDevice(UUID userId, Map<String, Object> deviceInfo);
    
    /**
     * Get registered NFC devices for user
     */
    CompletableFuture<Map<String, Object>> getUserDevices(UUID userId);
    
    /**
     * Deactivate NFC device
     */
    CompletableFuture<Boolean> deactivateDevice(UUID userId, String deviceId);
    
    /**
     * Process tap-to-pay transaction
     */
    CompletableFuture<PaymentResponse> processTapToPay(
            String merchantId,
            String terminalId,
            BigDecimal amount,
            String nfcData
    );
    
    /**
     * Generate QR code for NFC payment
     */
    CompletableFuture<String> generatePaymentQRCode(UUID userId, BigDecimal amount, String reference);
    
    /**
     * Verify contactless payment
     */
    CompletableFuture<Boolean> verifyContactlessPayment(String paymentId, String verificationCode);
    
    /**
     * Get NFC transaction history
     */
    CompletableFuture<Map<String, Object>> getNFCTransactionHistory(UUID userId, int limit);
}