package com.waqiti.payment.dto;

import com.waqiti.payment.domain.SplitPaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lightweight DTO for split payment summaries to avoid N+1 queries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitPaymentSummary {
    private UUID id;
    private String title;
    private BigDecimal totalAmount;
    private SplitPaymentStatus status;
    private LocalDateTime createdAt;
    private Long participantCount;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    
    // Constructor for JPQL projection
    public SplitPaymentSummary(UUID id, String title, BigDecimal totalAmount, 
                              SplitPaymentStatus status, LocalDateTime createdAt,
                              Long participantCount, BigDecimal paidAmount) {
        this.id = id;
        this.title = title;
        this.totalAmount = totalAmount;
        this.status = status;
        this.createdAt = createdAt;
        this.participantCount = participantCount;
        this.paidAmount = paidAmount != null ? paidAmount : BigDecimal.ZERO;
        this.remainingAmount = totalAmount.subtract(this.paidAmount);
    }
    
    public double getCompletionPercentage() {
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            return 100.0;
        }
        return paidAmount.divide(totalAmount, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal(100))
                        .doubleValue();
    }
}