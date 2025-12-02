/**
 * Loan Application Repository
 * Data access layer for traditional loan applications
 */
package com.waqiti.bnpl.repository;

import com.waqiti.bnpl.entity.LoanApplication;
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
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {
    
    // Find by loan number
    Optional<LoanApplication> findByLoanNumber(String loanNumber);
    
    // Find by user ID
    List<LoanApplication> findByUserIdOrderByApplicationDateDesc(UUID userId);
    
    // Find by status
    List<LoanApplication> findByStatusOrderByApplicationDateDesc(LoanApplication.LoanStatus status);
    
    // Find by loan type and status
    List<LoanApplication> findByLoanTypeAndStatusOrderByApplicationDateDesc(
        LoanApplication.LoanType loanType, 
        LoanApplication.LoanStatus status
    );
    
    // Find active loans for user
    @Query("SELECT l FROM LoanApplication l WHERE l.userId = :userId AND l.status = 'ACTIVE'")
    List<LoanApplication> findActiveLoansByUser(@Param("userId") UUID userId);
    
    // Find overdue loans
    @Query("SELECT l FROM LoanApplication l WHERE l.status = 'OVERDUE' OR l.status = 'ACTIVE' AND EXISTS " +
           "(SELECT i FROM LoanInstallment i WHERE i.loanApplication = l AND i.status = 'OVERDUE')")
    List<LoanApplication> findOverdueLoans();
    
    // Find loans by risk grade
    List<LoanApplication> findByRiskGradeOrderByApplicationDateDesc(String riskGrade);
    
    // Find loans by amount range
    @Query("SELECT l FROM LoanApplication l WHERE l.approvedAmount BETWEEN :minAmount AND :maxAmount")
    List<LoanApplication> findByAmountRange(@Param("minAmount") BigDecimal minAmount, @Param("maxAmount") BigDecimal maxAmount);
    
    // Find loans by date range
    @Query("SELECT l FROM LoanApplication l WHERE l.applicationDate BETWEEN :startDate AND :endDate")
    List<LoanApplication> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    // Count loans by status
    @Query("SELECT COUNT(l) FROM LoanApplication l WHERE l.status = :status")
    Long countByStatus(@Param("status") LoanApplication.LoanStatus status);
    
    // Find loans requiring attention (pending approvals, overdue, etc.)
    @Query("SELECT l FROM LoanApplication l WHERE l.status IN ('PENDING', 'UNDER_REVIEW', 'OVERDUE', 'DEFAULTED')")
    List<LoanApplication> findLoansRequiringAttention();
    
    // Portfolio statistics
    @Query("SELECT SUM(l.approvedAmount) FROM LoanApplication l WHERE l.status = 'ACTIVE'")
    BigDecimal getTotalActivePortfolio();
    
    @Query("SELECT SUM(l.outstandingBalance) FROM LoanApplication l WHERE l.status = 'ACTIVE'")
    BigDecimal getTotalOutstandingBalance();
    
    @Query("SELECT AVG(l.interestRate) FROM LoanApplication l WHERE l.status = 'ACTIVE'")
    BigDecimal getAverageInterestRate();
    
    // Search loans
    // SECURITY FIX: Properly escape wildcards to prevent wildcard injection attacks
    @Query("SELECT l FROM LoanApplication l WHERE " +
           "(:loanNumber IS NULL OR l.loanNumber LIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:loanNumber, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%')) AND " +
           "(:userId IS NULL OR l.userId = :userId) AND " +
           "(:status IS NULL OR l.status = :status) AND " +
           "(:loanType IS NULL OR l.loanType = :loanType)")
    Page<LoanApplication> searchLoans(
        @Param("loanNumber") String loanNumber,
        @Param("userId") UUID userId,
        @Param("status") LoanApplication.LoanStatus status,
        @Param("loanType") LoanApplication.LoanType loanType,
        Pageable pageable
    );
}