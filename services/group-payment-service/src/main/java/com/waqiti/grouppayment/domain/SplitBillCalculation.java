package com.waqiti.grouppayment.domain;

import com.waqiti.grouppayment.dto.ParticipantSplit;
import com.waqiti.grouppayment.dto.SplitAdjustment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a split bill calculation with all its details
 */
@Entity
@Table(name = "split_bill_calculations", indexes = {
    @Index(name = "idx_split_bill_organizer_id", columnList = "organizer_id"),
    @Index(name = "idx_split_bill_status", columnList = "status"),
    @Index(name = "idx_split_bill_created_at", columnList = "created_at"),
    @Index(name = "idx_split_bill_expires_at", columnList = "expires_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitBillCalculation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "organizer_id", nullable = false)
    private String organizerId;
    
    @Column(name = "group_name")
    private String groupName;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "split_method", nullable = false)
    private SplitMethod splitMethod;
    
    @Type(type = "jsonb")
    @Column(name = "participant_splits", columnDefinition = "jsonb")
    private List<ParticipantSplit> participantSplits;
    
    @Type(type = "jsonb")
    @Column(name = "adjustments", columnDefinition = "jsonb")
    private List<SplitAdjustment> adjustments;
    
    @Type(type = "jsonb")
    @Column(name = "original_request", columnDefinition = "jsonb")
    private String originalRequestJson;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;
    
    @Column(name = "qr_code_id")
    private String qrCodeId;
    
    @Column(name = "shareable_link")
    private String shareableLink;
    
    @Column(name = "short_code", unique = true, length = 8)
    private String shortCode;
    
    @Column(name = "payment_deadline")
    private LocalDateTime paymentDeadline;
    
    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;
    
    @Column(name = "completed_payments")
    private Integer completedPayments = 0;
    
    @Column(name = "total_payments_required")
    private Integer totalPaymentsRequired;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    
    @Column(name = "cancellation_reason")
    private String cancellationReason;
    
    @Version
    private Long version;
    
    public enum Status {
        CALCULATED,     // Calculation completed, awaiting payments
        IN_PROGRESS,    // Some payments received
        COMPLETED,      // All payments received
        CANCELLED,      // Cancelled by organizer
        EXPIRED,        // Expired without completion
        PARTIAL         // Partial completion, some participants opted out
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (shortCode == null) {
            shortCode = generateShortCode();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    private String generateShortCode() {
        // Generate 8-character alphanumeric code
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
    
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }
    
    public boolean canAcceptPayments() {
        return status == Status.CALCULATED || status == Status.IN_PROGRESS;
    }
    
    public BigDecimal getCompletionPercentage() {
        if (totalPaymentsRequired == null || totalPaymentsRequired == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(completedPayments)
            .divide(BigDecimal.valueOf(totalPaymentsRequired), 4, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }
}