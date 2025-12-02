package com.waqiti.payroll.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validation Service for Payroll Data
 * Validates employee information, bank accounts, SSN, and payroll data integrity
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ValidationService {

    private final BankTransferService bankTransferService;

    // SSN validation pattern (XXX-XX-XXXX)
    private static final Pattern SSN_PATTERN = Pattern.compile("^\\d{3}-\\d{2}-\\d{4}$");

    // EIN validation pattern (XX-XXXXXXX)
    private static final Pattern EIN_PATTERN = Pattern.compile("^\\d{2}-\\d{7}$");

    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    // Phone validation pattern (supports various formats)
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?1?\\s*\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}$");

    /**
     * Validate employee data for payroll processing
     */
    public EmployeeValidationResult validateEmployeeData(String companyId, List<EmployeePayrollData> employees) {
        log.info("Validating {} employees for company: {}", employees.size(), companyId);

        EmployeeValidationResult result = new EmployeeValidationResult();
        result.setCompanyId(companyId);
        result.setTotalEmployees(employees.size());

        List<ValidationError> errors = new ArrayList<>();
        List<String> validEmployeeIds = new ArrayList<>();

        for (EmployeePayrollData employee : employees) {
            List<ValidationError> employeeErrors = validateSingleEmployee(employee);

            if (employeeErrors.isEmpty()) {
                validEmployeeIds.add(employee.getEmployeeId());
            } else {
                errors.addAll(employeeErrors);
            }
        }

        result.setValidEmployeeIds(validEmployeeIds);
        result.setValidEmployeeCount(validEmployeeIds.size());
        result.setErrors(errors);
        result.setErrorCount(errors.size());
        result.setValid(errors.isEmpty());

        if (!errors.isEmpty()) {
            log.warn("Validation failed for {} employees in company {}", errors.size(), companyId);
        } else {
            log.info("All {} employees validated successfully for company {}", employees.size(), companyId);
        }

        return result;
    }

    /**
     * Validate single employee data
     */
    private List<ValidationError> validateSingleEmployee(EmployeePayrollData employee) {
        List<ValidationError> errors = new ArrayList<>();

        // 1. Employee ID validation
        if (employee.getEmployeeId() == null || employee.getEmployeeId().trim().isEmpty()) {
            errors.add(new ValidationError(
                employee.getEmployeeId(),
                ValidationErrorType.MISSING_FIELD,
                "Employee ID is required"
            ));
        }

        // 2. Personal information validation
        if (employee.getFirstName() == null || employee.getFirstName().trim().isEmpty()) {
            errors.add(new ValidationError(
                employee.getEmployeeId(),
                ValidationErrorType.MISSING_FIELD,
                "First name is required"
            ));
        }

        if (employee.getLastName() == null || employee.getLastName().trim().isEmpty()) {
            errors.add(new ValidationError(
                employee.getEmployeeId(),
                ValidationErrorType.MISSING_FIELD,
                "Last name is required"
            ));
        }

        // 3. SSN validation
        if (!isValidSSN(employee.getSsn())) {
            errors.add(new ValidationError(
                employee.getEmployeeId(),
                ValidationErrorType.INVALID_SSN,
                "Invalid SSN format. Expected: XXX-XX-XXXX"
            ));
        }

        // 4. Email validation
        if (employee.getEmail() != null && !isValidEmail(employee.getEmail())) {
            errors.add(new ValidationError(
                employee.getEmployeeId(),
                ValidationErrorType.INVALID_EMAIL,
                "Invalid email format: " + employee.getEmail()
            ));
        }

        // 5. Phone validation
        if (employee.getPhone() != null && !isValidPhone(employee.getPhone())) {
            errors.add(new ValidationError(
                employee.getEmployeeId(),
                ValidationErrorType.INVALID_PHONE,
                "Invalid phone number format: " + employee.getPhone()
            ));
        }

        // 6. Date of birth validation
        if (employee.getDateOfBirth() == null) {
            errors.add(new ValidationError(
                employee.getEmployeeId(),
                ValidationErrorType.MISSING_FIELD,
                "Date of birth is required"
            ));
        } else if (employee.getDateOfBirth().isAfter(LocalDate.now())) {
            errors.add(new ValidationError(
                employee.getEmployeeId(),
                ValidationErrorType.INVALID_DATE,
                "Date of birth cannot be in the future"
            ));
        } else {
            // Check minimum age (typically 14 for work)
            int age = LocalDate.now().getYear() - employee.getDateOfBirth().getYear();
            if (age < 14) {
                errors.add(new ValidationError(
                    employee.getEmployeeId(),
                    ValidationErrorType.AGE_RESTRICTION,
                    "Employee must be at least 14 years old (current age: " + age + ")"
                ));
            }
        }

        // 7. Address validation
        if (employee.getAddress() == null || employee.getAddress().trim().isEmpty()) {
            errors.add(new ValidationError(
                employee.getEmployeeId(),
                ValidationErrorType.MISSING_FIELD,
                "Address is required"
            ));
        }

        if (employee.getCity() == null || employee.getCity().trim().isEmpty()) {
            errors.add(new ValidationError(
                employee.getEmployeeId(),
                ValidationErrorType.MISSING_FIELD,
                "City is required"
            ));
        }

        if (employee.getState() == null || employee.getState().trim().isEmpty()) {
            errors.add(new ValidationError(
                employee.getEmployeeId(),
                ValidationErrorType.MISSING_FIELD,
                "State is required"
            ));
        }

        if (employee.getZipCode() == null || !isValidZipCode(employee.getZipCode())) {
            errors.add(new ValidationError(
                employee.getEmployeeId(),
                ValidationErrorType.INVALID_ZIP,
                "Invalid ZIP code format"
            ));
        }

        // 8. Employment status validation
        if (employee.getEmploymentStatus() == null) {
            errors.add(new ValidationError(
                employee.getEmployeeId(),
                ValidationErrorType.MISSING_FIELD,
                "Employment status is required"
            ));
        }

        // 9. Bank account validation
        if (employee.getRoutingNumber() != null || employee.getAccountNumber() != null) {
            if (!isEmployeeBankAccountValid(employee.getEmployeeId(),
                                            employee.getRoutingNumber(),
                                            employee.getAccountNumber())) {
                errors.add(new ValidationError(
                    employee.getEmployeeId(),
                    ValidationErrorType.INVALID_BANK_ACCOUNT,
                    "Invalid bank account information"
                ));
            }
        }

        // 10. Salary/wage validation
        if (employee.getSalary() == null || employee.getSalary().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(new ValidationError(
                employee.getEmployeeId(),
                ValidationErrorType.INVALID_AMOUNT,
                "Salary/wage must be greater than zero"
            ));
        }

        return errors;
    }

    /**
     * Validate SSN format
     */
    public boolean isValidSSN(String ssn) {
        if (ssn == null || ssn.trim().isEmpty()) {
            return false;
        }

        // Check format XXX-XX-XXXX
        if (!SSN_PATTERN.matcher(ssn).matches()) {
            return false;
        }

        // Additional SSN validity checks
        String[] parts = ssn.split("-");
        String area = parts[0];
        String group = parts[1];
        String serial = parts[2];

        // Area number cannot be 000, 666, or 900-999
        int areaNum = Integer.parseInt(area);
        if (areaNum == 0 || areaNum == 666 || areaNum >= 900) {
            return false;
        }

        // Group number cannot be 00
        if ("00".equals(group)) {
            return false;
        }

        // Serial number cannot be 0000
        if ("0000".equals(serial)) {
            return false;
        }

        return true;
    }

    /**
     * Validate EIN (Employer Identification Number) format
     */
    public boolean isValidEIN(String ein) {
        if (ein == null || ein.trim().isEmpty()) {
            return false;
        }

        return EIN_PATTERN.matcher(ein).matches();
    }

    /**
     * Validate email format
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Validate phone number format
     */
    public boolean isValidPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }

        return PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * Validate ZIP code
     */
    public boolean isValidZipCode(String zipCode) {
        if (zipCode == null || zipCode.trim().isEmpty()) {
            return false;
        }

        // Support both 5-digit and 9-digit ZIP codes
        return zipCode.matches("^\\d{5}(-\\d{4})?$");
    }

    /**
     * Validate employee bank account
     */
    public boolean isEmployeeBankAccountValid(String employeeId, String routingNumber, String accountNumber) {
        log.debug("Validating bank account for employee: {}", employeeId);

        // 1. Routing number validation
        if (!bankTransferService.isValidRoutingNumber(routingNumber)) {
            log.warn("Invalid routing number for employee {}: {}", employeeId, routingNumber);
            return false;
        }

        // 2. Account number validation
        if (!bankTransferService.isValidBankAccount(accountNumber)) {
            log.warn("Invalid account number for employee {}", employeeId);
            return false;
        }

        // 3. Additional checks could include:
        // - Verify account is active (would require bank integration)
        // - Verify account can receive ACH credits
        // - Check for duplicate accounts across employees

        return true;
    }

    /**
     * Check for terminated employees
     */
    public List<String> checkTerminatedEmployees(String companyId, List<String> employeeIds) {
        log.info("Checking terminated status for {} employees in company {}", employeeIds.size(), companyId);

        List<String> terminatedEmployees = new ArrayList<>();

        // TODO: Integration with employee-service or HR system
        // This would query employee records to check termination status

        // For each employee, check:
        // - Termination date
        // - Employment status (ACTIVE, TERMINATED, ON_LEAVE, etc.)
        // - Final paycheck processed flag

        // Placeholder implementation
        for (String employeeId : employeeIds) {
            // Check if employee is terminated
            // if (isEmployeeTerminated(employeeId)) {
            //     terminatedEmployees.add(employeeId);
            // }
        }

        if (!terminatedEmployees.isEmpty()) {
            log.warn("Found {} terminated employees in payroll batch: {}",
                     terminatedEmployees.size(), terminatedEmployees);
        }

        return terminatedEmployees;
    }

    /**
     * Validate payroll batch data
     */
    public PayrollBatchValidationResult validatePayrollBatch(PayrollBatchData batchData) {
        log.info("Validating payroll batch: {}", batchData.getBatchId());

        PayrollBatchValidationResult result = new PayrollBatchValidationResult();
        result.setBatchId(batchData.getBatchId());

        List<ValidationError> errors = new ArrayList<>();

        // 1. Batch ID validation
        if (batchData.getBatchId() == null || batchData.getBatchId().trim().isEmpty()) {
            errors.add(new ValidationError(
                null,
                ValidationErrorType.MISSING_FIELD,
                "Batch ID is required"
            ));
        }

        // 2. Company ID validation
        if (batchData.getCompanyId() == null || batchData.getCompanyId().trim().isEmpty()) {
            errors.add(new ValidationError(
                null,
                ValidationErrorType.MISSING_FIELD,
                "Company ID is required"
            ));
        }

        // 3. Pay period validation
        if (batchData.getPayPeriod() == null) {
            errors.add(new ValidationError(
                null,
                ValidationErrorType.MISSING_FIELD,
                "Pay period is required"
            ));
        } else if (batchData.getPayPeriod().isAfter(LocalDate.now())) {
            errors.add(new ValidationError(
                null,
                ValidationErrorType.INVALID_DATE,
                "Pay period cannot be in the future"
            ));
        }

        // 4. Employee count validation
        if (batchData.getEmployees() == null || batchData.getEmployees().isEmpty()) {
            errors.add(new ValidationError(
                null,
                ValidationErrorType.EMPTY_BATCH,
                "Payroll batch must contain at least one employee"
            ));
        }

        // 5. Total amount validation
        if (batchData.getTotalAmount() == null || batchData.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(new ValidationError(
                null,
                ValidationErrorType.INVALID_AMOUNT,
                "Total payroll amount must be greater than zero"
            ));
        }

        // 6. Check for duplicate employee IDs in batch
        List<String> employeeIds = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();

        if (batchData.getEmployees() != null) {
            for (EmployeePayrollData emp : batchData.getEmployees()) {
                if (employeeIds.contains(emp.getEmployeeId())) {
                    duplicates.add(emp.getEmployeeId());
                } else {
                    employeeIds.add(emp.getEmployeeId());
                }
            }
        }

        if (!duplicates.isEmpty()) {
            errors.add(new ValidationError(
                null,
                ValidationErrorType.DUPLICATE_ENTRY,
                "Duplicate employee IDs found in batch: " + String.join(", ", duplicates)
            ));
        }

        result.setErrors(errors);
        result.setValid(errors.isEmpty());

        return result;
    }

    /**
     * Validate tax withholding information
     */
    public boolean isValidTaxWithholding(String employeeId, TaxWithholdingData taxData) {
        log.debug("Validating tax withholding for employee: {}", employeeId);

        // 1. Filing status validation
        if (taxData.getFilingStatus() == null) {
            return false;
        }

        // 2. Exemptions validation (cannot be negative)
        if (taxData.getExemptions() < 0) {
            return false;
        }

        // 3. Additional withholding validation (cannot be negative)
        if (taxData.getAdditionalWithholding() != null &&
            taxData.getAdditionalWithholding().compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }

        // 4. W-4 form completion validation
        if (taxData.getW4FormDate() == null) {
            log.warn("Employee {} missing W-4 form date", employeeId);
            return false;
        }

        // W-4 form must be dated within the current or previous year
        int currentYear = LocalDate.now().getYear();
        int formYear = taxData.getW4FormDate().getYear();
        if (formYear < currentYear - 1) {
            log.warn("Employee {} has outdated W-4 form ({})", employeeId, formYear);
            return false;
        }

        return true;
    }

    // ============= DTOs =============

    public static class EmployeePayrollData {
        private String employeeId;
        private String firstName;
        private String lastName;
        private String ssn;
        private String email;
        private String phone;
        private LocalDate dateOfBirth;
        private String address;
        private String city;
        private String state;
        private String zipCode;
        private String employmentStatus;
        private String routingNumber;
        private String accountNumber;
        private BigDecimal salary;

        // Getters and Setters
        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getSsn() { return ssn; }
        public void setSsn(String ssn) { this.ssn = ssn; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public LocalDate getDateOfBirth() { return dateOfBirth; }
        public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getZipCode() { return zipCode; }
        public void setZipCode(String zipCode) { this.zipCode = zipCode; }
        public String getEmploymentStatus() { return employmentStatus; }
        public void setEmploymentStatus(String employmentStatus) { this.employmentStatus = employmentStatus; }
        public String getRoutingNumber() { return routingNumber; }
        public void setRoutingNumber(String routingNumber) { this.routingNumber = routingNumber; }
        public String getAccountNumber() { return accountNumber; }
        public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
        public BigDecimal getSalary() { return salary; }
        public void setSalary(BigDecimal salary) { this.salary = salary; }
    }

    public static class PayrollBatchData {
        private String batchId;
        private String companyId;
        private LocalDate payPeriod;
        private List<EmployeePayrollData> employees;
        private BigDecimal totalAmount;

        // Getters and Setters
        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        public String getCompanyId() { return companyId; }
        public void setCompanyId(String companyId) { this.companyId = companyId; }
        public LocalDate getPayPeriod() { return payPeriod; }
        public void setPayPeriod(LocalDate payPeriod) { this.payPeriod = payPeriod; }
        public List<EmployeePayrollData> getEmployees() { return employees; }
        public void setEmployees(List<EmployeePayrollData> employees) { this.employees = employees; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    }

    public static class TaxWithholdingData {
        private String filingStatus;
        private int exemptions;
        private BigDecimal additionalWithholding;
        private LocalDate w4FormDate;

        // Getters and Setters
        public String getFilingStatus() { return filingStatus; }
        public void setFilingStatus(String filingStatus) { this.filingStatus = filingStatus; }
        public int getExemptions() { return exemptions; }
        public void setExemptions(int exemptions) { this.exemptions = exemptions; }
        public BigDecimal getAdditionalWithholding() { return additionalWithholding; }
        public void setAdditionalWithholding(BigDecimal additionalWithholding) { this.additionalWithholding = additionalWithholding; }
        public LocalDate getW4FormDate() { return w4FormDate; }
        public void setW4FormDate(LocalDate w4FormDate) { this.w4FormDate = w4FormDate; }
    }

    public static class EmployeeValidationResult {
        private String companyId;
        private int totalEmployees;
        private int validEmployeeCount;
        private int errorCount;
        private boolean valid;
        private List<String> validEmployeeIds;
        private List<ValidationError> errors;

        // Getters and Setters
        public String getCompanyId() { return companyId; }
        public void setCompanyId(String companyId) { this.companyId = companyId; }
        public int getTotalEmployees() { return totalEmployees; }
        public void setTotalEmployees(int totalEmployees) { this.totalEmployees = totalEmployees; }
        public int getValidEmployeeCount() { return validEmployeeCount; }
        public void setValidEmployeeCount(int validEmployeeCount) { this.validEmployeeCount = validEmployeeCount; }
        public int getErrorCount() { return errorCount; }
        public void setErrorCount(int errorCount) { this.errorCount = errorCount; }
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public List<String> getValidEmployeeIds() { return validEmployeeIds; }
        public void setValidEmployeeIds(List<String> validEmployeeIds) { this.validEmployeeIds = validEmployeeIds; }
        public List<ValidationError> getErrors() { return errors; }
        public void setErrors(List<ValidationError> errors) { this.errors = errors; }
    }

    public static class PayrollBatchValidationResult {
        private String batchId;
        private boolean valid;
        private List<ValidationError> errors;

        // Getters and Setters
        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public List<ValidationError> getErrors() { return errors; }
        public void setErrors(List<ValidationError> errors) { this.errors = errors; }
    }

    public static class ValidationError {
        private String employeeId;
        private ValidationErrorType type;
        private String message;

        public ValidationError(String employeeId, ValidationErrorType type, String message) {
            this.employeeId = employeeId;
            this.type = type;
            this.message = message;
        }

        // Getters
        public String getEmployeeId() { return employeeId; }
        public ValidationErrorType getType() { return type; }
        public String getMessage() { return message; }
    }

    public enum ValidationErrorType {
        MISSING_FIELD,
        INVALID_SSN,
        INVALID_EMAIL,
        INVALID_PHONE,
        INVALID_DATE,
        INVALID_ZIP,
        INVALID_BANK_ACCOUNT,
        INVALID_AMOUNT,
        AGE_RESTRICTION,
        DUPLICATE_ENTRY,
        EMPTY_BATCH,
        TERMINATED_EMPLOYEE
    }
}
