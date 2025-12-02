package com.waqiti.reconciliation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_variances", indexes = {
    @Index(name = "idx_variances_type", columnList = "variance_type"),
    @Index(name = "idx_variances_amount", columnList = "amount"),
    @Index(name = "idx_variances_break_id", columnList = "reconciliation_break_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationVariance {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "variance_id")
    @Type(type = "uuid-char")
    private UUID varianceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciliation_break_id")
    private ReconciliationBreak reconciliationBreak;

    @Column(name = "variance_type", nullable = false, length = 50)
    @Size(max = 50)
    @NotNull
    private String varianceType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    @DecimalMin(value = "0.0000", message = "Variance amount must be non-negative")
    @NotNull
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    @Size(min = 3, max = 3, message = "Currency code must be 3 characters")
    private String currency;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "expected_value", columnDefinition = "TEXT")
    private String expectedValue;

    @Column(name = "actual_value", columnDefinition = "TEXT")
    private String actualValue;

    @Column(name = "field_name", length = 100)
    @Size(max = 100)
    private String fieldName;

    @Column(name = "system_source", length = 100)
    @Size(max = 100)
    private String systemSource;

    @Column(name = "system_target", length = 100)
    @Size(max = 100)
    private String systemTarget;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20)
    private VarianceSeverity severity;

    @Column(name = "tolerance_exceeded", nullable = false)
    @Builder.Default
    private Boolean toleranceExceeded = false;

    @Column(name = "auto_correctable", nullable = false)
    @Builder.Default
    private Boolean autoCorrectable = false;

    @Column(name = "correction_applied", nullable = false)
    @Builder.Default
    private Boolean correctionApplied = false;

    @Column(name = "correction_details", columnDefinition = "TEXT")
    private String correctionDetails;

    public enum VarianceSeverity {
        LOW("Low Impact"),
        MEDIUM("Medium Impact"),
        HIGH("High Impact"),
        CRITICAL("Critical Impact");

        private final String description;

        VarianceSeverity(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public boolean isSignificant() {
        return VarianceSeverity.HIGH.equals(severity) || VarianceSeverity.CRITICAL.equals(severity);
    }

    public boolean canBeAutoCorrected() {
        return autoCorrectable && !correctionApplied;
    }

    public void markCorrectionApplied(String details) {
        this.correctionApplied = true;
        this.correctionDetails = details;
    }

    public boolean isToleranceExceeded() {
        return toleranceExceeded != null && toleranceExceeded;
    }

    public String getVarianceDescription() {
        if (description != null && !description.isEmpty()) {
            return description;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Variance in ").append(varianceType);
        if (fieldName != null && !fieldName.isEmpty()) {
            sb.append(" (").append(fieldName).append(")");
        }
        sb.append(": ");
        
        if (expectedValue != null && actualValue != null) {
            sb.append("Expected '").append(expectedValue)
              .append("', but found '").append(actualValue).append("'");
        } else if (amount != null) {
            sb.append("Amount variance of ").append(amount);
            if (currency != null) {
                sb.append(" ").append(currency);
            }
        }
        
        return sb.toString();
    }
}