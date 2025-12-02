package com.waqiti.bnpl.service;

import com.waqiti.bnpl.domain.BnplInstallment;
import com.waqiti.bnpl.domain.BnplPlan;
import com.waqiti.bnpl.domain.BnplTransaction;
import com.waqiti.bnpl.domain.CreditAssessment;
import com.waqiti.bnpl.domain.enums.*;
import com.waqiti.bnpl.dto.request.*;
import com.waqiti.bnpl.dto.response.*;
import com.waqiti.bnpl.exception.*;
import com.waqiti.bnpl.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing BNPL plans
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BnplPlanService {

    private final BnplPlanRepository planRepository;
    private final BnplInstallmentRepository installmentRepository;
    private final BnplTransactionRepository transactionRepository;
    private final CreditAssessmentService creditAssessmentService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    
    private static final BigDecimal DEFAULT_INTEREST_RATE = new BigDecimal("0.00"); // 0% interest
    private static final BigDecimal LATE_FEE_PERCENTAGE = new BigDecimal("0.05"); // 5% late fee
    private static final int DEFAULT_GRACE_PERIOD_DAYS = 3;

    /**
     * Create a new BNPL plan
     */
    @Transactional
    public BnplPlanDto createBnplPlan(CreateBnplPlanRequest request) {
        log.info("Creating BNPL plan for user: {} with amount: {}", 
                request.getUserId(), request.getPurchaseAmount());

        // Step 1: Perform credit assessment
        CreditAssessment assessment = creditAssessmentService.performAssessment(
                request.getUserId(), request.getPurchaseAmount());

        if (assessment.getStatus() != AssessmentStatus.APPROVED) {
            throw new CreditCheckFailedException("Credit assessment failed: " + 
                    assessment.getDecisionReason());
        }

        // Step 2: Validate request against credit limits
        validateBnplRequest(request, assessment);

        // Step 3: Calculate plan details
        BnplPlan plan = calculateBnplPlan(request, assessment);

        // Step 4: Create installments
        List<BnplInstallment> installments = createInstallments(plan);
        plan.setInstallments(installments);

        // Step 5: Save plan
        plan = planRepository.save(plan);

        // Step 6: Update credit utilization
        creditAssessmentService.updateCreditUtilization(
                assessment.getId(), plan.getFinanceAmount());

        // Step 7: Send confirmation notification
        notificationService.sendPlanCreatedNotification(plan);

        log.info("BNPL plan created successfully: {}", plan.getPlanNumber());
        return mapToDto(plan);
    }

    /**
     * Approve a BNPL plan
     */
    @Transactional
    public BnplPlanDto approvePlan(Long planId, ApprovePlanRequest request) {
        BnplPlan plan = getPlanById(planId);
        
        plan.approve();
        
        // Process down payment if required
        if (plan.getDownPayment() != null && plan.getDownPayment().compareTo(BigDecimal.ZERO) > 0) {
            ProcessPaymentRequest paymentRequest = ProcessPaymentRequest.builder()
                    .planId(planId)
                    .amount(plan.getDownPayment())
                    .paymentMethod(request.getPaymentMethod())
                    .description("Down payment for BNPL plan " + plan.getPlanNumber())
                    .build();
            
            paymentService.processPayment(paymentRequest);
        }
        
        plan = planRepository.save(plan);
        notificationService.sendPlanApprovedNotification(plan);
        
        return mapToDto(plan);
    }

    /**
     * Activate a BNPL plan
     */
    @Transactional
    public BnplPlanDto activatePlan(Long planId) {
        BnplPlan plan = getPlanById(planId);
        
        plan.activate();
        
        // Update installment statuses
        LocalDate today = LocalDate.now();
        plan.getInstallments().forEach(installment -> {
            if (installment.getDueDate().isEqual(today) || installment.getDueDate().isBefore(today)) {
                installment.setStatus(InstallmentStatus.DUE);
            } else {
                installment.setStatus(InstallmentStatus.SCHEDULED);
            }
        });
        
        plan = planRepository.save(plan);
        notificationService.sendPlanActivatedNotification(plan);
        
        return mapToDto(plan);
    }

    /**
     * Cancel a BNPL plan
     */
    @Transactional
    public BnplPlanDto cancelPlan(Long planId, String reason) {
        BnplPlan plan = getPlanById(planId);
        
        plan.cancel(reason);
        
        // Release credit utilization
        if (plan.getCreditAssessment() != null) {
            creditAssessmentService.releaseCreditUtilization(
                    plan.getCreditAssessment().getId(), plan.getFinanceAmount());
        }
        
        // Process refunds if applicable
        if (plan.getTotalPaid().compareTo(BigDecimal.ZERO) > 0) {
            processRefunds(plan);
        }
        
        plan = planRepository.save(plan);
        notificationService.sendPlanCancelledNotification(plan);
        
        return mapToDto(plan);
    }

    /**
     * Get BNPL plan by ID
     */
    @Transactional(readOnly = true)
    public BnplPlanDto getBnplPlan(Long planId) {
        BnplPlan plan = getPlanById(planId);
        return mapToDto(plan);
    }

    /**
     * Get BNPL plans for a user
     */
    @Transactional(readOnly = true)
    public Page<BnplPlanDto> getUserBnplPlans(String userId, Pageable pageable) {
        Page<BnplPlan> plans = planRepository.findByUserId(userId, pageable);
        return plans.map(this::mapToDto);
    }

    /**
     * Get active BNPL plans for a user
     */
    @Transactional(readOnly = true)
    public List<BnplPlanDto> getActiveUserPlans(String userId) {
        List<BnplPlan> plans = planRepository.findByUserIdAndStatus(userId, BnplPlanStatus.ACTIVE);
        return plans.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    /**
     * Get BNPL plan details including installments
     */
    @Transactional(readOnly = true)
    public BnplPlanDetailsDto getBnplPlanDetails(Long planId) {
        BnplPlan plan = getPlanById(planId);
        
        BnplPlanDetailsDto details = new BnplPlanDetailsDto();
        details.setPlan(mapToDto(plan));
        details.setInstallments(plan.getInstallments().stream()
                .map(this::mapInstallmentToDto)
                .collect(Collectors.toList()));
        details.setTransactions(plan.getTransactions().stream()
                .map(this::mapTransactionToDto)
                .sorted(Comparator.comparing(BnplTransactionDto::getCreatedAt).reversed())
                .collect(Collectors.toList()));
        
        // Calculate summary
        BnplPlanSummaryDto summary = new BnplPlanSummaryDto();
        summary.setTotalAmount(plan.getTotalAmount());
        summary.setTotalPaid(plan.getTotalPaid());
        summary.setRemainingBalance(plan.getRemainingBalance());
        summary.setNextDueAmount(plan.getNextDueInstallment() != null ? 
                plan.getNextDueInstallment().getAmountDue() : BigDecimal.ZERO);
        summary.setNextDueDate(plan.getNextDueInstallment() != null ? 
                plan.getNextDueInstallment().getDueDate() : null);
        summary.setCompletedInstallments(plan.getPaidInstallmentsCount());
        summary.setTotalInstallments(plan.getNumberOfInstallments());
        summary.setCompletionPercentage(plan.getCompletionPercentage());
        summary.setIsOverdue(plan.isOverdue());
        summary.setOverdueAmount(plan.getOverdueAmount());
        
        details.setSummary(summary);
        
        return details;
    }

    /**
     * Process installment payment
     */
    @Transactional
    public BnplTransactionDto processInstallmentPayment(ProcessPaymentRequest request) {
        BnplPlan plan = getPlanById(request.getPlanId());
        
        if (plan.getStatus() != BnplPlanStatus.ACTIVE) {
            throw new InvalidPlanStatusException("Plan is not active");
        }
        
        // Find the installment to pay
        BnplInstallment installment = null;
        if (request.getInstallmentId() != null) {
            installment = installmentRepository.findById(request.getInstallmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Installment not found"));
        } else {
            // Pay the next due installment
            installment = plan.getNextDueInstallment();
            if (installment == null) {
                throw new InvalidRequestException("No installment due for payment");
            }
        }
        
        // Process payment
        BnplTransaction transaction = paymentService.processPayment(request);
        
        // Update installment
        installment.markAsPaid(request.getAmount(), transaction.getTransactionId());
        installmentRepository.save(installment);
        
        // Update plan
        plan.addPayment(request.getAmount());
        if (plan.getRemainingBalance().compareTo(BigDecimal.ZERO) <= 0) {
            plan.complete();
        }
        planRepository.save(plan);
        
        // Send notification
        notificationService.sendPaymentSuccessNotification(plan, transaction);
        
        return mapTransactionToDto(transaction);
    }

    /**
     * Get overdue plans
     */
    @Transactional(readOnly = true)
    public List<BnplPlanDto> getOverduePlans() {
        List<BnplPlan> plans = planRepository.findOverduePlans(LocalDate.now());
        return plans.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    /**
     * Apply late fees to overdue installments
     */
    @Transactional
    public void applyLateFees() {
        List<BnplInstallment> overdueInstallments = installmentRepository
                .findOverdueInstallments(LocalDate.now().minusDays(DEFAULT_GRACE_PERIOD_DAYS));
        
        for (BnplInstallment installment : overdueInstallments) {
            if (installment.getLateFee().compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal lateFee = installment.getAmount()
                        .multiply(LATE_FEE_PERCENTAGE)
                        .setScale(2, RoundingMode.HALF_UP);
                
                installment.addLateFee(lateFee);
                installmentRepository.save(installment);
                
                BnplPlan plan = installment.getBnplPlan();
                plan.addLateFee(lateFee);
                planRepository.save(plan);
                
                notificationService.sendLateFeeNotification(plan, installment, lateFee);
            }
        }
    }

    /**
     * Send payment reminders
     */
    @Transactional
    public void sendPaymentReminders() {
        // Send reminders 3 days before due date
        LocalDate reminderDate = LocalDate.now().plusDays(3);
        List<BnplInstallment> upcomingInstallments = installmentRepository
                .findByDueDateAndReminderSentFalse(reminderDate);
        
        for (BnplInstallment installment : upcomingInstallments) {
            notificationService.sendPaymentReminderNotification(
                    installment.getBnplPlan(), installment);
            installment.setReminderSent(true);
            installment.setReminderSentAt(LocalDateTime.now());
            installmentRepository.save(installment);
        }
        
        // Send overdue notifications
        List<BnplInstallment> overdueInstallments = installmentRepository
                .findOverdueWithoutNotification(LocalDate.now());
        
        for (BnplInstallment installment : overdueInstallments) {
            notificationService.sendOverdueNotification(
                    installment.getBnplPlan(), installment);
            installment.setOverdueNotificationSent(true);
            installment.setOverdueNotificationSentAt(LocalDateTime.now());
            installmentRepository.save(installment);
        }
    }

    // Private helper methods

    private BnplPlan getPlanById(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("BNPL plan not found: " + planId));
    }

    private void validateBnplRequest(CreateBnplPlanRequest request, CreditAssessment assessment) {
        // Check if amount is within approved limits
        if (request.getPurchaseAmount().compareTo(assessment.getMaxApprovedAmount()) > 0) {
            throw new InvalidRequestException("Purchase amount exceeds approved limit");
        }
        
        // Check available credit
        if (!assessment.hasAvailableCredit(request.getFinanceAmount())) {
            throw new InsufficientCreditException("Insufficient available credit");
        }
        
        // Check down payment requirements
        BigDecimal minDownPayment = request.getPurchaseAmount()
                .multiply(assessment.getMinDownPaymentPercentage())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        
        if (request.getDownPayment().compareTo(minDownPayment) < 0) {
            throw new InvalidRequestException("Down payment is below minimum required: " + minDownPayment);
        }
        
        // Check installments
        if (request.getNumberOfInstallments() > assessment.getMaxInstallments()) {
            throw new InvalidRequestException("Number of installments exceeds maximum allowed");
        }
    }

    private BnplPlan calculateBnplPlan(CreateBnplPlanRequest request, CreditAssessment assessment) {
        String planNumber = generatePlanNumber();
        
        BigDecimal financeAmount = request.getPurchaseAmount().subtract(request.getDownPayment());
        BigDecimal interestRate = assessment.getInterestRate() != null ? 
                assessment.getInterestRate() : DEFAULT_INTEREST_RATE;
        
        // Calculate total interest
        BigDecimal totalInterest = calculateTotalInterest(
                financeAmount, interestRate, request.getNumberOfInstallments());
        
        BigDecimal totalAmount = financeAmount.add(totalInterest);
        BigDecimal installmentAmount = totalAmount
                .divide(BigDecimal.valueOf(request.getNumberOfInstallments()), 2, RoundingMode.HALF_UP);
        
        LocalDate firstPaymentDate = calculateFirstPaymentDate(request.getPaymentFrequency());
        LocalDate lastPaymentDate = calculateLastPaymentDate(
                firstPaymentDate, request.getPaymentFrequency(), request.getNumberOfInstallments());
        
        return BnplPlan.builder()
                .planNumber(planNumber)
                .userId(request.getUserId())
                .merchantId(request.getMerchantId())
                .merchantName(request.getMerchantName())
                .orderReference(request.getOrderReference())
                .purchaseAmount(request.getPurchaseAmount())
                .downPayment(request.getDownPayment())
                .financeAmount(financeAmount)
                .interestRate(interestRate)
                .totalInterest(totalInterest)
                .totalAmount(totalAmount)
                .numberOfInstallments(request.getNumberOfInstallments())
                .installmentAmount(installmentAmount)
                .paymentFrequency(request.getPaymentFrequency())
                .firstPaymentDate(firstPaymentDate)
                .lastPaymentDate(lastPaymentDate)
                .status(BnplPlanStatus.PENDING_APPROVAL)
                .totalPaid(BigDecimal.ZERO)
                .remainingBalance(totalAmount)
                .lateFees(BigDecimal.ZERO)
                .description(request.getDescription())
                .termsAccepted(request.getTermsAccepted())
                .termsAcceptedAt(request.getTermsAccepted() ? LocalDateTime.now() : null)
                .creditAssessment(assessment)
                .build();
    }

    private List<BnplInstallment> createInstallments(BnplPlan plan) {
        List<BnplInstallment> installments = new ArrayList<>();
        LocalDate dueDate = plan.getFirstPaymentDate();
        
        BigDecimal principalPerInstallment = plan.getFinanceAmount()
                .divide(BigDecimal.valueOf(plan.getNumberOfInstallments()), 2, RoundingMode.HALF_UP);
        BigDecimal interestPerInstallment = plan.getTotalInterest()
                .divide(BigDecimal.valueOf(plan.getNumberOfInstallments()), 2, RoundingMode.HALF_UP);
        
        for (int i = 1; i <= plan.getNumberOfInstallments(); i++) {
            BnplInstallment installment = BnplInstallment.builder()
                    .bnplPlan(plan)
                    .installmentNumber(i)
                    .dueDate(dueDate)
                    .amount(plan.getInstallmentAmount())
                    .principalAmount(principalPerInstallment)
                    .interestAmount(interestPerInstallment)
                    .lateFee(BigDecimal.ZERO)
                    .amountPaid(BigDecimal.ZERO)
                    .amountDue(plan.getInstallmentAmount())
                    .status(InstallmentStatus.SCHEDULED)
                    .build();
            
            installments.add(installment);
            dueDate = calculateNextDueDate(dueDate, plan.getPaymentFrequency());
        }
        
        return installments;
    }

    private BigDecimal calculateTotalInterest(BigDecimal principal, BigDecimal interestRate, int installments) {
        if (interestRate.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        // Simple interest calculation
        BigDecimal monthlyRate = interestRate.divide(BigDecimal.valueOf(1200), 6, RoundingMode.HALF_UP);
        return principal.multiply(monthlyRate).multiply(BigDecimal.valueOf(installments));
    }

    private LocalDate calculateFirstPaymentDate(PaymentFrequency frequency) {
        LocalDate today = LocalDate.now();
        return switch (frequency) {
            case WEEKLY -> today.plusWeeks(1);
            case BI_WEEKLY -> today.plusWeeks(2);
            case MONTHLY -> today.plusMonths(1);
            case QUARTERLY -> today.plusMonths(3);
        };
    }

    private LocalDate calculateLastPaymentDate(LocalDate firstDate, PaymentFrequency frequency, int installments) {
        return switch (frequency) {
            case WEEKLY -> firstDate.plusWeeks(installments - 1);
            case BI_WEEKLY -> firstDate.plusWeeks((installments - 1) * 2L);
            case MONTHLY -> firstDate.plusMonths(installments - 1);
            case QUARTERLY -> firstDate.plusMonths((installments - 1) * 3L);
        };
    }

    private LocalDate calculateNextDueDate(LocalDate currentDate, PaymentFrequency frequency) {
        return switch (frequency) {
            case WEEKLY -> currentDate.plusWeeks(1);
            case BI_WEEKLY -> currentDate.plusWeeks(2);
            case MONTHLY -> currentDate.plusMonths(1);
            case QUARTERLY -> currentDate.plusMonths(3);
        };
    }

    private String generatePlanNumber() {
        return "BNPL-" + System.currentTimeMillis() + "-" + 
                UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void processRefunds(BnplPlan plan) {
        // Implementation for processing refunds
        log.info("Processing refunds for cancelled plan: {}", plan.getPlanNumber());
    }

    private BnplPlanDto mapToDto(BnplPlan plan) {
        return BnplPlanDto.builder()
                .id(plan.getId())
                .planNumber(plan.getPlanNumber())
                .userId(plan.getUserId())
                .merchantName(plan.getMerchantName())
                .purchaseAmount(plan.getPurchaseAmount())
                .downPayment(plan.getDownPayment())
                .financeAmount(plan.getFinanceAmount())
                .totalAmount(plan.getTotalAmount())
                .numberOfInstallments(plan.getNumberOfInstallments())
                .installmentAmount(plan.getInstallmentAmount())
                .paymentFrequency(plan.getPaymentFrequency())
                .status(plan.getStatus())
                .totalPaid(plan.getTotalPaid())
                .remainingBalance(plan.getRemainingBalance())
                .nextDueDate(plan.getNextDueInstallment() != null ? 
                        plan.getNextDueInstallment().getDueDate() : null)
                .completionPercentage(plan.getCompletionPercentage())
                .createdAt(plan.getCreatedAt())
                .build();
    }

    private BnplInstallmentDto mapInstallmentToDto(BnplInstallment installment) {
        return BnplInstallmentDto.builder()
                .id(installment.getId())
                .installmentNumber(installment.getInstallmentNumber())
                .dueDate(installment.getDueDate())
                .amount(installment.getAmount())
                .amountPaid(installment.getAmountPaid())
                .amountDue(installment.getAmountDue())
                .status(installment.getStatus())
                .paidDate(installment.getPaidDate())
                .isOverdue(installment.isOverdue())
                .daysOverdue(installment.getDaysOverdue())
                .build();
    }

    private BnplTransactionDto mapTransactionToDto(BnplTransaction transaction) {
        return BnplTransactionDto.builder()
                .id(transaction.getId())
                .transactionId(transaction.getTransactionId())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .status(transaction.getStatus())
                .paymentMethod(transaction.getPaymentMethod())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}