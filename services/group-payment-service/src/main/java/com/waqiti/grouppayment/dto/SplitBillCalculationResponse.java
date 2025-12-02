package com.waqiti.grouppayment.dto;

import com.waqiti.grouppayment.domain.SplitMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Split Bill Calculation Response DTO
 * Complete response after calculating a split bill
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitBillCalculationResponse {

    private UUID calculationId;

    private BigDecimal totalAmount;

    private String currency;

    private SplitMethod splitMethod;

    private List<ParticipantSplit> participantSplits;

    private List<SplitAdjustment> adjustments;

    private SplitBillSummary summary;

    private String qrCode;

    private String shareableLink;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    private String status; // CALCULATED, IN_PROGRESS, COMPLETED, EXPIRED
}
