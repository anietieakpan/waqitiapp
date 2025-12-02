package com.waqiti.reporting.controller;

import com.waqiti.reporting.dto.*;
import com.waqiti.reporting.service.FinancialReportingService;
import com.waqiti.reporting.service.ReportGenerationService;
import com.waqiti.reporting.service.ReportSchedulingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Secured REST controller for reporting operations with Keycloak authentication
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Validated
@Tag(name = "Reporting", description = "Report generation and management APIs")
@SecurityRequirement(name = "bearer-jwt")
public class SecureReportingController {

    private final FinancialReportingService financialReportingService;
    private final ReportGenerationService reportGenerationService;
    private final ReportSchedulingService reportSchedulingService;

    // ============== Report Generation ==============

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('SCOPE_reporting:generate')")
    @Operation(summary = "Generate a report synchronously")
    public ResponseEntity<ReportGenerationResponse> generateReport(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReportGenerationRequest request) {
        
        String userId = jwt.getSubject();
        log.info("Generating report for user: {} - type: {}", userId, request.getReportType());
        
        ReportGenerationResponse response = reportGenerationService.generateReport(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/generate/async")
    @PreAuthorize("hasAuthority('SCOPE_reporting:generate-async')")
    @Operation(summary = "Generate a report asynchronously")
    public ResponseEntity<AsyncReportResponse> generateReportAsync(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReportGenerationRequest request) {
        
        String userId = jwt.getSubject();
        log.info("Generating async report for user: {} - type: {}", userId, request.getReportType());
        
        AsyncReportResponse response = reportGenerationService.generateReportAsync(userId, request);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/status/{reportId}")
    @PreAuthorize("hasAuthority('SCOPE_reporting:status')")
    @Operation(summary = "Get report generation status")
    public ResponseEntity<ReportStatusResponse> getReportStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reportId) {
        
        String userId = jwt.getSubject();
        log.debug("Checking report status {} for user: {}", reportId, userId);
        
        ReportStatusResponse status = reportGenerationService.getReportStatus(userId, reportId);
        return ResponseEntity.ok(status);
    }

    // ============== Financial Reports ==============

    @GetMapping("/financial/statement")
    @PreAuthorize("hasAuthority('SCOPE_reporting:financial-statement')")
    @Operation(summary = "Generate financial statement")
    public ResponseEntity<FinancialStatementResponse> generateFinancialStatement(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "PDF") String format) {
        
        String userId = jwt.getSubject();
        log.info("Generating financial statement for user: {} from {} to {}", userId, startDate, endDate);
        
        FinancialStatementResponse statement = financialReportingService.generateFinancialStatement(
            userId, startDate, endDate, format);
        return ResponseEntity.ok(statement);
    }

    @GetMapping("/financial/balance-sheet")
    @PreAuthorize("hasAuthority('SCOPE_reporting:balance-sheet')")
    @Operation(summary = "Generate balance sheet")
    public ResponseEntity<BalanceSheetResponse> generateBalanceSheet(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(defaultValue = "DETAILED") String level) {
        
        String userId = jwt.getSubject();
        log.info("Generating balance sheet for user: {} as of {}", userId, asOfDate);
        
        BalanceSheetResponse balanceSheet = financialReportingService.generateBalanceSheet(
            userId, asOfDate, level);
        return ResponseEntity.ok(balanceSheet);
    }

    @GetMapping("/financial/income-statement")
    @PreAuthorize("hasAuthority('SCOPE_reporting:income-statement')")
    @Operation(summary = "Generate income statement")
    public ResponseEntity<IncomeStatementResponse> generateIncomeStatement(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "MONTHLY") String period) {
        
        String userId = jwt.getSubject();
        log.info("Generating income statement for user: {} from {} to {}", userId, startDate, endDate);
        
        IncomeStatementResponse incomeStatement = financialReportingService.generateIncomeStatement(
            userId, startDate, endDate, period);
        return ResponseEntity.ok(incomeStatement);
    }

    @GetMapping("/financial/cash-flow")
    @PreAuthorize("hasAuthority('SCOPE_reporting:cash-flow')")
    @Operation(summary = "Generate cash flow statement")
    public ResponseEntity<CashFlowStatementResponse> generateCashFlowStatement(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        String userId = jwt.getSubject();
        log.info("Generating cash flow statement for user: {} from {} to {}", userId, startDate, endDate);
        
        CashFlowStatementResponse cashFlow = financialReportingService.generateCashFlowStatement(
            userId, startDate, endDate);
        return ResponseEntity.ok(cashFlow);
    }

    // ============== Transaction Reports ==============

    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('SCOPE_reporting:transaction-report')")
    @Operation(summary = "Generate transaction report")
    public ResponseEntity<Page<TransactionReportEntry>> getTransactionReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String transactionType,
            Pageable pageable) {
        
        String userId = jwt.getSubject();
        log.debug("Generating transaction report for user: {}", userId);
        
        Page<TransactionReportEntry> report = reportGenerationService.getTransactionReport(
            userId, fromDate, toDate, accountId, transactionType, pageable);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/transactions/export")
    @PreAuthorize("hasAuthority('SCOPE_reporting:transaction-export')")
    @Operation(summary = "Export transaction report")
    public ResponseEntity<byte[]> exportTransactionReport(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TransactionExportRequest request) {
        
        String userId = jwt.getSubject();
        log.info("Exporting transaction report for user: {} - format: {}", userId, request.getFormat());
        
        byte[] exportData = reportGenerationService.exportTransactionReport(userId, request);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(getMediaType(request.getFormat()));
        headers.setContentDispositionFormData("attachment", 
            "transaction_report_" + LocalDateTime.now() + "." + request.getFormat().toLowerCase());
        
        return new ResponseEntity<>(exportData, headers, HttpStatus.OK);
    }

    // ============== Account Statements ==============

    @GetMapping("/statements/account")
    @PreAuthorize("hasAuthority('SCOPE_reporting:account-statement')")
    @Operation(summary = "Generate account statement")
    public ResponseEntity<AccountStatementResponse> generateAccountStatement(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "PDF") String format) {
        
        String userId = jwt.getSubject();
        log.info("Generating account statement for user: {} - account: {}", userId, accountId);
        
        AccountStatementResponse statement = reportGenerationService.generateAccountStatement(
            userId, accountId, fromDate, toDate, format);
        return ResponseEntity.ok(statement);
    }

    @PostMapping("/statements/email")
    @PreAuthorize("hasAuthority('SCOPE_reporting:statement-email')")
    @Operation(summary = "Email account statement")
    public ResponseEntity<EmailStatementResponse> emailAccountStatement(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmailStatementRequest request) {
        
        String userId = jwt.getSubject();
        log.info("Emailing account statement for user: {} to {}", userId, request.getRecipientEmail());
        
        EmailStatementResponse response = reportGenerationService.emailAccountStatement(userId, request);
        return ResponseEntity.ok(response);
    }

    // ============== Regulatory Reports ==============

    @GetMapping("/regulatory/aml")
    @PreAuthorize("hasAuthority('SCOPE_reporting:regulatory-aml')")
    @Operation(summary = "Generate AML report")
    public ResponseEntity<AMLReportResponse> generateAMLReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @RequestParam(required = false) String riskLevel) {
        
        String userId = jwt.getSubject();
        log.info("Generating AML report for user: {} - date: {}", userId, reportDate);
        
        AMLReportResponse amlReport = reportGenerationService.generateAMLReport(userId, reportDate, riskLevel);
        return ResponseEntity.ok(amlReport);
    }

    @PostMapping("/regulatory/submit")
    @PreAuthorize("hasAuthority('SCOPE_reporting:regulatory-submit')")
    @Operation(summary = "Submit regulatory report")
    public ResponseEntity<RegulatorySubmissionResponse> submitRegulatoryReport(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RegulatorySubmissionRequest request) {
        
        String userId = jwt.getSubject();
        log.info("Submitting regulatory report for user: {} - type: {}", userId, request.getReportType());
        
        RegulatorySubmissionResponse response = reportGenerationService.submitRegulatoryReport(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ============== Report Scheduling ==============

    @GetMapping("/schedules")
    @PreAuthorize("hasAuthority('SCOPE_reporting:schedule-list')")
    @Operation(summary = "Get report schedules")
    public ResponseEntity<List<ReportScheduleResponse>> getReportSchedules(
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        log.debug("Fetching report schedules for user: {}", userId);
        
        List<ReportScheduleResponse> schedules = reportSchedulingService.getReportSchedules(userId);
        return ResponseEntity.ok(schedules);
    }

    @PostMapping("/schedules")
    @PreAuthorize("hasAuthority('SCOPE_reporting:schedule-create')")
    @Operation(summary = "Create report schedule")
    public ResponseEntity<ReportScheduleResponse> createReportSchedule(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateReportScheduleRequest request) {
        
        String userId = jwt.getSubject();
        log.info("Creating report schedule for user: {} - type: {}", userId, request.getReportType());
        
        ReportScheduleResponse schedule = reportSchedulingService.createReportSchedule(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(schedule);
    }

    @PutMapping("/schedules/{scheduleId}")
    @PreAuthorize("hasAuthority('SCOPE_reporting:schedule-update')")
    @Operation(summary = "Update report schedule")
    public ResponseEntity<ReportScheduleResponse> updateReportSchedule(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID scheduleId,
            @Valid @RequestBody UpdateReportScheduleRequest request) {
        
        String userId = jwt.getSubject();
        log.info("Updating report schedule {} for user: {}", scheduleId, userId);
        
        ReportScheduleResponse schedule = reportSchedulingService.updateReportSchedule(userId, scheduleId, request);
        return ResponseEntity.ok(schedule);
    }

    @DeleteMapping("/schedules/{scheduleId}")
    @PreAuthorize("hasAuthority('SCOPE_reporting:schedule-delete')")
    @Operation(summary = "Delete report schedule")
    public ResponseEntity<Void> deleteReportSchedule(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID scheduleId) {
        
        String userId = jwt.getSubject();
        log.info("Deleting report schedule {} for user: {}", scheduleId, userId);
        
        reportSchedulingService.deleteReportSchedule(userId, scheduleId);
        return ResponseEntity.noContent().build();
    }

    // ============== Report Downloads ==============

    @GetMapping("/download/{reportId}")
    @PreAuthorize("hasAuthority('SCOPE_reporting:download')")
    @Operation(summary = "Download report")
    public ResponseEntity<byte[]> downloadReport(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reportId) {
        
        String userId = jwt.getSubject();
        log.info("Downloading report {} for user: {}", reportId, userId);
        
        ReportDownloadData downloadData = reportGenerationService.downloadReport(userId, reportId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(downloadData.getContentType()));
        headers.setContentDispositionFormData("attachment", downloadData.getFileName());
        
        return new ResponseEntity<>(downloadData.getData(), headers, HttpStatus.OK);
    }

    @GetMapping("/preview/{reportId}")
    @PreAuthorize("hasAuthority('SCOPE_reporting:preview')")
    @Operation(summary = "Preview report")
    public ResponseEntity<ReportPreviewResponse> previewReport(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reportId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) {
        
        String userId = jwt.getSubject();
        log.debug("Previewing report {} for user: {} - page: {}", reportId, userId, page);
        
        ReportPreviewResponse preview = reportGenerationService.previewReport(userId, reportId, page, size);
        return ResponseEntity.ok(preview);
    }

    // ============== Helper Methods ==============

    private MediaType getMediaType(String format) {
        switch (format.toUpperCase()) {
            case "PDF":
                return MediaType.APPLICATION_PDF;
            case "EXCEL":
            case "XLS":
            case "XLSX":
                return MediaType.parseMediaType("application/vnd.ms-excel");
            case "CSV":
                return MediaType.parseMediaType("text/csv");
            case "JSON":
                return MediaType.APPLICATION_JSON;
            default:
                return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}