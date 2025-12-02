package com.waqiti.compliance.aml.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AML Alert for suspicious activity
 */
@Data
@Builder
public class AMLAlert {
    private String alertId;
    private String userId;
    private AMLAlertType alertType;
    private AMLRiskLevel riskLevel;
    private BigDecimal totalAmount;
    private String currency;
    private int transactionCount;
    private LocalDateTime detectedAt;
    private String description;
    private List<String> transactionIds;
    private boolean sarRequired;
}
