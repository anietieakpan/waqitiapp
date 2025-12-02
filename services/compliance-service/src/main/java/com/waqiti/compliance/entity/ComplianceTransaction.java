package com.waqiti.compliance.entity;

import lombok.*;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entity for persisting compliance screening results
 */
@Entity
@Table(name = "compliance_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"riskIndicators", "alerts"})
@EqualsAndHashCode(of = {"id", "transactionId"})
public class ComplianceTransaction {
    
    @Id
    @GeneratedValue
    @Type(type = "uuid-char")
    private UUID id;

    /**
     * Version field for optimistic locking
     * CRITICAL: Prevents lost updates during concurrent compliance screening
     * Multiple fraud/AML/sanctions checks may update the same record simultaneously
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;
    
    @Column(name = "customer_id", nullable = false)
    private String customerId;
    
    @Column(name = "account_id")
    private String accountId;
    
    // Transaction Details
    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;
    
    @Column(nullable = false, length = 3)
    private String currency;
    
    @Column(name = "transaction_type", nullable = false)
    private String transactionType;
    
    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;
    
    // Screening Results
    @Column(name = "risk_score")
    private Integer riskScore;
    
    @Column(name = "risk_level")
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;
    
    @Column(name = "screening_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ScreeningStatus screeningStatus = ScreeningStatus.PENDING;
    
    @Column(name = "screening_date")
    private LocalDateTime screeningDate;
    
    @Column(name = "rules_fired")
    private Integer rulesFired = 0;
    
    // Decisions
    @Enumerated(EnumType.STRING)
    private Decision decision;
    
    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;
    
    @Column(name = "auto_decision")
    private Boolean autoDecision = true;
    
    // Flags
    @Column(name = "is_blocked")
    private Boolean isBlocked = false;
    
    @Column(name = "requires_review")
    private Boolean requiresReview = false;
    
    @Column(name = "requires_sar")
    private Boolean requiresSAR = false;
    
    @Column(name = "is_ctr_required")
    private Boolean isCTRRequired = false;
    
    // Risk Indicators and Alerts (stored as JSON)
    @Type(type = "jsonb")
    @Column(name = "risk_indicators", columnDefinition = "jsonb")
    @Builder.Default
    private List<RiskIndicator> riskIndicators = new ArrayList<>();
    
    @Type(type = "jsonb")
    @Column(name = "alerts", columnDefinition = "jsonb")
    @Builder.Default
    private List<Alert> alerts = new ArrayList<>();
    
    // Review Information
    @Column(name = "reviewed_by")
    private String reviewedBy;
    
    @Column(name = "review_date")
    private LocalDateTime reviewDate;
    
    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;
    
    @Column(name = "review_decision")
    @Enumerated(EnumType.STRING)
    private ReviewDecision reviewDecision;
    
    // Metadata
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Enums
    public enum RiskLevel {
        LOW, MEDIUM, HIGH, VERY_HIGH, CRITICAL
    }
    
    public enum ScreeningStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, ERROR
    }
    
    public enum Decision {
        APPROVE, MONITOR, REVIEW, ESCALATE, BLOCK
    }
    
    public enum ReviewDecision {
        APPROVED, REJECTED, ESCALATED, PENDING_INFO, FALSE_POSITIVE
    }
    
    // Embedded classes for JSON storage
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskIndicator {
        private String code;
        private String description;
        private Integer score;
        private LocalDateTime timestamp;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Alert {
        private String type;
        private String severity;
        private String message;
        private LocalDateTime timestamp;
        private Map<String, Object> metadata;
    }
    
    // Helper methods
    public void addRiskIndicator(String code, String description, Integer score) {
        if (riskIndicators == null) {
            riskIndicators = new ArrayList<>();
        }
        riskIndicators.add(RiskIndicator.builder()
            .code(code)
            .description(description)
            .score(score)
            .timestamp(LocalDateTime.now())
            .build());
    }
    
    public void addAlert(String type, String severity, String message) {
        if (alerts == null) {
            alerts = new ArrayList<>();
        }
        alerts.add(Alert.builder()
            .type(type)
            .severity(severity)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build());
    }
    
    public Integer getTotalRiskScore() {
        if (riskIndicators == null || riskIndicators.isEmpty()) {
            return 0;
        }
        return riskIndicators.stream()
            .mapToInt(indicator -> indicator.getScore() != null ? indicator.getScore() : 0)
            .sum();
    }
}