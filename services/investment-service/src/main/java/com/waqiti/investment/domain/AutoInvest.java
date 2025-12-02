package com.waqiti.investment.domain;

import com.waqiti.investment.domain.enums.AutoInvestFrequency;
import com.waqiti.investment.domain.enums.AutoInvestStatus;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "auto_invest")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"allocations"})
@ToString(exclude = {"allocations"})
@EntityListeners(AuditingEntityListener.class)
public class AutoInvest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String investmentAccountId;

    @Column(nullable = false)
    private String planName;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AutoInvestFrequency frequency;

    @Column(nullable = false)
    private Integer dayOfMonth = 1;

    private Integer dayOfWeek; // 1-7 for weekly frequency

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AutoInvestStatus status = AutoInvestStatus.ACTIVE;

    @Column(nullable = false)
    private Boolean rebalanceEnabled = false;

    @Column(nullable = false)
    private BigDecimal rebalanceThreshold = new BigDecimal("5.0"); // 5% deviation

    @Column(nullable = false)
    private Boolean fractionalSharesEnabled = true;

    @Column(nullable = false)
    private Boolean notificationsEnabled = true;

    @Column(nullable = false)
    private BigDecimal totalInvested = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer executionCount = 0;

    @Column(nullable = false)
    private Integer failedCount = 0;

    private LocalDateTime lastExecutionDate;

    private LocalDateTime nextExecutionDate;

    private String lastExecutionStatus;

    private String lastExecutionError;

    @OneToMany(mappedBy = "autoInvest", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @BatchSize(size = 10)
    @Builder.Default
    private List<AutoInvestAllocation> allocations = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    public void addAllocation(AutoInvestAllocation allocation) {
        allocations.add(allocation);
        allocation.setAutoInvest(this);
    }

    public void removeAllocation(AutoInvestAllocation allocation) {
        allocations.remove(allocation);
        allocation.setAutoInvest(null);
    }

    public void validateAllocations() {
        BigDecimal totalPercent = allocations.stream()
                .map(AutoInvestAllocation::getPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalPercent.compareTo(new BigDecimal("100")) != 0) {
            throw new IllegalStateException("Total allocation must equal 100%");
        }
    }

    public void activate() {
        this.status = AutoInvestStatus.ACTIVE;
        calculateNextExecutionDate();
    }

    public void pause() {
        this.status = AutoInvestStatus.PAUSED;
    }

    public void cancel() {
        this.status = AutoInvestStatus.CANCELLED;
    }

    public void complete() {
        this.status = AutoInvestStatus.COMPLETED;
    }

    public void recordExecution(boolean success, String error) {
        this.lastExecutionDate = LocalDateTime.now();
        if (success) {
            this.executionCount++;
            this.lastExecutionStatus = "SUCCESS";
            this.lastExecutionError = null;
            this.totalInvested = this.totalInvested.add(this.amount);
        } else {
            this.failedCount++;
            this.lastExecutionStatus = "FAILED";
            this.lastExecutionError = error;
        }
        calculateNextExecutionDate();
    }

    public void calculateNextExecutionDate() {
        if (status != AutoInvestStatus.ACTIVE) {
            this.nextExecutionDate = null;
            return;
        }

        LocalDateTime baseDate = lastExecutionDate != null ? lastExecutionDate : LocalDateTime.now();
        LocalDateTime next = null;

        switch (frequency) {
            case DAILY:
                next = baseDate.plusDays(1);
                break;
            case WEEKLY:
                next = baseDate.plusWeeks(1);
                break;
            case BIWEEKLY:
                next = baseDate.plusWeeks(2);
                break;
            case MONTHLY:
                next = baseDate.plusMonths(1).withDayOfMonth(dayOfMonth);
                break;
            case QUARTERLY:
                next = baseDate.plusMonths(3).withDayOfMonth(dayOfMonth);
                break;
        }

        // Check if next execution is beyond end date
        if (endDate != null && next.toLocalDate().isAfter(endDate)) {
            complete();
            this.nextExecutionDate = null;
        } else {
            this.nextExecutionDate = next;
        }
    }

    public boolean isActive() {
        return status == AutoInvestStatus.ACTIVE;
    }

    public boolean isDue() {
        return isActive() && 
               nextExecutionDate != null && 
               nextExecutionDate.isBefore(LocalDateTime.now());
    }

    public boolean needsRebalancing(List<InvestmentHolding> currentHoldings) {
        if (!rebalanceEnabled || allocations.isEmpty()) {
            return false;
        }

        // Calculate current allocation percentages
        BigDecimal totalValue = currentHoldings.stream()
                .map(InvestmentHolding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalValue.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        // Check if any allocation deviates more than threshold
        for (AutoInvestAllocation allocation : allocations) {
            BigDecimal currentValue = currentHoldings.stream()
                    .filter(h -> h.getSymbol().equals(allocation.getSymbol()))
                    .map(InvestmentHolding::getMarketValue)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);

            BigDecimal currentPercent = currentValue.divide(totalValue, 4, RoundingMode.HALF_UP)
                                                   .multiply(new BigDecimal("100"));
            BigDecimal deviation = currentPercent.subtract(allocation.getPercentage()).abs();

            if (deviation.compareTo(rebalanceThreshold) > 0) {
                return true;
            }
        }

        return false;
    }

    public BigDecimal getAllocationAmount(String symbol) {
        return allocations.stream()
                .filter(a -> a.getSymbol().equals(symbol))
                .findFirst()
                .map(a -> amount.multiply(a.getPercentage()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO);
    }
}