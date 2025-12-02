package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.waqiti.common.validation.ValidationConstraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Validated Payment Request DTO with comprehensive validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payment request with full validation")
public class ValidatedPaymentRequest {
    
    @NotNull(message = "Transaction ID is required")
    @Schema(description = "Unique transaction identifier", required = true)
    private UUID transactionId;
    
    @NotNull(message = "Sender ID is required")
    @Schema(description = "Sender user ID", required = true)
    private UUID senderId;
    
    @NotNull(message = "Recipient is required")
    @Schema(description = "Recipient identifier (user ID, email, phone)", required = true)
    @NoSQLInjection
    @NoXSS
    private String recipientIdentifier;
    
    @NotNull(message = "Amount is required")
    @ValidCurrencyAmount(min = "0.01", max = "1000000.00")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "1000000.00", message = "Amount cannot exceed 1,000,000.00")
    @Digits(integer = 7, fraction = 2, message = "Amount must have at most 7 integer digits and 2 decimal places")
    @Schema(description = "Payment amount", required = true, example = "100.50")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    @Schema(description = "Currency ISO code", required = true, example = "USD")
    private String currency;
    
    @NotNull(message = "Payment type is required")
    @Schema(description = "Type of payment", required = true)
    private PaymentType paymentType;
    
    @NotNull(message = "Payment method is required")
    @Schema(description = "Payment method", required = true)
    private PaymentMethod paymentMethod;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    @NoSQLInjection
    @NoXSS
    @Schema(description = "Payment description", maxLength = 500)
    private String description;
    
    @Size(max = 200, message = "Reference cannot exceed 200 characters")
    @Pattern(regexp = "^[A-Za-z0-9-_]+$", message = "Reference can only contain alphanumeric characters, hyphens, and underscores")
    @Schema(description = "External reference", maxLength = 200)
    private String externalReference;
    
    @Valid
    @Schema(description = "Bank account details for bank transfers")
    private BankAccountDetails bankAccountDetails;
    
    @Valid
    @Schema(description = "Card details for card payments")
    private CardDetails cardDetails;
    
    @Valid
    @Schema(description = "Crypto details for cryptocurrency payments")
    private CryptoDetails cryptoDetails;
    
    @NotNull(message = "Idempotency key is required")
    @Size(min = 16, max = 64, message = "Idempotency key must be between 16 and 64 characters")
    @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "Idempotency key can only contain alphanumeric characters and hyphens")
    @Schema(description = "Idempotency key for duplicate prevention", required = true)
    private String idempotencyKey;
    
    @Future(message = "Scheduled date must be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Scheduled payment date for future payments")
    private LocalDateTime scheduledDate;
    
    @AssertTrue(message = "Recurring payment requires valid frequency")
    @Schema(description = "Is this a recurring payment")
    private boolean isRecurring;
    
    @Valid
    @Schema(description = "Recurring payment configuration")
    private RecurringConfig recurringConfig;
    
    @Size(max = 50, message = "Source IP cannot exceed 50 characters")
    @Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$|^(?:[A-F0-9]{1,4}:){7}[A-F0-9]{1,4}$", 
             message = "Invalid IP address format")
    @Schema(description = "Source IP address")
    private String sourceIp;
    
    @Size(max = 500, message = "User agent cannot exceed 500 characters")
    @Schema(description = "User agent string")
    private String userAgent;
    
    @NotNull(message = "Device ID is required")
    @Size(min = 16, max = 128, message = "Device ID must be between 16 and 128 characters")
    @Schema(description = "Device identifier", required = true)
    private String deviceId;
    
    @Valid
    @Schema(description = "Geolocation information")
    private GeolocationInfo geolocation;
    
    @Size(max = 10)
    @Schema(description = "Additional metadata")
    private Map<@NotBlank @Size(max = 50) String, @Size(max = 500) String> metadata;
    
    @AssertTrue(message = "Terms must be accepted")
    @Schema(description = "User accepted terms and conditions", required = true)
    private boolean termsAccepted;
    
    @AssertTrue(message = "Privacy policy must be accepted")
    @Schema(description = "User accepted privacy policy", required = true)
    private boolean privacyPolicyAccepted;
    
    // Nested validated DTOs
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankAccountDetails {
        
        @NotBlank(message = "Account holder name is required")
        @Size(min = 2, max = 100, message = "Account holder name must be between 2 and 100 characters")
        @Pattern(regexp = "^[A-Za-z\\s\\-']+$", message = "Account holder name contains invalid characters")
        @NoXSS
        private String accountHolderName;
        
        @NotBlank(message = "Account number is required")
        @ValidAccountNumber
        private String accountNumber;
        
        @NotBlank(message = "Routing number is required")
        @ValidRoutingNumber
        private String routingNumber;
        
        @NotNull(message = "Account type is required")
        private AccountType accountType;
        
        @ValidIBAN
        private String iban;
        
        @ValidSwiftCode
        private String swiftCode;
        
        @Size(max = 100)
        @NoXSS
        private String bankName;
        
        @Size(max = 200)
        @NoXSS
        private String bankAddress;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardDetails {
        
        @NotBlank(message = "Card number is required")
        @ValidCardNumber
        private String cardNumber;
        
        @NotBlank(message = "Card holder name is required")
        @Size(min = 2, max = 100, message = "Card holder name must be between 2 and 100 characters")
        @Pattern(regexp = "^[A-Za-z\\s\\-']+$", message = "Card holder name contains invalid characters")
        @NoXSS
        private String cardHolderName;
        
        @NotBlank(message = "Expiry month is required")
        @Pattern(regexp = "^(0[1-9]|1[0-2])$", message = "Invalid expiry month format")
        private String expiryMonth;
        
        @NotBlank(message = "Expiry year is required")
        @Pattern(regexp = "^20[2-9][0-9]$", message = "Invalid expiry year format")
        private String expiryYear;
        
        @NotBlank(message = "CVV is required")
        @ValidCVV
        private String cvv;
        
        @Valid
        private BillingAddress billingAddress;
        
        @AssertTrue(message = "Card must not be expired")
        public boolean isCardNotExpired() {
            try {
                int month = Integer.parseInt(expiryMonth);
                int year = Integer.parseInt(expiryYear);
                LocalDateTime expiry = LocalDateTime.of(year, month, 1, 0, 0).plusMonths(1);
                return expiry.isAfter(LocalDateTime.now());
            } catch (Exception e) {
                return false;
            }
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CryptoDetails {
        
        @NotBlank(message = "From address is required")
        @ValidCryptoAddress
        private String fromAddress;
        
        @NotBlank(message = "To address is required")
        @ValidCryptoAddress
        private String toAddress;
        
        @NotNull(message = "Crypto currency is required")
        private CryptoCurrency cryptoCurrency;
        
        @DecimalMin(value = "0.00000001", message = "Crypto amount must be positive")
        @Digits(integer = 10, fraction = 8, message = "Invalid crypto amount precision")
        private BigDecimal cryptoAmount;
        
        @Size(max = 100)
        private String transactionHash;
        
        @Min(value = 0, message = "Confirmations cannot be negative")
        @Max(value = 100, message = "Confirmations cannot exceed 100")
        private Integer confirmations;
        
        @DecimalMin(value = "0", message = "Network fee cannot be negative")
        @Digits(integer = 10, fraction = 8)
        private BigDecimal networkFee;
        
        @Size(max = 200)
        private String memo;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillingAddress {
        
        @NotBlank(message = "Street address is required")
        @Size(min = 5, max = 200, message = "Street address must be between 5 and 200 characters")
        @NoXSS
        private String streetAddress;
        
        @Size(max = 100)
        @NoXSS
        private String addressLine2;
        
        @NotBlank(message = "City is required")
        @Size(min = 2, max = 100, message = "City must be between 2 and 100 characters")
        @Pattern(regexp = "^[A-Za-z\\s\\-']+$", message = "City contains invalid characters")
        private String city;
        
        @NotBlank(message = "State/Province is required")
        @Size(min = 2, max = 50, message = "State/Province must be between 2 and 50 characters")
        private String stateProvince;
        
        @NotBlank(message = "Postal code is required")
        @ValidPostalCode
        private String postalCode;
        
        @NotBlank(message = "Country is required")
        @Size(min = 2, max = 2, message = "Country must be 2-letter ISO code")
        @Pattern(regexp = "^[A-Z]{2}$", message = "Invalid country code format")
        private String countryCode;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecurringConfig {
        
        @NotNull(message = "Frequency is required for recurring payments")
        private RecurringFrequency frequency;
        
        @Min(value = 1, message = "Interval must be at least 1")
        @Max(value = 365, message = "Interval cannot exceed 365")
        private Integer interval;
        
        @Future(message = "Start date must be in the future")
        @NotNull(message = "Start date is required for recurring payments")
        private LocalDateTime startDate;
        
        @Future(message = "End date must be in the future")
        private LocalDateTime endDate;
        
        @Min(value = 1, message = "Max occurrences must be at least 1")
        @Max(value = 999, message = "Max occurrences cannot exceed 999")
        private Integer maxOccurrences;
        
        @AssertTrue(message = "End date must be after start date")
        public boolean isEndDateValid() {
            if (endDate == null || startDate == null) {
                return true;
            }
            return endDate.isAfter(startDate);
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeolocationInfo {
        
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        private Double latitude;
        
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        private Double longitude;
        
        @DecimalMin(value = "0", message = "Accuracy cannot be negative")
        @DecimalMax(value = "10000", message = "Accuracy cannot exceed 10000 meters")
        private Double accuracy;
        
        @Size(max = 100)
        private String city;
        
        @Size(max = 100)
        private String country;
        
        @Size(max = 10)
        @Pattern(regexp = "^[A-Z]{2,3}$", message = "Invalid country code")
        private String countryCode;
    }
    
    // Enums
    
    public enum PaymentType {
        PEER_TO_PEER,
        MERCHANT_PAYMENT,
        BILL_PAYMENT,
        INTERNATIONAL_TRANSFER,
        WITHDRAWAL,
        DEPOSIT
    }
    
    public enum PaymentMethod {
        BANK_ACCOUNT,
        DEBIT_CARD,
        CREDIT_CARD,
        CRYPTOCURRENCY,
        WALLET_BALANCE,
        WIRE_TRANSFER
    }
    
    public enum AccountType {
        CHECKING,
        SAVINGS,
        BUSINESS,
        INVESTMENT
    }
    
    public enum CryptoCurrency {
        BITCOIN,
        ETHEREUM,
        LITECOIN,
        USDC,
        USDT
    }
    
    public enum RecurringFrequency {
        DAILY,
        WEEKLY,
        BIWEEKLY,
        MONTHLY,
        QUARTERLY,
        YEARLY
    }
    
    // Custom validation methods
    
    @AssertTrue(message = "Bank details required for bank account payment method")
    public boolean isBankDetailsProvidedWhenRequired() {
        if (paymentMethod == PaymentMethod.BANK_ACCOUNT) {
            return bankAccountDetails != null;
        }
        return true;
    }
    
    @AssertTrue(message = "Card details required for card payment method")
    public boolean isCardDetailsProvidedWhenRequired() {
        if (paymentMethod == PaymentMethod.DEBIT_CARD || paymentMethod == PaymentMethod.CREDIT_CARD) {
            return cardDetails != null;
        }
        return true;
    }
    
    @AssertTrue(message = "Crypto details required for cryptocurrency payment method")
    public boolean isCryptoDetailsProvidedWhenRequired() {
        if (paymentMethod == PaymentMethod.CRYPTOCURRENCY) {
            return cryptoDetails != null;
        }
        return true;
    }
    
    @AssertTrue(message = "Recurring config required when payment is recurring")
    public boolean isRecurringConfigProvidedWhenRequired() {
        if (isRecurring) {
            return recurringConfig != null;
        }
        return true;
    }
}