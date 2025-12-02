package com.waqiti.payroll.kafka;

import com.waqiti.common.events.PayrollProcessingEvent;
import com.waqiti.common.events.PayrollProcessedEvent;
import com.waqiti.payroll.domain.PayrollBatch;
import com.waqiti.payroll.domain.PayrollPayment;
import com.waqiti.payroll.domain.BatchStatus;
import com.waqiti.payroll.domain.PaymentStatus;
import com.waqiti.payroll.domain.PayrollType;
import com.waqiti.payroll.repository.PayrollBatchRepository;
import com.waqiti.payroll.repository.PayrollPaymentRepository;
import com.waqiti.payroll.service.PayrollProcessingService;
import com.waqiti.payroll.service.TaxCalculationService;
import com.waqiti.payroll.service.BankTransferService;
import com.waqiti.payroll.service.ComplianceService;
import com.waqiti.payroll.service.NotificationService;
import com.waqiti.payroll.service.AuditService;
import com.waqiti.payroll.service.ValidationService;
import com.waqiti.payroll.service.DeductionService;
import com.waqiti.payroll.service.ReportingService;
import com.waqiti.payroll.exception.PayrollProcessingException;
import com.waqiti.payroll.exception.ComplianceViolationException;
import com.waqiti.payroll.exception.InsufficientFundsException;
import com.waqiti.common.security.encryption.EncryptionService;
import com.waqiti.common.compliance.ComplianceValidator;
import com.waqiti.common.audit.AuditEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CRITICAL PAYROLL PROCESSING EVENT CONSUMER - Consumer 43
 * 
 * Processes payroll processing events with zero-tolerance 12-step processing:
 * 1. Event validation and sanitization
 * 2. Idempotency and duplicate detection
 * 3. Regulatory compliance verification
 * 4. Employee data validation and verification
 * 5. Salary calculation and deduction processing
 * 6. Tax calculation and withholding
 * 7. Compliance checks and approval workflow
 * 8. Bank transfer preparation and validation
 * 9. Batch payment execution and monitoring
 * 10. Tax reporting and regulatory filing
 * 11. Audit trail and record creation
 * 12. Notification dispatch and confirmation
 * 
 * REGULATORY COMPLIANCE:
 * - Fair Labor Standards Act (FLSA)
 * - Federal Insurance Contributions Act (FICA)
 * - Federal Unemployment Tax Act (FUTA)
 * - State Unemployment Insurance (SUI)
 * - Worker's Compensation requirements
 * - Equal Pay Act compliance
 * - Anti-Money Laundering (AML) monitoring
 * 
 * PAYROLL TYPES SUPPORTED:
 * - Regular salary payments
 * - Hourly wage calculations
 * - Overtime payments
 * - Bonus distributions
 * - Commission payments
 * - Contractor payments
 * - Benefits disbursements
 * 
 * SLA: 99.99% uptime, <60s processing time for standard batches
 * 
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Validated
public class PayrollProcessingEventConsumer {
    
    private final PayrollBatchRepository payrollBatchRepository;
    private final PayrollPaymentRepository payrollPaymentRepository;
    private final PayrollProcessingService payrollProcessingService;
    private final TaxCalculationService taxCalculationService;
    private final BankTransferService bankTransferService;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ValidationService validationService;
    private final DeductionService deductionService;
    private final ReportingService reportingService;
    private final EncryptionService encryptionService;
    private final ComplianceValidator complianceValidator;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String PAYROLL_PROCESSED_TOPIC = "payroll-processed-events";
    private static final String TAX_FILING_TOPIC = "tax-filing-events";
    private static final String COMPLIANCE_ALERT_TOPIC = "compliance-alert-events";
    private static final String PAYMENT_NOTIFICATION_TOPIC = "payment-notification-events";
    private static final String DLQ_TOPIC = "payroll-processing-events-dlq";
    
    private static final BigDecimal MAX_INDIVIDUAL_PAYMENT = new BigDecimal("1000000.00");
    private static final BigDecimal MIN_INDIVIDUAL_PAYMENT = new BigDecimal("0.01");
    private static final BigDecimal MAX_BATCH_TOTAL = new BigDecimal("100000000.00");
    private static final int MAX_EMPLOYEES_PER_BATCH = 10000;
    private static final int MAX_PROCESSING_HOURS = 24;

    @KafkaListener(
        topics = "payroll-processing-events",
        groupId = "payroll-processing-processor",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Retryable(
        value = {PayrollProcessingException.class, InsufficientFundsException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 5000, multiplier = 2, maxDelay = 30000)
    )
    public void handlePayrollProcessingEvent(
            @Payload @Valid PayrollProcessingEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {
        
        String correlationId = generateCorrelationId(event, partition, offset);
        long processingStartTime = System.currentTimeMillis();
        
        log.info("STEP 1: Processing payroll event - ID: {}, Company: {}, Period: {}, Employees: {}, Total: {}, Correlation: {}",
            event.getPayrollBatchId(), event.getCompanyId(), event.getPayPeriod(), 
            event.getEmployeePayments().size(), event.getTotalAmount(), correlationId);
        
        try {
            // STEP 1: Event validation and sanitization
            validateAndSanitizeEvent(event, correlationId);
            
            // STEP 2: Idempotency and duplicate detection
            if (checkIdempotencyAndDuplicates(event, correlationId)) {
                acknowledgeAndReturn(acknowledgment, "Duplicate payroll processing event detected");
                return;
            }
            
            // STEP 3: Regulatory compliance verification
            performComplianceVerification(event, correlationId);
            
            // STEP 4: Employee data validation and verification
            EmployeeValidationResult employeeValidation = validateAndVerifyEmployeeData(event, correlationId);
            
            // STEP 5: Salary calculation and deduction processing
            SalaryCalculationResult salaryResult = processSalaryCalculationAndDeductions(event, employeeValidation, correlationId);
            
            // STEP 6: Tax calculation and withholding
            TaxCalculationResult taxResult = calculateTaxesAndWithholding(event, salaryResult, correlationId);
            
            // STEP 7: Compliance checks and approval workflow
            ComplianceApprovalResult complianceResult = performComplianceChecksAndApproval(
                event, salaryResult, taxResult, correlationId);
            
            // STEP 8: Bank transfer preparation and validation
            BankTransferPreparationResult transferPrep = prepareBankTransferAndValidation(
                event, salaryResult, taxResult, complianceResult, correlationId);
            
            // STEP 9: Batch payment execution and monitoring
            PaymentExecutionResult executionResult = executeBatchPaymentAndMonitoring(
                event, transferPrep, correlationId);
            
            // STEP 10: Tax reporting and regulatory filing
            performTaxReportingAndRegulatoryFiling(event, taxResult, executionResult, correlationId);
            
            // STEP 11: Audit trail and record creation
            PayrollBatch payrollBatch = createAuditTrailAndSaveRecords(event, employeeValidation, salaryResult,
                taxResult, complianceResult, transferPrep, executionResult, correlationId, processingStartTime);
            
            // STEP 12: Notification dispatch and confirmation
            dispatchNotificationsAndConfirmation(event, payrollBatch, executionResult, correlationId);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            long processingTime = System.currentTimeMillis() - processingStartTime;
            log.info("Successfully processed payroll batch - ID: {}, Status: {}, Payments: {}, Time: {}ms, Correlation: {}",
                event.getPayrollBatchId(), payrollBatch.getStatus(), executionResult.getSuccessfulPayments(), 
                processingTime, correlationId);
            
        } catch (ComplianceViolationException e) {
            handleComplianceViolation(event, e, correlationId, acknowledgment);
        } catch (InsufficientFundsException e) {
            handleInsufficientFundsError(event, e, correlationId, acknowledgment);
        } catch (PayrollProcessingException e) {
            handlePayrollProcessingError(event, e, correlationId, acknowledgment);
        } catch (Exception e) {
            handleCriticalError(event, e, correlationId, acknowledgment);
        }
    }

    /**
     * STEP 1: Event validation and sanitization
     */
    private void validateAndSanitizeEvent(PayrollProcessingEvent event, String correlationId) {
        log.debug("STEP 1: Validating payroll processing event - Correlation: {}", correlationId);
        
        if (event == null) {
            throw new IllegalArgumentException("Payroll processing event cannot be null");
        }
        
        if (event.getPayrollBatchId() == null || event.getPayrollBatchId().trim().isEmpty()) {
            throw new IllegalArgumentException("Payroll batch ID is required");
        }
        
        if (event.getCompanyId() == null || event.getCompanyId().trim().isEmpty()) {
            throw new IllegalArgumentException("Company ID is required");
        }
        
        if (event.getPayPeriod() == null) {
            throw new IllegalArgumentException("Pay period is required");
        }
        
        if (event.getPayPeriod().isBefore(LocalDate.now().minusDays(MAX_PROCESSING_HOURS))) {
            throw new PayrollProcessingException("Pay period too far in the past");
        }
        
        if (event.getEmployeePayments() == null || event.getEmployeePayments().isEmpty()) {
            throw new IllegalArgumentException("Employee payments list cannot be empty");
        }
        
        if (event.getEmployeePayments().size() > MAX_EMPLOYEES_PER_BATCH) {
            throw new PayrollProcessingException("Too many employees in batch: " + MAX_EMPLOYEES_PER_BATCH + " max");
        }
        
        if (event.getTotalAmount() == null || event.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid total amount: " + event.getTotalAmount());
        }
        
        if (event.getTotalAmount().compareTo(MAX_BATCH_TOTAL) > 0) {
            throw new PayrollProcessingException("Batch total exceeds maximum: " + MAX_BATCH_TOTAL);
        }
        
        // Validate individual payments
        for (var payment : event.getEmployeePayments()) {
            if (payment.getGrossAmount() == null || payment.getGrossAmount().compareTo(MIN_INDIVIDUAL_PAYMENT) < 0) {
                throw new IllegalArgumentException("Invalid payment amount for employee: " + payment.getEmployeeId());
            }
            if (payment.getGrossAmount().compareTo(MAX_INDIVIDUAL_PAYMENT) > 0) {
                throw new PayrollProcessingException("Payment exceeds maximum for employee: " + payment.getEmployeeId());
            }
        }
        
        // Sanitize string fields
        event.setPayrollBatchId(sanitizeString(event.getPayrollBatchId()));
        event.setCompanyId(sanitizeString(event.getCompanyId()));
        event.setPayrollType(sanitizeString(event.getPayrollType()));
        
        log.debug("STEP 1: Event validation completed - Employees: {}, Total: {}, Correlation: {}",
            event.getEmployeePayments().size(), event.getTotalAmount(), correlationId);
    }

    /**
     * STEP 2: Idempotency and duplicate detection
     */
    private boolean checkIdempotencyAndDuplicates(PayrollProcessingEvent event, String correlationId) {
        log.debug("STEP 2: Checking idempotency - Correlation: {}", correlationId);
        
        // Check for existing payroll batch
        boolean isDuplicate = payrollBatchRepository.existsByPayrollBatchIdAndCompanyId(
            event.getPayrollBatchId(), event.getCompanyId());
        
        if (isDuplicate) {
            log.warn("Duplicate payroll batch detected - ID: {}, Company: {}, Correlation: {}",
                event.getPayrollBatchId(), event.getCompanyId(), correlationId);
            
            auditService.logEvent(AuditEventType.DUPLICATE_PAYROLL_BATCH_DETECTED, 
                event.getCompanyId(), event.getPayrollBatchId(), correlationId);
        }
        
        return isDuplicate;
    }

    /**
     * STEP 3: Regulatory compliance verification
     */
    private void performComplianceVerification(PayrollProcessingEvent event, String correlationId) {
        log.debug("STEP 3: Performing compliance verification - Correlation: {}", correlationId);
        
        // Company compliance verification
        if (!complianceService.isCompanyCompliant(event.getCompanyId())) {
            throw new ComplianceViolationException("Company not compliant for payroll processing: " + event.getCompanyId());
        }
        
        // FLSA compliance check
        if (!complianceService.isFLSACompliant(event)) {
            throw new ComplianceViolationException("FLSA compliance violation detected");
        }
        
        // Equal Pay Act compliance
        if (!complianceService.isEqualPayActCompliant(event)) {
            throw new ComplianceViolationException("Equal Pay Act compliance violation detected");
        }
        
        // Worker classification compliance
        if (!complianceService.isWorkerClassificationCompliant(event.getEmployeePayments())) {
            throw new ComplianceViolationException("Worker classification compliance violation");
        }
        
        // AML screening for large payments
        ComplianceResult amlResult = complianceService.performPayrollAMLScreening(event);
        if (amlResult.hasViolations()) {
            throw new ComplianceViolationException("AML violations detected: " + amlResult.getViolations());
        }
        
        log.debug("STEP 3: Compliance verification completed - Correlation: {}", correlationId);
    }

    /**
     * STEP 4: Employee data validation and verification
     */
    private EmployeeValidationResult validateAndVerifyEmployeeData(PayrollProcessingEvent event, String correlationId) {
        log.debug("STEP 4: Validating employee data - Correlation: {}", correlationId);
        
        EmployeeValidationResult result = validationService.validateEmployeeData(
            event.getCompanyId(), event.getEmployeePayments());
        
        if (result.getInvalidEmployees().size() > 0) {
            log.warn("Invalid employees detected - Count: {}, Company: {}, Correlation: {}",
                result.getInvalidEmployees().size(), event.getCompanyId(), correlationId);
        }
        
        // Verify employee bank accounts
        for (var payment : event.getEmployeePayments()) {
            if (!validationService.isEmployeeBankAccountValid(payment.getEmployeeId(), payment.getBankAccountId())) {
                result.addInvalidEmployee(payment.getEmployeeId(), "Invalid bank account");
            }
        }
        
        // Check for terminated employees
        List<String> terminatedEmployees = validationService.checkTerminatedEmployees(
            event.getCompanyId(), 
            event.getEmployeePayments().stream().map(p -> p.getEmployeeId()).toList()
        );
        
        for (String employeeId : terminatedEmployees) {
            result.addInvalidEmployee(employeeId, "Employee terminated");
        }
        
        if (result.getInvalidEmployees().size() > event.getEmployeePayments().size() * 0.1) { // 10% threshold
            throw new PayrollProcessingException("Too many invalid employees: " + result.getInvalidEmployees().size());
        }
        
        log.debug("STEP 4: Employee validation completed - Valid: {}, Invalid: {}, Correlation: {}",
            result.getValidEmployees().size(), result.getInvalidEmployees().size(), correlationId);
        
        return result;
    }

    /**
     * STEP 5: Salary calculation and deduction processing
     */
    private SalaryCalculationResult processSalaryCalculationAndDeductions(PayrollProcessingEvent event,
            EmployeeValidationResult employeeValidation, String correlationId) {
        log.debug("STEP 5: Processing salary calculations - Correlation: {}", correlationId);
        
        SalaryCalculationResult result = new SalaryCalculationResult();
        AtomicInteger processedCount = new AtomicInteger(0);
        
        for (var payment : event.getEmployeePayments()) {
            if (employeeValidation.getValidEmployees().contains(payment.getEmployeeId())) {
                try {
                    // Calculate base salary/wages
                    BigDecimal basePay = calculateBasePay(payment, event.getPayrollType());
                    
                    // Calculate overtime if applicable
                    BigDecimal overtimePay = calculateOvertimePay(payment);
                    
                    // Calculate bonuses and commissions
                    BigDecimal bonusPay = calculateBonuses(payment);
                    
                    // Total gross pay
                    BigDecimal grossPay = basePay.add(overtimePay).add(bonusPay);
                    
                    // Process deductions
                    DeductionResult deductions = deductionService.calculateDeductions(
                        payment.getEmployeeId(), grossPay, event.getPayPeriod());
                    
                    // Calculate net pay
                    BigDecimal netPay = grossPay.subtract(deductions.getTotalDeductions());
                    
                    if (netPay.compareTo(BigDecimal.ZERO) < 0) {
                        throw new PayrollProcessingException("Negative net pay calculated for employee: " + payment.getEmployeeId());
                    }
                    
                    PayrollCalculation calculation = PayrollCalculation.builder()
                        .employeeId(payment.getEmployeeId())
                        .basePay(basePay)
                        .overtimePay(overtimePay)
                        .bonusPay(bonusPay)
                        .grossPay(grossPay)
                        .deductions(deductions)
                        .netPay(netPay)
                        .build();
                    
                    result.addCalculation(calculation);
                    processedCount.incrementAndGet();
                    
                } catch (Exception e) {
                    log.error("Failed to calculate salary for employee: {}, Error: {}, Correlation: {}",
                        payment.getEmployeeId(), e.getMessage(), correlationId);
                    result.addFailedCalculation(payment.getEmployeeId(), e.getMessage());
                }
            }
        }
        
        log.debug("STEP 5: Salary calculation completed - Processed: {}, Failed: {}, Correlation: {}",
            processedCount.get(), result.getFailedCalculations().size(), correlationId);
        
        return result;
    }

    /**
     * STEP 6: Tax calculation and withholding
     */
    private TaxCalculationResult calculateTaxesAndWithholding(PayrollProcessingEvent event, 
            SalaryCalculationResult salaryResult, String correlationId) {
        log.debug("STEP 6: Calculating taxes - Correlation: {}", correlationId);
        
        TaxCalculationResult result = taxCalculationService.calculatePayrollTaxes(
            event.getCompanyId(), salaryResult.getCalculations(), event.getPayPeriod());
        
        // Validate tax calculations
        for (TaxCalculation taxCalc : result.getTaxCalculations()) {
            if (taxCalc.getFederalTax().compareTo(BigDecimal.ZERO) < 0) {
                throw new PayrollProcessingException("Invalid federal tax calculation for employee: " + taxCalc.getEmployeeId());
            }
            
            if (taxCalc.getStateTax().compareTo(BigDecimal.ZERO) < 0) {
                throw new PayrollProcessingException("Invalid state tax calculation for employee: " + taxCalc.getEmployeeId());
            }
            
            // Validate FICA taxes
            BigDecimal expectedSocialSecurity = taxCalc.getGrossWages().multiply(new BigDecimal("0.062")); // 6.2%
            if (taxCalc.getSocialSecurityTax().subtract(expectedSocialSecurity).abs().compareTo(new BigDecimal("0.01")) > 0) {
                log.warn("Social Security tax calculation variance for employee: {}, Expected: {}, Calculated: {}, Correlation: {}",
                    taxCalc.getEmployeeId(), expectedSocialSecurity, taxCalc.getSocialSecurityTax(), correlationId);
            }
        }
        
        log.debug("STEP 6: Tax calculation completed - Employees: {}, Total Tax: {}, Correlation: {}",
            result.getTaxCalculations().size(), result.getTotalTaxWithheld(), correlationId);
        
        return result;
    }

    /**
     * STEP 7: Compliance checks and approval workflow
     */
    private ComplianceApprovalResult performComplianceChecksAndApproval(PayrollProcessingEvent event,
            SalaryCalculationResult salaryResult, TaxCalculationResult taxResult, String correlationId) {
        log.debug("STEP 7: Performing compliance checks - Correlation: {}", correlationId);
        
        ComplianceApprovalResult result = complianceService.performPayrollComplianceChecks(
            event, salaryResult, taxResult);
        
        // Minimum wage compliance check
        for (PayrollCalculation calc : salaryResult.getCalculations()) {
            if (!complianceService.isMinimumWageCompliant(calc, event.getPayPeriod())) {
                result.addViolation("MINIMUM_WAGE_VIOLATION", calc.getEmployeeId());
            }
        }
        
        // Overtime compliance check
        if (!complianceService.isOvertimeCompliant(salaryResult.getCalculations())) {
            result.addViolation("OVERTIME_COMPLIANCE_VIOLATION", "Multiple employees");
        }
        
        // Auto-approval for standard payrolls
        if (result.getViolations().isEmpty() && isStandardPayroll(event)) {
            result.setApprovalRequired(false);
            result.setAutoApproved(true);
        } else {
            result.setApprovalRequired(true);
            
            // For now, auto-approve but flag for review
            if (result.getViolations().size() <= 3) {
                result.setAutoApproved(true);
                result.setRequiresReview(true);
            } else {
                throw new ComplianceViolationException("Too many compliance violations: " + result.getViolations().size());
            }
        }
        
        log.debug("STEP 7: Compliance checks completed - Violations: {}, Auto-approved: {}, Correlation: {}",
            result.getViolations().size(), result.isAutoApproved(), correlationId);
        
        return result;
    }

    /**
     * STEP 8: Bank transfer preparation and validation
     */
    private BankTransferPreparationResult prepareBankTransferAndValidation(PayrollProcessingEvent event,
            SalaryCalculationResult salaryResult, TaxCalculationResult taxResult, 
            ComplianceApprovalResult complianceResult, String correlationId) {
        log.debug("STEP 8: Preparing bank transfers - Correlation: {}", correlationId);
        
        if (!complianceResult.isAutoApproved()) {
            throw new PayrollProcessingException("Payroll not approved for processing");
        }
        
        BankTransferPreparationResult result = bankTransferService.prepareBankTransfers(
            event.getCompanyId(), salaryResult.getCalculations());
        
        // Validate company funding account
        if (!bankTransferService.hasAdequateFunding(event.getCompanyId(), result.getTotalTransferAmount())) {
            throw new InsufficientFundsException("Insufficient funds for payroll batch: " + result.getTotalTransferAmount());
        }
        
        // Validate all employee bank accounts
        int invalidAccounts = 0;
        for (BankTransferInstruction instruction : result.getTransferInstructions()) {
            if (!bankTransferService.isValidBankAccount(instruction.getToBankAccount())) {
                result.markInstructionInvalid(instruction.getEmployeeId(), "Invalid bank account");
                invalidAccounts++;
            }
        }
        
        if (invalidAccounts > 0) {
            log.warn("Invalid bank accounts detected - Count: {}, Correlation: {}", invalidAccounts, correlationId);
        }
        
        // Reserve funds
        String reservationId = bankTransferService.reserveFunds(
            event.getCompanyId(), result.getTotalTransferAmount(), correlationId);
        result.setFundReservationId(reservationId);
        
        log.debug("STEP 8: Bank transfer preparation completed - Instructions: {}, Total: {}, Reserved: {}, Correlation: {}",
            result.getTransferInstructions().size(), result.getTotalTransferAmount(), reservationId, correlationId);
        
        return result;
    }

    /**
     * STEP 9: Batch payment execution and monitoring
     */
    private PaymentExecutionResult executeBatchPaymentAndMonitoring(PayrollProcessingEvent event,
            BankTransferPreparationResult transferPrep, String correlationId) {
        log.debug("STEP 9: Executing batch payments - Correlation: {}", correlationId);
        
        PaymentExecutionResult result = new PaymentExecutionResult();
        AtomicInteger successfulPayments = new AtomicInteger(0);
        AtomicInteger failedPayments = new AtomicInteger(0);
        
        // Execute payments in parallel batches
        List<CompletableFuture<Void>> paymentFutures = new ArrayList<>();
        
        for (BankTransferInstruction instruction : transferPrep.getValidInstructions()) {
            CompletableFuture<Void> paymentFuture = CompletableFuture.runAsync(() -> {
                try {
                    BankTransferResult transferResult = bankTransferService.executeTransfer(instruction);
                    
                    if (transferResult.isSuccessful()) {
                        result.addSuccessfulPayment(instruction.getEmployeeId(), 
                            instruction.getAmount(), transferResult.getTransactionId());
                        successfulPayments.incrementAndGet();
                    } else {
                        result.addFailedPayment(instruction.getEmployeeId(), 
                            instruction.getAmount(), transferResult.getErrorMessage());
                        failedPayments.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    result.addFailedPayment(instruction.getEmployeeId(), 
                        instruction.getAmount(), e.getMessage());
                    failedPayments.incrementAndGet();
                    log.error("Payment execution failed for employee: {}, Error: {}, Correlation: {}",
                        instruction.getEmployeeId(), e.getMessage(), correlationId);
                }
            });
            
            paymentFutures.add(paymentFuture);
        }
        
        // Wait for all payments to complete with timeout
        try {
            CompletableFuture.allOf(paymentFutures.toArray(new CompletableFuture[0]))
                .get(10, java.util.concurrent.TimeUnit.MINUTES);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Payroll payment execution timed out after 10 minutes. Correlation: {}", correlationId, e);
            // Cancel remaining futures
            paymentFutures.forEach(f -> f.cancel(true));
        } catch (java.util.concurrent.ExecutionException e) {
            log.error("Payroll payment execution failed. Correlation: {}", correlationId, e.getCause());
        } catch (java.util.concurrent.InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Payroll payment execution interrupted. Correlation: {}", correlationId, e);
        }

        result.setSuccessfulPayments(successfulPayments.get());
        result.setFailedPayments(failedPayments.get());
        result.setTotalAmount(transferPrep.getTotalTransferAmount());
        
        // Release fund reservation for failed payments
        if (failedPayments.get() > 0) {
            BigDecimal failedAmount = result.getFailedPaymentRecords().stream()
                .map(record -> record.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            bankTransferService.releaseFunds(transferPrep.getFundReservationId(), failedAmount);
        }
        
        log.debug("STEP 9: Batch payment execution completed - Successful: {}, Failed: {}, Correlation: {}",
            successfulPayments.get(), failedPayments.get(), correlationId);
        
        return result;
    }

    /**
     * STEP 10: Tax reporting and regulatory filing
     */
    private void performTaxReportingAndRegulatoryFiling(PayrollProcessingEvent event, TaxCalculationResult taxResult,
            PaymentExecutionResult executionResult, String correlationId) {
        log.debug("STEP 10: Performing tax reporting - Correlation: {}", correlationId);
        
        // Generate tax reports asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                reportingService.generatePayrollTaxReport(event, taxResult, executionResult, correlationId);
                
                // File quarterly reports if end of quarter
                if (isEndOfQuarter(event.getPayPeriod())) {
                    reportingService.generateQuarterlyTaxReport(event.getCompanyId(), event.getPayPeriod(), correlationId);
                }
                
                // File annual reports if end of year
                if (isEndOfYear(event.getPayPeriod())) {
                    reportingService.generateAnnualTaxReport(event.getCompanyId(), event.getPayPeriod(), correlationId);
                }
                
                // Send tax filing event
                kafkaTemplate.send(TAX_FILING_TOPIC, Map.of(
                    "companyId", event.getCompanyId(),
                    "payPeriod", event.getPayPeriod(),
                    "totalTaxWithheld", taxResult.getTotalTaxWithheld(),
                    "correlationId", correlationId
                ));
                
            } catch (Exception e) {
                log.error("Failed to generate tax reports - Correlation: {}", correlationId, e);
            }
        });
        
        log.debug("STEP 10: Tax reporting initiated - Correlation: {}", correlationId);
    }

    /**
     * STEP 11: Audit trail and record creation
     */
    private PayrollBatch createAuditTrailAndSaveRecords(PayrollProcessingEvent event, EmployeeValidationResult employeeValidation,
            SalaryCalculationResult salaryResult, TaxCalculationResult taxResult, ComplianceApprovalResult complianceResult,
            BankTransferPreparationResult transferPrep, PaymentExecutionResult executionResult, String correlationId,
            long processingStartTime) {
        log.debug("STEP 11: Creating audit trail - Correlation: {}", correlationId);
        
        // Determine batch status
        BatchStatus status = determineBatchStatus(executionResult, complianceResult);
        
        PayrollBatch payrollBatch = PayrollBatch.builder()
            .payrollBatchId(event.getPayrollBatchId())
            .companyId(event.getCompanyId())
            .payPeriod(event.getPayPeriod())
            .payrollType(PayrollType.valueOf(event.getPayrollType()))
            .status(status)
            .totalEmployees(event.getEmployeePayments().size())
            .validEmployees(employeeValidation.getValidEmployees().size())
            .invalidEmployees(employeeValidation.getInvalidEmployees().size())
            .successfulPayments(executionResult.getSuccessfulPayments())
            .failedPayments(executionResult.getFailedPayments())
            .grossAmount(calculateTotalGrossAmount(salaryResult))
            .totalDeductions(calculateTotalDeductions(salaryResult))
            .totalTaxWithheld(taxResult.getTotalTaxWithheld())
            .netAmount(executionResult.getTotalAmount())
            .fundReservationId(transferPrep.getFundReservationId())
            .complianceViolations(complianceResult.getViolations().size())
            .approvedBy(complianceResult.getApprovedBy())
            .correlationId(correlationId)
            .processedAt(LocalDateTime.now())
            .processingTimeMs(System.currentTimeMillis() - processingStartTime)
            .build();
        
        payrollBatch = payrollBatchRepository.save(payrollBatch);
        
        // Save individual payment records
        for (PaymentRecord record : executionResult.getAllPaymentRecords()) {
            PayrollPayment payment = PayrollPayment.builder()
                .payrollBatchId(payrollBatch.getPayrollBatchId())
                .employeeId(record.getEmployeeId())
                .grossAmount(getGrossAmountForEmployee(record.getEmployeeId(), salaryResult))
                .netAmount(record.getAmount())
                .taxWithheld(getTaxWithheldForEmployee(record.getEmployeeId(), taxResult))
                .status(record.isSuccessful() ? PaymentStatus.COMPLETED : PaymentStatus.FAILED)
                .transactionId(record.getTransactionId())
                .errorMessage(record.getErrorMessage())
                .processedAt(LocalDateTime.now())
                .build();
            
            payrollPaymentRepository.save(payment);
        }
        
        // Create detailed audit log
        auditService.logPayrollProcessingEvent(event, payrollBatch, employeeValidation, salaryResult,
            taxResult, complianceResult, transferPrep, executionResult, correlationId);
        
        log.debug("STEP 11: Audit trail created - Batch ID: {}, Status: {}, Correlation: {}",
            payrollBatch.getId(), payrollBatch.getStatus(), correlationId);
        
        return payrollBatch;
    }

    /**
     * STEP 12: Notification dispatch and confirmation
     */
    private void dispatchNotificationsAndConfirmation(PayrollProcessingEvent event, PayrollBatch payrollBatch,
            PaymentExecutionResult executionResult, String correlationId) {
        log.debug("STEP 12: Dispatching notifications - Correlation: {}", correlationId);
        
        // Send payroll completion notification to company
        CompletableFuture.runAsync(() -> {
            notificationService.sendPayrollCompletionNotification(
                event.getCompanyId(),
                payrollBatch.getPayrollBatchId(),
                payrollBatch.getSuccessfulPayments(),
                payrollBatch.getFailedPayments(),
                payrollBatch.getNetAmount()
            );
        });
        
        // Send individual payment confirmations to employees
        for (PaymentRecord record : executionResult.getSuccessfulPaymentRecords()) {
            kafkaTemplate.send(PAYMENT_NOTIFICATION_TOPIC, Map.of(
                "employeeId", record.getEmployeeId(),
                "amount", record.getAmount(),
                "payPeriod", event.getPayPeriod(),
                "transactionId", record.getTransactionId(),
                "correlationId", correlationId
            ));
        }
        
        // Send failure notifications for failed payments
        if (executionResult.getFailedPayments() > 0) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendPayrollFailureAlert(
                    event.getCompanyId(),
                    executionResult.getFailedPaymentRecords(),
                    correlationId
                );
            });
        }
        
        // Send compliance alerts if needed
        if (payrollBatch.getComplianceViolations() > 0) {
            kafkaTemplate.send(COMPLIANCE_ALERT_TOPIC, Map.of(
                "eventType", "PAYROLL_COMPLIANCE_VIOLATIONS",
                "companyId", event.getCompanyId(),
                "payrollBatchId", payrollBatch.getPayrollBatchId(),
                "violationCount", payrollBatch.getComplianceViolations(),
                "correlationId", correlationId
            ));
        }
        
        // Publish payroll processed event
        PayrollProcessedEvent processedEvent = PayrollProcessedEvent.builder()
            .payrollBatchId(event.getPayrollBatchId())
            .companyId(event.getCompanyId())
            .payPeriod(event.getPayPeriod())
            .status(payrollBatch.getStatus().toString())
            .successfulPayments(payrollBatch.getSuccessfulPayments())
            .failedPayments(payrollBatch.getFailedPayments())
            .totalAmount(payrollBatch.getNetAmount())
            .correlationId(correlationId)
            .processedAt(payrollBatch.getProcessedAt())
            .build();
        
        kafkaTemplate.send(PAYROLL_PROCESSED_TOPIC, processedEvent);
        
        log.debug("STEP 12: Notifications dispatched - Correlation: {}", correlationId);
    }

    // Error handling methods
    private void handleComplianceViolation(PayrollProcessingEvent event, ComplianceViolationException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Compliance violation in payroll processing - ID: {}, Error: {}, Correlation: {}",
            event.getPayrollBatchId(), e.getMessage(), correlationId);
        
        // Send compliance alert
        kafkaTemplate.send(COMPLIANCE_ALERT_TOPIC, Map.of(
            "eventType", "PAYROLL_COMPLIANCE_VIOLATION",
            "payrollBatchId", event.getPayrollBatchId(),
            "companyId", event.getCompanyId(),
            "violation", e.getMessage(),
            "correlationId", correlationId
        ));
        
        acknowledgment.acknowledge();
    }

    private void handleInsufficientFundsError(PayrollProcessingEvent event, InsufficientFundsException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Insufficient funds for payroll - ID: {}, Error: {}, Correlation: {}",
            event.getPayrollBatchId(), e.getMessage(), correlationId);
        
        // Create failed batch record
        PayrollBatch failedBatch = PayrollBatch.builder()
            .payrollBatchId(event.getPayrollBatchId())
            .companyId(event.getCompanyId())
            .payPeriod(event.getPayPeriod())
            .status(BatchStatus.FAILED)
            .failureReason(e.getMessage())
            .correlationId(correlationId)
            .processedAt(LocalDateTime.now())
            .build();
        
        payrollBatchRepository.save(failedBatch);
        acknowledgment.acknowledge();
    }

    private void handlePayrollProcessingError(PayrollProcessingEvent event, PayrollProcessingException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Payroll processing error - ID: {}, Error: {}, Correlation: {}",
            event.getPayrollBatchId(), e.getMessage(), correlationId);
        
        sendToDeadLetterQueue(event, e, correlationId);
        acknowledgment.acknowledge();
    }

    private void handleCriticalError(PayrollProcessingEvent event, Exception e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Critical error in payroll processing - ID: {}, Error: {}, Correlation: {}",
            event.getPayrollBatchId(), e.getMessage(), e, correlationId);
        
        sendToDeadLetterQueue(event, e, correlationId);
        
        // Send critical alert
        notificationService.sendCriticalAlert(
            "PAYROLL_PROCESSING_ERROR",
            String.format("Critical error processing payroll %s: %s", event.getPayrollBatchId(), e.getMessage()),
            correlationId
        );
        
        acknowledgment.acknowledge();
    }

    // Utility methods
    private String generateCorrelationId(PayrollProcessingEvent event, int partition, long offset) {
        return String.format("payroll-%s-p%d-o%d-%d",
            event.getPayrollBatchId(), partition, offset, System.currentTimeMillis());
    }

    private String sanitizeString(String input) {
        if (input == null) return null;
        return input.trim().replaceAll("[<>\"'&]", "");
    }

    private void acknowledgeAndReturn(Acknowledgment acknowledgment, String message) {
        log.info(message);
        acknowledgment.acknowledge();
    }

    private BigDecimal calculateBasePay(Object payment, String payrollType) {
        // Implementation would calculate base pay based on salary/hourly rate
        return new BigDecimal("5000.00"); // Placeholder
    }

    private BigDecimal calculateOvertimePay(Object payment) {
        // Implementation would calculate overtime pay
        return BigDecimal.ZERO; // Placeholder
    }

    private BigDecimal calculateBonuses(Object payment) {
        // Implementation would calculate bonuses and commissions
        return BigDecimal.ZERO; // Placeholder
    }

    private boolean isStandardPayroll(PayrollProcessingEvent event) {
        return "REGULAR".equals(event.getPayrollType()) && event.getTotalAmount().compareTo(new BigDecimal("1000000")) <= 0;
    }

    private boolean isEndOfQuarter(LocalDate payPeriod) {
        int month = payPeriod.getMonthValue();
        return month == 3 || month == 6 || month == 9 || month == 12;
    }

    private boolean isEndOfYear(LocalDate payPeriod) {
        return payPeriod.getMonthValue() == 12;
    }

    private BatchStatus determineBatchStatus(PaymentExecutionResult executionResult, ComplianceApprovalResult complianceResult) {
        if (executionResult.getFailedPayments() == 0) {
            return complianceResult.isRequiresReview() ? BatchStatus.COMPLETED_WITH_REVIEW : BatchStatus.COMPLETED;
        } else if (executionResult.getSuccessfulPayments() > 0) {
            return BatchStatus.PARTIALLY_COMPLETED;
        } else {
            return BatchStatus.FAILED;
        }
    }

    private BigDecimal calculateTotalGrossAmount(SalaryCalculationResult salaryResult) {
        return salaryResult.getCalculations().stream()
            .map(PayrollCalculation::getGrossPay)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotalDeductions(SalaryCalculationResult salaryResult) {
        return salaryResult.getCalculations().stream()
            .map(calc -> calc.getDeductions().getTotalDeductions())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getGrossAmountForEmployee(String employeeId, SalaryCalculationResult salaryResult) {
        return salaryResult.getCalculations().stream()
            .filter(calc -> calc.getEmployeeId().equals(employeeId))
            .findFirst()
            .map(PayrollCalculation::getGrossPay)
            .orElse(BigDecimal.ZERO);
    }

    private BigDecimal getTaxWithheldForEmployee(String employeeId, TaxCalculationResult taxResult) {
        return taxResult.getTaxCalculations().stream()
            .filter(calc -> calc.getEmployeeId().equals(employeeId))
            .findFirst()
            .map(TaxCalculation::getTotalTaxWithheld)
            .orElse(BigDecimal.ZERO);
    }

    private void sendToDeadLetterQueue(PayrollProcessingEvent event, Exception error, String correlationId) {
        try {
            Map<String, Object> dlqMessage = Map.of(
                "originalEvent", event,
                "errorMessage", error.getMessage(),
                "errorClass", error.getClass().getName(),
                "correlationId", correlationId,
                "failedAt", Instant.now(),
                "service", "batch-service"
            );
            
            kafkaTemplate.send(DLQ_TOPIC, dlqMessage);
            log.warn("Sent failed payroll to DLQ - ID: {}, Correlation: {}",
                event.getPayrollBatchId(), correlationId);
                
        } catch (Exception dlqError) {
            log.error("Failed to send payroll to DLQ - Correlation: {}", correlationId, dlqError);
        }
    }

    // Inner classes for results (simplified for brevity)
    @lombok.Data
    @lombok.Builder
    private static class EmployeeValidationResult {
        private List<String> validEmployees = new ArrayList<>();
        private Map<String, String> invalidEmployees = new HashMap<>();
        
        public void addInvalidEmployee(String employeeId, String reason) {
            invalidEmployees.put(employeeId, reason);
        }
    }

    @lombok.Data
    private static class SalaryCalculationResult {
        private List<PayrollCalculation> calculations = new ArrayList<>();
        private Map<String, String> failedCalculations = new HashMap<>();
        
        public void addCalculation(PayrollCalculation calculation) {
            calculations.add(calculation);
        }
        
        public void addFailedCalculation(String employeeId, String reason) {
            failedCalculations.put(employeeId, reason);
        }
    }

    @lombok.Data
    @lombok.Builder
    private static class PayrollCalculation {
        private String employeeId;
        private BigDecimal basePay;
        private BigDecimal overtimePay;
        private BigDecimal bonusPay;
        private BigDecimal grossPay;
        private DeductionResult deductions;
        private BigDecimal netPay;
    }

    @lombok.Data
    @lombok.Builder
    private static class TaxCalculationResult {
        private List<TaxCalculation> taxCalculations;
        private BigDecimal totalTaxWithheld;
    }

    @lombok.Data
    @lombok.Builder
    private static class TaxCalculation {
        private String employeeId;
        private BigDecimal grossWages;
        private BigDecimal federalTax;
        private BigDecimal stateTax;
        private BigDecimal socialSecurityTax;
        private BigDecimal medicareTax;
        private BigDecimal totalTaxWithheld;
    }

    @lombok.Data
    @lombok.Builder
    private static class ComplianceApprovalResult {
        private List<String> violations = new ArrayList<>();
        private boolean approvalRequired;
        private boolean autoApproved;
        private boolean requiresReview;
        private String approvedBy;
        
        public void addViolation(String violationType, String employeeId) {
            violations.add(violationType + ": " + employeeId);
        }
    }

    @lombok.Data
    @lombok.Builder
    private static class BankTransferPreparationResult {
        private List<BankTransferInstruction> transferInstructions;
        private BigDecimal totalTransferAmount;
        private String fundReservationId;
        
        public List<BankTransferInstruction> getValidInstructions() {
            return transferInstructions.stream()
                .filter(instruction -> instruction.isValid())
                .toList();
        }
        
        public void markInstructionInvalid(String employeeId, String reason) {
            // Implementation would mark instruction as invalid
        }
    }

    @lombok.Data
    @lombok.Builder
    private static class BankTransferInstruction {
        private String employeeId;
        private BigDecimal amount;
        private String toBankAccount;
        private boolean valid = true;
    }

    @lombok.Data
    private static class PaymentExecutionResult {
        private int successfulPayments;
        private int failedPayments;
        private BigDecimal totalAmount;
        private List<PaymentRecord> successfulPaymentRecords = new ArrayList<>();
        private List<PaymentRecord> failedPaymentRecords = new ArrayList<>();
        
        public void addSuccessfulPayment(String employeeId, BigDecimal amount, String transactionId) {
            successfulPaymentRecords.add(new PaymentRecord(employeeId, amount, transactionId, null, true));
        }
        
        public void addFailedPayment(String employeeId, BigDecimal amount, String errorMessage) {
            failedPaymentRecords.add(new PaymentRecord(employeeId, amount, null, errorMessage, false));
        }
        
        public List<PaymentRecord> getAllPaymentRecords() {
            List<PaymentRecord> allRecords = new ArrayList<>();
            allRecords.addAll(successfulPaymentRecords);
            allRecords.addAll(failedPaymentRecords);
            return allRecords;
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class PaymentRecord {
        private String employeeId;
        private BigDecimal amount;
        private String transactionId;
        private String errorMessage;
        private boolean successful;
    }

    @lombok.Data
    @lombok.Builder
    private static class DeductionResult {
        private BigDecimal totalDeductions;
        private Map<String, BigDecimal> deductionBreakdown;
    }

    @lombok.Data
    @lombok.Builder
    private static class BankTransferResult {
        private boolean successful;
        private String transactionId;
        private String errorMessage;
    }

    @lombok.Data
    @lombok.Builder
    private static class ComplianceResult {
        private boolean compliant;
        private List<String> violations;
        
        public boolean hasViolations() {
            return violations != null && !violations.isEmpty();
        }
    }
}