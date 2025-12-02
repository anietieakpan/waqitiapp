package com.waqiti.purchaseprotection.controller;

import com.waqiti.purchaseprotection.dto.*;
import com.waqiti.purchaseprotection.service.PurchaseProtectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/purchase-protection")
@RequiredArgsConstructor
@Tag(name = "Purchase Protection", description = "Purchase protection and guarantee management endpoints")
@SecurityRequirement(name = "bearer-jwt")
public class PurchaseProtectionController {

    private final PurchaseProtectionService purchaseProtectionService;

    @PostMapping("/protections")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:create')")
    @Operation(summary = "Create purchase protection", description = "Create a new purchase protection policy")
    public ResponseEntity<PurchaseProtectionResponse> createProtection(
            @Valid @RequestBody CreatePurchaseProtectionRequest request) {
        PurchaseProtectionResponse response = purchaseProtectionService.createProtection(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/protections/{protectionId}")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:read')")
    @Operation(summary = "Get protection details", description = "Get details of a specific purchase protection")
    public ResponseEntity<PurchaseProtectionResponse> getProtection(@PathVariable String protectionId) {
        PurchaseProtectionResponse response = purchaseProtectionService.getProtection(protectionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/protections")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:read')")
    @Operation(summary = "List user protections", description = "List all purchase protections for the current user")
    public ResponseEntity<Page<PurchaseProtectionResponse>> getUserProtections(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate,
            Pageable pageable) {
        Page<PurchaseProtectionResponse> protections = purchaseProtectionService.getUserProtections(
                status, startDate, endDate, pageable);
        return ResponseEntity.ok(protections);
    }

    @PostMapping("/claims")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:claim')")
    @Operation(summary = "File a claim", description = "File a claim against a purchase protection")
    public ResponseEntity<ClaimResponse> fileClaim(@Valid @RequestBody FileClaimRequest request) {
        ClaimResponse response = purchaseProtectionService.fileClaim(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/claims/{claimId}")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:read')")
    @Operation(summary = "Get claim details", description = "Get details of a specific claim")
    public ResponseEntity<ClaimResponse> getClaim(@PathVariable String claimId) {
        ClaimResponse response = purchaseProtectionService.getClaim(claimId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/claims/{claimId}/status")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:update')")
    @Operation(summary = "Update claim status", description = "Update the status of a claim")
    public ResponseEntity<ClaimResponse> updateClaimStatus(
            @PathVariable String claimId,
            @Valid @RequestBody UpdateClaimStatusRequest request) {
        ClaimResponse response = purchaseProtectionService.updateClaimStatus(claimId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/claims/{claimId}/documents")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:update')")
    @Operation(summary = "Upload claim documents", description = "Upload supporting documents for a claim")
    public ResponseEntity<DocumentUploadResponse> uploadClaimDocuments(
            @PathVariable String claimId,
            @Valid @RequestBody DocumentUploadRequest request) {
        DocumentUploadResponse response = purchaseProtectionService.uploadClaimDocuments(claimId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/claims")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:read')")
    @Operation(summary = "List user claims", description = "List all claims for the current user")
    public ResponseEntity<Page<ClaimResponse>> getUserClaims(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String protectionId,
            Pageable pageable) {
        Page<ClaimResponse> claims = purchaseProtectionService.getUserClaims(status, protectionId, pageable);
        return ResponseEntity.ok(claims);
    }

    @PostMapping("/disputes/{disputeId}/escalate")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:escalate')")
    @Operation(summary = "Escalate dispute", description = "Escalate a dispute to higher level review")
    public ResponseEntity<DisputeResponse> escalateDispute(
            @PathVariable String disputeId,
            @Valid @RequestBody EscalateDisputeRequest request) {
        DisputeResponse response = purchaseProtectionService.escalateDispute(disputeId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/merchants/{merchantId}/rating")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:read')")
    @Operation(summary = "Get merchant protection rating", description = "Get protection rating for a merchant")
    public ResponseEntity<MerchantProtectionRating> getMerchantRating(@PathVariable String merchantId) {
        MerchantProtectionRating rating = purchaseProtectionService.getMerchantRating(merchantId);
        return ResponseEntity.ok(rating);
    }

    @GetMapping("/coverage/check")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:read')")
    @Operation(summary = "Check coverage eligibility", description = "Check if a transaction is eligible for protection")
    public ResponseEntity<CoverageEligibilityResponse> checkCoverageEligibility(
            @Valid @RequestBody CoverageEligibilityRequest request) {
        CoverageEligibilityResponse response = purchaseProtectionService.checkCoverageEligibility(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/policies")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:read')")
    @Operation(summary = "Get protection policies", description = "Get available purchase protection policies")
    public ResponseEntity<List<ProtectionPolicyResponse>> getProtectionPolicies(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String merchantType) {
        List<ProtectionPolicyResponse> policies = purchaseProtectionService.getProtectionPolicies(
                category, merchantType);
        return ResponseEntity.ok(policies);
    }

    @PostMapping("/refunds/initiate")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:refund')")
    @Operation(summary = "Initiate refund", description = "Initiate a refund under purchase protection")
    public ResponseEntity<RefundResponse> initiateRefund(@Valid @RequestBody InitiateRefundRequest request) {
        RefundResponse response = purchaseProtectionService.initiateRefund(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/refunds/{refundId}")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:read')")
    @Operation(summary = "Get refund status", description = "Get the status of a refund")
    public ResponseEntity<RefundResponse> getRefundStatus(@PathVariable String refundId) {
        RefundResponse response = purchaseProtectionService.getRefundStatus(refundId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:read')")
    @Operation(summary = "Get protection statistics", description = "Get user's purchase protection statistics")
    public ResponseEntity<ProtectionStatistics> getUserStatistics() {
        ProtectionStatistics statistics = purchaseProtectionService.getUserStatistics();
        return ResponseEntity.ok(statistics);
    }

    // Admin endpoints
    @GetMapping("/admin/claims/pending")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:admin')")
    @Operation(summary = "Get pending claims", description = "Get all pending claims for review (Admin)")
    public ResponseEntity<Page<ClaimResponse>> getPendingClaims(Pageable pageable) {
        Page<ClaimResponse> claims = purchaseProtectionService.getPendingClaims(pageable);
        return ResponseEntity.ok(claims);
    }

    @PostMapping("/admin/claims/{claimId}/approve")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:admin')")
    @Operation(summary = "Approve claim", description = "Approve a purchase protection claim (Admin)")
    public ResponseEntity<ClaimResponse> approveClaim(
            @PathVariable String claimId,
            @Valid @RequestBody ApproveClaimRequest request) {
        ClaimResponse response = purchaseProtectionService.approveClaim(claimId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/admin/claims/{claimId}/reject")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:admin')")
    @Operation(summary = "Reject claim", description = "Reject a purchase protection claim (Admin)")
    public ResponseEntity<ClaimResponse> rejectClaim(
            @PathVariable String claimId,
            @Valid @RequestBody RejectClaimRequest request) {
        ClaimResponse response = purchaseProtectionService.rejectClaim(claimId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/analytics")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:admin')")
    @Operation(summary = "Get analytics", description = "Get purchase protection analytics (Admin)")
    public ResponseEntity<Map<String, Object>> getAnalytics(
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {
        Map<String, Object> analytics = purchaseProtectionService.getAnalytics(startDate, endDate);
        return ResponseEntity.ok(analytics);
    }

    @PutMapping("/admin/policies/{policyId}")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:admin')")
    @Operation(summary = "Update policy", description = "Update purchase protection policy (Admin)")
    public ResponseEntity<ProtectionPolicyResponse> updatePolicy(
            @PathVariable String policyId,
            @Valid @RequestBody UpdatePolicyRequest request) {
        ProtectionPolicyResponse response = purchaseProtectionService.updatePolicy(policyId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/admin/merchants/{merchantId}/blacklist")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:admin')")
    @Operation(summary = "Blacklist merchant", description = "Add merchant to protection blacklist (Admin)")
    public ResponseEntity<Void> blacklistMerchant(
            @PathVariable String merchantId,
            @Valid @RequestBody BlacklistMerchantRequest request) {
        purchaseProtectionService.blacklistMerchant(merchantId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/fraud-detection/alerts")
    @PreAuthorize("hasAuthority('SCOPE_purchase_protection:admin')")
    @Operation(summary = "Get fraud alerts", description = "Get fraud detection alerts (Admin)")
    public ResponseEntity<Page<FraudAlertResponse>> getFraudAlerts(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean resolved,
            Pageable pageable) {
        Page<FraudAlertResponse> alerts = purchaseProtectionService.getFraudAlerts(severity, resolved, pageable);
        return ResponseEntity.ok(alerts);
    }
}