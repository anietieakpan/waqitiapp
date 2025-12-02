/**
 * BNPL Application Controller
 * REST API endpoints for Buy Now Pay Later applications
 */
package com.waqiti.bnpl.controller;

import com.waqiti.bnpl.dto.request.BnplApplicationRequest;
import com.waqiti.bnpl.dto.request.ApplicationDecisionRequest;
import com.waqiti.bnpl.dto.response.BnplApplicationResponse;
import com.waqiti.bnpl.dto.response.ApplicationStatusResponse;
import com.waqiti.bnpl.entity.BnplApplication.ApplicationStatus;
import com.waqiti.bnpl.service.BnplApplicationService;
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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/applications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "BNPL Applications", description = "Buy Now Pay Later application management")
public class BnplApplicationController {
    
    private final BnplApplicationService applicationService;
    
    @PostMapping
    @Operation(summary = "Create BNPL application", description = "Creates a new Buy Now Pay Later application")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BnplApplicationResponse> createApplication(
            @Valid @RequestBody BnplApplicationRequest request,
            @RequestHeader("X-User-Id") UUID userId) {
        
        log.info("Creating BNPL application for user: {} amount: {}", userId, request.getPurchaseAmount());
        request.setUserId(userId);
        
        BnplApplicationResponse response = applicationService.createApplication(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{applicationId}")
    @Operation(summary = "Get application details", description = "Retrieves BNPL application details")
    @PreAuthorize("hasRole('USER') and @applicationService.isApplicationOwner(#applicationId, #userId)")
    public ResponseEntity<BnplApplicationResponse> getApplication(
            @PathVariable UUID applicationId,
            @RequestHeader("X-User-Id") UUID userId) {
        
        log.info("Fetching application: {} for user: {}", applicationId, userId);
        BnplApplicationResponse response = applicationService.getApplication(applicationId);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    @Operation(summary = "List user applications", description = "Retrieves user's BNPL applications")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<BnplApplicationResponse>> getUserApplications(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false) ApplicationStatus status,
            Pageable pageable) {
        
        log.info("Listing applications for user: {} with status: {}", userId, status);
        Page<BnplApplicationResponse> applications = applicationService.getUserApplications(userId, status, pageable);
        return ResponseEntity.ok(applications);
    }
    
    @GetMapping("/{applicationId}/status")
    @Operation(summary = "Get application status", description = "Retrieves current application status")
    @PreAuthorize("hasRole('USER') and @applicationService.isApplicationOwner(#applicationId, #userId)")
    public ResponseEntity<ApplicationStatusResponse> getApplicationStatus(
            @PathVariable UUID applicationId,
            @RequestHeader("X-User-Id") UUID userId) {
        
        log.info("Fetching status for application: {}", applicationId);
        ApplicationStatusResponse status = applicationService.getApplicationStatus(applicationId);
        return ResponseEntity.ok(status);
    }
    
    @PostMapping("/{applicationId}/approve")
    @Operation(summary = "Approve application", description = "Approves a pending BNPL application")
    @PreAuthorize("hasRole('ADMIN') or hasRole('UNDERWRITER')")
    public ResponseEntity<BnplApplicationResponse> approveApplication(
            @PathVariable UUID applicationId,
            @Valid @RequestBody ApplicationDecisionRequest request,
            @RequestHeader("X-User-Id") String approvedBy) {
        
        log.info("Approving application: {} by: {}", applicationId, approvedBy);
        BnplApplicationResponse response = applicationService.approveApplication(applicationId, approvedBy);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{applicationId}/reject")
    @Operation(summary = "Reject application", description = "Rejects a pending BNPL application")
    @PreAuthorize("hasRole('ADMIN') or hasRole('UNDERWRITER')")
    public ResponseEntity<BnplApplicationResponse> rejectApplication(
            @PathVariable UUID applicationId,
            @Valid @RequestBody ApplicationDecisionRequest request,
            @RequestHeader("X-User-Id") String rejectedBy) {
        
        log.info("Rejecting application: {} by: {}", applicationId, rejectedBy);
        BnplApplicationResponse response = applicationService.rejectApplication(
                applicationId, 
                request.getReason(), 
                rejectedBy
        );
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{applicationId}/cancel")
    @Operation(summary = "Cancel application", description = "Cancels an active BNPL application")
    @PreAuthorize("hasRole('USER') and @applicationService.isApplicationOwner(#applicationId, #userId)")
    public ResponseEntity<BnplApplicationResponse> cancelApplication(
            @PathVariable UUID applicationId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam String reason) {
        
        log.info("Cancelling application: {} for user: {}", applicationId, userId);
        BnplApplicationResponse response = applicationService.cancelApplication(applicationId, userId, reason);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/eligibility-check")
    @Operation(summary = "Check eligibility", description = "Checks user's eligibility for BNPL")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<EligibilityResponse> checkEligibility(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam BigDecimal purchaseAmount) {
        
        log.info("Checking BNPL eligibility for user: {} amount: {}", userId, purchaseAmount);
        EligibilityResponse eligibility = applicationService.checkEligibility(userId, purchaseAmount);
        return ResponseEntity.ok(eligibility);
    }
    
    @GetMapping("/merchant/{merchantId}")
    @Operation(summary = "List merchant applications", description = "Retrieves applications for a merchant")
    @PreAuthorize("hasRole('MERCHANT') and @merchantService.isMerchantOwner(#merchantId, #userId)")
    public ResponseEntity<Page<BnplApplicationResponse>> getMerchantApplications(
            @PathVariable UUID merchantId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {
        
        log.info("Listing applications for merchant: {} between {} and {}", merchantId, startDate, endDate);
        Page<BnplApplicationResponse> applications = applicationService.getMerchantApplications(
                merchantId, startDate, endDate, pageable);
        return ResponseEntity.ok(applications);
    }
    
    @Data
    @Builder
    public static class EligibilityResponse {
        private boolean eligible;
        private BigDecimal availableCredit;
        private BigDecimal maxPurchaseAmount;
        private Integer maxInstallments;
        private BigDecimal interestRate;
        private boolean downPaymentRequired;
        private BigDecimal minimumDownPayment;
        private List<String> ineligibilityReasons;
    }
}