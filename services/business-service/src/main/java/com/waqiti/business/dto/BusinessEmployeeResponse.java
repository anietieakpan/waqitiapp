package com.waqiti.business.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Business employee response with comprehensive details")
public class BusinessEmployeeResponse {
    
    @Schema(description = "Unique employee identifier")
    private UUID employeeId;
    
    @Schema(description = "Employee number", example = "EMP-001234")
    private String employeeNumber;
    
    @Schema(description = "Associated user ID if employee has app access")
    private UUID userId;
    
    @Schema(description = "Employee first name")
    private String firstName;
    
    @Schema(description = "Employee last name")
    private String lastName;
    
    @Schema(description = "Full name")
    private String fullName;
    
    @Schema(description = "Employee email address")
    private String email;
    
    @Schema(description = "Employee phone number")
    private String phoneNumber;
    
    @Schema(description = "Department")
    private String department;
    
    @Schema(description = "Role")
    private String role;
    
    @Schema(description = "Title")
    private String title;
    
    @Schema(description = "Manager information")
    private ManagerInfo manager;
    
    @Schema(description = "Monthly spending limit")
    private BigDecimal spendingLimit;
    
    @Schema(description = "Current month spending")
    private BigDecimal currentMonthSpending;
    
    @Schema(description = "Remaining budget this month")
    private BigDecimal remainingBudget;
    
    @Schema(description = "Employee status")
    private String status;
    
    @Schema(description = "Hire date")
    private LocalDate hireDate;
    
    @Schema(description = "Years of service")
    private Integer yearsOfService;
    
    @Schema(description = "Employee type")
    private String employeeType;
    
    @Schema(description = "Access level")
    private String accessLevel;
    
    @Schema(description = "List of permissions")
    private List<String> permissions;
    
    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;
    
    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
    
    @Schema(description = "Last login timestamp")
    private LocalDateTime lastLoginAt;
    
    @Schema(description = "Performance metrics")
    private PerformanceMetrics performanceMetrics;
    
    @Schema(description = "Card information")
    private CardInfo cardInfo;
    
    @Schema(description = "Recent activity")
    private RecentActivity recentActivity;
    
    @Schema(description = "Compliance status")
    private ComplianceStatus complianceStatus;
    
    @Schema(description = "Direct reports count")
    private Integer directReportsCount;
    
    @Schema(description = "Projects assigned")
    private List<ProjectAssignment> projects;
    
    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManagerInfo {
        private UUID managerId;
        private String managerName;
        private String managerEmail;
        private String managerTitle;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetrics {
        private Integer totalExpensesSubmitted;
        private Integer expensesApproved;
        private Integer expensesRejected;
        private BigDecimal totalExpenseAmount;
        private Double averageExpenseAmount;
        private Integer invoicesCreated;
        private BigDecimal totalInvoiceAmount;
        private Double complianceScore;
        private LocalDateTime lastReviewDate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardInfo {
        private boolean hasPhysicalCard;
        private boolean hasVirtualCard;
        private String cardNumber; // Masked
        private String cardStatus;
        private LocalDate cardExpiryDate;
        private BigDecimal cardSpendingLimit;
        private BigDecimal cardCurrentSpending;
        private LocalDateTime lastCardTransaction;
        private Integer cardTransactionCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentActivity {
        private LocalDateTime lastExpenseSubmitted;
        private LocalDateTime lastInvoiceCreated;
        private LocalDateTime lastPaymentProcessed;
        private LocalDateTime lastProfileUpdate;
        private List<ActivityItem> recentItems;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityItem {
        private String type;
        private String description;
        private LocalDateTime timestamp;
        private BigDecimal amount;
        private String status;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceStatus {
        private boolean backgroundCheckCompleted;
        private LocalDate backgroundCheckDate;
        private boolean trainingCompleted;
        private List<String> completedTraining;
        private List<String> pendingTraining;
        private boolean documentsVerified;
        private LocalDate nextReviewDate;
        private Map<String, Boolean> complianceChecks;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectAssignment {
        private UUID projectId;
        private String projectName;
        private String projectRole;
        private LocalDate assignedDate;
        private BigDecimal projectBudget;
        private BigDecimal projectSpent;
        private String projectStatus;
    }
}