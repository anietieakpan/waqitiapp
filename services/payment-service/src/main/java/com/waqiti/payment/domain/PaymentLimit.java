package com.waqiti.payment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_limits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLimit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String limitType;
    
    private BigDecimal dailyLimit;
    
    private BigDecimal weeklyLimit;
    
    private BigDecimal monthlyLimit;
    
    private BigDecimal perTransactionLimit;
    
    private BigDecimal dailySpent;
    
    private BigDecimal weeklySpent;
    
    private BigDecimal monthlySpent;
    
    private String status;
    
    private LocalDateTime expiresAt;
    
    private LocalDateTime lastResetAt;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = "ACTIVE";
        }
        if (dailySpent == null) {
            dailySpent = BigDecimal.ZERO;
        }
        if (weeklySpent == null) {
            weeklySpent = BigDecimal.ZERO;
        }
        if (monthlySpent == null) {
            monthlySpent = BigDecimal.ZERO;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}