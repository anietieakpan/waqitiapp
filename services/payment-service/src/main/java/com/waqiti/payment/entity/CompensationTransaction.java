package com.waqiti.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "compensation_transactions",
    indexes = {
        @Index(name = "idx_compensation_payment_id", columnList = "payment_id"),
        @Index(name = "idx_compensation_status", columnList = "status"),
        @Index(name = "idx_compensation_initiated_at", columnList = "initiated_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompensationTransaction {
    
    @Id
    @Column(name = "id", nullable = false)
    private String id;
    
    @Column(name = "payment_id", nullable = false)
    private String paymentId;
    
    @Column(name = "original_amount", precision = 19, scale = 2)
    private BigDecimal originalAmount;
    
    @Column(name = "reason", length = 50)
    private String reason;
    
    @Column(name = "status", nullable = false, length = 50)
    private String status;
    
    @Column(name = "initiated_by", length = 255)
    private String initiatedBy;
    
    @Column(name = "initiated_at")
    private LocalDateTime initiatedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "result", columnDefinition = "TEXT")
    private String result;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "retry_count")
    private Integer retryCount;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (retryCount == null) {
            retryCount = 0;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public void setResult(Object result) {
        this.result = result != null ? result.toString() : null;
    }
}