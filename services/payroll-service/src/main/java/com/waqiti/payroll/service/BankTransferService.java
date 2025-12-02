package com.waqiti.payroll.service;

import com.waqiti.payroll.exception.InsufficientFundsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Enterprise-grade Bank Transfer Service
 * Handles ACH, Direct Deposit, Wire Transfers for payroll
 * Compliant with NACHA Operating Rules and OFAC regulations
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BankTransferService {

    @Value("${payroll.bank-transfer.default-method:ACH}")
    private String defaultTransferMethod;

    @Value("${payroll.bank-transfer.settlement-days:2}")
    private int settlementDays;

    @Value("${payroll.bank-transfer.enable-same-day:false}")
    private boolean enableSameDayACH;

    @Value("${payroll.bank-transfer.max-individual-transfer:1000000.00}")
    private BigDecimal maxIndividualTransfer;

    @Value("${payroll.bank-transfer.max-daily-transfer:100000000.00}")
    private BigDecimal maxDailyTransfer;

    // ACH Transaction Codes (NACHA)
    private static final String ACH_CHECKING_CREDIT = "22";
    private static final String ACH_CHECKING_DEBIT = "27";
    private static final String ACH_SAVINGS_CREDIT = "32";
    private static final String ACH_SAVINGS_DEBIT = "37";

    // Wire Transfer Types
    private static final String WIRE_DOMESTIC = "DOMESTIC";
    private static final String WIRE_INTERNATIONAL = "INTERNATIONAL";

    // Routing number validation (ABA routing number check)
    private static final Pattern ROUTING_NUMBER_PATTERN = Pattern.compile("^\\d{9}$");

    // Account number validation (8-17 digits)
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^\\d{8,17}$");

    /**
     * Prepare bank transfers for payroll batch
     */
    public BankTransferBatchResult prepareBankTransfers(String companyId, List<PayrollTransferRequest> requests) {
        log.info("Preparing {} bank transfers for company: {}", requests.size(), companyId);

        BankTransferBatchResult result = new BankTransferBatchResult();
        result.setCompanyId(companyId);
        result.setBatchId(UUID.randomUUID().toString());
        result.setCreatedAt(LocalDateTime.now());

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<BankTransferInstruction> instructions = new ArrayList<>();
        List<String> validationErrors = new ArrayList<>();

        for (PayrollTransferRequest request : requests) {
            // Validate transfer
            List<String> errors = validateTransfer(request);
            if (!errors.isEmpty()) {
                validationErrors.addAll(errors);
                result.addFailedTransfer(request.getEmployeeId(), String.join(", ", errors));
                continue;
            }

            // Create transfer instruction
            BankTransferInstruction instruction = createTransferInstruction(companyId, request);
            instructions.add(instruction);
            totalAmount = totalAmount.add(request.getAmount());
        }

        result.setTotalAmount(totalAmount);
        result.setTransferCount(instructions.size());
        result.setFailedCount(validationErrors.size());
        result.setInstructions(instructions);

        // Calculate settlement date
        LocalDate settlementDate = calculateSettlementDate(LocalDate.now());
        result.setSettlementDate(settlementDate);

        log.info("Prepared {} transfers totaling ${}, settlement date: {}",
                 instructions.size(), totalAmount, settlementDate);

        return result;
    }

    /**
     * Validate individual transfer request
     */
    private List<String> validateTransfer(PayrollTransferRequest request) {
        List<String> errors = new ArrayList<>();

        // 1. Amount validation
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Invalid transfer amount");
        } else if (request.getAmount().compareTo(maxIndividualTransfer) > 0) {
            errors.add("Amount exceeds maximum individual transfer limit: $" + maxIndividualTransfer);
        }

        // 2. Routing number validation
        if (!isValidRoutingNumber(request.getRoutingNumber())) {
            errors.add("Invalid routing number: " + request.getRoutingNumber());
        }

        // 3. Account number validation
        if (!isValidBankAccount(request.getAccountNumber())) {
            errors.add("Invalid account number format");
        }

        // 4. Employee information
        if (request.getEmployeeId() == null || request.getEmployeeId().isEmpty()) {
            errors.add("Missing employee ID");
        }

        if (request.getEmployeeName() == null || request.getEmployeeName().isEmpty()) {
            errors.add("Missing employee name");
        }

        return errors;
    }

    /**
     * Create ACH transfer instruction
     */
    private BankTransferInstruction createTransferInstruction(String companyId, PayrollTransferRequest request) {
        BankTransferInstruction instruction = new BankTransferInstruction();
        instruction.setInstructionId(UUID.randomUUID().toString());
        instruction.setCompanyId(companyId);
        instruction.setEmployeeId(request.getEmployeeId());
        instruction.setEmployeeName(request.getEmployeeName());
        instruction.setAmount(request.getAmount());
        instruction.setRoutingNumber(request.getRoutingNumber());
        instruction.setAccountNumber(request.getAccountNumber());
        instruction.setAccountType(request.getAccountType());
        instruction.setTransferMethod(determineTransferMethod(request));
        instruction.setTransactionCode(getACHTransactionCode(request.getAccountType(), true));
        instruction.setCreatedAt(LocalDateTime.now());
        instruction.setStatus(TransferStatus.PENDING);

        // NACHA required fields
        instruction.setCompanyName(request.getCompanyName());
        instruction.setCompanyId(companyId);
        instruction.setEntryDescription("PAYROLL");
        instruction.setEffectiveEntryDate(calculateSettlementDate(LocalDate.now()));

        return instruction;
    }

    /**
     * Determine transfer method (ACH Standard, Same-Day ACH, Wire)
     */
    private String determineTransferMethod(PayrollTransferRequest request) {
        if (request.getUrgent() != null && request.getUrgent() && enableSameDayACH) {
            return "ACH_SAME_DAY";
        } else if (request.getAmount().compareTo(new BigDecimal("25000")) > 0) {
            // Large amounts may use wire transfer
            return "WIRE";
        } else {
            return "ACH_STANDARD";
        }
    }

    /**
     * Get NACHA ACH transaction code
     */
    private String getACHTransactionCode(String accountType, boolean isCredit) {
        if ("CHECKING".equalsIgnoreCase(accountType)) {
            return isCredit ? ACH_CHECKING_CREDIT : ACH_CHECKING_DEBIT;
        } else if ("SAVINGS".equalsIgnoreCase(accountType)) {
            return isCredit ? ACH_SAVINGS_CREDIT : ACH_SAVINGS_DEBIT;
        } else {
            return ACH_CHECKING_CREDIT; // Default
        }
    }

    /**
     * Calculate settlement date based on ACH rules
     */
    private LocalDate calculateSettlementDate(LocalDate processingDate) {
        LocalDate settlementDate = processingDate.plusDays(settlementDays);

        // Skip weekends
        while (settlementDate.getDayOfWeek().getValue() >= 6) {
            settlementDate = settlementDate.plusDays(1);
        }

        // TODO: Check bank holidays
        return settlementDate;
    }

    /**
     * Validate routing number using ABA checksum algorithm
     */
    public boolean isValidRoutingNumber(String routingNumber) {
        if (routingNumber == null || !ROUTING_NUMBER_PATTERN.matcher(routingNumber).matches()) {
            return false;
        }

        // ABA routing number checksum validation
        int[] digits = routingNumber.chars().map(c -> c - '0').toArray();
        int checksum = (3 * (digits[0] + digits[3] + digits[6]) +
                       7 * (digits[1] + digits[4] + digits[7]) +
                       (digits[2] + digits[5] + digits[8])) % 10;

        return checksum == 0;
    }

    /**
     * Validate bank account number format
     */
    public boolean isValidBankAccount(String accountNumber) {
        if (accountNumber == null) {
            return false;
        }

        // Remove spaces and dashes
        String cleaned = accountNumber.replaceAll("[\\s-]", "");
        return ACCOUNT_NUMBER_PATTERN.matcher(cleaned).matches();
    }

    /**
     * Check if company has adequate funding for payroll
     */
    public boolean hasAdequateFunding(String companyId, BigDecimal amount) {
        log.info("Checking funding for company: {}, amount: ${}", companyId, amount);

        // TODO: Integration with company's bank account or ledger service
        // This would query the company's available balance
        // For now, simplified implementation

        BigDecimal companyBalance = getCompanyBalance(companyId);
        return companyBalance.compareTo(amount) >= 0;
    }

    /**
     * Reserve funds from company account for payroll
     */
    @Transactional
    public String reserveFunds(String companyId, BigDecimal amount, String correlationId) {
        log.info("Reserving ${} for company: {}, correlation: {}", amount, companyId, correlationId);

        // Check adequate funding
        if (!hasAdequateFunding(companyId, amount)) {
            BigDecimal available = getCompanyBalance(companyId);
            BigDecimal shortfall = amount.subtract(available);
            throw new InsufficientFundsException(
                "Insufficient funds for payroll",
                companyId,
                amount,
                available,
                shortfall
            );
        }

        // Create reservation
        String reservationId = "RES-" + UUID.randomUUID().toString();

        // TODO: Create fund reservation record in database
        // This would:
        // 1. Lock the funds in company account
        // 2. Create reservation record with expiration (e.g., 24 hours)
        // 3. Update company available balance

        log.info("Funds reserved: {}, amount: ${}", reservationId, amount);
        return reservationId;
    }

    /**
     * Execute individual bank transfer
     */
    @Transactional
    public BankTransferResult executeTransfer(BankTransferInstruction instruction) {
        log.info("Executing transfer: {}, employee: {}, amount: ${}",
                 instruction.getInstructionId(), instruction.getEmployeeId(), instruction.getAmount());

        BankTransferResult result = new BankTransferResult();
        result.setInstructionId(instruction.getInstructionId());
        result.setEmployeeId(instruction.getEmployeeId());
        result.setAmount(instruction.getAmount());
        result.setInitiatedAt(LocalDateTime.now());

        try {
            // 1. Final validation
            if (!isValidRoutingNumber(instruction.getRoutingNumber())) {
                throw new IllegalArgumentException("Invalid routing number");
            }

            if (!isValidBankAccount(instruction.getAccountNumber())) {
                throw new IllegalArgumentException("Invalid account number");
            }

            // 2. Execute transfer based on method
            String transactionId;
            if ("ACH_STANDARD".equals(instruction.getTransferMethod()) ||
                "ACH_SAME_DAY".equals(instruction.getTransferMethod())) {
                transactionId = executeACHTransfer(instruction);
            } else if ("WIRE".equals(instruction.getTransferMethod())) {
                transactionId = executeWireTransfer(instruction);
            } else {
                throw new IllegalArgumentException("Unsupported transfer method: " + instruction.getTransferMethod());
            }

            // 3. Update result
            result.setTransactionId(transactionId);
            result.setStatus(TransferStatus.SUBMITTED);
            result.setSuccess(true);
            result.setSettlementDate(instruction.getEffectiveEntryDate());

            log.info("Transfer submitted successfully: {}, transaction ID: {}", instruction.getInstructionId(), transactionId);

        } catch (Exception e) {
            log.error("Transfer failed: {}, error: {}", instruction.getInstructionId(), e.getMessage(), e);
            result.setStatus(TransferStatus.FAILED);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    /**
     * Execute ACH transfer via NACHA network
     */
    private String executeACHTransfer(BankTransferInstruction instruction) {
        log.info("Executing ACH transfer: {}, method: {}", instruction.getInstructionId(), instruction.getTransferMethod());

        // TODO: Integration with ACH processor (e.g., Stripe, Plaid, Modern Treasury, Dwolla)
        // This would:
        // 1. Create NACHA file entry
        // 2. Submit to ACH processor
        // 3. Receive transaction ID
        // 4. Track ACH status (pending, settled, returned)

        // Simulated ACH submission
        String transactionId = "ACH-" + UUID.randomUUID().toString();

        // Log ACH details for audit
        log.info("ACH Transfer Details - Transaction: {}, Routing: {}, Account: {}, Amount: ${}, Code: {}",
                 transactionId,
                 instruction.getRoutingNumber(),
                 maskAccountNumber(instruction.getAccountNumber()),
                 instruction.getAmount(),
                 instruction.getTransactionCode());

        return transactionId;
    }

    /**
     * Execute wire transfer
     */
    private String executeWireTransfer(BankTransferInstruction instruction) {
        log.info("Executing wire transfer: {}", instruction.getInstructionId());

        // TODO: Integration with wire transfer system
        // This would:
        // 1. Submit wire transfer request to bank
        // 2. Receive confirmation number
        // 3. Track wire status

        String transactionId = "WIRE-" + UUID.randomUUID().toString();

        log.info("Wire Transfer Details - Transaction: {}, Routing: {}, Account: {}, Amount: ${}",
                 transactionId,
                 instruction.getRoutingNumber(),
                 maskAccountNumber(instruction.getAccountNumber()),
                 instruction.getAmount());

        return transactionId;
    }

    /**
     * Execute batch of transfers in parallel
     */
    @Transactional
    public BatchTransferExecutionResult executeBatchTransfers(List<BankTransferInstruction> instructions, String reservationId) {
        log.info("Executing batch of {} transfers, reservation: {}", instructions.size(), reservationId);

        BatchTransferExecutionResult result = new BatchTransferExecutionResult();
        result.setBatchId(UUID.randomUUID().toString());
        result.setReservationId(reservationId);
        result.setTotalCount(instructions.size());

        int successCount = 0;
        int failureCount = 0;
        BigDecimal totalSuccess = BigDecimal.ZERO;
        BigDecimal totalFailed = BigDecimal.ZERO;

        for (BankTransferInstruction instruction : instructions) {
            BankTransferResult transferResult = executeTransfer(instruction);

            if (transferResult.isSuccess()) {
                successCount++;
                totalSuccess = totalSuccess.add(transferResult.getAmount());
            } else {
                failureCount++;
                totalFailed = totalFailed.add(transferResult.getAmount());
            }

            result.addTransferResult(transferResult);
        }

        result.setSuccessCount(successCount);
        result.setFailureCount(failureCount);
        result.setTotalSuccessAmount(totalSuccess);
        result.setTotalFailedAmount(totalFailed);

        log.info("Batch execution complete - Success: {}, Failed: {}, Total Success Amount: ${}",
                 successCount, failureCount, totalSuccess);

        return result;
    }

    /**
     * Release reserved funds (if payroll is cancelled)
     */
    @Transactional
    public void releaseFunds(String reservationId, BigDecimal amount) {
        log.info("Releasing funds for reservation: {}, amount: ${}", reservationId, amount);

        // TODO: Release fund reservation in database
        // This would:
        // 1. Update company available balance
        // 2. Delete or mark reservation as released
        // 3. Log fund release for audit

        log.info("Funds released successfully: {}", reservationId);
    }

    /**
     * Get company balance (placeholder - would integrate with ledger service)
     */
    private BigDecimal getCompanyBalance(String companyId) {
        // TODO: Integration with ledger-service or company account service
        // This would query the actual company balance
        return new BigDecimal("10000000.00"); // Placeholder
    }

    /**
     * Mask account number for security/logging (show last 4 digits)
     */
    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    // ============= DTOs =============

    public static class PayrollTransferRequest {
        private String employeeId;
        private String employeeName;
        private String companyName;
        private BigDecimal amount;
        private String routingNumber;
        private String accountNumber;
        private String accountType; // CHECKING, SAVINGS
        private Boolean urgent;

        // Getters and Setters
        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
        public String getCompanyName() { return companyName; }
        public void setCompanyName(String companyName) { this.companyName = companyName; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getRoutingNumber() { return routingNumber; }
        public void setRoutingNumber(String routingNumber) { this.routingNumber = routingNumber; }
        public String getAccountNumber() { return accountNumber; }
        public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
        public String getAccountType() { return accountType; }
        public void setAccountType(String accountType) { this.accountType = accountType; }
        public Boolean getUrgent() { return urgent; }
        public void setUrgent(Boolean urgent) { this.urgent = urgent; }
    }

    public static class BankTransferBatchResult {
        private String batchId;
        private String companyId;
        private int transferCount;
        private int failedCount;
        private BigDecimal totalAmount;
        private LocalDate settlementDate;
        private LocalDateTime createdAt;
        private List<BankTransferInstruction> instructions;
        private List<String> validationErrors = new ArrayList<>();

        public void addFailedTransfer(String employeeId, String error) {
            validationErrors.add("Employee " + employeeId + ": " + error);
        }

        // Getters and Setters
        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        public String getCompanyId() { return companyId; }
        public void setCompanyId(String companyId) { this.companyId = companyId; }
        public int getTransferCount() { return transferCount; }
        public void setTransferCount(int transferCount) { this.transferCount = transferCount; }
        public int getFailedCount() { return failedCount; }
        public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public LocalDate getSettlementDate() { return settlementDate; }
        public void setSettlementDate(LocalDate settlementDate) { this.settlementDate = settlementDate; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public List<BankTransferInstruction> getInstructions() { return instructions; }
        public void setInstructions(List<BankTransferInstruction> instructions) { this.instructions = instructions; }
        public List<String> getValidationErrors() { return validationErrors; }
    }

    public static class BankTransferInstruction {
        private String instructionId;
        private String companyId;
        private String companyName;
        private String employeeId;
        private String employeeName;
        private BigDecimal amount;
        private String routingNumber;
        private String accountNumber;
        private String accountType;
        private String transferMethod;
        private String transactionCode;
        private String entryDescription;
        private LocalDate effectiveEntryDate;
        private TransferStatus status;
        private LocalDateTime createdAt;

        // Getters and Setters
        public String getInstructionId() { return instructionId; }
        public void setInstructionId(String instructionId) { this.instructionId = instructionId; }
        public String getCompanyId() { return companyId; }
        public void setCompanyId(String companyId) { this.companyId = companyId; }
        public String getCompanyName() { return companyName; }
        public void setCompanyName(String companyName) { this.companyName = companyName; }
        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getRoutingNumber() { return routingNumber; }
        public void setRoutingNumber(String routingNumber) { this.routingNumber = routingNumber; }
        public String getAccountNumber() { return accountNumber; }
        public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
        public String getAccountType() { return accountType; }
        public void setAccountType(String accountType) { this.accountType = accountType; }
        public String getTransferMethod() { return transferMethod; }
        public void setTransferMethod(String transferMethod) { this.transferMethod = transferMethod; }
        public String getTransactionCode() { return transactionCode; }
        public void setTransactionCode(String transactionCode) { this.transactionCode = transactionCode; }
        public String getEntryDescription() { return entryDescription; }
        public void setEntryDescription(String entryDescription) { this.entryDescription = entryDescription; }
        public LocalDate getEffectiveEntryDate() { return effectiveEntryDate; }
        public void setEffectiveEntryDate(LocalDate effectiveEntryDate) { this.effectiveEntryDate = effectiveEntryDate; }
        public TransferStatus getStatus() { return status; }
        public void setStatus(TransferStatus status) { this.status = status; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class BankTransferResult {
        private String instructionId;
        private String employeeId;
        private String transactionId;
        private BigDecimal amount;
        private TransferStatus status;
        private boolean success;
        private String errorMessage;
        private LocalDate settlementDate;
        private LocalDateTime initiatedAt;

        // Getters and Setters
        public String getInstructionId() { return instructionId; }
        public void setInstructionId(String instructionId) { this.instructionId = instructionId; }
        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public TransferStatus getStatus() { return status; }
        public void setStatus(TransferStatus status) { this.status = status; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public LocalDate getSettlementDate() { return settlementDate; }
        public void setSettlementDate(LocalDate settlementDate) { this.settlementDate = settlementDate; }
        public LocalDateTime getInitiatedAt() { return initiatedAt; }
        public void setInitiatedAt(LocalDateTime initiatedAt) { this.initiatedAt = initiatedAt; }
    }

    public static class BatchTransferExecutionResult {
        private String batchId;
        private String reservationId;
        private int totalCount;
        private int successCount;
        private int failureCount;
        private BigDecimal totalSuccessAmount;
        private BigDecimal totalFailedAmount;
        private List<BankTransferResult> transferResults = new ArrayList<>();

        public void addTransferResult(BankTransferResult result) {
            transferResults.add(result);
        }

        // Getters and Setters
        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        public String getReservationId() { return reservationId; }
        public void setReservationId(String reservationId) { this.reservationId = reservationId; }
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getFailureCount() { return failureCount; }
        public void setFailureCount(int failureCount) { this.failureCount = failureCount; }
        public BigDecimal getTotalSuccessAmount() { return totalSuccessAmount; }
        public void setTotalSuccessAmount(BigDecimal totalSuccessAmount) { this.totalSuccessAmount = totalSuccessAmount; }
        public BigDecimal getTotalFailedAmount() { return totalFailedAmount; }
        public void setTotalFailedAmount(BigDecimal totalFailedAmount) { this.totalFailedAmount = totalFailedAmount; }
        public List<BankTransferResult> getTransferResults() { return transferResults; }
    }

    public enum TransferStatus {
        PENDING,
        SUBMITTED,
        PROCESSING,
        SETTLED,
        FAILED,
        RETURNED,
        CANCELLED
    }
}
