package com.waqiti.investment.domain;

import lombok.*;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "auto_invest_allocations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"autoInvest"})
@ToString(exclude = {"autoInvest"})
public class AutoInvestAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auto_invest_id", nullable = false)
    private AutoInvest autoInvest;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String instrumentType; // STOCK, ETF, CRYPTO

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal percentage;

    @Column(nullable = false)
    private Boolean enabled = true;

    private BigDecimal minInvestment;

    private BigDecimal maxInvestment;

    private String notes;

    @Version
    private Long version;

    public BigDecimal calculateAmount(BigDecimal totalAmount) {
        BigDecimal amount = totalAmount.multiply(percentage).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        
        if (minInvestment != null && amount.compareTo(minInvestment) < 0) {
            return minInvestment;
        }
        
        if (maxInvestment != null && amount.compareTo(maxInvestment) > 0) {
            return maxInvestment;
        }
        
        return amount;
    }

    public boolean isValid() {
        return enabled && 
               percentage != null && 
               percentage.compareTo(BigDecimal.ZERO) > 0 &&
               percentage.compareTo(new BigDecimal("100")) <= 0;
    }
}