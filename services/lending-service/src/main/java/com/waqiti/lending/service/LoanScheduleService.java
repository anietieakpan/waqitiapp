package com.waqiti.lending.service;

import com.waqiti.lending.domain.Loan;
import com.waqiti.lending.domain.LoanSchedule;
import com.waqiti.lending.domain.enums.ScheduleStatus;
import com.waqiti.lending.repository.LoanScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Loan Schedule Service
 * Manages loan amortization schedules
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanScheduleService {

    private final LoanScheduleRepository loanScheduleRepository;

    /**
     * Generate complete amortization schedule for loan
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public List<LoanSchedule> generateSchedule(Loan loan) {
        List<LoanSchedule> schedules = new ArrayList<>();

        BigDecimal balance = loan.getPrincipalAmount();
        BigDecimal monthlyRate = loan.getInterestRate().divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        BigDecimal monthlyPayment = loan.getMonthlyPayment();
        LocalDate paymentDate = loan.getFirstPaymentDate() != null ?
                loan.getFirstPaymentDate() : LocalDate.now().plusDays(30);

        for (int paymentNumber = 1; paymentNumber <= loan.getTermMonths(); paymentNumber++) {
            // Calculate interest for this period
            BigDecimal interestAmount = balance.multiply(monthlyRate)
                    .setScale(2, RoundingMode.HALF_UP);

            // Calculate principal for this period
            BigDecimal principalAmount = monthlyPayment.subtract(interestAmount);

            // Ensure last payment pays off exactly
            if (paymentNumber == loan.getTermMonths()) {
                principalAmount = balance;
                monthlyPayment = balance.add(interestAmount);
            }

            // Reduce balance
            balance = balance.subtract(principalAmount);

            // Create schedule entry
            LoanSchedule schedule = LoanSchedule.builder()
                    .loanId(loan.getLoanId())
                    .paymentNumber(paymentNumber)
                    .dueDate(paymentDate)
                    .scheduledPayment(monthlyPayment)
                    .principalAmount(principalAmount)
                    .interestAmount(interestAmount)
                    .remainingBalance(balance)
                    .status(ScheduleStatus.SCHEDULED)
                    .build();

            schedules.add(schedule);

            // Move to next payment date
            paymentDate = paymentDate.plusMonths(1);
        }

        // Save all schedules
        List<LoanSchedule> saved = loanScheduleRepository.saveAll(schedules);
        log.info("Generated {} payment schedules for loan: {}", saved.size(), loan.getLoanId());

        return saved;
    }

    /**
     * Get schedule for loan
     */
    @Transactional(readOnly = true)
    public List<LoanSchedule> getScheduleForLoan(String loanId) {
        return loanScheduleRepository.findByLoanIdOrderByPaymentNumberAsc(loanId);
    }

    /**
     * Get next due payment
     */
    @Transactional(readOnly = true)
    public LoanSchedule getNextDuePayment(String loanId) {
        return loanScheduleRepository.findNextDuePayment(loanId)
                .orElse(null);
    }

    /**
     * Mark schedule as paid
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public LoanSchedule markAsPaid(String loanId, Integer paymentNumber,
                                  LocalDate paymentDate, BigDecimal amount) {
        LoanSchedule schedule = loanScheduleRepository.findByLoanIdAndPaymentNumber(loanId, paymentNumber)
                .orElseThrow(() -> new RuntimeException("Schedule not found for loan: " + loanId +
                        ", payment number: " + paymentNumber));

        schedule.markAsPaid(paymentDate, amount);

        LoanSchedule saved = loanScheduleRepository.save(schedule);
        log.info("Marked schedule as paid: loan={}, payment={}, amount={}",
                loanId, paymentNumber, amount);

        return saved;
    }

    /**
     * Get overdue schedules
     */
    @Transactional(readOnly = true)
    public List<LoanSchedule> getOverdueSchedules() {
        return loanScheduleRepository.findOverdueSchedules(LocalDate.now());
    }

    /**
     * Mark overdue schedules
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int markOverdueSchedules() {
        List<LoanSchedule> overdue = getOverdueSchedules();

        int count = 0;
        for (LoanSchedule schedule : overdue) {
            schedule.markAsOverdue();
            loanScheduleRepository.save(schedule);
            count++;
        }

        if (count > 0) {
            log.info("Marked {} schedules as overdue", count);
        }

        return count;
    }

    /**
     * Get upcoming payments
     */
    @Transactional(readOnly = true)
    public List<LoanSchedule> getUpcomingPayments(int days) {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = LocalDate.now().plusDays(days);
        return loanScheduleRepository.findUpcomingPayments(startDate, endDate);
    }

    /**
     * Count remaining payments for loan
     */
    @Transactional(readOnly = true)
    public long countRemainingPayments(String loanId) {
        return loanScheduleRepository.countRemainingPayments(loanId);
    }

    /**
     * Count paid payments for loan
     */
    @Transactional(readOnly = true)
    public long countPaidPayments(String loanId) {
        return loanScheduleRepository.countByLoanIdAndStatus(loanId, ScheduleStatus.PAID);
    }
}
