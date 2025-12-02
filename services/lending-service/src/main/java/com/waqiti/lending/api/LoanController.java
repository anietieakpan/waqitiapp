package com.waqiti.lending.api;

import com.waqiti.lending.domain.Loan;
import com.waqiti.lending.domain.enums.LoanStatus;
import com.waqiti.lending.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Loan REST Controller
 * Manages loan accounts via REST API
 */
@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Loans", description = "Loan account management APIs")
public class LoanController {

    private final LoanService loanService;

    /**
     * Get loan by ID
     */
    @GetMapping("/{loanId}")
    @Operation(summary = "Get loan by ID", description = "Retrieve a loan by its ID")
    public ResponseEntity<Loan> getLoan(@PathVariable String loanId) {
        log.info("Retrieving loan: {}", loanId);
        Loan loan = loanService.findByLoanId(loanId);
        return ResponseEntity.ok(loan);
    }

    /**
     * Get loans for borrower
     */
    @GetMapping("/borrower/{borrowerId}")
    @Operation(summary = "Get borrower loans", description = "Get all loans for a borrower")
    public ResponseEntity<List<Loan>> getBorrowerLoans(@PathVariable UUID borrowerId) {
        log.info("Retrieving loans for borrower: {}", borrowerId);
        List<Loan> loans = loanService.findByBorrower(borrowerId);
        return ResponseEntity.ok(loans);
    }

    /**
     * Get loans by status
     */
    @GetMapping("/status/{status}")
    @Operation(summary = "Get loans by status", description = "Get loans filtered by status")
    public ResponseEntity<Page<Loan>> getLoansByStatus(@PathVariable LoanStatus status, Pageable pageable) {
        log.info("Retrieving loans with status: {}", status);
        Page<Loan> loans = loanService.findByStatus(status, pageable);
        return ResponseEntity.ok(loans);
    }

    /**
     * Get active loans
     */
    @GetMapping("/active")
    @Operation(summary = "Get active loans", description = "Get all active loans")
    public ResponseEntity<List<Loan>> getActiveLoans() {
        log.info("Retrieving active loans");
        List<Loan> loans = loanService.getActiveLoans();
        return ResponseEntity.ok(loans);
    }

    /**
     * Get delinquent loans
     */
    @GetMapping("/delinquent")
    @Operation(summary = "Get delinquent loans", description = "Get all delinquent loans")
    public ResponseEntity<List<Loan>> getDelinquentLoans() {
        log.info("Retrieving delinquent loans");
        List<Loan> loans = loanService.getDelinquentLoans();
        return ResponseEntity.ok(loans);
    }

    /**
     * Get loans due today
     */
    @GetMapping("/due-today")
    @Operation(summary = "Get loans due today", description = "Get loans with payments due today")
    public ResponseEntity<List<Loan>> getLoansDueToday() {
        log.info("Retrieving loans due today");
        List<Loan> loans = loanService.getLoansDueToday();
        return ResponseEntity.ok(loans);
    }

    /**
     * Update loan status
     */
    @PutMapping("/{loanId}/status")
    @Operation(summary = "Update loan status", description = "Update the status of a loan")
    public ResponseEntity<Loan> updateLoanStatus(@PathVariable String loanId, @RequestBody StatusUpdateRequest request) {
        log.info("Updating loan status: {} to {}", loanId, request.getNewStatus());
        Loan updated = loanService.updateStatus(loanId, request.getNewStatus());
        return ResponseEntity.ok(updated);
    }

    /**
     * Mark loan as delinquent
     */
    @PostMapping("/{loanId}/mark-delinquent")
    @Operation(summary = "Mark loan delinquent", description = "Mark a loan as delinquent")
    public ResponseEntity<Loan> markDelinquent(@PathVariable String loanId, @RequestBody DelinquencyRequest request) {
        log.info("Marking loan as delinquent: {} - {} days past due", loanId, request.getDaysPastDue());
        Loan loan = loanService.markDelinquent(loanId, request.getDaysPastDue());
        return ResponseEntity.ok(loan);
    }

    /**
     * Charge off loan
     */
    @PostMapping("/{loanId}/charge-off")
    @Operation(summary = "Charge off loan", description = "Charge off a loan as uncollectible")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Loan> chargeOffLoan(@PathVariable String loanId) {
        log.info("Charging off loan: {}", loanId);
        Loan loan = loanService.chargeOffLoan(loanId);
        return ResponseEntity.ok(loan);
    }

    /**
     * Get total outstanding balance
     */
    @GetMapping("/portfolio/total-outstanding")
    @Operation(summary = "Get total outstanding", description = "Get total outstanding balance across all loans")
    public ResponseEntity<BalanceResponse> getTotalOutstandingBalance() {
        log.info("Calculating total outstanding balance");
        BigDecimal total = loanService.calculateTotalOutstandingBalance();
        return ResponseEntity.ok(new BalanceResponse(total));
    }

    /**
     * Get borrower outstanding balance
     */
    @GetMapping("/borrower/{borrowerId}/outstanding")
    @Operation(summary = "Get borrower outstanding", description = "Get outstanding balance for a borrower")
    public ResponseEntity<BalanceResponse> getBorrowerOutstandingBalance(@PathVariable UUID borrowerId) {
        log.info("Calculating outstanding balance for borrower: {}", borrowerId);
        BigDecimal total = loanService.calculateBorrowerOutstandingBalance(borrowerId);
        return ResponseEntity.ok(new BalanceResponse(total));
    }

    /**
     * Count active loans for borrower
     */
    @GetMapping("/borrower/{borrowerId}/count")
    @Operation(summary = "Count borrower loans", description = "Count active loans for a borrower")
    public ResponseEntity<CountResponse> countBorrowerLoans(@PathVariable UUID borrowerId) {
        log.info("Counting active loans for borrower: {}", borrowerId);
        long count = loanService.countActiveLoansByBorrower(borrowerId);
        return ResponseEntity.ok(new CountResponse(count));
    }

    /**
     * Get portfolio statistics
     */
    @GetMapping("/portfolio/statistics")
    @Operation(summary = "Get portfolio statistics", description = "Get statistical overview of loan portfolio")
    @PreAuthorize("hasAnyRole('ANALYST', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<Object[]>> getPortfolioStatistics() {
        log.info("Retrieving loan portfolio statistics");
        List<Object[]> stats = loanService.getLoanPortfolioStatistics();
        return ResponseEntity.ok(stats);
    }

    // DTOs
    @lombok.Data
    public static class StatusUpdateRequest {
        private LoanStatus newStatus;
    }

    @lombok.Data
    public static class DelinquencyRequest {
        private int daysPastDue;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class BalanceResponse {
        private BigDecimal balance;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class CountResponse {
        private long count;
    }
}
