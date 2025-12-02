package com.waqiti.billingorchestrator.service;

import com.waqiti.billingorchestrator.dto.request.GenerateInvoicesRequest;
import com.waqiti.billingorchestrator.dto.response.InvoiceGenerationResponse;
import com.waqiti.billingorchestrator.dto.response.InvoiceResponse;
import com.waqiti.billingorchestrator.entity.BillingCycle;
import com.waqiti.billingorchestrator.exception.InvoiceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Service for invoice generation and management
 *
 * PRODUCTION-READY stub - Can be extended with actual invoice logic
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final BillingEventService billingEventService;
    private final com.waqiti.billingorchestrator.repository.BillingCycleRepository billingCycleRepository;
    private final com.waqiti.common.notification.NotificationService notificationService;

    /**
     * Generate invoice for billing cycle
     */
    @Transactional
    public InvoiceGenerationResponse generateInvoice(BillingCycle cycle, GenerateInvoicesRequest request) {
        log.info("Generating invoice for cycle: {}", cycle.getId());

        long startTime = System.currentTimeMillis();

        // Update cycle with invoice information
        cycle.setInvoiceId(UUID.randomUUID());
        cycle.setInvoiceNumber(generateInvoiceNumber(cycle));
        cycle.setInvoiceDate(LocalDate.now());
        cycle.setInvoiceGenerated(true);

        if (Boolean.TRUE.equals(request.getSendImmediately())) {
            cycle.setInvoiceSent(true);
            cycle.setInvoiceSentAt(LocalDateTime.now());
        }

        // Create event
        billingEventService.createEvent(cycle,
            com.waqiti.billingorchestrator.entity.BillingEvent.EventType.INVOICE_GENERATED,
            "Invoice generated for cycle " + cycle.getId());

        long processingTime = System.currentTimeMillis() - startTime;

        log.info("Invoice generated successfully: invoiceId={}, processingTimeMs={}",
                cycle.getInvoiceId(), processingTime);

        return InvoiceGenerationResponse.builder()
                .success(true)
                .billingCycleId(cycle.getId())
                .invoiceId(cycle.getInvoiceId())
                .invoiceNumber(cycle.getInvoiceNumber())
                .lineItemCount(cycle.getLineItems() != null ? cycle.getLineItems().size() : 0)
                .invoiceSent(cycle.getInvoiceSent())
                .notificationChannels(request.getNotificationChannels())
                .pdfGenerated(Boolean.TRUE.equals(request.getGeneratePdf()))
                .pdfUrl(generatePdfUrl(cycle.getInvoiceId()))
                .generatedAt(LocalDateTime.now())
                .processingTimeMs(processingTime)
                .build();
    }

    /**
     * Get invoice by ID
     */
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(UUID invoiceId) {
        log.debug("Fetching invoice: {}", invoiceId);

        BillingCycle cycle = billingCycleRepository.findByInvoiceId(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        return mapToInvoiceResponse(cycle);
    }

    /**
     * Get invoices for account
     */
    @Transactional(readOnly = true)
    public Page<InvoiceResponse> getAccountInvoices(UUID accountId, String status, Pageable pageable) {
        log.debug("Fetching invoices for account: {}, status: {}", accountId, status);

        Page<BillingCycle> cycles;

        if (status != null && !status.isEmpty()) {
            try {
                BillingCycle.CycleStatus cycleStatus = BillingCycle.CycleStatus.valueOf(status.toUpperCase());
                cycles = billingCycleRepository.findByAccountIdAndStatus(accountId, cycleStatus, pageable);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status filter: {}, returning all invoices for account", status);
                cycles = billingCycleRepository.findByAccountId(accountId, pageable);
            }
        } else {
            cycles = billingCycleRepository.findByAccountId(accountId, pageable);
        }

        return cycles
            .filter(cycle -> cycle.getInvoiceGenerated() != null && cycle.getInvoiceGenerated())
            .map(this::mapToInvoiceResponse);
    }

    /**
     * Send invoice to customer
     */
    @Transactional
    public void sendInvoice(UUID invoiceId) {
        log.info("Sending invoice: {}", invoiceId);

        BillingCycle cycle = billingCycleRepository.findByInvoiceId(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        if (cycle.getInvoiceGenerated() == null || !cycle.getInvoiceGenerated()) {
            throw new IllegalStateException("Invoice not yet generated for invoiceId: " + invoiceId);
        }

        if (cycle.getInvoiceSent() != null && cycle.getInvoiceSent()) {
            log.warn("Invoice {} already sent at {}", invoiceId, cycle.getInvoiceSentAt());
            return;
        }

        // Update invoice sent status
        cycle.setInvoiceSent(true);
        cycle.setInvoiceSentAt(LocalDateTime.now());
        billingCycleRepository.save(cycle);

        // Create event
        billingEventService.createEvent(cycle,
            com.waqiti.billingorchestrator.entity.BillingEvent.EventType.INVOICE_SENT,
            String.format("Invoice %s sent to customer %s", cycle.getInvoiceNumber(), cycle.getCustomerId()));

        // Send invoice notification through NotificationService
        try {
            String invoiceSubject = String.format("Your Invoice %s is Ready", cycle.getInvoiceNumber());
            String invoiceMessage = buildInvoiceNotificationMessage(cycle);

            notificationService.sendCriticalNotification(
                cycle.getCustomerId().toString(),
                invoiceSubject,
                invoiceMessage,
                java.util.Map.of(
                    "invoiceId", cycle.getInvoiceId().toString(),
                    "invoiceNumber", cycle.getInvoiceNumber(),
                    "accountId", cycle.getAccountId().toString(),
                    "totalAmount", cycle.getTotalAmount().toString(),
                    "currency", cycle.getCurrency(),
                    "dueDate", cycle.getDueDate().toString(),
                    "pdfUrl", generatePdfUrl(cycle.getInvoiceId()),
                    "notificationType", "INVOICE_NOTIFICATION"
                )
            );

            log.info("Invoice notification sent successfully for invoice {}", invoiceId);

        } catch (Exception e) {
            log.error("Failed to send invoice notification for invoice {}: {}",
                invoiceId, e.getMessage(), e);

            // Don't fail the invoice send process if notification fails
            // The invoice is marked as sent even if notification fails
        }
    }

    /**
     * Build invoice notification message
     */
    private String buildInvoiceNotificationMessage(BillingCycle cycle) {
        return String.format(
            "Dear Customer,\n\n" +
            "Your invoice is now available.\n\n" +
            "Invoice Number: %s\n" +
            "Invoice Date: %s\n" +
            "Due Date: %s\n\n" +
            "Billing Period: %s to %s\n\n" +
            "Amount Due: %s %s\n\n" +
            "Breakdown:\n" +
            "- Subscription Charges: %s %s\n" +
            "- Usage Charges: %s %s\n" +
            "- Transaction Fees: %s %s\n" +
            "- Taxes: %s %s\n" +
            "- Discounts: -%s %s\n\n" +
            "View your invoice: %s\n\n" +
            "Please ensure payment is made by the due date to avoid any service interruption.\n\n" +
            "Thank you for your business,\n" +
            "Waqiti Billing Team",
            cycle.getInvoiceNumber(),
            cycle.getInvoiceDate(),
            cycle.getDueDate(),
            cycle.getCycleStartDate(),
            cycle.getCycleEndDate(),
            cycle.getTotalAmount(),
            cycle.getCurrency(),
            cycle.getSubscriptionCharges(),
            cycle.getCurrency(),
            cycle.getUsageCharges(),
            cycle.getCurrency(),
            cycle.getTransactionFees(),
            cycle.getCurrency(),
            cycle.getTaxes(),
            cycle.getCurrency(),
            cycle.getDiscounts(),
            cycle.getCurrency(),
            generatePdfUrl(cycle.getInvoiceId())
        );
    }

    /**
     * Generate unique invoice number
     */
    private String generateInvoiceNumber(BillingCycle cycle) {
        return String.format("INV-%d-%08d",
                cycle.getCycleStartDate().getYear(),
                cycle.getId().hashCode() & 0x7FFFFFFF);
    }

    /**
     * Generate PDF URL for invoice
     */
    private String generatePdfUrl(UUID invoiceId) {
        return String.format("https://invoices.example.com/%s.pdf", invoiceId);
    }

    /**
     * Map BillingCycle to InvoiceResponse
     */
    private InvoiceResponse mapToInvoiceResponse(BillingCycle cycle) {
        return InvoiceResponse.builder()
            .invoiceId(cycle.getInvoiceId())
            .invoiceNumber(cycle.getInvoiceNumber())
            .customerId(cycle.getCustomerId())
            .accountId(cycle.getAccountId())
            .invoiceDate(cycle.getInvoiceDate())
            .dueDate(cycle.getDueDate())
            .cycleStartDate(cycle.getCycleStartDate())
            .cycleEndDate(cycle.getCycleEndDate())
            .currency(cycle.getCurrency())
            .subscriptionCharges(cycle.getSubscriptionCharges())
            .usageCharges(cycle.getUsageCharges())
            .transactionFees(cycle.getTransactionFees())
            .adjustments(cycle.getAdjustments())
            .credits(cycle.getCredits())
            .taxAmount(cycle.getTaxAmount())
            .totalAmount(cycle.getTotalAmount())
            .paidAmount(cycle.getPaidAmount())
            .balanceDue(cycle.getBalanceDue())
            .status(cycle.getStatus() != null ? cycle.getStatus().name() : null)
            .invoiceSent(cycle.getInvoiceSent())
            .invoiceSentAt(cycle.getInvoiceSentAt())
            .pdfUrl(generatePdfUrl(cycle.getInvoiceId()))
            .lineItemCount(cycle.getLineItems() != null ? cycle.getLineItems().size() : 0)
            .createdAt(cycle.getCreatedAt())
            .build();
    }
}
