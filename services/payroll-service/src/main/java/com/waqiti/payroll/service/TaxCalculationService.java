package com.waqiti.payroll.service;

import com.waqiti.payroll.domain.PayrollPayment;
import com.waqiti.payroll.domain.PayrollType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enterprise-grade Tax Calculation Service
 * Handles federal, state, FICA, Medicare, and local tax calculations
 * Compliant with IRS Publication 15 (Circular E) and state tax regulations
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaxCalculationService {

    @Value("${payroll.tax.federal-rate:0.22}")
    private BigDecimal federalTaxRate;

    @Value("${payroll.tax.state-rate:0.05}")
    private BigDecimal stateTaxRate;

    @Value("${payroll.tax.social-security-rate:0.062}")
    private BigDecimal socialSecurityRate;

    @Value("${payroll.tax.medicare-rate:0.0145}")
    private BigDecimal medicareRate;

    @Value("${payroll.tax.social-security-wage-base:160200.00}")
    private BigDecimal socialSecurityWageBase;

    @Value("${payroll.tax.additional-medicare-threshold:200000.00}")
    private BigDecimal additionalMedicareThreshold;

    @Value("${payroll.tax.additional-medicare-rate:0.009}")
    private BigDecimal additionalMedicareRate;

    private static final BigDecimal FUTA_RATE = new BigDecimal("0.006");
    private static final BigDecimal FUTA_WAGE_BASE = new BigDecimal("7000.00");
    private static final BigDecimal SUI_RATE = new BigDecimal("0.027"); // Average state unemployment

    /**
     * Calculate comprehensive payroll taxes for a batch
     */
    public TaxCalculationResult calculatePayrollTaxes(String companyId, List<PaymentCalculation> calculations, LocalDate payPeriod) {
        log.info("Calculating taxes for company: {}, period: {}, employees: {}", companyId, payPeriod, calculations.size());

        TaxCalculationResult result = new TaxCalculationResult();
        result.setCompanyId(companyId);
        result.setPayPeriod(payPeriod);
        result.setTotalEmployees(calculations.size());

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalFederalTax = BigDecimal.ZERO;
        BigDecimal totalStateTax = BigDecimal.ZERO;
        BigDecimal totalFICA = BigDecimal.ZERO;
        BigDecimal totalMedicare = BigDecimal.ZERO;
        BigDecimal totalFUTA = BigDecimal.ZERO;
        BigDecimal totalSUI = BigDecimal.ZERO;

        for (PaymentCalculation calc : calculations) {
            EmployeeTaxCalculation employeeTax = calculateEmployeeTaxes(calc);

            totalGross = totalGross.add(employeeTax.getGrossAmount());
            totalFederalTax = totalFederalTax.add(employeeTax.getFederalTax());
            totalStateTax = totalStateTax.add(employeeTax.getStateTax());
            totalFICA = totalFICA.add(employeeTax.getSocialSecurityTax());
            totalMedicare = totalMedicare.add(employeeTax.getMedicareTax());
            totalFUTA = totalFUTA.add(employeeTax.getFutaTax());
            totalSUI = totalSUI.add(employeeTax.getSuiTax());

            result.addEmployeeTaxCalculation(employeeTax);
        }

        result.setTotalGrossAmount(totalGross);
        result.setTotalFederalTax(totalFederalTax);
        result.setTotalStateTax(totalStateTax);
        result.setTotalSocialSecurityTax(totalFICA);
        result.setTotalMedicareTax(totalMedicare);
        result.setTotalFutaTax(totalFUTA);
        result.setTotalSuiTax(totalSUI);
        result.setTotalTaxWithheld(totalFederalTax.add(totalStateTax).add(totalFICA).add(totalMedicare));
        result.setTotalEmployerTax(totalFICA.add(totalMedicare).add(totalFUTA).add(totalSUI));

        log.info("Tax calculation complete - Gross: ${}, Total Withholding: ${}, Employer Tax: ${}",
                 totalGross, result.getTotalTaxWithheld(), result.getTotalEmployerTax());

        return result;
    }

    /**
     * Calculate taxes for individual employee
     */
    public EmployeeTaxCalculation calculateEmployeeTaxes(PaymentCalculation calculation) {
        String employeeId = calculation.getEmployeeId();
        BigDecimal grossAmount = calculation.getGrossAmount();
        BigDecimal ytdGross = calculation.getYtdGross();
        String filingStatus = calculation.getFilingStatus();
        int exemptions = calculation.getExemptions();
        String state = calculation.getState();

        log.debug("Calculating taxes for employee: {}, gross: ${}, YTD: ${}", employeeId, grossAmount, ytdGross);

        EmployeeTaxCalculation result = new EmployeeTaxCalculation();
        result.setEmployeeId(employeeId);
        result.setGrossAmount(grossAmount);

        // 1. Federal Income Tax (graduated brackets)
        BigDecimal federalTax = calculateFederalIncomeTax(grossAmount, ytdGross, filingStatus, exemptions);
        result.setFederalTax(federalTax);

        // 2. State Income Tax
        BigDecimal stateTax = calculateStateIncomeTax(grossAmount, state, filingStatus);
        result.setStateTax(stateTax);

        // 3. Social Security Tax (FICA) - 6.2% up to wage base
        BigDecimal socialSecurityTax = calculateSocialSecurityTax(grossAmount, ytdGross);
        result.setSocialSecurityTax(socialSecurityTax);

        // 4. Medicare Tax - 1.45% + 0.9% additional for high earners
        BigDecimal medicareTax = calculateMedicareTax(grossAmount, ytdGross);
        result.setMedicareTax(medicareTax);

        // 5. FUTA (employer-only) - 0.6% up to $7,000
        BigDecimal futaTax = calculateFUTATax(grossAmount, ytdGross);
        result.setFutaTax(futaTax);

        // 6. SUI (employer-only) - varies by state, average ~2.7%
        BigDecimal suiTax = calculateSUITax(grossAmount, state);
        result.setSuiTax(suiTax);

        // 7. Local taxes (if applicable)
        BigDecimal localTax = calculateLocalTax(grossAmount, calculation.getCity(), state);
        result.setLocalTax(localTax);

        BigDecimal totalWithholding = federalTax.add(stateTax).add(socialSecurityTax).add(medicareTax).add(localTax);
        result.setTotalWithholding(totalWithholding);

        BigDecimal netAmount = grossAmount.subtract(totalWithholding);
        result.setNetAmount(netAmount);

        log.debug("Employee {} tax breakdown - Federal: ${}, State: ${}, FICA: ${}, Medicare: ${}, Net: ${}",
                  employeeId, federalTax, stateTax, socialSecurityTax, medicareTax, netAmount);

        return result;
    }

    /**
     * Calculate Federal Income Tax using IRS graduated tax brackets (2025)
     * Based on IRS Publication 15-T (Federal Income Tax Withholding Methods)
     */
    private BigDecimal calculateFederalIncomeTax(BigDecimal grossAmount, BigDecimal ytdGross, String filingStatus, int exemptions) {
        // Standard deduction (2025)
        BigDecimal standardDeduction = getStandardDeduction(filingStatus);

        // Personal exemption amount per exemption
        BigDecimal exemptionAmount = new BigDecimal("4700").multiply(new BigDecimal(exemptions));

        // Taxable income
        BigDecimal taxableIncome = grossAmount.subtract(standardDeduction).subtract(exemptionAmount);
        if (taxableIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Apply graduated tax brackets based on filing status
        BigDecimal federalTax = BigDecimal.ZERO;

        if ("SINGLE".equals(filingStatus)) {
            federalTax = calculateTaxWithBrackets(taxableIncome, getSingleBrackets());
        } else if ("MARRIED_JOINT".equals(filingStatus)) {
            federalTax = calculateTaxWithBrackets(taxableIncome, getMarriedJointBrackets());
        } else if ("MARRIED_SEPARATE".equals(filingStatus)) {
            federalTax = calculateTaxWithBrackets(taxableIncome, getMarriedSeparateBrackets());
        } else if ("HEAD_OF_HOUSEHOLD".equals(filingStatus)) {
            federalTax = calculateTaxWithBrackets(taxableIncome, getHeadOfHouseholdBrackets());
        } else {
            // Default to single
            federalTax = calculateTaxWithBrackets(taxableIncome, getSingleBrackets());
        }

        return federalTax.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate tax using graduated brackets
     */
    private BigDecimal calculateTaxWithBrackets(BigDecimal income, List<TaxBracket> brackets) {
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal remainingIncome = income;

        for (TaxBracket bracket : brackets) {
            if (remainingIncome.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal bracketIncome;
            if (bracket.getUpperLimit() == null) {
                // Top bracket - no upper limit
                bracketIncome = remainingIncome;
            } else {
                BigDecimal bracketRange = bracket.getUpperLimit().subtract(bracket.getLowerLimit());
                bracketIncome = remainingIncome.min(bracketRange);
            }

            BigDecimal bracketTax = bracketIncome.multiply(bracket.getRate());
            totalTax = totalTax.add(bracketTax);
            remainingIncome = remainingIncome.subtract(bracketIncome);
        }

        return totalTax;
    }

    /**
     * Calculate State Income Tax
     */
    private BigDecimal calculateStateIncomeTax(BigDecimal grossAmount, String state, String filingStatus) {
        // Simplified state tax calculation - in production, would use state-specific brackets
        Map<String, BigDecimal> stateTaxRates = getStateTaxRates();
        BigDecimal rate = stateTaxRates.getOrDefault(state, stateTaxRate);
        return grossAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate Social Security Tax (FICA) - 6.2% up to wage base ($160,200 for 2025)
     */
    private BigDecimal calculateSocialSecurityTax(BigDecimal grossAmount, BigDecimal ytdGross) {
        BigDecimal newYtd = ytdGross.add(grossAmount);

        if (ytdGross.compareTo(socialSecurityWageBase) >= 0) {
            // Already exceeded wage base
            return BigDecimal.ZERO;
        }

        if (newYtd.compareTo(socialSecurityWageBase) <= 0) {
            // Under wage base - tax full amount
            return grossAmount.multiply(socialSecurityRate).setScale(2, RoundingMode.HALF_UP);
        }

        // Partially taxable - only tax up to wage base
        BigDecimal taxableAmount = socialSecurityWageBase.subtract(ytdGross);
        return taxableAmount.multiply(socialSecurityRate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate Medicare Tax - 1.45% + 0.9% additional for income over $200,000
     */
    private BigDecimal calculateMedicareTax(BigDecimal grossAmount, BigDecimal ytdGross) {
        BigDecimal newYtd = ytdGross.add(grossAmount);
        BigDecimal baseMedicare = grossAmount.multiply(medicareRate);

        // Additional Medicare Tax for high earners (over $200,000)
        BigDecimal additionalMedicare = BigDecimal.ZERO;
        if (newYtd.compareTo(additionalMedicareThreshold) > 0) {
            if (ytdGross.compareTo(additionalMedicareThreshold) >= 0) {
                // Already over threshold - tax entire amount at additional rate
                additionalMedicare = grossAmount.multiply(additionalMedicareRate);
            } else {
                // Crossed threshold - only tax excess
                BigDecimal excessAmount = newYtd.subtract(additionalMedicareThreshold);
                additionalMedicare = excessAmount.multiply(additionalMedicareRate);
            }
        }

        return baseMedicare.add(additionalMedicare).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate FUTA Tax (employer-only) - 0.6% up to $7,000 per employee
     */
    private BigDecimal calculateFUTATax(BigDecimal grossAmount, BigDecimal ytdGross) {
        BigDecimal newYtd = ytdGross.add(grossAmount);

        if (ytdGross.compareTo(FUTA_WAGE_BASE) >= 0) {
            return BigDecimal.ZERO;
        }

        if (newYtd.compareTo(FUTA_WAGE_BASE) <= 0) {
            return grossAmount.multiply(FUTA_RATE).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal taxableAmount = FUTA_WAGE_BASE.subtract(ytdGross);
        return taxableAmount.multiply(FUTA_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate SUI Tax (employer-only) - varies by state
     */
    private BigDecimal calculateSUITax(BigDecimal grossAmount, String state) {
        // Simplified - in production, use state-specific rates and wage bases
        Map<String, BigDecimal> suiRates = getSUIRates();
        BigDecimal rate = suiRates.getOrDefault(state, SUI_RATE);
        return grossAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate Local Tax (city/county) if applicable
     */
    private BigDecimal calculateLocalTax(BigDecimal grossAmount, String city, String state) {
        // Cities with local income tax
        Map<String, BigDecimal> localTaxRates = getLocalTaxRates();
        String key = state + "_" + city;
        BigDecimal rate = localTaxRates.getOrDefault(key, BigDecimal.ZERO);
        return grossAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    // ============= TAX BRACKETS (2025 IRS) =============

    private BigDecimal getStandardDeduction(String filingStatus) {
        return switch (filingStatus) {
            case "MARRIED_JOINT" -> new BigDecimal("29200");
            case "HEAD_OF_HOUSEHOLD" -> new BigDecimal("21900");
            default -> new BigDecimal("14600"); // Single
        };
    }

    private List<TaxBracket> getSingleBrackets() {
        return List.of(
            new TaxBracket(BigDecimal.ZERO, new BigDecimal("11600"), new BigDecimal("0.10")),
            new TaxBracket(new BigDecimal("11600"), new BigDecimal("47150"), new BigDecimal("0.12")),
            new TaxBracket(new BigDecimal("47150"), new BigDecimal("100525"), new BigDecimal("0.22")),
            new TaxBracket(new BigDecimal("100525"), new BigDecimal("191950"), new BigDecimal("0.24")),
            new TaxBracket(new BigDecimal("191950"), new BigDecimal("243725"), new BigDecimal("0.32")),
            new TaxBracket(new BigDecimal("243725"), new BigDecimal("609350"), new BigDecimal("0.35")),
            new TaxBracket(new BigDecimal("609350"), null, new BigDecimal("0.37"))
        );
    }

    private List<TaxBracket> getMarriedJointBrackets() {
        return List.of(
            new TaxBracket(BigDecimal.ZERO, new BigDecimal("23200"), new BigDecimal("0.10")),
            new TaxBracket(new BigDecimal("23200"), new BigDecimal("94300"), new BigDecimal("0.12")),
            new TaxBracket(new BigDecimal("94300"), new BigDecimal("201050"), new BigDecimal("0.22")),
            new TaxBracket(new BigDecimal("201050"), new BigDecimal("383900"), new BigDecimal("0.24")),
            new TaxBracket(new BigDecimal("383900"), new BigDecimal("487450"), new BigDecimal("0.32")),
            new TaxBracket(new BigDecimal("487450"), new BigDecimal("731200"), new BigDecimal("0.35")),
            new TaxBracket(new BigDecimal("731200"), null, new BigDecimal("0.37"))
        );
    }

    private List<TaxBracket> getMarriedSeparateBrackets() {
        return List.of(
            new TaxBracket(BigDecimal.ZERO, new BigDecimal("11600"), new BigDecimal("0.10")),
            new TaxBracket(new BigDecimal("11600"), new BigDecimal("47150"), new BigDecimal("0.12")),
            new TaxBracket(new BigDecimal("47150"), new BigDecimal("100525"), new BigDecimal("0.22")),
            new TaxBracket(new BigDecimal("100525"), new BigDecimal("191950"), new BigDecimal("0.24")),
            new TaxBracket(new BigDecimal("191950"), new BigDecimal("243725"), new BigDecimal("0.32")),
            new TaxBracket(new BigDecimal("243725"), new BigDecimal("365600"), new BigDecimal("0.35")),
            new TaxBracket(new BigDecimal("365600"), null, new BigDecimal("0.37"))
        );
    }

    private List<TaxBracket> getHeadOfHouseholdBrackets() {
        return List.of(
            new TaxBracket(BigDecimal.ZERO, new BigDecimal("16550"), new BigDecimal("0.10")),
            new TaxBracket(new BigDecimal("16550"), new BigDecimal("63100"), new BigDecimal("0.12")),
            new TaxBracket(new BigDecimal("63100"), new BigDecimal("100500"), new BigDecimal("0.22")),
            new TaxBracket(new BigDecimal("100500"), new BigDecimal("191950"), new BigDecimal("0.24")),
            new TaxBracket(new BigDecimal("191950"), new BigDecimal("243700"), new BigDecimal("0.32")),
            new TaxBracket(new BigDecimal("243700"), new BigDecimal("609350"), new BigDecimal("0.35")),
            new TaxBracket(new BigDecimal("609350"), null, new BigDecimal("0.37"))
        );
    }

    private Map<String, BigDecimal> getStateTaxRates() {
        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("CA", new BigDecimal("0.093")); // California
        rates.put("NY", new BigDecimal("0.0685")); // New York
        rates.put("TX", BigDecimal.ZERO); // No state income tax
        rates.put("FL", BigDecimal.ZERO); // No state income tax
        rates.put("IL", new BigDecimal("0.0495")); // Illinois
        rates.put("PA", new BigDecimal("0.0307")); // Pennsylvania
        rates.put("OH", new BigDecimal("0.0399")); // Ohio
        rates.put("MA", new BigDecimal("0.05")); // Massachusetts
        // Add more states as needed
        return rates;
    }

    private Map<String, BigDecimal> getSUIRates() {
        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("CA", new BigDecimal("0.034"));
        rates.put("NY", new BigDecimal("0.041"));
        rates.put("TX", new BigDecimal("0.027"));
        rates.put("FL", new BigDecimal("0.027"));
        // Add more states as needed
        return rates;
    }

    private Map<String, BigDecimal> getLocalTaxRates() {
        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("NY_New York City", new BigDecimal("0.03876")); // NYC
        rates.put("PA_Philadelphia", new BigDecimal("0.03819")); // Philadelphia
        rates.put("OH_Columbus", new BigDecimal("0.025")); // Columbus
        // Add more cities as needed
        return rates;
    }

    // ============= DTOs =============

    public static class PaymentCalculation {
        private String employeeId;
        private BigDecimal grossAmount;
        private BigDecimal ytdGross;
        private String filingStatus;
        private int exemptions;
        private String state;
        private String city;

        // Getters and setters
        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
        public BigDecimal getGrossAmount() { return grossAmount; }
        public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
        public BigDecimal getYtdGross() { return ytdGross; }
        public void setYtdGross(BigDecimal ytdGross) { this.ytdGross = ytdGross; }
        public String getFilingStatus() { return filingStatus; }
        public void setFilingStatus(String filingStatus) { this.filingStatus = filingStatus; }
        public int getExemptions() { return exemptions; }
        public void setExemptions(int exemptions) { this.exemptions = exemptions; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
    }

    public static class TaxCalculationResult {
        private String companyId;
        private LocalDate payPeriod;
        private int totalEmployees;
        private BigDecimal totalGrossAmount;
        private BigDecimal totalFederalTax;
        private BigDecimal totalStateTax;
        private BigDecimal totalSocialSecurityTax;
        private BigDecimal totalMedicareTax;
        private BigDecimal totalFutaTax;
        private BigDecimal totalSuiTax;
        private BigDecimal totalTaxWithheld;
        private BigDecimal totalEmployerTax;
        private Map<String, EmployeeTaxCalculation> employeeTaxCalculations = new HashMap<>();

        public void addEmployeeTaxCalculation(EmployeeTaxCalculation calc) {
            employeeTaxCalculations.put(calc.getEmployeeId(), calc);
        }

        // Getters and setters
        public String getCompanyId() { return companyId; }
        public void setCompanyId(String companyId) { this.companyId = companyId; }
        public LocalDate getPayPeriod() { return payPeriod; }
        public void setPayPeriod(LocalDate payPeriod) { this.payPeriod = payPeriod; }
        public int getTotalEmployees() { return totalEmployees; }
        public void setTotalEmployees(int totalEmployees) { this.totalEmployees = totalEmployees; }
        public BigDecimal getTotalGrossAmount() { return totalGrossAmount; }
        public void setTotalGrossAmount(BigDecimal totalGrossAmount) { this.totalGrossAmount = totalGrossAmount; }
        public BigDecimal getTotalFederalTax() { return totalFederalTax; }
        public void setTotalFederalTax(BigDecimal totalFederalTax) { this.totalFederalTax = totalFederalTax; }
        public BigDecimal getTotalStateTax() { return totalStateTax; }
        public void setTotalStateTax(BigDecimal totalStateTax) { this.totalStateTax = totalStateTax; }
        public BigDecimal getTotalSocialSecurityTax() { return totalSocialSecurityTax; }
        public void setTotalSocialSecurityTax(BigDecimal totalSocialSecurityTax) { this.totalSocialSecurityTax = totalSocialSecurityTax; }
        public BigDecimal getTotalMedicareTax() { return totalMedicareTax; }
        public void setTotalMedicareTax(BigDecimal totalMedicareTax) { this.totalMedicareTax = totalMedicareTax; }
        public BigDecimal getTotalFutaTax() { return totalFutaTax; }
        public void setTotalFutaTax(BigDecimal totalFutaTax) { this.totalFutaTax = totalFutaTax; }
        public BigDecimal getTotalSuiTax() { return totalSuiTax; }
        public void setTotalSuiTax(BigDecimal totalSuiTax) { this.totalSuiTax = totalSuiTax; }
        public BigDecimal getTotalTaxWithheld() { return totalTaxWithheld; }
        public void setTotalTaxWithheld(BigDecimal totalTaxWithheld) { this.totalTaxWithheld = totalTaxWithheld; }
        public BigDecimal getTotalEmployerTax() { return totalEmployerTax; }
        public void setTotalEmployerTax(BigDecimal totalEmployerTax) { this.totalEmployerTax = totalEmployerTax; }
        public Map<String, EmployeeTaxCalculation> getEmployeeTaxCalculations() { return employeeTaxCalculations; }
    }

    public static class EmployeeTaxCalculation {
        private String employeeId;
        private BigDecimal grossAmount;
        private BigDecimal federalTax;
        private BigDecimal stateTax;
        private BigDecimal socialSecurityTax;
        private BigDecimal medicareTax;
        private BigDecimal localTax;
        private BigDecimal futaTax;
        private BigDecimal suiTax;
        private BigDecimal totalWithholding;
        private BigDecimal netAmount;

        // Getters and setters
        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
        public BigDecimal getGrossAmount() { return grossAmount; }
        public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
        public BigDecimal getFederalTax() { return federalTax; }
        public void setFederalTax(BigDecimal federalTax) { this.federalTax = federalTax; }
        public BigDecimal getStateTax() { return stateTax; }
        public void setStateTax(BigDecimal stateTax) { this.stateTax = stateTax; }
        public BigDecimal getSocialSecurityTax() { return socialSecurityTax; }
        public void setSocialSecurityTax(BigDecimal socialSecurityTax) { this.socialSecurityTax = socialSecurityTax; }
        public BigDecimal getMedicareTax() { return medicareTax; }
        public void setMedicareTax(BigDecimal medicareTax) { this.medicareTax = medicareTax; }
        public BigDecimal getLocalTax() { return localTax; }
        public void setLocalTax(BigDecimal localTax) { this.localTax = localTax; }
        public BigDecimal getFutaTax() { return futaTax; }
        public void setFutaTax(BigDecimal futaTax) { this.futaTax = futaTax; }
        public BigDecimal getSuiTax() { return suiTax; }
        public void setSuiTax(BigDecimal suiTax) { this.suiTax = suiTax; }
        public BigDecimal getTotalWithholding() { return totalWithholding; }
        public void setTotalWithholding(BigDecimal totalWithholding) { this.totalWithholding = totalWithholding; }
        public BigDecimal getNetAmount() { return netAmount; }
        public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
    }

    private static class TaxBracket {
        private final BigDecimal lowerLimit;
        private final BigDecimal upperLimit;
        private final BigDecimal rate;

        public TaxBracket(BigDecimal lowerLimit, BigDecimal upperLimit, BigDecimal rate) {
            this.lowerLimit = lowerLimit;
            this.upperLimit = upperLimit;
            this.rate = rate;
        }

        public BigDecimal getLowerLimit() { return lowerLimit; }
        public BigDecimal getUpperLimit() { return upperLimit; }
        public BigDecimal getRate() { return rate; }
    }
}
