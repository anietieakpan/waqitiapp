package com.waqiti.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for Payment Method information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodDTO {
    
    private UUID paymentMethodId;
    private UUID userId;
    private String type; // CARD, BANK_ACCOUNT, DIGITAL_WALLET
    private String provider;
    private String displayName; // Masked representation
    private String status;
    private boolean isPrimary;
    private boolean isVerified;
    private LocalDateTime verifiedAt;
    private LocalDateTime addedAt;
    private LocalDateTime lastUsedAt;
}