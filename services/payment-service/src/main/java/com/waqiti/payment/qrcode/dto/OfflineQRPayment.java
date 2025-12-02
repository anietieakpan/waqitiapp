package com.waqiti.payment.qrcode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Offline QR payment data for processing when connectivity is restored
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineQRPayment {
    
    private UUID id;
    private String qrCodeData;
    private String paymentReference;
    
    // Transaction details
    private BigDecimal amount;
    private String currency;
    private String description;
    
    // Parties involved
    private UUID payerId;
    private String payerDevice;
    private UUID merchantId;
    private String merchantName;
    
    // Offline tracking
    private Instant capturedAt;
    private Instant processedAt;
    private ProcessingStatus status;
    private int retryCount;
    private String failureReason;
    
    // Security
    private String offlineToken;
    private String deviceFingerprint;
    private String locationData;
    
    // Validation
    private boolean signatureValid;
    private String signatureData;
    
    // Metadata
    private Map<String, Object> metadata;
    private Map<String, String> headers;
    
    public enum ProcessingStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        EXPIRED,
        CANCELLED,
        RETRY_SCHEDULED
    }
}