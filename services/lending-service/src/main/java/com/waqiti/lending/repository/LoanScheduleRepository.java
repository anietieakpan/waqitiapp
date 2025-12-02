package com.waqiti.lending.repository;

import com.waqiti.lending.domain.LoanSchedule;
import com.waqiti.lending.domain.enums.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Loan Schedule entities
 */
@Repository
public interface LoanScheduleRepository extends JpaRepository<LoanSchedule, UUID> {

    /**
     * Find all schedules for a loan
     */
    List<LoanSchedule> findByLoanIdOrderByPaymentNumberAsc(String loanId);

    /**
     * Find schedule by loan and payment number
     */
    Optional<LoanSchedule> findByLoanIdAndPaymentNumber(String loanId, Integer paymentNumber);

    /**
     * Find schedules by status
     */
    List<LoanSchedule> findByStatus(ScheduleStatus status);

    /**
     * Find upcoming payments
     */
    @Query("SELECT ls FROM LoanSchedule ls WHERE ls.dueDate BETWEEN :startDate AND :endDate AND ls.status = 'SCHEDULED'")
    List<LoanSchedule> findUpcomingPayments(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Find overdue schedules
     */
    @Query("SELECT ls FROM LoanSchedule ls WHERE ls.dueDate < :today AND ls.status IN ('SCHEDULED', 'DUE')")
    List<LoanSchedule> findOverdueSchedules(@Param("today") LocalDate today);

    /**
     * Find next due payment for loan
     */
    @Query("SELECT ls FROM LoanSchedule ls WHERE ls.loanId = :loanId AND ls.status IN ('SCHEDULED', 'DUE') ORDER BY ls.dueDate ASC")
    Optional<LoanSchedule> findNextDuePayment(@Param("loanId") String loanId);

    /**
     * Count remaining payments for loan
     */
    @Query("SELECT COUNT(ls) FROM LoanSchedule ls WHERE ls.loanId = :loanId AND ls.status IN ('SCHEDULED', 'DUE')")
    long countRemainingPayments(@Param("loanId") String loanId);

    /**
     * Count paid payments for loan
     */
    long countByLoanIdAndStatus(String loanId, ScheduleStatus status);
}
