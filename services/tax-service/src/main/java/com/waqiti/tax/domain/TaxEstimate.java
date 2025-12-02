package com.waqiti.tax.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tax_estimates", indexes = {
    @Index(name = "idx_tax_estimates_return_id", columnList = "tax_return_id"),
    @Index(name = "idx_tax_estimates_calculated_at", columnList = "calculated_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxEstimate {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_return_id", nullable = false)
    private TaxReturn taxReturn;
    
    @Column(name = "total_income", precision = 19, scale = 2, nullable = false)
    private BigDecimal totalIncome;
    
    @Column(name = "adjusted_gross_income", precision = 19, scale = 2, nullable = false)
    private BigDecimal adjustedGrossIncome;
    
    @Column(name = "deductions", precision = 19, scale = 2, nullable = false)
    private BigDecimal deductions;
    
    @Column(name = "taxable_income", precision = 19, scale = 2, nullable = false)
    private BigDecimal taxableIncome;
    
    @Column(name = "federal_tax", precision = 19, scale = 2, nullable = false)
    private BigDecimal federalTax;
    
    @Column(name = "state_tax", precision = 19, scale = 2, nullable = false)
    private BigDecimal stateTax;
    
    @Column(name = "tax_credits", precision = 19, scale = 2, nullable = false)
    private BigDecimal taxCredits;
    
    @Column(name = "total_tax", precision = 19, scale = 2, nullable = false)
    private BigDecimal totalTax;
    
    @Column(name = "total_withheld", precision = 19, scale = 2, nullable = false)
    private BigDecimal totalWithheld;
    
    @Column(name = "estimated_refund", precision = 19, scale = 2, nullable = false)
    private BigDecimal estimatedRefund;
    
    @Column(name = "amount_owed", precision = 19, scale = 2, nullable = false)
    private BigDecimal amountOwed;
    
    @Column(name = "effective_tax_rate", precision = 5, scale = 4)
    private BigDecimal effectiveTaxRate;
    
    @Column(name = "marginal_tax_rate", precision = 5, scale = 4)
    private BigDecimal marginalTaxRate;
    
    @Column(name = "potential_savings", precision = 19, scale = 2)
    private BigDecimal potentialSavings;
    
    @Column(name = "optimization_suggestions", columnDefinition = "TEXT")
    private String optimizationSuggestions; // JSON array of suggestions
    
    @Column(name = "calculation_method")
    private String calculationMethod;
    
    @Column(name = "confidence_score", precision = 3, scale = 2)
    private BigDecimal confidenceScore; // 0.00 to 1.00
    
    @Column(name = "is_current", nullable = false)
    @Builder.Default
    private Boolean isCurrent = true;
    
    @CreationTimestamp
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
    
    @Column(name = "valid_until")
    private LocalDateTime validUntil;
}