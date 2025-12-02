package com.waqiti.payment.dto.rewards;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO for payment rewards processing
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessPaymentRewardsResponse implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private UUID paymentId;
    private UUID userId;
    private BigDecimal pointsAwarded;
    private BigDecimal cashbackAwarded;
    private String rewardTier;
    private boolean success;
    private String message;
    private boolean requiresManualProcessing;
}