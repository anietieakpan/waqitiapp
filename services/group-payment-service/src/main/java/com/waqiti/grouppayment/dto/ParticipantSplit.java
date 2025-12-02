package com.waqiti.grouppayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Participant Split DTO
 * Represents how much each participant owes in a split bill
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantSplit {

    private String participantId;

    private String participantName;

    private BigDecimal amount;

    private BigDecimal percentage;

    private BigDecimal weight;

    private List<BillItem> items;

    private BigDecimal taxes;

    private BigDecimal tips;

    private String paymentStatus; // PENDING, PAID, FAILED

    private String paymentMethod;
}
