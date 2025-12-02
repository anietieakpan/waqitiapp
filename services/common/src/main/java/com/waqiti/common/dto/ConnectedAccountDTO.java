package com.waqiti.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for Connected Account information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectedAccountDTO {
    
    private UUID accountId;
    private UUID userId;
    private String accountType;
    private String bankName;
    private String accountNumber; // Masked
    private String routingNumber;
    private String accountHolderName;
    private String status;
    private boolean isPrimary;
    private boolean isVerified;
    private LocalDateTime verifiedAt;
    private LocalDateTime connectedAt;
    private LocalDateTime lastUsedAt;
}