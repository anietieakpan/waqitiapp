package com.waqiti.payment.core.model;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Merchant payment data model for B2B and merchant transactions
 * Production-ready implementation for merchant payment processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"metadata", "merchantCredentials"})
public class MerchantPaymentData {
    
    @NotNull
    private UUID merchantId;
    
    @NotNull
    @NotBlank
    private String merchantName;
    
    @NotBlank
    private String merchantCategoryCode;
    
    @NotNull
    private MerchantType merchantType;
    
    @NotNull
    private BigDecimal transactionAmount;
    
    @NotNull
    private String currency;
    
    private String terminalId;
    
    private String storeId;
    
    private Location storeLocation;
    
    @NotNull
    private PaymentMethod paymentMethod;
    
    private CardDetails cardDetails;
    
    private String invoiceNumber;
    
    private String purchaseOrderNumber;
    
    @Builder.Default
    private List<LineItem> lineItems = new ArrayList<>();
    
    private BigDecimal taxAmount;
    
    private BigDecimal tipAmount;
    
    private BigDecimal serviceCharge;
    
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private BigDecimal discountPercentage;
    
    private BigDecimal discountAmount;
    
    @NotNull
    @Builder.Default
    private MerchantPaymentStatus status = MerchantPaymentStatus.INITIATED;
    
    private SettlementInfo settlementInfo;
    
    private Map<String, String> merchantCredentials;
    
    @Builder.Default
    private boolean recurring = false;
    
    private String subscriptionId;
    
    @Builder.Default
    private RiskLevel riskLevel = RiskLevel.LOW;
    
    private Map<String, Object> metadata;
    
    public enum MerchantType {
        RETAIL,
        E_COMMERCE,
        RESTAURANT,
        HOSPITALITY,
        TRANSPORTATION,
        ENTERTAINMENT,
        HEALTHCARE,
        EDUCATION,
        UTILITIES,
        GOVERNMENT,
        NON_PROFIT,
        B2B,
        MARKETPLACE,
        SUBSCRIPTION_SERVICE,
        PROFESSIONAL_SERVICES,
        OTHER
    }
    
    public enum PaymentMethod {
        CREDIT_CARD,
        DEBIT_CARD,
        ACH,
        WIRE_TRANSFER,
        DIGITAL_WALLET,
        BANK_TRANSFER,
        CHECK,
        CASH,
        CRYPTOCURRENCY,
        BNPL,
        INVOICE,
        OTHER
    }
    
    public enum MerchantPaymentStatus {
        INITIATED,
        AUTHORIZED,
        CAPTURED,
        SETTLING,
        SETTLED,
        REFUNDED,
        PARTIALLY_REFUNDED,
        VOIDED,
        FAILED,
        CANCELLED,
        DISPUTED,
        CHARGEBACK
    }
    
    public enum RiskLevel {
        VERY_LOW,
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH,
        BLOCKED
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private String address;
        private String city;
        private String state;
        private String country;
        private String postalCode;
        private Double latitude;
        private Double longitude;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardDetails {
        private String lastFourDigits;
        private String cardBrand;
        private String cardType;
        private String issuingBank;
        private String cardCountry;
        private boolean tokenized;
        private String tokenId;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItem {
        @NotNull
        private String itemId;
        
        @NotNull
        private String description;
        
        @NotNull
        @Min(1)
        private Integer quantity;
        
        @NotNull
        private BigDecimal unitPrice;
        
        private BigDecimal discount;
        
        private BigDecimal tax;
        
        @NotNull
        private BigDecimal totalAmount;
        
        private String category;
        
        private String sku;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementInfo {
        private String settlementId;
        private LocalDateTime settlementDate;
        private String batchId;
        private BigDecimal settlementAmount;
        private BigDecimal merchantFee;
        private BigDecimal netAmount;
        private String bankAccountId;
        private SettlementStatus status;
    }
    
    public enum SettlementStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        REVERSED
    }
    
    // Business logic methods
    public BigDecimal calculateTotal() {
        BigDecimal subtotal = lineItems.stream()
            .map(LineItem::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal total = subtotal;
        
        if (taxAmount != null) {
            total = total.add(taxAmount);
        }
        
        if (tipAmount != null) {
            total = total.add(tipAmount);
        }
        
        if (serviceCharge != null) {
            total = total.add(serviceCharge);
        }
        
        if (discountAmount != null) {
            total = total.subtract(discountAmount);
        } else if (discountPercentage != null) {
            BigDecimal discount = subtotal.multiply(discountPercentage)
                .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
            total = total.subtract(discount);
        }
        
        return total;
    }
    
    public boolean requiresSettlement() {
        return status == MerchantPaymentStatus.CAPTURED || 
               status == MerchantPaymentStatus.SETTLING;
    }
    
    public boolean isRefundable() {
        return status == MerchantPaymentStatus.SETTLED || 
               status == MerchantPaymentStatus.CAPTURED;
    }
    
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || 
               riskLevel == RiskLevel.VERY_HIGH || 
               riskLevel == RiskLevel.BLOCKED;
    }
}