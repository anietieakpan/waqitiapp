/**
 * BNPL Application Request DTO
 * Production-grade validation for all input fields
 */
package com.waqiti.bnpl.dto.request;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BnplApplicationRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;

    @NotBlank(message = "Merchant name is required")
    @Size(max = 255, message = "Merchant name must not exceed 255 characters")
    private String merchantName;

    @NotBlank(message = "Order ID is required")
    @Size(max = 100, message = "Order ID must not exceed 100 characters")
    private String orderId;

    @NotNull(message = "Purchase amount is required")
    @DecimalMin(value = "50.0000", inclusive = true, message = "Minimum purchase amount is $50.00")
    @DecimalMax(value = "10000.0000", inclusive = true, message = "Maximum purchase amount is $10,000.00")
    @Digits(integer = 15, fraction = 4, message = "Invalid amount format (max 15 digits, 4 decimals)")
    private BigDecimal purchaseAmount;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3-letter ISO code (e.g., USD, EUR, GBP)")
    @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
    private String currency = "USD";

    @NotNull(message = "Down payment is required")
    @DecimalMin(value = "0.0000", inclusive = true, message = "Down payment cannot be negative")
    @Digits(integer = 15, fraction = 4, message = "Invalid down payment format")
    private BigDecimal downPayment = BigDecimal.ZERO;

    @NotNull(message = "Requested installments is required")
    @Min(value = 2, message = "Minimum 2 installments required")
    @Max(value = 24, message = "Maximum 24 installments allowed")
    private Integer requestedInstallments = 4;

    @NotBlank(message = "Application source is required")
    @Pattern(regexp = "^(WEB|MOBILE_IOS|MOBILE_ANDROID|API|POS|PARTNER)$",
             message = "Application source must be one of: WEB, MOBILE_IOS, MOBILE_ANDROID, API, POS, PARTNER")
    private String applicationSource;

    @Size(max = 255, message = "Device fingerprint must not exceed 255 characters")
    private String deviceFingerprint;

    @Pattern(regexp = "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$",
             message = "Invalid IP address format (must be valid IPv4 or IPv6)")
    private String ipAddress;

    @Size(max = 1000, message = "User agent must not exceed 1000 characters")
    private String userAgent;
    
    // Shopping cart details (optional but validated if present)
    @Valid
    private List<CartItem> cartItems;

    /**
     * Cart Item nested DTO with validation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItem {

        @NotBlank(message = "SKU is required")
        @Size(max = 100, message = "SKU must not exceed 100 characters")
        private String sku;

        @NotBlank(message = "Product name is required")
        @Size(max = 500, message = "Product name must not exceed 500 characters")
        private String name;

        @Size(max = 100, message = "Category must not exceed 100 characters")
        private String category;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 9999, message = "Quantity must not exceed 9999")
        private Integer quantity;

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.0001", inclusive = true, message = "Unit price must be positive")
        @Digits(integer = 15, fraction = 4, message = "Invalid unit price format")
        private BigDecimal unitPrice;

        @NotNull(message = "Total price is required")
        @DecimalMin(value = "0.0001", inclusive = true, message = "Total price must be positive")
        @Digits(integer = 15, fraction = 4, message = "Invalid total price format")
        private BigDecimal totalPrice;
    }

    /**
     * Custom validation: Down payment must not exceed purchase amount
     */
    @AssertTrue(message = "Down payment cannot exceed purchase amount")
    public boolean isDownPaymentValid() {
        if (purchaseAmount == null || downPayment == null) {
            return true; // Let @NotNull handle null validation
        }
        return downPayment.compareTo(purchaseAmount) <= 0;
    }

    /**
     * Custom validation: Cart items total must match purchase amount (if cart provided)
     */
    @AssertTrue(message = "Cart items total must match purchase amount")
    public boolean isCartTotalValid() {
        if (cartItems == null || cartItems.isEmpty() || purchaseAmount == null) {
            return true; // Cart is optional
        }

        BigDecimal cartTotal = cartItems.stream()
                .map(CartItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Allow 0.01 difference for rounding
        BigDecimal difference = purchaseAmount.subtract(cartTotal).abs();
        return difference.compareTo(new BigDecimal("0.01")) <= 0;
    }
}