package com.waqiti.expense.repository;

import com.waqiti.expense.domain.Expense;
import com.waqiti.expense.domain.enums.ExpenseStatus;
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
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
    // Repository already uses UUID correctly
    
    Page<Expense> findByUserIdOrderByExpenseDateDesc(UUID userId, Pageable pageable);
    
    Page<Expense> findByUserIdAndStatusOrderByExpenseDateDesc(
        UUID userId, ExpenseStatus status, Pageable pageable);
    
    List<Expense> findByUserIdAndExpenseDateBetween(
        UUID userId, LocalDateTime start, LocalDateTime end);
    
    Page<Expense> findByCategoryAndExpenseDateBetween(
        String category, LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    @Query("SELECT e FROM Expense e WHERE e.userId = :userId " +
           "AND e.expenseDate BETWEEN :start AND :end " +
           "AND (:category IS NULL OR e.category = :category) " +
           "AND (:status IS NULL OR e.status = :status) " +
           "ORDER BY e.expenseDate DESC")
    Page<Expense> findExpensesWithFilters(
        @Param("userId") UUID userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,
        @Param("category") String category,
        @Param("status") ExpenseStatus status,
        Pageable pageable);
    
    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.userId = :userId " +
           "AND e.expenseDate BETWEEN :start AND :end " +
           "AND e.status IN ('APPROVED', 'PAID')")
    Optional<BigDecimal> getTotalExpensesForPeriod(
        @Param("userId") UUID userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);
    
    @Query("SELECT e.category, SUM(e.amount) FROM Expense e " +
           "WHERE e.userId = :userId AND e.expenseDate BETWEEN :start AND :end " +
           "AND e.status IN ('APPROVED', 'PAID') " +
           "GROUP BY e.category ORDER BY SUM(e.amount) DESC")
    List<Object[]> getExpensesByCategory(
        @Param("userId") UUID userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);
    
    @Query("SELECT COUNT(e) FROM Expense e WHERE e.userId = :userId " +
           "AND e.status = :status AND e.expenseDate >= :since")
    Long countByUserIdAndStatusSince(
        @Param("userId") UUID userId,
        @Param("status") ExpenseStatus status,
        @Param("since") LocalDateTime since);
    
    @Query("SELECT AVG(e.amount) FROM Expense e WHERE e.userId = :userId " +
           "AND e.category = :category AND e.expenseDate >= :since " +
           "AND e.status IN ('APPROVED', 'PAID')")
    Optional<BigDecimal> getAverageExpenseByCategory(
        @Param("userId") UUID userId,
        @Param("category") String category,
        @Param("since") LocalDateTime since);
    
    List<Expense> findByStatusAndExpenseDateBefore(ExpenseStatus status, LocalDateTime date);
    
    @Query("SELECT e FROM Expense e WHERE e.status = 'PENDING' " +
           "AND e.submittedAt < :timeout")
    List<Expense> findPendingExpensesOlderThan(@Param("timeout") LocalDateTime timeout);
    
    @Query("SELECT e FROM Expense e WHERE e.approvalRequired = true " +
           "AND e.status = 'SUBMITTED' AND e.assignedApproverId = :approverId")
    List<Expense> findExpensesPendingApprovalBy(@Param("approverId") UUID approverId);
    
    @Query("SELECT EXTRACT(MONTH FROM e.expenseDate), SUM(e.amount) " +
           "FROM Expense e WHERE e.userId = :userId " +
           "AND e.expenseDate >= :since AND e.status IN ('APPROVED', 'PAID') " +
           "GROUP BY EXTRACT(MONTH FROM e.expenseDate) " +
           "ORDER BY EXTRACT(MONTH FROM e.expenseDate)")
    List<Object[]> getMonthlyExpenseTrends(
        @Param("userId") UUID userId,
        @Param("since") LocalDateTime since);
    
    @Query("SELECT e FROM Expense e WHERE e.amount > :threshold " +
           "AND e.expenseDate >= :since ORDER BY e.amount DESC")
    List<Expense> findHighValueExpenses(
        @Param("threshold") BigDecimal threshold,
        @Param("since") LocalDateTime since);
    
    boolean existsByUserIdAndReceiptNumberAndExpenseDate(
        UUID userId, String receiptNumber, LocalDateTime expenseDate);
    
    @Query("SELECT COUNT(e) FROM Expense e WHERE e.merchantName = :merchantName " +
           "AND e.expenseDate >= :since")
    Long countExpensesByMerchant(
        @Param("merchantName") String merchantName,
        @Param("since") LocalDateTime since);
    
    @Query("SELECT e.currency, SUM(e.amount) FROM Expense e " +
           "WHERE e.userId = :userId AND e.expenseDate BETWEEN :start AND :end " +
           "AND e.status IN ('APPROVED', 'PAID') " +
           "GROUP BY e.currency")
    List<Object[]> getTotalExpensesByCurrency(
        @Param("userId") UUID userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);
}