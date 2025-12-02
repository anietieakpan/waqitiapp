package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Event DTO for fraud alerts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlertEvent {
    private UUID alertId;
    private String type;
    private String severity;
    private UUID userId;
    private UUID transactionId;
    private BigDecimal amount;
    private BigDecimal riskScore;
    private List<String> fraudIndicators;
    private LocalDateTime timestamp;
}