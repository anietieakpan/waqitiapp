/**
 * Loan Installment Repository
 * Data access layer for loan installments
 */
package com.waqiti.bnpl.repository;

import com.waqiti.bnpl.entity.LoanApplication;
import com.waqiti.bnpl.entity.LoanInstallment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LoanInstallmentRepository extends JpaRepository<LoanInstallment, UUID> {
    
    // Find installments by loan application
    List<LoanInstallment> findByLoanApplicationOrderByInstallmentNumber(LoanApplication loanApplication);
    
    // Find installments by loan and status
    List<LoanInstallment> findByLoanApplicationAndStatusInOrderByDueDate(
        LoanApplication loanApplication, 
        List<LoanInstallment.InstallmentStatus> statuses
    );
    
    // Find overdue installments
    @Query("SELECT i FROM LoanInstallment i WHERE i.status = 'OVERDUE' OR (i.dueDate < CURRENT_DATE AND i.status IN ('PENDING', 'DUE', 'PARTIALLY_PAID'))")
    List<LoanInstallment> findOverdueInstallments();
    
    // Find installments due today
    @Query("SELECT i FROM LoanInstallment i WHERE i.dueDate = CURRENT_DATE AND i.status IN ('PENDING', 'DUE')")
    List<LoanInstallment> findInstallmentsDueToday();
    
    // Find installments due in next N days
    @Query("SELECT i FROM LoanInstallment i WHERE i.dueDate BETWEEN CURRENT_DATE AND :endDate AND i.status IN ('PENDING', 'DUE')")
    List<LoanInstallment> findInstallmentsDueInNextDays(@Param("endDate") LocalDate endDate);
    
    // Find unpaid installments for a loan
    @Query("SELECT i FROM LoanInstallment i WHERE i.loanApplication = :loan AND i.status != 'PAID'")
    List<LoanInstallment> findUnpaidInstallments(@Param("loan") LoanApplication loan);
    
    // Get next due installment for a loan
    @Query("SELECT i FROM LoanInstallment i WHERE i.loanApplication = :loan AND i.status IN ('PENDING', 'DUE', 'OVERDUE', 'PARTIALLY_PAID') ORDER BY i.dueDate ASC")
    List<LoanInstallment> findNextDueInstallments(@Param("loan") LoanApplication loan);
    
    // Count overdue installments by loan
    @Query("SELECT COUNT(i) FROM LoanInstallment i WHERE i.loanApplication = :loan AND i.status = 'OVERDUE'")
    Long countOverdueInstallmentsByLoan(@Param("loan") LoanApplication loan);
    
    // Sum outstanding amount by loan
    @Query("SELECT SUM(i.outstandingAmount) FROM LoanInstallment i WHERE i.loanApplication = :loan AND i.status != 'PAID'")
    BigDecimal sumOutstandingAmountByLoan(@Param("loan") LoanApplication loan);
    
    // Find installments by payment date range
    @Query("SELECT i FROM LoanInstallment i WHERE i.paymentDate BETWEEN :startDate AND :endDate")
    List<LoanInstallment> findByPaymentDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    // Portfolio summary statistics
    @Query("SELECT SUM(i.totalAmount) FROM LoanInstallment i WHERE i.dueDate <= CURRENT_DATE AND i.status != 'PAID'")
    BigDecimal getTotalOverdueAmount();
    
    @Query("SELECT SUM(i.paidAmount) FROM LoanInstallment i WHERE i.paymentDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalCollectionsInPeriod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}