package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.util.Locale;
import java.util.Map;

/**
 * Options for customizing receipt generation
 */
@Data
@Builder
public class ReceiptGenerationOptions {

    /**
     * Language/locale for receipt generation
     */
    @Builder.Default
    private Locale locale = Locale.ENGLISH;

    /**
     * Include detailed fee breakdown
     */
    @Builder.Default
    private boolean includeDetailedFees = true;

    /**
     * Include transaction timeline
     */
    @Builder.Default
    private boolean includeTimeline = false;

    /**
     * Include QR code for verification
     */
    @Builder.Default
    private boolean includeQrCode = true;

    /**
     * Include watermark for security
     */
    @Builder.Default
    private boolean includeWatermark = true;

    /**
     * Receipt format/template to use
     */
    @Builder.Default
    private ReceiptFormat format = ReceiptFormat.STANDARD;

    /**
     * Include compliance information
     */
    @Builder.Default
    private boolean includeComplianceInfo = false;

    /**
     * Custom branding options
     */
    private Map<String, Object> brandingOptions;

    /**
     * Additional metadata to include
     */
    private Map<String, Object> additionalData;

    public enum ReceiptFormat {
        STANDARD,
        DETAILED,
        MINIMAL,
        PROOF_OF_PAYMENT,
        TAX_DOCUMENT
    }
}