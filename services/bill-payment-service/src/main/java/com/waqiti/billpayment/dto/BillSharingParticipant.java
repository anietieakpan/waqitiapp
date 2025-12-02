package com.waqiti.billpayment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for bill sharing participant with status information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillSharingParticipant {

    private UUID participantId;

    private String participantUserId;

    private String participantEmail;

    private String participantName;

    private BigDecimal shareAmount;

    private BigDecimal sharePercentage;

    private BigDecimal paidAmount;

    private String status; // PENDING, ACCEPTED, DECLINED, PAID

    private LocalDateTime acceptedAt;

    private LocalDateTime paidAt;

    private String paymentMethod;

    private UUID paymentId;
}
