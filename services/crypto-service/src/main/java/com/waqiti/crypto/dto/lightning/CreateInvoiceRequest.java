package com.waqiti.crypto.dto.lightning;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.Map;

/**
 * Request DTO for creating a Lightning invoice
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a Lightning Network invoice")
public class CreateInvoiceRequest {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Max(value = 4294967295L, message = "Amount exceeds maximum Lightning invoice limit")
    @Schema(description = "Amount in satoshis", example = "100000", required = true)
    private Long amountSat;

    @NotBlank(message = "Description is required")
    @Size(max = 512, message = "Description must not exceed 512 characters")
    @Schema(description = "Invoice description", example = "Payment for services", required = true)
    private String description;

    @Min(value = 60, message = "Minimum expiry is 60 seconds")
    @Max(value = 31536000, message = "Maximum expiry is 1 year")
    @Schema(description = "Invoice expiry time in seconds", example = "3600", defaultValue = "3600")
    private Integer expirySeconds;

    @Pattern(regexp = "^https?://.*", message = "Webhook URL must be a valid HTTP(S) URL")
    @Schema(description = "Webhook URL for payment notifications", example = "https://example.com/webhook")
    private String webhookUrl;

    @Schema(description = "Custom metadata for the invoice")
    private Map<String, String> metadata;

    @Schema(description = "Enable payment splitting across multiple paths", defaultValue = "false")
    private Boolean enableMpp;

    @Schema(description = "Whether this is a recurring invoice", defaultValue = "false")
    private Boolean recurring;

    @Pattern(regexp = "^P(?!$)(\\d+Y)?(\\d+M)?(\\d+W)?(\\d+D)?(T(?=\\d)(\\d+H)?(\\d+M)?(\\d+S)?)?$",
            message = "Invalid ISO 8601 duration format")
    @Schema(description = "Recurring schedule in ISO 8601 duration format", example = "P1D")
    private String recurringSchedule;

    @Schema(description = "Custom routing hints for private channels")
    private RoutingHint[] routingHints;

    @Schema(description = "Fallback on-chain address if Lightning payment fails")
    @Pattern(regexp = "^(bc1|tb1|bcrt1)[a-z0-9]{39,87}$", 
            message = "Invalid Bitcoin address format")
    private String fallbackAddress;

    @Schema(description = "Enable AMP (Atomic Multi-path Payments)", defaultValue = "false")
    private Boolean enableAmp;

    @Schema(description = "Custom preimage for the payment (32 bytes hex)")
    @Pattern(regexp = "^[0-9a-fA-F]{64}$", message = "Preimage must be 64 hex characters")
    private String customPreimage;

    /**
     * Routing hint for private channels
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Routing hint for reaching the destination through private channels")
    public static class RoutingHint {
        
        @NotBlank
        @Schema(description = "Public key of the hop node", required = true)
        private String hopPubkey;
        
        @NotBlank
        @Schema(description = "Short channel ID", required = true)
        private String chanId;
        
        @NotNull
        @Positive
        @Schema(description = "Fee base in millisatoshi", required = true)
        private Long feeBaseMsat;
        
        @NotNull
        @PositiveOrZero
        @Schema(description = "Fee proportional in millionths", required = true)
        private Long feeProportionalMillionths;
        
        @NotNull
        @Positive
        @Schema(description = "CLTV expiry delta", required = true)
        private Integer cltvExpiryDelta;
    }
}