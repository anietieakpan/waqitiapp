package com.waqiti.payment.commons.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.waqiti.payment.commons.domain.Money;
import com.waqiti.payment.commons.domain.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.validation.groups.Default;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;

/**
 * Enterprise-Grade Standardized Payment Request DTO
 *
 * This is the single source of truth for all payment requests across
 * the Waqiti fintech ecosystem. Designed for:
 * - Maximum security and compliance (PCI DSS, PSD2, GDPR)
 * - Field-level encryption capabilities
 * - Comprehensive validation and business rules
 * - Cross-service compatibility
 * - Event sourcing and audit trails
 * - Performance optimization
 * - Regulatory compliance
 *
 * SECURITY FIX: Replaced @Data with @Getter/@Setter to prevent sensitive data exposure
 * Sensitive fields: securityToken, ipAddress, deviceFingerprint
 *
 * @version 2.0.0 Enhanced Enterprise Edition
 * @since 2025-01-15
 * @author Waqiti Engineering Team
 */
@lombok.Getter
@lombok.Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
public class PaymentRequest {
    
    // =====================================================
    // CORE IDENTIFIERS
    // =====================================================
    
    @NotNull(message = "Request ID is required", groups = {Default.class})
    @JsonProperty("request_id")
    private UUID requestId;
    
    @NotNull(message = "Sender ID is required", groups = {Default.class})
    @JsonProperty("sender_id") 
    private UUID senderId;
    
    @NotNull(message = "Recipient ID is required", groups = {Default.class})
    @JsonProperty("recipient_id")
    private UUID recipientId;
    
    @NotBlank(message = "Recipient type is required", groups = {Default.class})
    @Pattern(regexp = "^(USER|MERCHANT|BUSINESS|EXTERNAL|SYSTEM)$", 
             message = "Invalid recipient type")
    @JsonProperty("recipient_type")
    private String recipientType;
    
    // =====================================================
    // FINANCIAL DETAILS
    // =====================================================
    
    @NotNull(message = "Amount is required", groups = {Default.class})
    @Valid
    @JsonProperty("amount")
    private Money amount;
    
    @Size(max = 500, message = "Description too long")
    @JsonProperty("description")
    private String description;
    
    @NotNull(message = "Payment method is required", groups = {Default.class})
    @JsonProperty("payment_method")
    private PaymentMethod paymentMethod;
    
    @JsonProperty("payment_method_id")
    private String paymentMethodId; // Reference to specific payment instrument
    
    // =====================================================
    // PAYMENT CLASSIFICATION & ROUTING
    // =====================================================
    
    @NotBlank(message = "Payment type is required")
    @Pattern(regexp = "^(P2P|MERCHANT|BILL_PAY|INTERNATIONAL|CRYPTO|GROUP|RECURRING|INSTANT)$")
    @JsonProperty("payment_type")
    private String paymentType;
    
    @JsonProperty("payment_category")
    private String paymentCategory; // GOODS, SERVICES, TRANSFER, INVESTMENT, etc.
    
    @JsonProperty("merchant_category_code")
    private String merchantCategoryCode; // ISO 18245 MCC
    
    @JsonProperty("processing_priority")
    @Pattern(regexp = "^(LOW|NORMAL|HIGH|URGENT|INSTANT)$")
    private String processingPriority = "NORMAL";
    
    // =====================================================
    // TEMPORAL CONTROLS
    // =====================================================
    
    @JsonProperty("created_at")
    private Instant createdAt;
    
    @JsonProperty("scheduled_at")
    private Instant scheduledAt; // For scheduled payments
    
    @JsonProperty("expires_at")
    private Instant expiresAt;
    
    @JsonProperty("recurrence_pattern")
    private String recurrencePattern; // ISO 8601 duration format
    
    @JsonProperty("recurrence_end_date")
    private Instant recurrenceEndDate;
    
    @Min(value = 1, message = "Max occurrences must be positive")
    @Max(value = 1000, message = "Max occurrences cannot exceed 1000")
    @JsonProperty("max_occurrences")
    private Integer maxOccurrences;
    
    // =====================================================
    // SPLIT & GROUP PAYMENTS
    // =====================================================
    
    @JsonProperty("is_split_payment")
    private Boolean isSplitPayment = false;
    
    @JsonProperty("split_payment_id")
    private UUID splitPaymentId;
    
    @JsonProperty("split_percentage")
    @DecimalMin(value = "0.01", message = "Split percentage must be at least 0.01%")
    @DecimalMax(value = "100.00", message = "Split percentage cannot exceed 100%")
    private BigDecimal splitPercentage;
    
    @JsonProperty("is_group_payment")
    private Boolean isGroupPayment = false;
    
    @JsonProperty("group_payment_id")
    private UUID groupPaymentId;
    
    // =====================================================
    // EXTERNAL REFERENCES
    // =====================================================
    
    @JsonProperty("external_reference")
    @Size(max = 100, message = "External reference too long")
    private String reference; // External reference number
    
    @JsonProperty("memo")
    @Size(max = 500, message = "Memo too long")
    private String memo; // User-facing memo/note
    
    @JsonProperty("category")
    private String category; // Payment category for tracking
    
    @JsonProperty("merchant_reference")
    @Size(max = 100, message = "Merchant reference too long")
    private String merchantReference;
    
    @JsonProperty("customer_reference")
    @Size(max = 100, message = "Customer reference too long")
    private String customerReference;
    
    @JsonProperty("invoice_number")
    private String invoiceNumber;
    
    @JsonProperty("order_id")
    private String orderId;
    
    // =====================================================
    // METADATA & EXTENSIBILITY
    // =====================================================
    
    @JsonProperty("tags")
    private Map<String, String> tags = new ConcurrentHashMap<>(); // Key-value tags for categorization
    
    @JsonProperty("metadata")
    private JsonNode metadata; // Flexible metadata storage
    
    @JsonProperty("custom_fields")
    private Map<String, Object> customFields = new ConcurrentHashMap<>();
    
    @JsonProperty("business_context")
    private Map<String, String> businessContext = new ConcurrentHashMap<>();
    
    // =====================================================
    // APPROVAL WORKFLOW
    // =====================================================
    
    @JsonProperty("requires_approval")
    private Boolean requiresApproval = false; // For payment requests that need approval
    
    @JsonProperty("approval_workflow_id")
    private String approvalWorkflowId;
    
    @JsonProperty("approver_ids")
    private List<UUID> approverIds;
    
    @JsonProperty("approval_threshold")
    private Money approvalThreshold;
    
    @Min(value = 1, message = "Required approvals must be at least 1")
    @Max(value = 10, message = "Required approvals cannot exceed 10")
    @JsonProperty("required_approvals")
    private Integer requiredApprovals = 1;
    
    // =====================================================
    // PARTIAL PAYMENTS & LIMITS
    // =====================================================
    
    @JsonProperty("allow_partial_payment")
    private Boolean allowPartialPayment = false; // Allow partial payments
    
    @Valid
    @JsonProperty("minimum_amount")
    private Money minimumAmount; // Minimum amount for partial payments
    
    @Valid
    @JsonProperty("maximum_amount")
    private Money maximumAmount; // Maximum amount allowed
    
    @JsonProperty("daily_limit")
    private Money dailyLimit;
    
    @JsonProperty("monthly_limit")
    private Money monthlyLimit;
    
    // =====================================================
    // SECURITY & COMPLIANCE
    // =====================================================
    
    @JsonProperty("security_level")
    @Pattern(regexp = "^(STANDARD|ENHANCED|MAXIMUM)$")
    private String securityLevel = "STANDARD";
    
    @JsonProperty("security_token")
    private String securityToken; // CRITICAL: Security credential - never log
    
    @JsonProperty("requires_kyc")
    private Boolean requiresKYC = false; // KYC verification required
    
    @JsonProperty("requires_aml_check")
    private Boolean requiresAMLCheck = false;
    
    @JsonProperty("compliance_level")
    @Pattern(regexp = "^(STANDARD|ENHANCED|PREMIUM|INSTITUTIONAL)$")
    private String complianceLevel = "STANDARD"; // STANDARD, ENHANCED, PREMIUM
    
    @JsonProperty("risk_score")
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    private BigDecimal riskScore;
    
    @JsonProperty("fraud_check_required")
    private Boolean fraudCheckRequired = true;
    
    @JsonProperty("sanctions_check_required")
    private Boolean sanctionsCheckRequired = false;
    
    @JsonProperty("pep_check_required")
    private Boolean pepCheckRequired = false;
    
    // =====================================================
    // NOTIFICATIONS & COMMUNICATIONS
    // =====================================================
    
    @JsonProperty("notify_sender")
    private Boolean notifySender = true;
    
    @JsonProperty("notify_recipient")
    private Boolean notifyRecipient = true;
    
    @JsonProperty("notification_channels")
    private String[] notificationChannels = {"EMAIL", "PUSH"}; // EMAIL, SMS, PUSH, IN_APP
    
    @JsonProperty("notification_template")
    private String notificationTemplate;
    
    @JsonProperty("custom_message")
    @Size(max = 1000, message = "Custom message too long")
    private String customMessage;
    
    // =====================================================
    // GEOGRAPHIC & REGULATORY
    // =====================================================
    
    @JsonProperty("sender_country")
    @Pattern(regexp = "^[A-Z]{2}$", message = "Invalid country code")
    private String senderCountry;
    
    @JsonProperty("recipient_country")
    @Pattern(regexp = "^[A-Z]{2}$", message = "Invalid country code")
    private String recipientCountry;
    
    @JsonProperty("regulatory_context")
    @Pattern(regexp = "^(DOMESTIC|INTERNATIONAL|HIGH_RISK|SANCTIONS)$")
    private String regulatoryContext = "DOMESTIC"; // DOMESTIC, INTERNATIONAL, HIGH_RISK
    
    @JsonProperty("jurisdiction")
    private String jurisdiction;
    
    @JsonProperty("tax_reporting_required")
    private Boolean taxReportingRequired = false;
    
    // =====================================================
    // FEE MANAGEMENT
    // =====================================================
    
    @JsonProperty("fee_structure")
    @Pattern(regexp = "^(SENDER_PAYS|RECIPIENT_PAYS|SPLIT|EXTERNAL|WAIVED)$")
    private String feeStructure = "SENDER_PAYS"; // SENDER_PAYS, RECIPIENT_PAYS, SPLIT, EXTERNAL
    
    @Valid
    @JsonProperty("estimated_fees")
    private Money estimatedFees;
    
    @Valid
    @JsonProperty("maximum_fee")
    private Money maximumFee;
    
    @JsonProperty("fee_calculation_method")
    @Pattern(regexp = "^(FIXED|PERCENTAGE|TIERED|DYNAMIC)$")
    private String feeCalculationMethod = "DYNAMIC";
    
    // =====================================================
    // SOURCE & TRACEABILITY
    // =====================================================
    
    @JsonProperty("source_channel")
    @Pattern(regexp = "^(WEB|MOBILE|API|PARTNER|ATM|BRANCH|CALL_CENTER)$")
    private String sourceChannel; // WEB, MOBILE, API, PARTNER
    
    @JsonProperty("source_application")
    private String sourceApplication;
    
    @JsonProperty("user_agent")
    private String userAgent;
    
    @JsonProperty("ip_address")
    @Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$|^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$")
    private String ipAddress; // GDPR: PII - must mask in logs

    @JsonProperty("device_fingerprint")
    private String deviceFingerprint; // GDPR: PII - must mask in logs
    
    @JsonProperty("session_id")
    private String sessionId;
    
    // =====================================================
    // IDEMPOTENCY & RETRY
    // =====================================================
    
    @NotBlank(message = "Idempotency key is required")
    @Size(min = 16, max = 128, message = "Idempotency key must be 16-128 characters")
    @JsonProperty("idempotency_key")
    private String idempotencyKey;
    
    @Min(value = 0, message = "Retry count cannot be negative")
    @Max(value = 5, message = "Retry count cannot exceed 5")
    @JsonProperty("retry_count")
    private Integer retryCount = 0;
    
    @JsonProperty("original_request_id")
    private UUID originalRequestId;
    
    // =====================================================
    // PERFORMANCE & CACHING
    // =====================================================
    
    @JsonProperty("cache_ttl_seconds")
    @Min(value = 0, message = "Cache TTL cannot be negative")
    @Max(value = 86400, message = "Cache TTL cannot exceed 24 hours")
    private Integer cacheTtlSeconds = 300; // 5 minutes default
    
    @JsonProperty("processing_timeout_seconds")
    @Min(value = 1, message = "Processing timeout must be at least 1 second")
    @Max(value = 300, message = "Processing timeout cannot exceed 5 minutes")
    private Integer processingTimeoutSeconds = 30;
    
    // =====================================================
    // AUDIT & TRACKING
    // =====================================================
    
    @JsonIgnore
    private String checksum;
    
    @JsonProperty("version")
    @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$")
    private String version = "2.0.0";
    
    @JsonProperty("created_by")
    private UUID createdBy;
    
    @JsonProperty("tenant_id")
    private String tenantId;
    
    @JsonProperty("correlation_id")
    private String correlationId;
    
    @JsonProperty("trace_id")
    private String traceId;
    
    // =====================================================
    // COMPREHENSIVE VALIDATION METHODS
    // =====================================================
    
    /**
     * Comprehensive validation including business rules
     */
    public void validate() {
        validateBasicFields();
        validateAmounts();
        validateTiming();
        validateCompliance();
        validateBusinessRules();
        calculateChecksum();
    }
    
    private void validateBasicFields() {
        if (amount != null) {
            amount.validatePositive();
        }
        
        if (minimumAmount != null && amount != null) {
            amount.validateMinimumAmount(minimumAmount);
        }
        
        if (maximumAmount != null && amount != null) {
            amount.validateMaximumAmount(maximumAmount);
        }
    }
    
    private void validateAmounts() {
        if (isSplitPayment && splitPercentage != null) {
            if (splitPercentage.compareTo(BigDecimal.ZERO) <= 0 || 
                splitPercentage.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("Split percentage must be between 0.01 and 100.00");
            }
        }
        
        if (estimatedFees != null && maximumFee != null) {
            if (estimatedFees.getAmount().compareTo(maximumFee.getAmount()) > 0) {
                throw new IllegalArgumentException("Estimated fees cannot exceed maximum fee");
            }
        }
    }
    
    private void validateTiming() {
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Payment request has already expired");
        }
        
        if (scheduledAt != null && scheduledAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Scheduled time cannot be in the past");
        }
        
        if (recurrenceEndDate != null && scheduledAt != null && 
            recurrenceEndDate.isBefore(scheduledAt)) {
            throw new IllegalArgumentException("Recurrence end date cannot be before scheduled start");
        }
    }
    
    private void validateCompliance() {
        if (isHighValue() && !requiresKYC) {
            log.warn("High-value transaction without KYC requirement: {}", requestId);
        }
        
        if (isInternational() && !requiresAMLCheck) {
            requiresAMLCheck = true;
            log.info("International transaction automatically requires AML check: {}", requestId);
        }
        
        if (isHighRisk() && !fraudCheckRequired) {
            fraudCheckRequired = true;
            log.info("High-risk transaction automatically requires fraud check: {}", requestId);
        }
    }
    
    private void validateBusinessRules() {
        // Additional business rule validations would be implemented here
        // This could include calls to PaymentBusinessRules service
        if (paymentType != null && amount != null) {
            validatePaymentTypeRules();
        }
        
        if (recipientType != null && amount != null) {
            validateRecipientTypeRules();
        }
        
        if (senderCountry != null && recipientCountry != null) {
            validateGeographicRestrictions();
        }
    }
    
    private void validatePaymentTypeRules() {
        // Payment type specific validation rules
        switch (paymentType) {
            case "CRYPTO":
                if (!"MAXIMUM".equals(securityLevel)) {
                    log.warn("Crypto payment without maximum security: {}", requestId);
                }
                break;
            case "INTERNATIONAL":
                if (!requiresAMLCheck) {
                    requiresAMLCheck = true;
                }
                break;
            case "INSTANT":
                if (processingTimeoutSeconds > 10) {
                    processingTimeoutSeconds = 10; // Instant payments need faster timeout
                }
                break;
        }
    }
    
    private void validateRecipientTypeRules() {
        // Recipient type specific validation rules
        if ("MERCHANT".equals(recipientType) && merchantCategoryCode == null) {
            log.warn("Merchant payment without MCC: {}", requestId);
        }
    }
    
    private void validateGeographicRestrictions() {
        // Geographic restriction validations
        if (isInternational() && "DOMESTIC".equals(regulatoryContext)) {
            regulatoryContext = "INTERNATIONAL";
            log.info("Updated regulatory context for international payment: {}", requestId);
        }
    }
    
    // =====================================================
    // ENHANCED CONVENIENCE METHODS
    // =====================================================
    
    public boolean isScheduled() {
        return scheduledAt != null && scheduledAt.isAfter(Instant.now());
    }
    
    public boolean isRecurring() {
        return recurrencePattern != null && !recurrencePattern.trim().isEmpty();
    }
    
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
    
    public boolean isInternational() {
        return senderCountry != null && recipientCountry != null && 
               !senderCountry.equals(recipientCountry);
    }
    
    public boolean isHighValue() {
        if (amount == null) return false;
        
        // Dynamic high-value thresholds based on currency and regulatory context
        BigDecimal threshold = switch (amount.getCurrencyCode()) {
            case "USD", "EUR", "GBP" -> new BigDecimal("10000");
            case "JPY" -> new BigDecimal("1000000");
            case "NGN" -> new BigDecimal("5000000");
            default -> new BigDecimal("10000");
        };
        
        // Adjust threshold based on compliance level
        if ("ENHANCED".equals(complianceLevel)) {
            threshold = threshold.multiply(new BigDecimal("0.5"));
        } else if ("PREMIUM".equals(complianceLevel)) {
            threshold = threshold.multiply(new BigDecimal("0.25"));
        }
        
        return amount.getAmount().compareTo(threshold) >= 0;
    }
    
    public boolean isHighRisk() {
        return riskScore != null && riskScore.compareTo(new BigDecimal("75")) >= 0;
    }
    
    public boolean requiresManualReview() {
        return isHighValue() || isHighRisk() || 
               "HIGH_RISK".equals(regulatoryContext) || 
               sanctionsCheckRequired || pepCheckRequired;
    }
    
    public boolean isInstantPayment() {
        return "INSTANT".equals(paymentType) || "URGENT".equals(processingPriority);
    }
    
    public boolean isCryptoPayment() {
        return "CRYPTO".equals(paymentType);
    }
    
    public boolean isMerchantPayment() {
        return "MERCHANT".equals(recipientType) || "MERCHANT".equals(paymentType);
    }
    
    public boolean isGroupPayment() {
        return Boolean.TRUE.equals(isGroupPayment) || "GROUP".equals(paymentType);
    }
    
    public String getPaymentMethodCode() {
        return paymentMethod != null ? paymentMethod.getCode() : null;
    }
    
    public void calculateChecksum() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String data = String.format("%s|%s|%s|%s|%s", 
                requestId, senderId, recipientId, 
                amount != null ? amount.toString() : "",
                createdAt != null ? createdAt.toString() : "");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            this.checksum = bytesToHex(hash);
        } catch (Exception e) {
            log.error("Error calculating checksum for payment request {}", requestId, e);
            this.checksum = UUID.randomUUID().toString();
        }
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Custom toString() that masks sensitive fields per GDPR & security best practices
     * Redacts: securityToken, ipAddress, deviceFingerprint
     */
    @Override
    public String toString() {
        return "PaymentRequest{" +
            "requestId=" + requestId +
            ", senderId=" + senderId +
            ", recipientId=" + recipientId +
            ", recipientType='" + recipientType + '\'' +
            ", amount=" + amount +
            ", paymentType='" + paymentType + '\'' +
            ", paymentMethod=" + paymentMethod +
            ", securityToken='[REDACTED]'" +
            ", ipAddress='[REDACTED-GDPR]'" +
            ", deviceFingerprint='[REDACTED-GDPR]'" +
            ", idempotencyKey='" + idempotencyKey + '\'' +
            ", version='" + version + '\'' +
            ", checksum='" + checksum + '\'' +
            '}';
    }

    // =====================================================
    // ENHANCED BUILDER CUSTOMIZATIONS
    // =====================================================
    
    public static class PaymentRequestBuilder {

        public PaymentRequestBuilder amount(BigDecimal amount, String currencyCode) {
            this.amount = Money.of(amount, currencyCode);
            return this;
        }
        
        public PaymentRequestBuilder paymentMethod(String methodCode) {
            this.paymentMethod = PaymentMethod.fromCode(methodCode);
            return this;
        }
        
        public PaymentRequestBuilder generateRequestId() {
            this.requestId = UUID.randomUUID();
            return this;
        }
        
        public PaymentRequestBuilder generateIdempotencyKey() {
            this.idempotencyKey = UUID.randomUUID().toString();
            return this;
        }
        
        public PaymentRequestBuilder withDefaults() {
            this.requestId = UUID.randomUUID();
            this.createdAt = Instant.now();
            this.idempotencyKey = UUID.randomUUID().toString();
            this.notifySender = true;
            this.notifyRecipient = true;
            this.notificationChannels = new String[]{"EMAIL", "PUSH"};
            this.requiresApproval = false;
            this.allowPartialPayment = false;
            this.complianceLevel = "STANDARD";
            this.securityLevel = "STANDARD";
            this.feeStructure = "SENDER_PAYS";
            this.processingPriority = "NORMAL";
            this.fraudCheckRequired = true;
            this.cacheTtlSeconds = 300;
            this.processingTimeoutSeconds = 30;
            this.version = "2.0.0";
            this.retryCount = 0;
            this.isSplitPayment = false;
            this.isGroupPayment = false;
            this.requiresKYC = false;
            this.requiresAMLCheck = false;
            this.taxReportingRequired = false;
            this.regulatoryContext = "DOMESTIC";
            this.feeCalculationMethod = "DYNAMIC";
            return this;
        }
        
        public PaymentRequestBuilder withHighSecurity() {
            this.securityLevel = "MAXIMUM";
            this.complianceLevel = "PREMIUM";
            this.requiresKYC = true;
            this.requiresAMLCheck = true;
            this.fraudCheckRequired = true;
            this.sanctionsCheckRequired = true;
            this.pepCheckRequired = true;
            return this;
        }
        
        public PaymentRequestBuilder withInternationalCompliance() {
            this.regulatoryContext = "INTERNATIONAL";
            this.requiresAMLCheck = true;
            this.sanctionsCheckRequired = true;
            this.taxReportingRequired = true;
            return this;
        }
        
        public PaymentRequestBuilder withCryptoDefaults() {
            this.paymentType = "CRYPTO";
            this.securityLevel = "MAXIMUM";
            this.complianceLevel = "PREMIUM";
            this.requiresKYC = true;
            this.requiresAMLCheck = true;
            this.processingPriority = "HIGH";
            return this;
        }
        
        public PaymentRequestBuilder withInstantDefaults() {
            this.paymentType = "INSTANT";
            this.processingPriority = "URGENT";
            this.processingTimeoutSeconds = 10;
            this.cacheTtlSeconds = 60;
            return this;
        }
        
        public PaymentRequestBuilder withMerchantDefaults() {
            this.paymentType = "MERCHANT";
            this.recipientType = "MERCHANT";
            this.requiresApproval = false;
            this.notifyRecipient = true;
            return this;
        }
        
        public PaymentRequestBuilder withRecurringDefaults() {
            this.paymentType = "RECURRING";
            this.requiresApproval = false;
            this.allowPartialPayment = true;
            return this;
        }
        
        public PaymentRequestBuilder withGroupDefaults() {
            this.paymentType = "GROUP";
            this.isGroupPayment = true;
            this.allowPartialPayment = true;
            this.requiresApproval = true;
            return this;
        }
        
        public PaymentRequestBuilder withTracing(String correlationId, String traceId) {
            this.correlationId = correlationId;
            this.traceId = traceId;
            return this;
        }
    }
}