package com.waqiti.card.dto;

import com.waqiti.card.enums.DisputeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CardDisputeResponse DTO - Dispute details response
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardDisputeResponse {
    private UUID id;
    private String disputeId;
    private String caseNumber;
    private UUID transactionId;
    private UUID cardId;
    private UUID userId;
    private DisputeStatus disputeStatus;
    private String disputeCategory;
    private String disputeReason;
    private BigDecimal disputedAmount;
    private String currencyCode;
    private LocalDateTime filedDate;
    private LocalDateTime merchantResponseDeadline;
    private LocalDateTime resolutionDate;
    private String resolutionOutcome;
    private String resolvedInFavorOf;
    private Boolean provisionalCreditIssued;
    private BigDecimal provisionalCreditAmount;
    private Boolean chargebackIssued;
    private Boolean escalatedToArbitration;
    private String assignedTo;
    private LocalDateTime createdAt;
}
