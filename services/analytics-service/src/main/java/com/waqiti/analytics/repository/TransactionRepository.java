package com.waqiti.analytics.repository;

import com.waqiti.analytics.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByUserIdAndTransactionDateBetween(
            UUID userId, LocalDateTime startDate, LocalDateTime endDate);

    List<Transaction> findByUserIdAndDateBetween(
            UUID userId, LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT t.category, SUM(t.amount), COUNT(t) FROM Transaction t " +
           "WHERE t.userId = :userId AND t.transactionDate BETWEEN :startDate AND :endDate " +
           "GROUP BY t.category")
    List<Object[]> getCategorySpendingByUser(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT DATE(t.transactionDate), SUM(t.amount), COUNT(t) FROM Transaction t " +
           "WHERE t.userId = :userId AND t.transactionDate BETWEEN :startDate AND :endDate " +
           "GROUP BY DATE(t.transactionDate) ORDER BY DATE(t.transactionDate)")
    List<Object[]> getDailySpending(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    List<Transaction> findByUserIdAndCategoryAndTransactionDateBetween(
            UUID userId, String category, LocalDateTime startDate, LocalDateTime endDate);
}
