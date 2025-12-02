package com.waqiti.business.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "business_expenses", indexes = {
        @Index(name = "idx_business_expenses_account", columnList = "business_account_id"),
        @Index(name = "idx_business_expenses_submitter", columnList = "submitted_by"),
        @Index(name = "idx_business_expenses_status", columnList = "status"),
        @Index(name = "idx_business_expenses_category", columnList = "category"),
        @Index(name = "idx_business_expenses_date", columnList = "expense_date"),
        @Index(name = "idx_business_expenses_amount", columnList = "amount")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessExpense {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "business_account_id", nullable = false)
    private UUID businessAccountId;
    
    @Column(name = "submitted_by", nullable = false)
    private UUID submittedBy;
    
    @Column(name = "approved_by")
    private UUID approvedBy;
    
    @Column(name = "expense_number", unique = true, length = 50)
    private String expenseNumber;
    
    @Column(name = "category", nullable = false, length = 50)
    private String category;
    
    @Column(name = "subcategory", length = 50)
    private String subcategory;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";
    
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "merchant", length = 200)
    private String merchant;
    
    @Column(name = "expense_date", nullable = false)
    private LocalDateTime expenseDate;
    
    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;
    
    @Type(type = "jsonb")
    @Column(name = "receipt_data", columnDefinition = "jsonb")
    private Map<String, Object> receiptData; // OCR extracted data
    
    @Column(name = "location", length = 200)
    private String location;
    
    @Column(name = "project", length = 100)
    private String project;
    
    @Column(name = "department", length = 100)
    private String department;
    
    @Type(type = "jsonb")
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags;
    
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Status status;
    
    @Column(name = "is_reimbursable")
    private Boolean isReimbursable = true;
    
    @Column(name = "reimbursement_amount", precision = 19, scale = 2)
    private BigDecimal reimbursementAmount;
    
    @Column(name = "reimbursement_status", length = 20)
    @Enumerated(EnumType.STRING)
    private ReimbursementStatus reimbursementStatus = ReimbursementStatus.PENDING;
    
    // Tax information
    @Type(type = "jsonb")
    @Column(name = "tax_info", columnDefinition = "jsonb")
    private Map<String, Object> taxInfo;
    
    @Column(name = "tax_deductible")
    private Boolean taxDeductible = true;
    
    @Column(name = "tax_amount", precision = 19, scale = 2)
    private BigDecimal taxAmount;
    
    @Column(name = "tax_rate", precision = 5, scale = 4)
    private BigDecimal taxRate;
    
    // Mileage information (for travel expenses)
    @Type(type = "jsonb")
    @Column(name = "mileage_info", columnDefinition = "jsonb")
    private Map<String, Object> mileageInfo;
    
    @Column(name = "miles_driven", precision = 10, scale = 2)
    private BigDecimal milesDriven;
    
    @Column(name = "mileage_rate", precision = 5, scale = 3)
    private BigDecimal mileageRate;
    
    @Column(name = "start_location", length = 200)
    private String startLocation;
    
    @Column(name = "end_location", length = 200)
    private String endLocation;
    
    // Entertainment/meal expenses
    @Type(type = "jsonb")
    @Column(name = "attendees", columnDefinition = "jsonb")
    private List<String> attendees;
    
    @Column(name = "business_purpose", columnDefinition = "TEXT")
    private String businessPurpose;
    
    @Column(name = "meal_type", length = 20)
    private String mealType; // BREAKFAST, LUNCH, DINNER, OTHER
    
    @Column(name = "attendee_count")
    private Integer attendeeCount;
    
    // Payment information
    @Column(name = "payment_method", length = 30)
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;
    
    @Column(name = "card_last_four", length = 4)
    private String cardLastFour;
    
    @Column(name = "transaction_id", length = 100)
    private String transactionId;
    
    // Approval workflow
    @Column(name = "requires_approval")
    private Boolean requiresApproval = true;
    
    @Column(name = "approval_threshold", precision = 19, scale = 2)
    private BigDecimal approvalThreshold;
    
    @Column(name = "auto_approved")
    private Boolean autoApproved = false;
    
    @Type(type = "jsonb")
    @Column(name = "approval_workflow", columnDefinition = "jsonb")
    private List<Map<String, Object>> approvalWorkflow;
    
    @Column(name = "current_approval_step")
    private Integer currentApprovalStep = 0;
    
    // Comments and notes
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;
    
    @Type(type = "jsonb")
    @Column(name = "comments", columnDefinition = "jsonb")
    private List<Map<String, Object>> comments;
    
    // Audit trail
    @Type(type = "jsonb")
    @Column(name = "audit_trail", columnDefinition = "jsonb")
    private List<Map<String, Object>> auditTrail;
    
    // Integration data
    @Column(name = "external_id", length = 100)
    private String externalId; // For accounting system integration
    
    @Column(name = "exported_to_accounting")
    private Boolean exportedToAccounting = false;
    
    @Column(name = "exported_at")
    private LocalDateTime exportedAt;
    
    @Type(type = "jsonb")
    @Column(name = "integration_data", columnDefinition = "jsonb")
    private Map<String, Object> integrationData;
    
    // Metadata
    @Type(type = "jsonb")
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    
    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;
    
    @Column(name = "reimbursed_at")
    private LocalDateTime reimbursedAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        if (status == Status.SUBMITTED && submittedAt == null) {
            submittedAt = LocalDateTime.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        
        if (status == Status.APPROVED && approvedAt == null) {
            approvedAt = LocalDateTime.now();
        } else if (status == Status.REJECTED && rejectedAt == null) {
            rejectedAt = LocalDateTime.now();
        }
        
        if (reimbursementStatus == ReimbursementStatus.REIMBURSED && reimbursedAt == null) {
            reimbursedAt = LocalDateTime.now();
        }
    }
    
    // Business logic methods
    public boolean isPending() {
        return status == Status.SUBMITTED || status == Status.UNDER_REVIEW;
    }
    
    public boolean isApproved() {
        return status == Status.APPROVED;
    }
    
    public boolean isRejected() {
        return status == Status.REJECTED;
    }
    
    public boolean canBeApproved() {
        return status == Status.SUBMITTED || status == Status.UNDER_REVIEW;
    }
    
    public boolean canBeRejected() {
        return status == Status.SUBMITTED || status == Status.UNDER_REVIEW;
    }
    
    public boolean needsReceipt() {
        // Typically expenses over $25 require receipts
        return amount.compareTo(BigDecimal.valueOf(25)) > 0 && receiptUrl == null;
    }
    
    public boolean isTravelExpense() {
        return category != null && (
                category.toLowerCase().contains("travel") ||
                category.toLowerCase().contains("transportation") ||
                category.toLowerCase().contains("lodging") ||
                mileageInfo != null
        );
    }
    
    public boolean isMealExpense() {
        return category != null && (
                category.toLowerCase().contains("meal") ||
                category.toLowerCase().contains("entertainment") ||
                category.toLowerCase().contains("dining")
        );
    }
    
    public boolean requiresBusinessPurpose() {
        return isMealExpense() || 
               (category != null && category.toLowerCase().contains("entertainment"));
    }
    
    public boolean isHighValue() {
        return amount.compareTo(BigDecimal.valueOf(1000)) > 0;
    }
    
    public boolean isRecurring() {
        return metadata != null && 
               Boolean.TRUE.equals(metadata.get("isRecurring"));
    }
    
    public BigDecimal getEffectiveReimbursementAmount() {
        if (reimbursementAmount != null) {
            return reimbursementAmount;
        }
        return isReimbursable ? amount : BigDecimal.ZERO;
    }
    
    public String getDisplayCategory() {
        if (subcategory != null && !subcategory.isEmpty()) {
            return category + " - " + subcategory;
        }
        return category;
    }
    
    public boolean hasReceipt() {
        return receiptUrl != null && !receiptUrl.isEmpty();
    }
    
    public boolean hasReceiptData() {
        return receiptData != null && !receiptData.isEmpty();
    }
    
    public String getApprovalStatusDisplay() {
        if (autoApproved) {
            return "Auto-approved";
        }
        
        switch (status) {
            case DRAFT:
                return "Draft";
            case SUBMITTED:
                return "Pending Approval";
            case UNDER_REVIEW:
                return "Under Review";
            case APPROVED:
                return "Approved";
            case REJECTED:
                return "Rejected";
            default:
                return status.name();
        }
    }
    
    public int getBusinessDaysToApproval() {
        if (approvedAt == null || submittedAt == null) {
            return 0;
        }
        // Simplified calculation - would use actual business day calculation
        return (int) java.time.temporal.ChronoUnit.DAYS.between(submittedAt, approvedAt);
    }
    
    // Enums
    public enum Status {
        DRAFT,
        SUBMITTED,
        UNDER_REVIEW,
        APPROVED,
        REJECTED,
        CANCELLED
    }
    
    public enum ReimbursementStatus {
        PENDING,
        APPROVED,
        PROCESSING,
        REIMBURSED,
        REJECTED,
        NOT_REIMBURSABLE
    }
    
    public enum PaymentMethod {
        CORPORATE_CARD,
        PERSONAL_CARD,
        CASH,
        CHECK,
        BANK_TRANSFER,
        DIGITAL_WALLET,
        OTHER
    }
}