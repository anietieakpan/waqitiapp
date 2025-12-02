package com.waqiti.frauddetection.dto.ml;

import lombok.*;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Batch Analysis Result DTO
 *
 * Results from batch processing multiple fraud detection requests.
 * Includes aggregate statistics and individual predictions.
 *
 * PRODUCTION-GRADE DTO
 * - Batch processing statistics
 * - Individual prediction results
 * - Performance metrics
 * - Error tracking
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchAnalysisResult {

    @NotNull
    private String batchId;

    @NotNull
    @Builder.Default
    private List<ModelPrediction> predictions = new ArrayList<>();

    /**
     * Batch statistics
     */
    private Integer totalRequests;
    private Integer successfulPredictions;
    private Integer failedPredictions;

    /**
     * Risk distribution
     */
    private Integer lowRiskCount;
    private Integer mediumRiskCount;
    private Integer highRiskCount;
    private Integer criticalRiskCount;

    /**
     * Performance metrics
     */
    private Long totalProcessingTimeMs;
    private Double avgProcessingTimeMs;
    private Long minProcessingTimeMs;
    private Long maxProcessingTimeMs;

    /**
     * Batch metadata
     */
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String modelVersion;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Error tracking
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public double getSuccessRate() {
        if (totalRequests == null || totalRequests == 0) {
            return 0.0;
        }
        return (double) successfulPredictions / totalRequests;
    }

    public long getDurationMs() {
        if (startedAt == null || completedAt == null) {
            return 0L;
        }
        return java.time.Duration.between(startedAt, completedAt).toMillis();
    }
}
