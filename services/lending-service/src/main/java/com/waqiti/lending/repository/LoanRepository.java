package com.waqiti.lending.repository;

import com.waqiti.lending.domain.Loan;
import com.waqiti.lending.domain.enums.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Loan entities
 */
@Repository
public interface LoanRepository extends JpaRepository<Loan, UUID> {

    /**
     * Find loan by loan ID
     */
    Optional<Loan> findByLoanId(String loanId);

    /**
     * Check if loan ID exists
     */
    boolean existsByLoanId(String loanId);

    /**
     * Find loan by application ID
     */
    Optional<Loan> findByApplicationId(String applicationId);

    /**
     * Find all loans for a borrower
     */
    List<Loan> findByBorrowerIdOrderByCreatedAtDesc(UUID borrowerId);

    /**
     * Find loans by status
     */
    Page<Loan> findByLoanStatus(LoanStatus status, Pageable pageable);

    /**
     * Find loans by borrower and status
     */
    List<Loan> findByBorrowerIdAndLoanStatus(UUID borrowerId, LoanStatus status);

    /**
     * Find active loans
     */
    @Query("SELECT l FROM Loan l WHERE l.loanStatus IN ('ACTIVE', 'CURRENT')")
    List<Loan> findActiveLoans();

    /**
     * Find delinquent loans
     */
    @Query("SELECT l FROM Loan l WHERE l.loanStatus IN ('DELINQUENT', 'DEFAULT') ORDER BY l.daysPastDue DESC")
    List<Loan> findDelinquentLoans();

    /**
     * Find loans past due by threshold
     */
    @Query("SELECT l FROM Loan l WHERE l.daysPastDue >= :daysPastDue AND l.loanStatus NOT IN ('PAID_OFF', 'CLOSED', 'CHARGED_OFF')")
    List<Loan> findLoansPastDueByThreshold(@Param("daysPastDue") Integer daysPastDue);

    /**
     * Find loans with next payment due
     */
    @Query("SELECT l FROM Loan l WHERE l.nextPaymentDueDate = :dueDate AND l.loanStatus IN ('ACTIVE', 'CURRENT')")
    List<Loan> findLoansDueOnDate(@Param("dueDate") LocalDate dueDate);

    /**
     * Find loans maturing soon
     */
    @Query("SELECT l FROM Loan l WHERE l.maturityDate BETWEEN :startDate AND :endDate AND l.loanStatus IN ('ACTIVE', 'CURRENT')")
    List<Loan> findLoansMaturi ngSoon(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Calculate total outstanding balance
     */
    @Query("SELECT SUM(l.outstandingBalance) FROM Loan l WHERE l.loanStatus IN ('ACTIVE', 'CURRENT', 'DELINQUENT', 'DEFAULT')")
    BigDecimal calculateTotalOutstandingBalance();

    /**
     * Calculate total outstanding balance for borrower
     */
    @Query("SELECT SUM(l.outstandingBalance) FROM Loan l WHERE l.borrowerId = :borrowerId AND l.loanStatus IN ('ACTIVE', 'CURRENT', 'DELINQUENT', 'DEFAULT')")
    BigDecimal calculateBorrowerOutstandingBalance(@Param("borrowerId") UUID borrowerId);

    /**
     * Count loans by status
     */
    long countByLoanStatus(LoanStatus status);

    /**
     * Count active loans for borrower
     */
    @Query("SELECT COUNT(l) FROM Loan l WHERE l.borrowerId = :borrowerId AND l.loanStatus IN ('ACTIVE', 'CURRENT', 'DELINQUENT')")
    long countActiveLoansByBorrower(@Param("borrowerId") UUID borrowerId);

    /**
     * Find loans by risk rating
     */
    List<Loan> findByRiskRatingOrderByCreatedAtDesc(String riskRating);

    /**
     * Get loan portfolio statistics
     */
    @Query("SELECT l.loanStatus, COUNT(l), SUM(l.outstandingBalance) FROM Loan l GROUP BY l.loanStatus")
    List<Object[]> getLoanPortfolioStatistics();

    /**
     * Find loans eligible for refinancing
     */
    @Query("SELECT l FROM Loan l WHERE l.loanStatus IN ('ACTIVE', 'CURRENT') AND l.outstandingBalance > :minBalance AND l.creditScoreAtOrigination IS NOT NULL")
    List<Loan> findLoansEligibleForRefinancing(@Param("minBalance") BigDecimal minBalance);
}
