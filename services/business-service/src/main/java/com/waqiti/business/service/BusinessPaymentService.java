package com.waqiti.business.service;

import com.waqiti.business.domain.*;
import com.waqiti.business.dto.*;
import com.waqiti.business.exception.BusinessExceptions.*;
import com.waqiti.business.repository.*;
import com.waqiti.common.security.AuthenticationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Comprehensive Business Payment Processing Service
 * 
 * Handles B2B payments, vendor management, payment automation,
 * ACH processing, wire transfers, and payment reconciliation
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BusinessPaymentService {

    private final BusinessAccountRepository businessAccountRepository;
    private final BusinessPaymentRepository paymentRepository;
    private final BusinessVendorRepository vendorRepository;
    private final BusinessContractRepository contractRepository;
    private final PaymentScheduleRepository scheduleRepository;
    private final PaymentApprovalRepository approvalRepository;
    private final ReconciliationRepository reconciliationRepository;
    private final AuthenticationFacade authenticationFacade;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Payment constants
    private static final BigDecimal ACH_FEE = new BigDecimal("1.50");
    private static final BigDecimal WIRE_FEE = new BigDecimal("25.00");
    private static final BigDecimal INTERNATIONAL_WIRE_FEE = new BigDecimal("45.00");
    private static final BigDecimal ACH_DAILY_LIMIT = new BigDecimal("250000.00");
    private static final BigDecimal WIRE_DAILY_LIMIT = new BigDecimal("1000000.00");

    /**
     * Process business-to-business payment
     */
    public BusinessPaymentResponse processB2BPayment(UUID accountId, CreateBusinessPaymentRequest request) {
        log.info("Processing B2B payment for account: {} amount: {}", accountId, request.getAmount());

        try {
            BusinessAccount account = getValidatedBusinessAccount(accountId);
            validatePaymentRequest(request, account);

            // Get or create vendor
            BusinessVendor vendor = getOrCreateVendor(accountId, request.getVendor());

            // Check payment limits and approvals
            PaymentApprovalResult approvalResult = checkPaymentApproval(account, request);

            // Create payment record
            BusinessPayment payment = createBusinessPayment(account, vendor, request, approvalResult);

            // Process payment based on type
            PaymentProcessingResult processingResult = switch (request.getPaymentMethod()) {
                case ACH -> processACHPayment(payment);
                case WIRE -> processWirePayment(payment);
                case CHECK -> processCheckPayment(payment);
                case CARD -> processCardPayment(payment);
                case INTERNATIONAL_WIRE -> processInternationalWirePayment(payment);
            };

            // Update payment status
            payment.setProcessingStatus(processingResult.getStatus());
            payment.setTransactionId(processingResult.getTransactionId());
            payment.setProcessingFee(processingResult.getFee());
            payment.setEstimatedSettlementDate(processingResult.getEstimatedSettlement());
            payment = paymentRepository.save(payment);

            // Send notifications
            sendPaymentNotifications(payment, vendor);

            // Publish payment event
            publishPaymentEvent(payment);

            log.info("B2B payment processed successfully: {} status: {}", payment.getId(), payment.getStatus());

            return mapToBusinessPaymentResponse(payment, vendor);

        } catch (Exception e) {
            log.error("Failed to process B2B payment for account: {}", accountId, e);
            throw new BusinessPaymentException("Failed to process payment: " + e.getMessage(), e);
        }
    }

    /**
     * Create and manage vendor relationships
     */
    public BusinessVendorResponse createVendor(UUID accountId, CreateVendorRequest request) {
        log.info("Creating vendor for account: {} vendor: {}", accountId, request.getVendorName());

        BusinessAccount account = getValidatedBusinessAccount(accountId);

        // Check for duplicate vendor
        if (vendorRepository.existsByAccountIdAndVendorName(accountId, request.getVendorName())) {
            throw new DuplicateVendorException("Vendor with this name already exists");
        }

        BusinessVendor vendor = BusinessVendor.builder()
                .accountId(accountId)
                .vendorName(request.getVendorName())
                .vendorType(request.getVendorType())
                .contactPerson(request.getContactPerson())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .address(request.getAddress())
                .taxId(request.getTaxId())
                .paymentTerms(request.getPaymentTerms())
                .preferredPaymentMethod(request.getPreferredPaymentMethod())
                .currency(request.getCurrency())
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        vendor = vendorRepository.save(vendor);

        // Create initial contract if provided
        if (request.getContractDetails() != null) {
            createVendorContract(vendor, request.getContractDetails());
        }

        return mapToVendorResponse(vendor);
    }

    /**
     * Set up automated payment schedules
     */
    public PaymentScheduleResponse createPaymentSchedule(UUID accountId, CreatePaymentScheduleRequest request) {
        log.info("Creating payment schedule for account: {} vendor: {}", accountId, request.getVendorId());

        BusinessAccount account = getValidatedBusinessAccount(accountId);
        BusinessVendor vendor = getValidatedVendor(accountId, request.getVendorId());

        PaymentSchedule schedule = PaymentSchedule.builder()
                .accountId(accountId)
                .vendorId(request.getVendorId())
                .paymentType(request.getPaymentType())
                .amount(request.getAmount())
                .frequency(request.getFrequency())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .nextPaymentDate(calculateNextPaymentDate(request.getStartDate(), request.getFrequency()))
                .paymentMethod(request.getPaymentMethod())
                .description(request.getDescription())
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        schedule = scheduleRepository.save(schedule);

        log.info("Payment schedule created: {} next payment: {}", schedule.getId(), schedule.getNextPaymentDate());

        return mapToScheduleResponse(schedule, vendor);
    }

    /**
     * Process scheduled payments (automated job)
     */
    @Async
    public CompletableFuture<ScheduledPaymentResults> processScheduledPayments() {
        log.info("Processing scheduled payments");

        try {
            LocalDate today = LocalDate.now();
            List<PaymentSchedule> dueSchedules = scheduleRepository.findDuePayments(today);

            List<ScheduledPaymentResult> results = new ArrayList<>();
            int successCount = 0;
            int failureCount = 0;

            for (PaymentSchedule schedule : dueSchedules) {
                try {
                    ScheduledPaymentResult result = processScheduledPayment(schedule);
                    results.add(result);

                    if (result.isSuccess()) {
                        successCount++;
                        // Update next payment date
                        schedule.setNextPaymentDate(
                                calculateNextPaymentDate(schedule.getNextPaymentDate(), schedule.getFrequency()));
                        schedule.setLastPaymentDate(today);
                        scheduleRepository.save(schedule);
                    } else {
                        failureCount++;
                    }

                } catch (Exception e) {
                    log.error("Failed to process scheduled payment: {}", schedule.getId(), e);
                    results.add(ScheduledPaymentResult.failure(schedule.getId(), e.getMessage()));
                    failureCount++;
                }
            }

            log.info("Scheduled payment processing completed. Success: {}, Failures: {}", successCount, failureCount);

            return CompletableFuture.completedFuture(ScheduledPaymentResults.builder()
                    .totalProcessed(dueSchedules.size())
                    .successCount(successCount)
                    .failureCount(failureCount)
                    .results(results)
                    .processedAt(LocalDateTime.now())
                    .build());

        } catch (Exception e) {
            log.error("Error in scheduled payment processing", e);
            throw new BusinessPaymentException("Scheduled payment processing failed", e);
        }
    }

    /**
     * Manage payment approvals and workflows
     */
    public PaymentApprovalResponse submitForApproval(UUID accountId, UUID paymentId) {
        log.info("Submitting payment for approval: {} account: {}", paymentId, accountId);

        BusinessAccount account = getValidatedBusinessAccount(accountId);
        BusinessPayment payment = getValidatedPayment(accountId, paymentId);

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidPaymentStatusException("Payment is not in pending status");
        }

        // Create approval workflow
        PaymentApproval approval = PaymentApproval.builder()
                .paymentId(paymentId)
                .accountId(accountId)
                .requestedBy(UUID.fromString(authenticationFacade.getCurrentUserId()))
                .requestedAt(LocalDateTime.now())
                .approvalLevel(determineApprovalLevel(payment.getAmount(), account))
                .status(ApprovalStatus.PENDING)
                .build();

        approval = approvalRepository.save(approval);

        // Update payment status
        payment.setStatus(PaymentStatus.PENDING_APPROVAL);
        payment.setApprovalId(approval.getId());
        paymentRepository.save(payment);

        // Notify approvers
        notifyApprovers(approval, payment);

        return mapToApprovalResponse(approval);
    }

    /**
     * Approve or reject payment
     */
    public PaymentApprovalResponse processApproval(UUID accountId, UUID approvalId, PaymentApprovalRequest request) {
        log.info("Processing payment approval: {} decision: {}", approvalId, request.getDecision());

        BusinessAccount account = getValidatedBusinessAccount(accountId);
        PaymentApproval approval = approvalRepository.findByIdAndAccountId(approvalId, accountId)
                .orElseThrow(() -> new ApprovalNotFoundException("Payment approval not found"));

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new InvalidApprovalStatusException("Approval is not in pending status");
        }

        // Update approval
        approval.setApprovedBy(UUID.fromString(authenticationFacade.getCurrentUserId()));
        approval.setApprovedAt(LocalDateTime.now());
        approval.setComments(request.getComments());
        approval.setStatus(request.getDecision() == ApprovalDecision.APPROVE ? 
                ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);

        approval = approvalRepository.save(approval);

        // Update payment status
        BusinessPayment payment = paymentRepository.findById(approval.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found"));

        if (approval.getStatus() == ApprovalStatus.APPROVED) {
            payment.setStatus(PaymentStatus.APPROVED);
            // Automatically process if configured
            if (account.isAutoProcessApprovedPayments()) {
                processApprovedPayment(payment);
            }
        } else {
            payment.setStatus(PaymentStatus.REJECTED);
        }

        paymentRepository.save(payment);

        // Send notifications
        sendApprovalNotifications(payment, approval);

        return mapToApprovalResponse(approval);
    }

    /**
     * Reconcile payments with bank statements
     */
    public ReconciliationResponse reconcilePayments(UUID accountId, BankReconciliationRequest request) {
        log.info("Starting payment reconciliation for account: {} statement date: {}", 
                accountId, request.getStatementDate());

        try {
            BusinessAccount account = getValidatedBusinessAccount(accountId);

            // Get payments to reconcile
            List<BusinessPayment> paymentsToReconcile = paymentRepository
                    .findByAccountIdAndProcessedDateBetween(
                            accountId, 
                            request.getStatementStartDate().atStartOfDay(),
                            request.getStatementEndDate().atTime(23, 59, 59));

            // Parse bank statement
            List<BankTransaction> bankTransactions = parseBankStatement(request.getBankStatementData());

            // Perform reconciliation
            ReconciliationResult result = performReconciliation(paymentsToReconcile, bankTransactions);

            // Save reconciliation record
            PaymentReconciliation reconciliation = PaymentReconciliation.builder()
                    .accountId(accountId)
                    .statementDate(request.getStatementDate())
                    .totalPayments(result.getTotalPayments())
                    .totalBankTransactions(result.getTotalBankTransactions())
                    .matchedCount(result.getMatchedTransactions().size())
                    .unmatchedPaymentsCount(result.getUnmatchedPayments().size())
                    .unmatchedTransactionsCount(result.getUnmatchedTransactions().size())
                    .reconciledBy(UUID.fromString(authenticationFacade.getCurrentUserId()))
                    .reconciledAt(LocalDateTime.now())
                    .status(result.isFullyReconciled() ? ReconciliationStatus.COMPLETED : ReconciliationStatus.PARTIAL)
                    .build();

            reconciliation = reconciliationRepository.save(reconciliation);

            // Update payment statuses for matched transactions
            updateReconciledPayments(result.getMatchedTransactions());

            log.info("Reconciliation completed: {} matched: {} unmatched payments: {} unmatched transactions: {}", 
                    reconciliation.getId(), result.getMatchedTransactions().size(), 
                    result.getUnmatchedPayments().size(), result.getUnmatchedTransactions().size());

            return ReconciliationResponse.builder()
                    .reconciliationId(reconciliation.getId())
                    .status(reconciliation.getStatus())
                    .totalPayments(result.getTotalPayments())
                    .matchedCount(result.getMatchedTransactions().size())
                    .unmatchedPayments(result.getUnmatchedPayments())
                    .unmatchedTransactions(result.getUnmatchedTransactions())
                    .reconciledAt(reconciliation.getReconciledAt())
                    .build();

        } catch (Exception e) {
            log.error("Failed to reconcile payments for account: {}", accountId, e);
            throw new ReconciliationException("Payment reconciliation failed", e);
        }
    }

    /**
     * Generate payment analytics and reports
     */
    @Transactional(readOnly = true)
    public BusinessPaymentAnalytics generatePaymentAnalytics(UUID accountId, PaymentAnalyticsRequest request) {
        log.info("Generating payment analytics for account: {} period: {} to {}", 
                accountId, request.getStartDate(), request.getEndDate());

        try {
            BusinessAccount account = getValidatedBusinessAccount(accountId);

            // Get payment data for the period
            List<BusinessPayment> payments = paymentRepository.findByAccountIdAndCreatedAtBetween(
                    accountId, request.getStartDate().atStartOfDay(), request.getEndDate().atTime(23, 59, 59));

            // Calculate payment metrics
            PaymentMetrics metrics = calculatePaymentMetrics(payments);

            // Vendor spending analysis
            Map<String, VendorSpendingAnalysis> vendorAnalysis = analyzeVendorSpending(payments);

            // Payment method analysis
            Map<PaymentMethod, PaymentMethodAnalysis> methodAnalysis = analyzePaymentMethods(payments);

            // Cash flow analysis
            CashFlowAnalysis cashFlow = analyzeCashFlow(payments, request);

            // Payment trends
            List<PaymentTrend> trends = calculatePaymentTrends(payments, request);

            // Outstanding payments
            OutstandingPaymentsAnalysis outstanding = analyzeOutstandingPayments(accountId);

            return BusinessPaymentAnalytics.builder()
                    .accountId(accountId)
                    .period(request.getStartDate() + " to " + request.getEndDate())
                    .metrics(metrics)
                    .vendorAnalysis(vendorAnalysis)
                    .methodAnalysis(methodAnalysis)
                    .cashFlow(cashFlow)
                    .trends(trends)
                    .outstanding(outstanding)
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate payment analytics for account: {}", accountId, e);
            throw new AnalyticsException("Payment analytics generation failed", e);
        }
    }

    // Helper methods for payment processing

    private PaymentProcessingResult processACHPayment(BusinessPayment payment) {
        log.debug("Processing ACH payment: {}", payment.getId());

        // Validate ACH daily limits
        BigDecimal todayACH = getTodayACHTotal(payment.getAccountId());
        if (todayACH.add(payment.getAmount()).compareTo(ACH_DAILY_LIMIT) > 0) {
            throw new PaymentLimitExceededException("ACH daily limit exceeded");
        }

        // Simulate ACH processing
        String transactionId = "ACH-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate settlementDate = LocalDate.now().plusDays(1); // Next business day

        return PaymentProcessingResult.builder()
                .status(ProcessingStatus.SUBMITTED)
                .transactionId(transactionId)
                .fee(ACH_FEE)
                .estimatedSettlement(settlementDate)
                .build();
    }

    private PaymentProcessingResult processWirePayment(BusinessPayment payment) {
        log.debug("Processing wire payment: {}", payment.getId());

        // Validate wire daily limits
        BigDecimal todayWire = getTodayWireTotal(payment.getAccountId());
        if (todayWire.add(payment.getAmount()).compareTo(WIRE_DAILY_LIMIT) > 0) {
            throw new PaymentLimitExceededException("Wire daily limit exceeded");
        }

        // Simulate wire processing
        String transactionId = "WIRE-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate settlementDate = LocalDate.now(); // Same day for domestic wires

        return PaymentProcessingResult.builder()
                .status(ProcessingStatus.SUBMITTED)
                .transactionId(transactionId)
                .fee(WIRE_FEE)
                .estimatedSettlement(settlementDate)
                .build();
    }

    private BusinessAccount getValidatedBusinessAccount(UUID accountId) {
        BusinessAccount account = businessAccountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessAccountNotFoundException("Business account not found"));

        String currentUserId = authenticationFacade.getCurrentUserId();
        if (!account.getOwnerId().toString().equals(currentUserId)) {
            throw new UnauthorizedBusinessAccessException("Unauthorized access to business account");
        }

        return account;
    }

    private void validatePaymentRequest(CreateBusinessPaymentRequest request, BusinessAccount account) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentAmountException("Payment amount must be positive");
        }

        if (request.getAmount().compareTo(account.getMonthlyTransactionLimit()) > 0) {
            throw new PaymentLimitExceededException("Payment amount exceeds monthly limit");
        }
    }

    // Additional helper methods and placeholder implementations...

    private BusinessVendor getOrCreateVendor(UUID accountId, VendorDetails vendorDetails) {
        return vendorRepository.findByAccountIdAndVendorName(accountId, vendorDetails.getName())
                .orElseGet(() -> createVendorFromDetails(accountId, vendorDetails));
    }

    private BusinessVendor createVendorFromDetails(UUID accountId, VendorDetails details) {
        return BusinessVendor.builder()
                .accountId(accountId)
                .vendorName(details.getName())
                .email(details.getEmail())
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private PaymentApprovalResult checkPaymentApproval(BusinessAccount account, CreateBusinessPaymentRequest request) {
        boolean requiresApproval = request.getAmount().compareTo(account.getAutoApprovalLimit()) > 0;
        
        return PaymentApprovalResult.builder()
                .requiresApproval(requiresApproval)
                .approvalLevel(determineApprovalLevel(request.getAmount(), account))
                .build();
    }

    private ApprovalLevel determineApprovalLevel(BigDecimal amount, BusinessAccount account) {
        if (amount.compareTo(new BigDecimal("50000")) > 0) {
            return ApprovalLevel.EXECUTIVE;
        } else if (amount.compareTo(new BigDecimal("10000")) > 0) {
            return ApprovalLevel.MANAGER;
        } else {
            return ApprovalLevel.SUPERVISOR;
        }
    }

    // Placeholder implementations for complex operations
    private BigDecimal getTodayACHTotal(UUID accountId) { return BigDecimal.ZERO; }
    private BigDecimal getTodayWireTotal(UUID accountId) { return BigDecimal.ZERO; }
    private LocalDate calculateNextPaymentDate(LocalDate start, PaymentFrequency frequency) { return start.plusDays(30); }
    private void sendPaymentNotifications(BusinessPayment payment, BusinessVendor vendor) {}
    private void publishPaymentEvent(BusinessPayment payment) {}
    private void notifyApprovers(PaymentApproval approval, BusinessPayment payment) {}

    // Response mapping methods
    private BusinessPaymentResponse mapToBusinessPaymentResponse(BusinessPayment payment, BusinessVendor vendor) {
        return BusinessPaymentResponse.builder()
                .paymentId(payment.getId())
                .vendorName(vendor.getVendorName())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .build();
    }

    private BusinessVendorResponse mapToVendorResponse(BusinessVendor vendor) {
        return BusinessVendorResponse.builder()
                .vendorId(vendor.getId())
                .vendorName(vendor.getVendorName())
                .email(vendor.getEmail())
                .isActive(vendor.isActive())
                .build();
    }

    // Enum definitions and data classes would be defined here...
    public enum PaymentMethod { ACH, WIRE, CHECK, CARD, INTERNATIONAL_WIRE }
    public enum PaymentStatus { PENDING, PENDING_APPROVAL, APPROVED, REJECTED, PROCESSING, COMPLETED, FAILED }
    public enum ProcessingStatus { SUBMITTED, PROCESSING, COMPLETED, FAILED }
    public enum ApprovalLevel { SUPERVISOR, MANAGER, EXECUTIVE }
    public enum ApprovalStatus { PENDING, APPROVED, REJECTED }
    public enum ApprovalDecision { APPROVE, REJECT }
    public enum PaymentFrequency { WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY }
    public enum ReconciliationStatus { PENDING, PARTIAL, COMPLETED }
}