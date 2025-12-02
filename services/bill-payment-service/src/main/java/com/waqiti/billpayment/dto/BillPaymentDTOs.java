package com.waqiti.billpayment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// ============== Biller DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BillerResponse {
    private String billerId;
    private String billerName;
    private String billerCode;
    private String category;
    private String subCategory;
    private String country;
    private String currency;
    private String logoUrl;
    private String description;
    private boolean isActive;
    private boolean supportsInquiry;
    private boolean supportsValidation;
    private boolean supportsInstantPayment;
    private boolean supportsScheduledPayment;
    private boolean supportsRecurringPayment;
    private boolean supportsPartialPayment;
    private BigDecimal minimumAmount;
    private BigDecimal maximumAmount;
    private List<String> requiredFields;
    private Map<String, String> additionalInfo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// ============== Bill Account DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AddBillAccountRequest {
    @NotBlank(message = "Biller ID is required")
    private String billerId;
    
    @NotBlank(message = "Account number is required")
    private String accountNumber;
    
    @NotBlank(message = "Account name is required")
    private String accountName;
    
    private String nickname;
    private Map<String, String> additionalFields;
    private boolean setAsDefault;
    private boolean enableAutoPay;
    private boolean enableReminders;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BillAccountResponse {
    private UUID accountId;
    private String billerId;
    private String billerName;
    private String accountNumber;
    private String accountName;
    private String nickname;
    private boolean isDefault;
    private boolean autoPayEnabled;
    private boolean remindersEnabled;
    private String status;
    private LocalDateTime lastPaymentDate;
    private BigDecimal lastPaymentAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class UpdateBillAccountRequest {
    private String accountName;
    private String nickname;
    private boolean setAsDefault;
    private boolean enableAutoPay;
    private boolean enableReminders;
    private Map<String, String> additionalFields;
}

// ============== Bill Inquiry DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BillInquiryRequest {
    @NotBlank(message = "Biller ID is required")
    private String billerId;
    
    @NotBlank(message = "Account number is required")
    private String accountNumber;
    
    private String customerReference;
    private Map<String, String> additionalFields;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BillInquiryResponse {
    private String inquiryId;
    private String billerId;
    private String billerName;
    private String accountNumber;
    private String customerName;
    private BigDecimal amountDue;
    private BigDecimal minimumAmountDue;
    private LocalDate dueDate;
    private LocalDate billDate;
    private String billPeriod;
    private String status;
    private boolean isPastDue;
    private BigDecimal lateFee;
    private List<BillItemDetail> itemDetails;
    private Map<String, Object> additionalInfo;
    private LocalDateTime inquiryTime;
    private LocalDateTime validUntil;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BillItemDetail {
    private String itemCode;
    private String itemDescription;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String category;
}

// ============== Bill Validation DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BillValidationRequest {
    @NotBlank(message = "Biller ID is required")
    private String billerId;
    
    @NotBlank(message = "Account number is required")
    private String accountNumber;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    private String customerReference;
    private Map<String, String> additionalFields;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BillValidationResponse {
    private String validationId;
    private boolean isValid;
    private String status;
    private String customerName;
    private BigDecimal validatedAmount;
    private BigDecimal convenienceFee;
    private BigDecimal totalAmount;
    private String currency;
    private List<String> validationErrors;
    private List<String> validationWarnings;
    private Map<String, Object> additionalInfo;
    private LocalDateTime validationTime;
}

// ============== Bill Payment DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PayBillRequest {
    @NotBlank(message = "Biller ID is required")
    private String billerId;
    
    @NotBlank(message = "Account number is required")
    private String accountNumber;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    @NotBlank(message = "Source account is required")
    private String sourceAccountId;
    
    private String customerReference;
    private String narration;
    private boolean saveAsTemplate;
    private boolean addToFavorites;
    private Map<String, String> additionalFields;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PayBillInstantRequest extends PayBillRequest {
    private boolean skipValidation;
    private boolean requireInstantSettlement;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BillPaymentResponse {
    private UUID paymentId;
    private String transactionReference;
    private String billerId;
    private String billerName;
    private String accountNumber;
    private String customerName;
    private BigDecimal amount;
    private BigDecimal convenienceFee;
    private BigDecimal totalAmount;
    private String currency;
    private String status;
    private String paymentMethod;
    private LocalDateTime paymentDate;
    private LocalDateTime processingDate;
    private String receiptNumber;
    private String confirmationCode;
    private Map<String, Object> additionalInfo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// ============== Scheduled Payment DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ScheduleBillPaymentRequest {
    @NotBlank(message = "Biller ID is required")
    private String billerId;
    
    @NotBlank(message = "Account number is required")
    private String accountNumber;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    @NotBlank(message = "Source account is required")
    private String sourceAccountId;
    
    @NotNull(message = "Scheduled date is required")
    @Future(message = "Scheduled date must be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate scheduledDate;
    
    private String customerReference;
    private String narration;
    private boolean sendReminder;
    private Integer reminderDaysBefore;
    private Map<String, String> additionalFields;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ScheduledPaymentResponse {
    private UUID scheduledPaymentId;
    private String billerId;
    private String billerName;
    private String accountNumber;
    private BigDecimal amount;
    private LocalDate scheduledDate;
    private String status;
    private boolean reminderEnabled;
    private Integer reminderDaysBefore;
    private LocalDateTime nextReminderDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// ============== Recurring Payment DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SetupRecurringPaymentRequest {
    @NotBlank(message = "Biller ID is required")
    private String billerId;
    
    @NotBlank(message = "Account number is required")
    private String accountNumber;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    @NotBlank(message = "Source account is required")
    private String sourceAccountId;
    
    @NotNull(message = "Frequency is required")
    private RecurringFrequency frequency;
    
    @NotNull(message = "Start date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
    
    private Integer dayOfMonth;
    private Integer dayOfWeek;
    private boolean adjustForWeekends;
    private boolean adjustForHolidays;
    private String customerReference;
    private String narration;
    private Map<String, String> additionalFields;
}

enum RecurringFrequency {
    WEEKLY, BIWEEKLY, MONTHLY, BIMONTHLY, QUARTERLY, SEMIANNUALLY, ANNUALLY
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RecurringPaymentResponse {
    private UUID recurringPaymentId;
    private String billerId;
    private String billerName;
    private String accountNumber;
    private BigDecimal amount;
    private RecurringFrequency frequency;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate nextPaymentDate;
    private String status;
    private Integer totalPayments;
    private Integer completedPayments;
    private BigDecimal totalAmountPaid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// ============== Auto-pay DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SetupAutoPayRequest {
    @NotNull(message = "Account ID is required")
    private UUID accountId;
    
    @NotBlank(message = "Source account is required")
    private String sourceAccountId;
    
    private BigDecimal maximumAmount;
    private Integer paymentDaysBefore;
    private boolean payFullAmount;
    private boolean payMinimumOnly;
    private boolean requireApprovalAboveLimit;
    private BigDecimal approvalLimit;
    private boolean sendNotificationBeforePayment;
    private Integer notificationDaysBefore;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AutoPayResponse {
    private UUID autoPayId;
    private UUID accountId;
    private String billerId;
    private String billerName;
    private String accountNumber;
    private String sourceAccountId;
    private BigDecimal maximumAmount;
    private String status;
    private boolean isActive;
    private LocalDate nextPaymentDate;
    private BigDecimal lastPaymentAmount;
    private LocalDateTime lastPaymentDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// ============== Payment Status DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PaymentStatusResponse {
    private UUID paymentId;
    private String status;
    private String statusDescription;
    private LocalDateTime statusUpdatedAt;
    private List<PaymentStatusHistory> statusHistory;
    private String failureReason;
    private boolean isRetryable;
    private LocalDateTime estimatedCompletionTime;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PaymentStatusHistory {
    private String status;
    private String description;
    private LocalDateTime timestamp;
    private String updatedBy;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class CancelPaymentRequest {
    private String reason;
    private boolean requestRefund;
}

// ============== Reports and Analytics DTOs ==============

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BillPaymentSummaryReport {
    private LocalDate fromDate;
    private LocalDate toDate;
    private Integer totalPayments;
    private BigDecimal totalAmount;
    private Map<String, BigDecimal> amountByCategory;
    private Map<String, Integer> countByCategory;
    private Map<String, BigDecimal> amountByBiller;
    private List<TopBillerSummary> topBillers;
    private BigDecimal averagePaymentAmount;
    private String mostFrequentCategory;
    private String mostFrequentBiller;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class TopBillerSummary {
    private String billerId;
    private String billerName;
    private String category;
    private Integer paymentCount;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SpendingAnalytics {
    private String period;
    private List<PeriodSpending> spendingByPeriod;
    private BigDecimal totalSpending;
    private BigDecimal averageSpending;
    private BigDecimal highestSpending;
    private BigDecimal lowestSpending;
    private Map<String, BigDecimal> spendingByCategory;
    private List<SpendingTrend> trends;
    private List<String> insights;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PeriodSpending {
    private String period;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal amount;
    private Integer paymentCount;
    private BigDecimal percentageChange;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SpendingTrend {
    private String category;
    private String trend;
    private BigDecimal percentageChange;
    private String insight;
}