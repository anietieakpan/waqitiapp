package com.waqiti.payroll.controller;

import com.waqiti.payroll.service.ReportingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Controller for Payroll Tax Reporting
 * Provides endpoints for generating W-2, 1099, Form 941, and other tax reports
 */
@RestController
@RequestMapping("/api/v1/payroll/reports")
@RequiredArgsConstructor
@Slf4j
public class PayrollReportingController {

    private final ReportingService reportingService;

    /**
     * Generate W-2 report for an employee
     * GET /api/v1/payroll/reports/w2/{employeeId}/{year}
     */
    @GetMapping("/w2/{employeeId}/{year}")
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS_READ')")
    public ResponseEntity<ReportingService.W2Report> generateW2Report(
            @PathVariable String employeeId,
            @PathVariable int year) {

        log.info("REST API: Generate W-2 - Employee: {}, Year: {}", employeeId, year);

        ReportingService.W2Report report = reportingService.generateW2Report(employeeId, year);

        // Validate before returning
        if (!reportingService.validateW2Report(report)) {
            log.warn("W-2 validation failed for employee: {}, year: {}", employeeId, year);
        }

        return ResponseEntity.ok(report);
    }

    /**
     * Generate 1099-NEC report for a contractor
     * GET /api/v1/payroll/reports/1099/{contractorId}/{year}
     */
    @GetMapping("/1099/{contractorId}/{year}")
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS_READ')")
    public ResponseEntity<ReportingService.Form1099Report> generate1099Report(
            @PathVariable String contractorId,
            @PathVariable int year) {

        log.info("REST API: Generate 1099-NEC - Contractor: {}, Year: {}", contractorId, year);

        ReportingService.Form1099Report report = reportingService.generate1099Report(contractorId, year);

        return ResponseEntity.ok(report);
    }

    /**
     * Generate Form 941 (Quarterly Federal Tax Return)
     * GET /api/v1/payroll/reports/941/{companyId}/{year}/{quarter}
     */
    @GetMapping("/941/{companyId}/{year}/{quarter}")
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS_READ')")
    public ResponseEntity<ReportingService.Form941Report> generateForm941Report(
            @PathVariable String companyId,
            @PathVariable int year,
            @PathVariable int quarter) {

        log.info("REST API: Generate Form 941 - Company: {}, Year: {}, Quarter: {}",
                 companyId, year, quarter);

        // Validate quarter (1-4)
        if (quarter < 1 || quarter > 4) {
            return ResponseEntity.badRequest().build();
        }

        ReportingService.Form941Report report =
            reportingService.generateForm941Report(companyId, year, quarter);

        // Validate before returning
        if (!reportingService.validateForm941Report(report)) {
            log.warn("Form 941 validation failed for company: {}, year: {}, quarter: {}",
                     companyId, year, quarter);
        }

        return ResponseEntity.ok(report);
    }

    /**
     * Generate annual tax summary for a company
     * GET /api/v1/payroll/reports/annual/{companyId}/{year}
     */
    @GetMapping("/annual/{companyId}/{year}")
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS_READ')")
    public ResponseEntity<ReportingService.AnnualTaxSummary> generateAnnualTaxSummary(
            @PathVariable String companyId,
            @PathVariable int year) {

        log.info("REST API: Generate annual tax summary - Company: {}, Year: {}", companyId, year);

        ReportingService.AnnualTaxSummary summary =
            reportingService.generateAnnualTaxSummary(companyId, year);

        return ResponseEntity.ok(summary);
    }
}
