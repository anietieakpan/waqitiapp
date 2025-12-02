package com.waqiti.analytics.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing predictive analytics and forecasting
 */
@Entity
@Table(name = "predictive_analytics", indexes = {
    @Index(name = "idx_predictive_user", columnList = "userId"),
    @Index(name = "idx_predictive_model", columnList = "modelType"),
    @Index(name = "idx_predictive_date", columnList = "predictionDate"),
    @Index(name = "idx_predictive_target", columnList = "targetDate")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PredictiveAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDateTime predictionDate;

    @Column(nullable = false)
    private LocalDateTime targetDate;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ModelType modelType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PredictionType predictionType;

    @Column(nullable = false)
    private String predictionCategory;

    // Prediction values
    @Column
    private BigDecimal predictedValue;

    @Column
    private BigDecimal lowerBound;

    @Column
    private BigDecimal upperBound;

    @Column(nullable = false)
    private Double confidenceLevel; // 0.0 to 100.0

    @Column
    private Double accuracy; // Historical accuracy of this model

    // Model metadata
    @Column
    private String modelVersion;

    @Column
    private Integer trainingDataPoints;

    @Column
    private LocalDateTime modelTrainedDate;

    @Column(columnDefinition = "TEXT")
    private String features;

    // Prediction details
    @Column(columnDefinition = "TEXT")
    private String predictionDetails;

    @Column(columnDefinition = "TEXT")
    private String assumptions;

    @Column(columnDefinition = "TEXT")
    private String recommendations;

    // Actual vs Predicted (for completed predictions)
    @Column
    private BigDecimal actualValue;

    @Column
    private BigDecimal predictionError;

    @Column
    private Double errorPercentage;

    @Column
    @Enumerated(EnumType.STRING)
    private PredictionStatus status;

    // Risk and opportunity analysis
    @Column
    private Double riskScore;

    @Column
    private Double opportunityScore;

    @Column(columnDefinition = "TEXT")
    private String riskFactors;

    @Column(columnDefinition = "TEXT")
    private String opportunities;

    // User feedback
    @Column
    private Boolean userFeedback;

    @Column
    private Integer userRating;

    @Column(columnDefinition = "TEXT")
    private String userComments;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum ModelType {
        LINEAR_REGRESSION,
        TIME_SERIES,
        NEURAL_NETWORK,
        RANDOM_FOREST,
        GRADIENT_BOOST,
        ARIMA,
        PROPHET,
        ENSEMBLE,
        CUSTOM
    }

    public enum PredictionType {
        SPENDING_FORECAST,
        CASHFLOW_PROJECTION,
        FRAUD_PROBABILITY,
        CHURN_RISK,
        TRANSACTION_VOLUME,
        BALANCE_FORECAST,
        INVESTMENT_RETURN,
        CREDIT_SCORE,
        DEFAULT_RISK,
        BEHAVIOR_PATTERN
    }

    public enum PredictionStatus {
        PENDING,      // Prediction made, waiting for target date
        VALIDATED,    // Target date reached, actual value recorded
        ACCURATE,     // Prediction was within acceptable error margin
        INACCURATE,   // Prediction exceeded error margin
        CANCELLED     // Prediction cancelled or invalidated
    }
}