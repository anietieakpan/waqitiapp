package com.waqiti.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to add a new employee to business account")
public class AddEmployeeRequest {
    
    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z\\s'-]+$", message = "First name contains invalid characters")
    @Schema(description = "Employee first name", example = "John", required = true)
    private String firstName;
    
    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z\\s'-]+$", message = "Last name contains invalid characters")
    @Schema(description = "Employee last name", example = "Doe", required = true)
    private String lastName;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "Employee email address", example = "john.doe@company.com", required = true)
    private String email;
    
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    @Schema(description = "Employee phone number", example = "+1234567890")
    private String phoneNumber;
    
    @Size(max = 100, message = "Department must not exceed 100 characters")
    @Schema(description = "Department name", example = "Engineering")
    private String department;
    
    @NotBlank(message = "Role is required")
    @Size(max = 100, message = "Role must not exceed 100 characters")
    @Schema(description = "Employee role", example = "Software Engineer", required = true)
    private String role;
    
    @Size(max = 150, message = "Title must not exceed 150 characters")
    @Schema(description = "Employee title", example = "Senior Software Engineer")
    private String title;
    
    @Schema(description = "Manager's employee ID")
    private UUID managerId;
    
    @DecimalMin(value = "0.00", message = "Spending limit must be non-negative")
    @Digits(integer = 9, fraction = 2, message = "Invalid spending limit format")
    @Schema(description = "Monthly spending limit", example = "5000.00")
    private BigDecimal spendingLimit;
    
    @DecimalMin(value = "0.00", message = "Daily spending limit must be non-negative")
    @Digits(integer = 9, fraction = 2, message = "Invalid daily spending limit format")
    @Schema(description = "Daily spending limit", example = "500.00")
    private BigDecimal dailySpendingLimit;
    
    @NotNull(message = "Hire date is required")
    @PastOrPresent(message = "Hire date cannot be in the future")
    @Schema(description = "Employee hire date", example = "2024-01-15", required = true)
    private LocalDate hireDate;
    
    @Schema(description = "Employee type", example = "FULL_TIME")
    @Pattern(regexp = "^(FULL_TIME|PART_TIME|CONTRACT|INTERN|CONSULTANT)$", 
             message = "Invalid employee type")
    private String employeeType;
    
    @Schema(description = "Access level", example = "STANDARD")
    @Pattern(regexp = "^(ADMIN|MANAGER|STANDARD|LIMITED|VIEW_ONLY)$", 
             message = "Invalid access level")
    private String accessLevel;
    
    @NotNull(message = "Permissions list is required")
    @Schema(description = "List of permissions granted to the employee", required = true)
    private List<String> permissions;
    
    @Schema(description = "Employee address information")
    private AddressInfo address;
    
    @Schema(description = "Emergency contact information")
    private EmergencyContact emergencyContact;
    
    @Schema(description = "Compensation details")
    private CompensationDetails compensation;
    
    @Schema(description = "Card issuance settings")
    private CardSettings cardSettings;
    
    @Schema(description = "Approval authorities")
    private ApprovalAuthorities approvalAuthorities;
    
    @Schema(description = "System access settings")
    private SystemAccess systemAccess;
    
    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressInfo {
        @Size(max = 200, message = "Street address must not exceed 200 characters")
        private String streetAddress;
        
        @Size(max = 100, message = "City must not exceed 100 characters")
        private String city;
        
        @Size(max = 100, message = "State/Province must not exceed 100 characters")
        private String stateProvince;
        
        @Pattern(regexp = "^[A-Z0-9\\s-]{3,10}$", message = "Invalid postal code format")
        private String postalCode;
        
        @Size(min = 2, max = 2, message = "Country code must be 2 characters")
        private String countryCode;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmergencyContact {
        @NotBlank(message = "Emergency contact name is required")
        @Size(max = 200, message = "Contact name must not exceed 200 characters")
        private String name;
        
        @Size(max = 100, message = "Relationship must not exceed 100 characters")
        private String relationship;
        
        @NotBlank(message = "Emergency contact phone is required")
        @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
        private String phoneNumber;
        
        @Email(message = "Invalid email format")
        private String email;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompensationDetails {
        @DecimalMin(value = "0.00", message = "Salary must be non-negative")
        @Digits(integer = 12, fraction = 2, message = "Invalid salary format")
        private BigDecimal salary;
        
        @Pattern(regexp = "^(ANNUAL|MONTHLY|BIWEEKLY|WEEKLY|HOURLY)$", 
                 message = "Invalid pay frequency")
        private String payFrequency;
        
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
        private String currency;
        
        @DecimalMin(value = "0.00", message = "Bonus must be non-negative")
        private BigDecimal bonusEligibility;
        
        private Map<String, BigDecimal> benefits;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardSettings {
        private boolean issuePhysicalCard;
        private boolean issueVirtualCard;
        private BigDecimal cardSpendingLimit;
        private List<String> allowedMerchantCategories;
        private List<String> blockedMerchantCategories;
        private boolean internationalTransactions;
        private boolean atmWithdrawals;
        private BigDecimal atmWithdrawalLimit;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalAuthorities {
        private boolean canApproveExpenses;
        private BigDecimal maxApprovalAmount;
        private boolean canApproveInvoices;
        private boolean canApprovePayments;
        private List<String> approvalCategories;
        private List<UUID> delegatedApprovers;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemAccess {
        private boolean apiAccess;
        private List<String> apiScopes;
        private boolean mobileAppAccess;
        private boolean webPortalAccess;
        private List<String> ipWhitelist;
        private Map<String, Boolean> featureFlags;
        private String defaultDashboard;
    }
}