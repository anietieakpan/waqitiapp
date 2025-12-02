package com.waqiti.kyc.controller;

import com.waqiti.kyc.dto.request.KYCVerificationRequest;
import com.waqiti.kyc.dto.request.ReviewRequest;
import com.waqiti.kyc.dto.response.KYCStatusResponse;
import com.waqiti.kyc.dto.response.KYCVerificationResponse;
import com.waqiti.kyc.dto.response.VerificationHistoryResponse;
import com.waqiti.kyc.service.KYCService;
import com.waqiti.common.ratelimit.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "KYC", description = "KYC verification management APIs")
@SecurityRequirement(name = "bearerAuth")
public class KYCController {

    private final KYCService kycService;

    @Operation(summary = "Initiate KYC verification for a user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Verification initiated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "409", description = "Active verification already exists")
    })
    @PostMapping("/users/{userId}/verifications")
    @PreAuthorize("hasRole('USER') and #userId == authentication.principal.id or hasRole('ADMIN')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 3, refillTokens = 3, refillPeriodMinutes = 10, tokens = 5)
    public ResponseEntity<KYCVerificationResponse> initiateVerification(
            @PathVariable String userId,
            @Valid @RequestBody KYCVerificationRequest request) {
        log.info("Initiating KYC verification for user: {}", userId);
        KYCVerificationResponse response = kycService.initiateVerification(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get verification by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Verification found"),
        @ApiResponse(responseCode = "404", description = "Verification not found")
    })
    @GetMapping("/verifications/{verificationId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE')")
    public ResponseEntity<KYCVerificationResponse> getVerification(
            @PathVariable String verificationId) {
        log.info("Fetching verification: {}", verificationId);
        KYCVerificationResponse response = kycService.getVerification(verificationId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get active verification for user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Active verification found"),
        @ApiResponse(responseCode = "404", description = "No active verification found")
    })
    @GetMapping("/users/{userId}/verifications/active")
    @PreAuthorize("hasRole('USER') and #userId == authentication.principal.id or hasRole('ADMIN')")
    public ResponseEntity<KYCVerificationResponse> getActiveVerification(
            @PathVariable String userId) {
        log.info("Fetching active verification for user: {}", userId);
        KYCVerificationResponse response = kycService.getActiveVerificationForUser(userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get all verifications for user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Verifications retrieved successfully")
    })
    @GetMapping("/users/{userId}/verifications")
    @PreAuthorize("hasRole('USER') and #userId == authentication.principal.id or hasRole('ADMIN')")
    public ResponseEntity<List<KYCVerificationResponse>> getUserVerifications(
            @PathVariable String userId) {
        log.info("Fetching all verifications for user: {}", userId);
        List<KYCVerificationResponse> verifications = kycService.getUserVerifications(userId);
        return ResponseEntity.ok(verifications);
    }

    @Operation(summary = "Get user KYC status")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully")
    })
    @GetMapping("/users/{userId}/status")
    @PreAuthorize("hasRole('USER') and #userId == authentication.principal.id or hasRole('ADMIN')")
    public ResponseEntity<KYCStatusResponse> getUserKYCStatus(
            @PathVariable String userId) {
        log.info("Fetching KYC status for user: {}", userId);
        KYCStatusResponse status = kycService.getUserKYCStatus(userId);
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Update verification status")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid status transition")
    })
    @PutMapping("/verifications/{verificationId}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE')")
    public ResponseEntity<KYCVerificationResponse> updateVerificationStatus(
            @PathVariable String verificationId,
            @RequestParam String status) {
        log.info("Updating verification {} status to: {}", verificationId, status);
        KYCVerificationResponse response = kycService.updateVerificationStatus(verificationId, status);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Review verification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Review completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid review request")
    })
    @PostMapping("/verifications/{verificationId}/review")
    @PreAuthorize("hasRole('COMPLIANCE') or hasRole('ADMIN')")
    public ResponseEntity<KYCVerificationResponse> reviewVerification(
            @PathVariable String verificationId,
            @Valid @RequestBody ReviewRequest request) {
        log.info("Reviewing verification: {}", verificationId);
        KYCVerificationResponse response = kycService.reviewVerification(verificationId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cancel verification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Verification cancelled successfully")
    })
    @DeleteMapping("/verifications/{verificationId}/cancel")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<KYCVerificationResponse> cancelVerification(
            @PathVariable String verificationId,
            @RequestParam(required = false) String reason) {
        log.info("Cancelling verification: {}", verificationId);
        KYCVerificationResponse response = kycService.cancelVerification(verificationId, reason);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get pending reviews")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pending reviews retrieved successfully")
    })
    @GetMapping("/reviews/pending")
    @PreAuthorize("hasRole('COMPLIANCE') or hasRole('ADMIN')")
    public ResponseEntity<Page<KYCVerificationResponse>> getPendingReviews(
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("Fetching pending reviews");
        Page<KYCVerificationResponse> reviews = kycService.getPendingReviews(pageable);
        return ResponseEntity.ok(reviews);
    }

    @Operation(summary = "Get user verification history")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "History retrieved successfully")
    })
    @GetMapping("/users/{userId}/history")
    @PreAuthorize("hasRole('USER') and #userId == authentication.principal.id or hasRole('ADMIN')")
    public ResponseEntity<VerificationHistoryResponse> getUserVerificationHistory(
            @PathVariable String userId) {
        log.info("Fetching verification history for user: {}", userId);
        VerificationHistoryResponse history = kycService.getUserVerificationHistory(userId);
        return ResponseEntity.ok(history);
    }

    @Operation(summary = "Search verifications")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Search results retrieved successfully")
    })
    @GetMapping("/verifications/search")
    @PreAuthorize("hasRole('COMPLIANCE') or hasRole('ADMIN')")
    public ResponseEntity<Page<KYCVerificationResponse>> searchVerifications(
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("Searching verifications with query: {}", query);
        Page<KYCVerificationResponse> results = kycService.searchVerifications(query, pageable);
        return ResponseEntity.ok(results);
    }

    @Operation(summary = "Check if user is verified")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Verification status checked")
    })
    @GetMapping("/users/{userId}/verified")
    @PreAuthorize("hasRole('SERVICE') or hasRole('ADMIN')")
    public ResponseEntity<Boolean> isUserVerified(
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "BASIC") String level) {
        log.info("Checking if user {} is verified at level: {}", userId, level);
        boolean isVerified = kycService.isUserVerified(userId, level);
        return ResponseEntity.ok(isVerified);
    }

    @Operation(summary = "Check if user can perform action")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Action permission checked")
    })
    @GetMapping("/users/{userId}/can-perform")
    @PreAuthorize("hasRole('SERVICE') or hasRole('ADMIN')")
    public ResponseEntity<Boolean> canUserPerformAction(
            @PathVariable String userId,
            @RequestParam String action) {
        log.info("Checking if user {} can perform action: {}", userId, action);
        boolean canPerform = kycService.canUserPerformAction(userId, action);
        return ResponseEntity.ok(canPerform);
    }
}