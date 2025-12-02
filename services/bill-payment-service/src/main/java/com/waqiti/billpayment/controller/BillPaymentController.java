package com.waqiti.billpayment.controller;

import com.waqiti.billpayment.dto.*;
import com.waqiti.billpayment.service.BillPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for bill payment operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/bills")
@RequiredArgsConstructor
@Validated
@Tag(name = "Bill Payment", description = "Bill payment management APIs")
@SecurityRequirement(name = "bearer-jwt")
public class BillPaymentController {

    private final BillPaymentService billPaymentService;

    // ============== Biller Management ==============
    
    @GetMapping("/billers")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:biller-read')")
    @Operation(summary = "Get all available billers")
    public ResponseEntity<Page<BillerResponse>> getAllBillers(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String country,
            Pageable pageable) {
        
        log.debug("Fetching billers - category: {}, country: {}", category, country);
        Page<BillerResponse> billers = billPaymentService.getAllBillers(category, country, pageable);
        return ResponseEntity.ok(billers);
    }

    @GetMapping("/billers/{billerId}")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:biller-read')")
    @Operation(summary = "Get biller details")
    public ResponseEntity<BillerResponse> getBillerDetails(
            @PathVariable @NotBlank String billerId) {
        
        log.debug("Fetching biller details for ID: {}", billerId);
        BillerResponse biller = billPaymentService.getBillerDetails(billerId);
        return ResponseEntity.ok(biller);
    }

    @GetMapping("/billers/search")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:biller-search')")
    @Operation(summary = "Search billers")
    public ResponseEntity<List<BillerResponse>> searchBillers(
            @RequestParam @NotBlank String query,
            @RequestParam(defaultValue = "10") int limit) {
        
        log.debug("Searching billers with query: {}", query);
        List<BillerResponse> results = billPaymentService.searchBillers(query, limit);
        return ResponseEntity.ok(results);
    }

    // ============== Bill Account Management ==============
    
    @PostMapping("/accounts/add")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:account-add')")
    @Operation(summary = "Add a bill account")
    public ResponseEntity<BillAccountResponse> addBillAccount(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddBillAccountRequest request) {
        
        String userId = jwt.getSubject();
        log.info("Adding bill account for user: {}", userId);
        
        BillAccountResponse account = billPaymentService.addBillAccount(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:account-read')")
    @Operation(summary = "Get user's bill accounts")
    public ResponseEntity<List<BillAccountResponse>> getUserBillAccounts(
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        log.debug("Fetching bill accounts for user: {}", userId);
        
        List<BillAccountResponse> accounts = billPaymentService.getUserBillAccounts(userId);
        return ResponseEntity.ok(accounts);
    }

    @PutMapping("/accounts/{accountId}")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:account-update')")
    @Operation(summary = "Update bill account")
    public ResponseEntity<BillAccountResponse> updateBillAccount(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateBillAccountRequest request) {
        
        String userId = jwt.getSubject();
        log.info("Updating bill account {} for user: {}", accountId, userId);
        
        BillAccountResponse account = billPaymentService.updateBillAccount(userId, accountId, request);
        return ResponseEntity.ok(account);
    }

    @DeleteMapping("/accounts/{accountId}")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:account-delete')")
    @Operation(summary = "Delete bill account")
    public ResponseEntity<Void> deleteBillAccount(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId) {
        
        String userId = jwt.getSubject();
        log.info("Deleting bill account {} for user: {}", accountId, userId);
        
        billPaymentService.deleteBillAccount(userId, accountId);
        return ResponseEntity.noContent().build();
    }

    // ============== Bill Inquiry and Validation ==============
    
    @PostMapping("/inquiry")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:inquiry')")
    @Operation(summary = "Inquire about a bill")
    public ResponseEntity<BillInquiryResponse> inquireBill(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BillInquiryRequest request) {
        
        String userId = jwt.getSubject();
        log.info("Bill inquiry for user: {} - biller: {}", userId, request.getBillerId());
        
        BillInquiryResponse inquiry = billPaymentService.inquireBill(userId, request);
        return ResponseEntity.ok(inquiry);
    }

    @PostMapping("/validate")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:validate')")
    @Operation(summary = "Validate bill payment details")
    public ResponseEntity<BillValidationResponse> validateBillPayment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BillValidationRequest request) {
        
        String userId = jwt.getSubject();
        log.info("Validating bill payment for user: {}", userId);
        
        BillValidationResponse validation = billPaymentService.validateBillPayment(userId, request);
        return ResponseEntity.ok(validation);
    }

    // ============== Bill Payment Operations ==============
    
    @PostMapping("/pay")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:pay')")
    @Operation(summary = "Pay a bill")
    public ResponseEntity<BillPaymentResponse> payBill(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PayBillRequest request) {
        
        String userId = jwt.getSubject();
        log.info("Processing bill payment for user: {} - amount: {}", userId, request.getAmount());
        
        BillPaymentResponse payment = billPaymentService.payBill(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    @PostMapping("/pay/instant")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:pay-instant')")
    @Operation(summary = "Make instant bill payment")
    public ResponseEntity<BillPaymentResponse> payBillInstant(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PayBillInstantRequest request) {
        
        String userId = jwt.getSubject();
        log.info("Processing instant bill payment for user: {}", userId);
        
        BillPaymentResponse payment = billPaymentService.payBillInstant(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    @PostMapping("/pay/scheduled")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:pay-scheduled')")
    @Operation(summary = "Schedule a bill payment")
    public ResponseEntity<ScheduledPaymentResponse> scheduleBillPayment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ScheduleBillPaymentRequest request) {
        
        String userId = jwt.getSubject();
        log.info("Scheduling bill payment for user: {} - date: {}", userId, request.getScheduledDate());
        
        ScheduledPaymentResponse scheduled = billPaymentService.scheduleBillPayment(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduled);
    }

    @PostMapping("/pay/recurring")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:pay-recurring')")
    @Operation(summary = "Setup recurring bill payment")
    public ResponseEntity<RecurringPaymentResponse> setupRecurringPayment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SetupRecurringPaymentRequest request) {
        
        String userId = jwt.getSubject();
        log.info("Setting up recurring payment for user: {} - frequency: {}", userId, request.getFrequency());
        
        RecurringPaymentResponse recurring = billPaymentService.setupRecurringPayment(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(recurring);
    }

    // ============== Payment Management ==============
    
    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:payment-read')")
    @Operation(summary = "Get payment history")
    public ResponseEntity<Page<BillPaymentResponse>> getPaymentHistory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        
        String userId = jwt.getSubject();
        log.debug("Fetching payment history for user: {}", userId);
        
        Page<BillPaymentResponse> payments = billPaymentService.getPaymentHistory(userId, fromDate, toDate, status, pageable);
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/payments/{paymentId}")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:payment-read')")
    @Operation(summary = "Get payment details")
    public ResponseEntity<BillPaymentResponse> getPaymentDetails(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId) {
        
        String userId = jwt.getSubject();
        log.debug("Fetching payment details {} for user: {}", paymentId, userId);
        
        BillPaymentResponse payment = billPaymentService.getPaymentDetails(userId, paymentId);
        return ResponseEntity.ok(payment);
    }

    @GetMapping("/payments/{paymentId}/status")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:payment-status')")
    @Operation(summary = "Get payment status")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId) {
        
        String userId = jwt.getSubject();
        log.debug("Checking payment status {} for user: {}", paymentId, userId);
        
        PaymentStatusResponse status = billPaymentService.getPaymentStatus(userId, paymentId);
        return ResponseEntity.ok(status);
    }

    @PostMapping("/payments/{paymentId}/cancel")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:payment-cancel')")
    @Operation(summary = "Cancel a payment")
    public ResponseEntity<Void> cancelPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId,
            @RequestBody(required = false) CancelPaymentRequest request) {
        
        String userId = jwt.getSubject();
        log.info("Cancelling payment {} for user: {}", paymentId, userId);
        
        billPaymentService.cancelPayment(userId, paymentId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/payments/{paymentId}/receipt")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:receipt-download')")
    @Operation(summary = "Download payment receipt")
    public ResponseEntity<byte[]> downloadReceipt(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId,
            @RequestParam(defaultValue = "PDF") String format) {
        
        String userId = jwt.getSubject();
        log.debug("Downloading receipt {} for user: {} in format: {}", paymentId, userId, format);
        
        byte[] receipt = billPaymentService.generateReceipt(userId, paymentId, format);
        return ResponseEntity.ok()
                .header("Content-Type", format.equalsIgnoreCase("PDF") ? "application/pdf" : "image/png")
                .header("Content-Disposition", "attachment; filename=receipt_" + paymentId + "." + format.toLowerCase())
                .body(receipt);
    }

    // ============== Auto-pay Settings ==============
    
    @PostMapping("/autopay/setup")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:autopay-setup')")
    @Operation(summary = "Setup auto-pay for a bill")
    public ResponseEntity<AutoPayResponse> setupAutoPay(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SetupAutoPayRequest request) {
        
        String userId = jwt.getSubject();
        log.info("Setting up auto-pay for user: {} - account: {}", userId, request.getAccountId());
        
        AutoPayResponse autoPay = billPaymentService.setupAutoPay(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(autoPay);
    }

    @GetMapping("/autopay")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:autopay-read')")
    @Operation(summary = "Get auto-pay settings")
    public ResponseEntity<List<AutoPayResponse>> getAutoPaySettings(
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        log.debug("Fetching auto-pay settings for user: {}", userId);
        
        List<AutoPayResponse> settings = billPaymentService.getAutoPaySettings(userId);
        return ResponseEntity.ok(settings);
    }

    @DeleteMapping("/autopay/{autoPayId}")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:autopay-cancel')")
    @Operation(summary = "Cancel auto-pay")
    public ResponseEntity<Void> cancelAutoPay(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID autoPayId) {
        
        String userId = jwt.getSubject();
        log.info("Cancelling auto-pay {} for user: {}", autoPayId, userId);
        
        billPaymentService.cancelAutoPay(userId, autoPayId);
        return ResponseEntity.noContent().build();
    }

    // ============== Reports and Analytics ==============
    
    @GetMapping("/reports/summary")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:reports')")
    @Operation(summary = "Get bill payment summary report")
    public ResponseEntity<BillPaymentSummaryReport> getPaymentSummaryReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        
        String userId = jwt.getSubject();
        log.debug("Generating payment summary report for user: {} from {} to {}", userId, fromDate, toDate);
        
        BillPaymentSummaryReport report = billPaymentService.generateSummaryReport(userId, fromDate, toDate);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/analytics/spending")
    @PreAuthorize("hasAuthority('SCOPE_bill-payment:analytics')")
    @Operation(summary = "Get spending analytics")
    public ResponseEntity<SpendingAnalytics> getSpendingAnalytics(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "MONTHLY") String period) {
        
        String userId = jwt.getSubject();
        log.debug("Fetching spending analytics for user: {} - period: {}", userId, period);
        
        SpendingAnalytics analytics = billPaymentService.getSpendingAnalytics(userId, period);
        return ResponseEntity.ok(analytics);
    }
}