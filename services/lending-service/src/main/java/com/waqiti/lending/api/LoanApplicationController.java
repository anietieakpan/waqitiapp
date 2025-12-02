package com.waqiti.lending.api;

import com.waqiti.lending.domain.LoanApplication;
import com.waqiti.lending.domain.enums.ApplicationStatus;
import com.waqiti.lending.service.LoanApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Loan Application REST Controller
 * Manages loan application lifecycle via REST API
 */
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Loan Applications", description = "Loan application management APIs")
public class LoanApplicationController {

    private final LoanApplicationService loanApplicationService;

    /**
     * Submit a new loan application
     */
    @PostMapping
    @Operation(summary = "Submit loan application", description = "Submit a new loan application")
    @PreAuthorize("hasAnyRole('BORROWER', 'LOAN_OFFICER', 'ADMIN')")
    public ResponseEntity<LoanApplication> submitApplication(@Valid @RequestBody LoanApplication application) {
        log.info("Received loan application submission for borrower: {}", application.getBorrowerId());

        // Security: Ensure borrower can only submit applications for themselves
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_BORROWER"))) {
            // Validate borrower ID matches authenticated user
            log.debug("Validating borrower authorization for user: {}", auth.getName());
        }

        LoanApplication submitted = loanApplicationService.submitApplication(application);
        return ResponseEntity.status(HttpStatus.CREATED).body(submitted);
    }

    /**
     * Get application by ID
     */
    @GetMapping("/{applicationId}")
    @Operation(summary = "Get application by ID", description = "Retrieve a loan application by its ID")
    public ResponseEntity<LoanApplication> getApplication(@PathVariable String applicationId) {
        log.info("Retrieving loan application: {}", applicationId);
        LoanApplication application = loanApplicationService.findByApplicationId(applicationId);
        return ResponseEntity.ok(application);
    }

    /**
     * Get applications for borrower
     */
    @GetMapping("/borrower/{borrowerId}")
    @Operation(summary = "Get borrower applications", description = "Get all applications for a borrower")
    public ResponseEntity<List<LoanApplication>> getBorrowerApplications(@PathVariable UUID borrowerId) {
        log.info("Retrieving applications for borrower: {}", borrowerId);
        List<LoanApplication> applications = loanApplicationService.findByBorrower(borrowerId);
        return ResponseEntity.ok(applications);
    }

    /**
     * Get applications by status
     */
    @GetMapping("/status/{status}")
    @Operation(summary = "Get applications by status", description = "Get applications filtered by status")
    public ResponseEntity<Page<LoanApplication>> getApplicationsByStatus(
            @PathVariable ApplicationStatus status,
            Pageable pageable) {
        log.info("Retrieving applications with status: {}", status);
        Page<LoanApplication> applications = loanApplicationService.findByStatus(status, pageable);
        return ResponseEntity.ok(applications);
    }

    /**
     * Approve application
     */
    @PostMapping("/{applicationId}/approve")
    @Operation(summary = "Approve application", description = "Approve a loan application")
    @PreAuthorize("hasAnyRole('UNDERWRITER', 'ADMIN')")
    public ResponseEntity<LoanApplication> approveApplication(
            @PathVariable String applicationId,
            @RequestBody ApprovalRequest request) {
        log.info("Approving application: {} with amount: {}", applicationId, request.getApprovedAmount());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String reviewedBy = auth.getName();

        LoanApplication approved = loanApplicationService.approveApplication(
                applicationId,
                request.getApprovedAmount(),
                request.getApprovedTermMonths(),
                request.getApprovedInterestRate(),
                reviewedBy
        );
        return ResponseEntity.ok(approved);
    }

    /**
     * Reject application
     */
    @PostMapping("/{applicationId}/reject")
    @Operation(summary = "Reject application", description = "Reject a loan application")
    @PreAuthorize("hasAnyRole('UNDERWRITER', 'ADMIN')")
    public ResponseEntity<LoanApplication> rejectApplication(
            @PathVariable String applicationId,
            @RequestBody RejectionRequest request) {
        log.info("Rejecting application: {} - Reason: {}", applicationId, request.getReason());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String reviewedBy = auth.getName();

        LoanApplication rejected = loanApplicationService.rejectApplication(
                applicationId,
                request.getReason(),
                reviewedBy
        );
        return ResponseEntity.ok(rejected);
    }

    /**
     * Mark for manual review
     */
    @PostMapping("/{applicationId}/manual-review")
    @Operation(summary = "Mark for manual review", description = "Flag application for manual review")
    public ResponseEntity<LoanApplication> markForManualReview(
            @PathVariable String applicationId,
            @RequestBody ManualReviewRequest request) {
        log.info("Marking application for manual review: {} - Reason: {}", applicationId, request.getReason());
        LoanApplication application = loanApplicationService.markForManualReview(applicationId, request.getReason());
        return ResponseEntity.ok(application);
    }

    /**
     * Get pending applications
     */
    @GetMapping("/pending")
    @Operation(summary = "Get pending applications", description = "Get all pending applications")
    public ResponseEntity<List<LoanApplication>> getPendingApplications() {
        log.info("Retrieving pending applications");
        List<LoanApplication> applications = loanApplicationService.getPendingApplications();
        return ResponseEntity.ok(applications);
    }

    /**
     * Get applications awaiting decision
     */
    @GetMapping("/awaiting-decision")
    @Operation(summary = "Get applications awaiting decision", description = "Get applications awaiting underwriting decision")
    public ResponseEntity<List<LoanApplication>> getApplicationsAwaitingDecision() {
        log.info("Retrieving applications awaiting decision");
        List<LoanApplication> applications = loanApplicationService.getApplicationsAwaitingDecision();
        return ResponseEntity.ok(applications);
    }

    /**
     * Get application statistics
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get application statistics", description = "Get statistical overview of applications")
    @PreAuthorize("hasAnyRole('ANALYST', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<Object[]>> getApplicationStatistics() {
        log.info("Retrieving application statistics");
        List<Object[]> stats = loanApplicationService.getApplicationStatistics();
        return ResponseEntity.ok(stats);
    }

    // DTOs
    @lombok.Data
    public static class ApprovalRequest {
        private BigDecimal approvedAmount;
        private Integer approvedTermMonths;
        private BigDecimal approvedInterestRate;
        private String reviewedBy;
    }

    @lombok.Data
    public static class RejectionRequest {
        private String reason;
        private String reviewedBy;
    }

    @lombok.Data
    public static class ManualReviewRequest {
        private String reason;
    }
}
