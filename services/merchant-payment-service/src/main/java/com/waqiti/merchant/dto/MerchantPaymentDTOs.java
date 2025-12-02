package com.waqiti.merchant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// ============== Registration and Onboarding DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class MerchantRegistrationRequest {
    @NotBlank(message = "Business name is required")
    private String businessName;
    
    @NotBlank(message = "Business type is required")
    private String businessType;
    
    @NotBlank(message = "Registration number is required")
    private String registrationNumber;
    
    @NotBlank(message = "Tax ID is required")
    private String taxId;
    
    @NotBlank(message = "Country is required")
    private String country;
    
    @NotBlank(message = "Contact name is required")
    private String contactName;
    
    @Email(message = "Valid email is required")
    @NotBlank(message = "Email is required")
    private String email;
    
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "Valid phone number is required")
    private String phoneNumber;
    
    private BusinessAddress businessAddress;
    private String website;
    private String industry;
    private Integer expectedMonthlyVolume;
    private String referralCode;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BusinessAddress {
    @NotBlank(message = "Street address is required")
    private String streetAddress;
    
    private String streetAddress2;
    
    @NotBlank(message = "City is required")
    private String city;
    
    @NotBlank(message = "State/Province is required")
    private String stateProvince;
    
    @NotBlank(message = "Postal code is required")
    private String postalCode;
    
    @NotBlank(message = "Country is required")
    private String country;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class MerchantRegistrationResponse {
    private String merchantId;
    private String status;
    private String verificationToken;
    private String message;
    private LocalDateTime createdAt;
    private List<String> nextSteps;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class VerificationResponse {
    private boolean verified;
    private String merchantId;
    private String status;
    private String message;
    private LocalDateTime verifiedAt;
}

// ============== Profile Management DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class MerchantProfileResponse {
    private String merchantId;
    private String businessName;
    private String businessType;
    private String registrationNumber;
    private String taxId;
    private String status;
    private String kycStatus;
    private BusinessAddress businessAddress;
    private ContactInfo contactInfo;
    private BankingInfo bankingInfo;
    private MerchantLimits limits;
    private BigDecimal currentBalance;
    private BigDecimal pendingBalance;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ContactInfo {
    private String primaryContactName;
    private String primaryEmail;
    private String primaryPhone;
    private String secondaryContactName;
    private String secondaryEmail;
    private String secondaryPhone;
    private String supportEmail;
    private String supportPhone;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BankingInfo {
    private String bankName;
    private String accountName;
    private String accountNumber;
    private String routingNumber;
    private String swiftCode;
    private String iban;
    private String currency;
    private boolean verified;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class MerchantLimits {
    private BigDecimal dailyTransactionLimit;
    private BigDecimal singleTransactionLimit;
    private BigDecimal monthlyVolumeLimit;
    private Integer dailyTransactionCount;
    private BigDecimal minimumPayoutAmount;
    private String settlementFrequency;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class UpdateMerchantProfileRequest {
    private String businessName;
    private BusinessAddress businessAddress;
    private ContactInfo contactInfo;
    private String website;
    private String description;
    private List<String> businessHours;
    private Map<String, String> socialMedia;
}

// ============== KYC and Document DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class KYCSubmissionRequest {
    @NotBlank(message = "Document type is required")
    private String documentType;
    
    @NotBlank(message = "Document number is required")
    private String documentNumber;
    
    @NotNull(message = "Issue date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate issueDate;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;
    
    private String issuingCountry;
    private String issuingAuthority;
    private Map<String, String> additionalInfo;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class KYCSubmissionResponse {
    private UUID submissionId;
    private String status;
    private String verificationStatus;
    private List<String> requiredDocuments;
    private List<String> submittedDocuments;
    private String message;
    private LocalDateTime submittedAt;
    private LocalDateTime estimatedReviewTime;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class DocumentUploadResponse {
    private UUID documentId;
    private String documentType;
    private String fileName;
    private String status;
    private Long fileSize;
    private LocalDateTime uploadedAt;
}

// ============== Payment Processing DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ProcessPaymentRequest {
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    private String currency;
    
    @NotBlank(message = "Payment method is required")
    private String paymentMethod;
    
    private String customerId;
    private String customerEmail;
    private String customerPhone;
    private String orderId;
    private String description;
    private Map<String, String> metadata;
    private PaymentCard cardDetails;
    private String returnUrl;
    private String callbackUrl;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PaymentCard {
    @NotBlank(message = "Card number is required")
    private String cardNumber;
    
    @NotBlank(message = "Card holder name is required")
    private String cardHolderName;
    
    @NotBlank(message = "Expiry month is required")
    private String expiryMonth;
    
    @NotBlank(message = "Expiry year is required")
    private String expiryYear;
    
    @NotBlank(message = "CVV is required")
    private String cvv;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PaymentProcessResponse {
    private UUID paymentId;
    private String transactionReference;
    private String status;
    private BigDecimal amount;
    private BigDecimal fee;
    private BigDecimal netAmount;
    private String currency;
    private String paymentMethod;
    private String authorizationCode;
    private String processorResponse;
    private LocalDateTime processedAt;
    private String receiptUrl;
    private Map<String, Object> additionalData;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RefundRequest {
    @NotNull(message = "Payment ID is required")
    private UUID paymentId;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    @NotBlank(message = "Reason is required")
    private String reason;
    
    private String customerNotification;
    private Map<String, String> metadata;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RefundResponse {
    private UUID refundId;
    private UUID paymentId;
    private String status;
    private BigDecimal refundAmount;
    private BigDecimal refundFee;
    private String currency;
    private String reason;
    private LocalDateTime refundedAt;
    private String processorResponse;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PaymentHistoryResponse {
    private UUID paymentId;
    private String transactionReference;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String paymentMethod;
    private String customerName;
    private String customerEmail;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private BigDecimal fee;
    private BigDecimal netAmount;
}

// ============== QR Code Payment DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class GenerateQRCodeRequest {
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    private String currency;
    
    private String reference;
    private String description;
    private Integer expiryMinutes;
    private boolean oneTimeUse;
    private Map<String, String> metadata;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class QRCodeResponse {
    private String qrCodeId;
    private String qrCodeData;
    private String qrCodeImageUrl;
    private BigDecimal amount;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private String deepLink;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class StaticQRCodeRequest {
    private String locationId;
    private String terminalId;
    private String description;
    private boolean acceptTips;
    private Map<String, String> metadata;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class StaticQRCodeResponse {
    private String qrCodeId;
    private String qrCodeData;
    private String qrCodeImageUrl;
    private String type;
    private String status;
    private LocalDateTime createdAt;
    private String downloadUrl;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class QRCodeStatusResponse {
    private String qrCodeId;
    private String status;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private String currency;
    private String payerId;
    private String payerName;
    private LocalDateTime paidAt;
    private String transactionReference;
}

// ============== POS Terminal DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RegisterPOSTerminalRequest {
    @NotBlank(message = "Terminal ID is required")
    private String terminalId;
    
    @NotBlank(message = "Terminal model is required")
    private String terminalModel;
    
    private String serialNumber;
    private String locationId;
    private String locationName;
    private Map<String, String> configuration;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class POSTerminalResponse {
    private String terminalId;
    private String terminalModel;
    private String serialNumber;
    private String status;
    private String locationId;
    private String locationName;
    private LocalDateTime registeredAt;
    private LocalDateTime lastActiveAt;
    private Integer todayTransactionCount;
    private BigDecimal todayTransactionVolume;
}

// ============== Settlement DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SettlementResponse {
    private UUID settlementId;
    private String settlementReference;
    private BigDecimal grossAmount;
    private BigDecimal fees;
    private BigDecimal netAmount;
    private String currency;
    private String status;
    private LocalDate settlementDate;
    private LocalDateTime processedAt;
    private String bankReference;
    private Integer transactionCount;
    private List<String> includedTransactions;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SettlementRequest {
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    private String accountId;
    private String urgency;
    private String reason;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SettlementRequestResponse {
    private UUID requestId;
    private String status;
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private BigDecimal fee;
    private String estimatedArrival;
    private String message;
    private LocalDateTime requestedAt;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class MerchantBalanceResponse {
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private BigDecimal reservedBalance;
    private BigDecimal totalBalance;
    private String currency;
    private LocalDateTime lastUpdated;
    private NextSettlement nextSettlement;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class NextSettlement {
    private LocalDate settlementDate;
    private BigDecimal estimatedAmount;
    private String frequency;
    private Integer transactionCount;
}

// ============== Analytics DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class DashboardAnalyticsResponse {
    private BigDecimal totalRevenue;
    private BigDecimal totalTransactions;
    private Integer transactionCount;
    private BigDecimal averageTransactionValue;
    private BigDecimal todayRevenue;
    private Integer todayTransactions;
    private BigDecimal growthRate;
    private List<ChartDataPoint> revenueChart;
    private List<TopProduct> topProducts;
    private List<PaymentMethodBreakdown> paymentMethods;
    private Map<String, Object> insights;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ChartDataPoint {
    private String label;
    private BigDecimal value;
    private LocalDate date;
    private Integer count;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class TopProduct {
    private String productId;
    private String productName;
    private Integer salesCount;
    private BigDecimal revenue;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PaymentMethodBreakdown {
    private String method;
    private Integer count;
    private BigDecimal amount;
    private BigDecimal percentage;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RevenueAnalyticsResponse {
    private List<RevenueDataPoint> data;
    private BigDecimal totalRevenue;
    private BigDecimal totalFees;
    private BigDecimal netRevenue;
    private Integer totalTransactions;
    private BigDecimal averageTransactionValue;
    private String period;
    private Map<String, BigDecimal> comparisonData;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RevenueDataPoint {
    private LocalDate date;
    private BigDecimal revenue;
    private BigDecimal fees;
    private BigDecimal netRevenue;
    private Integer transactionCount;
}

// ============== Webhook DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class WebhookConfigResponse {
    private UUID webhookId;
    private String url;
    private List<String> events;
    private String status;
    private String signingSecret;
    private LocalDateTime createdAt;
    private LocalDateTime lastTriggeredAt;
    private Integer failureCount;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class CreateWebhookRequest {
    @NotBlank(message = "URL is required")
    @Pattern(regexp = "^https://.*", message = "URL must use HTTPS")
    private String url;
    
    @NotEmpty(message = "At least one event is required")
    private List<String> events;
    
    private String description;
    private Map<String, String> headers;
    private boolean enabled;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class WebhookTestResponse {
    private UUID webhookId;
    private boolean success;
    private Integer statusCode;
    private String response;
    private Long responseTimeMs;
    private String error;
    private LocalDateTime testedAt;
}

// ============== Settings DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class MerchantSettingsResponse {
    private PaymentSettings paymentSettings;
    private NotificationSettings notificationSettings;
    private SecuritySettings securitySettings;
    private SettlementSettings settlementSettings;
    private FraudSettings fraudSettings;
    private LocalDateTime updatedAt;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PaymentSettings {
    private List<String> acceptedPaymentMethods;
    private List<String> acceptedCurrencies;
    private boolean autoCapture;
    private Integer captureDelay;
    private boolean partialRefunds;
    private BigDecimal minimumAmount;
    private BigDecimal maximumAmount;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class NotificationSettings {
    private boolean emailNotifications;
    private boolean smsNotifications;
    private boolean webhookNotifications;
    private List<String> notificationEvents;
    private String notificationEmail;
    private String notificationPhone;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SecuritySettings {
    private boolean twoFactorEnabled;
    private boolean ipWhitelisting;
    private List<String> whitelistedIps;
    private boolean apiKeyRotation;
    private Integer apiKeyRotationDays;
    private String webhookSigningAlgorithm;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SettlementSettings {
    private String frequency;
    private String bankAccountId;
    private BigDecimal minimumSettlementAmount;
    private boolean autoSettlement;
    private Integer settlementDelayDays;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class FraudSettings {
    private boolean enabled;
    private BigDecimal riskThreshold;
    private boolean autoBlockHighRisk;
    private List<String> blockedCountries;
    private List<String> blockedCards;
    private boolean velocityChecks;
    private Integer maxDailyTransactions;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class UpdateSettingsRequest {
    private PaymentSettings paymentSettings;
    private NotificationSettings notificationSettings;
    private SecuritySettings securitySettings;
    private SettlementSettings settlementSettings;
    private FraudSettings fraudSettings;
}