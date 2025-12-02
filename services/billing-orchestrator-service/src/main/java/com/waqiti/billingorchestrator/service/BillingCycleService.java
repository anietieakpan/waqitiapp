package com.waqiti.billingorchestrator.service;

import com.waqiti.billingorchestrator.dto.request.InitiateBillingCycleRequest;
import com.waqiti.billingorchestrator.entity.BillingCycle;
import com.waqiti.billingorchestrator.entity.BillingEvent;
import com.waqiti.billingorchestrator.exception.BillingCycleNotFoundException;
import com.waqiti.billingorchestrator.repository.BillingCycleRepository;
import lombok.RequiredArgsConstructor;
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
 * Service for managing billing cycles
 *
 * Handles billing cycle lifecycle: creation, closing, payment recording, dunning
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BillingCycleService {

    private final BillingCycleRepository billingCycleRepository;
    private final BillingEventService billingEventService;

    /**
     * Create a new billing cycle
     */
    @Transactional
    public BillingCycle createBillingCycle(InitiateBillingCycleRequest request) {
        log.info("Creating billing cycle for customer: {}, period: {} to {}",
                request.getCustomerId(), request.getCycleStartDate(), request.getCycleEndDate());

        // Calculate due date with grace period
        LocalDate dueDate = request.getDueDate() != null ? request.getDueDate() :
                request.getCycleEndDate().plusDays(request.getPaymentTermsDays() != null ? request.getPaymentTermsDays() : 0);

        LocalDate gracePeriodEndDate = request.getGracePeriodDays() != null ?
                dueDate.plusDays(request.getGracePeriodDays()) : dueDate;

        BillingCycle cycle = BillingCycle.builder()
                .customerId(request.getCustomerId())
                .accountId(request.getAccountId())
                .status(BillingCycle.CycleStatus.OPEN)
                .cycleStartDate(request.getCycleStartDate())
                .cycleEndDate(request.getCycleEndDate())
                .dueDate(dueDate)
                .gracePeriodEndDate(gracePeriodEndDate)
                .currency(request.getCurrency())
                .billingFrequency(request.getBillingFrequency() != null ? request.getBillingFrequency() : BillingCycle.BillingFrequency.MONTHLY)
                .customerType(request.getCustomerType() != null ? request.getCustomerType() : BillingCycle.CustomerType.INDIVIDUAL)
                .paymentTermsDays(request.getPaymentTermsDays())
                .gracePeriodDays(request.getGracePeriodDays())
                .autoPayEnabled(request.getAutoPayEnabled() != null ? request.getAutoPayEnabled() : false)
                .invoiceGenerated(false)
                .invoiceSent(false)
                .totalAmount(BigDecimal.ZERO)
                .subscriptionCharges(BigDecimal.ZERO)
                .usageCharges(BigDecimal.ZERO)
                .oneTimeCharges(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .adjustmentAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .balanceDue(BigDecimal.ZERO)
                .dunningLevel(0)
                .build();

        if (request.getSubscriptionIds() != null) {
            cycle.setSubscriptionIds(request.getSubscriptionIds());
        }

        cycle = billingCycleRepository.save(cycle);

        // Create billing event
        billingEventService.createEvent(
                cycle,
                BillingEvent.EventType.CYCLE_CREATED,
                String.format("Billing cycle created for period %s to %s",
                        request.getCycleStartDate(), request.getCycleEndDate())
        );

        log.info("Billing cycle created successfully: {}", cycle.getId());

        return cycle;
    }

    /**
     * Get billing cycle by ID
     */
    @Transactional(readOnly = true)
    public BillingCycle getBillingCycle(UUID cycleId) {
        return billingCycleRepository.findById(cycleId)
                .orElseThrow(() -> new BillingCycleNotFoundException("Billing cycle not found: " + cycleId));
    }

    /**
     * Get billing cycles by customer ID
     */
    @Transactional(readOnly = true)
    public List<BillingCycle> getBillingCyclesByCustomer(UUID customerId) {
        return billingCycleRepository.findByCustomerId(customerId);
    }

    /**
     * Get billing cycles by customer ID with pagination
     */
    @Transactional(readOnly = true)
    public Page<BillingCycle> getBillingCyclesByCustomer(UUID customerId, Pageable pageable) {
        return billingCycleRepository.findByCustomerId(customerId, pageable);
    }

    /**
     * Get billing cycles by account ID
     */
    @Transactional(readOnly = true)
    public Page<BillingCycle> getBillingCyclesByAccount(UUID accountId, Pageable pageable) {
        return billingCycleRepository.findByAccountId(accountId, pageable);
    }

    /**
     * Get billing cycles by status
     */
    @Transactional(readOnly = true)
    public Page<BillingCycle> getBillingCyclesByStatus(BillingCycle.CycleStatus status, Pageable pageable) {
        return billingCycleRepository.findByStatus(status, pageable);
    }

    /**
     * Get billing cycle by invoice number
     */
    @Transactional(readOnly = true)
    public BillingCycle getBillingCycleByInvoiceNumber(String invoiceNumber) {
        return billingCycleRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new BillingCycleNotFoundException("Billing cycle not found for invoice: " + invoiceNumber));
    }

    /**
     * Add charges to billing cycle
     */
    @Transactional
    public BillingCycle addCharges(UUID cycleId, BigDecimal subscriptionCharges, BigDecimal usageCharges, BigDecimal oneTimeCharges) {
        log.info("Adding charges to billing cycle: {}", cycleId);

        BillingCycle cycle = getBillingCycle(cycleId);

        if (cycle.getStatus() != BillingCycle.CycleStatus.OPEN) {
            throw new IllegalStateException("Cannot add charges to non-OPEN cycle: " + cycleId);
        }

        if (subscriptionCharges != null && subscriptionCharges.compareTo(BigDecimal.ZERO) > 0) {
            cycle.setSubscriptionCharges(cycle.getSubscriptionCharges().add(subscriptionCharges));
        }

        if (usageCharges != null && usageCharges.compareTo(BigDecimal.ZERO) > 0) {
            cycle.setUsageCharges(cycle.getUsageCharges().add(usageCharges));
        }

        if (oneTimeCharges != null && oneTimeCharges.compareTo(BigDecimal.ZERO) > 0) {
            cycle.setOneTimeCharges(cycle.getOneTimeCharges().add(oneTimeCharges));
        }

        cycle.calculateTotalAmount();

        cycle = billingCycleRepository.save(cycle);

        billingEventService.createEvent(
                cycle,
                BillingEvent.EventType.CHARGES_ADDED,
                String.format("Charges added - Subscription: %s, Usage: %s, OneTime: %s",
                        subscriptionCharges, usageCharges, oneTimeCharges)
        );

        log.info("Charges added to billing cycle: {}, new total: {}", cycleId, cycle.getTotalAmount());

        return cycle;
    }

    /**
     * Apply discount to billing cycle
     */
    @Transactional
    public BillingCycle applyDiscount(UUID cycleId, BigDecimal discountAmount, String description) {
        log.info("Applying discount to billing cycle: {}, amount: {}", cycleId, discountAmount);

        BillingCycle cycle = getBillingCycle(cycleId);

        if (cycle.getStatus() == BillingCycle.CycleStatus.PAID ||
                cycle.getStatus() == BillingCycle.CycleStatus.WRITTEN_OFF) {
            throw new IllegalStateException("Cannot apply discount to PAID or WRITTEN_OFF cycle: " + cycleId);
        }

        cycle.setDiscountAmount(cycle.getDiscountAmount().add(discountAmount));
        cycle.calculateTotalAmount();

        cycle = billingCycleRepository.save(cycle);

        billingEventService.createEvent(
                cycle,
                BillingEvent.EventType.DISCOUNT_APPLIED,
                description != null ? description : String.format("Discount applied: %s", discountAmount)
        );

        log.info("Discount applied to billing cycle: {}, new total: {}", cycleId, cycle.getTotalAmount());

        return cycle;
    }

    /**
     * Apply adjustment to billing cycle
     */
    @Transactional
    public BillingCycle applyAdjustment(UUID cycleId, BigDecimal adjustmentAmount, String description) {
        log.info("Applying adjustment to billing cycle: {}, amount: {}", cycleId, adjustmentAmount);

        BillingCycle cycle = getBillingCycle(cycleId);

        cycle.setAdjustmentAmount(cycle.getAdjustmentAmount().add(adjustmentAmount));
        cycle.calculateTotalAmount();

        cycle = billingCycleRepository.save(cycle);

        billingEventService.createEvent(
                cycle,
                BillingEvent.EventType.ADJUSTMENT_APPLIED,
                description != null ? description : String.format("Adjustment applied: %s", adjustmentAmount)
        );

        log.info("Adjustment applied to billing cycle: {}, new total: {}", cycleId, cycle.getTotalAmount());

        return cycle;
    }

    /**
     * Close billing cycle
     */
    @Transactional
    public BillingCycle closeBillingCycle(UUID cycleId) {
        log.info("Closing billing cycle: {}", cycleId);

        BillingCycle cycle = getBillingCycle(cycleId);

        if (cycle.getStatus() != BillingCycle.CycleStatus.OPEN) {
            throw new IllegalStateException("Can only close OPEN cycles: " + cycleId);
        }

        cycle.setStatus(BillingCycle.CycleStatus.CLOSED);
        cycle.setCycleClosedDate(LocalDateTime.now());
        cycle.calculateTotalAmount();

        cycle = billingCycleRepository.save(cycle);

        billingEventService.createEvent(
                cycle,
                BillingEvent.EventType.CYCLE_CLOSED,
                String.format("Billing cycle closed, total amount: %s %s", cycle.getTotalAmount(), cycle.getCurrency())
        );

        log.info("Billing cycle closed successfully: {}, total: {}", cycleId, cycle.getTotalAmount());

        return cycle;
    }

    /**
     * Mark cycle as invoiced
     */
    @Transactional
    public BillingCycle markAsInvoiced(UUID cycleId, UUID invoiceId, String invoiceNumber) {
        log.info("Marking billing cycle as invoiced: {}, invoice: {}", cycleId, invoiceNumber);

        BillingCycle cycle = getBillingCycle(cycleId);

        if (cycle.getStatus() != BillingCycle.CycleStatus.CLOSED) {
            throw new IllegalStateException("Can only invoice CLOSED cycles: " + cycleId);
        }

        cycle.setStatus(BillingCycle.CycleStatus.INVOICED);
        cycle.setInvoiceId(invoiceId);
        cycle.setInvoiceNumber(invoiceNumber);
        cycle.setInvoiceGenerated(true);
        cycle.setInvoiceGeneratedDate(LocalDateTime.now());

        cycle = billingCycleRepository.save(cycle);

        billingEventService.createEvent(
                cycle,
                BillingEvent.EventType.INVOICE_GENERATED,
                String.format("Invoice generated: %s", invoiceNumber)
        );

        log.info("Billing cycle marked as invoiced: {}", cycleId);

        return cycle;
    }

    /**
     * Mark invoice as sent
     */
    @Transactional
    public BillingCycle markInvoiceAsSent(UUID cycleId) {
        log.info("Marking invoice as sent for cycle: {}", cycleId);

        BillingCycle cycle = getBillingCycle(cycleId);

        cycle.setInvoiceSent(true);
        cycle.setInvoiceSentDate(LocalDateTime.now());

        cycle = billingCycleRepository.save(cycle);

        billingEventService.createEvent(
                cycle,
                BillingEvent.EventType.INVOICE_SENT,
                String.format("Invoice sent: %s", cycle.getInvoiceNumber())
        );

        log.info("Invoice marked as sent for cycle: {}", cycleId);

        return cycle;
    }

    /**
     * Record payment
     */
    @Transactional
    public BillingCycle recordPayment(UUID cycleId, UUID paymentId, BigDecimal paymentAmount) {
        log.info("Recording payment for billing cycle: {}, amount: {}", cycleId, paymentAmount);

        BillingCycle cycle = getBillingCycle(cycleId);

        cycle.setPaidAmount(cycle.getPaidAmount().add(paymentAmount));
        cycle.calculateBalanceDue();

        // Update status based on balance
        if (cycle.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0) {
            cycle.setStatus(BillingCycle.CycleStatus.PAID);
            cycle.setPaidDate(LocalDate.now());
        } else if (cycle.getStatus() == BillingCycle.CycleStatus.INVOICED) {
            cycle.setStatus(BillingCycle.CycleStatus.PARTIALLY_PAID);
        }

        // Add payment ID to list
        if (cycle.getPaymentIds() == null) {
            cycle.setPaymentIds(List.of(paymentId));
        } else {
            List<UUID> paymentIds = new java.util.ArrayList<>(cycle.getPaymentIds());
            paymentIds.add(paymentId);
            cycle.setPaymentIds(paymentIds);
        }

        cycle = billingCycleRepository.save(cycle);

        BillingEvent.EventType eventType = cycle.getStatus() == BillingCycle.CycleStatus.PAID ?
                BillingEvent.EventType.PAYMENT_SUCCEEDED : BillingEvent.EventType.PARTIAL_PAYMENT_RECEIVED;

        billingEventService.createPaymentEvent(
                cycle,
                eventType,
                paymentAmount.toString(),
                cycle.getCurrency(),
                String.format("Payment recorded: %s %s, balance due: %s",
                        paymentAmount, cycle.getCurrency(), cycle.getBalanceDue())
        );

        log.info("Payment recorded for cycle: {}, paid: {}, balance: {}",
                cycleId, cycle.getPaidAmount(), cycle.getBalanceDue());

        return cycle;
    }

    /**
     * Mark cycle as overdue
     */
    @Transactional
    public BillingCycle markAsOverdue(UUID cycleId) {
        log.info("Marking billing cycle as overdue: {}", cycleId);

        BillingCycle cycle = getBillingCycle(cycleId);

        if (cycle.getStatus() == BillingCycle.CycleStatus.INVOICED ||
                cycle.getStatus() == BillingCycle.CycleStatus.PARTIALLY_PAID) {
            cycle.setStatus(BillingCycle.CycleStatus.OVERDUE);

            cycle = billingCycleRepository.save(cycle);

            billingEventService.createEvent(
                    cycle,
                    BillingEvent.EventType.PAYMENT_OVERDUE,
                    String.format("Payment overdue, due date: %s, balance: %s %s",
                            cycle.getDueDate(), cycle.getBalanceDue(), cycle.getCurrency())
            );

            log.info("Billing cycle marked as overdue: {}", cycleId);
        }

        return cycle;
    }

    /**
     * Start dunning process
     */
    @Transactional
    public BillingCycle startDunning(UUID cycleId) {
        log.info("Starting dunning process for cycle: {}", cycleId);

        BillingCycle cycle = getBillingCycle(cycleId);

        if (cycle.getStatus() != BillingCycle.CycleStatus.OVERDUE) {
            throw new IllegalStateException("Can only start dunning for OVERDUE cycles: " + cycleId);
        }

        cycle.setStatus(BillingCycle.CycleStatus.DUNNING);
        cycle.setDunningLevel(1);
        cycle.setLastDunningDate(LocalDateTime.now());

        cycle = billingCycleRepository.save(cycle);

        billingEventService.createEvent(
                cycle,
                BillingEvent.EventType.DUNNING_STARTED,
                "Dunning process started - Level 1"
        );

        log.info("Dunning process started for cycle: {}", cycleId);

        return cycle;
    }

    /**
     * Escalate dunning level
     */
    @Transactional
    public BillingCycle escalateDunning(UUID cycleId) {
        log.info("Escalating dunning for cycle: {}", cycleId);

        BillingCycle cycle = getBillingCycle(cycleId);

        if (cycle.getStatus() != BillingCycle.CycleStatus.DUNNING) {
            throw new IllegalStateException("Cycle is not in dunning: " + cycleId);
        }

        int newLevel = cycle.getDunningLevel() + 1;
        cycle.setDunningLevel(newLevel);
        cycle.setLastDunningDate(LocalDateTime.now());

        cycle = billingCycleRepository.save(cycle);

        billingEventService.createEvent(
                cycle,
                BillingEvent.EventType.DUNNING_ESCALATED,
                String.format("Dunning escalated to level %d", newLevel)
        );

        log.info("Dunning escalated for cycle: {}, new level: {}", cycleId, newLevel);

        return cycle;
    }

    /**
     * Write off cycle
     */
    @Transactional
    public BillingCycle writeOff(UUID cycleId, String reason) {
        log.info("Writing off billing cycle: {}, reason: {}", cycleId, reason);

        BillingCycle cycle = getBillingCycle(cycleId);

        cycle.setStatus(BillingCycle.CycleStatus.WRITTEN_OFF);
        cycle.setWriteOffReason(reason);
        cycle.setWriteOffDate(LocalDate.now());

        cycle = billingCycleRepository.save(cycle);

        billingEventService.createEvent(
                cycle,
                BillingEvent.EventType.WRITE_OFF,
                reason != null ? reason : "Billing cycle written off"
        );

        log.info("Billing cycle written off: {}", cycleId);

        return cycle;
    }

    /**
     * Get cycles due for processing (ready to close)
     */
    @Transactional(readOnly = true)
    public List<BillingCycle> getCyclesDueForProcessing(LocalDate currentDate) {
        return billingCycleRepository.findCyclesDueForProcessing(currentDate);
    }

    /**
     * Get cycles needing invoice generation
     */
    @Transactional(readOnly = true)
    public List<BillingCycle> getCyclesNeedingInvoiceGeneration() {
        return billingCycleRepository.findCyclesNeedingInvoiceGeneration();
    }

    /**
     * Get overdue cycles
     */
    @Transactional(readOnly = true)
    public List<BillingCycle> getOverdueCycles(LocalDate currentDate) {
        return billingCycleRepository.findOverdueCycles(currentDate);
    }

    /**
     * Get cycles with auto-pay enabled that are due
     */
    @Transactional(readOnly = true)
    public List<BillingCycle> getAutoPayCyclesDue(LocalDate currentDate) {
        return billingCycleRepository.findAutoPayCyclesDue(currentDate);
    }

    /**
     * Calculate total revenue for date range
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalRevenue(LocalDate startDate, LocalDate endDate) {
        return billingCycleRepository.calculateTotalRevenue(startDate, endDate)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Calculate outstanding balance
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateOutstandingBalance() {
        return billingCycleRepository.calculateOutstandingBalance()
                .orElse(BigDecimal.ZERO);
    }
}
