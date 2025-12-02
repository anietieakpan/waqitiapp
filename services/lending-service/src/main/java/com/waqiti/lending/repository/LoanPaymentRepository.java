package com.waqiti.lending.repository;

import com.waqiti.lending.domain.LoanPayment;
import com.waqiti.lending.domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Loan Payment entities
 */
@Repository
public interface LoanPaymentRepository extends JpaRepository<LoanPayment, UUID> {

    /**
     * Find payment by payment ID
     */
    Optional<LoanPayment> findByPaymentId(String paymentId);

    /**
     * Check if payment ID exists
     */
    boolean existsByPaymentId(String paymentId);

    /**
     * Find all payments for a loan
     */
    List<LoanPayment> findByLoanIdOrderByPaymentDateDesc(String loanId);

    /**
     * Find payments by borrower
     */
    List<LoanPayment> findByBorrowerIdOrderByPaymentDateDesc(UUID borrowerId);

    /**
     * Find payments by status
     */
    List<LoanPayment> findByPaymentStatus(PaymentStatus status);

    /**
     * Find payments in date range
     */
    @Query("SELECT lp FROM LoanPayment lp WHERE lp.paymentDate BETWEEN :startDate AND :endDate ORDER BY lp.paymentDate DESC")
    List<LoanPayment> findPaymentsInDateRange(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    /**
     * Calculate total payments for loan
     */
    @Query("SELECT SUM(lp.paymentAmount) FROM LoanPayment lp WHERE lp.loanId = :loanId AND lp.paymentStatus = 'COMPLETED'")
    BigDecimal calculateTotalPaymentsForLoan(@Param("loanId") String loanId);

    /**
     * Calculate total principal paid for loan
     */
    @Query("SELECT SUM(lp.principalAmount) FROM LoanPayment lp WHERE lp.loanId = :loanId AND lp.paymentStatus = 'COMPLETED'")
    BigDecimal calculateTotalPrincipalPaid(@Param("loanId") String loanId);

    /**
     * Calculate total interest paid for loan
     */
    @Query("SELECT SUM(lp.interestAmount) FROM LoanPayment lp WHERE lp.loanId = :loanId AND lp.paymentStatus = 'COMPLETED'")
    BigDecimal calculateTotalInterestPaid(@Param("loanId") String loanId);

    /**
     * Find failed payments
     */
    List<LoanPayment> findByPaymentStatusOrderByPaymentDateDesc(PaymentStatus status);

    /**
     * Find autopay payments
     */
    List<LoanPayment> findByIsAutopayTrueOrderByPaymentDateDesc();

    /**
     * Count payments for loan
     */
    long countByLoanId(String loanId);

    /**
     * Find recent payments for borrower
     */
    @Query("SELECT lp FROM LoanPayment lp WHERE lp.borrowerId = :borrowerId ORDER BY lp.paymentDate DESC")
    List<LoanPayment> findRecentPaymentsByBorrower(@Param("borrowerId") UUID borrowerId);
}
