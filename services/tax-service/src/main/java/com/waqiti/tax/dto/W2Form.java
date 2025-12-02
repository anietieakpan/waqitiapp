package com.waqiti.tax.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class W2Form {
    private String employerEin;
    private String employerName;
    private String employerAddress;
    private Number wages;
    private Number federalTaxWithheld;
    private Number socialSecurityWages;
    private Number socialSecurityTaxWithheld;
    private Number medicareWages;
    private Number medicareTaxWithheld;
    private Number socialSecurityTips;
    private Number allocatedTips;
    private Number dependentCareBenefits;
    private Number nonqualifiedPlans;
    private String box12Codes;
    private Boolean statutoryEmployee;
    private Boolean retirementPlan;
    private Boolean thirdPartySickPay;
    private String statewages;
    private String stateTaxWithheld;
    private String state;
    private String localWages;
    private String localTaxWithheld;
    private String locality;
}
