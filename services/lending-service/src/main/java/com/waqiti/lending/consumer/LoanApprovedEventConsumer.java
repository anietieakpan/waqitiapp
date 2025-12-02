package com.waqiti.lending.consumer;

import com.waqiti.common.events.LoanApprovedEvent;
import com.waqiti.lending.domain.Loan;
import com.waqiti.lending.domain.LoanApplication;
import com.waqiti.lending.domain.LoanSchedule;
import com.waqiti.lending.repository.LoanRepository;
import com.waqiti.lending.repository.LoanApplicationRepository;
import com.waqiti.lending.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Consumer for LoanApprovedEvent
 *
 * Handles the complete loan origination workflow after application approval:
 * 1. Idempotency check to prevent duplicate processing
 * 2. Originate loan from approved application
 * 3. Generate amortization schedule
 * 4. Create loan account and GL accounts
 * 5. Generate compliance documents (TILA, loan agreement)
 * 6. Disburse funds to borrower
 * 7. Send notifications
 * 8. Report to credit bureaus
 * 9. Create audit trail
 *
 * CRITICAL: This consumer uses SERIALIZABLE transaction isolation to ensure
 * financial data consistency and prevent duplicate disbursements.
 *
 * Error Handling: Any failures result in manual intervention tasks and
 * message redelivery to DLQ for investigation.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LoanApprovedEventConsumer {

    private final IdempotencyService idempotencyService;
    private final LoanService loanService;
    private final LoanApplicationService loanApplicationService;
    private final LoanScheduleService loanScheduleService;
    private final LoanAccountService loanAccountService;
    private final DocumentGenerationService documentGenerationService;
    private final LoanDisbursementService loanDisbursementService;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final ManualInterventionService manualInterventionService;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanRepository loanRepository;

    @KafkaListener(
            topics = "${kafka.topics.loan-approved:loan.approved}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleLoanApproved(
            @Payload LoanApprovedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        String eventId = event.getEventId();
        String applicationId = event.getApplicationId();

        log.info("=== LOAN APPROVAL PROCESSING STARTED ===");
        log.info("EventID: {}, ApplicationID: {}, Partition: {}, Offset: {}",
                eventId, applicationId, partition, offset);

        try {
            // STEP 1: Idempotency check - prevent duplicate processing
            if (idempotencyService.isEventProcessed(eventId)) {
                log.warn("Event already processed, skipping: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // STEP 2: Acquire distributed lock for application
            if (!idempotencyService.tryAcquire("loan-approved:" + applicationId, java.time.Duration.ofMinutes(5))) {
                log.warn("Could not acquire lock for application: {}, will retry", applicationId);
                throw new RuntimeException("Lock acquisition failed for application: " + applicationId);
            }

            try {
                // STEP 3: Load approved application
                LoanApplication application = loanApplicationRepository.findByApplicationId(applicationId)
                        .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));

                log.info("Processing approved application: {}", applicationId);
                log.info("Borrower: {}, Amount: {}, Rate: {}%, Term: {} months",
                        application.getBorrowerId(),
                        application.getApprovedAmount(),
                        application.getApprovedInterestRate(),
                        application.getApprovedTermMonths());

                // STEP 4: Originate loan from application
                log.info("Step 1/10: Originating loan from application...");
                Loan loan = loanService.originateLoan(application);
                log.info("✓ Loan originated: {}", loan.getLoanId());

                // STEP 5: Generate payment schedule (amortization)
                log.info("Step 2/10: Generating amortization schedule...");
                List<LoanSchedule> schedules = loanScheduleService.generateSchedule(loan);
                log.info("✓ Payment schedule generated: {} payments", schedules.size());

                // STEP 6: Create loan account
                log.info("Step 3/10: Creating loan account...");
                String accountNumber = loanAccountService.generateLoanAccountNumber(
                        loan.getLoanId(),
                        loan.getBorrowerId(),
                        application.getLoanType().toString()
                );

                String accountId = loanAccountService.createLoanAccount(
                        loan.getLoanId(),
                        accountNumber,
                        loan.getBorrowerId(),
                        loan.getPrincipalAmount(),
                        loan.getInterestRate(),
                        loan.getTermMonths(),
                        loan.getFirstPaymentDate()
                );

                // Setup GL accounts for proper accounting
                loanAccountService.setupGeneralLedgerAccounts(
                        accountId,
                        application.getLoanType().toString(),
                        loan.getPrincipalAmount()
                );

                // Update loan with account info
                loan.setAccountNumber(accountNumber);
                loan.setAccountId(accountId);
                loanRepository.save(loan);
                log.info("✓ Loan account created: {}", accountNumber);

                // STEP 7: Generate compliance documents
                log.info("Step 4/10: Generating loan documents...");

                // Generate loan agreement
                String agreementId = documentGenerationService.generateLoanAgreement(
                        loan.getLoanId(),
                        loan.getBorrowerId(),
                        loan.getPrincipalAmount(),
                        loan.getInterestRate(),
                        loan.getTermMonths(),
                        "Standard loan terms and conditions apply"
                );

                // Generate TILA disclosure
                BigDecimal totalInterest = calculateTotalInterest(loan);
                BigDecimal financeCharge = calculateFinanceCharge(loan, totalInterest);
                BigDecimal totalOfPayments = loan.getPrincipalAmount().add(totalInterest);

                String tilaDisclosureId = documentGenerationService.generateTruthInLendingDisclosure(
                        loan.getLoanId(),
                        loan.getInterestRate(), // APR
                        financeCharge,
                        totalOfPayments,
                        loan.getMonthlyPayment()
                );

                // Generate payment schedule document
                String scheduleDocId = documentGenerationService.generatePaymentSchedule(
                        loan.getLoanId(),
                        loan.getFirstPaymentDate(),
                        loan.getMonthlyPayment(),
                        loan.getTermMonths()
                );

                loan.setAgreementDocumentId(agreementId);
                loan.setDisclosureDocumentId(tilaDisclosureId);
                loan.setPaymentScheduleDocumentId(scheduleDocId);
                loanRepository.save(loan);
                log.info("✓ Documents generated - Agreement: {}, TILA: {}, Schedule: {}",
                        agreementId, tilaDisclosureId, scheduleDocId);

                // STEP 8: Perform compliance checks
                log.info("Step 5/10: Performing compliance checks...");

                // TILA compliance
                String tilaCompliance = complianceService.generateTilaDisclosure(
                        loan.getBorrowerId(),
                        loan.getPrincipalAmount(),
                        loan.getInterestRate(),
                        loan.getTermMonths(),
                        loan.getMonthlyPayment()
                );

                // Record TILA compliance
                loanAccountService.recordTILACompliance(
                        loan.getLoanId(),
                        tilaDisclosureId,
                        loan.getInterestRate(),
                        financeCharge
                );

                // ECOA compliance check
                complianceService.performEcoaComplianceCheck(loan.getLoanId(), loan.getBorrowerId());

                log.info("✓ Compliance checks completed");

                // STEP 9: Disburse funds to borrower
                log.info("Step 6/10: Disbursing funds...");
                String disbursementMethod = event.getDisbursementMethod() != null ?
                        event.getDisbursementMethod() : "ACH";

                String disbursementId = loanDisbursementService.disburseFunds(
                        loan.getLoanId(),
                        loan.getBorrowerId(),
                        loan.getPrincipalAmount(),
                        disbursementMethod
                );

                loan.setDisbursementId(disbursementId);
                loan.setDisbursedAt(java.time.Instant.now());
                loanRepository.save(loan);
                log.info("✓ Funds disbursed - Disbursement ID: {}, Amount: {}, Method: {}",
                        disbursementId, loan.getPrincipalAmount(), disbursementMethod);

                // STEP 10: Create initial statement
                log.info("Step 7/10: Creating initial statement...");
                LocalDate statementEndDate = loan.getFirstPaymentDate().minusDays(1);

                String statementId = loanAccountService.createStatement(
                        loan.getLoanId(),
                        accountId,
                        LocalDate.now(),
                        statementEndDate,
                        loan.getPrincipalAmount(), // Beginning balance
                        BigDecimal.ZERO, // No payments yet
                        loan.getOutstandingBalance(), // Ending balance
                        loan.getMonthlyPayment(),
                        loan.getFirstPaymentDate()
                );

                log.info("✓ Initial statement created: {}", statementId);

                // STEP 11: Send notifications to borrower
                log.info("Step 8/10: Sending notifications...");
                notificationService.sendLoanApprovalNotification(
                        loan.getBorrowerId(),
                        loan.getLoanId(),
                        loan.getPrincipalAmount(),
                        loan.getMonthlyPayment()
                );

                notificationService.sendLoanDisbursementNotification(
                        loan.getBorrowerId(),
                        loan.getLoanId(),
                        loan.getPrincipalAmount()
                );
                log.info("✓ Notifications sent to borrower");

                // STEP 12: Report to credit bureaus
                log.info("Step 9/10: Reporting to credit bureaus...");
                loanAccountService.reportToCreditBureaus(
                        loan.getLoanId(),
                        loan.getBorrowerId(),
                        "****", // SSN last 4 (would come from event)
                        "NEW_ACCOUNT",
                        loan.getPrincipalAmount(),
                        loan.getMonthlyPayment(),
                        application.getLoanType().toString()
                );

                // Schedule monthly reporting
                loanAccountService.scheduleMonthlyReporting(loan.getLoanId(), loan.getBorrowerId());
                log.info("✓ Credit bureau reporting initiated");

                // STEP 13: Create comprehensive audit trail
                log.info("Step 10/10: Creating audit trail...");
                loanAccountService.createAuditEntry(
                        loan.getLoanId(),
                        "LOAN_APPROVED_AND_DISBURSED",
                        event,
                        String.format("Loan approved and disbursed: Amount=%s, Rate=%s%%, Term=%d months",
                                loan.getPrincipalAmount(), loan.getInterestRate(), loan.getTermMonths())
                );

                // Record fair lending data for HMDA/ECOA compliance
                if (event.getDemographicData() != null) {
                    loanAccountService.recordFairLendingData(
                            loan.getLoanId(),
                            loan.getBorrowerId(),
                            event.getDemographicData()
                    );
                }
                log.info("✓ Audit trail created");

                // STEP 14: Mark event as processed (idempotency)
                idempotencyService.markEventAsProcessed(
                        eventId,
                        "LoanApprovedEvent",
                        applicationId
                );

                // STEP 15: Acknowledge message
                acknowledgment.acknowledge();

                log.info("=== LOAN APPROVAL PROCESSING COMPLETED SUCCESSFULLY ===");
                log.info("Loan: {}, Account: {}, Disbursement: {}, Amount: {}",
                        loan.getLoanId(), accountNumber, disbursementId, loan.getPrincipalAmount());

            } finally {
                // Release distributed lock
                idempotencyService.release("loan-approved:" + applicationId);
            }

        } catch (Exception e) {
            log.error("=== LOAN APPROVAL PROCESSING FAILED ===");
            log.error("EventID: {}, ApplicationID: {}", eventId, applicationId, e);

            // Create manual intervention task for immediate attention
            try {
                String taskId = manualInterventionService.createLoanProcessingFailureTask(
                        applicationId,
                        event.getBorrowerId(),
                        event.getApprovedAmount(),
                        "Loan approval processing failed: " + e.getMessage(),
                        e
                );

                log.error("Manual intervention task created: {}", taskId);

            } catch (Exception taskException) {
                log.error("Failed to create manual intervention task", taskException);
            }

            // Re-throw to trigger DLQ delivery
            throw new RuntimeException("Loan approval processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Calculate total interest over loan lifetime
     */
    private BigDecimal calculateTotalInterest(Loan loan) {
        BigDecimal totalPayments = loan.getMonthlyPayment()
                .multiply(BigDecimal.valueOf(loan.getTermMonths()));
        return totalPayments.subtract(loan.getPrincipalAmount());
    }

    /**
     * Calculate total finance charge (interest + fees)
     */
    private BigDecimal calculateFinanceCharge(Loan loan, BigDecimal totalInterest) {
        // Finance charge = total interest + origination fees
        // Origination fee would come from loan data
        BigDecimal originationFee = BigDecimal.ZERO; // TODO: Get from loan
        return totalInterest.add(originationFee);
    }
}
