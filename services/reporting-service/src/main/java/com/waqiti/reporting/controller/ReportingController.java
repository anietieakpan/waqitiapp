package com.waqiti.reporting.controller;

import com.waqiti.reporting.dto.*;
import com.waqiti.reporting.service.FinancialReportingService;
import com.waqiti.reporting.domain.ReportDefinition;
import com.waqiti.reporting.domain.ReportExecution;
import com.waqiti.reporting.domain.ReportSchedule;
import com.waqiti.reporting.repository.ReportDefinitionRepository;
import com.waqiti.reporting.repository.ReportExecutionRepository;
import com.waqiti.reporting.repository.ReportScheduleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reporting", description = "Financial and regulatory reporting endpoints")
public class ReportingController {

    private final FinancialReportingService reportingService;
    private final ReportDefinitionRepository reportDefinitionRepository;
    private final ReportExecutionRepository reportExecutionRepository;
    private final ReportScheduleRepository reportScheduleRepository;

    @GetMapping("/dashboard/{dashboardType}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ANALYST')")
    @Operation(summary = "Get financial dashboard", description = "Retrieve financial dashboard data")
    public ResponseEntity<FinancialDashboardResponse> getFinancialDashboard(
            @PathVariable String dashboardType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String currency) {
        
        log.info("Fetching {} dashboard", dashboardType);
        FinancialDashboardResponse dashboard = reportingService.generateFinancialDashboard(
            dashboardType, startDate, endDate, currency);
        
        return ResponseEntity.ok(dashboard);
    }

    @PostMapping("/regulatory")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER')")
    @Operation(summary = "Generate regulatory report", description = "Generate regulatory compliance report")
    public ResponseEntity<RegulatoryReportResult> generateRegulatoryReport(
            @Valid @RequestBody RegulatoryReportRequest request) {
        
        log.info("Generating {} report", request.getReportType());
        RegulatoryReportResult result = reportingService.generateRegulatoryReport(request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/account-statement/{accountId}")
    @PreAuthorize("hasRole('USER') and @securityService.isAccountOwner(#accountId, authentication.principal.id)")
    @Operation(summary = "Generate account statement", description = "Generate account statement for specified period")
    public ResponseEntity<byte[]> generateAccountStatement(
            @PathVariable UUID accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "PDF") String format) {
        
        log.info("Generating account statement for account: {}", accountId);
        
        StatementDocument statement = reportingService.generateAccountStatement(
            accountId, startDate, endDate, format);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(getMediaType(format));
        headers.setContentDispositionFormData("attachment", 
            String.format("statement_%s_%s_%s.%s", accountId, startDate, endDate, format.toLowerCase()));
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(statement.getContent());
    }

    @GetMapping("/mis/{reportType}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Generate MIS report", description = "Generate Management Information System report")
    public ResponseEntity<MISDocument> generateMISReport(
            @PathVariable String reportType,
            @RequestParam(required = false) String managementLevel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        log.info("Generating MIS report: {} for level: {}", reportType, managementLevel);
        
        MISDocument report = reportingService.generateMISReport(
            reportType, managementLevel, date);
        
        return ResponseEntity.ok(report);
    }

    @PostMapping("/analytics/transactions")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    @Operation(summary = "Analyze transactions", description = "Perform transaction analytics")
    public ResponseEntity<Map<String, Object>> analyzeTransactions(
            @RequestBody TransactionAnalyticsRequest request) {
        
        log.info("Analyzing transactions with type: {}", request.getAnalysisType());
        
        Map<String, Object> analytics = reportingService.generateTransactionAnalytics(
            request.getAnalysisType(),
            request.getStartDate(),
            request.getEndDate(),
            request.getParameters()
        );
        
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/risk/{reportType}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RISK_MANAGER')")
    @Operation(summary = "Generate risk report", description = "Generate risk management report")
    public ResponseEntity<RiskReport> generateRiskReport(
            @PathVariable String reportType,
            @RequestParam(required = false) Map<String, Object> parameters) {
        
        log.info("Generating risk report: {}", reportType);
        
        RiskReport report = reportingService.generateRiskReport(reportType, parameters);
        
        return ResponseEntity.ok(report);
    }

    @GetMapping("/definitions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "List report definitions", description = "Get all available report definitions")
    public ResponseEntity<Page<ReportDefinition>> getReportDefinitions(Pageable pageable) {
        Page<ReportDefinition> definitions = reportDefinitionRepository.findAll(pageable);
        return ResponseEntity.ok(definitions);
    }

    @PostMapping("/definitions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create report definition", description = "Create new report definition")
    public ResponseEntity<ReportDefinition> createReportDefinition(
            @Valid @RequestBody ReportDefinition definition) {
        
        log.info("Creating new report definition: {}", definition.getName());
        ReportDefinition saved = reportDefinitionRepository.save(definition);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/executions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "List report executions", description = "Get report execution history")
    public ResponseEntity<Page<ReportExecution>> getReportExecutions(
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        
        Page<ReportExecution> executions;
        if (reportType != null || status != null) {
            executions = reportExecutionRepository.findByReportTypeAndStatus(reportType, status, pageable);
        } else {
            executions = reportExecutionRepository.findAll(pageable);
        }
        
        return ResponseEntity.ok(executions);
    }

    @GetMapping("/executions/{executionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Get report execution", description = "Get specific report execution details")
    public ResponseEntity<ReportExecution> getReportExecution(@PathVariable UUID executionId) {
        return reportExecutionRepository.findById(executionId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/executions/{executionId}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    @Operation(summary = "Download report", description = "Download generated report")
    public ResponseEntity<byte[]> downloadReport(@PathVariable UUID executionId) {
        
        ReportExecution execution = reportExecutionRepository.findById(executionId)
            .orElseThrow(() -> new ReportNotFoundException("Report execution not found: " + executionId));
        
        if (!"COMPLETED".equals(execution.getStatus())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        
        byte[] reportContent = reportingService.getReportContent(executionId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", execution.getFileName());
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(reportContent);
    }

    @GetMapping("/schedules")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "List report schedules", description = "Get all report schedules")
    public ResponseEntity<Page<ReportSchedule>> getReportSchedules(
            @RequestParam(required = false) Boolean active,
            Pageable pageable) {
        
        Page<ReportSchedule> schedules;
        if (active != null) {
            schedules = reportScheduleRepository.findByActive(active, pageable);
        } else {
            schedules = reportScheduleRepository.findAll(pageable);
        }
        
        return ResponseEntity.ok(schedules);
    }

    @PostMapping("/schedules")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create report schedule", description = "Schedule recurring report generation")
    public ResponseEntity<ReportSchedule> createReportSchedule(
            @Valid @RequestBody ReportSchedule schedule) {
        
        log.info("Creating report schedule for: {}", schedule.getReportType());
        ReportSchedule saved = reportScheduleRepository.save(schedule);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/schedules/{scheduleId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update report schedule", description = "Update existing report schedule")
    public ResponseEntity<ReportSchedule> updateReportSchedule(
            @PathVariable UUID scheduleId,
            @Valid @RequestBody ReportSchedule schedule) {
        
        return reportScheduleRepository.findById(scheduleId)
            .map(existing -> {
                schedule.setId(scheduleId);
                return ResponseEntity.ok(reportScheduleRepository.save(schedule));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/schedules/{scheduleId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete report schedule", description = "Delete report schedule")
    public ResponseEntity<Void> deleteReportSchedule(@PathVariable UUID scheduleId) {
        
        return reportScheduleRepository.findById(scheduleId)
            .map(schedule -> {
                reportScheduleRepository.delete(schedule);
                return ResponseEntity.<Void>noContent().build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/execute/{reportType}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Execute report", description = "Manually trigger report generation")
    public ResponseEntity<ReportExecution> executeReport(
            @PathVariable String reportType,
            @RequestBody Map<String, Object> parameters) {
        
        log.info("Manually executing report: {}", reportType);
        
        ReportExecution execution = reportingService.executeReport(reportType, parameters);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(execution);
    }

    private MediaType getMediaType(String format) {
        return switch (format.toUpperCase()) {
            case "PDF" -> MediaType.APPLICATION_PDF;
            case "EXCEL", "XLSX" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "CSV" -> MediaType.parseMediaType("text/csv");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}