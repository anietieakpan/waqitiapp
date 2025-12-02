package com.waqiti.expense.domain;

import com.waqiti.expense.domain.enums.ExpenseStatus;
import com.waqiti.expense.domain.enums.ExpenseType;
import com.waqiti.expense.domain.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Core Expense Entity
 * Represents individual expense transactions with comprehensive tracking capabilities
 */
@Entity
@Table(name = "expenses", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_expense_date", columnList = "expense_date"),
    @Index(name = "idx_category_id", columnList = "category_id"),
    @Index(name = "idx_budget_id", columnList = "budget_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_user_category_date", columnList = "user_id, category_id, expense_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
public class Expense {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "transaction_id")
    private String transactionId; // Link to original transaction

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ExpenseCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_id")
    private Budget budget;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_type", nullable = false)
    private ExpenseType expenseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExpenseStatus status;

    // Merchant and Location Information
    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "merchant_category")
    private String merchantCategory;

    @Column(name = "merchant_id")
    private String merchantId;

    @Column(name = "location_city")
    private String locationCity;

    @Column(name = "location_country")
    private String locationCountry;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    // Expense Details
    @Column(name = "is_recurring")
    private Boolean isRecurring = false;

    @Column(name = "recurring_frequency")
    private String recurringFrequency; // WEEKLY, MONTHLY, YEARLY

    @Column(name = "parent_expense_id")
    private String parentExpenseId; // For recurring expenses

    @Column(name = "is_reimbursable")
    private Boolean isReimbursable = false;

    @Column(name = "reimbursement_status")
    private String reimbursementStatus; // PENDING, APPROVED, PAID

    @Column(name = "is_business_expense")
    private Boolean isBusinessExpense = false;

    @Column(name = "tax_deductible")
    private Boolean taxDeductible = false;

    // Analytics and Classification
    @Column(name = "auto_categorized")
    private Boolean autoCategorized = false;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "needs_review")
    private Boolean needsReview = false;

    @Column(name = "review_reason")
    private String reviewReason;

    // Attachments and Notes
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "expense_attachments", joinColumns = @JoinColumn(name = "expense_id"))
    @Column(name = "attachment_url")
    private List<String> attachmentUrls = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "expense_tags", joinColumns = @JoinColumn(name = "expense_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    // Metadata
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "expense_metadata", joinColumns = @JoinColumn(name = "expense_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Version
    private Long version;

    // Business Methods

    /**
     * Mark expense as reviewed
     */
    public void markAsReviewed() {
        this.needsReview = false;
        this.processedAt = LocalDateTime.now();
        this.status = ExpenseStatus.PROCESSED;
    }

    /**
     * Flag expense for review
     */
    public void flagForReview(String reason) {
        this.needsReview = true;
        this.reviewReason = reason;
        this.status = ExpenseStatus.PENDING_REVIEW;
    }

    /**
     * Set category with confidence score
     */
    public void setCategoryWithConfidence(ExpenseCategory category, Double confidence) {
        this.category = category;
        this.confidenceScore = confidence;
        this.autoCategorized = confidence != null && confidence > 0.8;
        
        if (confidence != null && confidence < 0.6) {
            flagForReview("Low categorization confidence: " + String.format("%.2f", confidence));
        }
    }

    /**
     * Check if expense is over budget
     */
    public boolean isOverBudget() {
        if (budget == null) return false;
        return budget.isOverBudget(this.amount);
    }

    /**
     * Add attachment
     */
    public void addAttachment(String attachmentUrl) {
        if (attachmentUrls == null) {
            attachmentUrls = new ArrayList<>();
        }
        attachmentUrls.add(attachmentUrl);
    }

    /**
     * Add tag
     */
    public void addTag(String tag) {
        if (tags == null) {
            tags = new ArrayList<>();
        }
        if (!tags.contains(tag)) {
            tags.add(tag);
        }
    }

    /**
     * Check if expense matches criteria
     */
    public boolean matchesCriteria(ExpenseSearchCriteria criteria) {
        if (criteria.getMinAmount() != null && amount.compareTo(criteria.getMinAmount()) < 0) {
            return false;
        }
        if (criteria.getMaxAmount() != null && amount.compareTo(criteria.getMaxAmount()) > 0) {
            return false;
        }
        if (criteria.getStartDate() != null && expenseDate.isBefore(criteria.getStartDate())) {
            return false;
        }
        if (criteria.getEndDate() != null && expenseDate.isAfter(criteria.getEndDate())) {
            return false;
        }
        if (criteria.getCategoryIds() != null && !criteria.getCategoryIds().isEmpty()) {
            if (category == null || !criteria.getCategoryIds().contains(category.getId())) {
                return false;
            }
        }
        if (criteria.getMerchantName() != null && 
            (merchantName == null || !merchantName.toLowerCase().contains(criteria.getMerchantName().toLowerCase()))) {
            return false;
        }
        return true;
    }

    /**
     * Get expense for budget calculation
     */
    public BigDecimal getEffectiveAmount() {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    /**
     * Check if expense is recent (within last 7 days)
     */
    public boolean isRecent() {
        return expenseDate != null && 
               expenseDate.isAfter(LocalDate.now().minusDays(7));
    }

    /**
     * Get display description
     */
    public String getDisplayDescription() {
        if (description != null && !description.trim().isEmpty()) {
            return description;
        }
        if (merchantName != null && !merchantName.trim().isEmpty()) {
            return "Purchase at " + merchantName;
        }
        return "Expense transaction";
    }

    /**
     * Check if expense needs categorization
     */
    public boolean needsCategorization() {
        return category == null || (autoCategorized && confidenceScore != null && confidenceScore < 0.7);
    }

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = ExpenseStatus.PENDING;
        }
        if (currency == null) {
            currency = "USD";
        }
        if (expenseDate == null) {
            expenseDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (status == ExpenseStatus.PROCESSED && processedAt == null) {
            processedAt = LocalDateTime.now();
        }
    }

    /**
     * Search criteria inner class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpenseSearchCriteria {
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private LocalDate startDate;
        private LocalDate endDate;
        private List<String> categoryIds;
        private String merchantName;
        private ExpenseType expenseType;
        private PaymentMethod paymentMethod;
        private ExpenseStatus status;
        private Boolean isRecurring;
        private Boolean needsReview;
        private String searchText;
    }
}