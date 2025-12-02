package com.waqiti.crypto.dto.lightning;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.util.Map;

/**
 * Response DTO for Lightning invoice creation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Lightning invoice creation response")
public class LightningInvoiceResponse {

    @Schema(description = "Unique invoice identifier", example = "inv_123456789")
    private String invoiceId;

    @Schema(description = "BOLT-11 encoded payment request", 
            example = "lnbc100n1p3q7xz9pp5...")
    private String paymentRequest;

    @Schema(description = "Payment hash (32 bytes hex)", 
            example = "8a7b9c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b")
    private String paymentHash;

    @Schema(description = "Amount in satoshis", example = "100000")
    private Long amountSat;

    @Schema(description = "Invoice description", example = "Payment for services")
    private String description;

    @Schema(description = "Invoice expiry time in seconds", example = "3600")
    private Long expiry;

    @Schema(description = "Invoice creation timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private Instant createdAt;

    @Schema(description = "Invoice expiry timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private Instant expiresAt;

    @Schema(description = "QR code image as base64 data URI", 
            example = "data:image/png;base64,iVBORw0KGgoAAAANS...")
    private String qrCode;

    @Schema(description = "Lightning address for this invoice", 
            example = "user@pay.waqiti.com")
    private String lightningAddress;

    @Schema(description = "LNURL-pay code if applicable")
    private String lnurlPay;

    @Schema(description = "Custom metadata associated with the invoice")
    private Map<String, String> metadata;

    @Schema(description = "URI for Lightning wallet integration", 
            example = "lightning:lnbc100n1p3q7xz9pp5...")
    private String lightningUri;

    @Schema(description = "Fallback on-chain address if specified")
    private String fallbackAddress;

    @Schema(description = "Routing hints for private channels")
    private RoutingHintInfo[] routingHints;

    @Schema(description = "Invoice features supported")
    private String[] features;

    @Schema(description = "Whether multi-path payments are enabled")
    private Boolean mppEnabled;

    @Schema(description = "Whether AMP is enabled")
    private Boolean ampEnabled;

    /**
     * Routing hint information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Routing hint information for private channels")
    public static class RoutingHintInfo {
        
        @Schema(description = "Hop public key")
        private String hopPubkey;
        
        @Schema(description = "Channel ID")
        private String chanId;
        
        @Schema(description = "Base fee in millisatoshi")
        private Long feeBaseMsat;
        
        @Schema(description = "Proportional fee in millionths")
        private Long feeProportionalMillionths;
        
        @Schema(description = "CLTV expiry delta")
        private Integer cltvExpiryDelta;
    }
}