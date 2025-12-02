package com.waqiti.recurringpayment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "recurring_templates")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringTemplate {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "category")
    private String category;
    
    @Column(name = "default_recipient_id")
    private String defaultRecipientId;
    
    @Column(name = "default_amount", precision = 19, scale = 4)
    private BigDecimal defaultAmount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private RecurringFrequency frequency;
    
    @Column(name = "day_of_month")
    private Integer dayOfMonth;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week")
    private DayOfWeek dayOfWeek;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "monthly_pattern")
    private MonthlyPattern monthlyPattern;
    
    @Column(name = "reminder_enabled", nullable = false)
    @Builder.Default
    private boolean reminderEnabled = false;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "recurring_template_reminder_days", 
                    joinColumns = @JoinColumn(name = "template_id"))
    @Column(name = "days_before")
    @Builder.Default
    private Set<Integer> reminderDays = new HashSet<>();
    
    @Column(name = "auto_retry", nullable = false)
    @Builder.Default
    private boolean autoRetry = true;
    
    @Column(name = "max_retry_attempts", nullable = false)
    @Builder.Default
    private Integer maxRetryAttempts = 3;
    
    @Column(name = "payment_method")
    private String paymentMethod;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_action", nullable = false)
    @Builder.Default
    private FailureAction failureAction = FailureAction.CONTINUE;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "recurring_template_tags", 
                    joinColumns = @JoinColumn(name = "template_id"))
    @Column(name = "tag")
    @Builder.Default
    private Set<String> tags = new HashSet<>();
    
    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Integer usageCount = 0;
    
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
    
    @Column(name = "is_favorite", nullable = false)
    @Builder.Default
    private boolean favorite = false;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;
    
    @Column(name = "updated_at")
    @UpdateTimestamp
    private Instant updatedAt;
    
    @Version
    private Long version;
    
    // Business logic methods
    
    public void incrementUsage() {
        this.usageCount++;
        this.lastUsedAt = Instant.now();
    }
    
    public boolean isFrequentlyUsed() {
        return usageCount >= 5;
    }
}