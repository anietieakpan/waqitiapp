package com.waqiti.payment.core.model;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Split payment data model for distributing payments across multiple recipients
 * Production-ready implementation for complex payment splitting scenarios
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"splitInstructions"})
public class SplitPaymentData {
    
    @NotNull
    private UUID splitId;
    
    @NotNull
    private SplitType splitType;
    
    @NotNull
    @Size(min = 2, max = 100)
    private List<SplitInstruction> splitInstructions;
    
    @NotNull
    private BigDecimal totalAmount;
    
    private String currency;
    
    @Builder.Default
    private SplitCalculationMethod calculationMethod = SplitCalculationMethod.PERCENTAGE;
    
    @Builder.Default
    private boolean enforceExactSplit = true;
    
    private BigDecimal platformFee;
    
    private SplitSettlementStrategy settlementStrategy;
    
    public enum SplitType {
        MARKETPLACE,      // Marketplace split between seller and platform
        GROUP_PAYMENT,    // Split among group members
        BILL_SPLIT,      // Restaurant/utility bill splitting
        COMMISSION,      // Commission-based splitting
        REVENUE_SHARE,   // Revenue sharing agreement
        ESCROW_RELEASE,  // Escrow release to multiple parties
        AFFILIATE,       // Affiliate commission splits
        CUSTOM          // Custom split logic
    }
    
    public enum SplitCalculationMethod {
        PERCENTAGE,      // Split by percentage
        FIXED_AMOUNT,   // Fixed amount per recipient
        PROPORTIONAL,   // Proportional to contribution
        EQUAL,          // Equal split among all
        CUSTOM          // Custom calculation logic
    }
    
    public enum SplitSettlementStrategy {
        IMMEDIATE,       // Settle immediately
        BATCH,          // Batch settlement
        SCHEDULED,      // Scheduled settlement
        ON_DEMAND,      // On-demand by recipient
        MILESTONE_BASED // Based on milestones
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitInstruction {
        @NotNull
        private UUID recipientId;
        
        @NotNull
        private String recipientAccountId;
        
        private BigDecimal amount;
        
        @Min(0)
        @Max(100)
        private BigDecimal percentage;
        
        private String description;
        
        @Builder.Default
        private int priority = 0;
        
        private boolean isPlatformFee;
        
        private String metadata;
        
        @Builder.Default
        private SplitStatus status = SplitStatus.PENDING;
    }
    
    public enum SplitStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    // Business logic methods
    public boolean validateSplit() {
        if (calculationMethod == SplitCalculationMethod.PERCENTAGE) {
            BigDecimal totalPercentage = splitInstructions.stream()
                .map(SplitInstruction::getPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return totalPercentage.compareTo(new BigDecimal("100")) == 0;
        } else if (calculationMethod == SplitCalculationMethod.FIXED_AMOUNT) {
            BigDecimal totalFixed = splitInstructions.stream()
                .map(SplitInstruction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return totalFixed.compareTo(totalAmount) <= 0;
        }
        return true;
    }
    
    public BigDecimal getRemainingAmount() {
        BigDecimal allocated = splitInstructions.stream()
            .map(si -> si.getAmount() != null ? si.getAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalAmount.subtract(allocated);
    }
}