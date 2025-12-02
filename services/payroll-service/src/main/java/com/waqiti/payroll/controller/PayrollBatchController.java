package com.waqiti.payroll.controller;

import com.waqiti.payroll.service.PayrollProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDate;

/**
 * REST API Controller for Payroll Batch Operations
 * Provides endpoints for submitting, monitoring, and managing payroll batches
 */
@RestController
@RequestMapping("/api/v1/payroll/batches")
@RequiredArgsConstructor
@Slf4j
public class PayrollBatchController {

    private final PayrollProcessingService payrollProcessingService;

    /**
     * Submit a new payroll batch for processing
     * POST /api/v1/payroll/batches
     */
    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_SUBMIT')")
    public ResponseEntity<PayrollProcessingService.PayrollBatchSubmissionResult> submitPayrollBatch(
            @Valid @RequestBody PayrollProcessingService.PayrollBatchRequest request) {

        log.info("REST API: Submit payroll batch - Company: {}, Employees: {}",
                 request.getCompanyId(), request.getEmployees().size());

        PayrollProcessingService.PayrollBatchSubmissionResult result =
            payrollProcessingService.submitPayrollBatch(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    /**
     * Get payroll batch status
     * GET /api/v1/payroll/batches/{batchId}
     */
    @GetMapping("/{batchId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    public ResponseEntity<PayrollProcessingService.PayrollBatchStatus> getBatchStatus(
            @PathVariable String batchId) {

        log.info("REST API: Get batch status - Batch: {}", batchId);

        PayrollProcessingService.PayrollBatchStatus status =
            payrollProcessingService.getBatchStatus(batchId);

        return ResponseEntity.ok(status);
    }

    /**
     * Retry a failed payroll batch
     * POST /api/v1/payroll/batches/{batchId}/retry
     */
    @PostMapping("/{batchId}/retry")
    @PreAuthorize("hasAuthority('PAYROLL_SUBMIT')")
    public ResponseEntity<PayrollProcessingService.PayrollBatchSubmissionResult> retryBatch(
            @PathVariable String batchId) {

        log.info("REST API: Retry payroll batch - Batch: {}", batchId);

        PayrollProcessingService.PayrollBatchSubmissionResult result =
            payrollProcessingService.retryPayrollBatch(batchId);

        return ResponseEntity.ok(result);
    }

    /**
     * Cancel a pending payroll batch
     * DELETE /api/v1/payroll/batches/{batchId}
     */
    @DeleteMapping("/{batchId}")
    @PreAuthorize("hasAuthority('PAYROLL_CANCEL')")
    public ResponseEntity<Void> cancelBatch(
            @PathVariable String batchId,
            @RequestParam(required = false, defaultValue = "User requested cancellation") String reason) {

        log.info("REST API: Cancel payroll batch - Batch: {}, Reason: {}", batchId, reason);

        payrollProcessingService.cancelPayrollBatch(batchId, reason);

        return ResponseEntity.noContent().build();
    }

    /**
     * Approve a payroll batch (if approval workflow enabled)
     * POST /api/v1/payroll/batches/{batchId}/approve
     */
    @PostMapping("/{batchId}/approve")
    @PreAuthorize("hasAuthority('PAYROLL_APPROVE')")
    public ResponseEntity<Void> approveBatch(
            @PathVariable String batchId,
            @RequestParam String approvedBy) {

        log.info("REST API: Approve payroll batch - Batch: {}, Approver: {}", batchId, approvedBy);

        payrollProcessingService.approvePayrollBatch(batchId, approvedBy);

        return ResponseEntity.ok().build();
    }

    /**
     * Get payroll statistics for a company
     * GET /api/v1/payroll/batches/companies/{companyId}/statistics
     */
    @GetMapping("/companies/{companyId}/statistics")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    public ResponseEntity<PayrollProcessingService.PayrollStatistics> getCompanyStatistics(
            @PathVariable String companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("REST API: Get company statistics - Company: {}, Period: {} to {}",
                 companyId, startDate, endDate);

        PayrollProcessingService.PayrollStatistics statistics =
            payrollProcessingService.getCompanyPayrollStatistics(companyId, startDate, endDate);

        return ResponseEntity.ok(statistics);
    }
}
