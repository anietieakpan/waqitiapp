package com.waqiti.dispute.controller;

import com.waqiti.common.security.SecurityContextUtil;
import com.waqiti.dispute.dto.*;
import com.waqiti.dispute.entity.DisputeStatus;
import com.waqiti.dispute.service.DisputeResolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Dispute Management
 *
 * CRITICAL P1 SECURITY FIX: All endpoints now use SecurityContext instead of X-User-ID headers
 * to prevent IDOR (Insecure Direct Object Reference) vulnerabilities.
 *
 * Provides comprehensive API for dispute creation, management, and resolution
 */
@RestController
@RequestMapping("/api/v1/disputes")
@Tag(name = "Dispute Management", description = "APIs for managing transaction disputes")
@RequiredArgsConstructor
@Validated
@Slf4j
public class DisputeController {

    private final DisputeResolutionService disputeResolutionService;

    /**
     * Create a new dispute
     *
     * SECURITY FIX: User ID now extracted from SecurityContext (JWT) instead of X-User-ID header
     */
    @PostMapping
    @Operation(summary = "Create a new dispute", description = "Initiates a new dispute for a transaction")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Dispute created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid dispute data"),
        @ApiResponse(responseCode = "404", description = "Transaction not found"),
        @ApiResponse(responseCode = "409", description = "Dispute already exists for this transaction")
    })
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<DisputeDTO> createDispute(
            @Valid @RequestBody CreateDisputeRequest request) {

        // SECURITY FIX: Extract user ID from SecurityContext (JWT token) instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Creating dispute for transaction: {} by user: {}", request.getTransactionId(), userId);

        request.setInitiatorId(userId.toString());
        DisputeDTO dispute = disputeResolutionService.createDispute(request);

        log.info("Dispute created successfully with ID: {}", dispute.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(dispute);
    }

    /**
     * Get dispute by ID
     *
     * SECURITY FIX: User ID now extracted from SecurityContext (JWT) instead of X-User-ID header
     */
    @GetMapping("/{disputeId}")
    @Operation(summary = "Get dispute by ID", description = "Retrieves detailed information about a specific dispute")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dispute found"),
        @ApiResponse(responseCode = "404", description = "Dispute not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<DisputeDTO> getDispute(
            @PathVariable @NotBlank String disputeId) {

        // SECURITY FIX: Extract user ID from SecurityContext (JWT token) instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.debug("Fetching dispute: {} for user: {}", disputeId, userId);
        DisputeDTO dispute = disputeResolutionService.getDispute(disputeId, userId.toString());
        return ResponseEntity.ok(dispute);
    }

    /**
     * Get all disputes for a user
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user disputes", description = "Retrieves all disputes for a specific user")
    @PreAuthorize("hasRole('USER') and #userId == authentication.principal.id or hasRole('ADMIN')")
    public ResponseEntity<Page<DisputeDTO>> getUserDisputes(
            @PathVariable @NotBlank String userId,
            @RequestParam(required = false) DisputeStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {
        
        log.debug("Fetching disputes for user: {} with status: {}", userId, status);
        Page<DisputeDTO> disputes = disputeResolutionService.getUserDisputes(userId, status, startDate, endDate, pageable);
        return ResponseEntity.ok(disputes);
    }

    /**
     * Update dispute status
     *
     * SECURITY FIX: Admin ID now extracted from SecurityContext (JWT) instead of X-User-ID header
     */
    @PutMapping("/{disputeId}/status")
    @Operation(summary = "Update dispute status", description = "Updates the status of an existing dispute")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid status transition"),
        @ApiResponse(responseCode = "404", description = "Dispute not found"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<DisputeDTO> updateDisputeStatus(
            @PathVariable @NotBlank String disputeId,
            @RequestParam @NotNull DisputeStatus newStatus,
            @RequestParam(required = false) String reason) {

        // SECURITY FIX: Extract admin ID from SecurityContext (JWT token) instead of header
        UUID adminId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Updating dispute {} status to {} by admin: {}", disputeId, newStatus, adminId);

        UpdateDisputeStatusRequest request = UpdateDisputeStatusRequest.builder()
                .disputeId(disputeId)
                .newStatus(newStatus)
                .reason(reason)
                .updatedBy(adminId.toString())
                .build();

        DisputeDTO updatedDispute = disputeResolutionService.updateDisputeStatus(request);

        log.info("Dispute {} status updated successfully to {}", disputeId, newStatus);
        return ResponseEntity.ok(updatedDispute);
    }

    /**
     * Add evidence to dispute
     *
     * SECURITY FIX: User ID now extracted from SecurityContext (JWT) instead of X-User-ID header
     */
    @PostMapping("/{disputeId}/evidence")
    @Operation(summary = "Add evidence to dispute", description = "Uploads evidence documents for a dispute")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Evidence added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid file or data"),
        @ApiResponse(responseCode = "404", description = "Dispute not found"),
        @ApiResponse(responseCode = "413", description = "File too large")
    })
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<EvidenceDTO> addEvidence(
            @PathVariable @NotBlank String disputeId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("description") String description,
            @RequestParam("type") String evidenceType) {

        // SECURITY FIX: Extract user ID from SecurityContext (JWT token) instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Adding evidence to dispute {} by user: {}", disputeId, userId);

        if (file.getSize() > 10 * 1024 * 1024) { // 10MB limit
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }

        AddEvidenceRequest request = AddEvidenceRequest.builder()
                .disputeId(disputeId)
                .file(file)
                .description(description)
                .evidenceType(evidenceType)
                .uploadedBy(userId.toString())
                .build();

        EvidenceDTO evidence = disputeResolutionService.addEvidence(request);

        log.info("Evidence added successfully to dispute {}", disputeId);
        return ResponseEntity.status(HttpStatus.CREATED).body(evidence);
    }

    /**
     * Escalate dispute
     *
     * SECURITY FIX: User ID now extracted from SecurityContext (JWT) instead of X-User-ID header
     */
    @PostMapping("/{disputeId}/escalate")
    @Operation(summary = "Escalate dispute", description = "Escalates a dispute to higher support level")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dispute escalated successfully"),
        @ApiResponse(responseCode = "400", description = "Cannot escalate dispute in current status"),
        @ApiResponse(responseCode = "404", description = "Dispute not found")
    })
    @PreAuthorize("hasRole('SUPPORT') or hasRole('ADMIN')")
    public ResponseEntity<DisputeDTO> escalateDispute(
            @PathVariable @NotBlank String disputeId,
            @RequestParam @NotBlank String escalationReason,
            @RequestParam(required = false) String assignTo) {

        // SECURITY FIX: Extract user ID from SecurityContext (JWT token) instead of header
        UUID escalatedBy = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Escalating dispute {} by: {}", disputeId, escalatedBy);

        EscalateDisputeRequest request = EscalateDisputeRequest.builder()
                .disputeId(disputeId)
                .escalationReason(escalationReason)
                .assignTo(assignTo)
                .escalatedBy(escalatedBy.toString())
                .build();

        DisputeDTO escalatedDispute = disputeResolutionService.escalateDispute(request);

        log.info("Dispute {} escalated successfully", disputeId);
        return ResponseEntity.ok(escalatedDispute);
    }

    /**
     * Resolve dispute
     *
     * SECURITY FIX: User ID now extracted from SecurityContext (JWT) instead of X-User-ID header
     */
    @PostMapping("/{disputeId}/resolve")
    @Operation(summary = "Resolve dispute", description = "Marks a dispute as resolved with resolution details")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dispute resolved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid resolution data"),
        @ApiResponse(responseCode = "404", description = "Dispute not found"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<DisputeDTO> resolveDispute(
            @PathVariable @NotBlank String disputeId,
            @Valid @RequestBody ResolveDisputeRequest request) {

        // SECURITY FIX: Extract user ID from SecurityContext (JWT token) instead of header
        UUID resolvedBy = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Resolving dispute {} by: {}", disputeId, resolvedBy);

        request.setDisputeId(disputeId);
        request.setResolvedBy(resolvedBy.toString());

        DisputeDTO resolvedDispute = disputeResolutionService.resolveDispute(request);

        log.info("Dispute {} resolved successfully with outcome: {}", disputeId, request.getResolutionType());
        return ResponseEntity.ok(resolvedDispute);
    }

    /**
     * Get dispute statistics
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get dispute statistics", description = "Retrieves statistical data about disputes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DisputeStatistics> getDisputeStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        log.debug("Fetching dispute statistics from {} to {}", startDate, endDate);
        DisputeStatistics statistics = disputeResolutionService.getDisputeStatistics(startDate, endDate);
        return ResponseEntity.ok(statistics);
    }

    /**
     * Search disputes
     */
    @GetMapping("/search")
    @Operation(summary = "Search disputes", description = "Search disputes with various filters")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<Page<DisputeDTO>> searchDisputes(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) DisputeStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) Integer minAmount,
            @RequestParam(required = false) Integer maxAmount,
            Pageable pageable) {
        
        log.debug("Searching disputes with term: {}, status: {}", searchTerm, status);
        
        DisputeSearchCriteria criteria = DisputeSearchCriteria.builder()
                .searchTerm(searchTerm)
                .status(status)
                .category(category)
                .startDate(startDate)
                .endDate(endDate)
                .assignedTo(assignedTo)
                .minAmount(minAmount)
                .maxAmount(maxAmount)
                .build();
        
        Page<DisputeDTO> disputes = disputeResolutionService.searchDisputes(criteria, pageable);
        return ResponseEntity.ok(disputes);
    }

    /**
     * Get dispute timeline
     *
     * SECURITY FIX: User ID now extracted from SecurityContext (JWT) instead of X-User-ID header
     */
    @GetMapping("/{disputeId}/timeline")
    @Operation(summary = "Get dispute timeline", description = "Retrieves the complete timeline of dispute events")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<DisputeTimelineEvent>> getDisputeTimeline(
            @PathVariable @NotBlank String disputeId) {

        // SECURITY FIX: Extract user ID from SecurityContext (JWT token) instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.debug("Fetching timeline for dispute: {}", disputeId);
        List<DisputeTimelineEvent> timeline = disputeResolutionService.getDisputeTimeline(disputeId, userId.toString());
        return ResponseEntity.ok(timeline);
    }

    /**
     * Bulk update disputes
     *
     * SECURITY FIX: Admin ID now extracted from SecurityContext (JWT) instead of X-User-ID header
     */
    @PutMapping("/bulk-update")
    @Operation(summary = "Bulk update disputes", description = "Update multiple disputes at once")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkUpdateResult> bulkUpdateDisputes(
            @Valid @RequestBody BulkUpdateRequest request) {

        // SECURITY FIX: Extract admin ID from SecurityContext (JWT token) instead of header
        UUID adminId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Bulk updating {} disputes by admin: {}", request.getDisputeIds().size(), adminId);

        request.setUpdatedBy(adminId.toString());
        BulkUpdateResult result = disputeResolutionService.bulkUpdateDisputes(request);

        log.info("Bulk update completed. Success: {}, Failed: {}",
                result.getSuccessCount(), result.getFailureCount());
        return ResponseEntity.ok(result);
    }

    /**
     * Export disputes
     */
    @GetMapping("/export")
    @Operation(summary = "Export disputes", description = "Export disputes data in various formats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportDisputes(
            @RequestParam(defaultValue = "CSV") String format,
            @RequestParam(required = false) DisputeStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        log.info("Exporting disputes in {} format", format);
        
        ExportRequest exportRequest = ExportRequest.builder()
                .format(format)
                .status(status)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        
        byte[] exportData = disputeResolutionService.exportDisputes(exportRequest);
        
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=disputes." + format.toLowerCase())
                .contentType(getMediaType(format))
                .body(exportData);
    }

    /**
     * Get dispute categories
     */
    @GetMapping("/categories")
    @Operation(summary = "Get dispute categories", description = "Retrieves all available dispute categories")
    public ResponseEntity<List<String>> getDisputeCategories() {
        List<String> categories = disputeResolutionService.getDisputeCategories();
        return ResponseEntity.ok(categories);
    }

    /**
     * Get dispute resolution templates
     */
    @GetMapping("/templates")
    @Operation(summary = "Get resolution templates", description = "Retrieves pre-defined resolution templates")
    @PreAuthorize("hasRole('SUPPORT') or hasRole('ADMIN')")
    public ResponseEntity<List<ResolutionTemplate>> getResolutionTemplates(
            @RequestParam(required = false) String category) {
        
        List<ResolutionTemplate> templates = disputeResolutionService.getResolutionTemplates(category);
        return ResponseEntity.ok(templates);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if dispute service is running")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "dispute-service",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    private MediaType getMediaType(String format) {
        switch (format.toUpperCase()) {
            case "CSV":
                return MediaType.parseMediaType("text/csv");
            case "EXCEL":
                return MediaType.parseMediaType("application/vnd.ms-excel");
            case "PDF":
                return MediaType.APPLICATION_PDF;
            default:
                return MediaType.APPLICATION_JSON;
        }
    }
}