package com.waqiti.payment.ach;

import com.waqiti.common.exceptions.BusinessException;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.payment.ach.dto.*;
import com.waqiti.payment.ach.nacha.NACHAFileGenerator;
import com.waqiti.payment.ach.repository.ACHTransactionRepository;
import com.waqiti.payment.wallet.WalletService;
import com.waqiti.payment.compliance.ComplianceService;
import com.waqiti.payment.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.security.SecureRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectDepositACHService {

    private final ACHTransactionRepository achRepository;
    private final WalletService walletService;
    private final BankAccountService bankAccountService;
    private final NACHAFileGenerator nachaGenerator;
    private final ACHNetworkClient achNetworkClient;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final RoutingNumberValidator routingValidator;
    private final ACHLimitService limitService;
    private final RecurringTransferService recurringService;
    private final ACHBatchProcessorService batchProcessorService;

    @Value("${ach.originator.id}")
    private String originatorId;

    @Value("${ach.originator.name}")
    private String originatorName;

    @Value("${ach.batch.cutoff.time:14:00}")
    private String batchCutoffTime;

    @Value("${ach.max.transaction.amount:25000}")
    private BigDecimal maxTransactionAmount;

    @Value("${ach.daily.limit:100000}")
    private BigDecimal dailyLimit;

    @Value("${ach.processing.days:3}")
    private int standardProcessingDays;

    @Transactional
    public DirectDepositSetup setupDirectDeposit(DirectDepositRequest request) {
        log.info("Setting up direct deposit for user: {}", securityContext.getUserId());
        
        // Validate bank account
        validateBankAccount(request);
        
        // Check compliance
        performComplianceCheck(request);
        
        // Create or update bank account
        BankAccount bankAccount = createOrUpdateBankAccount(request);
        
        // Generate direct deposit information
        DirectDepositSetup setup = DirectDepositSetup.builder()
                .id(UUID.randomUUID())
                .userId(securityContext.getUserId())
                .bankAccountId(bankAccount.getId())
                .accountNumber(generateVirtualAccountNumber())
                .routingNumber(getCompanyRoutingNumber())
                .accountType(request.getAccountType())
                .employerName(request.getEmployerName())
                .status(DirectDepositStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
        
        // Save setup
        setup = directDepositRepository.save(setup);
        
        // Generate employer forms
        generateEmployerForms(setup);
        
        // Send confirmation
        sendDirectDepositConfirmation(setup);
        
        log.info("Direct deposit setup completed: {}", setup.getId());
        return setup;
    }

    @Transactional
    public ACHTransfer initiateACHTransfer(ACHTransferRequest request) {
        log.info("Initiating ACH transfer for user: {}", securityContext.getUserId());
        
        // Validate transfer
        validateACHTransfer(request);
        
        // Check limits
        checkTransferLimits(request);
        
        // Perform compliance checks
        performACHComplianceCheck(request);
        
        // Create ACH transaction
        ACHTransfer transfer = ACHTransfer.builder()
                .id(UUID.randomUUID())
                .userId(securityContext.getUserId())
                .type(request.getTransferType())
                .direction(request.getDirection())
                .amount(request.getAmount())
                .currency("USD")
                .status(ACHStatus.PENDING)
                .sourceAccountId(request.getSourceAccountId())
                .destinationAccountId(request.getDestinationAccountId())
                .description(request.getDescription())
                .secCode(determineSecCode(request))
                .effectiveDate(calculateEffectiveDate(request))
                .traceNumber(generateTraceNumber())
                .initiatedAt(Instant.now())
                .build();
        
        // Set beneficiary information
        if (request.getBeneficiary() != null) {
            transfer.setBeneficiaryName(request.getBeneficiary().getName());
            transfer.setBeneficiaryAccount(request.getBeneficiary().getAccountNumber());
            transfer.setBeneficiaryRouting(request.getBeneficiary().getRoutingNumber());
            transfer.setBeneficiaryType(request.getBeneficiary().getAccountType());
        }
        
        // Handle recurring transfers
        if (request.isRecurring()) {
            setupRecurringTransfer(transfer, request.getRecurringConfig());
        }
        
        // Save transaction
        transfer = achRepository.save(transfer);
        
        // Process based on type
        if (request.isImmediate()) {
            processSameDayACH(transfer);
        } else {
            addToBatchQueue(transfer);
        }
        
        // Send confirmation
        sendACHInitiationConfirmation(transfer);
        
        log.info("ACH transfer initiated: {}", transfer.getId());
        return transfer;
    }

    @Transactional
    public ACHTransfer initiateDirectDebit(DirectDebitRequest request) {
        log.info("Initiating direct debit for user: {}", securityContext.getUserId());
        
        // Validate authorization
        validateDirectDebitAuthorization(request);
        
        // Check debit limits
        checkDebitLimits(request);
        
        // Create ACH debit transaction
        ACHTransfer debit = ACHTransfer.builder()
                .id(UUID.randomUUID())
                .userId(securityContext.getUserId())
                .type(TransferType.DEBIT)
                .direction(TransferDirection.INCOMING)
                .amount(request.getAmount())
                .currency("USD")
                .status(ACHStatus.PENDING_AUTHORIZATION)
                .sourceAccountId(request.getSourceAccountId())
                .destinationAccountId(request.getDestinationWalletId())
                .description(request.getDescription())
                .secCode(SECCode.WEB)
                .authorizationId(request.getAuthorizationId())
                .effectiveDate(calculateEffectiveDate(request))
                .traceNumber(generateTraceNumber())
                .initiatedAt(Instant.now())
                .build();
        
        // Save transaction
        debit = achRepository.save(debit);
        
        // Request customer authorization if needed
        if (!hasPreAuthorization(request)) {
            requestDebitAuthorization(debit);
        } else {
            debit.setStatus(ACHStatus.PENDING);
            addToBatchQueue(debit);
        }
        
        log.info("Direct debit initiated: {}", debit.getId());
        return debit;
    }

    @Scheduled(cron = "0 0 14 * * MON-FRI") // Run at 2 PM on weekdays
    public void processDailyACHBatch() {
        log.info("Processing daily ACH batch");
        
        LocalDate today = LocalDate.now();
        List<ACHTransfer> pendingTransfers = achRepository.findPendingTransfers(today);
        
        if (pendingTransfers.isEmpty()) {
            log.info("No pending ACH transfers for today");
            return;
        }
        
        // Group transfers by type and direction
        Map<String, List<ACHTransfer>> batches = groupTransfersForBatching(pendingTransfers);
        
        // Process each batch
        for (Map.Entry<String, List<ACHTransfer>> entry : batches.entrySet()) {
            batchProcessorService.processBatch(entry.getKey(), entry.getValue());
        }
        
        log.info("Daily ACH batch processing completed. Processed {} transfers", pendingTransfers.size());
    }


    @Scheduled(fixedDelay = 300000) // Run every 5 minutes
    public void checkACHReturns() {
        log.info("Checking for ACH returns");
        
        List<ACHReturn> returns = achNetworkClient.getReturns();
        
        for (ACHReturn achReturn : returns) {
            processACHReturn(achReturn);
        }
        
        if (!returns.isEmpty()) {
            log.info("Processed {} ACH returns", returns.size());
        }
    }

    @Transactional
    public void processACHReturn(ACHReturn achReturn) {
        log.info("Processing ACH return: {}", achReturn.getTraceNumber());
        
        ACHTransfer transfer = achRepository.findByTraceNumber(achReturn.getTraceNumber())
                .orElseThrow(() -> new BusinessException("Transfer not found for return"));
        
        transfer.setStatus(ACHStatus.RETURNED);
        transfer.setReturnCode(achReturn.getReturnCode());
        transfer.setReturnReason(achReturn.getReturnReason());
        transfer.setReturnedAt(Instant.now());
        achRepository.save(transfer);
        
        // Reverse the transaction
        reverseACHTransaction(transfer, achReturn);
        
        // Notify user
        notificationService.sendACHReturnNotification(transfer, achReturn);
        
        // Handle based on return code
        handleReturnCode(transfer, achReturn);
    }

    @Transactional
    public void cancelACHTransfer(UUID transferId) {
        log.info("Cancelling ACH transfer: {}", transferId);
        
        ACHTransfer transfer = achRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("Transfer not found"));
        
        // Verify ownership
        if (!transfer.getUserId().equals(securityContext.getUserId())) {
            throw new BusinessException("Unauthorized access to transfer");
        }
        
        // Check if cancellable
        if (!isCancellable(transfer)) {
            throw new BusinessException("Transfer cannot be cancelled in status: " + transfer.getStatus());
        }
        
        transfer.setStatus(ACHStatus.CANCELLED);
        transfer.setCancelledAt(Instant.now());
        achRepository.save(transfer);
        
        // Notify user
        notificationService.sendACHCancellationConfirmation(transfer);
        
        log.info("ACH transfer cancelled: {}", transferId);
    }

    public ACHTransfer getTransferStatus(UUID transferId) {
        ACHTransfer transfer = achRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("Transfer not found"));
        
        // Verify ownership
        if (!transfer.getUserId().equals(securityContext.getUserId())) {
            throw new BusinessException("Unauthorized access to transfer");
        }
        
        // Check for updates from ACH network
        if (transfer.getStatus() == ACHStatus.SUBMITTED) {
            updateTransferStatus(transfer);
        }
        
        return transfer;
    }

    public List<ACHTransfer> getUserTransfers(TransferFilter filter) {
        return achRepository.findByUserIdAndFilter(
                securityContext.getUserId(),
                filter.getStatus(),
                filter.getType(),
                filter.getFromDate(),
                filter.getToDate()
        );
    }

    @Transactional
    public MicroDeposit initiateMicroDeposits(UUID bankAccountId) {
        log.info("Initiating micro deposits for account: {}", bankAccountId);
        
        BankAccount account = bankAccountService.getBankAccount(bankAccountId);
        
        // Generate random amounts
        BigDecimal amount1 = generateMicroDepositAmount();
        BigDecimal amount2 = generateMicroDepositAmount();
        
        // Create micro deposit transfers
        ACHTransfer deposit1 = createMicroDeposit(account, amount1, "Verification deposit 1");
        ACHTransfer deposit2 = createMicroDeposit(account, amount2, "Verification deposit 2");
        
        // Save micro deposit record
        MicroDeposit microDeposit = MicroDeposit.builder()
                .id(UUID.randomUUID())
                .bankAccountId(bankAccountId)
                .amount1(amount1)
                .amount2(amount2)
                .transfer1Id(deposit1.getId())
                .transfer2Id(deposit2.getId())
                .status(MicroDepositStatus.PENDING)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        
        microDepositRepository.save(microDeposit);
        
        // Add to batch queue
        addToBatchQueue(deposit1);
        addToBatchQueue(deposit2);
        
        log.info("Micro deposits initiated: {}", microDeposit.getId());
        return microDeposit;
    }

    @Transactional
    public boolean verifyMicroDeposits(UUID bankAccountId, BigDecimal amount1, BigDecimal amount2) {
        log.info("Verifying micro deposits for account: {}", bankAccountId);
        
        MicroDeposit microDeposit = microDepositRepository.findByBankAccountId(bankAccountId)
                .orElseThrow(() -> new BusinessException("Micro deposits not found"));
        
        // Check expiration
        if (microDeposit.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("Micro deposit verification expired");
        }
        
        // Verify amounts
        boolean verified = microDeposit.getAmount1().compareTo(amount1) == 0 &&
                          microDeposit.getAmount2().compareTo(amount2) == 0;
        
        if (verified) {
            microDeposit.setStatus(MicroDepositStatus.VERIFIED);
            microDeposit.setVerifiedAt(Instant.now());
            
            // Mark bank account as verified
            BankAccount account = bankAccountService.getBankAccount(bankAccountId);
            account.setVerified(true);
            account.setVerifiedAt(Instant.now());
            bankAccountService.save(account);
            
            // Reverse micro deposits
            reverseMicroDeposits(microDeposit);
        } else {
            microDeposit.setAttempts(microDeposit.getAttempts() + 1);
            
            if (microDeposit.getAttempts() >= 3) {
                microDeposit.setStatus(MicroDepositStatus.FAILED);
                throw new BusinessException("Maximum verification attempts exceeded");
            }
        }
        
        microDepositRepository.save(microDeposit);
        
        log.info("Micro deposit verification {}: {}", verified ? "successful" : "failed", bankAccountId);
        return verified;
    }

    private void validateBankAccount(DirectDepositRequest request) {
        // Validate routing number
        if (!routingValidator.isValid(request.getRoutingNumber())) {
            throw new BusinessException("Invalid routing number");
        }
        
        // Validate account number
        if (!isValidAccountNumber(request.getAccountNumber())) {
            throw new BusinessException("Invalid account number");
        }
        
        // Check if bank supports ACH
        if (!routingValidator.supportsACH(request.getRoutingNumber())) {
            throw new BusinessException("Bank does not support ACH transfers");
        }
    }

    private void validateACHTransfer(ACHTransferRequest request) {
        // Validate amount
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Transfer amount must be positive");
        }
        
        if (request.getAmount().compareTo(maxTransactionAmount) > 0) {
            throw new BusinessException("Transfer amount exceeds maximum limit of $" + maxTransactionAmount);
        }
        
        // Validate accounts
        if (!bankAccountService.isVerified(request.getSourceAccountId())) {
            throw new BusinessException("Source bank account not verified");
        }
        
        if (!bankAccountService.isVerified(request.getDestinationAccountId())) {
            throw new BusinessException("Destination bank account not verified");
        }
    }

    private void checkTransferLimits(ACHTransferRequest request) {
        BigDecimal dailyTotal = achRepository.getDailyTotal(
                securityContext.getUserId(),
                LocalDate.now()
        );
        
        if (dailyTotal.add(request.getAmount()).compareTo(dailyLimit) > 0) {
            throw new BusinessException("Transfer would exceed daily limit of $" + dailyLimit);
        }
        
        // Check velocity limits
        if (!limitService.checkVelocityLimits(securityContext.getUserId(), request.getAmount())) {
            throw new BusinessException("Transfer exceeds velocity limits");
        }
    }

    private void performComplianceCheck(DirectDepositRequest request) {
        ComplianceCheckResult result = complianceService.checkDirectDeposit(
                securityContext.getUserId(),
                request.getEmployerName(),
                request.getAmount()
        );
        
        if (!result.isPassed()) {
            throw new BusinessException("Compliance check failed: " + result.getReason());
        }
    }

    private void performACHComplianceCheck(ACHTransferRequest request) {
        ComplianceCheckResult result = complianceService.checkACHTransfer(
                securityContext.getUserId(),
                request.getAmount(),
                request.getBeneficiary()
        );
        
        if (!result.isPassed()) {
            throw new BusinessException("Compliance check failed: " + result.getReason());
        }
    }

    private BankAccount createOrUpdateBankAccount(DirectDepositRequest request) {
        Optional<BankAccount> existing = bankAccountService.findByRoutingAndAccount(
                request.getRoutingNumber(),
                request.getAccountNumber()
        );
        
        if (existing.isPresent()) {
            return existing.get();
        }
        
        return bankAccountService.createBankAccount(
                securityContext.getUserId(),
                request.getAccountHolderName(),
                request.getRoutingNumber(),
                request.getAccountNumber(),
                request.getAccountType()
        );
    }

    private String generateVirtualAccountNumber() {
        // Generate unique virtual account number for direct deposits
        return "VRT" + System.currentTimeMillis() + securityContext.getUserId().toString().substring(0, 8);
    }

    private String getCompanyRoutingNumber() {
        // Return company's routing number for receiving direct deposits
        return originatorId;
    }

    private void generateEmployerForms(DirectDepositSetup setup) {
        // Generate direct deposit authorization forms for employer
        CompletableFuture.runAsync(() -> {
            try {
                byte[] formData = pdfGenerator.generateDirectDepositForm(setup);
                notificationService.sendDirectDepositForm(setup.getUserId(), formData);
            } catch (Exception e) {
                log.error("Error generating employer forms", e);
            }
        });
    }

    private SECCode determineSecCode(ACHTransferRequest request) {
        if (request.isConsumerAccount()) {
            return request.isRecurring() ? SECCode.PPD : SECCode.WEB;
        } else {
            return SECCode.CCD;
        }
    }

    private LocalDate calculateEffectiveDate(ACHTransferRequest request) {
        if (request.getEffectiveDate() != null) {
            return request.getEffectiveDate();
        }
        
        LocalDate today = LocalDate.now();
        LocalTime cutoff = LocalTime.parse(batchCutoffTime);
        
        if (LocalTime.now().isAfter(cutoff)) {
            today = today.plusDays(1);
        }
        
        // Skip weekends and holidays
        while (isNonProcessingDay(today)) {
            today = today.plusDays(1);
        }
        
        return today.plusDays(standardProcessingDays);
    }

    private String generateTraceNumber() {
        return originatorId + System.currentTimeMillis();
    }

    private void processSameDayACH(ACHTransfer transfer) {
        log.info("Processing same-day ACH: {}", transfer.getId());
        
        // Same-day ACH has specific windows
        if (!isWithinSameDayWindow()) {
            throw new BusinessException("Outside same-day ACH processing window");
        }
        
        // Additional fee for same-day
        transfer.setFee(BigDecimal.valueOf(25));
        
        // Process immediately
        CompletableFuture.runAsync(() -> {
            try {
                NACHAFile nachaFile = nachaGenerator.generateSingleTransferFile(transfer, originatorId, originatorName);
                ACHSubmissionResult result = achNetworkClient.submitSameDay(nachaFile);
                
                if (result.isSuccess()) {
                    transfer.setStatus(ACHStatus.SUBMITTED);
                    transfer.setBatchId(result.getBatchId());
                    transfer.setSubmittedAt(Instant.now());
                } else {
                    transfer.setStatus(ACHStatus.FAILED);
                    transfer.setFailureReason(result.getErrorMessage());
                }
                
                achRepository.save(transfer);
            } catch (Exception e) {
                log.error("Error processing same-day ACH", e);
                transfer.setStatus(ACHStatus.FAILED);
                transfer.setFailureReason(e.getMessage());
                achRepository.save(transfer);
            }
        });
    }

    private void addToBatchQueue(ACHTransfer transfer) {
        transfer.setStatus(ACHStatus.QUEUED);
        achRepository.save(transfer);
    }

    private void setupRecurringTransfer(ACHTransfer transfer, RecurringConfig config) {
        recurringService.setupRecurringTransfer(
                transfer.getId(),
                config.getFrequency(),
                config.getStartDate(),
                config.getEndDate(),
                config.getOccurrences()
        );
    }

    private boolean hasPreAuthorization(DirectDebitRequest request) {
        return request.getAuthorizationId() != null &&
               bankAccountService.hasValidAuthorization(request.getAuthorizationId());
    }

    private void requestDebitAuthorization(ACHTransfer debit) {
        notificationService.sendDebitAuthorizationRequest(
                debit.getUserId(),
                debit.getId(),
                debit.getAmount(),
                debit.getDescription()
        );
    }

    private Map<String, List<ACHTransfer>> groupTransfersForBatching(List<ACHTransfer> transfers) {
        return transfers.stream()
                .collect(Collectors.groupingBy(t -> 
                    t.getSecCode() + "_" + t.getDirection() + "_" + t.getType()
                ));
    }

    private void processOutgoingTransfer(ACHTransfer transfer) {
        walletService.debit(
                transfer.getSourceAccountId(),
                transfer.getAmount(),
                "ACH Transfer: " + transfer.getDescription()
        );
    }

    private void handleBatchSubmissionFailure(List<ACHTransfer> transfers, String errorMessage) {
        for (ACHTransfer transfer : transfers) {
            transfer.setStatus(ACHStatus.FAILED);
            transfer.setFailureReason(errorMessage);
            achRepository.save(transfer);
            
            notificationService.sendACHFailureNotification(transfer, errorMessage);
        }
    }

    private void handleBatchProcessingError(List<ACHTransfer> transfers, String errorMessage) {
        for (ACHTransfer transfer : transfers) {
            transfer.setStatus(ACHStatus.ERROR);
            transfer.setErrorMessage(errorMessage);
            achRepository.save(transfer);
        }
    }

    private void reverseACHTransaction(ACHTransfer transfer, ACHReturn achReturn) {
        if (transfer.getDirection() == TransferDirection.INCOMING) {
            // Reverse credit
            walletService.debit(
                    transfer.getDestinationAccountId(),
                    transfer.getAmount(),
                    "ACH Return: " + achReturn.getReturnReason()
            );
        } else {
            // Reverse debit
            walletService.credit(
                    transfer.getSourceAccountId(),
                    transfer.getAmount(),
                    "ACH Return Reversal: " + achReturn.getReturnReason()
            );
        }
    }

    private void handleReturnCode(ACHTransfer transfer, ACHReturn achReturn) {
        switch (achReturn.getReturnCode()) {
            case "R01": // Insufficient funds
                handleInsufficientFunds(transfer);
                break;
            case "R02": // Account closed
                handleAccountClosed(transfer);
                break;
            case "R03": // No account
                handleNoAccount(transfer);
                break;
            case "R29": // Corporate customer advises not authorized
                handleUnauthorized(transfer);
                break;
            default:
                log.warn("Unhandled return code: {}", achReturn.getReturnCode());
        }
    }

    private void handleInsufficientFunds(ACHTransfer transfer) {
        // Mark account for monitoring
        bankAccountService.flagInsufficientFunds(transfer.getSourceAccountId());
        
        // Retry with smaller amount if configured
        if (transfer.isRetryable()) {
            scheduleRetry(transfer, 3);
        }
    }

    private void handleAccountClosed(ACHTransfer transfer) {
        // Deactivate bank account
        bankAccountService.deactivateAccount(transfer.getSourceAccountId(), "Account closed");
    }

    private void handleNoAccount(ACHTransfer transfer) {
        // Mark account as invalid
        bankAccountService.markAsInvalid(transfer.getSourceAccountId());
    }

    private void handleUnauthorized(ACHTransfer transfer) {
        // Revoke authorization
        bankAccountService.revokeAuthorization(transfer.getAuthorizationId());
        
        // Flag for review
        complianceService.flagForReview(transfer.getId(), "Unauthorized ACH return");
    }

    private boolean isCancellable(ACHTransfer transfer) {
        return transfer.getStatus() == ACHStatus.PENDING ||
               transfer.getStatus() == ACHStatus.QUEUED;
    }

    private void updateTransferStatus(ACHTransfer transfer) {
        ACHStatusUpdate update = achNetworkClient.getTransferStatus(transfer.getTraceNumber());
        
        if (update != null && update.getStatus() != transfer.getStatus()) {
            transfer.setStatus(update.getStatus());
            transfer.setUpdatedAt(Instant.now());
            
            if (update.getStatus() == ACHStatus.COMPLETED) {
                transfer.setCompletedAt(update.getCompletedAt());
                
                // Credit destination for incoming transfers
                if (transfer.getDirection() == TransferDirection.INCOMING) {
                    walletService.credit(
                            transfer.getDestinationAccountId(),
                            transfer.getAmount(),
                            "ACH Credit: " + transfer.getDescription()
                    );
                }
            }
            
            achRepository.save(transfer);
        }
    }

    /**
     * Generate cryptographically secure micro-deposit amounts for bank account verification
     * 
     * SECURITY FIX: Replaced Random with SecureRandom to prevent predictable micro-deposit amounts
     * This is critical for preventing attackers from guessing verification amounts
     */
    private BigDecimal generateMicroDepositAmount() {
        SecureRandom secureRandom = new SecureRandom();
        
        // Generate secure random cents between 1-99
        // Using SecureRandom prevents attackers from predicting the micro-deposit amounts
        int cents = secureRandom.nextInt(99) + 1;
        
        // Add additional entropy using timestamp
        long timestamp = System.currentTimeMillis();
        int additionalCents = (int)((timestamp % 10) / 10.0 * 9); // 0-9 based on timestamp
        
        // Combine for final amount (still 1-99 cents range)
        cents = ((cents + additionalCents) % 99) + 1;
        
        // Log for audit trail (without exposing the actual amount)
        log.debug("Generated secure micro-deposit amount for verification");
        
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private ACHTransfer createMicroDeposit(BankAccount account, BigDecimal amount, String description) {
        return ACHTransfer.builder()
                .id(UUID.randomUUID())
                .userId(account.getUserId())
                .type(TransferType.CREDIT)
                .direction(TransferDirection.OUTGOING)
                .amount(amount)
                .currency("USD")
                .status(ACHStatus.PENDING)
                .destinationAccountId(account.getId())
                .description(description)
                .secCode(SECCode.PPD)
                .effectiveDate(LocalDate.now().plusDays(1))
                .traceNumber(generateTraceNumber())
                .isMicroDeposit(true)
                .initiatedAt(Instant.now())
                .build();
    }

    private void reverseMicroDeposits(MicroDeposit microDeposit) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(86400000); // Wait 24 hours
                
                // Create reversal transfers
                ACHTransfer reversal1 = createMicroDepositReversal(
                        microDeposit.getTransfer1Id(),
                        microDeposit.getAmount1()
                );
                ACHTransfer reversal2 = createMicroDepositReversal(
                        microDeposit.getTransfer2Id(),
                        microDeposit.getAmount2()
                );
                
                addToBatchQueue(reversal1);
                addToBatchQueue(reversal2);
                
            } catch (Exception e) {
                log.error("Error reversing micro deposits", e);
            }
        });
    }

    private ACHTransfer createMicroDepositReversal(UUID originalTransferId, BigDecimal amount) {
        ACHTransfer original = achRepository.findById(originalTransferId)
                .orElseThrow(() -> new BusinessException("Original transfer not found"));
        
        return ACHTransfer.builder()
                .id(UUID.randomUUID())
                .userId(original.getUserId())
                .type(TransferType.DEBIT)
                .direction(TransferDirection.INCOMING)
                .amount(amount)
                .currency("USD")
                .status(ACHStatus.PENDING)
                .sourceAccountId(original.getDestinationAccountId())
                .description("Micro deposit reversal")
                .secCode(SECCode.PPD)
                .effectiveDate(LocalDate.now().plusDays(1))
                .traceNumber(generateTraceNumber())
                .originalTransferId(originalTransferId)
                .initiatedAt(Instant.now())
                .build();
    }

    private boolean isValidAccountNumber(String accountNumber) {
        return accountNumber != null && 
               accountNumber.matches("\\d{4,17}");
    }

    private boolean isNonProcessingDay(LocalDate date) {
        // Check weekends
        if (date.getDayOfWeek().getValue() > 5) {
            return true;
        }
        
        // Check federal holidays
        return federalHolidayService.isHoliday(date);
    }

    private boolean isWithinSameDayWindow() {
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(8, 0)) && now.isBefore(LocalTime.of(14, 30));
    }

    private void scheduleRetry(ACHTransfer transfer, int daysLater) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(daysLater * 86400000L);
                
                // Create retry transfer
                ACHTransfer retry = transfer.toBuilder()
                        .id(UUID.randomUUID())
                        .status(ACHStatus.PENDING)
                        .originalTransferId(transfer.getId())
                        .retryAttempt(transfer.getRetryAttempt() + 1)
                        .effectiveDate(LocalDate.now().plusDays(standardProcessingDays))
                        .traceNumber(generateTraceNumber())
                        .initiatedAt(Instant.now())
                        .build();
                
                achRepository.save(retry);
                addToBatchQueue(retry);
                
            } catch (Exception e) {
                log.error("Error scheduling retry", e);
            }
        });
    }

    private void sendDirectDepositConfirmation(DirectDepositSetup setup) {
        notificationService.sendDirectDepositSetupConfirmation(
                setup.getUserId(),
                setup.getAccountNumber(),
                setup.getRoutingNumber()
        );
    }

    private void sendACHInitiationConfirmation(ACHTransfer transfer) {
        notificationService.sendACHTransferInitiated(
                transfer.getUserId(),
                transfer.getId(),
                transfer.getAmount(),
                transfer.getEffectiveDate()
        );
    }
}