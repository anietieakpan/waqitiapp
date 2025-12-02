package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.time.LocalDateTime;

@Data
@Builder
public class FraudScore {
    @DecimalMin(value = "0.0", message = "Fraud score cannot be negative")
    @DecimalMax(value = "1.0", message = "Fraud score cannot exceed 1.0")
    private double score;

    @DecimalMin(value = "0.0", message = "Confidence cannot be negative")
    @DecimalMax(value = "1.0", message = "Confidence cannot exceed 1.0")
    private double confidence;

    private FraudScoreBreakdown breakdown;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scoringTimestamp;
    private String modelVersion;
    private String error;
}