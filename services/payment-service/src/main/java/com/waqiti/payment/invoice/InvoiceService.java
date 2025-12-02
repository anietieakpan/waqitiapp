package com.waqiti.payment.invoice;

import com.waqiti.common.exceptions.BusinessException;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.payment.businessprofile.BusinessProfile;
import com.waqiti.payment.businessprofile.BusinessProfileService;
import com.waqiti.payment.invoice.dto.*;
import com.waqiti.payment.invoice.repository.InvoiceRepository;
import com.waqiti.payment.invoice.template.InvoiceTemplateEngine;
import com.waqiti.payment.notification.EmailService;
import com.waqiti.payment.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final BusinessProfileService businessProfileService;
    private final InvoiceTemplateEngine templateEngine;
    private final PaymentService paymentService;
    private final EmailService emailService;
    private final TaxCalculationService taxCalculationService;
    private final InvoiceNumberGenerator numberGenerator;
    private final SecurityContext securityContext;
    private final PdfGeneratorService pdfGeneratorService;
    private final AnalyticsService analyticsService;
    
    // Self-injection for @Cacheable methods to work correctly
    @Autowired
    @Lazy
    private InvoiceService self;

    @Transactional
    public Invoice createInvoice(InvoiceRequest request) {
        log.info("Creating invoice for business: {}", request.getBusinessProfileId());
        
        // Get business profile
        BusinessProfile businessProfile = businessProfileService.getBusinessProfile(request.getBusinessProfileId());
        
        // Verify ownership
        if (!businessProfile.getUserId().equals(securityContext.getUserId())) {
            throw new BusinessException("Unauthorized access to business profile");
        }
        
        // Generate invoice number
        String invoiceNumber = generateInvoiceNumber(businessProfile);
        
        // Create invoice
        Invoice invoice = Invoice.builder()
                .id(UUID.randomUUID())
                .invoiceNumber(invoiceNumber)
                .businessProfileId(businessProfile.getId())
                .businessName(businessProfile.getBusinessName())
                .businessAddress(businessProfile.getAddress())
                .businessTaxId(businessProfile.getTaxId())
                .customerId(request.getCustomerId())
                .customerName(request.getCustomerName())
                .customerEmail(request.getCustomerEmail())
                .customerAddress(request.getCustomerAddress())
                .customerTaxId(request.getCustomerTaxId())
                .status(InvoiceStatus.DRAFT)
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .issueDate(request.getIssueDate() != null ? request.getIssueDate() : LocalDate.now())
                .dueDate(calculateDueDate(request, businessProfile))
                .paymentTerms(request.getPaymentTerms() != null ? request.getPaymentTerms() : businessProfile.getInvoiceSettings().getDefaultPaymentTerms())
                .notes(request.getNotes())
                .termsAndConditions(request.getTermsAndConditions())
                .createdAt(Instant.now())
                .build();
        
        // Add line items
        List<InvoiceLineItem> lineItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        
        for (LineItemRequest itemRequest : request.getLineItems()) {
            InvoiceLineItem lineItem = InvoiceLineItem.builder()
                    .id(UUID.randomUUID())
                    .description(itemRequest.getDescription())
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(itemRequest.getUnitPrice())
                    .amount(itemRequest.getUnitPrice().multiply(itemRequest.getQuantity()))
                    .taxRate(itemRequest.getTaxRate())
                    .taxAmount(calculateItemTax(itemRequest))
                    .discountRate(itemRequest.getDiscountRate())
                    .discountAmount(calculateItemDiscount(itemRequest))
                    .build();
            
            BigDecimal netAmount = lineItem.getAmount()
                    .subtract(lineItem.getDiscountAmount())
                    .add(lineItem.getTaxAmount());
            lineItem.setNetAmount(netAmount);
            
            lineItems.add(lineItem);
            subtotal = subtotal.add(lineItem.getAmount());
        }
        
        invoice.setLineItems(lineItems);
        invoice.setSubtotal(subtotal);
        
        // Calculate discounts
        BigDecimal totalDiscount = calculateTotalDiscount(invoice, request);
        invoice.setDiscountAmount(totalDiscount);
        
        // Calculate taxes
        TaxCalculation taxCalculation = calculateTaxes(invoice, businessProfile);
        invoice.setTaxAmount(taxCalculation.getTotalTax());
        invoice.setTaxBreakdown(taxCalculation.getBreakdown());
        
        // Calculate total
        BigDecimal total = subtotal
                .subtract(totalDiscount)
                .add(taxCalculation.getTotalTax());
        invoice.setTotalAmount(total);
        invoice.setBalanceDue(total);
        
        // Save invoice
        invoice = invoiceRepository.save(invoice);
        
        // Track creation
        trackInvoiceCreation(invoice);
        
        // Auto-send if enabled
        if (request.isSendImmediately() && businessProfile.getInvoiceSettings().isAutoSendEnabled()) {
            sendInvoice(invoice.getId());
        }
        
        log.info("Invoice created: {}", invoice.getInvoiceNumber());
        return invoice;
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "invoices", key = "#invoiceId"),
        @CacheEvict(value = "invoices", key = "'number:' + #result.invoiceNumber", condition = "#result != null")
    })
    public Invoice updateInvoice(UUID invoiceId, InvoiceUpdateRequest request) {
        log.info("Updating invoice: {}", invoiceId);
        
        Invoice invoice = self.getInvoice(invoiceId);
        
        // Verify ownership
        BusinessProfile businessProfile = businessProfileService.getBusinessProfile(invoice.getBusinessProfileId());
        if (!businessProfile.getUserId().equals(securityContext.getUserId())) {
            throw new BusinessException("Unauthorized access to invoice");
        }
        
        // Check if invoice can be updated
        if (!canUpdateInvoice(invoice)) {
            throw new BusinessException("Invoice cannot be updated in status: " + invoice.getStatus());
        }
        
        // Update customer information
        if (request.getCustomerName() != null) {
            invoice.setCustomerName(request.getCustomerName());
        }
        if (request.getCustomerEmail() != null) {
            invoice.setCustomerEmail(request.getCustomerEmail());
        }
        if (request.getCustomerAddress() != null) {
            invoice.setCustomerAddress(request.getCustomerAddress());
        }
        
        // Update dates
        if (request.getIssueDate() != null) {
            invoice.setIssueDate(request.getIssueDate());
        }
        if (request.getDueDate() != null) {
            invoice.setDueDate(request.getDueDate());
        }
        
        // Update line items if provided
        if (request.getLineItems() != null) {
            updateLineItems(invoice, request.getLineItems());
            recalculateInvoiceTotals(invoice, businessProfile);
        }
        
        // Update notes and terms
        if (request.getNotes() != null) {
            invoice.setNotes(request.getNotes());
        }
        if (request.getTermsAndConditions() != null) {
            invoice.setTermsAndConditions(request.getTermsAndConditions());
        }
        
        invoice.setUpdatedAt(Instant.now());
        
        return invoiceRepository.save(invoice);
    }

    @Transactional
    @CacheEvict(value = "invoices", key = "#invoiceId")
    public Invoice sendInvoice(UUID invoiceId) {
        log.info("Sending invoice: {}", invoiceId);
        
        Invoice invoice = self.getInvoice(invoiceId);
        
        // Verify ownership
        BusinessProfile businessProfile = businessProfileService.getBusinessProfile(invoice.getBusinessProfileId());
        if (!businessProfile.getUserId().equals(securityContext.getUserId())) {
            throw new BusinessException("Unauthorized access to invoice");
        }
        
        // Check if invoice can be sent
        if (invoice.getStatus() != InvoiceStatus.DRAFT && invoice.getStatus() != InvoiceStatus.SENT) {
            throw new BusinessException("Invoice cannot be sent in status: " + invoice.getStatus());
        }
        
        // Generate PDF
        byte[] pdfData = generateInvoicePdf(invoice, businessProfile);
        
        // Create payment link
        String paymentLink = createPaymentLink(invoice);
        invoice.setPaymentLink(paymentLink);
        
        // Send email
        EmailResult emailResult = emailService.sendInvoiceEmail(
                invoice.getCustomerEmail(),
                invoice.getCustomerName(),
                invoice.getInvoiceNumber(),
                invoice.getTotalAmount(),
                invoice.getCurrency(),
                invoice.getDueDate(),
                paymentLink,
                pdfData
        );
        
        if (emailResult.isSuccess()) {
            invoice.setStatus(InvoiceStatus.SENT);
            invoice.setSentAt(Instant.now());
            invoice.setLastSentAt(Instant.now());
            
            // Track sending
            trackInvoiceSent(invoice);
        } else {
            throw new BusinessException("Failed to send invoice: " + emailResult.getErrorMessage());
        }
        
        return invoiceRepository.save(invoice);
    }

    @Transactional
    @CacheEvict(value = "invoices", key = "#invoiceId")
    public Invoice recordPayment(UUID invoiceId, PaymentRecordRequest request) {
        log.info("Recording payment for invoice: {}", invoiceId);
        
        Invoice invoice = self.getInvoice(invoiceId);
        
        // Verify ownership
        BusinessProfile businessProfile = businessProfileService.getBusinessProfile(invoice.getBusinessProfileId());
        if (!businessProfile.getUserId().equals(securityContext.getUserId())) {
            throw new BusinessException("Unauthorized access to invoice");
        }
        
        // Validate payment amount with null safety
        if (request.getAmount() == null) {
            throw new BusinessException("Payment amount is required");
        }
        
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Payment amount must be positive");
        }
        
        BigDecimal balanceDue = invoice.getBalanceDue() != null ? invoice.getBalanceDue() : BigDecimal.ZERO;
        if (request.getAmount().compareTo(balanceDue) > 0) {
            throw new BusinessException("Payment amount exceeds balance due");
        }
        
        // Record payment
        InvoicePayment payment = InvoicePayment.builder()
                .id(UUID.randomUUID())
                .invoiceId(invoiceId)
                .amount(request.getAmount())
                .currency(invoice.getCurrency())
                .paymentMethod(request.getPaymentMethod())
                .paymentReference(request.getPaymentReference())
                .paymentDate(request.getPaymentDate() != null ? request.getPaymentDate() : Instant.now())
                .notes(request.getNotes())
                .build();
        
        // Add to payments list
        if (invoice.getPayments() == null) {
            invoice.setPayments(new ArrayList<>());
        }
        invoice.getPayments().add(payment);
        
        // Update balance
        BigDecimal newBalance = invoice.getBalanceDue().subtract(request.getAmount());
        invoice.setBalanceDue(newBalance);
        
        // Update status
        if (newBalance.compareTo(BigDecimal.ZERO) == 0) {
            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setPaidAt(Instant.now());
        } else {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }
        
        invoice = invoiceRepository.save(invoice);
        
        // Send payment confirmation
        sendPaymentConfirmation(invoice, payment);
        
        // Track payment
        trackPaymentRecorded(invoice, payment);
        
        return invoice;
    }

    @Transactional
    @CacheEvict(value = "invoices", key = "#invoiceId")
    public Invoice voidInvoice(UUID invoiceId, String reason) {
        log.info("Voiding invoice: {}", invoiceId);
        
        Invoice invoice = self.getInvoice(invoiceId);
        
        // Verify ownership
        BusinessProfile businessProfile = businessProfileService.getBusinessProfile(invoice.getBusinessProfileId());
        if (!businessProfile.getUserId().equals(securityContext.getUserId())) {
            throw new BusinessException("Unauthorized access to invoice");
        }
        
        // Check if invoice can be voided
        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.VOIDED) {
            throw new BusinessException("Cannot void invoice in status: " + invoice.getStatus());
        }
        
        invoice.setStatus(InvoiceStatus.VOIDED);
        invoice.setVoidedAt(Instant.now());
        invoice.setVoidReason(reason);
        
        invoice = invoiceRepository.save(invoice);
        
        // Notify customer
        notifyInvoiceVoided(invoice, reason);
        
        return invoice;
    }

    @Cacheable(value = "invoices", key = "#invoiceId")
    public Invoice getInvoice(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new BusinessException("Invoice not found"));
    }

    @Cacheable(value = "invoices", key = "'number:' + #invoiceNumber")
    public Invoice getInvoiceByNumber(String invoiceNumber) {
        return invoiceRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new BusinessException("Invoice not found"));
    }

    public Page<Invoice> getBusinessInvoices(UUID businessProfileId, InvoiceSearchCriteria criteria, Pageable pageable) {
        // Verify ownership
        BusinessProfile businessProfile = businessProfileService.getBusinessProfile(businessProfileId);
        if (!businessProfile.getUserId().equals(securityContext.getUserId())) {
            throw new BusinessException("Unauthorized access to business invoices");
        }
        
        return invoiceRepository.searchInvoices(
                businessProfileId,
                criteria.getStatus(),
                criteria.getCustomerId(),
                criteria.getFromDate(),
                criteria.getToDate(),
                criteria.getMinAmount(),
                criteria.getMaxAmount(),
                pageable
        );
    }

    @Transactional
    public Invoice duplicateInvoice(UUID invoiceId) {
        Invoice original = self.getInvoice(invoiceId);
        
        // Verify ownership
        BusinessProfile businessProfile = businessProfileService.getBusinessProfile(original.getBusinessProfileId());
        if (!businessProfile.getUserId().equals(securityContext.getUserId())) {
            throw new BusinessException("Unauthorized access to invoice");
        }
        
        // Create new invoice with same details
        Invoice duplicate = Invoice.builder()
                .id(UUID.randomUUID())
                .invoiceNumber(generateInvoiceNumber(businessProfile))
                .businessProfileId(original.getBusinessProfileId())
                .businessName(original.getBusinessName())
                .businessAddress(original.getBusinessAddress())
                .businessTaxId(original.getBusinessTaxId())
                .customerId(original.getCustomerId())
                .customerName(original.getCustomerName())
                .customerEmail(original.getCustomerEmail())
                .customerAddress(original.getCustomerAddress())
                .customerTaxId(original.getCustomerTaxId())
                .status(InvoiceStatus.DRAFT)
                .currency(original.getCurrency())
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(businessProfile.getInvoiceSettings().getDefaultDueDays()))
                .paymentTerms(original.getPaymentTerms())
                .notes(original.getNotes())
                .termsAndConditions(original.getTermsAndConditions())
                .lineItems(duplicateLineItems(original.getLineItems()))
                .subtotal(original.getSubtotal())
                .discountAmount(original.getDiscountAmount())
                .taxAmount(original.getTaxAmount())
                .taxBreakdown(original.getTaxBreakdown())
                .totalAmount(original.getTotalAmount())
                .balanceDue(original.getTotalAmount())
                .createdAt(Instant.now())
                .build();
        
        return invoiceRepository.save(duplicate);
    }

    @Scheduled(cron = "0 0 9 * * *") // Run daily at 9 AM
    public void sendInvoiceReminders() {
        log.info("Starting invoice reminder process");
        
        // Get all invoices that need reminders
        List<Invoice> invoicesToRemind = invoiceRepository.findInvoicesNeedingReminders();
        
        for (Invoice invoice : invoicesToRemind) {
            try {
                BusinessProfile businessProfile = businessProfileService.getBusinessProfile(invoice.getBusinessProfileId());
                
                if (businessProfile.getInvoiceSettings().isAutoRemindersEnabled()) {
                    sendReminder(invoice, businessProfile);
                }
            } catch (Exception e) {
                log.error("Error sending reminder for invoice: {}", invoice.getInvoiceNumber(), e);
            }
        }
        
        log.info("Invoice reminder process completed. Sent {} reminders", invoicesToRemind.size());
    }

    @Scheduled(cron = "0 0 0 * * *") // Run daily at midnight
    public void processOverdueInvoices() {
        log.info("Processing overdue invoices");
        
        LocalDate today = LocalDate.now();
        List<Invoice> overdueInvoices = invoiceRepository.findByStatusAndDueDateBefore(
                InvoiceStatus.SENT,
                today
        );
        
        for (Invoice invoice : overdueInvoices) {
            invoice.setStatus(InvoiceStatus.OVERDUE);
            invoice.setOverdueAt(Instant.now());
            invoiceRepository.save(invoice);
            
            // Calculate and apply late fees if enabled
            applyLateFees(invoice);
            
            // Send overdue notification
            sendOverdueNotification(invoice);
        }
        
        log.info("Processed {} overdue invoices", overdueInvoices.size());
    }

    private String generateInvoiceNumber(BusinessProfile businessProfile) {
        return numberGenerator.generateInvoiceNumber(
                businessProfile.getId(),
                businessProfile.getInvoiceSettings().getInvoicePrefix(),
                businessProfile.getInvoiceSettings().getStartingNumber()
        );
    }

    private LocalDate calculateDueDate(InvoiceRequest request, BusinessProfile businessProfile) {
        if (request.getDueDate() != null) {
            return request.getDueDate();
        }
        
        int dueDays = businessProfile.getInvoiceSettings().getDefaultDueDays();
        return request.getIssueDate() != null ? 
                request.getIssueDate().plusDays(dueDays) : 
                LocalDate.now().plusDays(dueDays);
    }

    private BigDecimal calculateItemTax(LineItemRequest item) {
        if (item.getTaxRate() == null || item.getTaxRate().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal amount = item.getUnitPrice().multiply(item.getQuantity());
        if (item.getDiscountRate() != null) {
            BigDecimal discount = amount.multiply(item.getDiscountRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            amount = amount.subtract(discount);
        }
        
        return amount.multiply(item.getTaxRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateItemDiscount(LineItemRequest item) {
        if (item.getDiscountRate() == null || item.getDiscountRate().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal amount = item.getUnitPrice().multiply(item.getQuantity());
        return amount.multiply(item.getDiscountRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTotalDiscount(Invoice invoice, InvoiceRequest request) {
        BigDecimal itemDiscounts = invoice.getLineItems().stream()
                .map(InvoiceLineItem::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (request.getOverallDiscountRate() != null) {
            BigDecimal overallDiscount = invoice.getSubtotal()
                    .multiply(request.getOverallDiscountRate())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return itemDiscounts.add(overallDiscount);
        }
        
        return itemDiscounts;
    }

    private TaxCalculation calculateTaxes(Invoice invoice, BusinessProfile businessProfile) {
        return taxCalculationService.calculateTaxes(
                invoice.getLineItems(),
                invoice.getBusinessAddress(),
                invoice.getCustomerAddress(),
                businessProfile.getTaxSettings()
        );
    }

    private boolean canUpdateInvoice(Invoice invoice) {
        return invoice.getStatus() == InvoiceStatus.DRAFT || 
               invoice.getStatus() == InvoiceStatus.SENT;
    }

    private void updateLineItems(Invoice invoice, List<LineItemRequest> newItems) {
        invoice.getLineItems().clear();
        
        for (LineItemRequest itemRequest : newItems) {
            InvoiceLineItem lineItem = InvoiceLineItem.builder()
                    .id(UUID.randomUUID())
                    .description(itemRequest.getDescription())
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(itemRequest.getUnitPrice())
                    .amount(itemRequest.getUnitPrice().multiply(itemRequest.getQuantity()))
                    .taxRate(itemRequest.getTaxRate())
                    .taxAmount(calculateItemTax(itemRequest))
                    .discountRate(itemRequest.getDiscountRate())
                    .discountAmount(calculateItemDiscount(itemRequest))
                    .build();
            
            BigDecimal netAmount = lineItem.getAmount()
                    .subtract(lineItem.getDiscountAmount())
                    .add(lineItem.getTaxAmount());
            lineItem.setNetAmount(netAmount);
            
            invoice.getLineItems().add(lineItem);
        }
    }

    private void recalculateInvoiceTotals(Invoice invoice, BusinessProfile businessProfile) {
        BigDecimal subtotal = invoice.getLineItems().stream()
                .map(InvoiceLineItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.setSubtotal(subtotal);
        
        BigDecimal totalDiscount = invoice.getLineItems().stream()
                .map(InvoiceLineItem::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.setDiscountAmount(totalDiscount);
        
        TaxCalculation taxCalculation = calculateTaxes(invoice, businessProfile);
        invoice.setTaxAmount(taxCalculation.getTotalTax());
        invoice.setTaxBreakdown(taxCalculation.getBreakdown());
        
        BigDecimal total = subtotal
                .subtract(totalDiscount)
                .add(taxCalculation.getTotalTax());
        invoice.setTotalAmount(total);
        
        // Adjust balance due based on payments
        BigDecimal totalPaid = invoice.getPayments() != null ?
                invoice.getPayments().stream()
                        .map(InvoicePayment::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add) :
                BigDecimal.ZERO;
        invoice.setBalanceDue(total.subtract(totalPaid));
    }

    private byte[] generateInvoicePdf(Invoice invoice, BusinessProfile businessProfile) {
        return pdfGeneratorService.generateInvoicePdf(invoice, businessProfile);
    }

    private String createPaymentLink(Invoice invoice) {
        return paymentService.createPaymentLink(
                invoice.getId(),
                invoice.getTotalAmount(),
                invoice.getCurrency(),
                "Invoice " + invoice.getInvoiceNumber(),
                invoice.getDueDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
        );
    }

    private List<InvoiceLineItem> duplicateLineItems(List<InvoiceLineItem> original) {
        return original.stream()
                .map(item -> InvoiceLineItem.builder()
                        .id(UUID.randomUUID())
                        .description(item.getDescription())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .amount(item.getAmount())
                        .taxRate(item.getTaxRate())
                        .taxAmount(item.getTaxAmount())
                        .discountRate(item.getDiscountRate())
                        .discountAmount(item.getDiscountAmount())
                        .netAmount(item.getNetAmount())
                        .build())
                .collect(Collectors.toList());
    }

    private void sendReminder(Invoice invoice, BusinessProfile businessProfile) {
        long daysUntilDue = ChronoUnit.DAYS.between(LocalDate.now(), invoice.getDueDate());
        
        List<Integer> reminderDays = businessProfile.getInvoiceSettings().getReminderDays();
        if (reminderDays.contains((int) daysUntilDue)) {
            emailService.sendInvoiceReminder(
                    invoice.getCustomerEmail(),
                    invoice.getCustomerName(),
                    invoice.getInvoiceNumber(),
                    invoice.getBalanceDue(),
                    invoice.getCurrency(),
                    invoice.getDueDate(),
                    invoice.getPaymentLink()
            );
            
            invoice.setLastReminderSentAt(Instant.now());
            invoiceRepository.save(invoice);
        }
    }

    private void applyLateFees(Invoice invoice) {
        BusinessProfile businessProfile = businessProfileService.getBusinessProfile(invoice.getBusinessProfileId());
        
        if (businessProfile.getInvoiceSettings().isLateFeeEnabled()) {
            BigDecimal lateFeeRate = businessProfile.getInvoiceSettings().getLateFeeRate();
            BigDecimal lateFeeAmount = invoice.getBalanceDue()
                    .multiply(lateFeeRate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            
            invoice.setLateFeeAmount(lateFeeAmount);
            invoice.setBalanceDue(invoice.getBalanceDue().add(lateFeeAmount));
            invoiceRepository.save(invoice);
        }
    }

    private void sendPaymentConfirmation(Invoice invoice, InvoicePayment payment) {
        emailService.sendPaymentConfirmation(
                invoice.getCustomerEmail(),
                invoice.getCustomerName(),
                invoice.getInvoiceNumber(),
                payment.getAmount(),
                invoice.getCurrency(),
                invoice.getBalanceDue()
        );
    }

    private void notifyInvoiceVoided(Invoice invoice, String reason) {
        emailService.sendInvoiceVoidedNotification(
                invoice.getCustomerEmail(),
                invoice.getCustomerName(),
                invoice.getInvoiceNumber(),
                reason
        );
    }

    private void sendOverdueNotification(Invoice invoice) {
        emailService.sendOverdueNotification(
                invoice.getCustomerEmail(),
                invoice.getCustomerName(),
                invoice.getInvoiceNumber(),
                invoice.getBalanceDue(),
                invoice.getCurrency(),
                ChronoUnit.DAYS.between(invoice.getDueDate(), LocalDate.now())
        );
    }

    private void trackInvoiceCreation(Invoice invoice) {
        analyticsService.trackEvent("invoice_created", Map.of(
                "invoice_id", invoice.getId(),
                "invoice_number", invoice.getInvoiceNumber(),
                "amount", invoice.getTotalAmount(),
                "currency", invoice.getCurrency()
        ));
    }

    private void trackInvoiceSent(Invoice invoice) {
        analyticsService.trackEvent("invoice_sent", Map.of(
                "invoice_id", invoice.getId(),
                "invoice_number", invoice.getInvoiceNumber()
        ));
    }

    private void trackPaymentRecorded(Invoice invoice, InvoicePayment payment) {
        analyticsService.trackEvent("invoice_payment_recorded", Map.of(
                "invoice_id", invoice.getId(),
                "payment_id", payment.getId(),
                "amount", payment.getAmount(),
                "payment_method", payment.getPaymentMethod()
        ));
    }
}