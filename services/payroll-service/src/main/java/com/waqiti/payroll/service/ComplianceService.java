package com.waqiti.payroll.service;

import com.waqiti.payroll.exception.ComplianceViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enterprise Compliance Service
 * Enforces regulatory compliance for payroll processing:
 * - Fair Labor Standards Act (FLSA) - Minimum wage, overtime, child labor
 * - Equal Pay Act - Pay equity across gender, race
 * - Anti-Money Laundering (AML) - Large payment screening
 * - Worker Classification - Employee vs Contractor
 * - State Labor Laws - State-specific requirements
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ComplianceService {

    @Value("${payroll.compliance.minimum-wage:7.25}")
    private BigDecimal federalMinimumWage;

    @Value("${payroll.compliance.overtime-threshold-hours:40}")
    private int overtimeThresholdHours;

    @Value("${payroll.compliance.enable-flsa-checks:true}")
    private boolean enableFLSAChecks;

    @Value("${payroll.compliance.enable-equal-pay-checks:true}")
    private boolean enableEqualPayChecks;

    @Value("${payroll.compliance.enable-aml-screening:true}")
    private boolean enableAMLScreening;

    @Value("${payroll.compliance.aml-threshold:10000.00}")
    private BigDecimal amlThreshold;

    // State minimum wages (2025)
    private static final Map<String, BigDecimal> STATE_MINIMUM_WAGES = Map.ofEntries(
        Map.entry("CA", new BigDecimal("16.00")),  // California
        Map.entry("NY", new BigDecimal("15.00")),  // New York
        Map.entry("WA", new BigDecimal("16.28")),  // Washington
        Map.entry("MA", new BigDecimal("15.00")),  // Massachusetts
        Map.entry("CT", new BigDecimal("15.69")),  // Connecticut
        Map.entry("OR", new BigDecimal("14.20")),  // Oregon
        Map.entry("CO", new BigDecimal("14.42")),  // Colorado
        Map.entry("AZ", new BigDecimal("14.35"))   // Arizona
    );

    /**
     * Perform comprehensive payroll compliance checks
     */
    public ComplianceCheckResult performPayrollComplianceChecks(
            PayrollComplianceRequest request,
            List<EmployeePayrollCalculation> calculations,
            BigDecimal totalPayroll) {

        log.info("Performing compliance checks for company: {}, employees: {}, total: ${}",
                 request.getCompanyId(), calculations.size(), totalPayroll);

        ComplianceCheckResult result = new ComplianceCheckResult();
        result.setCompanyId(request.getCompanyId());
        result.setPayPeriod(request.getPayPeriod());
        result.setEmployeeCount(calculations.size());
        result.setTotalPayroll(totalPayroll);

        List<ComplianceViolation> violations = new ArrayList<>();

        // 1. FLSA Compliance (Fair Labor Standards Act)
        if (enableFLSAChecks) {
            violations.addAll(checkFLSACompliance(request, calculations));
        }

        // 2. Equal Pay Act Compliance
        if (enableEqualPayChecks) {
            violations.addAll(checkEqualPayCompliance(calculations));
        }

        // 3. AML Screening (Anti-Money Laundering)
        if (enableAMLScreening) {
            violations.addAll(performAMLScreening(request, calculations));
        }

        // 4. Worker Classification Compliance
        violations.addAll(checkWorkerClassification(request, calculations));

        // 5. State-Specific Labor Law Compliance
        violations.addAll(checkStateLaborLaws(request, calculations));

        // 6. Child Labor Law Compliance
        violations.addAll(checkChildLaborLaws(calculations));

        result.setViolations(violations);
        result.setViolationCount(violations.size());
        result.setCompliant(violations.isEmpty());

        if (!violations.isEmpty()) {
            log.warn("Compliance violations detected for company {}: {} violations",
                     request.getCompanyId(), violations.size());
            result.getSummary().putAll(summarizeViolations(violations));
        } else {
            log.info("All compliance checks passed for company: {}", request.getCompanyId());
        }

        return result;
    }

    /**
     * Check FLSA (Fair Labor Standards Act) compliance
     * - Minimum wage requirements
     * - Overtime pay (1.5x for hours > 40/week)
     * - Recordkeeping requirements
     */
    private List<ComplianceViolation> checkFLSACompliance(
            PayrollComplianceRequest request,
            List<EmployeePayrollCalculation> calculations) {

        List<ComplianceViolation> violations = new ArrayList<>();

        for (EmployeePayrollCalculation calc : calculations) {
            // Skip exempt employees (salaried, executive, professional)
            if (calc.isExemptFromOvertime()) {
                continue;
            }

            // 1. Minimum Wage Check
            if (!isMinimumWageCompliant(calc, request.getPayPeriod())) {
                BigDecimal applicableMinWage = getApplicableMinimumWage(calc.getState());
                BigDecimal actualHourlyRate = calc.getHourlyRate();

                violations.add(new ComplianceViolation(
                    ViolationType.MINIMUM_WAGE,
                    ViolationSeverity.CRITICAL,
                    "Employee " + calc.getEmployeeId() + " hourly rate ($" + actualHourlyRate +
                    ") is below minimum wage ($" + applicableMinWage + ")",
                    calc.getEmployeeId(),
                    "FLSA Section 6"
                ));
            }

            // 2. Overtime Pay Check
            if (calc.getHoursWorked() > overtimeThresholdHours) {
                if (!isOvertimeCompliant(calc)) {
                    violations.add(new ComplianceViolation(
                        ViolationType.OVERTIME,
                        ViolationSeverity.CRITICAL,
                        "Employee " + calc.getEmployeeId() + " worked " + calc.getHoursWorked() +
                        " hours but overtime not properly calculated",
                        calc.getEmployeeId(),
                        "FLSA Section 7"
                    ));
                }
            }

            // 3. Recordkeeping (must retain for 3 years)
            // This would check if proper records are maintained
        }

        return violations;
    }

    /**
     * Check minimum wage compliance
     */
    public boolean isMinimumWageCompliant(EmployeePayrollCalculation calc, LocalDate payPeriod) {
        BigDecimal applicableMinWage = getApplicableMinimumWage(calc.getState());
        BigDecimal actualHourlyRate = calc.getHourlyRate();

        return actualHourlyRate.compareTo(applicableMinWage) >= 0;
    }

    /**
     * Get applicable minimum wage (federal or state, whichever is higher)
     */
    private BigDecimal getApplicableMinimumWage(String state) {
        BigDecimal stateMinWage = STATE_MINIMUM_WAGES.getOrDefault(state, federalMinimumWage);
        return stateMinWage.max(federalMinimumWage);
    }

    /**
     * Check overtime compliance (1.5x pay for hours > 40/week)
     */
    public boolean isOvertimeCompliant(EmployeePayrollCalculation calc) {
        if (calc.getHoursWorked() <= overtimeThresholdHours) {
            return true;
        }

        BigDecimal regularHours = new BigDecimal(overtimeThresholdHours);
        BigDecimal overtimeHours = calc.getHoursWorked().subtract(regularHours);
        BigDecimal expectedOvertimePay = overtimeHours.multiply(calc.getHourlyRate())
                                                       .multiply(new BigDecimal("1.5"));

        BigDecimal actualOvertimePay = calc.getOvertimePay();

        // Allow 1 cent tolerance for rounding
        return actualOvertimePay.subtract(expectedOvertimePay).abs()
                                .compareTo(new BigDecimal("0.01")) <= 0;
    }

    /**
     * Check Equal Pay Act compliance
     * Detect potential pay discrimination based on protected characteristics
     */
    private List<ComplianceViolation> checkEqualPayCompliance(List<EmployeePayrollCalculation> calculations) {
        List<ComplianceViolation> violations = new ArrayList<>();

        // Group employees by job title/role
        Map<String, List<EmployeePayrollCalculation>> byJobTitle = new HashMap<>();
        for (EmployeePayrollCalculation calc : calculations) {
            byJobTitle.computeIfAbsent(calc.getJobTitle(), k -> new ArrayList<>()).add(calc);
        }

        // Analyze pay equity within each job title
        for (Map.Entry<String, List<EmployeePayrollCalculation>> entry : byJobTitle.entrySet()) {
            String jobTitle = entry.getKey();
            List<EmployeePayrollCalculation> employees = entry.getValue();

            if (employees.size() < 2) {
                continue; // Need at least 2 employees to compare
            }

            // Calculate pay statistics
            BigDecimal avgPay = employees.stream()
                .map(EmployeePayrollCalculation::getBasePay)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(employees.size()), 2, RoundingMode.HALF_UP);

            BigDecimal maxPay = employees.stream()
                .map(EmployeePayrollCalculation::getBasePay)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

            BigDecimal minPay = employees.stream()
                .map(EmployeePayrollCalculation::getBasePay)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

            // Flag if pay disparity > 20% (potential discrimination indicator)
            if (maxPay.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal disparity = maxPay.subtract(minPay).divide(maxPay, 4, RoundingMode.HALF_UP);

                if (disparity.compareTo(new BigDecimal("0.20")) > 0) {
                    violations.add(new ComplianceViolation(
                        ViolationType.PAY_EQUITY,
                        ViolationSeverity.WARNING,
                        "Significant pay disparity detected for job title: " + jobTitle +
                        " (Max: $" + maxPay + ", Min: $" + minPay + ", Disparity: " +
                        disparity.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP) + "%)",
                        null,
                        "Equal Pay Act of 1963"
                    ));
                }
            }
        }

        return violations;
    }

    /**
     * Perform AML (Anti-Money Laundering) screening
     * Flag large or suspicious payments
     */
    private List<ComplianceViolation> performAMLScreening(
            PayrollComplianceRequest request,
            List<EmployeePayrollCalculation> calculations) {

        List<ComplianceViolation> violations = new ArrayList<>();

        for (EmployeePayrollCalculation calc : calculations) {
            // 1. Large payment screening (>$10,000 requires CTR - Currency Transaction Report)
            if (calc.getNetPay().compareTo(amlThreshold) > 0) {
                violations.add(new ComplianceViolation(
                    ViolationType.AML_LARGE_PAYMENT,
                    ViolationSeverity.INFO,
                    "Large payment detected: Employee " + calc.getEmployeeId() +
                    " net pay $" + calc.getNetPay() + " exceeds AML threshold ($" + amlThreshold + ")",
                    calc.getEmployeeId(),
                    "Bank Secrecy Act (BSA)"
                ));
            }

            // 2. Unusual payment pattern detection
            if (calc.getNetPay().compareTo(calc.getTypicalPay().multiply(new BigDecimal("3"))) > 0) {
                violations.add(new ComplianceViolation(
                    ViolationType.AML_UNUSUAL_PATTERN,
                    ViolationSeverity.WARNING,
                    "Unusual payment pattern: Employee " + calc.getEmployeeId() +
                    " payment ($" + calc.getNetPay() + ") is 3x typical pay ($" + calc.getTypicalPay() + ")",
                    calc.getEmployeeId(),
                    "FinCEN Guidance"
                ));
            }

            // 3. Structuring detection (multiple payments just under threshold)
            // This would analyze historical patterns to detect deliberate structuring
        }

        return violations;
    }

    /**
     * Check worker classification compliance (Employee vs Independent Contractor)
     */
    private List<ComplianceViolation> checkWorkerClassification(
            PayrollComplianceRequest request,
            List<EmployeePayrollCalculation> calculations) {

        List<ComplianceViolation> violations = new ArrayList<>();

        for (EmployeePayrollCalculation calc : calculations) {
            // Check for misclassification red flags
            if (calc.isContractor() && hasEmployeeCharacteristics(calc)) {
                violations.add(new ComplianceViolation(
                    ViolationType.WORKER_CLASSIFICATION,
                    ViolationSeverity.HIGH,
                    "Potential worker misclassification: Employee " + calc.getEmployeeId() +
                    " classified as contractor but exhibits employee characteristics",
                    calc.getEmployeeId(),
                    "IRS Publication 15-A"
                ));
            }
        }

        return violations;
    }

    /**
     * Determine if a worker classified as contractor has employee characteristics
     */
    private boolean hasEmployeeCharacteristics(EmployeePayrollCalculation calc) {
        // IRS uses "Common Law Rules" to determine worker status
        // Factors: Behavioral control, financial control, relationship type

        int employeeIndicators = 0;

        // 1. Works full-time hours (40+ hours/week)
        if (calc.getHoursWorked().compareTo(new BigDecimal("40")) >= 0) {
            employeeIndicators++;
        }

        // 2. Receives benefits (contractors typically don't)
        if (calc.hasHealthBenefits() || calc.has401k()) {
            employeeIndicators++;
        }

        // 3. Long-term relationship (>6 months)
        if (calc.getTenureMonths() > 6) {
            employeeIndicators++;
        }

        // If 2+ indicators, likely misclassified
        return employeeIndicators >= 2;
    }

    /**
     * Check state-specific labor law compliance
     */
    private List<ComplianceViolation> checkStateLaborLaws(
            PayrollComplianceRequest request,
            List<EmployeePayrollCalculation> calculations) {

        List<ComplianceViolation> violations = new ArrayList<>();

        for (EmployeePayrollCalculation calc : calculations) {
            String state = calc.getState();

            // California-specific laws
            if ("CA".equals(state)) {
                // CA requires meal breaks for 5+ hour shifts
                if (calc.getHoursWorked().compareTo(new BigDecimal("5")) > 0 && !calc.hadMealBreak()) {
                    violations.add(new ComplianceViolation(
                        ViolationType.STATE_LABOR_LAW,
                        ViolationSeverity.HIGH,
                        "California meal break violation: Employee " + calc.getEmployeeId() +
                        " worked " + calc.getHoursWorked() + " hours without meal break",
                        calc.getEmployeeId(),
                        "California Labor Code Section 512"
                    ));
                }

                // CA daily overtime (>8 hours/day at 1.5x, >12 hours/day at 2x)
                if (calc.getDailyHours() != null && calc.getDailyHours().compareTo(new BigDecimal("8")) > 0) {
                    // Check if daily overtime calculated correctly
                }
            }

            // New York-specific laws
            if ("NY".equals(state)) {
                // NY requires spread-of-hours pay for shifts > 10 hours
                if (calc.getShiftDuration() != null && calc.getShiftDuration() > 10) {
                    // Check spread-of-hours compensation
                }
            }
        }

        return violations;
    }

    /**
     * Check child labor law compliance
     */
    private List<ComplianceViolation> checkChildLaborLaws(List<EmployeePayrollCalculation> calculations) {
        List<ComplianceViolation> violations = new ArrayList<>();

        for (EmployeePayrollCalculation calc : calculations) {
            int age = calc.getEmployeeAge();

            // Under 14: Generally cannot work (few exceptions)
            if (age < 14) {
                violations.add(new ComplianceViolation(
                    ViolationType.CHILD_LABOR,
                    ViolationSeverity.CRITICAL,
                    "Child labor violation: Employee " + calc.getEmployeeId() +
                    " is under 14 years old (age: " + age + ")",
                    calc.getEmployeeId(),
                    "FLSA Section 12"
                ));
            }

            // Age 14-15: Limited hours (3 hours/day school days, 8 hours/day non-school days)
            if (age >= 14 && age <= 15) {
                if (calc.getHoursWorked().compareTo(new BigDecimal("8")) > 0) {
                    violations.add(new ComplianceViolation(
                        ViolationType.CHILD_LABOR,
                        ViolationSeverity.HIGH,
                        "Child labor hour violation: Employee " + calc.getEmployeeId() +
                        " (age " + age + ") worked " + calc.getHoursWorked() + " hours (max 8)",
                        calc.getEmployeeId(),
                        "FLSA Section 12"
                    ));
                }
            }

            // Age 16-17: No hour restrictions, but hazardous occupation restrictions apply
            if (age >= 16 && age < 18 && calc.hasHazardousOccupation()) {
                violations.add(new ComplianceViolation(
                    ViolationType.CHILD_LABOR,
                    ViolationSeverity.CRITICAL,
                    "Minor in hazardous occupation: Employee " + calc.getEmployeeId() +
                    " (age " + age + ") assigned to hazardous occupation",
                    calc.getEmployeeId(),
                    "FLSA Section 12"
                ));
            }
        }

        return violations;
    }

    /**
     * Check if company is compliant overall
     */
    public boolean isCompanyCompliant(String companyId) {
        // TODO: Check company-level compliance
        // - Tax registration status
        // - Workers' compensation insurance
        // - Unemployment insurance registration
        // - OSHA compliance
        // - I-9 employment verification
        return true;
    }

    /**
     * Summarize violations by type
     */
    private Map<String, Integer> summarizeViolations(List<ComplianceViolation> violations) {
        Map<String, Integer> summary = new HashMap<>();
        for (ComplianceViolation violation : violations) {
            String type = violation.getType().toString();
            summary.put(type, summary.getOrDefault(type, 0) + 1);
        }
        return summary;
    }

    // ============= DTOs =============

    public static class PayrollComplianceRequest {
        private String companyId;
        private LocalDate payPeriod;
        private String state;

        public String getCompanyId() { return companyId; }
        public void setCompanyId(String companyId) { this.companyId = companyId; }
        public LocalDate getPayPeriod() { return payPeriod; }
        public void setPayPeriod(LocalDate payPeriod) { this.payPeriod = payPeriod; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
    }

    public static class EmployeePayrollCalculation {
        private String employeeId;
        private String state;
        private String jobTitle;
        private BigDecimal basePay;
        private BigDecimal netPay;
        private BigDecimal typicalPay;
        private BigDecimal hourlyRate;
        private BigDecimal hoursWorked;
        private BigDecimal dailyHours;
        private BigDecimal overtimePay;
        private Integer shiftDuration;
        private boolean exemptFromOvertime;
        private boolean isContractor;
        private boolean hasHealthBenefits;
        private boolean has401k;
        private int tenureMonths;
        private int employeeAge;
        private boolean hadMealBreak;
        private boolean hasHazardousOccupation;

        // Getters and Setters
        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getJobTitle() { return jobTitle; }
        public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
        public BigDecimal getBasePay() { return basePay; }
        public void setBasePay(BigDecimal basePay) { this.basePay = basePay; }
        public BigDecimal getNetPay() { return netPay; }
        public void setNetPay(BigDecimal netPay) { this.netPay = netPay; }
        public BigDecimal getTypicalPay() { return typicalPay; }
        public void setTypicalPay(BigDecimal typicalPay) { this.typicalPay = typicalPay; }
        public BigDecimal getHourlyRate() { return hourlyRate; }
        public void setHourlyRate(BigDecimal hourlyRate) { this.hourlyRate = hourlyRate; }
        public BigDecimal getHoursWorked() { return hoursWorked; }
        public void setHoursWorked(BigDecimal hoursWorked) { this.hoursWorked = hoursWorked; }
        public BigDecimal getDailyHours() { return dailyHours; }
        public void setDailyHours(BigDecimal dailyHours) { this.dailyHours = dailyHours; }
        public BigDecimal getOvertimePay() { return overtimePay; }
        public void setOvertimePay(BigDecimal overtimePay) { this.overtimePay = overtimePay; }
        public Integer getShiftDuration() { return shiftDuration; }
        public void setShiftDuration(Integer shiftDuration) { this.shiftDuration = shiftDuration; }
        public boolean isExemptFromOvertime() { return exemptFromOvertime; }
        public void setExemptFromOvertime(boolean exemptFromOvertime) { this.exemptFromOvertime = exemptFromOvertime; }
        public boolean isContractor() { return isContractor; }
        public void setContractor(boolean contractor) { isContractor = contractor; }
        public boolean hasHealthBenefits() { return hasHealthBenefits; }
        public void setHasHealthBenefits(boolean hasHealthBenefits) { this.hasHealthBenefits = hasHealthBenefits; }
        public boolean has401k() { return has401k; }
        public void setHas401k(boolean has401k) { this.has401k = has401k; }
        public int getTenureMonths() { return tenureMonths; }
        public void setTenureMonths(int tenureMonths) { this.tenureMonths = tenureMonths; }
        public int getEmployeeAge() { return employeeAge; }
        public void setEmployeeAge(int employeeAge) { this.employeeAge = employeeAge; }
        public boolean hadMealBreak() { return hadMealBreak; }
        public void setHadMealBreak(boolean hadMealBreak) { this.hadMealBreak = hadMealBreak; }
        public boolean hasHazardousOccupation() { return hasHazardousOccupation; }
        public void setHasHazardousOccupation(boolean hasHazardousOccupation) { this.hasHazardousOccupation = hasHazardousOccupation; }
    }

    public static class ComplianceCheckResult {
        private String companyId;
        private LocalDate payPeriod;
        private int employeeCount;
        private BigDecimal totalPayroll;
        private boolean compliant;
        private int violationCount;
        private List<ComplianceViolation> violations;
        private Map<String, Integer> summary = new HashMap<>();

        // Getters and Setters
        public String getCompanyId() { return companyId; }
        public void setCompanyId(String companyId) { this.companyId = companyId; }
        public LocalDate getPayPeriod() { return payPeriod; }
        public void setPayPeriod(LocalDate payPeriod) { this.payPeriod = payPeriod; }
        public int getEmployeeCount() { return employeeCount; }
        public void setEmployeeCount(int employeeCount) { this.employeeCount = employeeCount; }
        public BigDecimal getTotalPayroll() { return totalPayroll; }
        public void setTotalPayroll(BigDecimal totalPayroll) { this.totalPayroll = totalPayroll; }
        public boolean isCompliant() { return compliant; }
        public void setCompliant(boolean compliant) { this.compliant = compliant; }
        public int getViolationCount() { return violationCount; }
        public void setViolationCount(int violationCount) { this.violationCount = violationCount; }
        public List<ComplianceViolation> getViolations() { return violations; }
        public void setViolations(List<ComplianceViolation> violations) { this.violations = violations; }
        public Map<String, Integer> getSummary() { return summary; }
    }

    public static class ComplianceViolation {
        private ViolationType type;
        private ViolationSeverity severity;
        private String description;
        private String employeeId;
        private String regulation;

        public ComplianceViolation(ViolationType type, ViolationSeverity severity,
                                  String description, String employeeId, String regulation) {
            this.type = type;
            this.severity = severity;
            this.description = description;
            this.employeeId = employeeId;
            this.regulation = regulation;
        }

        // Getters
        public ViolationType getType() { return type; }
        public ViolationSeverity getSeverity() { return severity; }
        public String getDescription() { return description; }
        public String getEmployeeId() { return employeeId; }
        public String getRegulation() { return regulation; }
    }

    public enum ViolationType {
        MINIMUM_WAGE,
        OVERTIME,
        PAY_EQUITY,
        AML_LARGE_PAYMENT,
        AML_UNUSUAL_PATTERN,
        WORKER_CLASSIFICATION,
        STATE_LABOR_LAW,
        CHILD_LABOR,
        TAX_WITHHOLDING,
        RECORDKEEPING
    }

    public enum ViolationSeverity {
        INFO,        // Informational only
        WARNING,     // Potential issue, review recommended
        HIGH,        // Serious violation, must address
        CRITICAL     // Critical violation, halt processing
    }
}
