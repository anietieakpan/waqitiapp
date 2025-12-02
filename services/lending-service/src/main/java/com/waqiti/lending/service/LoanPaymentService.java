package com.waqiti.lending.service;

import com.waqiti.lending.domain.Loan;
import com.waqiti.lending.domain.LoanPayment;
import com.waqiti.lending.domain.enums.PaymentStatus;
import com.waqiti.lending.repository.LoanPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Loan Payment Service
 * Handles payment processing for loans
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanPaymentService {

    private final LoanPaymentRepository loanPaymentRepository;
    private final LoanService loanService;
    private final LoanScheduleService loanScheduleService;

    /**
     * Process a loan payment
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public LoanPayment processPayment(String loanId, UUID borrowerId, BigDecimal paymentAmount,
                                     String paymentMethod, boolean isAutopay) {
        // Get loan
        Loan loan = loanService.findByLoanId(loanId);

        // Validate payment
        if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }

        // Calculate interest and principal allocation
        BigDecimal monthlyRate = loan.getInterestRate().divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        BigDecimal interestPortion = loan.getOutstandingBalance().multiply(monthlyRate)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal principalPortion = paymentAmount.subtract(interestPortion);

        // Ensure principal doesn't exceed outstanding balance
        if (principalPortion.compareTo(loan.getOutstandingBalance()) > 0) {
            principalPortion = loan.getOutstandingBalance();
        }

        // Create payment record
        LoanPayment payment = LoanPayment.builder()
                .paymentId(generatePaymentId())
                .loanId(loanId)
                .borrowerId(borrowerId)
                .paymentDate(Instant.now())
                .dueDate(loan.getNextPaymentDueDate())
                .paymentAmount(paymentAmount)
                .principalAmount(principalPortion)
                .interestAmount(interestPortion)
                .lateFee(BigDecimal.ZERO)
                .paymentMethod(paymentMethod)
                .paymentStatus(PaymentStatus.COMPLETED)
                .isAutopay(isAutopay)
                .isExtraPayment(false)
                .confirmationNumber(generateConfirmationNumber())
                .build();

        // Check if payment is late
        if (payment.isLate()) {
            BigDecimal lateFee = BigDecimal.valueOf(25.00); // TODO: Make configurable
            payment.setLateFee(lateFee);
            log.warn("Late payment fee applied: {} for loan: {}", lateFee, loanId);
        }

        // Save payment
        LoanPayment savedPayment = loanPaymentRepository.save(payment);

        // Update loan
        loanService.applyPayment(loanId, paymentAmount, principalPortion, interestPortion);

        // Mark schedule as paid
        var nextSchedule = loanScheduleService.getNextDuePayment(loanId);
        if (nextSchedule != null) {
            loanScheduleService.markAsPaid(loanId, nextSchedule.getPaymentNumber(),
                    LocalDate.now(), paymentAmount);
        }

        log.info("Payment processed: {} for loan: {} - Amount: {}, Principal: {}, Interest: {}",
                savedPayment.getPaymentId(), loanId, paymentAmount, principalPortion, interestPortion);

        return savedPayment;
    }

    /**
     * Process early payoff
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public LoanPayment processEarlyPayoff(String loanId, UUID borrowerId, String paymentMethod) {
        Loan loan = loanService.findByLoanId(loanId);

        // Calculate payoff amount
        BigDecimal payoffAmount = loan.calculateTotalAmountDue();

        // Process as regular payment
        return processPayment(loanId, borrowerId, payoffAmount, paymentMethod, false);
    }

    /**
     * Find payment by ID
     */
    @Transactional(readOnly = true)
    public LoanPayment findByPaymentId(String paymentId) {
        return loanPaymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));
    }

    /**
     * Get all payments for loan
     */
    @Transactional(readOnly = true)
    public List<LoanPayment> findByLoan(String loanId) {
        return loanPaymentRepository.findByLoanIdOrderByPaymentDateDesc(loanId);
    }

    /**
     * Get payments by borrower
     */
    @Transactional(readOnly = true)
    public List<LoanPayment> findByBorrower(UUID borrowerId) {
        return loanPaymentRepository.findByBorrowerIdOrderByPaymentDateDesc(borrowerId);
    }

    /**
     * Calculate total payments for loan
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalPaymentsForLoan(String loanId) {
        BigDecimal total = loanPaymentRepository.calculateTotalPaymentsForLoan(loanId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Calculate total interest paid for loan
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalInterestPaid(String loanId) {
        BigDecimal total = loanPaymentRepository.calculateTotalInterestPaid(loanId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Mark payment as failed
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoanPayment markPaymentAsFailed(String paymentId, String reason) {
        LoanPayment payment = findByPaymentId(paymentId);
        payment.markAsFailed(reason);

        LoanPayment saved = loanPaymentRepository.save(payment);
        log.error("Payment marked as failed: {} - Reason: {}", paymentId, reason);

        return saved;
    }

    /**
     * Get failed payments
     */
    @Transactional(readOnly = true)
    public List<LoanPayment> getFailedPayments() {
        return loanPaymentRepository.findByPaymentStatusOrderByPaymentDateDesc(PaymentStatus.FAILED);
    }

    /**
     * Count payments for loan
     */
    @Transactional(readOnly = true)
    public long countPaymentsForLoan(String loanId) {
        return loanPaymentRepository.countByLoanId(loanId);
    }

    /**
     * Generate unique payment ID
     */
    private String generatePaymentId() {
        return "PAY-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Generate confirmation number
     */
    private String generateConfirmationNumber() {
        return "CONF-" + UUID.randomUUID().toString().toUpperCase();
    }
}
