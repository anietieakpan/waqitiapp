package com.waqiti.lending.service;

import com.waqiti.lending.domain.Loan;
import com.waqiti.lending.domain.LoanApplication;
import com.waqiti.lending.domain.enums.InterestType;
import com.waqiti.lending.domain.enums.LoanStatus;
import com.waqiti.lending.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Loan Service
 * Manages active loan accounts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanService {

    private final LoanRepository loanRepository;
    private final LoanScheduleService loanScheduleService;

    /**
     * Originate a new loan from approved application
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Loan originateLoan(LoanApplication application) {
        if (!application.isApproved()) {
            throw new IllegalStateException("Cannot originate loan from non-approved application");
        }

        // Check if loan already exists for this application
        if (loanRepository.findByApplicationId(application.getApplicationId()).isPresent()) {
            throw new IllegalStateException("Loan already exists for application: " + application.getApplicationId());
        }

        // Create loan
        Loan loan = Loan.builder()
                .loanId(generateLoanId())
                .applicationId(application.getApplicationId())
                .borrowerId(application.getBorrowerId())
                .coBorrowerId(application.getCoBorrowerId())
                .loanType(application.getLoanType())
                .principalAmount(application.getApprovedAmount())
                .currency(application.getCurrency())
                .interestRate(application.getApprovedInterestRate())
                .interestType(InterestType.FIXED)
                .termMonths(application.getApprovedTermMonths())
                .outstandingBalance(application.getApprovedAmount())
                .loanStatus(LoanStatus.PENDING)
                .creditScoreAtOrigination(application.getCreditScore())
                .maturityDate(LocalDate.now().plusMonths(application.getApprovedTermMonths()))
                .build();

        // Calculate monthly payment
        BigDecimal monthlyPayment = loan.calculateMonthlyPayment();
        loan.setMonthlyPayment(monthlyPayment);

        Loan savedLoan = loanRepository.save(loan);
        log.info("Loan originated: {} for borrower: {} with principal: {}",
                savedLoan.getLoanId(), savedLoan.getBorrowerId(), savedLoan.getPrincipalAmount());

        // Generate payment schedule
        loanScheduleService.generateSchedule(savedLoan);

        return savedLoan;
    }

    /**
     * Find loan by ID
     */
    @Transactional(readOnly = true)
    public Loan findByLoanId(String loanId) {
        return loanRepository.findByLoanId(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanId));
    }

    /**
     * Get all loans for borrower
     */
    @Transactional(readOnly = true)
    public List<Loan> findByBorrower(UUID borrowerId) {
        return loanRepository.findByBorrowerIdOrderByCreatedAtDesc(borrowerId);
    }

    /**
     * Get loans by status
     */
    @Transactional(readOnly = true)
    public Page<Loan> findByStatus(LoanStatus status, Pageable pageable) {
        return loanRepository.findByLoanStatus(status, pageable);
    }

    /**
     * Disburse loan funds
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Loan disburseLoan(String loanId, String disbursementMethod) {
        Loan loan = findByLoanId(loanId);

        if (loan.getLoanStatus() != LoanStatus.PENDING) {
            throw new IllegalStateException("Cannot disburse loan in status: " + loan.getLoanStatus());
        }

        loan.disburse(Instant.now(), disbursementMethod);

        // Set first payment date (typically 30 days from disbursement)
        loan.setFirstPaymentDate(LocalDate.now().plusDays(30));
        loan.setNextPaymentDueDate(LocalDate.now().plusDays(30));

        Loan saved = loanRepository.save(loan);
        log.info("Loan disbursed: {} for borrower: {} via {}",
                saved.getLoanId(), saved.getBorrowerId(), disbursementMethod);

        return saved;
    }

    /**
     * Apply payment to loan
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Loan applyPayment(String loanId, BigDecimal paymentAmount,
                            BigDecimal principalPortion, BigDecimal interestPortion) {
        Loan loan = findByLoanId(loanId);

        if (!loan.isActive()) {
            throw new IllegalStateException("Cannot apply payment to loan in status: " + loan.getLoanStatus());
        }

        loan.applyPayment(paymentAmount, principalPortion, interestPortion);

        // Update next payment due date
        if (loan.getNextPaymentDueDate() != null) {
            loan.setNextPaymentDueDate(loan.getNextPaymentDueDate().plusMonths(1));
        }

        // Reset days past due on successful payment
        loan.setDaysPastDue(0);
        loan.setLoanStatus(LoanStatus.CURRENT);

        Loan saved = loanRepository.save(loan);
        log.info("Payment applied to loan: {} - Amount: {}, New balance: {}",
                loanId, paymentAmount, saved.getOutstandingBalance());

        return saved;
    }

    /**
     * Mark loan as delinquent
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Loan markDelinquent(String loanId, int daysPastDue) {
        Loan loan = findByLoanId(loanId);
        loan.markDelinquent(daysPastDue);

        Loan saved = loanRepository.save(loan);
        log.warn("Loan marked delinquent: {} - Days past due: {}, Status: {}",
                loanId, daysPastDue, saved.getLoanStatus());

        return saved;
    }

    /**
     * Charge off loan
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Loan chargeOffLoan(String loanId) {
        Loan loan = findByLoanId(loanId);

        if (loan.getDaysPastDue() < 120) {
            throw new IllegalStateException("Loan must be 120+ days past due to charge off");
        }

        loan.chargeOff();

        Loan saved = loanRepository.save(loan);
        log.error("Loan charged off: {} - Outstanding balance: {}",
                loanId, saved.getOutstandingBalance());

        return saved;
    }

    /**
     * Update loan status
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Loan updateStatus(String loanId, LoanStatus newStatus) {
        Loan loan = findByLoanId(loanId);
        loan.setLoanStatus(newStatus);

        Loan saved = loanRepository.save(loan);
        log.info("Loan status updated: {} to {}", loanId, newStatus);

        return saved;
    }

    /**
     * Get active loans
     */
    @Transactional(readOnly = true)
    public List<Loan> getActiveLoans() {
        return loanRepository.findActiveLoans();
    }

    /**
     * Get delinquent loans
     */
    @Transactional(readOnly = true)
    public List<Loan> getDelinquentLoans() {
        return loanRepository.findDelinquentLoans();
    }

    /**
     * Get loans due today
     */
    @Transactional(readOnly = true)
    public List<Loan> getLoansDueToday() {
        return loanRepository.findLoansDueOnDate(LocalDate.now());
    }

    /**
     * Calculate total portfolio outstanding balance
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalOutstandingBalance() {
        BigDecimal total = loanRepository.calculateTotalOutstandingBalance();
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Calculate borrower's outstanding balance
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateBorrowerOutstandingBalance(UUID borrowerId) {
        BigDecimal total = loanRepository.calculateBorrowerOutstandingBalance(borrowerId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Count active loans for borrower
     */
    @Transactional(readOnly = true)
    public long countActiveLoansByBorrower(UUID borrowerId) {
        return loanRepository.countActiveLoansByBorrower(borrowerId);
    }

    /**
     * Generate unique loan ID
     */
    private String generateLoanId() {
        return "LOAN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Get loan portfolio statistics
     */
    @Transactional(readOnly = true)
    public List<Object[]> getLoanPortfolioStatistics() {
        return loanRepository.getLoanPortfolioStatistics();
    }
}
