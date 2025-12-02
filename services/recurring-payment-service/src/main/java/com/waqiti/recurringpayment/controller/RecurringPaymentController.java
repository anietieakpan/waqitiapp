package com.waqiti.recurringpayment.controller;

import com.waqiti.recurringpayment.dto.CreateRecurringPaymentRequest;
import com.waqiti.recurringpayment.dto.RecurringPaymentDto;
import com.waqiti.recurringpayment.dto.RecurringPaymentDetailDTO;
import com.waqiti.recurringpayment.dto.RecurringExecutionDto;
import com.waqiti.recurringpayment.dto.UpdateRecurringPaymentRequest;
import com.waqiti.recurringpayment.dto.CreateTemplateRequest;
import com.waqiti.recurringpayment.dto.RecurringTemplateDto;
import com.waqiti.recurringpayment.dto.CreateFromTemplateRequest;
import com.waqiti.recurringpayment.service.RecurringPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.validation.Valid;

import java.security.Principal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/recurring-payments")
@RequiredArgsConstructor
@Validated
@Tag(name = "Recurring Payments", description = "API for managing recurring and scheduled payments")
public class RecurringPaymentController {
    
    private final RecurringPaymentService recurringPaymentService;
    
    @PostMapping
    @Operation(summary = "Create recurring payment", description = "Create a new recurring payment")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RecurringPaymentDto> createRecurringPayment(
            @Valid @RequestBody CreateRecurringPaymentRequest request,
            Principal principal) {
        log.info("Creating recurring payment for user: {}", principal.getName());
        RecurringPaymentDto response = recurringPaymentService.createRecurringPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/templates/{templateId}")
    @Operation(summary = "Create from template", description = "Create recurring payment from template")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RecurringPaymentDto> createFromTemplate(
            @PathVariable String templateId,
            @Valid @RequestBody CreateFromTemplateRequest request,
            Principal principal) {
        log.info("Creating recurring payment from template {} for user: {}", templateId, principal.getName());
        RecurringPaymentDto response = recurringPaymentService.createFromTemplate(templateId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping
    @Operation(summary = "Get user recurring payments", description = "Get paginated list of user's recurring payments")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<RecurringPaymentDto>> getRecurringPayments(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String frequency,
            @RequestParam(required = false) String currency,
            Principal principal) {
        log.info("Getting recurring payments for user: {}", principal.getName());
        Page<RecurringPaymentDto> payments = recurringPaymentService.getUserRecurringPayments(pageable);
        return ResponseEntity.ok(payments);
    }
    
    @GetMapping("/summary")
    @Operation(summary = "Get recurring payments summary", description = "Get summarized view of recurring payments")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<RecurringPaymentSummaryDto>> getRecurringPaymentsSummary(Principal principal) {
        log.info("Getting recurring payments summary for user: {}", principal.getName());
        List<RecurringPaymentSummaryDto> summary = recurringPaymentService.getRecurringPaymentsSummary();
        return ResponseEntity.ok(summary);
    }
    
    @GetMapping("/stats")
    @Operation(summary = "Get recurring payment statistics", description = "Get user's recurring payment statistics")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RecurringPaymentStatsDto> getRecurringPaymentStats(Principal principal) {
        log.info("Getting recurring payment stats for user: {}", principal.getName());
        RecurringPaymentStatsDto stats = recurringPaymentService.getRecurringPaymentStats();
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/{recurringId}")
    @Operation(summary = "Get recurring payment details", description = "Get details of a specific recurring payment")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RecurringPaymentDto> getRecurringPayment(
            @PathVariable String recurringId,
            Principal principal) {
        log.info("Getting recurring payment: {} for user: {}", recurringId, principal.getName());
        RecurringPaymentDto payment = recurringPaymentService.getRecurringPayment(recurringId);
        return ResponseEntity.ok(payment);
    }
    
    @PutMapping("/{recurringId}")
    @Operation(summary = "Update recurring payment", description = "Update an existing recurring payment")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RecurringPaymentDto> updateRecurringPayment(
            @PathVariable String recurringId,
            @Valid @RequestBody UpdateRecurringPaymentRequest request,
            Principal principal) {
        log.info("Updating recurring payment: {} for user: {}", recurringId, principal.getName());
        RecurringPaymentDto payment = recurringPaymentService.updateRecurringPayment(recurringId, request);
        return ResponseEntity.ok(payment);
    }
    
    @PostMapping("/{recurringId}/pause")
    @Operation(summary = "Pause recurring payment", description = "Pause a recurring payment")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RecurringPaymentDto> pauseRecurringPayment(
            @PathVariable String recurringId,
            Principal principal) {
        log.info("Pausing recurring payment: {} for user: {}", recurringId, principal.getName());
        RecurringPaymentDto payment = recurringPaymentService.pauseRecurringPayment(recurringId);
        return ResponseEntity.ok(payment);
    }
    
    @PostMapping("/{recurringId}/resume")
    @Operation(summary = "Resume recurring payment", description = "Resume a paused recurring payment")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RecurringPaymentDto> resumeRecurringPayment(
            @PathVariable String recurringId,
            Principal principal) {
        log.info("Resuming recurring payment: {} for user: {}", recurringId, principal.getName());
        RecurringPaymentDto payment = recurringPaymentService.resumeRecurringPayment(recurringId);
        return ResponseEntity.ok(payment);
    }
    
    @PostMapping("/{recurringId}/cancel")
    @Operation(summary = "Cancel recurring payment", description = "Cancel a recurring payment")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> cancelRecurringPayment(
            @PathVariable String recurringId,
            @RequestParam(required = false) String reason,
            Principal principal) {
        log.info("Cancelling recurring payment: {} for user: {}", recurringId, principal.getName());
        recurringPaymentService.cancelRecurringPayment(recurringId, 
            reason != null ? reason : "Cancelled by user");
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{recurringId}/execute")
    @Operation(summary = "Execute recurring payment manually", description = "Manually trigger execution of a recurring payment")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RecurringExecutionDto> executeRecurringPayment(
            @PathVariable String recurringId,
            Principal principal) {
        log.info("Manually executing recurring payment: {} for user: {}", recurringId, principal.getName());
        RecurringExecutionDto execution = recurringPaymentService.executeRecurringPayment(recurringId);
        return ResponseEntity.ok(execution);
    }
    
    @GetMapping("/{recurringId}/executions")
    @Operation(summary = "Get execution history", description = "Get execution history for a recurring payment")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<RecurringExecutionDto>> getRecurringExecutions(
            @PathVariable String recurringId,
            @PageableDefault(size = 20, sort = "executedAt", direction = Sort.Direction.DESC) Pageable pageable,
            Principal principal) {
        log.info("Getting execution history for recurring payment: {} for user: {}", recurringId, principal.getName());
        Page<RecurringExecutionDto> executions = recurringPaymentService.getRecurringExecutions(recurringId, pageable);
        return ResponseEntity.ok(executions);
    }
    
    // Template Management
    
    @PostMapping("/templates")
    @Operation(summary = "Create recurring payment template", description = "Create a template for recurring payments")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RecurringTemplateDto> createTemplate(
            @Valid @RequestBody CreateTemplateRequest request,
            Principal principal) {
        log.info("Creating recurring payment template for user: {}", principal.getName());
        RecurringTemplateDto template = recurringPaymentService.createTemplate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(template);
    }
    
    @GetMapping("/templates")
    @Operation(summary = "Get user templates", description = "Get user's recurring payment templates")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<RecurringTemplateDto>> getTemplates(
            @PageableDefault(size = 20, sort = "usageCount", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            Principal principal) {
        log.info("Getting templates for user: {}", principal.getName());
        Page<RecurringTemplateDto> templates = recurringPaymentService.getUserTemplates(pageable);
        return ResponseEntity.ok(templates);
    }
    
    @GetMapping("/templates/favorites")
    @Operation(summary = "Get favorite templates", description = "Get user's favorite recurring payment templates")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<RecurringTemplateDto>> getFavoriteTemplates(Principal principal) {
        log.info("Getting favorite templates for user: {}", principal.getName());
        List<RecurringTemplateDto> templates = recurringPaymentService.getFavoriteTemplates();
        return ResponseEntity.ok(templates);
    }
    
    @GetMapping("/templates/{templateId}")
    @Operation(summary = "Get template details", description = "Get details of a specific template")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RecurringTemplateDto> getTemplate(
            @PathVariable String templateId,
            Principal principal) {
        log.info("Getting template: {} for user: {}", templateId, principal.getName());
        RecurringTemplateDto template = recurringPaymentService.getTemplate(templateId);
        return ResponseEntity.ok(template);
    }
    
    @PutMapping("/templates/{templateId}")
    @Operation(summary = "Update template", description = "Update a recurring payment template")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RecurringTemplateDto> updateTemplate(
            @PathVariable String templateId,
            @Valid @RequestBody UpdateTemplateRequest request,
            Principal principal) {
        log.info("Updating template: {} for user: {}", templateId, principal.getName());
        RecurringTemplateDto template = recurringPaymentService.updateTemplate(templateId, request);
        return ResponseEntity.ok(template);
    }
    
    @PostMapping("/templates/{templateId}/favorite")
    @Operation(summary = "Toggle template favorite", description = "Add or remove template from favorites")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> toggleTemplateFavorite(
            @PathVariable String templateId,
            Principal principal) {
        log.info("Toggling favorite for template: {} for user: {}", templateId, principal.getName());
        recurringPaymentService.toggleTemplateFavorite(templateId);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/templates/{templateId}")
    @Operation(summary = "Delete template", description = "Delete a recurring payment template")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteTemplate(
            @PathVariable String templateId,
            Principal principal) {
        log.info("Deleting template: {} for user: {}", templateId, principal.getName());
        recurringPaymentService.deleteTemplate(templateId);
        return ResponseEntity.noContent().build();
    }
    
    // Utility endpoints
    
    @GetMapping("/frequencies")
    @Operation(summary = "Get supported frequencies", description = "Get list of supported recurring payment frequencies")
    public ResponseEntity<List<FrequencyInfo>> getSupportedFrequencies() {
        List<FrequencyInfo> frequencies = recurringPaymentService.getSupportedFrequencies();
        return ResponseEntity.ok(frequencies);
    }
    
    @GetMapping("/upcoming")
    @Operation(summary = "Get upcoming payments", description = "Get upcoming recurring payments for the next specified days")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<UpcomingPaymentDto>> getUpcomingPayments(
            @RequestParam(defaultValue = "7") int days,
            Principal principal) {
        log.info("Getting upcoming payments for next {} days for user: {}", days, principal.getName());
        List<UpcomingPaymentDto> upcomingPayments = recurringPaymentService.getUpcomingPayments(days);
        return ResponseEntity.ok(upcomingPayments);
    }
    
    @PostMapping("/{recurringId}/skip-next")
    @Operation(summary = "Skip next execution", description = "Skip the next scheduled execution of a recurring payment")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RecurringPaymentDto> skipNextExecution(
            @PathVariable String recurringId,
            @RequestParam(required = false) String reason,
            Principal principal) {
        log.info("Skipping next execution for recurring payment: {} for user: {}", recurringId, principal.getName());
        RecurringPaymentDto payment = recurringPaymentService.skipNextExecution(recurringId, reason);
        return ResponseEntity.ok(payment);
    }
    
    // Admin endpoints
    
    @GetMapping("/admin/processing-stats")
    @Operation(summary = "Get processing statistics", description = "Get system-wide recurring payment processing statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProcessingStatsDto> getProcessingStats() {
        log.info("Getting processing statistics");
        ProcessingStatsDto stats = recurringPaymentService.getProcessingStats();
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/admin/failed-executions")
    @Operation(summary = "Get failed executions", description = "Get failed executions requiring attention")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<Page<RecurringExecutionDto>> getFailedExecutions(
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Getting failed executions");
        Page<RecurringExecutionDto> executions = recurringPaymentService.getFailedExecutions(pageable);
        return ResponseEntity.ok(executions);
    }
    
    @PostMapping("/admin/process-retries")
    @Operation(summary = "Process pending retries", description = "Manually trigger processing of pending retries")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProcessingResultDto> processPendingRetries() {
        log.info("Processing pending retries");
        ProcessingResultDto result = recurringPaymentService.processPendingRetries();
        return ResponseEntity.ok(result);
    }
}