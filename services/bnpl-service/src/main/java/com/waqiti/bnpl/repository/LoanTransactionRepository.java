/**
 * Loan Transaction Repository
 * Data access layer for loan transactions
 */
package com.waqiti.bnpl.repository;

import com.waqiti.bnpl.entity.LoanApplication;
import com.waqiti.bnpl.entity.LoanTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanTransactionRepository extends JpaRepository<LoanTransaction, UUID> {
    
    // Find by transaction reference
    Optional<LoanTransaction> findByTransactionReference(String transactionReference);
    
    // Find transactions by loan application
    List<LoanTransaction> findByLoanApplicationOrderByTransactionDateDesc(LoanApplication loanApplication);
    
    // Find transactions by loan and type
    List<LoanTransaction> findByLoanApplicationAndTransactionTypeOrderByTransactionDateDesc(
        LoanApplication loanApplication, 
        LoanTransaction.TransactionType transactionType
    );
    
    // Find transactions by status
    List<LoanTransaction> findByStatusOrderByTransactionDateDesc(LoanTransaction.TransactionStatus status);
    
    // Find transactions by date range
    @Query("SELECT t FROM LoanTransaction t WHERE t.transactionDate BETWEEN :startDate AND :endDate ORDER BY t.transactionDate DESC")
    List<LoanTransaction> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    // Find repayment transactions
    @Query("SELECT t FROM LoanTransaction t WHERE t.transactionType IN ('REPAYMENT', 'PREPAYMENT', 'PARTIAL_PREPAYMENT', 'FULL_PREPAYMENT') ORDER BY t.transactionDate DESC")
    List<LoanTransaction> findRepaymentTransactions();
    
    // Find disbursement transactions
    @Query("SELECT t FROM LoanTransaction t WHERE t.transactionType = 'DISBURSEMENT' ORDER BY t.transactionDate DESC")
    List<LoanTransaction> findDisbursementTransactions();
    
    // Find pending transactions
    @Query("SELECT t FROM LoanTransaction t WHERE t.status = 'PENDING' ORDER BY t.transactionDate ASC")
    List<LoanTransaction> findPendingTransactions();
    
    // Find failed transactions
    @Query("SELECT t FROM LoanTransaction t WHERE t.status = 'FAILED' ORDER BY t.transactionDate DESC")
    List<LoanTransaction> findFailedTransactions();
    
    // Find transactions by external reference
    List<LoanTransaction> findByExternalReference(String externalReference);
    
    // Find transactions by payment method
    List<LoanTransaction> findByPaymentMethodOrderByTransactionDateDesc(String paymentMethod);
    
    // Sum transactions by type and date range
    @Query("SELECT SUM(t.amount) FROM LoanTransaction t WHERE t.transactionType = :type AND t.transactionDate BETWEEN :startDate AND :endDate AND t.status = 'COMPLETED'")
    BigDecimal sumAmountByTypeAndDateRange(
        @Param("type") LoanTransaction.TransactionType type,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    // Count transactions by status
    @Query("SELECT COUNT(t) FROM LoanTransaction t WHERE t.status = :status")
    Long countByStatus(@Param("status") LoanTransaction.TransactionStatus status);
    
    // Get transaction statistics for a loan
    @Query("SELECT NEW map(" +
           "SUM(CASE WHEN t.transactionType = 'DISBURSEMENT' THEN t.amount ELSE 0 END) as totalDisbursed, " +
           "SUM(CASE WHEN t.transactionType IN ('REPAYMENT', 'PREPAYMENT') THEN t.amount ELSE 0 END) as totalRepaid, " +
           "SUM(CASE WHEN t.transactionType IN ('REPAYMENT', 'PREPAYMENT') THEN t.principalAmount ELSE 0 END) as totalPrincipalPaid, " +
           "SUM(CASE WHEN t.transactionType IN ('REPAYMENT', 'PREPAYMENT') THEN t.interestAmount ELSE 0 END) as totalInterestPaid " +
           ") FROM LoanTransaction t WHERE t.loanApplication = :loan AND t.status = 'COMPLETED'")
    Object getLoanTransactionSummary(@Param("loan") LoanApplication loan);
    
    // Daily transaction summary
    @Query("SELECT DATE(t.transactionDate) as date, t.transactionType as type, SUM(t.amount) as amount, COUNT(t) as count " +
           "FROM LoanTransaction t WHERE t.transactionDate BETWEEN :startDate AND :endDate AND t.status = 'COMPLETED' " +
           "GROUP BY DATE(t.transactionDate), t.transactionType ORDER BY date DESC")
    List<Object[]> getDailyTransactionSummary(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    // Find reversible transactions
    @Query("SELECT t FROM LoanTransaction t WHERE t.status = 'COMPLETED' AND t.isReversed = false AND " +
           "t.transactionType IN ('REPAYMENT', 'PREPAYMENT', 'DISBURSEMENT') ORDER BY t.transactionDate DESC")
    List<LoanTransaction> findReversibleTransactions();
    
    // Search transactions
    // SECURITY FIX: Properly escape wildcards to prevent wildcard injection attacks
    @Query("SELECT t FROM LoanTransaction t WHERE " +
           "(:reference IS NULL OR t.transactionReference LIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:reference, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%') OR t.externalReference LIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:reference, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%')) AND " +
           "(:loanId IS NULL OR t.loanApplication.id = :loanId) AND " +
           "(:type IS NULL OR t.transactionType = :type) AND " +
           "(:status IS NULL OR t.status = :status)")
    Page<LoanTransaction> searchTransactions(
        @Param("reference") String reference,
        @Param("loanId") UUID loanId,
        @Param("type") LoanTransaction.TransactionType type,
        @Param("status") LoanTransaction.TransactionStatus status,
        Pageable pageable
    );
}