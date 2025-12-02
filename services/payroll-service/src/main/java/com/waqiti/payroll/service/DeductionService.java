package com.waqiti.payroll.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enterprise-grade Deduction Calculation Service
 * Handles pre-tax and post-tax deductions including:
 * - Health Insurance (Medical, Dental, Vision)
 * - Retirement Contributions (401k, Roth 401k, 403b)
 * - Flexible Spending Accounts (FSA, HSA)
 * - Wage Garnishments (Child Support, Tax Liens, Student Loans)
 * - Other Deductions (Union Dues, Charitable Giving, Parking)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeductionService {

    // IRS 2025 Contribution Limits
    private static final BigDecimal MAX_401K_CONTRIBUTION = new BigDecimal("23000");
    private static final BigDecimal MAX_401K_CATCHUP = new BigDecimal("7500"); // Age 50+
    private static final BigDecimal MAX_HSA_INDIVIDUAL = new BigDecimal("4150");
    private static final BigDecimal MAX_HSA_FAMILY = new BigDecimal("8300");
    private static final BigDecimal MAX_HSA_CATCHUP = new BigDecimal("1000"); // Age 55+
    private static final BigDecimal MAX_FSA_HEALTH = new BigDecimal("3200");
    private static final BigDecimal MAX_FSA_DEPENDENT_CARE = new BigDecimal("5000");

    /**
     * Calculate all deductions for an employee
     */
    public DeductionCalculationResult calculateDeductions(String employeeId, BigDecimal grossPay, LocalDate payPeriod) {
        return calculateDeductions(employeeId, grossPay, payPeriod, new EmployeeDeductionProfile());
    }

    /**
     * Calculate comprehensive deductions with employee profile
     */
    public DeductionCalculationResult calculateDeductions(
            String employeeId,
            BigDecimal grossPay,
            LocalDate payPeriod,
            EmployeeDeductionProfile profile) {

        log.info("Calculating deductions for employee: {}, gross: ${}, period: {}", employeeId, grossPay, payPeriod);

        DeductionCalculationResult result = new DeductionCalculationResult();
        result.setEmployeeId(employeeId);
        result.setGrossPay(grossPay);
        result.setPayPeriod(payPeriod);

        BigDecimal adjustedGrossPay = grossPay;
        List<DeductionItem> allDeductions = new ArrayList<>();

        // PHASE 1: PRE-TAX DEDUCTIONS (reduce taxable income)

        // 1. Health Insurance (Medical, Dental, Vision)
        if (profile.hasHealthInsurance()) {
            DeductionItem healthDeduction = calculateHealthInsurance(profile, payPeriod);
            allDeductions.add(healthDeduction);
            adjustedGrossPay = adjustedGrossPay.subtract(healthDeduction.getAmount());
            result.addPreTaxDeduction(healthDeduction);
        }

        // 2. Retirement Contributions (401k, 403b)
        if (profile.has401kContribution()) {
            DeductionItem retirement = calculate401kContribution(employeeId, grossPay, profile, payPeriod);
            allDeductions.add(retirement);
            adjustedGrossPay = adjustedGrossPay.subtract(retirement.getAmount());
            result.addPreTaxDeduction(retirement);
        }

        // 3. Health Savings Account (HSA)
        if (profile.hasHSA()) {
            DeductionItem hsa = calculateHSAContribution(profile, payPeriod);
            allDeductions.add(hsa);
            adjustedGrossPay = adjustedGrossPay.subtract(hsa.getAmount());
            result.addPreTaxDeduction(hsa);
        }

        // 4. Flexible Spending Account (FSA)
        if (profile.hasFSA()) {
            DeductionItem fsa = calculateFSAContribution(profile, payPeriod);
            allDeductions.add(fsa);
            adjustedGrossPay = adjustedGrossPay.subtract(fsa.getAmount());
            result.addPreTaxDeduction(fsa);
        }

        // 5. Commuter Benefits (Transit, Parking)
        if (profile.hasCommuterBenefits()) {
            DeductionItem commuter = calculateCommuterBenefits(profile, payPeriod);
            allDeductions.add(commuter);
            adjustedGrossPay = adjustedGrossPay.subtract(commuter.getAmount());
            result.addPreTaxDeduction(commuter);
        }

        result.setTaxableIncome(adjustedGrossPay);

        // PHASE 2: POST-TAX DEDUCTIONS (after tax withholding)

        // 6. Roth 401k Contributions
        if (profile.hasRoth401k()) {
            DeductionItem roth = calculateRoth401kContribution(employeeId, grossPay, profile, payPeriod);
            allDeductions.add(roth);
            result.addPostTaxDeduction(roth);
        }

        // 7. Wage Garnishments (LEGALLY REQUIRED - highest priority)
        if (profile.hasGarnishments()) {
            List<DeductionItem> garnishments = calculateGarnishments(employeeId, grossPay, profile, payPeriod);
            allDeductions.addAll(garnishments);
            garnishments.forEach(result::addPostTaxDeduction);
        }

        // 8. Union Dues
        if (profile.hasUnionDues()) {
            DeductionItem union = calculateUnionDues(profile, payPeriod);
            allDeductions.add(union);
            result.addPostTaxDeduction(union);
        }

        // 9. Charitable Contributions
        if (profile.hasCharitableContributions()) {
            DeductionItem charity = calculateCharitableContributions(profile, grossPay);
            allDeductions.add(charity);
            result.addPostTaxDeduction(charity);
        }

        // 10. Other Deductions (Parking, Gym, Life Insurance)
        if (profile.hasOtherDeductions()) {
            List<DeductionItem> others = calculateOtherDeductions(profile, payPeriod);
            allDeductions.addAll(others);
            others.forEach(result::addPostTaxDeduction);
        }

        // Calculate totals
        BigDecimal totalPreTax = result.getPreTaxDeductions().stream()
                .map(DeductionItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPostTax = result.getPostTaxDeductions().stream()
                .map(DeductionItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        result.setTotalPreTaxDeductions(totalPreTax);
        result.setTotalPostTaxDeductions(totalPostTax);
        result.setTotalDeductions(totalPreTax.add(totalPostTax));

        log.info("Employee {} deductions - Pre-tax: ${}, Post-tax: ${}, Total: ${}",
                 employeeId, totalPreTax, totalPostTax, result.getTotalDeductions());

        return result;
    }

    /**
     * Calculate Health Insurance deductions (Medical, Dental, Vision)
     */
    private DeductionItem calculateHealthInsurance(EmployeeDeductionProfile profile, LocalDate payPeriod) {
        BigDecimal amount = BigDecimal.ZERO;
        StringBuilder description = new StringBuilder("Health Insurance: ");

        if (profile.getMedicalPremium() != null) {
            amount = amount.add(profile.getMedicalPremium());
            description.append("Medical ");
        }

        if (profile.getDentalPremium() != null) {
            amount = amount.add(profile.getDentalPremium());
            description.append("Dental ");
        }

        if (profile.getVisionPremium() != null) {
            amount = amount.add(profile.getVisionPremium());
            description.append("Vision");
        }

        return new DeductionItem("HEALTH_INSURANCE", description.toString().trim(),
                                 amount, DeductionType.PRE_TAX, DeductionCategory.BENEFITS);
    }

    /**
     * Calculate Traditional 401k Contribution (pre-tax)
     */
    private DeductionItem calculate401kContribution(String employeeId, BigDecimal grossPay,
                                                     EmployeeDeductionProfile profile, LocalDate payPeriod) {
        BigDecimal contributionAmount;

        if (profile.is401kPercentage()) {
            // Percentage-based contribution
            contributionAmount = grossPay.multiply(profile.get401kPercentage())
                                         .setScale(2, RoundingMode.HALF_UP);
        } else {
            // Fixed amount per paycheck
            contributionAmount = profile.get401kAmount();
        }

        // Check annual limit ($23,000 for 2025, $30,500 with catch-up)
        BigDecimal ytdContribution = profile.getYtd401kContribution();
        BigDecimal annualLimit = MAX_401K_CONTRIBUTION;

        // Age 50+ catch-up contribution
        if (profile.isAge50OrOlder()) {
            annualLimit = annualLimit.add(MAX_401K_CATCHUP);
        }

        BigDecimal newYtd = ytdContribution.add(contributionAmount);
        if (newYtd.compareTo(annualLimit) > 0) {
            // Cap at annual limit
            contributionAmount = annualLimit.subtract(ytdContribution);
            log.warn("Employee {} 401k contribution capped at annual limit: ${}", employeeId, contributionAmount);
        }

        // Employer match (if applicable)
        BigDecimal employerMatch = BigDecimal.ZERO;
        if (profile.hasEmployerMatch()) {
            employerMatch = contributionAmount.multiply(profile.getEmployerMatchPercentage())
                                              .setScale(2, RoundingMode.HALF_UP);
        }

        return new DeductionItem("401K_EMPLOYEE", "401k Contribution",
                                 contributionAmount, DeductionType.PRE_TAX, DeductionCategory.RETIREMENT,
                                 employerMatch);
    }

    /**
     * Calculate Roth 401k Contribution (post-tax)
     */
    private DeductionItem calculateRoth401kContribution(String employeeId, BigDecimal grossPay,
                                                         EmployeeDeductionProfile profile, LocalDate payPeriod) {
        BigDecimal contributionAmount;

        if (profile.isRoth401kPercentage()) {
            contributionAmount = grossPay.multiply(profile.getRoth401kPercentage())
                                         .setScale(2, RoundingMode.HALF_UP);
        } else {
            contributionAmount = profile.getRoth401kAmount();
        }

        // Combined 401k + Roth 401k limit
        BigDecimal ytdTotal401k = profile.getYtd401kContribution().add(profile.getYtdRoth401kContribution());
        BigDecimal annualLimit = MAX_401K_CONTRIBUTION;
        if (profile.isAge50OrOlder()) {
            annualLimit = annualLimit.add(MAX_401K_CATCHUP);
        }

        BigDecimal newYtd = ytdTotal401k.add(contributionAmount);
        if (newYtd.compareTo(annualLimit) > 0) {
            contributionAmount = annualLimit.subtract(ytdTotal401k);
            log.warn("Employee {} Roth 401k contribution capped at annual limit: ${}", employeeId, contributionAmount);
        }

        return new DeductionItem("ROTH_401K", "Roth 401k Contribution",
                                 contributionAmount, DeductionType.POST_TAX, DeductionCategory.RETIREMENT);
    }

    /**
     * Calculate Health Savings Account (HSA) contribution
     */
    private DeductionItem calculateHSAContribution(EmployeeDeductionProfile profile, LocalDate payPeriod) {
        BigDecimal contributionAmount = profile.getHsaContribution();

        // Annual limits: $4,150 (individual), $8,300 (family), +$1,000 catch-up (55+)
        BigDecimal annualLimit = profile.isHsaFamilyCoverage() ? MAX_HSA_FAMILY : MAX_HSA_INDIVIDUAL;
        if (profile.isAge55OrOlder()) {
            annualLimit = annualLimit.add(MAX_HSA_CATCHUP);
        }

        BigDecimal ytdHSA = profile.getYtdHsaContribution();
        BigDecimal newYtd = ytdHSA.add(contributionAmount);

        if (newYtd.compareTo(annualLimit) > 0) {
            contributionAmount = annualLimit.subtract(ytdHSA);
        }

        return new DeductionItem("HSA", "Health Savings Account",
                                 contributionAmount, DeductionType.PRE_TAX, DeductionCategory.BENEFITS);
    }

    /**
     * Calculate Flexible Spending Account (FSA) contribution
     */
    private DeductionItem calculateFSAContribution(EmployeeDeductionProfile profile, LocalDate payPeriod) {
        BigDecimal healthFSA = profile.getHealthFsaContribution();
        BigDecimal dependentFSA = profile.getDependentCareFsaContribution();

        // Annual limits: $3,200 (health), $5,000 (dependent care)
        BigDecimal ytdHealthFSA = profile.getYtdHealthFsaContribution();
        BigDecimal ytdDependentFSA = profile.getYtdDependentCareFsaContribution();

        if (ytdHealthFSA.add(healthFSA).compareTo(MAX_FSA_HEALTH) > 0) {
            healthFSA = MAX_FSA_HEALTH.subtract(ytdHealthFSA);
        }

        if (ytdDependentFSA.add(dependentFSA).compareTo(MAX_FSA_DEPENDENT_CARE) > 0) {
            dependentFSA = MAX_FSA_DEPENDENT_CARE.subtract(ytdDependentFSA);
        }

        BigDecimal totalFSA = healthFSA.add(dependentFSA);
        return new DeductionItem("FSA", "Flexible Spending Account",
                                 totalFSA, DeductionType.PRE_TAX, DeductionCategory.BENEFITS);
    }

    /**
     * Calculate Commuter Benefits (Transit, Parking)
     */
    private DeductionItem calculateCommuterBenefits(EmployeeDeductionProfile profile, LocalDate payPeriod) {
        BigDecimal transit = profile.getTransitBenefit();
        BigDecimal parking = profile.getParkingBenefit();
        BigDecimal total = transit.add(parking);

        return new DeductionItem("COMMUTER", "Commuter Benefits (Transit + Parking)",
                                 total, DeductionType.PRE_TAX, DeductionCategory.BENEFITS);
    }

    /**
     * Calculate Wage Garnishments (LEGALLY REQUIRED)
     * Priority order: IRS Tax Levy, Child Support, Student Loans, Creditor Garnishments
     */
    private List<DeductionItem> calculateGarnishments(String employeeId, BigDecimal grossPay,
                                                      EmployeeDeductionProfile profile, LocalDate payPeriod) {
        List<DeductionItem> garnishments = new ArrayList<>();

        // Federal law limits total garnishments to 25% of disposable income
        BigDecimal disposableIncome = grossPay; // Simplified - should be after taxes
        BigDecimal maxGarnishment = disposableIncome.multiply(new BigDecimal("0.25"));

        BigDecimal totalGarnished = BigDecimal.ZERO;

        // 1. IRS Tax Levy (highest priority)
        if (profile.hasTaxLevy()) {
            BigDecimal taxLevy = calculateTaxLevy(profile, disposableIncome);
            garnishments.add(new DeductionItem("TAX_LEVY", "IRS Tax Levy",
                                               taxLevy, DeductionType.POST_TAX, DeductionCategory.GARNISHMENT));
            totalGarnished = totalGarnished.add(taxLevy);
        }

        // 2. Child Support (2nd priority)
        if (profile.hasChildSupport() && totalGarnished.compareTo(maxGarnishment) < 0) {
            BigDecimal childSupport = profile.getChildSupportAmount();
            BigDecimal remaining = maxGarnishment.subtract(totalGarnished);
            childSupport = childSupport.min(remaining);

            garnishments.add(new DeductionItem("CHILD_SUPPORT", "Child Support",
                                               childSupport, DeductionType.POST_TAX, DeductionCategory.GARNISHMENT));
            totalGarnished = totalGarnished.add(childSupport);
        }

        // 3. Student Loan Garnishment
        if (profile.hasStudentLoanGarnishment() && totalGarnished.compareTo(maxGarnishment) < 0) {
            BigDecimal studentLoan = profile.getStudentLoanGarnishmentAmount();
            BigDecimal remaining = maxGarnishment.subtract(totalGarnished);
            studentLoan = studentLoan.min(remaining);

            garnishments.add(new DeductionItem("STUDENT_LOAN", "Student Loan Garnishment",
                                               studentLoan, DeductionType.POST_TAX, DeductionCategory.GARNISHMENT));
            totalGarnished = totalGarnished.add(studentLoan);
        }

        // 4. Creditor Garnishments (lowest priority)
        if (profile.hasCreditorGarnishment() && totalGarnished.compareTo(maxGarnishment) < 0) {
            BigDecimal creditor = profile.getCreditorGarnishmentAmount();
            BigDecimal remaining = maxGarnishment.subtract(totalGarnished);
            creditor = creditor.min(remaining);

            garnishments.add(new DeductionItem("CREDITOR", "Creditor Garnishment",
                                               creditor, DeductionType.POST_TAX, DeductionCategory.GARNISHMENT));
        }

        return garnishments;
    }

    /**
     * Calculate IRS Tax Levy (based on IRS Publication 1494)
     */
    private BigDecimal calculateTaxLevy(EmployeeDeductionProfile profile, BigDecimal disposableIncome) {
        // Simplified - actual calculation involves exempt amounts based on filing status
        BigDecimal exemptAmount = getIRSTaxLevyExemption(profile.getFilingStatus(), profile.getDependents());
        BigDecimal taxableAmount = disposableIncome.subtract(exemptAmount);

        if (taxableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return taxableAmount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Get IRS Tax Levy Exemption Amount (IRS Publication 1494)
     */
    private BigDecimal getIRSTaxLevyExemption(String filingStatus, int dependents) {
        // 2025 weekly exemption amounts (simplified)
        BigDecimal baseExemption = switch (filingStatus) {
            case "SINGLE" -> new BigDecimal("275.00");
            case "MARRIED_JOINT" -> new BigDecimal("550.00");
            case "HEAD_OF_HOUSEHOLD" -> new BigDecimal("412.50");
            default -> new BigDecimal("275.00");
        };

        // Additional exemption per dependent
        BigDecimal dependentExemption = new BigDecimal("137.50").multiply(new BigDecimal(dependents));

        return baseExemption.add(dependentExemption);
    }

    /**
     * Calculate Union Dues
     */
    private DeductionItem calculateUnionDues(EmployeeDeductionProfile profile, LocalDate payPeriod) {
        BigDecimal amount = profile.getUnionDuesAmount();
        return new DeductionItem("UNION_DUES", "Union Dues",
                                 amount, DeductionType.POST_TAX, DeductionCategory.OTHER);
    }

    /**
     * Calculate Charitable Contributions
     */
    private DeductionItem calculateCharitableContributions(EmployeeDeductionProfile profile, BigDecimal grossPay) {
        BigDecimal amount;

        if (profile.isCharitablePercentage()) {
            amount = grossPay.multiply(profile.getCharitablePercentage())
                             .setScale(2, RoundingMode.HALF_UP);
        } else {
            amount = profile.getCharitableAmount();
        }

        return new DeductionItem("CHARITABLE", "Charitable Contributions",
                                 amount, DeductionType.POST_TAX, DeductionCategory.OTHER);
    }

    /**
     * Calculate Other Deductions (Parking, Gym, Life Insurance)
     */
    private List<DeductionItem> calculateOtherDeductions(EmployeeDeductionProfile profile, LocalDate payPeriod) {
        List<DeductionItem> others = new ArrayList<>();

        if (profile.getSupplementalLifeInsurance() != null) {
            others.add(new DeductionItem("LIFE_INSURANCE", "Supplemental Life Insurance",
                                         profile.getSupplementalLifeInsurance(),
                                         DeductionType.POST_TAX, DeductionCategory.BENEFITS));
        }

        if (profile.getGymMembership() != null) {
            others.add(new DeductionItem("GYM", "Gym Membership",
                                         profile.getGymMembership(),
                                         DeductionType.POST_TAX, DeductionCategory.OTHER));
        }

        return others;
    }

    // ============= DTOs =============

    public static class EmployeeDeductionProfile {
        // Health Insurance
        private BigDecimal medicalPremium;
        private BigDecimal dentalPremium;
        private BigDecimal visionPremium;

        // 401k
        private boolean is401kPercentage;
        private BigDecimal _401kPercentage;
        private BigDecimal _401kAmount;
        private BigDecimal ytd401kContribution = BigDecimal.ZERO;
        private boolean hasEmployerMatch;
        private BigDecimal employerMatchPercentage = new BigDecimal("0.05"); // 5% default

        // Roth 401k
        private boolean isRoth401kPercentage;
        private BigDecimal roth401kPercentage;
        private BigDecimal roth401kAmount;
        private BigDecimal ytdRoth401kContribution = BigDecimal.ZERO;

        // HSA
        private BigDecimal hsaContribution;
        private boolean hsaFamilyCoverage;
        private BigDecimal ytdHsaContribution = BigDecimal.ZERO;

        // FSA
        private BigDecimal healthFsaContribution;
        private BigDecimal dependentCareFsaContribution;
        private BigDecimal ytdHealthFsaContribution = BigDecimal.ZERO;
        private BigDecimal ytdDependentCareFsaContribution = BigDecimal.ZERO;

        // Commuter
        private BigDecimal transitBenefit;
        private BigDecimal parkingBenefit;

        // Garnishments
        private boolean hasTaxLevy;
        private boolean hasChildSupport;
        private BigDecimal childSupportAmount;
        private boolean hasStudentLoanGarnishment;
        private BigDecimal studentLoanGarnishmentAmount;
        private boolean hasCreditorGarnishment;
        private BigDecimal creditorGarnishmentAmount;

        // Tax Levy Details
        private String filingStatus = "SINGLE";
        private int dependents;

        // Other
        private BigDecimal unionDuesAmount;
        private boolean charitablePercentage;
        private BigDecimal charitablePercentage;
        private BigDecimal charitableAmount;
        private BigDecimal supplementalLifeInsurance;
        private BigDecimal gymMembership;

        // Age-based limits
        private int employeeAge;

        // Helper methods
        public boolean hasHealthInsurance() {
            return medicalPremium != null || dentalPremium != null || visionPremium != null;
        }

        public boolean has401kContribution() {
            return _401kPercentage != null || _401kAmount != null;
        }

        public boolean hasRoth401k() {
            return roth401kPercentage != null || roth401kAmount != null;
        }

        public boolean hasHSA() {
            return hsaContribution != null && hsaContribution.compareTo(BigDecimal.ZERO) > 0;
        }

        public boolean hasFSA() {
            return (healthFsaContribution != null && healthFsaContribution.compareTo(BigDecimal.ZERO) > 0) ||
                   (dependentCareFsaContribution != null && dependentCareFsaContribution.compareTo(BigDecimal.ZERO) > 0);
        }

        public boolean hasCommuterBenefits() {
            return (transitBenefit != null && transitBenefit.compareTo(BigDecimal.ZERO) > 0) ||
                   (parkingBenefit != null && parkingBenefit.compareTo(BigDecimal.ZERO) > 0);
        }

        public boolean hasGarnishments() {
            return hasTaxLevy || hasChildSupport || hasStudentLoanGarnishment || hasCreditorGarnishment;
        }

        public boolean hasUnionDues() {
            return unionDuesAmount != null && unionDuesAmount.compareTo(BigDecimal.ZERO) > 0;
        }

        public boolean hasCharitableContributions() {
            return charitablePercentage != null || charitableAmount != null;
        }

        public boolean hasOtherDeductions() {
            return supplementalLifeInsurance != null || gymMembership != null;
        }

        public boolean isAge50OrOlder() {
            return employeeAge >= 50;
        }

        public boolean isAge55OrOlder() {
            return employeeAge >= 55;
        }

        // Getters and Setters (generated for all fields)
        public BigDecimal getMedicalPremium() { return medicalPremium; }
        public void setMedicalPremium(BigDecimal medicalPremium) { this.medicalPremium = medicalPremium; }
        public BigDecimal getDentalPremium() { return dentalPremium; }
        public void setDentalPremium(BigDecimal dentalPremium) { this.dentalPremium = dentalPremium; }
        public BigDecimal getVisionPremium() { return visionPremium; }
        public void setVisionPremium(BigDecimal visionPremium) { this.visionPremium = visionPremium; }

        public boolean is401kPercentage() { return is401kPercentage; }
        public void set401kPercentage(boolean is401kPercentage) { this.is401kPercentage = is401kPercentage; }
        public BigDecimal get401kPercentage() { return _401kPercentage; }
        public void set401kPercentage(BigDecimal _401kPercentage) { this._401kPercentage = _401kPercentage; }
        public BigDecimal get401kAmount() { return _401kAmount; }
        public void set401kAmount(BigDecimal _401kAmount) { this._401kAmount = _401kAmount; }
        public BigDecimal getYtd401kContribution() { return ytd401kContribution; }
        public void setYtd401kContribution(BigDecimal ytd401kContribution) { this.ytd401kContribution = ytd401kContribution; }
        public boolean hasEmployerMatch() { return hasEmployerMatch; }
        public void setHasEmployerMatch(boolean hasEmployerMatch) { this.hasEmployerMatch = hasEmployerMatch; }
        public BigDecimal getEmployerMatchPercentage() { return employerMatchPercentage; }
        public void setEmployerMatchPercentage(BigDecimal employerMatchPercentage) { this.employerMatchPercentage = employerMatchPercentage; }

        public boolean isRoth401kPercentage() { return isRoth401kPercentage; }
        public void setRoth401kPercentage(boolean isRoth401kPercentage) { this.isRoth401kPercentage = isRoth401kPercentage; }
        public BigDecimal getRoth401kPercentage() { return roth401kPercentage; }
        public void setRoth401kPercentage(BigDecimal roth401kPercentage) { this.roth401kPercentage = roth401kPercentage; }
        public BigDecimal getRoth401kAmount() { return roth401kAmount; }
        public void setRoth401kAmount(BigDecimal roth401kAmount) { this.roth401kAmount = roth401kAmount; }
        public BigDecimal getYtdRoth401kContribution() { return ytdRoth401kContribution; }
        public void setYtdRoth401kContribution(BigDecimal ytdRoth401kContribution) { this.ytdRoth401kContribution = ytdRoth401kContribution; }

        public BigDecimal getHsaContribution() { return hsaContribution; }
        public void setHsaContribution(BigDecimal hsaContribution) { this.hsaContribution = hsaContribution; }
        public boolean isHsaFamilyCoverage() { return hsaFamilyCoverage; }
        public void setHsaFamilyCoverage(boolean hsaFamilyCoverage) { this.hsaFamilyCoverage = hsaFamilyCoverage; }
        public BigDecimal getYtdHsaContribution() { return ytdHsaContribution; }
        public void setYtdHsaContribution(BigDecimal ytdHsaContribution) { this.ytdHsaContribution = ytdHsaContribution; }

        public BigDecimal getHealthFsaContribution() { return healthFsaContribution; }
        public void setHealthFsaContribution(BigDecimal healthFsaContribution) { this.healthFsaContribution = healthFsaContribution; }
        public BigDecimal getDependentCareFsaContribution() { return dependentCareFsaContribution; }
        public void setDependentCareFsaContribution(BigDecimal dependentCareFsaContribution) { this.dependentCareFsaContribution = dependentCareFsaContribution; }
        public BigDecimal getYtdHealthFsaContribution() { return ytdHealthFsaContribution; }
        public void setYtdHealthFsaContribution(BigDecimal ytdHealthFsaContribution) { this.ytdHealthFsaContribution = ytdHealthFsaContribution; }
        public BigDecimal getYtdDependentCareFsaContribution() { return ytdDependentCareFsaContribution; }
        public void setYtdDependentCareFsaContribution(BigDecimal ytdDependentCareFsaContribution) { this.ytdDependentCareFsaContribution = ytdDependentCareFsaContribution; }

        public BigDecimal getTransitBenefit() { return transitBenefit; }
        public void setTransitBenefit(BigDecimal transitBenefit) { this.transitBenefit = transitBenefit; }
        public BigDecimal getParkingBenefit() { return parkingBenefit; }
        public void setParkingBenefit(BigDecimal parkingBenefit) { this.parkingBenefit = parkingBenefit; }

        public boolean hasTaxLevy() { return hasTaxLevy; }
        public void setHasTaxLevy(boolean hasTaxLevy) { this.hasTaxLevy = hasTaxLevy; }
        public boolean hasChildSupport() { return hasChildSupport; }
        public void setHasChildSupport(boolean hasChildSupport) { this.hasChildSupport = hasChildSupport; }
        public BigDecimal getChildSupportAmount() { return childSupportAmount; }
        public void setChildSupportAmount(BigDecimal childSupportAmount) { this.childSupportAmount = childSupportAmount; }
        public boolean hasStudentLoanGarnishment() { return hasStudentLoanGarnishment; }
        public void setHasStudentLoanGarnishment(boolean hasStudentLoanGarnishment) { this.hasStudentLoanGarnishment = hasStudentLoanGarnishment; }
        public BigDecimal getStudentLoanGarnishmentAmount() { return studentLoanGarnishmentAmount; }
        public void setStudentLoanGarnishmentAmount(BigDecimal studentLoanGarnishmentAmount) { this.studentLoanGarnishmentAmount = studentLoanGarnishmentAmount; }
        public boolean hasCreditorGarnishment() { return hasCreditorGarnishment; }
        public void setHasCreditorGarnishment(boolean hasCreditorGarnishment) { this.hasCreditorGarnishment = hasCreditorGarnishment; }
        public BigDecimal getCreditorGarnishmentAmount() { return creditorGarnishmentAmount; }
        public void setCreditorGarnishmentAmount(BigDecimal creditorGarnishmentAmount) { this.creditorGarnishmentAmount = creditorGarnishmentAmount; }

        public String getFilingStatus() { return filingStatus; }
        public void setFilingStatus(String filingStatus) { this.filingStatus = filingStatus; }
        public int getDependents() { return dependents; }
        public void setDependents(int dependents) { this.dependents = dependents; }

        public BigDecimal getUnionDuesAmount() { return unionDuesAmount; }
        public void setUnionDuesAmount(BigDecimal unionDuesAmount) { this.unionDuesAmount = unionDuesAmount; }
        public boolean isCharitablePercentage() { return charitablePercentage; }
        public void setCharitablePercentage(boolean charitablePercentage) { this.charitablePercentage = charitablePercentage; }
        public BigDecimal getCharitablePercentage() { return charitablePercentage; }
        public void setCharitablePercentage(BigDecimal charitablePercentage) { this.charitablePercentage = charitablePercentage; }
        public BigDecimal getCharitableAmount() { return charitableAmount; }
        public void setCharitableAmount(BigDecimal charitableAmount) { this.charitableAmount = charitableAmount; }
        public BigDecimal getSupplementalLifeInsurance() { return supplementalLifeInsurance; }
        public void setSupplementalLifeInsurance(BigDecimal supplementalLifeInsurance) { this.supplementalLifeInsurance = supplementalLifeInsurance; }
        public BigDecimal getGymMembership() { return gymMembership; }
        public void setGymMembership(BigDecimal gymMembership) { this.gymMembership = gymMembership; }

        public int getEmployeeAge() { return employeeAge; }
        public void setEmployeeAge(int employeeAge) { this.employeeAge = employeeAge; }
    }

    public static class DeductionCalculationResult {
        private String employeeId;
        private BigDecimal grossPay;
        private LocalDate payPeriod;
        private BigDecimal taxableIncome;
        private List<DeductionItem> preTaxDeductions = new ArrayList<>();
        private List<DeductionItem> postTaxDeductions = new ArrayList<>();
        private BigDecimal totalPreTaxDeductions = BigDecimal.ZERO;
        private BigDecimal totalPostTaxDeductions = BigDecimal.ZERO;
        private BigDecimal totalDeductions = BigDecimal.ZERO;

        public void addPreTaxDeduction(DeductionItem item) {
            preTaxDeductions.add(item);
        }

        public void addPostTaxDeduction(DeductionItem item) {
            postTaxDeductions.add(item);
        }

        // Getters and Setters
        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
        public BigDecimal getGrossPay() { return grossPay; }
        public void setGrossPay(BigDecimal grossPay) { this.grossPay = grossPay; }
        public LocalDate getPayPeriod() { return payPeriod; }
        public void setPayPeriod(LocalDate payPeriod) { this.payPeriod = payPeriod; }
        public BigDecimal getTaxableIncome() { return taxableIncome; }
        public void setTaxableIncome(BigDecimal taxableIncome) { this.taxableIncome = taxableIncome; }
        public List<DeductionItem> getPreTaxDeductions() { return preTaxDeductions; }
        public List<DeductionItem> getPostTaxDeductions() { return postTaxDeductions; }
        public BigDecimal getTotalPreTaxDeductions() { return totalPreTaxDeductions; }
        public void setTotalPreTaxDeductions(BigDecimal totalPreTaxDeductions) { this.totalPreTaxDeductions = totalPreTaxDeductions; }
        public BigDecimal getTotalPostTaxDeductions() { return totalPostTaxDeductions; }
        public void setTotalPostTaxDeductions(BigDecimal totalPostTaxDeductions) { this.totalPostTaxDeductions = totalPostTaxDeductions; }
        public BigDecimal getTotalDeductions() { return totalDeductions; }
        public void setTotalDeductions(BigDecimal totalDeductions) { this.totalDeductions = totalDeductions; }
    }

    public static class DeductionItem {
        private String code;
        private String description;
        private BigDecimal amount;
        private DeductionType type;
        private DeductionCategory category;
        private BigDecimal employerContribution; // For employer match

        public DeductionItem(String code, String description, BigDecimal amount,
                            DeductionType type, DeductionCategory category) {
            this.code = code;
            this.description = description;
            this.amount = amount;
            this.type = type;
            this.category = category;
        }

        public DeductionItem(String code, String description, BigDecimal amount,
                            DeductionType type, DeductionCategory category, BigDecimal employerContribution) {
            this(code, description, amount, type, category);
            this.employerContribution = employerContribution;
        }

        // Getters
        public String getCode() { return code; }
        public String getDescription() { return description; }
        public BigDecimal getAmount() { return amount; }
        public DeductionType getType() { return type; }
        public DeductionCategory getCategory() { return category; }
        public BigDecimal getEmployerContribution() { return employerContribution; }
    }

    public enum DeductionType {
        PRE_TAX,  // Reduces taxable income
        POST_TAX  // Deducted after taxes
    }

    public enum DeductionCategory {
        BENEFITS,     // Health, HSA, FSA
        RETIREMENT,   // 401k, Roth 401k
        GARNISHMENT,  // Legally required deductions
        OTHER         // Union dues, charitable, etc.
    }
}
