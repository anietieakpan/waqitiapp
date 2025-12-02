package com.waqiti.frauddetection.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction Block Request DTO
 *
 * Used to request transaction blocking due to fraud detection.
 * Contains all necessary information for fraud prevention and audit trail.
 *
 * @author Waqiti Security Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionBlockRequest {

    @NotBlank(message = "Transaction ID is required")
    private String transactionId;

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotBlank(message = "Fraud type is required")
    private String fraudType;

    @NotNull(message = "Risk score is required")
    private Integer riskScore;

    @NotBlank(message = "Block reason is required")
    private String blockReason;

    private String detectionMethod;

    private String sourceIp;

    private String deviceFingerprint;

    private String geolocation;

    private LocalDateTime detectedAt;

    private String fraudAnalystId;

    private Boolean automaticBlock;

    /**
     * Severity levels: LOW, MEDIUM, HIGH, CRITICAL
     */
    private String severity;

    /**
     * Additional context for fraud investigation
     */
    private String investigationNotes;
}
