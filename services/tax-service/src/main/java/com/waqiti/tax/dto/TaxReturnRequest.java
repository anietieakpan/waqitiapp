package com.waqiti.tax.dto;

import com.waqiti.tax.domain.TaxReturn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxReturnRequest {
    
    @NotNull(message = "Tax year is required")
    @Min(value = 2020, message = "Tax year must be 2020 or later")
    @Max(value = 2030, message = "Tax year cannot be more than current year + 1")
    private Integer taxYear;
    
    @NotNull(message = "Filing status is required")
    private TaxReturn.FilingStatus filingStatus;
    
    @Pattern(regexp = "\\d{3}-\\d{2}-\\d{4}", message = "SSN must be in format XXX-XX-XXXX")
    private String ssn;
    
    @Builder.Default
    private boolean includeCrypto = false;
    
    @Builder.Default
    private boolean includeInvestments = false;
    
    @Builder.Default
    private boolean authorizeIRSImport = false;
    
    @Builder.Default
    private boolean isPremium = false;
    
    private String spouseSSN; // For married filing jointly
    
    private String spouseFirstName;
    
    private String spouseLastName;
    
    private Boolean hasHealthInsurance;
    
    private Boolean claimDependents;
    
    private Integer numberOfDependents;
    
    private Boolean itemizeDeductions;
    
    private Boolean hasBusinessIncome;
    
    private Boolean hasRentalIncome;
    
    private Boolean hasForeignIncome;
    
    private Boolean soldCrypto;
    
    private Boolean soldStocks;
    
    private Boolean receivedUnemployment;
    
    private Boolean paidStudentLoanInterest;
    
    private Boolean contributedToIRA;
    
    private Boolean contributedTo401k;
    
    private String preferredLanguage;
    
    private String preparerNotes;
}