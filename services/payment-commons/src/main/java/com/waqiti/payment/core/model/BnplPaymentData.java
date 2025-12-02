package com.waqiti.payment.core.model;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Buy Now Pay Later (BNPL) payment data model
 * Industrial-grade implementation for BNPL payment processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"creditCheckData", "metadata"})
public class BnplPaymentData {
    
    @NotNull
    private UUID bnplId;
    
    @NotNull
    private BnplProvider provider;
    
    @NotNull
    private BnplPlan plan;
    
    @NotNull
    private BigDecimal purchaseAmount;
    
    @NotNull
    private String currency;
    
    @NotNull
    @Size(min = 1)
    private List<InstallmentSchedule> installments;
    
    private BigDecimal downPayment;
    
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private BigDecimal interestRate;
    
    private BigDecimal totalInterest;
    
    private BigDecimal totalAmount;
    
    @NotNull
    private UUID customerId;
    
    private CreditCheckData creditCheckData;
    
    @NotNull
    private UUID merchantId;
    
    private String merchantName;
    
    private String orderReference;
    
    @Builder.Default
    private BnplStatus status = BnplStatus.PENDING;
    
    private LocalDateTime approvedAt;
    
    private LocalDateTime activatedAt;
    
    private BigDecimal paidAmount;
    
    @Min(0)
    @Builder.Default
    private int completedInstallments = 0;
    
    @Min(0)
    @Builder.Default
    private int missedPayments = 0;
    
    private BigDecimal lateFees;
    
    @Builder.Default
    private boolean autoPayEnabled = false;
    
    private String autoPayAccountId;
    
    private Map<String, Object> metadata;
    
    public enum BnplProvider {
        KLARNA,
        AFTERPAY,
        AFFIRM,
        SEZZLE,
        QUADPAY,
        SPLITIT,
        PAYL8R,
        CLEARPAY,
        LAYBUY,
        INTERNAL,
        CUSTOM
    }
    
    public enum BnplPlan {
        PAY_IN_2,        // 2 installments
        PAY_IN_3,        // 3 installments
        PAY_IN_4,        // 4 installments (most common)
        PAY_IN_6,        // 6 installments
        PAY_IN_12,       // 12 monthly installments
        PAY_IN_24,       // 24 monthly installments
        FLEXIBLE,        // Flexible payment plan
        CUSTOM          // Custom plan
    }
    
    public enum BnplStatus {
        PENDING,         // Awaiting approval
        APPROVED,        // Credit approved
        DECLINED,        // Credit declined
        ACTIVE,          // Plan active
        CURRENT,         // Payments current
        OVERDUE,         // Payments overdue
        DEFAULTED,       // In default
        COMPLETED,       // Fully paid
        CANCELLED,       // Plan cancelled
        REFUNDED,        // Purchase refunded
        SUSPENDED        // Account suspended
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstallmentSchedule {
        @NotNull
        private Integer installmentNumber;
        
        @NotNull
        private BigDecimal amount;
        
        @NotNull
        private LocalDate dueDate;
        
        private BigDecimal principal;
        
        private BigDecimal interest;
        
        @Builder.Default
        private InstallmentStatus status = InstallmentStatus.PENDING;
        
        private LocalDateTime paidAt;
        
        private String paymentReference;
        
        private BigDecimal paidAmount;
        
        private BigDecimal lateFee;
        
        @Min(0)
        @Builder.Default
        private int retryAttempts = 0;
    }
    
    public enum InstallmentStatus {
        PENDING,
        DUE,
        PROCESSING,
        PAID,
        PARTIALLY_PAID,
        OVERDUE,
        FAILED,
        WAIVED,
        REFUNDED
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreditCheckData {
        private String creditScore;
        private CreditDecision decision;
        private BigDecimal approvedAmount;
        private BigDecimal creditLimit;
        private String decisionReason;
        private LocalDateTime checkPerformedAt;
        private Map<String, Object> additionalData;
    }
    
    public enum CreditDecision {
        APPROVED,
        DECLINED,
        MANUAL_REVIEW,
        PARTIAL_APPROVAL,
        CONDITIONAL_APPROVAL
    }
    
    // Business logic methods
    public BigDecimal getRemainingBalance() {
        BigDecimal paid = paidAmount != null ? paidAmount : BigDecimal.ZERO;
        BigDecimal total = totalAmount != null ? totalAmount : purchaseAmount;
        return total.subtract(paid);
    }
    
    public InstallmentSchedule getNextInstallment() {
        return installments.stream()
            .filter(i -> i.getStatus() == InstallmentStatus.PENDING || 
                        i.getStatus() == InstallmentStatus.DUE)
            .min(Comparator.comparing(InstallmentSchedule::getDueDate))
            .orElse(null);
    }
    
    public boolean isOverdue() {
        return installments.stream()
            .anyMatch(i -> i.getStatus() == InstallmentStatus.OVERDUE);
    }
    
    public long getOverdueInstallments() {
        return installments.stream()
            .filter(i -> i.getStatus() == InstallmentStatus.OVERDUE)
            .count();
    }
    
    public double getCompletionPercentage() {
        if (installments.isEmpty()) {
            return 0.0;
        }
        long paid = installments.stream()
            .filter(i -> i.getStatus() == InstallmentStatus.PAID)
            .count();
        return (paid * 100.0) / installments.size();
    }
    
    public BigDecimal getTotalLateFees() {
        return installments.stream()
            .map(i -> i.getLateFee() != null ? i.getLateFee() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .add(lateFees != null ? lateFees : BigDecimal.ZERO);
    }
    
    public boolean canRefund() {
        return status == BnplStatus.ACTIVE || 
               status == BnplStatus.CURRENT || 
               status == BnplStatus.COMPLETED;
    }
}