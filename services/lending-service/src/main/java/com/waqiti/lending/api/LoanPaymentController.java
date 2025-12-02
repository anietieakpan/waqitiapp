package com.waqiti.lending.api;

import com.waqiti.lending.domain.LoanPayment;
import com.waqiti.lending.service.LoanPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Loan Payment REST Controller
 * Handles payment processing via REST API
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments", description = "Loan payment processing APIs")
public class LoanPaymentController {

    private final LoanPaymentService loanPaymentService;

    /**
     * Process a loan payment
     */
    @PostMapping
    @Operation(summary = "Process payment", description = "Process a loan payment")
    @PreAuthorize("hasAnyRole('BORROWER', 'PAYMENT_PROCESSOR', 'ADMIN')")
    public ResponseEntity<LoanPayment> processPayment(@Valid @RequestBody PaymentRequest request) {
        log.info("Processing payment for loan: {} - Amount: {}", request.getLoanId(), request.getPaymentAmount());

        LoanPayment payment = loanPaymentService.processPayment(
                request.getLoanId(),
                request.getBorrowerId(),
                request.getPaymentAmount(),
                request.getPaymentMethod(),
                request.isAutopay()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    /**
     * Process early payoff
     */
    @PostMapping("/payoff")
    @Operation(summary = "Process payoff", description = "Process early loan payoff")
    public ResponseEntity<LoanPayment> processPayoff(@Valid @RequestBody PayoffRequest request) {
        log.info("Processing payoff for loan: {}", request.getLoanId());

        LoanPayment payment = loanPaymentService.processEarlyPayoff(
                request.getLoanId(),
                request.getBorrowerId(),
                request.getPaymentMethod()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    /**
     * Get payment by ID
     */
    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment by ID", description = "Retrieve a payment by its ID")
    public ResponseEntity<LoanPayment> getPayment(@PathVariable String paymentId) {
        log.info("Retrieving payment: {}", paymentId);
        LoanPayment payment = loanPaymentService.findByPaymentId(paymentId);
        return ResponseEntity.ok(payment);
    }

    /**
     * Get payments for loan
     */
    @GetMapping("/loan/{loanId}")
    @Operation(summary = "Get loan payments", description = "Get all payments for a loan")
    public ResponseEntity<List<LoanPayment>> getLoanPayments(@PathVariable String loanId) {
        log.info("Retrieving payments for loan: {}", loanId);
        List<LoanPayment> payments = loanPaymentService.findByLoan(loanId);
        return ResponseEntity.ok(payments);
    }

    /**
     * Get payments for borrower
     */
    @GetMapping("/borrower/{borrowerId}")
    @Operation(summary = "Get borrower payments", description = "Get all payments for a borrower")
    public ResponseEntity<List<LoanPayment>> getBorrowerPayments(@PathVariable UUID borrowerId) {
        log.info("Retrieving payments for borrower: {}", borrowerId);
        List<LoanPayment> payments = loanPaymentService.findByBorrower(borrowerId);
        return ResponseEntity.ok(payments);
    }

    /**
     * Get failed payments
     */
    @GetMapping("/failed")
    @Operation(summary = "Get failed payments", description = "Get all failed payments")
    public ResponseEntity<List<LoanPayment>> getFailedPayments() {
        log.info("Retrieving failed payments");
        List<LoanPayment> payments = loanPaymentService.getFailedPayments();
        return ResponseEntity.ok(payments);
    }

    /**
     * Calculate total payments for loan
     */
    @GetMapping("/loan/{loanId}/total")
    @Operation(summary = "Get total payments", description = "Calculate total payments made for a loan")
    public ResponseEntity<TotalPaymentResponse> getTotalPayments(@PathVariable String loanId) {
        log.info("Calculating total payments for loan: {}", loanId);
        BigDecimal total = loanPaymentService.calculateTotalPaymentsForLoan(loanId);
        return ResponseEntity.ok(new TotalPaymentResponse(total));
    }

    /**
     * Calculate total interest paid for loan
     */
    @GetMapping("/loan/{loanId}/interest")
    @Operation(summary = "Get total interest", description = "Calculate total interest paid for a loan")
    public ResponseEntity<TotalPaymentResponse> getTotalInterest(@PathVariable String loanId) {
        log.info("Calculating total interest for loan: {}", loanId);
        BigDecimal total = loanPaymentService.calculateTotalInterestPaid(loanId);
        return ResponseEntity.ok(new TotalPaymentResponse(total));
    }

    /**
     * Mark payment as failed
     */
    @PostMapping("/{paymentId}/fail")
    @Operation(summary = "Mark payment failed", description = "Mark a payment as failed")
    @PreAuthorize("hasAnyRole('PAYMENT_PROCESSOR', 'ADMIN')")
    public ResponseEntity<LoanPayment> markPaymentAsFailed(
            @PathVariable String paymentId,
            @RequestBody FailureRequest request) {
        log.info("Marking payment as failed: {} - Reason: {}", paymentId, request.getReason());
        LoanPayment payment = loanPaymentService.markPaymentAsFailed(paymentId, request.getReason());
        return ResponseEntity.ok(payment);
    }

    // DTOs
    @lombok.Data
    public static class PaymentRequest {
        private String loanId;
        private UUID borrowerId;
        private BigDecimal paymentAmount;
        private String paymentMethod;
        private boolean autopay;
    }

    @lombok.Data
    public static class PayoffRequest {
        private String loanId;
        private UUID borrowerId;
        private String paymentMethod;
    }

    @lombok.Data
    public static class FailureRequest {
        private String reason;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class TotalPaymentResponse {
        private BigDecimal total;
    }
}
