package com.waqiti.business.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Business profile response with comprehensive business information")
public class BusinessProfileResponse {
    
    @Schema(description = "Unique business account identifier")
    private UUID accountId;
    
    @Schema(description = "Business name", example = "Acme Corporation")
    private String businessName;
    
    @Schema(description = "Type of business entity", example = "LLC")
    private String businessType;
    
    @Schema(description = "Business industry", example = "Technology")
    private String industry;
    
    @Schema(description = "Business registration number")
    private String registrationNumber;
    
    @Schema(description = "Tax identification number")
    private String taxId;
    
    @Schema(description = "Business address")
    private String address;
    
    @Schema(description = "Primary phone number")
    private String phoneNumber;
    
    @Schema(description = "Business email address")
    private String email;
    
    @Schema(description = "Business website URL")
    private String website;
    
    @Schema(description = "Current account status")
    private String status;
    
    @Schema(description = "Account creation timestamp")
    private LocalDateTime createdAt;
    
    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
    
    @Schema(description = "Monthly transaction limit")
    private BigDecimal monthlyTransactionLimit;
    
    @Schema(description = "Current month's transaction total")
    private BigDecimal currentMonthTransactionTotal;
    
    @Schema(description = "Auto-approval limit for expenses")
    private BigDecimal autoApprovalLimit;
    
    @Schema(description = "Account risk level")
    private String riskLevel;
    
    @Schema(description = "Account balance")
    private BigDecimal balance;
    
    @Schema(description = "Available balance")
    private BigDecimal availableBalance;
    
    @Schema(description = "Number of active employees")
    private Integer activeEmployeeCount;
    
    @Schema(description = "Number of sub-accounts")
    private Integer subAccountCount;
    
    @Schema(description = "Account verification status")
    private VerificationStatus verificationStatus;
    
    @Schema(description = "Account features and limits")
    private Map<String, Object> features;
    
    @Schema(description = "Business metadata")
    private Map<String, Object> metadata;
    
    @Schema(description = "Primary contact information")
    private ContactInfo primaryContact;
    
    @Schema(description = "Banking information")
    private BankingInfo bankingInfo;
    
    @Schema(description = "Compliance information")
    private ComplianceInfo complianceInfo;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificationStatus {
        private boolean isVerified;
        private String verificationLevel;
        private LocalDateTime verifiedAt;
        private LocalDateTime nextReviewDate;
        private Map<String, Boolean> verificationChecks;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContactInfo {
        private String fullName;
        private String title;
        private String email;
        private String phoneNumber;
        private String alternatePhoneNumber;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankingInfo {
        private String bankName;
        private String accountNumber;
        private String routingNumber;
        private String swiftCode;
        private String iban;
        private boolean isVerified;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceInfo {
        private boolean amlCheckPassed;
        private boolean kycCompleted;
        private String complianceLevel;
        private LocalDateTime lastComplianceReview;
        private LocalDateTime nextComplianceReview;
        private Map<String, String> licenses;
    }
}