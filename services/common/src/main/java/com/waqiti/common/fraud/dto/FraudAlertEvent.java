package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.waqiti.common.fraud.model.AlertLevel;
import com.waqiti.common.fraud.model.FraudRiskLevel;
import java.time.LocalDateTime;

/**
 * Fraud alert event DTO for event-driven notifications
 * Thread-Safe: Immutable when using builder pattern
 */
@Data
@Builder(toBuilder = true)
public class FraudAlertEvent {
    private String alertId;
    private AlertLevel level;
    private String transactionId;
    private String userId;
    private double fraudScore;
    private double fraudProbability; // Probability score (0.0-1.0)
    private FraudRiskLevel riskLevel;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
}