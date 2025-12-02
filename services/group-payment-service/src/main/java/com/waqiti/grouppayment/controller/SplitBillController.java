package com.waqiti.grouppayment.controller;

import com.waqiti.common.ratelimit.RateLimit;
import com.waqiti.common.security.RequiresScope;
import com.waqiti.grouppayment.dto.*;
import com.waqiti.grouppayment.service.SplitBillCalculatorService;
import com.waqiti.grouppayment.service.SplitBillPaymentService;
import com.waqiti.grouppayment.service.SplitBillNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Split Bill functionality
 * Provides comprehensive API for bill splitting, calculation, and payment processing
 */
@RestController
@RequestMapping("/api/v1/split-bills")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Split Bills", description = "Bill splitting calculation and payment management")
@SecurityRequirement(name = "Bearer Authentication")
public class SplitBillController {
    
    private final SplitBillCalculatorService splitBillCalculatorService;
    private final SplitBillPaymentService splitBillPaymentService;
    private final SplitBillNotificationService splitBillNotificationService;
    
    /**
     * Calculate split bill
     */
    @PostMapping("/calculate")
    @Operation(summary = "Calculate split bill", 
               description = "Calculates how to split a bill among participants using various methods")
    @ApiResponse(responseCode = "201", description = "Split bill calculated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid calculation parameters")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    @RequiresScope("SCOPE_payment:split-bill-create")
    @RateLimit(key = "split-bill-calculate", limit = 20, window = 300) // 20 per 5 minutes
    public ResponseEntity<SplitBillCalculationResponse> calculateSplitBill(
            @Valid @RequestBody SplitBillCalculationRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("Calculating split bill for organizer: {} with {} participants using method: {}", 
                request.getOrganizerId(), request.getParticipants().size(), request.getSplitMethod());
        
        // Add request context
        if (request.getMetadata() != null) {
            request.getMetadata().put("client_ip", getClientIP(httpRequest));
            request.getMetadata().put("user_agent", httpRequest.getHeader("User-Agent"));
        }
        
        SplitBillCalculationResponse response = splitBillCalculatorService.calculateSplitBill(request);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Split-Bill-ID", response.getCalculationId())
                .body(response);
    }
    
    /**
     * Get split bill details
     */
    @GetMapping("/{splitBillId}")
    @Operation(summary = "Get split bill details", 
               description = "Retrieves complete details of a split bill calculation")
    @RequiresScope("SCOPE_payment:split-bill-read")
    public ResponseEntity<SplitBillDetailsResponse> getSplitBillDetails(
            @Parameter(description = "Split bill calculation ID") 
            @PathVariable @NotBlank String splitBillId) {
        
        SplitBillDetailsResponse response = splitBillCalculatorService.getSplitBillDetails(splitBillId);
        
        return ResponseEntity.ok()
                .header("Cache-Control", "private, max-age=60")
                .body(response);
    }
    
    /**
     * Update split bill calculation
     */
    @PutMapping("/{splitBillId}")
    @Operation(summary = "Update split bill calculation", 
               description = "Updates an existing split bill calculation")
    @RequiresScope("SCOPE_payment:split-bill-update")
    @PreAuthorize("@splitBillService.isOrganizer(#splitBillId, authentication.principal.userId)")
    public ResponseEntity<SplitBillCalculationResponse> updateSplitBill(
            @Parameter(description = "Split bill calculation ID") 
            @PathVariable @NotBlank String splitBillId,
            @Valid @RequestBody SplitBillUpdateRequest request) {
        
        log.info("Updating split bill: {} by organizer", splitBillId);
        
        SplitBillCalculationResponse response = splitBillCalculatorService.updateSplitBill(splitBillId, request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Add participants to existing split bill
     */
    @PostMapping("/{splitBillId}/participants")
    @Operation(summary = "Add participants to split bill", 
               description = "Adds new participants to an existing split bill")
    @RequiresScope("SCOPE_payment:split-bill-update")
    @PreAuthorize("@splitBillService.isOrganizer(#splitBillId, authentication.principal.userId)")
    public ResponseEntity<SplitBillCalculationResponse> addParticipants(
            @Parameter(description = "Split bill calculation ID") 
            @PathVariable @NotBlank String splitBillId,
            @Valid @RequestBody AddParticipantsRequest request) {
        
        log.info("Adding {} participants to split bill: {}", request.getParticipants().size(), splitBillId);
        
        SplitBillCalculationResponse response = splitBillCalculatorService.addParticipants(splitBillId, request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Remove participant from split bill
     */
    @DeleteMapping("/{splitBillId}/participants/{participantId}")
    @Operation(summary = "Remove participant from split bill", 
               description = "Removes a participant and recalculates the split")
    @RequiresScope("SCOPE_payment:split-bill-update")
    @PreAuthorize("@splitBillService.isOrganizerOrParticipant(#splitBillId, authentication.principal.userId)")
    public ResponseEntity<SplitBillCalculationResponse> removeParticipant(
            @Parameter(description = "Split bill calculation ID") 
            @PathVariable @NotBlank String splitBillId,
            @Parameter(description = "Participant ID to remove") 
            @PathVariable @NotBlank String participantId,
            @Parameter(description = "Reason for removal") 
            @RequestParam(required = false) String reason) {
        
        log.info("Removing participant: {} from split bill: {}", participantId, splitBillId);
        
        SplitBillCalculationResponse response = splitBillCalculatorService.removeParticipant(
                splitBillId, participantId, reason);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Process payment for split bill
     */
    @PostMapping("/{splitBillId}/pay")
    @Operation(summary = "Pay split bill amount", 
               description = "Processes payment for a participant's portion of the split bill")
    @RequiresScope("SCOPE_payment:split-bill-pay")
    @RateLimit(key = "split-bill-pay", limit = 10, window = 300) // 10 per 5 minutes
    public ResponseEntity<SplitBillPaymentResponse> paySplitBill(
            @Parameter(description = "Split bill calculation ID") 
            @PathVariable @NotBlank String splitBillId,
            @Valid @RequestBody SplitBillPaymentRequest request) {
        
        log.info("Processing payment for split bill: {} by participant: {}", 
                splitBillId, request.getParticipantId());
        
        SplitBillPaymentResponse response = splitBillPaymentService.processPayment(splitBillId, request);
        
        return ResponseEntity.ok()
                .header("X-Transaction-ID", response.getTransactionId())
                .body(response);
    }
    
    /**
     * Get payment status for split bill
     */
    @GetMapping("/{splitBillId}/payment-status")
    @Operation(summary = "Get payment status", 
               description = "Retrieves payment status for all participants in a split bill")
    @RequiresScope("SCOPE_payment:split-bill-read")
    public ResponseEntity<SplitBillPaymentStatusResponse> getPaymentStatus(
            @Parameter(description = "Split bill calculation ID") 
            @PathVariable @NotBlank String splitBillId) {
        
        SplitBillPaymentStatusResponse response = splitBillPaymentService.getPaymentStatus(splitBillId);
        
        return ResponseEntity.ok()
                .header("Cache-Control", "private, max-age=30")
                .body(response);
    }
    
    /**
     * Send payment reminders
     */
    @PostMapping("/{splitBillId}/reminders")
    @Operation(summary = "Send payment reminders", 
               description = "Sends payment reminders to participants who haven't paid")
    @RequiresScope("SCOPE_payment:split-bill-notify")
    @PreAuthorize("@splitBillService.isOrganizer(#splitBillId, authentication.principal.userId)")
    @RateLimit(key = "split-bill-reminder", limit = 5, window = 3600) // 5 per hour
    public ResponseEntity<SplitBillReminderResponse> sendReminders(
            @Parameter(description = "Split bill calculation ID") 
            @PathVariable @NotBlank String splitBillId,
            @Valid @RequestBody SplitBillReminderRequest request) {
        
        log.info("Sending payment reminders for split bill: {}", splitBillId);
        
        SplitBillReminderResponse response = splitBillNotificationService.sendReminders(splitBillId, request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get user's split bills
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user's split bills", 
               description = "Retrieves paginated list of split bills for a user")
    @RequiresScope("SCOPE_payment:split-bill-read")
    @PreAuthorize("#userId == authentication.principal.userId or hasRole('ADMIN')")
    public ResponseEntity<Page<SplitBillSummaryResponse>> getUserSplitBills(
            @Parameter(description = "User ID") 
            @PathVariable @NotBlank String userId,
            @Parameter(description = "Filter by status")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter by role in split")
            @RequestParam(required = false) String role, // ORGANIZER, PARTICIPANT
            @Parameter(description = "Start date filter")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date filter")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<SplitBillSummaryResponse> response = splitBillCalculatorService.getUserSplitBills(
                userId, status, role, startDate, endDate, pageable);
        
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(response.getTotalElements()))
                .body(response);
    }
    
    /**
     * Cancel split bill
     */
    @DeleteMapping("/{splitBillId}")
    @Operation(summary = "Cancel split bill", 
               description = "Cancels a split bill and refunds any payments made")
    @RequiresScope("SCOPE_payment:split-bill-cancel")
    @PreAuthorize("@splitBillService.isOrganizer(#splitBillId, authentication.principal.userId)")
    public ResponseEntity<SplitBillCancellationResponse> cancelSplitBill(
            @Parameter(description = "Split bill calculation ID") 
            @PathVariable @NotBlank String splitBillId,
            @Valid @RequestBody SplitBillCancellationRequest request) {
        
        log.info("Cancelling split bill: {} with reason: {}", splitBillId, request.getReason());
        
        SplitBillCancellationResponse response = splitBillCalculatorService.cancelSplitBill(splitBillId, request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get split bill by short code
     */
    @GetMapping("/code/{shortCode}")
    @Operation(summary = "Get split bill by short code", 
               description = "Retrieves split bill details using the short shareable code")
    @RequiresScope("SCOPE_payment:split-bill-read")
    public ResponseEntity<SplitBillDetailsResponse> getSplitBillByShortCode(
            @Parameter(description = "Short code for the split bill") 
            @PathVariable @NotBlank @Pattern(regexp = "^[A-Z0-9]{8}$") String shortCode) {
        
        SplitBillDetailsResponse response = splitBillCalculatorService.getSplitBillByShortCode(shortCode);
        
        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=300")
                .body(response);
    }
    
    /**
     * Export split bill details
     */
    @GetMapping("/{splitBillId}/export")
    @Operation(summary = "Export split bill", 
               description = "Exports split bill details in various formats")
    @RequiresScope("SCOPE_payment:split-bill-read")
    public ResponseEntity<byte[]> exportSplitBill(
            @Parameter(description = "Split bill calculation ID") 
            @PathVariable @NotBlank String splitBillId,
            @Parameter(description = "Export format")
            @RequestParam(defaultValue = "PDF") String format) { // PDF, CSV, EXCEL
        
        log.info("Exporting split bill: {} in format: {}", splitBillId, format);
        
        byte[] exportData = splitBillCalculatorService.exportSplitBill(splitBillId, format);
        
        String contentType = switch (format.toUpperCase()) {
            case "CSV" -> "text/csv";
            case "EXCEL" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/pdf";
        };
        
        String filename = "split-bill-" + splitBillId + "." + format.toLowerCase();
        
        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(exportData);
    }
    
    /**
     * Get split bill analytics
     */
    @GetMapping("/analytics")
    @Operation(summary = "Get split bill analytics", 
               description = "Retrieves analytics for split bill usage")
    @RequiresScope("SCOPE_analytics:split-bill-read")
    public ResponseEntity<SplitBillAnalyticsResponse> getSplitBillAnalytics(
            @Parameter(description = "User ID for personal analytics")
            @RequestParam(required = false) String userId,
            @Parameter(description = "Group by period")
            @RequestParam(defaultValue = "MONTH") String groupBy,
            @Parameter(description = "Start date")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        SplitBillAnalyticsRequest analyticsRequest = SplitBillAnalyticsRequest.builder()
                .userId(userId)
                .groupBy(groupBy)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        
        SplitBillAnalyticsResponse response = splitBillCalculatorService.getAnalytics(analyticsRequest);
        
        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=300")
                .body(response);
    }
    
    /**
     * Suggest split optimization
     */
    @PostMapping("/{splitBillId}/optimize")
    @Operation(summary = "Suggest split optimization", 
               description = "Analyzes split bill and suggests optimizations")
    @RequiresScope("SCOPE_payment:split-bill-optimize")
    public ResponseEntity<SplitBillOptimizationResponse> optimizeSplitBill(
            @Parameter(description = "Split bill calculation ID") 
            @PathVariable @NotBlank String splitBillId,
            @Valid @RequestBody SplitBillOptimizationRequest request) {
        
        log.info("Optimizing split bill: {}", splitBillId);
        
        SplitBillOptimizationResponse response = splitBillCalculatorService.optimizeSplitBill(splitBillId, request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Duplicate split bill
     */
    @PostMapping("/{splitBillId}/duplicate")
    @Operation(summary = "Duplicate split bill", 
               description = "Creates a new split bill based on an existing one")
    @RequiresScope("SCOPE_payment:split-bill-create")
    @PreAuthorize("@splitBillService.hasAccessToSplitBill(#splitBillId, authentication.principal.userId)")
    public ResponseEntity<SplitBillCalculationResponse> duplicateSplitBill(
            @Parameter(description = "Split bill calculation ID to duplicate") 
            @PathVariable @NotBlank String splitBillId,
            @Valid @RequestBody SplitBillDuplicationRequest request) {
        
        log.info("Duplicating split bill: {} for organizer: {}", splitBillId, request.getNewOrganizerId());
        
        SplitBillCalculationResponse response = splitBillCalculatorService.duplicateSplitBill(splitBillId, request);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Split-Bill-ID", response.getCalculationId())
                .body(response);
    }
    
    // Helper methods
    
    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}