package com.waqiti.accounting.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Financial Period entity for accounting periods
 */
@Entity
@Table(name = "fiscal_period")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @NotNull
    @Column(name = "period_number", nullable = false)
    private Integer periodNumber;

    @NotNull
    @Size(max = 50)
    @Column(name = "period_name", nullable = false, length = 50)
    private String name;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @NotNull
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @NotNull
    @Size(max = 20)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";

    @NotNull
    @Column(name = "is_adjustment_period", nullable = false)
    @Builder.Default
    private Boolean isAdjustmentPeriod = false;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Size(max = 100)
    @Column(name = "closed_by", length = 100)
    private String closedBy;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Boolean isClosed() {
        return "CLOSED".equals(status);
    }
}