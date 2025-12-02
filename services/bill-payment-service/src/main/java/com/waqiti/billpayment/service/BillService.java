package com.waqiti.billpayment.service;

import com.waqiti.billpayment.entity.*;
import com.waqiti.billpayment.repository.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Logger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing bills
 * Handles CRUD operations, queries, and business logic for bills
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;
    private final BillPaymentAuditLogRepository auditLogRepository;
    private final MeterRegistry meterRegistry;

    // Metrics
    private Counter billCreatedCounter;
    private Counter billUpdatedCounter;
    private Counter billDeletedCounter;

    /**
     * Initialize metrics
     */
    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        billCreatedCounter = Counter.builder("bill.created")
                .description("Number of bills created")
                .register(meterRegistry);

        billUpdatedCounter = Counter.builder("bill.updated")
                .description("Number of bills updated")
                .register(meterRegistry);

        billDeletedCounter = Counter.builder("bill.deleted")
                .description("Number of bills deleted")
                .register(meterRegistry);
    }

    /**
     * Create a new bill
     */
    @Transactional
    public Bill createBill(Bill bill, String userId) {
        log.info("Creating bill for user: {}, biller: {}, amount: {}",
                userId, bill.getBillerName(), bill.getAmount());

        bill.setUserId(userId);
        Bill savedBill = billRepository.save(bill);

        // Audit log
        auditLog(savedBill, "CREATE", userId, null, savedBill);

        billCreatedCounter.increment();

        log.info("Bill created successfully: {}", savedBill.getId());
        return savedBill;
    }

    /**
     * Get bill by ID
     */
    @Transactional(readOnly = true)
    public Bill getBillById(UUID billId, String userId) {
        return billRepository.findByIdAndUserId(billId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + billId));
    }

    /**
     * Get all bills for user
     */
    @Transactional(readOnly = true)
    public Page<Bill> getBillsByUser(String userId, Pageable pageable) {
        return billRepository.findByUserId(userId, pageable);
    }

    /**
     * Get bills by status
     */
    @Transactional(readOnly = true)
    public Page<Bill> getBillsByStatus(String userId, BillStatus status, Pageable pageable) {
        return billRepository.findByUserIdAndStatus(userId, status, pageable);
    }

    /**
     * Get bills by category
     */
    @Transactional(readOnly = true)
    public List<Bill> getBillsByCategory(String userId, BillCategory category) {
        return billRepository.findByUserIdAndCategory(userId, category);
    }

    /**
     * Get upcoming bills (due within specified days)
     */
    @Transactional(readOnly = true)
    public List<Bill> getUpcomingBills(String userId, int daysAhead) {
        LocalDate endDate = LocalDate.now().plusDays(daysAhead);
        return billRepository.findUpcomingBills(userId, endDate);
    }

    /**
     * Get overdue bills
     */
    @Transactional(readOnly = true)
    public List<Bill> getOverdueBills(String userId) {
        return billRepository.findOverdueBills(userId, LocalDate.now());
    }

    /**
     * Search bills with filters
     */
    @Transactional(readOnly = true)
    public Page<Bill> searchBills(String userId, BillCategory category, BillStatus status,
                                   LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return billRepository.findByFilters(userId, category, status, startDate, endDate, pageable);
    }

    /**
     * Update bill
     */
    @Transactional
    public Bill updateBill(UUID billId, String userId, Bill updatedBill) {
        log.info("Updating bill: {} for user: {}", billId, userId);

        Bill existingBill = getBillById(billId, userId);
        Bill previousState = cloneBill(existingBill);

        // Update fields
        existingBill.setAmount(updatedBill.getAmount());
        existingBill.setDueDate(updatedBill.getDueDate());
        existingBill.setCategory(updatedBill.getCategory());
        existingBill.setDescription(updatedBill.getDescription());
        existingBill.setMinimumAmountDue(updatedBill.getMinimumAmountDue());

        Bill savedBill = billRepository.save(existingBill);

        // Audit log
        auditLog(savedBill, "UPDATE", userId, previousState, savedBill);

        billUpdatedCounter.increment();

        log.info("Bill updated successfully: {}", billId);
        return savedBill;
    }

    /**
     * Mark bill as paid
     */
    @Transactional
    public Bill markAsPaid(UUID billId, String userId, BigDecimal paymentAmount, UUID paymentId) {
        log.info("Marking bill {} as paid, amount: {}", billId, paymentAmount);

        Bill bill = getBillById(billId, userId);
        Bill previousState = cloneBill(bill);

        bill.markAsPaid(paymentAmount, paymentId);
        Bill savedBill = billRepository.save(bill);

        // Audit log
        auditLog(savedBill, "MARK_PAID", userId, previousState, savedBill);

        log.info("Bill marked as paid: {}, status: {}", billId, savedBill.getStatus());
        return savedBill;
    }

    /**
     * Soft delete bill
     */
    @Transactional
    public void deleteBill(UUID billId, String userId) {
        log.info("Soft deleting bill: {} for user: {}", billId, userId);

        Bill bill = getBillById(billId, userId);
        Bill previousState = cloneBill(bill);

        bill.softDelete(userId);
        billRepository.save(bill);

        // Audit log
        auditLog(bill, "DELETE", userId, previousState, bill);

        billDeletedCounter.increment();

        log.info("Bill soft deleted successfully: {}", billId);
    }

    /**
     * Get total pending amount for user
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalPendingAmount(String userId) {
        BigDecimal total = billRepository.getTotalPendingAmount(userId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Get total paid amount for date range
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalPaidAmount(String userId, LocalDate startDate, LocalDate endDate) {
        BigDecimal total = billRepository.getTotalPaidAmount(userId, startDate, endDate);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Get monthly spending by category
     */
    @Transactional(readOnly = true)
    public List<Object[]> getMonthlySpendingByCategory(String userId, int month, int year) {
        return billRepository.getMonthlySpendingByCategory(userId, month, year);
    }

    /**
     * Count bills by status
     */
    @Transactional(readOnly = true)
    public long countBillsByStatus(String userId, BillStatus status) {
        Long count = billRepository.countByUserIdAndStatus(userId, status);
        return count != null ? count : 0L;
    }

    /**
     * Get average bill amount by category
     */
    @Transactional(readOnly = true)
    public BigDecimal getAverageAmountByCategory(String userId, BillCategory category) {
        BigDecimal avg = billRepository.getAverageAmountByCategory(userId, category);
        return avg != null ? avg : BigDecimal.ZERO;
    }

    /**
     * Get top billers for user
     */
    @Transactional(readOnly = true)
    public List<Object[]> getTopBillers(String userId) {
        return billRepository.getTopBillers(userId);
    }

    /**
     * Process overdue bills (called by scheduled job)
     */
    @Transactional
    public void processOverdueBills() {
        log.info("Processing overdue bills");

        List<Bill> overdueBills = billRepository.findOverdueBillsWithoutAlert();

        for (Bill bill : overdueBills) {
            try {
                bill.setStatus(BillStatus.OVERDUE);
                bill.setOverdueAlertSent(true);
                billRepository.save(bill);

                log.info("Marked bill as overdue: {}", bill.getId());
            } catch (Exception e) {
                log.error("Error processing overdue bill: {}", bill.getId(), e);
            }
        }

        log.info("Processed {} overdue bills", overdueBills.size());
    }

    // Helper methods

    private void auditLog(Bill bill, String action, String userId, Bill previousState, Bill newState) {
        try {
            BillPaymentAuditLog auditLog = BillPaymentAuditLog.builder()
                    .entityType("BILL")
                    .entityId(bill.getId())
                    .action(action)
                    .userId(userId)
                    .timestamp(LocalDateTime.now())
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to create audit log for bill: {}", bill.getId(), e);
            // Don't fail the main operation if audit fails
        }
    }

    private Bill cloneBill(Bill bill) {
        // Create a shallow copy for audit purposes
        return Bill.builder()
                .id(bill.getId())
                .amount(bill.getAmount())
                .status(bill.getStatus())
                .dueDate(bill.getDueDate())
                .paidAmount(bill.getPaidAmount())
                .build();
    }
}
