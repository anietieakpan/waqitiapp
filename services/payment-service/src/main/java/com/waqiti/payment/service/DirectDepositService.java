package com.waqiti.payment.service;

import com.waqiti.payment.domain.*;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.repository.*;
import com.waqiti.payment.provider.ACHProvider;
import com.waqiti.payment.provider.PlaidProvider;
import com.waqiti.payment.security.ACHSecurityService;
import com.waqiti.common.events.EventPublisher;
import com.waqiti.common.events.DirectDepositEvent;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.exception.*;
import com.waqiti.common.kyc.service.KYCClientService;
import com.waqiti.common.kyc.annotation.RequireKYCVerification;
import com.waqiti.common.kyc.annotation.RequireKYCVerification.VerificationLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Direct Deposit and ACH Service - Manages direct deposits, ACH transfers, and employer integrations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DirectDepositService {

    private final DirectDepositAccountRepository accountRepository;
    private final ACHTransferRepository achTransferRepository;
    private final DirectDepositRepository directDepositRepository;
    private final EmployerRepository employerRepository;
    private final ACHProvider achProvider;
    private final PlaidProvider plaidProvider;
    private final ACHSecurityService securityService;
    private final BankAccountService bankAccountService;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final EventPublisher eventPublisher;
    private final SecurityContext securityContext;
    private final KYCClientService kycClientService;
    
    @Value("${direct-deposit.routing-number}")
    private String waqitiRoutingNumber;
    
    @Value("${direct-deposit.bank-name}")
    private String waqitiBankName;
    
    @Value("${direct-deposit.max-daily-ach:50000.00}")
    private BigDecimal maxDailyACH;
    
    @Value("${direct-deposit.ach-fee:0.00}")
    private BigDecimal achFee;
    
    @Value("${direct-deposit.instant-ach-fee:4.99}")
    private BigDecimal instantAchFee;

    /**
     * Generate direct deposit account for user
     */
    @Transactional
    @RequireKYCVerification(level = VerificationLevel.ADVANCED, action = "DIRECT_DEPOSIT")
    public DirectDepositAccountDto generateDirectDepositAccount(String userId) {
        // Check if account already exists
        Optional<DirectDepositAccount> existing = accountRepository.findByUserId(userId);
        if (existing.isPresent()) {
            return toDirectDepositAccountDto(existing.get());
        }
        
        try {
            // Generate unique account number
            String accountNumber = generateUniqueAccountNumber();
            
            // Create ACH account with provider
            ACHAccountRequest providerRequest = ACHAccountRequest.builder()
                .userId(userId)
                .accountNumber(accountNumber)
                .routingNumber(waqitiRoutingNumber)
                .accountType(ACHAccountType.CHECKING)
                .accountName(getUserFullName(userId))
                .build();
            
            ACHAccountResponse providerResponse = achProvider.createACHAccount(providerRequest);
            
            // Create direct deposit account
            DirectDepositAccount account = DirectDepositAccount.builder()
                .userId(userId)
                .accountNumber(accountNumber)
                .routingNumber(waqitiRoutingNumber)
                .bankName(waqitiBankName)
                .accountType(ACHAccountType.CHECKING)
                .providerAccountId(providerResponse.getProviderAccountId())
                .status(DirectDepositStatus.ACTIVE)
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .metadata(Map.of(
                    "provider", "ach_provider",
                    "created_by", "user_request"
                ))
                .build();
            
            account = accountRepository.save(account);
            
            // Send account details to user
            notificationService.sendDirectDepositAccountCreated(userId, account);
            
            // Publish event
            eventPublisher.publish(DirectDepositEvent.accountCreated(account));
            
            log.info("Created direct deposit account for user {}, account: {}", 
                userId, accountNumber);
            
            return toDirectDepositAccountDto(account);
            
        } catch (Exception e) {
            log.error("Failed to create direct deposit account for user {}", userId, e);
            throw new DirectDepositException("Failed to create direct deposit account", e);
        }
    }

    /**
     * Add employer for direct deposit
     */
    @Transactional
    public EmployerInfoDto addEmployer(String userId, AddEmployerRequest request) {
        DirectDepositAccount account = getActiveAccount(userId);
        
        // Validate employer
        validateEmployer(request);
        
        // Check if employer already added
        if (employerRepository.existsByUserIdAndEmployerName(userId, request.getEmployerName())) {
            throw new DuplicateEmployerException("Employer already added");
        }
        
        try {
            // Generate employee ID if not provided
            String employeeId = request.getEmployeeId() != null ? 
                request.getEmployeeId() : generateEmployeeId(userId);
            
            // Create employer record
            EmployerInfo employer = EmployerInfo.builder()
                .userId(userId)
                .accountId(account.getId())
                .employerName(request.getEmployerName())
                .employerEIN(request.getEmployerEIN())
                .employeeId(employeeId)
                .payrollProvider(request.getPayrollProvider())
                .payFrequency(request.getPayFrequency())
                .expectedPayDays(request.getExpectedPayDays())
                .status(EmployerStatus.PENDING_VERIFICATION)
                .addedAt(Instant.now())
                .metadata(request.getMetadata())
                .build();
            
            employer = employerRepository.save(employer);
            
            // Generate employer-specific deposit instructions
            DirectDepositInstructions instructions = generateEmployerInstructions(account, employer);
            
            // Send instructions to user
            notificationService.sendEmployerAddedNotification(userId, employer, instructions);
            
            // Publish event
            eventPublisher.publish(DirectDepositEvent.employerAdded(account, employer));
            
            log.info("Added employer {} for user {}", request.getEmployerName(), userId);
            
            return toEmployerInfoDto(employer);
            
        } catch (Exception e) {
            log.error("Failed to add employer for user {}", userId, e);
            throw new DirectDepositException("Failed to add employer", e);
        }
    }

    /**
     * Process incoming direct deposit
     */
    @Transactional
    public void processIncomingDirectDeposit(IncomingACHRequest request) {
        DirectDepositAccount account = accountRepository.findByAccountNumberAndRoutingNumber(
            request.getAccountNumber(), request.getRoutingNumber())
            .orElseThrow(() -> new AccountNotFoundException("Direct deposit account not found"));
        
        if (account.getStatus() != DirectDepositStatus.ACTIVE) {
            throw new IllegalStateException("Direct deposit account is not active");
        }
        
        // Validate ACH transaction
        ACHValidationResult validation = achProvider.validateIncomingACH(request);
        if (!validation.isValid()) {
            log.warn("Invalid incoming ACH for account {}: {}", 
                account.getAccountNumber(), validation.getErrorMessage());
            rejectIncomingACH(request, validation.getErrorMessage());
            return;
        }
        
        try {
            // Check for duplicate
            if (isDuplicateTransaction(request)) {
                log.warn("Duplicate ACH transaction detected: {}", request.getTraceNumber());
                return;
            }
            
            // Create direct deposit record
            DirectDeposit deposit = DirectDeposit.builder()
                .accountId(account.getId())
                .userId(account.getUserId())
                .originatorName(request.getOriginatorName())
                .originatorId(request.getOriginatorId())
                .amount(request.getAmount())
                .currency("USD")
                .description(request.getDescription())
                .traceNumber(request.getTraceNumber())
                .settlementDate(request.getSettlementDate())
                .status(DirectDepositStatus.PENDING)
                .receivedAt(Instant.now())
                .type(identifyDepositType(request))
                .metadata(request.getMetadata())
                .build();
            
            deposit = directDepositRepository.save(deposit);
            
            // Process deposit
            processDeposit(deposit, account);
            
            // Update employer info if payroll
            if (deposit.getType() == DirectDepositType.PAYROLL) {
                updateEmployerLastPayment(account.getUserId(), request.getOriginatorName());
            }
            
            log.info("Processed direct deposit {} for user {}, amount: {}", 
                deposit.getId(), account.getUserId(), request.getAmount());
            
        } catch (Exception e) {
            log.error("Failed to process direct deposit for account {}", 
                account.getAccountNumber(), e);
            throw new DirectDepositException("Failed to process direct deposit", e);
        }
    }

    /**
     * Initiate ACH transfer (pull from external account)
     */
    @Transactional
    @RequireKYCVerification(level = VerificationLevel.ADVANCED, action = "ACH_TRANSFER")
    public ACHTransferDto initiateACHTransfer(String userId, InitiateACHRequest request) {
        // Validate request
        validateACHTransfer(request, userId);
        
        // Check daily limits
        BigDecimal todayTransfers = getTodayACHTransfers(userId);
        if (todayTransfers.add(request.getAmount()).compareTo(maxDailyACH) > 0) {
            throw new TransferLimitExceededException("Daily ACH limit exceeded");
        }
        
        // Get linked bank account
        BankAccount bankAccount = bankAccountService.getBankAccount(request.getBankAccountId());
        if (!bankAccount.getUserId().equals(userId)) {
            throw new SecurityException("Bank account does not belong to user");
        }
        
        // Calculate fees
        BigDecimal fee = request.isInstant() ? instantAchFee : achFee;
        BigDecimal totalAmount = request.getAmount().add(fee);
        
        try {
            // Initiate ACH with provider
            ACHTransferRequest providerRequest = ACHTransferRequest.builder()
                .userId(userId)
                .amount(request.getAmount())
                .fromAccount(ACHAccountInfo.builder()
                    .accountNumber(bankAccount.getAccountNumber())
                    .routingNumber(bankAccount.getRoutingNumber())
                    .accountType(bankAccount.getAccountType())
                    .build())
                .toAccount(ACHAccountInfo.builder()
                    .accountNumber(getWaqitiAccountNumber(userId))
                    .routingNumber(waqitiRoutingNumber)
                    .accountType(ACHAccountType.CHECKING)
                    .build())
                .instant(request.isInstant())
                .description(request.getDescription())
                .build();
            
            ACHTransferResponse providerResponse = achProvider.initiateTransfer(providerRequest);
            
            // Create ACH transfer record
            ACHTransfer transfer = ACHTransfer.builder()
                .userId(userId)
                .type(ACHTransferType.DEBIT)
                .direction(ACHDirection.INCOMING)
                .amount(request.getAmount())
                .fee(fee)
                .netAmount(request.getAmount())
                .currency("USD")
                .fromAccountId(request.getBankAccountId())
                .fromAccountMask(bankAccount.getAccountNumberMask())
                .toAccountId(getDirectDepositAccountId(userId))
                .toAccountMask("****" + getWaqitiAccountNumber(userId).substring(
                    getWaqitiAccountNumber(userId).length() - 4))
                .providerTransferId(providerResponse.getTransferId())
                .traceNumber(providerResponse.getTraceNumber())
                .status(ACHStatus.PENDING)
                .instant(request.isInstant())
                .expectedSettlement(providerResponse.getExpectedSettlement())
                .description(request.getDescription())
                .initiatedAt(Instant.now())
                .metadata(request.getMetadata())
                .build();
            
            transfer = achTransferRepository.save(transfer);
            
            // Send notification
            notificationService.sendACHTransferInitiated(userId, transfer);
            
            // Publish event
            eventPublisher.publish(DirectDepositEvent.achTransferInitiated(transfer));
            
            log.info("Initiated ACH transfer {} for user {}, amount: {}", 
                transfer.getId(), userId, request.getAmount());
            
            return toACHTransferDto(transfer);
            
        } catch (Exception e) {
            log.error("Failed to initiate ACH transfer for user {}", userId, e);
            throw new ACHTransferException("Failed to initiate ACH transfer", e);
        }
    }

    /**
     * Get direct deposit account details
     */
    @Transactional(readOnly = true)
    public DirectDepositAccountDto getDirectDepositAccount(String userId) {
        DirectDepositAccount account = accountRepository.findByUserId(userId)
            .orElseThrow(() -> new AccountNotFoundException("Direct deposit account not found"));
        
        return toDirectDepositAccountDto(account);
    }

    /**
     * Get direct deposit history
     */
    @Transactional(readOnly = true)
    public Page<DirectDepositDto> getDirectDepositHistory(String userId, Pageable pageable) {
        Page<DirectDeposit> deposits = directDepositRepository.findByUserIdOrderByReceivedAtDesc(
            userId, pageable);
        
        return deposits.map(this::toDirectDepositDto);
    }

    /**
     * Get ACH transfer history
     */
    @Transactional(readOnly = true)
    public Page<ACHTransferDto> getACHTransferHistory(String userId, Pageable pageable) {
        Page<ACHTransfer> transfers = achTransferRepository.findByUserIdOrderByInitiatedAtDesc(
            userId, pageable);
        
        return transfers.map(this::toACHTransferDto);
    }

    /**
     * Get employer list
     */
    @Transactional(readOnly = true)
    public List<EmployerInfoDto> getEmployers(String userId) {
        List<EmployerInfo> employers = employerRepository.findByUserIdOrderByAddedAtDesc(userId);
        return employers.stream()
            .map(this::toEmployerInfoDto)
            .collect(Collectors.toList());
    }

    /**
     * Remove employer
     */
    @Transactional
    public void removeEmployer(String userId, String employerId) {
        EmployerInfo employer = employerRepository.findByIdAndUserId(employerId, userId)
            .orElseThrow(() -> new EmployerNotFoundException("Employer not found"));
        
        employer.setStatus(EmployerStatus.REMOVED);
        employer.setRemovedAt(Instant.now());
        employerRepository.save(employer);
        
        log.info("Removed employer {} for user {}", employerId, userId);
    }

    /**
     * Get direct deposit instructions
     */
    @Transactional(readOnly = true)
    public DirectDepositInstructionsDto getDirectDepositInstructions(String userId) {
        DirectDepositAccount account = getActiveAccount(userId);
        
        return DirectDepositInstructionsDto.builder()
            .accountNumber(account.getAccountNumber())
            .routingNumber(account.getRoutingNumber())
            .bankName(account.getBankName())
            .accountType(account.getAccountType().toString())
            .accountHolderName(getUserFullName(userId))
            .instructions(List.of(
                "Provide these details to your employer or benefits provider",
                "Direct deposits typically take 1-2 business days to process",
                "You'll receive a notification when deposits are received",
                "There are no fees for receiving direct deposits"
            ))
            .build();
    }

    /**
     * Update direct deposit settings
     */
    @Transactional
    public DirectDepositAccountDto updateDirectDepositSettings(String userId, 
                                                             UpdateDirectDepositRequest request) {
        DirectDepositAccount account = getActiveAccount(userId);
        
        // Update settings
        if (request.getAutoTransferEnabled() != null) {
            account.setAutoTransferEnabled(request.getAutoTransferEnabled());
        }
        if (request.getAutoTransferThreshold() != null) {
            account.setAutoTransferThreshold(request.getAutoTransferThreshold());
        }
        if (request.getNotificationPreferences() != null) {
            account.setNotificationPreferences(request.getNotificationPreferences());
        }
        
        account.setLastActivityAt(Instant.now());
        account = accountRepository.save(account);
        
        log.info("Updated direct deposit settings for user {}", userId);
        
        return toDirectDepositAccountDto(account);
    }

    /**
     * Scheduled job to process pending ACH transfers
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @Transactional
    public void processPendingACHTransfers() {
        List<ACHTransfer> pendingTransfers = achTransferRepository.findByStatusAndExpectedSettlementBefore(
            ACHStatus.PENDING, Instant.now()
        );
        
        for (ACHTransfer transfer : pendingTransfers) {
            try {
                processACHSettlement(transfer);
            } catch (Exception e) {
                log.error("Failed to process ACH settlement for transfer {}", transfer.getId(), e);
            }
        }
    }

    /**
     * Scheduled job to check ACH returns
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    @Transactional
    public void checkACHReturns() {
        List<ACHTransfer> recentTransfers = achTransferRepository.findRecentCompleted(
            Instant.now().minus(Duration.ofDays(5))
        );
        
        for (ACHTransfer transfer : recentTransfers) {
            try {
                ACHReturnStatus returnStatus = achProvider.checkReturnStatus(
                    transfer.getProviderTransferId()
                );
                
                if (returnStatus.isReturned()) {
                    processACHReturn(transfer, returnStatus);
                }
            } catch (Exception e) {
                log.error("Failed to check return status for transfer {}", transfer.getId(), e);
            }
        }
    }

    private void processDeposit(DirectDeposit deposit, DirectDepositAccount account) {
        try {
            // Credit user wallet
            walletService.credit(
                account.getUserId(),
                deposit.getAmount(),
                deposit.getCurrency(),
                "Direct deposit from " + deposit.getOriginatorName(),
                Map.of(
                    "depositId", deposit.getId(),
                    "traceNumber", deposit.getTraceNumber(),
                    "originator", deposit.getOriginatorName()
                )
            );
            
            // Update deposit status
            deposit.setStatus(DirectDepositStatus.COMPLETED);
            deposit.setProcessedAt(Instant.now());
            directDepositRepository.save(deposit);
            
            // Update account activity
            account.setLastActivityAt(Instant.now());
            account.setTotalDeposits(account.getTotalDeposits().add(deposit.getAmount()));
            account.setDepositCount(account.getDepositCount() + 1);
            accountRepository.save(account);
            
            // Send notification
            notificationService.sendDirectDepositReceived(account.getUserId(), deposit);
            
            // Publish event
            eventPublisher.publish(DirectDepositEvent.depositReceived(deposit));
            
            // Check for auto-transfer
            if (account.isAutoTransferEnabled() && 
                deposit.getAmount().compareTo(account.getAutoTransferThreshold()) >= 0) {
                initiateAutoTransfer(account, deposit);
            }
            
        } catch (Exception e) {
            deposit.setStatus(DirectDepositStatus.FAILED);
            deposit.setFailureReason(e.getMessage());
            directDepositRepository.save(deposit);
            throw e;
        }
    }

    private void processACHSettlement(ACHTransfer transfer) {
        ACHSettlementStatus settlement = achProvider.checkSettlement(transfer.getProviderTransferId());
        
        if (settlement.isSettled()) {
            // Credit wallet for incoming transfers
            if (transfer.getDirection() == ACHDirection.INCOMING) {
                walletService.credit(
                    transfer.getUserId(),
                    transfer.getNetAmount(),
                    transfer.getCurrency(),
                    "ACH transfer",
                    Map.of("transferId", transfer.getId())
                );
            }
            
            transfer.setStatus(ACHStatus.COMPLETED);
            transfer.setSettledAt(settlement.getSettledAt());
            achTransferRepository.save(transfer);
            
            // Send notification
            notificationService.sendACHTransferCompleted(transfer.getUserId(), transfer);
            
            log.info("ACH transfer {} settled for user {}", transfer.getId(), transfer.getUserId());
        }
    }

    private void processACHReturn(ACHTransfer transfer, ACHReturnStatus returnStatus) {
        transfer.setStatus(ACHStatus.RETURNED);
        transfer.setReturnCode(returnStatus.getReturnCode());
        transfer.setReturnReason(returnStatus.getReturnReason());
        transfer.setReturnedAt(returnStatus.getReturnedAt());
        achTransferRepository.save(transfer);
        
        // Reverse the transaction
        if (transfer.getDirection() == ACHDirection.INCOMING) {
            walletService.debit(
                transfer.getUserId(),
                transfer.getNetAmount(),
                transfer.getCurrency(),
                "ACH return - " + returnStatus.getReturnReason(),
                Map.of("transferId", transfer.getId(), "returnCode", returnStatus.getReturnCode())
            );
        }
        
        // Send notification
        notificationService.sendACHReturnNotification(transfer.getUserId(), transfer, returnStatus);
        
        log.warn("ACH transfer {} returned for user {}, code: {}, reason: {}", 
            transfer.getId(), transfer.getUserId(), 
            returnStatus.getReturnCode(), returnStatus.getReturnReason());
    }

    private String generateUniqueAccountNumber() {
        // Generate 10-digit account number
        SecureRandom random = new SecureRandom();
        String accountNumber;
        do {
            accountNumber = String.format("%010d", random.nextInt(1000000000));
        } while (accountRepository.existsByAccountNumber(accountNumber));
        
        return accountNumber;
    }

    private String generateEmployeeId(String userId) {
        return "EMP" + userId.substring(0, 8).toUpperCase();
    }

    private DirectDepositType identifyDepositType(IncomingACHRequest request) {
        String description = request.getDescription().toLowerCase();
        
        if (description.contains("payroll") || description.contains("salary") || 
            description.contains("wages")) {
            return DirectDepositType.PAYROLL;
        } else if (description.contains("benefit") || description.contains("pension") ||
                   description.contains("social security")) {
            return DirectDepositType.GOVERNMENT_BENEFIT;
        } else if (description.contains("tax refund") || description.contains("treasury")) {
            return DirectDepositType.TAX_REFUND;
        } else {
            return DirectDepositType.OTHER;
        }
    }

    private void validateEmployer(AddEmployerRequest request) {
        if (request.getEmployerName() == null || request.getEmployerName().trim().isEmpty()) {
            throw new IllegalArgumentException("Employer name is required");
        }
        
        if (request.getPayFrequency() == null) {
            throw new IllegalArgumentException("Pay frequency is required");
        }
    }

    private void validateACHTransfer(InitiateACHRequest request, String userId) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        
        if (request.getAmount().compareTo(maxDailyACH) > 0) {
            throw new IllegalArgumentException("Amount exceeds maximum limit");
        }
    }

    private boolean isDuplicateTransaction(IncomingACHRequest request) {
        return directDepositRepository.existsByTraceNumber(request.getTraceNumber());
    }

    private void rejectIncomingACH(IncomingACHRequest request, String reason) {
        achProvider.rejectIncomingACH(request.getTraceNumber(), reason);
        log.warn("Rejected incoming ACH: {}, reason: {}", request.getTraceNumber(), reason);
    }

    private void updateEmployerLastPayment(String userId, String originatorName) {
        employerRepository.findByUserIdAndEmployerName(userId, originatorName)
            .ifPresent(employer -> {
                employer.setLastPaymentDate(LocalDate.now());
                employer.setStatus(EmployerStatus.VERIFIED);
                employerRepository.save(employer);
            });
    }

    private void initiateAutoTransfer(DirectDepositAccount account, DirectDeposit deposit) {
        // Auto-transfer to savings or investment account
        log.info("Initiating auto-transfer for deposit {} based on user preferences", deposit.getId());
        // Implementation would connect to savings/investment service
    }

    private DirectDepositInstructions generateEmployerInstructions(DirectDepositAccount account, 
                                                                 EmployerInfo employer) {
        return DirectDepositInstructions.builder()
            .accountNumber(account.getAccountNumber())
            .routingNumber(account.getRoutingNumber())
            .bankName(account.getBankName())
            .accountType(account.getAccountType().toString())
            .employeeId(employer.getEmployeeId())
            .employerSpecificInstructions(getEmployerSpecificInstructions(employer.getPayrollProvider()))
            .build();
    }

    private List<String> getEmployerSpecificInstructions(String payrollProvider) {
        // Return provider-specific instructions
        if ("ADP".equalsIgnoreCase(payrollProvider)) {
            return List.of(
                "Log into ADP portal",
                "Navigate to Pay > Direct Deposit",
                "Add new account with provided details",
                "Set allocation percentage or amount"
            );
        }
        // Add more providers as needed
        return Collections.emptyList();
    }

    private BigDecimal getTodayACHTransfers(String userId) {
        Instant startOfDay = LocalDate.now().atStartOfDay()
            .atZone(java.time.ZoneId.systemDefault()).toInstant();
        return achTransferRepository.getTotalTransfersInPeriod(userId, startOfDay, Instant.now());
    }

    private DirectDepositAccount getActiveAccount(String userId) {
        return accountRepository.findByUserIdAndStatus(userId, DirectDepositStatus.ACTIVE)
            .orElseThrow(() -> new AccountNotFoundException("No active direct deposit account found"));
    }

    private String getWaqitiAccountNumber(String userId) {
        DirectDepositAccount account = getActiveAccount(userId);
        return account.getAccountNumber();
    }

    private String getDirectDepositAccountId(String userId) {
        DirectDepositAccount account = getActiveAccount(userId);
        return account.getId();
    }

    private String getUserFullName(String userId) {
        return userService.getUserFullName(userId);
    }

    private boolean isUserFullyVerified(String userId) {
        return kycClientService.isUserAdvancedVerified(userId);
    }

    // DTO conversion methods
    private DirectDepositAccountDto toDirectDepositAccountDto(DirectDepositAccount account) {
        return DirectDepositAccountDto.builder()
            .id(account.getId())
            .userId(account.getUserId())
            .accountNumber(account.getAccountNumber())
            .routingNumber(account.getRoutingNumber())
            .bankName(account.getBankName())
            .accountType(account.getAccountType())
            .status(account.getStatus())
            .autoTransferEnabled(account.isAutoTransferEnabled())
            .autoTransferThreshold(account.getAutoTransferThreshold())
            .totalDeposits(account.getTotalDeposits())
            .depositCount(account.getDepositCount())
            .createdAt(account.getCreatedAt())
            .lastActivityAt(account.getLastActivityAt())
            .build();
    }

    private DirectDepositDto toDirectDepositDto(DirectDeposit deposit) {
        return DirectDepositDto.builder()
            .id(deposit.getId())
            .originatorName(deposit.getOriginatorName())
            .amount(deposit.getAmount())
            .currency(deposit.getCurrency())
            .description(deposit.getDescription())
            .type(deposit.getType())
            .status(deposit.getStatus())
            .traceNumber(deposit.getTraceNumber())
            .settlementDate(deposit.getSettlementDate())
            .receivedAt(deposit.getReceivedAt())
            .processedAt(deposit.getProcessedAt())
            .build();
    }

    private ACHTransferDto toACHTransferDto(ACHTransfer transfer) {
        return ACHTransferDto.builder()
            .id(transfer.getId())
            .type(transfer.getType())
            .direction(transfer.getDirection())
            .amount(transfer.getAmount())
            .fee(transfer.getFee())
            .netAmount(transfer.getNetAmount())
            .currency(transfer.getCurrency())
            .fromAccountMask(transfer.getFromAccountMask())
            .toAccountMask(transfer.getToAccountMask())
            .status(transfer.getStatus())
            .instant(transfer.isInstant())
            .description(transfer.getDescription())
            .expectedSettlement(transfer.getExpectedSettlement())
            .initiatedAt(transfer.getInitiatedAt())
            .settledAt(transfer.getSettledAt())
            .returnCode(transfer.getReturnCode())
            .returnReason(transfer.getReturnReason())
            .build();
    }

    private EmployerInfoDto toEmployerInfoDto(EmployerInfo employer) {
        return EmployerInfoDto.builder()
            .id(employer.getId())
            .employerName(employer.getEmployerName())
            .employerEIN(employer.getEmployerEIN())
            .employeeId(employer.getEmployeeId())
            .payrollProvider(employer.getPayrollProvider())
            .payFrequency(employer.getPayFrequency())
            .expectedPayDays(employer.getExpectedPayDays())
            .status(employer.getStatus())
            .lastPaymentDate(employer.getLastPaymentDate())
            .addedAt(employer.getAddedAt())
            .build();
    }
}