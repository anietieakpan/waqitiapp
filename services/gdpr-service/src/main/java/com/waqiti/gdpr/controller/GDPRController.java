package com.waqiti.gdpr.controller;

import com.waqiti.common.dto.ApiResponse;
import com.waqiti.gdpr.dto.*;
import com.waqiti.gdpr.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/gdpr")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "GDPR Compliance", description = "GDPR compliance and data privacy endpoints")
@SecurityRequirement(name = "bearerAuth")
public class GDPRController {

    private final DataSubjectRequestService requestService;
    private final ConsentManagementService consentService;
    private final DataExportService exportService;
    private final DataProcessingActivityService activityService;
    private final PrivacyPolicyService policyService;

    // Data Subject Request Endpoints

    @PostMapping("/requests")
    @Operation(summary = "Create data subject request", 
              description = "Submit a GDPR data subject request (access, erasure, etc.)")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<DataSubjectRequestDTO>> createRequest(
            @Valid @RequestBody CreateRequestDTO request,
            Principal principal) {
        
        log.info("Creating data subject request for user: {}", principal.getName());
        
        DataSubjectRequestDTO result = requestService.createRequest(request, principal.getName());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.<DataSubjectRequestDTO>builder()
                .success(true)
                .data(result)
                .message("Data subject request created successfully. Please check your email for verification.")
                .build()
        );
    }

    @PostMapping("/requests/{requestId}/verify")
    @Operation(summary = "Verify data subject request", 
              description = "Verify a data subject request using email token")
    public ResponseEntity<ApiResponse<DataSubjectRequestDTO>> verifyRequest(
            @PathVariable String requestId,
            @RequestParam String token) {
        
        log.info("Verifying data subject request: {}", requestId);
        
        DataSubjectRequestDTO result = requestService.verifyRequest(requestId, token);
        
        return ResponseEntity.ok(
            ApiResponse.<DataSubjectRequestDTO>builder()
                .success(true)
                .data(result)
                .message("Request verified successfully. Processing will begin shortly.")
                .build()
        );
    }

    @GetMapping("/requests")
    @Operation(summary = "Get user's data subject requests", 
              description = "Retrieve all data subject requests for the authenticated user")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<DataSubjectRequestDTO>>> getUserRequests(Principal principal) {
        log.info("Fetching data subject requests for user: {}", principal.getName());
        
        List<DataSubjectRequestDTO> requests = requestService.getUserRequests(principal.getName());
        
        return ResponseEntity.ok(
            ApiResponse.<List<DataSubjectRequestDTO>>builder()
                .success(true)
                .data(requests)
                .message("Retrieved " + requests.size() + " requests")
                .build()
        );
    }

    @GetMapping("/requests/{requestId}")
    @Operation(summary = "Get specific data subject request", 
              description = "Retrieve details of a specific data subject request")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<DataSubjectRequestDTO>> getRequest(
            @PathVariable String requestId,
            Principal principal) {
        
        log.info("Fetching data subject request: {} for user: {}", requestId, principal.getName());
        
        DataSubjectRequestDTO request = requestService.getRequest(requestId, principal.getName());
        
        return ResponseEntity.ok(
            ApiResponse.<DataSubjectRequestDTO>builder()
                .success(true)
                .data(request)
                .message("Request retrieved successfully")
                .build()
        );
    }

    @GetMapping("/requests/{requestId}/status")
    @Operation(summary = "Get request status", 
              description = "Check the current status of a data subject request")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<RequestStatusDTO>> getRequestStatus(
            @PathVariable String requestId,
            Principal principal) {
        
        RequestStatusDTO status = requestService.getRequestStatus(requestId, principal.getName());
        
        return ResponseEntity.ok(
            ApiResponse.<RequestStatusDTO>builder()
                .success(true)
                .data(status)
                .message("Request status retrieved")
                .build()
        );
    }

    @DeleteMapping("/requests/{requestId}")
    @Operation(summary = "Cancel data subject request", 
              description = "Cancel a pending data subject request")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> cancelRequest(
            @PathVariable String requestId,
            Principal principal) {
        
        log.info("Cancelling request: {} for user: {}", requestId, principal.getName());
        
        requestService.cancelRequest(requestId, principal.getName());
        
        return ResponseEntity.ok(
            ApiResponse.<Void>builder()
                .success(true)
                .message("Request cancelled successfully")
                .build()
        );
    }

    // Consent Management Endpoints

    @PostMapping("/consent")
    @Operation(summary = "Grant consent", 
              description = "Grant consent for specific data processing purpose")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ConsentRecordDTO>> grantConsent(
            @Valid @RequestBody GrantConsentDTO consent,
            Principal principal,
            HttpServletRequest request) {
        
        log.info("Granting consent for user: {} purpose: {}", principal.getName(), consent.getPurpose());
        
        ConsentRecordDTO result = consentService.grantConsent(consent, principal.getName(), request);
        
        return ResponseEntity.ok(
            ApiResponse.<ConsentRecordDTO>builder()
                .success(true)
                .data(result)
                .message("Consent granted successfully")
                .build()
        );
    }

    @DeleteMapping("/consent/{purpose}")
    @Operation(summary = "Withdraw consent", 
              description = "Withdraw consent for specific data processing purpose")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ConsentRecordDTO>> withdrawConsent(
            @PathVariable ConsentPurpose purpose,
            @RequestParam(required = false) String reason,
            Principal principal) {
        
        log.info("Withdrawing consent for user: {} purpose: {}", principal.getName(), purpose);
        
        ConsentRecordDTO result = consentService.withdrawConsent(
            principal.getName(), purpose, reason != null ? reason : "User requested withdrawal"
        );
        
        return ResponseEntity.ok(
            ApiResponse.<ConsentRecordDTO>builder()
                .success(true)
                .data(result)
                .message("Consent withdrawn successfully")
                .build()
        );
    }

    @GetMapping("/consent")
    @Operation(summary = "Get user consents", 
              description = "Retrieve all consent records for the authenticated user")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<ConsentRecordDTO>>> getUserConsents(
            @RequestParam(defaultValue = "false") boolean activeOnly,
            Principal principal) {
        
        log.info("Fetching consents for user: {} (activeOnly: {})", principal.getName(), activeOnly);
        
        List<ConsentRecordDTO> consents = activeOnly 
            ? consentService.getActiveUserConsents(principal.getName())
            : consentService.getUserConsents(principal.getName());
        
        return ResponseEntity.ok(
            ApiResponse.<List<ConsentRecordDTO>>builder()
                .success(true)
                .data(consents)
                .message("Retrieved " + consents.size() + " consent records")
                .build()
        );
    }

    @GetMapping("/consent/status")
    @Operation(summary = "Get consent status map", 
              description = "Get a map of all consent purposes and their current status")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Map<ConsentPurpose, Boolean>>> getConsentStatus(Principal principal) {
        log.info("Fetching consent status map for user: {}", principal.getName());
        
        Map<ConsentPurpose, Boolean> consentMap = consentService.getUserConsentMap(principal.getName());
        
        return ResponseEntity.ok(
            ApiResponse.<Map<ConsentPurpose, Boolean>>builder()
                .success(true)
                .data(consentMap)
                .message("Consent status map retrieved")
                .build()
        );
    }

    @PutMapping("/consent/preferences")
    @Operation(summary = "Update consent preferences", 
              description = "Update multiple consent preferences at once")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> updateConsentPreferences(
            @Valid @RequestBody UpdateConsentPreferencesDTO preferences,
            Principal principal,
            HttpServletRequest request) {
        
        log.info("Updating consent preferences for user: {}", principal.getName());
        
        preferences.setRequest(request);
        consentService.updateConsentPreferences(principal.getName(), preferences);
        
        return ResponseEntity.ok(
            ApiResponse.<Void>builder()
                .success(true)
                .message("Consent preferences updated successfully")
                .build()
        );
    }

    @GetMapping("/consent/history")
    @Operation(summary = "Get consent history", 
              description = "Retrieve complete consent history for the user")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ConsentHistoryDTO>> getConsentHistory(Principal principal) {
        log.info("Fetching consent history for user: {}", principal.getName());
        
        ConsentHistoryDTO history = consentService.getUserConsentHistory(principal.getName());
        
        return ResponseEntity.ok(
            ApiResponse.<ConsentHistoryDTO>builder()
                .success(true)
                .data(history)
                .message("Consent history retrieved")
                .build()
        );
    }

    @GetMapping("/consent/form/{purpose}")
    @Operation(summary = "Get consent form", 
              description = "Get the consent form text and details for a specific purpose")
    public ResponseEntity<ApiResponse<ConsentFormDTO>> getConsentForm(
            @PathVariable ConsentPurpose purpose,
            @RequestParam(defaultValue = "en") String language) {
        
        ConsentFormDTO form = consentService.getConsentForm(purpose, language);
        
        return ResponseEntity.ok(
            ApiResponse.<ConsentFormDTO>builder()
                .success(true)
                .data(form)
                .message("Consent form retrieved")
                .build()
        );
    }

    // Data Export Endpoints

    @GetMapping("/export")
    @Operation(summary = "Export user data", 
              description = "Export all user data in specified format")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<DataExportDTO>> exportUserData(
            @RequestParam(defaultValue = "JSON") ExportFormat format,
            @RequestParam(required = false) List<String> categories,
            Principal principal) {
        
        log.info("Exporting data for user: {} in format: {}", principal.getName(), format);
        
        DataExportDTO export = exportService.initiateDataExport(principal.getName(), format, categories);
        
        return ResponseEntity.ok(
            ApiResponse.<DataExportDTO>builder()
                .success(true)
                .data(export)
                .message("Data export initiated. You will receive an email when ready.")
                .build()
        );
    }

    @GetMapping("/export/{exportId}/download")
    @Operation(summary = "Download exported data", 
              description = "Download the exported data file")
    @PreAuthorize("hasRole('USER')")
    @Produces(MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> downloadExport(
            @PathVariable String exportId,
            Principal principal) {
        
        log.info("Downloading export: {} for user: {}", exportId, principal.getName());
        
        return exportService.downloadExport(exportId, principal.getName());
    }

    // Privacy Policy Endpoints

    @GetMapping("/privacy/policy")
    @Operation(summary = "Get privacy policy", 
              description = "Retrieve the current privacy policy")
    public ResponseEntity<ApiResponse<PrivacyPolicyDTO>> getPrivacyPolicy(
            @RequestParam(defaultValue = "en") String language) {
        
        PrivacyPolicyDTO policy = policyService.getCurrentPolicy(language);
        
        return ResponseEntity.ok(
            ApiResponse.<PrivacyPolicyDTO>builder()
                .success(true)
                .data(policy)
                .message("Privacy policy retrieved")
                .build()
        );
    }

    @GetMapping("/privacy/rights")
    @Operation(summary = "Get data subject rights", 
              description = "Get information about GDPR data subject rights")
    public ResponseEntity<ApiResponse<DataSubjectRightsDTO>> getDataSubjectRights(
            @RequestParam(defaultValue = "en") String language) {
        
        DataSubjectRightsDTO rights = policyService.getDataSubjectRights(language);
        
        return ResponseEntity.ok(
            ApiResponse.<DataSubjectRightsDTO>builder()
                .success(true)
                .data(rights)
                .message("Data subject rights retrieved")
                .build()
        );
    }

    // Data Processing Activities (for transparency)

    @GetMapping("/processing/activities")
    @Operation(summary = "Get data processing activities", 
              description = "Get list of data processing activities")
    public ResponseEntity<ApiResponse<List<ProcessingActivityDTO>>> getProcessingActivities() {
        List<ProcessingActivityDTO> activities = activityService.getPublicProcessingActivities();
        
        return ResponseEntity.ok(
            ApiResponse.<List<ProcessingActivityDTO>>builder()
                .success(true)
                .data(activities)
                .message("Retrieved " + activities.size() + " processing activities")
                .build()
        );
    }

    // Admin Endpoints

    @GetMapping("/admin/requests/pending")
    @Operation(summary = "Get pending requests", 
              description = "Get all pending data subject requests (admin only)")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DPO')")
    public ResponseEntity<ApiResponse<List<DataSubjectRequestDTO>>> getPendingRequests() {
        log.info("Admin fetching pending data subject requests");
        
        List<DataSubjectRequestDTO> requests = requestService.getPendingRequests();
        
        return ResponseEntity.ok(
            ApiResponse.<List<DataSubjectRequestDTO>>builder()
                .success(true)
                .data(requests)
                .message("Retrieved " + requests.size() + " pending requests")
                .build()
        );
    }

    @GetMapping("/admin/requests/overdue")
    @Operation(summary = "Get overdue requests", 
              description = "Get all overdue data subject requests (admin only)")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DPO')")
    public ResponseEntity<ApiResponse<List<DataSubjectRequestDTO>>> getOverdueRequests() {
        log.info("Admin fetching overdue data subject requests");
        
        List<DataSubjectRequestDTO> requests = requestService.getOverdueRequests();
        
        return ResponseEntity.ok(
            ApiResponse.<List<DataSubjectRequestDTO>>builder()
                .success(true)
                .data(requests)
                .message("Retrieved " + requests.size() + " overdue requests")
                .build()
        );
    }

    @PostMapping("/admin/consent/expired/process")
    @Operation(summary = "Process expired consents", 
              description = "Process all expired consents (admin only)")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DPO')")
    public ResponseEntity<ApiResponse<Void>> processExpiredConsents() {
        log.info("Admin processing expired consents");
        
        consentService.processExpiredConsents();
        
        return ResponseEntity.ok(
            ApiResponse.<Void>builder()
                .success(true)
                .message("Expired consents processed successfully")
                .build()
        );
    }
}