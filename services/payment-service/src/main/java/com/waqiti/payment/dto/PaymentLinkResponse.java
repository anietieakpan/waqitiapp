package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.waqiti.payment.domain.PaymentLink.PaymentLinkStatus;
import com.waqiti.payment.domain.PaymentLink.PaymentLinkType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for payment links
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLinkResponse {
    
    private UUID id;
    private String linkId;
    private UUID creatorId;
    private String creatorName; // Enriched from user service
    private String title;
    private String description;
    private BigDecimal amount;
    private String currency;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private PaymentLinkType linkType;
    private PaymentLinkStatus status;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;
    
    private Integer maxUses;
    private Integer currentUses;
    private BigDecimal totalCollected;
    private Boolean requiresNote;
    private String customMessage;
    private Map<String, String> metadata;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastUsedAt;
    
    // Computed fields
    private String shareableUrl;
    private String shortUrl; // Shortened URL for sharing
    private String qrCode; // QR code URL
    private Boolean isActive;
    private Boolean isExpired;
    private Boolean hasReachedMaxUses;
    private Boolean canAcceptPayment;
    private Boolean isFlexibleAmount;
    private Integer remainingUses;
    private Double successRate; // Success rate of payments
    private LocalDateTime estimatedExpirationWarning; // Warning time before expiration
    
    // Statistics (optional, for detailed view)
    private PaymentLinkStatistics statistics;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentLinkStatistics {
        private Long totalTransactions;
        private Long completedTransactions;
        private Long failedTransactions;
        private Long pendingTransactions;
        private BigDecimal totalAmount;
        private BigDecimal averageAmount;
        private Double successRate;
        private String mostCommonPaymentMethod;
        private Integer uniquePayers;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime firstPaymentAt;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime lastPaymentAt;
        
        // Geographic and temporal insights
        private Map<String, Integer> paymentsByCountry;
        private Map<String, Integer> paymentsByHour;
        private Map<String, Integer> paymentsByDay;
        private Map<String, Integer> paymentsByMethod;
    }
    
    // Builder helper methods
    public static PaymentLinkResponse fromDomain(com.waqiti.payment.domain.PaymentLink paymentLink) {
        return PaymentLinkResponse.builder()
                .id(paymentLink.getId())
                .linkId(paymentLink.getLinkId())
                .creatorId(paymentLink.getCreatorId())
                .title(paymentLink.getTitle())
                .description(paymentLink.getDescription())
                .amount(paymentLink.getAmount())
                .currency(paymentLink.getCurrency())
                .minAmount(paymentLink.getMinAmount())
                .maxAmount(paymentLink.getMaxAmount())
                .linkType(paymentLink.getLinkType())
                .status(paymentLink.getStatus())
                .expiresAt(paymentLink.getExpiresAt())
                .maxUses(paymentLink.getMaxUses())
                .currentUses(paymentLink.getCurrentUses())
                .totalCollected(paymentLink.getTotalCollected())
                .requiresNote(paymentLink.getRequiresNote())
                .customMessage(paymentLink.getCustomMessage())
                .metadata(paymentLink.getMetadata())
                .createdAt(paymentLink.getCreatedAt())
                .updatedAt(paymentLink.getUpdatedAt())
                .lastUsedAt(paymentLink.getLastUsedAt())
                .shareableUrl(paymentLink.getShareableUrl())
                .isActive(paymentLink.isActive())
                .isExpired(paymentLink.isExpired())
                .hasReachedMaxUses(paymentLink.hasReachedMaxUses())
                .canAcceptPayment(paymentLink.canAcceptPayment())
                .isFlexibleAmount(paymentLink.isFlexibleAmount())
                .remainingUses(paymentLink.getMaxUses() != null ? 
                              paymentLink.getMaxUses() - paymentLink.getCurrentUses() : null)
                .build();
    }
}