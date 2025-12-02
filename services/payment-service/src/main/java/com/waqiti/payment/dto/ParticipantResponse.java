package com.waqiti.payment.dto;

import com.waqiti.payment.domain.ParticipantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantResponse {
    private UUID userId;
    private BigDecimal amount;
    private ParticipantStatus status;
    private LocalDateTime paidAt;
    private String userName; // To be populated by service if needed
    private String userEmail; // To be populated by service if needed
}