package com.waqiti.payroll.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tax Reporting Service
 * Generates IRS-required tax forms and reports:
 * - W-2 (Employee Annual Wage Statement)
 * - 1099 (Contractor Payments)
 * - Form 941 (Quarterly Federal Tax Return)
 * - Annual Tax Summary Reports
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReportingService {

    /**
     * Generate W-2 form for employee (annual wage and tax statement)
     */
    public W2Report generateW2Report(String employeeId, int year) {
        log.info("Generating W-2 report for employee: {}, year: {}", employeeId, year);

        // TODO: Fetch employee payroll data from repository for the year

        W2Report report = new W2Report();
        report.setEmployeeId(employeeId);
        report.setTaxYear(year);
        report.setFormType("W-2");

        // Box 1: Wages, tips, other compensation
        report.setWages(BigDecimal.ZERO);

        // Box 2: Federal income tax withheld
        report.setFederalTaxWithheld(BigDecimal.ZERO);

        // Box 3: Social security wages
        report.setSocialSecurityWages(BigDecimal.ZERO);

        // Box 4: Social security tax withheld
        report.setSocialSecurityTaxWithheld(BigDecimal.ZERO);

        // Box 5: Medicare wages and tips
        report.setMedicareWages(BigDecimal.ZERO);

        // Box 6: Medicare tax withheld
        report.setMedicareTaxWithheld(BigDecimal.ZERO);

        // Box 12: Other compensation codes (401k, etc.)
        report.setOtherCompensation(new HashMap<>());

        // Box 15-20: State/local tax information
        report.setStateTaxInfo(new ArrayList<>());

        log.info("W-2 report generated for employee: {}, year: {}", employeeId, year);
        return report;
    }

    /**
     * Generate 1099-NEC form for contractor (non-employee compensation)
     */
    public Form1099Report generate1099Report(String contractorId, int year) {
        log.info("Generating 1099-NEC report for contractor: {}, year: {}", contractorId, year);

        // TODO: Fetch contractor payment data from repository for the year

        Form1099Report report = new Form1099Report();
        report.setContractorId(contractorId);
        report.setTaxYear(year);
        report.setFormType("1099-NEC");

        // Box 1: Non-employee compensation
        report.setNonEmployeeCompensation(BigDecimal.ZERO);

        // Box 4: Federal income tax withheld (if backup withholding)
        report.setFederalTaxWithheld(BigDecimal.ZERO);

        // Box 5-7: State tax information
        report.setStateTaxInfo(new ArrayList<>());

        log.info("1099-NEC report generated for contractor: {}, year: {}", contractorId, year);
        return report;
    }

    /**
     * Generate Form 941 (Employer's Quarterly Federal Tax Return)
     */
    public Form941Report generateForm941Report(String companyId, int year, int quarter) {
        log.info("Generating Form 941 for company: {}, year: {}, quarter: {}", companyId, year, quarter);

        // TODO: Fetch quarterly payroll data from repository

        Form941Report report = new Form941Report();
        report.setCompanyId(companyId);
        report.setTaxYear(year);
        report.setQuarter(quarter);
        report.setFormType("941");

        // Line 1: Number of employees who received wages
        report.setNumberOfEmployees(0);

        // Line 2: Wages, tips, and other compensation
        report.setTotalWages(BigDecimal.ZERO);

        // Line 3: Federal income tax withheld from wages
        report.setFederalTaxWithheld(BigDecimal.ZERO);

        // Line 5a: Taxable social security wages
        report.setSocialSecurityWages(BigDecimal.ZERO);

        // Line 5b: Taxable social security tips
        report.setSocialSecurityTips(BigDecimal.ZERO);

        // Line 5c: Taxable Medicare wages & tips
        report.setMedicareWages(BigDecimal.ZERO);

        // Line 5d: Total social security and Medicare taxes
        BigDecimal socialSecurityTax = report.getSocialSecurityWages()
            .add(report.getSocialSecurityTips())
            .multiply(new BigDecimal("0.124")); // 12.4% (employee + employer)

        BigDecimal medicareTax = report.getMedicareWages()
            .multiply(new BigDecimal("0.029")); // 2.9% (employee + employer)

        report.setTotalSocialSecurityAndMedicareTax(socialSecurityTax.add(medicareTax));

        // Line 10: Total taxes after adjustments
        report.setTotalTax(report.getFederalTaxWithheld()
            .add(report.getTotalSocialSecurityAndMedicareTax()));

        // Line 12: Total deposits for quarter
        report.setTotalDeposits(BigDecimal.ZERO);

        // Line 14: Balance due or overpayment
        report.setBalanceDue(report.getTotalTax().subtract(report.getTotalDeposits()));

        log.info("Form 941 generated - Company: {}, Year: {}, Quarter: {}, Total Tax: ${}",
                 companyId, year, quarter, report.getTotalTax());

        return report;
    }

    /**
     * Generate annual tax summary for company
     */
    public AnnualTaxSummary generateAnnualTaxSummary(String companyId, int year) {
        log.info("Generating annual tax summary for company: {}, year: {}", companyId, year);

        // TODO: Fetch all payroll data for the year from repository

        AnnualTaxSummary summary = new AnnualTaxSummary();
        summary.setCompanyId(companyId);
        summary.setTaxYear(year);

        // Employee summary
        summary.setTotalEmployees(0);
        summary.setTotalW2sGenerated(0);

        // Contractor summary
        summary.setTotal1099sGenerated(0);

        // Payroll totals
        summary.setTotalGrossWages(BigDecimal.ZERO);
        summary.setTotalNetPay(BigDecimal.ZERO);

        // Tax totals
        summary.setTotalFederalTaxWithheld(BigDecimal.ZERO);
        summary.setTotalStateTaxWithheld(BigDecimal.ZERO);
        summary.setTotalSocialSecurityTax(BigDecimal.ZERO);
        summary.setTotalMedicareTax(BigDecimal.ZERO);
        summary.setTotalFUTATax(BigDecimal.ZERO);
        summary.setTotalSUITax(BigDecimal.ZERO);

        // Total tax liability
        BigDecimal totalTaxLiability = summary.getTotalFederalTaxWithheld()
            .add(summary.getTotalStateTaxWithheld())
            .add(summary.getTotalSocialSecurityTax())
            .add(summary.getTotalMedicareTax())
            .add(summary.getTotalFUTATax())
            .add(summary.getTotalSUITax());

        summary.setTotalTaxLiability(totalTaxLiability);

        // Quarterly breakdown
        summary.setQuarterlyBreakdown(new HashMap<>());

        log.info("Annual tax summary generated - Company: {}, Year: {}, Total Tax Liability: ${}",
                 companyId, year, totalTaxLiability);

        return summary;
    }

    /**
     * Generate payroll tax report for batch
     */
    public void generatePayrollTaxReport(Object event, Object taxResult,
                                        Object executionResult, String correlationId) {
        log.info("Generating payroll tax report - Correlation: {}", correlationId);

        // TODO: Generate detailed tax report for the payroll batch
        // This would include:
        // - Per-employee tax breakdown
        // - Tax liability summary
        // - Withholding verification
        // - Compliance checklist
    }

    /**
     * Generate quarterly tax report (for Form 941 preparation)
     */
    public void generateQuarterlyTaxReport(String companyId, LocalDate payPeriod, String correlationId) {
        log.info("Generating quarterly tax report - Company: {}, Period: {}", companyId, payPeriod);

        // Determine quarter
        int quarter = (payPeriod.getMonthValue() - 1) / 3 + 1;
        int year = payPeriod.getYear();

        // Generate Form 941 for the quarter
        Form941Report report = generateForm941Report(companyId, year, quarter);

        log.info("Quarterly tax report generated - Company: {}, Q{} {}", companyId, quarter, year);
    }

    /**
     * Generate annual tax report (W-2s, 1099s, summary)
     */
    public void generateAnnualTaxReport(String companyId, LocalDate payPeriod, String correlationId) {
        log.info("Generating annual tax report - Company: {}, Period: {}", companyId, payPeriod);

        int year = payPeriod.getYear();

        // Generate annual summary
        AnnualTaxSummary summary = generateAnnualTaxSummary(companyId, year);

        // Generate W-2s for all employees
        // TODO: Fetch all employees and generate W-2s

        // Generate 1099s for all contractors
        // TODO: Fetch all contractors and generate 1099s

        log.info("Annual tax report generated - Company: {}, Year: {}", companyId, year);
    }

    /**
     * Validate W-2 report before submission
     */
    public boolean validateW2Report(W2Report report) {
        log.debug("Validating W-2 report for employee: {}", report.getEmployeeId());

        // 1. Required fields validation
        if (report.getEmployeeId() == null || report.getTaxYear() == 0) {
            log.error("W-2 validation failed: Missing required fields");
            return false;
        }

        // 2. Amount validations
        if (report.getWages().compareTo(BigDecimal.ZERO) < 0) {
            log.error("W-2 validation failed: Negative wages");
            return false;
        }

        // 3. Social Security wage base limit check ($160,200 for 2025)
        BigDecimal ssWageBase = new BigDecimal("160200");
        if (report.getSocialSecurityWages().compareTo(ssWageBase) > 0) {
            log.warn("W-2 warning: Social Security wages exceed wage base");
        }

        // 4. Medicare wages should generally equal total wages
        if (report.getMedicareWages().compareTo(report.getWages()) != 0) {
            log.warn("W-2 warning: Medicare wages differ from total wages");
        }

        return true;
    }

    /**
     * Validate Form 941 before submission
     */
    public boolean validateForm941Report(Form941Report report) {
        log.debug("Validating Form 941 for company: {}, Q{} {}",
                  report.getCompanyId(), report.getQuarter(), report.getTaxYear());

        // 1. Required fields validation
        if (report.getCompanyId() == null || report.getTaxYear() == 0 || report.getQuarter() == 0) {
            log.error("Form 941 validation failed: Missing required fields");
            return false;
        }

        // 2. Quarter validation (1-4)
        if (report.getQuarter() < 1 || report.getQuarter() > 4) {
            log.error("Form 941 validation failed: Invalid quarter");
            return false;
        }

        // 3. Tax calculation verification
        BigDecimal calculatedTax = report.getFederalTaxWithheld()
            .add(report.getTotalSocialSecurityAndMedicareTax());

        if (calculatedTax.compareTo(report.getTotalTax()) != 0) {
            log.error("Form 941 validation failed: Tax calculation mismatch");
            return false;
        }

        return true;
    }

    // ============= DTOs =============

    @lombok.Data
    public static class W2Report {
        private String employeeId;
        private String employeeName;
        private String employeeSSN;
        private String employeeAddress;
        private String companyId;
        private String companyName;
        private String companyEIN;
        private String companyAddress;
        private int taxYear;
        private String formType;

        // Box 1: Wages, tips, other compensation
        private BigDecimal wages;

        // Box 2: Federal income tax withheld
        private BigDecimal federalTaxWithheld;

        // Box 3: Social security wages
        private BigDecimal socialSecurityWages;

        // Box 4: Social security tax withheld
        private BigDecimal socialSecurityTaxWithheld;

        // Box 5: Medicare wages and tips
        private BigDecimal medicareWages;

        // Box 6: Medicare tax withheld
        private BigDecimal medicareTaxWithheld;

        // Box 7: Social security tips
        private BigDecimal socialSecurityTips;

        // Box 8: Allocated tips
        private BigDecimal allocatedTips;

        // Box 10: Dependent care benefits
        private BigDecimal dependentCareBenefits;

        // Box 11: Nonqualified plans
        private BigDecimal nonqualifiedPlans;

        // Box 12: Codes (D=401k, DD=employer health coverage, etc.)
        private Map<String, BigDecimal> otherCompensation;

        // Box 13: Checkboxes (Statutory employee, Retirement plan, Third-party sick pay)
        private boolean statutoryEmployee;
        private boolean retirementPlan;
        private boolean thirdPartySickPay;

        // Box 14: Other (employer use)
        private String other;

        // Box 15-20: State/local tax information
        private List<StateTaxInfo> stateTaxInfo;
    }

    @lombok.Data
    public static class Form1099Report {
        private String contractorId;
        private String contractorName;
        private String contractorTIN;
        private String contractorAddress;
        private String companyId;
        private String companyName;
        private String companyEIN;
        private String companyAddress;
        private int taxYear;
        private String formType;

        // Box 1: Non-employee compensation
        private BigDecimal nonEmployeeCompensation;

        // Box 4: Federal income tax withheld
        private BigDecimal federalTaxWithheld;

        // Box 5-7: State tax information
        private List<StateTaxInfo> stateTaxInfo;
    }

    @lombok.Data
    public static class Form941Report {
        private String companyId;
        private String companyName;
        private String companyEIN;
        private String companyAddress;
        private int taxYear;
        private int quarter;
        private String formType;

        // Line 1: Number of employees
        private int numberOfEmployees;

        // Line 2: Total wages, tips, and other compensation
        private BigDecimal totalWages;

        // Line 3: Federal income tax withheld
        private BigDecimal federalTaxWithheld;

        // Line 5a: Taxable social security wages
        private BigDecimal socialSecurityWages;

        // Line 5b: Taxable social security tips
        private BigDecimal socialSecurityTips;

        // Line 5c: Taxable Medicare wages & tips
        private BigDecimal medicareWages;

        // Line 5d: Total social security and Medicare taxes
        private BigDecimal totalSocialSecurityAndMedicareTax;

        // Line 10: Total taxes after adjustments
        private BigDecimal totalTax;

        // Line 12: Total deposits for this quarter
        private BigDecimal totalDeposits;

        // Line 14: Balance due or overpayment
        private BigDecimal balanceDue;
    }

    @lombok.Data
    public static class AnnualTaxSummary {
        private String companyId;
        private int taxYear;

        // Employee summary
        private int totalEmployees;
        private int totalW2sGenerated;

        // Contractor summary
        private int total1099sGenerated;

        // Payroll totals
        private BigDecimal totalGrossWages;
        private BigDecimal totalNetPay;

        // Tax totals
        private BigDecimal totalFederalTaxWithheld;
        private BigDecimal totalStateTaxWithheld;
        private BigDecimal totalSocialSecurityTax;
        private BigDecimal totalMedicareTax;
        private BigDecimal totalFUTATax;
        private BigDecimal totalSUITax;
        private BigDecimal totalTaxLiability;

        // Quarterly breakdown
        private Map<Integer, QuarterlyTaxSummary> quarterlyBreakdown;
    }

    @lombok.Data
    public static class QuarterlyTaxSummary {
        private int quarter;
        private BigDecimal totalWages;
        private BigDecimal totalTaxWithheld;
        private BigDecimal socialSecurityTax;
        private BigDecimal medicareTax;
    }

    @lombok.Data
    public static class StateTaxInfo {
        private String state;
        private String stateIdNumber;
        private BigDecimal stateWages;
        private BigDecimal stateTaxWithheld;
        private BigDecimal localWages;
        private BigDecimal localTaxWithheld;
        private String locality;
    }
}
