package com.waqiti.analytics.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Anomaly Detection DTO
 *
 * Detected anomaly or unusual transaction pattern.
 *
 * @author Waqiti Analytics Team
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyDetection {

    @NotNull
    private String anomalyType; // UNUSUAL_AMOUNT, UNUSUAL_MERCHANT, UNUSUAL_LOCATION, UNUSUAL_TIME

    @Min(0) @Max(100)
    private Integer severityScore; // 0-100

    private String description;

    @NotNull
    private BigDecimal transactionAmount;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime detectedAt;

    private BigDecimal expectedAmount;
    private BigDecimal deviation;

    private String recommendation; // REVIEW, BLOCK, ALLOW_WITH_MFA
    private Boolean requiresUserAction;
}
