package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response for card verification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardVerificationResponse {
    private boolean verified;
    private UUID cardId; // If saved
    private String last4;
    private String cardType;
    private String network;
    private boolean instantDepositEligible;
    private String verificationMethod;
    private String failureReason;
    private Double estimatedFeePercentage;
    private Double estimatedMinFee;
    private Double estimatedMaxFee;
}