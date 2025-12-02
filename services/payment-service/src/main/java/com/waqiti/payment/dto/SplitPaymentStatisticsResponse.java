package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response for split payment statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SplitPaymentStatisticsResponse {
    private UUID id;
    private String title;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    private String currency;
    private int totalParticipants;
    private int paidParticipants;
    private int unpaidParticipants;
    private BigDecimal completionPercentage;
    private String status;
    private LocalDateTime expiryDate;
}