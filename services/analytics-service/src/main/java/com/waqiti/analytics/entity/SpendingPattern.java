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
 * Entity representing user spending patterns
 */
@Entity
@Table(name = "spending_patterns", indexes = {
    @Index(name = "idx_spending_pattern_user", columnList = "userId"),
    @Index(name = "idx_spending_pattern_category", columnList = "category"),
    @Index(name = "idx_spending_pattern_date", columnList = "patternDate")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SpendingPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false)
    private BigDecimal averageAmount;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private Integer transactionCount;

    @Column(nullable = false)
    private LocalDateTime patternDate;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private PeriodType periodType;

    @Column
    private BigDecimal percentageOfTotal;

    @Column
    private BigDecimal monthOverMonthChange;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private TrendDirection trend;

    @Column(columnDefinition = "TEXT")
    private String insights;

    @Column
    private BigDecimal predictedNextPeriod;

    @Column
    private Double confidenceScore;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum PeriodType {
        DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY
    }

    public enum TrendDirection {
        INCREASING, DECREASING, STABLE, VOLATILE
    }
}