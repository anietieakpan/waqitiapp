package com.waqiti.grouppayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Split Participant DTO
 * Input DTO for specifying how a participant should be included in the split
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitParticipant {

    private String participantId;

    private String participantName;

    private String email;

    private String phoneNumber;

    // For percentage-based split
    private BigDecimal percentage;

    // For amount-based split
    private BigDecimal amount;

    // For weight-based split
    private BigDecimal weight;

    // Participant-specific adjustments
    private BigDecimal discountAmount;

    private BigDecimal additionalAmount;

    private String discountReason;
}
