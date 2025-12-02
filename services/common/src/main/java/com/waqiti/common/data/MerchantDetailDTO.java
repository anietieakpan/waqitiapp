package com.waqiti.common.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Merchant Detail DTO for optimized merchant queries
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class MerchantDetailDTO {
    private String merchantId;
    private String merchantName;
    private String businessName;
    private String category;
    private String mcc; // Merchant Category Code
    private String country;
    private String city;
    private String website;
    private String status;
    private BigDecimal totalVolume;
    private int totalTransactions;
    private LocalDateTime lastTransactionDate;
    private List<String> acceptedPaymentMethods;
    private double averageRating;
    private boolean isVerified;
    
    /**
     * Get merchant ID for compatibility
     */
    public String getId() {
        return merchantId;
    }
}