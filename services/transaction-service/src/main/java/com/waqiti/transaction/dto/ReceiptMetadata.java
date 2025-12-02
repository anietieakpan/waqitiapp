package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Metadata for generated receipts
 */
@Data
@Builder
public class ReceiptMetadata {

    /**
     * Unique receipt ID
     */
    private UUID receiptId;

    /**
     * Associated transaction ID
     */
    private UUID transactionId;

    /**
     * Receipt generation timestamp
     */
    private LocalDateTime generatedAt;

    /**
     * Receipt file size in bytes
     */
    private long fileSize;

    /**
     * Security hash for integrity verification
     */
    private String securityHash;

    /**
     * Storage location/path
     */
    private String storagePath;

    /**
     * Receipt format used
     */
    private ReceiptGenerationOptions.ReceiptFormat format;

    /**
     * Receipt version (for template versioning)
     */
    @Builder.Default
    private String version = "1.0";

    /**
     * Expiration date for stored receipt
     */
    private LocalDateTime expiresAt;

    /**
     * Number of times receipt has been accessed
     */
    @Builder.Default
    private int accessCount = 0;

    /**
     * Last access timestamp
     */
    private LocalDateTime lastAccessedAt;

    /**
     * Whether receipt is cached
     */
    @Builder.Default
    private boolean cached = false;
}