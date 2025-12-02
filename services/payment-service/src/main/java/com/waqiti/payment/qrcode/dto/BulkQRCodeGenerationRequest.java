package com.waqiti.payment.qrcode.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * CRITICAL P0 FIX: Bulk QR Code Generation Request DTO
 *
 * Request DTO for generating multiple QR codes in a single batch operation.
 * Used by merchants for generating codes for multiple terminals or products.
 *
 * @author Waqiti Engineering Team - Production Fix
 * @version 2.0.0
 * @since 2025-01-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for bulk generation of QR codes")
public class BulkQRCodeGenerationRequest {

    @NotBlank(message = "Merchant ID is required")
    @Schema(description = "ID of the merchant generating QR codes",
            required = true,
            example = "mch_789012")
    private String merchantId;

    @Schema(description = "User ID initiating bulk generation", example = "usr_123456")
    private String userId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 1000, message = "Cannot generate more than 1000 QR codes at once")
    @Schema(description = "Number of QR codes to generate",
            required = true,
            example = "50")
    private Integer quantity;

    @NotNull(message = "QR code type is required")
    @Pattern(regexp = "^(MERCHANT_STATIC|MERCHANT_DYNAMIC|USER_DYNAMIC)$",
             message = "Invalid QR code type")
    @Schema(description = "Type of QR codes to generate",
            required = true,
            example = "MERCHANT_STATIC")
    private String type;

    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "100000.00", message = "Amount exceeds maximum limit")
    @Schema(description = "Fixed amount for all QR codes (optional)", example = "99.99")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter ISO code")
    @Schema(description = "Currency code for all QR codes",
            required = true,
            example = "USD")
    private String currency;

    @Size(max = 255, message = "Description too long")
    @Schema(description = "Common description for all QR codes", example = "Store checkout terminals")
    private String description;

    @Size(max = 50, message = "Prefix too long")
    @Pattern(regexp = "^[A-Za-z0-9-_]*$",
             message = "Prefix can only contain alphanumeric characters, hyphens, and underscores")
    @Schema(description = "Reference prefix for generated QR codes", example = "TERM-2024-")
    private String referencePrefix;

    @Schema(description = "List of specific QR code configurations (overrides common settings)")
    @Size(max = 1000, message = "Too many specific configurations")
    private List<QRCodeSpec> qrCodeSpecs;

    @Min(value = 1, message = "Expiry must be at least 1 minute")
    @Max(value = 525600, message = "Expiry cannot exceed 1 year")
    @Schema(description = "QR code expiry time in minutes", example = "43200")
    private Integer expiryMinutes;

    @Schema(description = "Include merchant logo in all QR codes", example = "true")
    private Boolean includeLogo;

    @Size(max = 500, message = "Logo URL too long")
    @Schema(description = "Logo URL for all QR codes")
    private String logoUrl;

    @Min(value = 200)
    @Max(value = 1000)
    @Schema(description = "QR code image size in pixels", example = "400")
    private Integer imageSize;

    @Size(max = 50)
    @Schema(description = "QR code theme", example = "brand")
    private String theme;

    @Size(max = 100)
    @Schema(description = "Common store ID for all QR codes", example = "store_001")
    private String storeId;

    @Schema(description = "Generate printable versions (high resolution)", example = "false")
    private Boolean generatePrintable;

    @Schema(description = "Export format for bulk download", example = "ZIP")
    @Pattern(regexp = "^(ZIP|PDF|CSV|JSON)$", message = "Export format must be ZIP, PDF, CSV, or JSON")
    private String exportFormat;

    @Size(max = 200)
    @Pattern(regexp = "^(https?://)?[\\w.-]+(?:\\.[\\w\\.-]+)+[\\w\\-\\._~:/?#\\[\\]@!\\$&'\\(\\)\\*\\+,;=.]+$",
             message = "Invalid callback URL")
    @Schema(description = "Webhook URL for bulk generation completion notification")
    private String callbackUrl;

    @Schema(description = "Additional metadata for all QR codes")
    private Map<String, String> metadata;

    @Schema(description = "Process asynchronously (for large batches)", example = "true")
    private Boolean async;

    @Schema(description = "Priority level for async processing", example = "NORMAL")
    @Pattern(regexp = "^(LOW|NORMAL|HIGH|URGENT)$",
             message = "Priority must be LOW, NORMAL, HIGH, or URGENT")
    private String priority;

    @Email(message = "Invalid email format")
    @Schema(description = "Email address for completion notification", example = "merchant@example.com")
    private String notificationEmail;

    /**
     * Individual QR code specification (for customized bulk generation)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Individual QR code specification")
    public static class QRCodeSpec {

        @Schema(description = "Custom reference for this QR code", example = "TERM-001")
        private String reference;

        @Schema(description = "Custom amount (overrides common amount)", example = "50.00")
        private BigDecimal amount;

        @Schema(description = "Custom description", example = "Terminal 1 checkout")
        private String description;

        @Schema(description = "Terminal ID", example = "pos_001")
        private String terminalId;

        @Schema(description = "Custom metadata")
        private Map<String, String> metadata;
    }

    // Validation methods
    @AssertTrue(message = "Either quantity or qrCodeSpecs must be provided, but not both")
    private boolean isQuantityOrSpecsValid() {
        boolean hasQuantity = quantity != null && quantity > 0;
        boolean hasSpecs = qrCodeSpecs != null && !qrCodeSpecs.isEmpty();
        return hasQuantity != hasSpecs; // XOR: exactly one must be true
    }
}
