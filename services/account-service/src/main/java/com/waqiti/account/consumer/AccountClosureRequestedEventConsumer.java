package com.waqiti.account.consumer;

import com.waqiti.common.events.AccountClosureRequestedEvent;
import com.waqiti.account.service.AccountClosureService;
import com.waqiti.account.service.ComplianceService;
import com.waqiti.account.service.NotificationService;
import com.waqiti.account.service.EscheatmentService;
import com.waqiti.account.repository.ProcessedEventRepository;
import com.waqiti.account.repository.AccountRepository;
import com.waqiti.account.repository.AccountClosureRepository;
import com.waqiti.account.model.ProcessedEvent;
import com.waqiti.account.model.Account;
import com.waqiti.account.model.AccountClosure;
import com.waqiti.account.model.ClosureStatus;
import com.waqiti.account.model.ClosureReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Consumer for AccountClosureRequestedEvent - Critical for account lifecycle management
 * Handles account closure verification, compliance checks, and fund disposition
 * ZERO TOLERANCE: All account closures must follow regulatory compliance procedures
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AccountClosureRequestedEventConsumer {
    
    private final AccountClosureService accountClosureService;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final EscheatmentService escheatmentService;
    private final ProcessedEventRepository processedEventRepository;
    private final AccountRepository accountRepository;
    private final AccountClosureRepository accountClosureRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal LARGE_BALANCE_THRESHOLD = new BigDecimal("10000");
    private static final int CLOSURE_COOLING_PERIOD_DAYS = 30;
    
    @KafkaListener(
        topics = "account.closure.requested",
        groupId = "account-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // Highest isolation for account closures
    public void handleAccountClosureRequested(AccountClosureRequestedEvent event) {
        log.info("Processing account closure request: Account {} - User {} - Reason: {}", 
            event.getAccountId(), event.getUserId(), event.getClosureReason());
        
        // IDEMPOTENCY CHECK - Prevent duplicate closure processing
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Account closure request already processed: {}", event.getEventId());
            return;
        }
        
        try {
            // Get account details
            Account account = accountRepository.findById(event.getAccountId())
                .orElseThrow(() -> new RuntimeException("Account not found: " + event.getAccountId()));
            
            // Create closure record
            AccountClosure closure = createClosureRecord(account, event);
            
            // STEP 1: Validate account closure eligibility
            validateClosureEligibility(closure, account, event);
            
            // STEP 2: Perform regulatory compliance checks
            performComplianceChecks(closure, account, event);
            
            // STEP 3: Check for outstanding obligations and holds
            checkOutstandingObligations(closure, account, event);
            
            // STEP 4: Verify account balance and calculate final amounts
            verifyAccountBalanceAndCalculateFinal(closure, account, event);
            
            // STEP 5: Process automatic account transfers and closures
            processAutomaticTransfers(closure, account, event);
            
            // STEP 6: Handle dormant account procedures if applicable
            handleDormantAccountProcedures(closure, account, event);
            
            // STEP 7: Generate required regulatory notifications
            generateRegulatoryNotifications(closure, account, event);
            
            // STEP 8: Create final account statements and documentation
            createFinalDocumentation(closure, account, event);
            
            // STEP 9: Process fund disposition and escheatment
            processFundDisposition(closure, account, event);
            
            // STEP 10: Update credit reporting agencies
            updateCreditReporting(closure, account, event);
            
            // STEP 11: Send customer closure notifications
            sendCustomerNotifications(closure, account, event);
            
            // STEP 12: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("AccountClosureRequestedEvent")
                .processedAt(Instant.now())
                .accountId(event.getAccountId())
                .userId(event.getUserId())
                .closureReason(event.getClosureReason())
                .finalBalance(closure.getFinalBalance())
                .closureStatus(closure.getStatus())
                .complianceChecksPassed(closure.isComplianceChecksPassed())
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully processed account closure request: {} - Status: {}, Final Balance: ${}", 
                event.getAccountId(), closure.getStatus(), closure.getFinalBalance());
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process account closure request: {}", 
                event.getAccountId(), e);
                
            // Create manual intervention record
            createManualInterventionRecord(event, e);
            
            throw new RuntimeException("Account closure request processing failed", e);
        }
    }
    
    private AccountClosure createClosureRecord(Account account, AccountClosureRequestedEvent event) {
        AccountClosure closure = AccountClosure.builder()
            .id(UUID.randomUUID().toString())
            .accountId(event.getAccountId())
            .userId(event.getUserId())
            .closureReason(mapClosureReason(event.getClosureReason()))
            .requestedAt(event.getRequestedAt())
            .requestedBy(event.getRequestedBy())
            .customerInitiated(event.isCustomerInitiated())
            .status(ClosureStatus.PENDING_VALIDATION)
            .complianceFlags(new ArrayList<>())
            .accountType(account.getAccountType())
            .currentBalance(account.getBalance())
            .build();
        
        return accountClosureRepository.save(closure);
    }
    
    private void validateClosureEligibility(AccountClosure closure, Account account, AccountClosureRequestedEvent event) {
        List<String> validationErrors = new ArrayList<>();
        
        // Check if account is already closed
        if ("CLOSED".equals(account.getStatus())) {
            validationErrors.add("ACCOUNT_ALREADY_CLOSED");
            closure.setStatus(ClosureStatus.REJECTED);
            
            accountClosureRepository.save(closure);
            
            log.warn("Account closure rejected - account already closed: {}", event.getAccountId());
            throw new RuntimeException("Account is already closed");
        }
        
        // Check for account freeze or legal holds
        if (account.isFrozen() || account.hasLegalHolds()) {
            validationErrors.add("ACCOUNT_HAS_HOLDS");
            closure.addComplianceFlag("LEGAL_HOLDS_PRESENT");
            closure.setRequiresManualReview(true);
        }
        
        // Check minimum account age (prevent immediate closure after opening)
        long accountAgeDays = java.time.Duration.between(
            account.getOpenedAt().atZone(java.time.ZoneOffset.UTC).toInstant(),
            Instant.now()
        ).toDays();
        
        if (accountAgeDays < 30 && event.isCustomerInitiated()) {
            validationErrors.add("ACCOUNT_TOO_NEW");
            closure.addComplianceFlag("EARLY_CLOSURE_REQUEST");
        }
        
        // Check for recent dispute or fraud activity
        boolean recentActivity = accountClosureService.hasRecentDisputeOrFraudActivity(
            event.getAccountId(),
            LocalDateTime.now().minusDays(90)
        );
        
        if (recentActivity) {
            validationErrors.add("RECENT_DISPUTE_ACTIVITY");
            closure.addComplianceFlag("RECENT_DISPUTE_ACTIVITY");
            closure.setRequiresManualReview(true);
        }
        
        // Check for pending transactions
        int pendingTransactions = accountClosureService.getPendingTransactionCount(event.getAccountId());
        
        if (pendingTransactions > 0) {
            validationErrors.add("PENDING_TRANSACTIONS");
            closure.addComplianceFlag("PENDING_TRANSACTIONS");
            closure.setPendingTransactionCount(pendingTransactions);
        }
        
        closure.setValidationErrors(validationErrors);
        
        if (validationErrors.isEmpty()) {
            closure.setStatus(ClosureStatus.VALIDATED);
        } else if (validationErrors.contains("ACCOUNT_ALREADY_CLOSED")) {
            // Already handled above
        } else {
            closure.setStatus(ClosureStatus.PENDING_MANUAL_REVIEW);
        }
        
        accountClosureRepository.save(closure);
        
        log.info("Closure eligibility validation completed for account {}: Errors: {}", 
            event.getAccountId(), validationErrors.size());
    }
    
    private void performComplianceChecks(AccountClosure closure, Account account, AccountClosureRequestedEvent event) {
        List<String> complianceFlags = new ArrayList<>();
        
        // Check Bank Secrecy Act (BSA) requirements
        boolean bsaCompliant = complianceService.checkBSACompliance(
            event.getAccountId(),
            event.getUserId(),
            account.getBalance()
        );
        
        if (!bsaCompliant) {
            complianceFlags.add("BSA_NON_COMPLIANT");
            closure.setRequiresManualReview(true);
        }
        
        // Check suspicious activity reporting (SAR) requirements
        boolean sarRequired = complianceService.checkSARRequirements(
            event.getAccountId(),
            closure.getClosureReason(),
            account.getBalance(),
            LocalDateTime.now().minusDays(30)
        );
        
        if (sarRequired) {
            String sarId = complianceService.fileSAR(
                event.getUserId(),
                event.getAccountId(),
                account.getBalance(),
                "ACCOUNT_CLOSURE",
                closure.getClosureReason().toString()
            );
            
            closure.setSarId(sarId);
            complianceFlags.add("SAR_FILED");
        }
        
        // Check CTR (Currency Transaction Report) requirements for large balances
        if (account.getBalance().compareTo(new BigDecimal("10000")) > 0) {
            String ctrId = complianceService.fileCTR(
                event.getUserId(),
                account.getBalance(),
                "ACCOUNT_CLOSURE",
                event.getAccountId()
            );
            
            closure.setCtrId(ctrId);
            complianceFlags.add("CTR_FILED");
        }
        
        // Check Regulation D requirements for savings accounts
        if ("SAVINGS".equals(account.getAccountType())) {
            boolean regDCompliant = complianceService.checkRegulationDCompliance(
                event.getAccountId(),
                LocalDateTime.now().minusDays(30)
            );
            
            if (!regDCompliant) {
                complianceFlags.add("REGULATION_D_VIOLATION");
                closure.setRequiresManualReview(true);
            }
        }
        
        // Check FDIC insurance requirements
        boolean fdicNotificationRequired = complianceService.isFDICNotificationRequired(
            account.getBalance(),
            closure.getClosureReason()
        );
        
        if (fdicNotificationRequired) {
            complianceFlags.add("FDIC_NOTIFICATION_REQUIRED");
        }
        
        // Check state-specific requirements
        List<String> stateRequirements = complianceService.checkStateClosureRequirements(
            account.getState(),
            account.getAccountType(),
            account.getBalance()
        );
        
        complianceFlags.addAll(stateRequirements);
        
        closure.addComplianceFlags(complianceFlags);
        closure.setComplianceChecksPassed(complianceFlags.isEmpty() || 
            complianceFlags.stream().noneMatch(flag -> flag.contains("NON_COMPLIANT")));
        
        accountClosureRepository.save(closure);
        
        log.info("Compliance checks completed for account {}: Flags: {}, Passed: {}", 
            event.getAccountId(), complianceFlags.size(), closure.isComplianceChecksPassed());
    }
    
    private void checkOutstandingObligations(AccountClosure closure, Account account, AccountClosureRequestedEvent event) {
        // Check for outstanding loans or credit facilities
        BigDecimal outstandingLoans = accountClosureService.getOutstandingLoanBalance(event.getUserId());
        closure.setOutstandingLoanBalance(outstandingLoans);
        
        if (outstandingLoans.compareTo(BigDecimal.ZERO) > 0) {
            closure.addComplianceFlag("OUTSTANDING_LOANS");
            closure.setRequiresManualReview(true);
        }
        
        // Check for outstanding card balances
        BigDecimal outstandingCardBalance = accountClosureService.getOutstandingCardBalance(event.getUserId());
        closure.setOutstandingCardBalance(outstandingCardBalance);
        
        if (outstandingCardBalance.compareTo(BigDecimal.ZERO) > 0) {
            closure.addComplianceFlag("OUTSTANDING_CARD_BALANCE");
            closure.setRequiresManualReview(true);
        }
        
        // Check for pending fee assessments
        BigDecimal pendingFees = accountClosureService.getPendingFees(event.getAccountId());
        closure.setPendingFees(pendingFees);
        
        // Check for scheduled automatic payments
        int scheduledPayments = accountClosureService.getScheduledPaymentCount(event.getAccountId());
        closure.setScheduledPaymentCount(scheduledPayments);
        
        if (scheduledPayments > 0) {
            closure.addComplianceFlag("SCHEDULED_PAYMENTS");
            closure.setRequiresCustomerAction(true);
        }
        
        // Check for direct deposit arrangements
        boolean hasDirectDeposit = accountClosureService.hasDirectDepositSetup(event.getAccountId());
        closure.setHasDirectDeposit(hasDirectDeposit);
        
        if (hasDirectDeposit) {
            closure.addComplianceFlag("DIRECT_DEPOSIT_ACTIVE");
            closure.setRequiresCustomerAction(true);
        }
        
        accountClosureRepository.save(closure);
        
        log.info("Outstanding obligations check completed for account {}: Loans: ${}, Cards: ${}, Scheduled payments: {}", 
            event.getAccountId(), outstandingLoans, outstandingCardBalance, scheduledPayments);
    }
    
    private void verifyAccountBalanceAndCalculateFinal(AccountClosure closure, Account account, AccountClosureRequestedEvent event) {
        // Get real-time account balance
        BigDecimal currentBalance = accountClosureService.getRealTimeBalance(event.getAccountId());
        closure.setCurrentBalance(currentBalance);
        
        // Calculate accrued interest up to closure date
        BigDecimal accruedInterest = accountClosureService.calculateAccruedInterest(
            event.getAccountId(),
            LocalDateTime.now()
        );
        closure.setAccruedInterest(accruedInterest);
        
        // Calculate closure fees
        BigDecimal closureFees = accountClosureService.calculateClosureFees(
            event.getAccountId(),
            closure.getClosureReason(),
            java.time.Duration.between(
                account.getOpenedAt().atZone(java.time.ZoneOffset.UTC).toInstant(),
                Instant.now()
            ).toDays()
        );
        closure.setClosureFees(closureFees);
        
        // Calculate final balance after fees and interest
        BigDecimal finalBalance = currentBalance
            .add(accruedInterest)
            .subtract(closureFees)
            .subtract(closure.getPendingFees() != null ? closure.getPendingFees() : BigDecimal.ZERO);
        
        closure.setFinalBalance(finalBalance);
        
        // Handle negative balance scenarios
        if (finalBalance.compareTo(BigDecimal.ZERO) < 0) {
            closure.addComplianceFlag("NEGATIVE_BALANCE");
            closure.setRequiresManualReview(true);
            
            log.warn("Account {} has negative final balance: ${}", event.getAccountId(), finalBalance);
        }
        
        // Flag large balance closures for additional scrutiny
        if (finalBalance.compareTo(LARGE_BALANCE_THRESHOLD) > 0) {
            closure.addComplianceFlag("LARGE_BALANCE_CLOSURE");
            closure.setRequiresManualReview(true);
        }
        
        accountClosureRepository.save(closure);
        
        log.info("Balance verification completed for account {}: Current: ${}, Final: ${}", 
            event.getAccountId(), currentBalance, finalBalance);
    }
    
    private void processAutomaticTransfers(AccountClosure closure, Account account, AccountClosureRequestedEvent event) {
        // Cancel all scheduled transfers
        int cancelledTransfers = accountClosureService.cancelScheduledTransfers(event.getAccountId());
        closure.setCancelledTransferCount(cancelledTransfers);
        
        // Handle automatic bill payments
        if (closure.getScheduledPaymentCount() > 0) {
            accountClosureService.notifyBillers(
                event.getAccountId(),
                event.getUserId(),
                "ACCOUNT_CLOSURE",
                LocalDateTime.now().plusDays(CLOSURE_COOLING_PERIOD_DAYS)
            );
        }
        
        // Process automatic fund transfers to designated account
        if (event.getTransferToAccountId() != null && 
            closure.getFinalBalance().compareTo(BigDecimal.ZERO) > 0) {
            
            String transferId = accountClosureService.initiateClosureFundTransfer(
                event.getAccountId(),
                event.getTransferToAccountId(),
                closure.getFinalBalance(),
                "ACCOUNT_CLOSURE_TRANSFER"
            );
            
            closure.setFinalTransferId(transferId);
            closure.setFundsTransferred(true);
        }
        
        accountClosureRepository.save(closure);
        
        log.info("Automatic transfers processed for account {}: Cancelled: {}, Final transfer: {}", 
            event.getAccountId(), cancelledTransfers, closure.getFinalTransferId());
    }
    
    private void handleDormantAccountProcedures(AccountClosure closure, Account account, AccountClosureRequestedEvent event) {
        // Check if account is dormant
        boolean isDormant = accountClosureService.isAccountDormant(
            event.getAccountId(),
            LocalDateTime.now().minusMonths(12)
        );
        
        closure.setAccountDormant(isDormant);
        
        if (isDormant && closure.getFinalBalance().compareTo(BigDecimal.ZERO) > 0) {
            // Check state escheatment laws
            int escheatmentDays = escheatmentService.getEscheatmentPeriod(account.getState());
            LocalDateTime escheatmentDate = account.getLastActivityDate()
                .plusDays(escheatmentDays);
            
            closure.setEscheatmentDate(escheatmentDate);
            
            // If escheatment period has passed, initiate escheatment process
            if (LocalDateTime.now().isAfter(escheatmentDate)) {
                String escheatmentId = escheatmentService.initiateEscheatment(
                    event.getAccountId(),
                    event.getUserId(),
                    closure.getFinalBalance(),
                    account.getState()
                );
                
                closure.setEscheatmentId(escheatmentId);
                closure.setFundsEscheated(true);
                
                log.info("Escheatment initiated for dormant account {}: ID: {}, Amount: ${}", 
                    event.getAccountId(), escheatmentId, closure.getFinalBalance());
            }
        }
        
        accountClosureRepository.save(closure);
        
        log.info("Dormant account procedures completed for account {}: Dormant: {}, Escheatment: {}", 
            event.getAccountId(), isDormant, closure.isAccountDormant());
    }
    
    private void generateRegulatoryNotifications(AccountClosure closure, Account account, AccountClosureRequestedEvent event) {
        List<String> notifications = new ArrayList<>();
        
        // Generate FDIC notification if required
        if (closure.getComplianceFlags().contains("FDIC_NOTIFICATION_REQUIRED")) {
            String fdicNotificationId = complianceService.generateFDICNotification(
                event.getAccountId(),
                event.getUserId(),
                closure.getFinalBalance(),
                closure.getClosureReason()
            );
            
            closure.setFdicNotificationId(fdicNotificationId);
            notifications.add("FDIC_NOTIFICATION");
        }
        
        // Generate state banking regulator notification
        String stateNotificationId = complianceService.generateStateRegulatoryNotification(
            account.getState(),
            event.getAccountId(),
            closure.getClosureReason(),
            closure.getFinalBalance()
        );
        
        closure.setStateNotificationId(stateNotificationId);
        notifications.add("STATE_REGULATOR_NOTIFICATION");
        
        // Generate IRS notifications for interest-bearing accounts
        if (closure.getAccruedInterest().compareTo(new BigDecimal("10")) > 0) {
            String irsNotificationId = complianceService.generateIRSNotification(
                event.getUserId(),
                closure.getAccruedInterest(),
                LocalDateTime.now().getYear()
            );
            
            closure.setIrsNotificationId(irsNotificationId);
            notifications.add("IRS_NOTIFICATION");
        }
        
        closure.setRegulatoryNotifications(notifications);
        accountClosureRepository.save(closure);
        
        log.info("Regulatory notifications generated for account {}: {}", 
            event.getAccountId(), notifications);
    }
    
    private void createFinalDocumentation(AccountClosure closure, Account account, AccountClosureRequestedEvent event) {
        // Generate final account statement
        String finalStatementId = accountClosureService.generateFinalStatement(
            event.getAccountId(),
            closure.getCurrentBalance(),
            closure.getFinalBalance(),
            closure.getAccruedInterest(),
            closure.getClosureFees(),
            LocalDateTime.now()
        );
        
        closure.setFinalStatementId(finalStatementId);
        
        // Generate closure confirmation letter
        String closureConfirmationId = accountClosureService.generateClosureConfirmation(
            event.getAccountId(),
            event.getUserId(),
            closure.getClosureReason(),
            closure.getFinalBalance(),
            closure.getFinalTransferId()
        );
        
        closure.setClosureConfirmationId(closureConfirmationId);
        
        // Generate tax documents if applicable
        if (closure.getAccruedInterest().compareTo(new BigDecimal("10")) > 0) {
            String taxDocumentId = accountClosureService.generateTaxDocuments(
                event.getUserId(),
                closure.getAccruedInterest(),
                LocalDateTime.now().getYear()
            );
            
            closure.setTaxDocumentId(taxDocumentId);
        }
        
        accountClosureRepository.save(closure);
        
        log.info("Final documentation created for account {}: Statement: {}, Confirmation: {}", 
            event.getAccountId(), finalStatementId, closureConfirmationId);
    }
    
    private void processFundDisposition(AccountClosure closure, Account account, AccountClosureRequestedEvent event) {
        if (closure.getFinalBalance().compareTo(BigDecimal.ZERO) > 0) {
            
            if (!closure.isFundsTransferred() && !closure.isFundsEscheated()) {
                // Issue cashier's check for remaining funds
                String checkId = accountClosureService.issueCashiersCheck(
                    event.getUserId(),
                    closure.getFinalBalance(),
                    "ACCOUNT_CLOSURE_PROCEEDS",
                    account.getMailingAddress()
                );
                
                closure.setCheckId(checkId);
                closure.setFundDispositionMethod("CASHIERS_CHECK");
            } else if (closure.isFundsTransferred()) {
                closure.setFundDispositionMethod("ACCOUNT_TRANSFER");
            } else if (closure.isFundsEscheated()) {
                closure.setFundDispositionMethod("ESCHEATMENT");
            }
        } else if (closure.getFinalBalance().equals(BigDecimal.ZERO)) {
            closure.setFundDispositionMethod("ZERO_BALANCE");
        } else {
            closure.setFundDispositionMethod("NEGATIVE_BALANCE");
            // Handle negative balance collection procedures
            accountClosureService.initiateNegativeBalanceCollection(
                event.getUserId(),
                closure.getFinalBalance().abs()
            );
        }
        
        accountClosureRepository.save(closure);
        
        log.info("Fund disposition completed for account {}: Method: {}, Amount: ${}", 
            event.getAccountId(), closure.getFundDispositionMethod(), closure.getFinalBalance());
    }
    
    private void updateCreditReporting(AccountClosure closure, Account account, AccountClosureRequestedEvent event) {
        // Report account closure to credit bureaus if applicable
        if (account.isReportedToCreditBureaus()) {
            
            String creditReportingId = accountClosureService.reportAccountClosureToCredit(
                event.getUserId(),
                event.getAccountId(),
                account.getAccountType(),
                closure.getClosureReason(),
                closure.getFinalBalance().compareTo(BigDecimal.ZERO) >= 0 ? "SATISFACTORY" : "NEGATIVE"
            );
            
            closure.setCreditReportingId(creditReportingId);
            
            log.info("Credit reporting updated for account {}: ID: {}", 
                event.getAccountId(), creditReportingId);
        }
        
        accountClosureRepository.save(closure);
    }
    
    private void sendCustomerNotifications(AccountClosure closure, Account account, AccountClosureRequestedEvent event) {
        // Send closure confirmation email
        notificationService.sendClosureConfirmationEmail(
            event.getUserId(),
            event.getAccountId(),
            closure.getClosureReason(),
            closure.getFinalBalance(),
            closure.getFundDispositionMethod(),
            closure.getFinalStatementId()
        );
        
        // Send SMS for high-value account closures
        if (closure.getCurrentBalance().compareTo(LARGE_BALANCE_THRESHOLD) > 0) {
            notificationService.sendClosureConfirmationSMS(
                event.getUserId(),
                event.getAccountId(),
                closure.getFinalBalance()
            );
        }
        
        // Send mail notification for formal closure confirmation
        notificationService.sendClosureConfirmationMail(
            event.getUserId(),
            account.getMailingAddress(),
            closure.getClosureConfirmationId(),
            closure.getFinalStatementId()
        );
        
        // Send notifications about ongoing obligations
        if (closure.isRequiresCustomerAction()) {
            notificationService.sendCustomerActionRequiredNotification(
                event.getUserId(),
                event.getAccountId(),
                Arrays.asList(
                    closure.getScheduledPaymentCount() > 0 ? "Update automatic payments" : null,
                    closure.isHasDirectDeposit() ? "Update direct deposit" : null
                ).stream()
                .filter(Objects::nonNull)
                .toList()
            );
        }
        
        log.info("Customer notifications sent for account closure: {}", event.getAccountId());
    }
    
    private ClosureReason mapClosureReason(String closureReasonStr) {
        try {
            return ClosureReason.valueOf(closureReasonStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ClosureReason.OTHER;
        }
    }
    
    private void createManualInterventionRecord(AccountClosureRequestedEvent event, Exception exception) {
        manualInterventionService.createTask(
            "ACCOUNT_CLOSURE_PROCESSING_FAILED",
            String.format(
                "Failed to process account closure request. " +
                "Account ID: %s, User ID: %s, Reason: %s. " +
                "Account may remain in inconsistent state. Customer may not have received closure confirmation. " +
                "Exception: %s. Manual intervention required.",
                event.getAccountId(),
                event.getUserId(),
                event.getClosureReason(),
                exception.getMessage()
            ),
            "CRITICAL",
            event,
            exception
        );
    }
}