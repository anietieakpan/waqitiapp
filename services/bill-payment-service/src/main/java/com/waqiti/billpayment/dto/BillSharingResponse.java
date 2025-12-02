package com.waqiti.billpayment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for bill sharing request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillSharingResponse {

    private UUID shareRequestId;

    private UUID billId;

    private String billerName;

    private String accountNumber;

    private BigDecimal totalAmount;

    private String currency;

    private String splitType;

    private String status; // PENDING, PARTIALLY_PAID, FULLY_PAID, CANCELLED

    private String createdByUserId;

    private Integer totalParticipants;

    private Integer participantsPaid;

    private BigDecimal totalCollected;

    private BigDecimal remainingAmount;

    private List<BillSharingParticipant> participants;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    private String notes;
}
