package com.waqiti.billpayment.repository;

import com.waqiti.billpayment.entity.Bill;
import com.waqiti.billpayment.entity.BillStatus;
import com.waqiti.billpayment.entity.BillCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Bill entity operations
 */
@Repository
public interface BillRepository extends JpaRepository<Bill, UUID> {

    Page<Bill> findByUserId(String userId, Pageable pageable);

    Page<Bill> findByUserIdAndStatus(String userId, BillStatus status, Pageable pageable);

    Optional<Bill> findByIdAndUserId(UUID id, String userId);

    List<Bill> findByUserIdAndDueDateBetween(String userId, LocalDate startDate, LocalDate endDate);

    List<Bill> findByUserIdAndCategory(String userId, BillCategory category);

    List<Bill> findByBillerId(UUID billerId);

    @Query("SELECT b FROM Bill b WHERE b.userId = :userId AND b.status = 'UNPAID' " +
           "AND b.dueDate <= :date AND b.deletedAt IS NULL")
    List<Bill> findUpcomingBills(@Param("userId") String userId, @Param("date") LocalDate date);

    @Query("SELECT b FROM Bill b WHERE b.userId = :userId AND b.status = 'UNPAID' " +
           "AND b.dueDate < :currentDate AND b.deletedAt IS NULL")
    List<Bill> findOverdueBills(@Param("userId") String userId, @Param("currentDate") LocalDate currentDate);

    @Query("SELECT b FROM Bill b WHERE b.userId = :userId " +
           "AND (:category IS NULL OR b.category = :category) " +
           "AND (:status IS NULL OR b.status = :status) " +
           "AND (:startDate IS NULL OR b.dueDate >= :startDate) " +
           "AND (:endDate IS NULL OR b.dueDate <= :endDate) " +
           "AND b.deletedAt IS NULL")
    Page<Bill> findByFilters(@Param("userId") String userId,
                            @Param("category") BillCategory category,
                            @Param("status") BillStatus status,
                            @Param("startDate") LocalDate startDate,
                            @Param("endDate") LocalDate endDate,
                            Pageable pageable);

    @Query("SELECT SUM(b.amount) FROM Bill b WHERE b.userId = :userId " +
           "AND b.status = 'UNPAID' AND b.deletedAt IS NULL")
    BigDecimal getTotalPendingAmount(@Param("userId") String userId);

    @Query("SELECT SUM(b.amount) FROM Bill b WHERE b.userId = :userId AND b.status = 'PAID' " +
           "AND b.paidDate BETWEEN :startDate AND :endDate AND b.deletedAt IS NULL")
    BigDecimal getTotalPaidAmount(@Param("userId") String userId,
                                 @Param("startDate") LocalDate startDate,
                                 @Param("endDate") LocalDate endDate);

    @Query("SELECT b.category, SUM(b.amount) FROM Bill b WHERE b.userId = :userId " +
           "AND b.status = 'PAID' AND EXTRACT(MONTH FROM b.paidDate) = :month " +
           "AND EXTRACT(YEAR FROM b.paidDate) = :year AND b.deletedAt IS NULL GROUP BY b.category")
    List<Object[]> getMonthlySpendingByCategory(@Param("userId") String userId,
                                                @Param("month") Integer month,
                                                @Param("year") Integer year);

    @Query("SELECT COUNT(b) FROM Bill b WHERE b.userId = :userId AND b.status = :status AND b.deletedAt IS NULL")
    Long countByUserIdAndStatus(@Param("userId") String userId, @Param("status") BillStatus status);

    @Query("SELECT b FROM Bill b WHERE b.isRecurring = true AND b.dueDate <= :date " +
           "AND b.status IN ('UNPAID', 'PARTIALLY_PAID') AND b.deletedAt IS NULL")
    List<Bill> findRecurringBillsDue(@Param("date") LocalDate date);

    @Query("SELECT b FROM Bill b WHERE b.userId = :userId AND b.reminderSent = false " +
           "AND b.status = 'UNPAID' AND b.dueDate = :reminderDate AND b.deletedAt IS NULL")
    List<Bill> findBillsForReminder(@Param("userId") String userId, @Param("reminderDate") LocalDate reminderDate);

    @Query("SELECT AVG(b.amount) FROM Bill b WHERE b.userId = :userId AND b.category = :category " +
           "AND b.status = 'PAID' AND b.deletedAt IS NULL")
    BigDecimal getAverageAmountByCategory(@Param("userId") String userId, @Param("category") BillCategory category);

    @Query("SELECT b.billerName, COUNT(b) FROM Bill b WHERE b.userId = :userId AND b.deletedAt IS NULL " +
           "GROUP BY b.billerName ORDER BY COUNT(b) DESC")
    List<Object[]> getTopBillers(@Param("userId") String userId);

    @Modifying
    @Query("UPDATE Bill b SET b.status = :status, b.paidDate = :paidDate, b.paidAmount = :paidAmount " +
           "WHERE b.id = :billId")
    void updateBillPayment(@Param("billId") UUID billId,
                          @Param("status") BillStatus status,
                          @Param("paidDate") LocalDate paidDate,
                          @Param("paidAmount") BigDecimal paidAmount);

    @Query("SELECT b FROM Bill b WHERE b.autoPayEnabled = true AND b.status = 'UNPAID' " +
           "AND b.dueDate <= :processDate AND b.deletedAt IS NULL")
    List<Bill> findAutoPayBillsDue(@Param("processDate") LocalDate processDate);

    boolean existsByUserIdAndBillerIdAndAccountNumber(String userId, UUID billerId, String accountNumber);

    Optional<Bill> findByExternalBillId(String externalBillId);

    @Query("SELECT b FROM Bill b WHERE b.status = 'OVERDUE' AND b.overdueAlertSent = false " +
           "AND b.deletedAt IS NULL")
    List<Bill> findOverdueBillsWithoutAlert();

    @Modifying
    @Query("UPDATE Bill b SET b.deletedAt = :now, b.deletedBy = :deletedBy WHERE b.id = :id")
    void softDelete(@Param("id") UUID id, @Param("deletedBy") String deletedBy, @Param("now") LocalDateTime now);

    /**
     * Find bill by biller ID and account number
     */
    Optional<Bill> findByBillerIdAndAccountNumber(UUID billerId, String accountNumber);
}