package com.waqiti.ledger.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Accounting Period Entity
 * 
 * Represents accounting periods for financial reporting and period-end
 * closing procedures. Ensures proper temporal organization of financial data.
 */
@Entity
@Table(name = "accounting_periods", indexes = {
    @Index(name = "idx_period_dates", columnList = "startDate, endDate"),
    @Index(name = "idx_period_status", columnList = "status"),
    @Index(name = "idx_period_type", columnList = "periodType"),
    @Index(name = "idx_period_year", columnList = "fiscalYear")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "period_id")
    private UUID periodId;

    @Column(name = "period_name", nullable = false, length = 100)
    private String periodName;

    @Column(name = "period_code", nullable = false, unique = true, length = 20)
    private String periodCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false)
    private PeriodType periodType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(name = "fiscal_quarter")
    private Integer fiscalQuarter;

    @Column(name = "fiscal_month")
    private Integer fiscalMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private PeriodStatus status = PeriodStatus.OPEN;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by", length = 100)
    private String closedBy;

    @Column(name = "reopened_at")
    private LocalDateTime reopenedAt;

    @Column(name = "reopened_by", length = 100)
    private String reopenedBy;

    @Column(name = "reopen_reason", length = 500)
    private String reopenReason;

    @Column(name = "lock_date")
    private LocalDate lockDate;

    @Column(name = "soft_close_date")
    private LocalDate softCloseDate;

    @Column(name = "hard_close_date")
    private LocalDate hardCloseDate;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_adjustment_period")
    @Builder.Default
    private Boolean isAdjustmentPeriod = false;

    @Column(name = "parent_period_id")
    private UUID parentPeriodId;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Period Types
     */
    public enum PeriodType {
        DAILY,
        WEEKLY,
        MONTHLY,
        QUARTERLY,
        SEMI_ANNUALLY,
        ANNUALLY,
        ADJUSTMENT,
        SPECIAL
    }

    /**
     * Period Status
     */
    public enum PeriodStatus {
        FUTURE,         // Period hasn't started yet
        OPEN,           // Period is open for transactions
        SOFT_CLOSED,    // Period is soft closed (limited adjustments allowed)
        HARD_CLOSED,    // Period is hard closed (no adjustments allowed)
        LOCKED,         // Period is permanently locked
        REOPENED        // Period was reopened after closing
    }

    /**
     * Business logic methods
     */
    
    public boolean isOpen() {
        return status == PeriodStatus.OPEN || status == PeriodStatus.REOPENED;
    }

    public boolean isClosed() {
        return status == PeriodStatus.SOFT_CLOSED || status == PeriodStatus.HARD_CLOSED || 
               status == PeriodStatus.LOCKED;
    }

    public boolean allowsTransactions() {
        return isOpen() || (status == PeriodStatus.SOFT_CLOSED && isAdjustmentAllowed());
    }

    public boolean allowsAdjustments() {
        return status == PeriodStatus.OPEN || status == PeriodStatus.SOFT_CLOSED || 
               status == PeriodStatus.REOPENED;
    }

    public boolean canBeReopened() {
        return (status == PeriodStatus.SOFT_CLOSED || status == PeriodStatus.HARD_CLOSED) && 
               status != PeriodStatus.LOCKED;
    }

    public boolean canBeClosed() {
        return status == PeriodStatus.OPEN || status == PeriodStatus.REOPENED;
    }

    public boolean isCurrentPeriod() {
        LocalDate today = LocalDate.now();
        return !today.isBefore(startDate) && !today.isAfter(endDate);
    }

    public boolean isFuturePeriod() {
        return LocalDate.now().isBefore(startDate);
    }

    public boolean isPastPeriod() {
        return LocalDate.now().isAfter(endDate);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public boolean overlaps(AccountingPeriod other) {
        return !endDate.isBefore(other.startDate) && !startDate.isAfter(other.endDate);
    }

    public int getDaysInPeriod() {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    public void softClose(String closedBy) {
        this.status = PeriodStatus.SOFT_CLOSED;
        this.softCloseDate = LocalDate.now();
        this.closedAt = LocalDateTime.now();
        this.closedBy = closedBy;
    }

    public void hardClose(String closedBy) {
        this.status = PeriodStatus.HARD_CLOSED;
        this.hardCloseDate = LocalDate.now();
        this.closedAt = LocalDateTime.now();
        this.closedBy = closedBy;
    }

    public void lock() {
        this.status = PeriodStatus.LOCKED;
        this.lockDate = LocalDate.now();
    }

    public void reopen(String reopenedBy, String reason) {
        this.status = PeriodStatus.REOPENED;
        this.reopenedAt = LocalDateTime.now();
        this.reopenedBy = reopenedBy;
        this.reopenReason = reason;
    }

    private boolean isAdjustmentAllowed() {
        return softCloseDate != null && 
               java.time.temporal.ChronoUnit.DAYS.between(softCloseDate, LocalDate.now()) <= 30;
    }
}