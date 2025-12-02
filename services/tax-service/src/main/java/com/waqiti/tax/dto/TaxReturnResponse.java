package com.waqiti.tax.dto;

import com.waqiti.tax.domain.TaxReturn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxReturnResponse {
    
    private UUID id;
    private UUID userId;
    private Integer taxYear;
    private TaxReturn.FilingStatus filingStatus;
    private TaxReturn.TaxReturnStatus status;
    
    private BigDecimal estimatedRefund;
    private BigDecimal estimatedTax;
    private BigDecimal totalIncome;
    private BigDecimal adjustedGrossIncome;
    private BigDecimal federalTax;
    private BigDecimal stateTax;
    private BigDecimal capitalGains;
    private BigDecimal deductions;
    private BigDecimal taxCredits;
    private BigDecimal totalWithholdings;
    
    private Boolean isPremium;
    private Boolean includeCrypto;
    private Boolean includeInvestments;
    private Boolean isStateReturnRequired;
    private Boolean refundReceived;
    
    private String irsConfirmationNumber;
    private String stateConfirmationNumber;
    
    private LocalDateTime createdAt;
    private LocalDateTime lastModified;
    private LocalDateTime filedAt;
    private LocalDateTime refundReceivedDate;
    
    private PersonalInfoDto personalInfo;
    
    // Progress indicators
    private Integer documentsCount;
    private Integer verifiedDocumentsCount;
    private Integer formsCount;
    private Integer completedFormsCount;
    private Double completionPercentage;
    
    // Next steps
    private String nextAction;
    private String nextActionDescription;
    private Boolean canFile;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PersonalInfoDto {
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private String address;
        private String city;
        private String state;
        private String zipCode;
        
        public String getFullName() {
            return firstName + " " + lastName;
        }
        
        public String getFullAddress() {
            return address + ", " + city + ", " + state + " " + zipCode;
        }
    }
}