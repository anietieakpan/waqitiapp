package com.waqiti.business.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "business_invoices", indexes = {
        @Index(name = "idx_business_invoices_account", columnList = "business_account_id"),
        @Index(name = "idx_business_invoices_number", columnList = "invoice_number", unique = true),
        @Index(name = "idx_business_invoices_customer", columnList = "customer_id"),
        @Index(name = "idx_business_invoices_status", columnList = "status"),
        @Index(name = "idx_business_invoices_due_date", columnList = "due_date"),
        @Index(name = "idx_business_invoices_amount", columnList = "total_amount")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessInvoice {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "business_account_id", nullable = false)
    private UUID businessAccountId;
    
    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;
    
    @Column(name = "customer_id")
    private UUID customerId; // Internal customer ID if exists
    
    @Type(type = "jsonb")
    @Column(name = "customer_info", columnDefinition = "jsonb")
    private Map<String, Object> customerInfo; // External customer details
    
    @Column(name = "issue_date", nullable = false)
    private LocalDateTime issueDate;
    
    @Column(name = "due_date", nullable = false)
    private LocalDateTime dueDate;
    
    @Type(type = "jsonb")
    @Column(name = "line_items", columnDefinition = "jsonb")
    private List<Map<String, Object>> lineItems;
    
    @Column(name = "subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;
    
    @Column(name = "tax_amount", precision = 19, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;
    
    @Column(name = "discount_amount", precision = 19, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;
    
    @Column(name = "shipping_amount", precision = 19, scale = 2)
    private BigDecimal shippingAmount = BigDecimal.ZERO;
    
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";
    
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Status status;
    
    @Column(name = "payment_status", length = 20)
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;
    
    @Column(name = "payment_terms", length = 20)
    @Enumerated(EnumType.STRING)
    private PaymentTerms paymentTerms = PaymentTerms.NET_30;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "terms", columnDefinition = "TEXT")
    private String terms;
    
    @Column(name = "project", length = 100)
    private String project;
    
    @Column(name = "purchase_order", length = 100)
    private String purchaseOrder;
    
    // Payment tracking
    @Column(name = "amount_paid", precision = 19, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;
    
    @Column(name = "amount_due", precision = 19, scale = 2)
    private BigDecimal amountDue;
    
    @Column(name = "last_payment_date")
    private LocalDateTime lastPaymentDate;
    
    @Type(type = "jsonb")
    @Column(name = "payments", columnDefinition = "jsonb")
    private List<Map<String, Object>> payments;
    
    // Tax information
    @Type(type = "jsonb")
    @Column(name = "tax_details", columnDefinition = "jsonb")
    private Map<String, Object> taxDetails;
    
    @Column(name = "tax_rate", precision = 5, scale = 4)
    private BigDecimal taxRate;
    
    @Column(name = "tax_exempt")
    private Boolean taxExempt = false;
    
    // Billing address
    @Type(type = "jsonb")
    @Column(name = "billing_address", columnDefinition = "jsonb")
    private Map<String, Object> billingAddress;
    
    @Type(type = "jsonb")
    @Column(name = "shipping_address", columnDefinition = "jsonb")
    private Map<String, Object> shippingAddress;
    
    // File attachments
    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;
    
    @Type(type = "jsonb")
    @Column(name = "attachments", columnDefinition = "jsonb")
    private List<String> attachments;
    
    // Communication
    @Column(name = "sent_to_customer")
    private Boolean sentToCustomer = false;
    
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    
    @Column(name = "viewed_by_customer")
    private Boolean viewedByCustomer = false;
    
    @Column(name = "viewed_at")
    private LocalDateTime viewedAt;
    
    @Type(type = "jsonb")
    @Column(name = "email_history", columnDefinition = "jsonb")
    private List<Map<String, Object>> emailHistory;
    
    // Reminder settings
    @Column(name = "reminder_enabled")
    private Boolean reminderEnabled = true;
    
    @Column(name = "first_reminder_sent")
    private Boolean firstReminderSent = false;
    
    @Column(name = "second_reminder_sent")
    private Boolean secondReminderSent = false;
    
    @Column(name = "final_reminder_sent")
    private Boolean finalReminderSent = false;
    
    @Column(name = "last_reminder_sent")
    private LocalDateTime lastReminderSent;
    
    // Recurring invoice
    @Column(name = "is_recurring")
    private Boolean isRecurring = false;
    
    @Column(name = "recurring_pattern", length = 20)
    private String recurringPattern; // MONTHLY, QUARTERLY, ANNUALLY
    
    @Column(name = "recurring_interval")
    private Integer recurringInterval = 1;
    
    @Column(name = "next_invoice_date")
    private LocalDateTime nextInvoiceDate;
    
    @Column(name = "recurring_end_date")
    private LocalDateTime recurringEndDate;
    
    @Column(name = "parent_invoice_id")
    private UUID parentInvoiceId; // For recurring invoices
    
    // Integration data
    @Column(name = "external_id", length = 100)
    private String externalId;
    
    @Column(name = "quickbooks_id", length = 100)
    private String quickbooksId;
    
    @Column(name = "stripe_invoice_id", length = 100)
    private String stripeInvoiceId;
    
    @Type(type = "jsonb")
    @Column(name = "integration_data", columnDefinition = "jsonb")
    private Map<String, Object> integrationData;
    
    // Metadata
    @Type(type = "jsonb")
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "sent_date")
    private LocalDateTime sentDate;
    
    @Column(name = "paid_date")
    private LocalDateTime paidDate;
    
    @Column(name = "voided_at")
    private LocalDateTime voidedAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        if (amountDue == null) {
            amountDue = totalAmount.subtract(amountPaid);
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        
        // Update amount due
        amountDue = totalAmount.subtract(amountPaid);
        
        // Update payment status based on amount paid
        if (amountPaid.compareTo(BigDecimal.ZERO) == 0) {
            paymentStatus = PaymentStatus.UNPAID;
        } else if (amountPaid.compareTo(totalAmount) >= 0) {
            paymentStatus = PaymentStatus.PAID;
            if (paidDate == null) {
                paidDate = LocalDateTime.now();
            }
        } else {
            paymentStatus = PaymentStatus.PARTIALLY_PAID;
        }
        
        if (status == Status.SENT && sentDate == null) {
            sentDate = LocalDateTime.now();
        }
    }
    
    // Business logic methods
    public boolean isDraft() {
        return status == Status.DRAFT;
    }
    
    public boolean isSent() {
        return status == Status.SENT;
    }
    
    public boolean isPaid() {
        return paymentStatus == PaymentStatus.PAID;
    }
    
    public boolean isPartiallyPaid() {
        return paymentStatus == PaymentStatus.PARTIALLY_PAID;
    }
    
    public boolean isOverdue() {
        return !isPaid() && dueDate.isBefore(LocalDateTime.now());
    }
    
    public boolean isVoid() {
        return status == Status.VOID;
    }
    
    public boolean canBeSent() {
        return status == Status.DRAFT && !isVoid();
    }
    
    public boolean canBeVoided() {
        return !isPaid() && !isVoid();
    }
    
    public boolean canReceivePayment() {
        return isSent() && !isPaid() && !isVoid();
    }
    
    public long getDaysOverdue() {
        if (!isOverdue()) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(dueDate, LocalDateTime.now());
    }
    
    public long getDaysUntilDue() {
        if (isOverdue()) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), dueDate);
    }
    
    public BigDecimal getOutstandingBalance() {
        return totalAmount.subtract(amountPaid);
    }
    
    public String getPaymentStatusDisplay() {
        switch (paymentStatus) {
            case UNPAID:
                return isOverdue() ? "Overdue" : "Unpaid";
            case PARTIALLY_PAID:
                return "Partially Paid";
            case PAID:
                return "Paid";
            case REFUNDED:
                return "Refunded";
            default:
                return paymentStatus.name();
        }
    }
    
    public String getCustomerName() {
        if (customerInfo != null) {
            Object name = customerInfo.get("name");
            if (name != null) return name.toString();
            
            // Try company name
            Object company = customerInfo.get("company");
            if (company != null) return company.toString();
            
            // Try first/last name combination
            Object firstName = customerInfo.get("firstName");
            Object lastName = customerInfo.get("lastName");
            if (firstName != null && lastName != null) {
                return firstName + " " + lastName;
            }
        }
        return "Unknown Customer";
    }
    
    public String getCustomerEmail() {
        if (customerInfo != null) {
            Object email = customerInfo.get("email");
            if (email != null) {
                String emailStr = email.toString().trim();
                return emailStr.isEmpty() ? "[NO_EMAIL]" : emailStr;
            }
        }
        
        // Return placeholder instead of null for better handling
        return "[NO_EMAIL_PROVIDED]";
    }
    
    public boolean shouldSendReminder() {
        if (!reminderEnabled || isPaid() || isVoid()) return false;
        
        long daysOverdue = getDaysOverdue();
        
        // First reminder: 1 day after due date
        if (daysOverdue >= 1 && !firstReminderSent) return true;
        
        // Second reminder: 7 days after due date  
        if (daysOverdue >= 7 && !secondReminderSent) return true;
        
        // Final reminder: 30 days after due date
        if (daysOverdue >= 30 && !finalReminderSent) return true;
        
        return false;
    }
    
    public String getNextReminderType() {
        if (!firstReminderSent) return "FIRST";
        if (!secondReminderSent) return "SECOND";
        if (!finalReminderSent) return "FINAL";
        
        // Return appropriate status instead of null
        if (isPaid()) return "NO_REMINDER_PAID";
        if (isVoid()) return "NO_REMINDER_VOID";
        return "NO_REMINDER_NEEDED";
    }
    
    public boolean isRecurringInvoice() {
        return isRecurring && recurringPattern != null;
    }
    
    public LocalDateTime getNextRecurringDate() {
        if (!isRecurringInvoice()) {
            // Return a sentinel value indicating no recurring date instead of null
            return LocalDateTime.MIN; // Use MIN to indicate "no recurring date"
        }
        
        LocalDateTime base = nextInvoiceDate != null ? nextInvoiceDate : issueDate;
        
        switch (recurringPattern) {
            case "WEEKLY":
                return base.plusWeeks(recurringInterval);
            case "MONTHLY":
                return base.plusMonths(recurringInterval);
            case "QUARTERLY":
                return base.plusMonths(3 * recurringInterval);
            case "ANNUALLY":
                return base.plusYears(recurringInterval);
            default:
                log.warn("Unknown recurring pattern: {}, defaulting to monthly", recurringPattern);
                return base.plusMonths(recurringInterval);
        }
    }
    
    public BigDecimal getPaymentPercentage() {
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return amountPaid.divide(totalAmount, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
    
    // Enums
    public enum Status {
        DRAFT,
        SENT,
        VIEWED,
        PAID,
        VOID,
        CANCELLED
    }
    
    public enum PaymentStatus {
        UNPAID,
        PARTIALLY_PAID,
        PAID,
        OVERDUE,
        REFUNDED
    }
    
    public enum PaymentTerms {
        DUE_ON_RECEIPT("Due on Receipt", 0),
        NET_15("Net 15", 15),
        NET_30("Net 30", 30),
        NET_45("Net 45", 45),
        NET_60("Net 60", 60),
        NET_90("Net 90", 90);
        
        private final String displayName;
        private final int days;
        
        PaymentTerms(String displayName, int days) {
            this.displayName = displayName;
            this.days = days;
        }
        
        public String getDisplayName() { return displayName; }
        public int getDays() { return days; }
    }
}