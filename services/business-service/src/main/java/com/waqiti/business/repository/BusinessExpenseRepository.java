package com.waqiti.business.repository;

import com.waqiti.business.domain.BusinessExpense;
import com.waqiti.business.domain.ExpenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessExpenseRepository extends JpaRepository<BusinessExpense, UUID> {
    
    Page<BusinessExpense> findByAccountId(UUID accountId, Pageable pageable);
    
    Page<BusinessExpense> findByAccountIdAndStatus(UUID accountId, ExpenseStatus status, Pageable pageable);
    
    Optional<BusinessExpense> findByIdAndAccountId(UUID id, UUID accountId);
    
    List<BusinessExpense> findBySubmittedBy(UUID submittedBy);
    
    Page<BusinessExpense> findBySubmittedBy(UUID submittedBy, Pageable pageable);
    
    List<BusinessExpense> findByEmployeeId(UUID employeeId);
    
    @Query("SELECT e FROM BusinessExpense e WHERE e.accountId = :accountId AND " +
           "(:category IS NULL OR e.category = :category) AND " +
           "(:status IS NULL OR e.status = :status) AND " +
           "(:employeeId IS NULL OR e.employeeId = :employeeId) AND " +
           "(:startDate IS NULL OR e.expenseDate >= :startDate) AND " +
           "(:endDate IS NULL OR e.expenseDate <= :endDate)")
    Page<BusinessExpense> findByFilters(@Param("accountId") UUID accountId,
                                       @Param("category") String category,
                                       @Param("status") String status,
                                       @Param("employeeId") UUID employeeId,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate,
                                       Pageable pageable);
    
    @Query("SELECT e FROM BusinessExpense e WHERE e.accountId = :accountId AND " +
           "e.expenseDate BETWEEN :startDate AND :endDate")
    List<BusinessExpense> findByAccountIdAndExpenseDateBetween(@Param("accountId") UUID accountId,
                                                              @Param("startDate") LocalDateTime startDate,
                                                              @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT SUM(e.amount) FROM BusinessExpense e WHERE e.accountId = :accountId AND " +
           "e.status = 'APPROVED' AND e.expenseDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalApprovedExpensesByDateRange(@Param("accountId") UUID accountId,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);
    
    @Query("SELECT e.category, SUM(e.amount) FROM BusinessExpense e WHERE e.accountId = :accountId AND " +
           "e.status = 'APPROVED' AND e.expenseDate BETWEEN :startDate AND :endDate GROUP BY e.category")
    List<Object[]> getExpenseAmountByCategory(@Param("accountId") UUID accountId,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);
    
    @Query("SELECT COUNT(e) FROM BusinessExpense e WHERE e.accountId = :accountId AND e.status = :status")
    Long countByAccountIdAndStatus(@Param("accountId") UUID accountId, @Param("status") ExpenseStatus status);
    
    @Query("SELECT e FROM BusinessExpense e WHERE e.status = 'PENDING' AND " +
           "e.submittedAt < :cutoffDate")
    List<BusinessExpense> findPendingExpensesOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    @Query("SELECT AVG(e.amount) FROM BusinessExpense e WHERE e.accountId = :accountId AND " +
           "e.status = 'APPROVED'")
    BigDecimal getAverageExpenseAmount(@Param("accountId") UUID accountId);
    
    @Query("SELECT e.submittedBy, COUNT(e) FROM BusinessExpense e WHERE e.accountId = :accountId AND " +
           "e.expenseDate BETWEEN :startDate AND :endDate GROUP BY e.submittedBy")
    List<Object[]> getExpenseCountByEmployee(@Param("accountId") UUID accountId,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);
    
    @Query("SELECT e FROM BusinessExpense e WHERE e.amount > :threshold AND e.status = 'PENDING'")
    List<BusinessExpense> findHighValuePendingExpenses(@Param("threshold") BigDecimal threshold);
}