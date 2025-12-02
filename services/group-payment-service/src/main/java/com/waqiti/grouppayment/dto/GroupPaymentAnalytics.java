package com.waqiti.grouppayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Group Payment Analytics DTO
 * Contains aggregated analytics data for user's group payment activity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupPaymentAnalytics {

    private String userId;

    private Long totalGroupPayments;

    private Long successfulGroupPayments;

    private Long failedGroupPayments;

    private BigDecimal totalAmount;

    private BigDecimal averageAmount;

    private Double successRate;

    private LocalDateTime periodStart;

    private LocalDateTime periodEnd;

    // Extended analytics
    private Long totalAsOrganizer;

    private Long totalAsParticipant;

    private BigDecimal totalPaid;

    private BigDecimal totalReceived;

    private BigDecimal outstandingAmount;

    private Integer averageParticipantsPerGroup;
}
