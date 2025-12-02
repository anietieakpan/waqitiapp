package com.waqiti.compliance.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "manual_flags")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualFlag {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    
    @Column(name = "transaction_id")
    private UUID transactionId;
    
    @Column(name = "flag_type", nullable = false, length = 100)
    private String flagType;
    
    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;
    
    @Column(name = "severity", length = 20)
    @Enumerated(EnumType.STRING)
    private FlagSeverity severity;
    
    @Column(name = "associated_amount", precision = 19, scale = 2)
    private BigDecimal associatedAmount;
    
    @Column(name = "currency", length = 3)
    private String currency;
    
    @Column(name = "analyst_id", nullable = false)
    private String analystId;
    
    @Column(name = "analyst_name")
    private String analystName;
    
    @Column(name = "department", length = 100)
    private String department;
    
    @Column(name = "status", length = 50)
    @Enumerated(EnumType.STRING)
    private FlagStatus status;
    
    @Column(name = "resolution", columnDefinition = "TEXT")
    private String resolution;
    
    @Column(name = "resolved_by")
    private String resolvedBy;
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    
    @Column(name = "escalated")
    private Boolean escalated;
    
    @Column(name = "escalated_to")
    private String escalatedTo;
    
    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;
    
    @Column(name = "sar_filed")
    private Boolean sarFiled;
    
    @Column(name = "sar_reference")
    private String sarReference;
    
    @CreationTimestamp
    @Column(name = "flagged_at", nullable = false, updatable = false)
    private LocalDateTime flaggedAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    public enum FlagSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public enum FlagStatus {
        ACTIVE, UNDER_REVIEW, RESOLVED, EXPIRED, ESCALATED
    }
}