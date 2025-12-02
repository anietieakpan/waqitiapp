package com.waqiti.kyc.controller;

import com.waqiti.common.ratelimit.RateLimited;
import com.waqiti.kyc.domain.DocumentVerification;
import com.waqiti.kyc.dto.request.DocumentVerificationRequest;
import com.waqiti.kyc.dto.response.DocumentVerificationResponse;
import com.waqiti.kyc.dto.response.DocumentVerificationStatusResponse;
import com.waqiti.kyc.service.KYCDocumentVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/kyc/documents")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Document Verification", description = "KYC document verification management")
@SecurityRequirement(name = "bearerAuth")
public class DocumentVerificationController {

    private final KYCDocumentVerificationService documentVerificationService;

    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 60)
    @Operation(summary = "Submit document for verification")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Document submitted for verification"),
        @ApiResponse(responseCode = "400", description = "Invalid document or request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<CompletableFuture<KYCDocumentVerificationService.DocumentVerificationResponse>> verifyDocument(
            @Parameter(description = "Document type", required = true)
            @RequestParam("documentType") @NotNull String documentType,
            
            @Parameter(description = "Document file", required = true)
            @RequestPart("file") @NotNull MultipartFile file,
            
            @Parameter(description = "Expected data to validate against")
            @RequestParam(value = "expectedData", required = false) Map<String, String> expectedData,
            
            Authentication authentication) {
        
        UUID userId = UUID.fromString(authentication.getName());
        log.info("Document verification request for user: {} type: {}", userId, documentType);
        
        CompletableFuture<KYCDocumentVerificationService.DocumentVerificationResponse> result = 
            documentVerificationService.verifyDocument(userId, documentType, file, expectedData);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @GetMapping("/verifications/{verificationId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get verification status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Verification status retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Verification not found")
    })
    public ResponseEntity<DocumentVerificationStatusResponse> getVerificationStatus(
            @PathVariable UUID verificationId,
            Authentication authentication) {
        
        UUID userId = UUID.fromString(authentication.getName());
        
        return documentVerificationService.getVerificationStatus(verificationId, userId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/verifications")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get user's document verifications")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Verifications retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Page<DocumentVerificationStatusResponse>> getUserVerifications(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        
        UUID userId = UUID.fromString(authentication.getName());
        
        Page<DocumentVerificationStatusResponse> verifications = 
            documentVerificationService.getUserVerifications(userId, pageable);
        
        return ResponseEntity.ok(verifications);
    }

    @PostMapping("/verifications/{verificationId}/retry")
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 3, refillTokens = 3, refillPeriodMinutes = 60)
    @Operation(summary = "Retry failed verification")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Retry initiated"),
        @ApiResponse(responseCode = "400", description = "Cannot retry in current state"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Verification not found"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<Void> retryVerification(
            @PathVariable UUID verificationId,
            Authentication authentication) {
        
        UUID userId = UUID.fromString(authentication.getName());
        
        documentVerificationService.retryVerification(verificationId, userId);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/verifications/{verificationId}/download")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Download verification report")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Report downloaded"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Verification or report not found")
    })
    public ResponseEntity<byte[]> downloadVerificationReport(
            @PathVariable UUID verificationId,
            Authentication authentication) {
        
        UUID userId = UUID.fromString(authentication.getName());
        
        return documentVerificationService.downloadVerificationReport(verificationId, userId)
            .map(report -> ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(report))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/verifications/{verificationId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Cancel pending verification")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Verification cancelled"),
        @ApiResponse(responseCode = "400", description = "Cannot cancel in current state"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Verification not found")
    })
    public ResponseEntity<Void> cancelVerification(
            @PathVariable UUID verificationId,
            Authentication authentication) {
        
        UUID userId = UUID.fromString(authentication.getName());
        
        documentVerificationService.cancelVerification(verificationId, userId);
        
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/types")
    @Operation(summary = "Get supported document types")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Document types retrieved")
    })
    public ResponseEntity<KYCDocumentVerificationService.DocumentType[]> getDocumentTypes() {
        return ResponseEntity.ok(KYCDocumentVerificationService.DocumentType.values());
    }

    @GetMapping("/requirements/{documentType}")
    @Operation(summary = "Get document requirements")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Requirements retrieved"),
        @ApiResponse(responseCode = "400", description = "Invalid document type")
    })
    public ResponseEntity<DocumentRequirementsResponse> getDocumentRequirements(
            @PathVariable String documentType) {
        
        return documentVerificationService.getDocumentRequirements(documentType)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.badRequest().build());
    }

    // Admin endpoints

    @GetMapping("/admin/pending-review")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    @Operation(summary = "Get documents pending review")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pending documents retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<Page<DocumentVerificationAdminResponse>> getPendingReview(
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<DocumentVerificationAdminResponse> pending = 
            documentVerificationService.getPendingReview(pageable);
        
        return ResponseEntity.ok(pending);
    }

    @PostMapping("/admin/verifications/{verificationId}/review")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    @Operation(summary = "Review document verification")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Review completed"),
        @ApiResponse(responseCode = "400", description = "Invalid review decision"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Verification not found")
    })
    public ResponseEntity<Void> reviewVerification(
            @PathVariable UUID verificationId,
            @Valid @RequestBody DocumentReviewRequest request,
            Authentication authentication) {
        
        UUID reviewerId = UUID.fromString(authentication.getName());
        
        documentVerificationService.reviewVerification(
            verificationId, 
            reviewerId, 
            request.getDecision(), 
            request.getNotes()
        );
        
        return ResponseEntity.ok().build();
    }

    @GetMapping("/admin/statistics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    @Operation(summary = "Get verification statistics")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Statistics retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<DocumentVerificationStatistics> getStatistics(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        
        DocumentVerificationStatistics stats = 
            documentVerificationService.getStatistics(fromDate, toDate);
        
        return ResponseEntity.ok(stats);
    }

    // DTOs

    @lombok.Data
    public static class DocumentRequirementsResponse {
        private String documentType;
        private List<String> requiredFields;
        private String minResolution;
        private String maxFileSize;
        private List<String> acceptedFormats;
        private String exampleImage;
        private String instructions;
    }

    @lombok.Data
    public static class DocumentVerificationStatusResponse {
        private UUID verificationId;
        private String documentType;
        private DocumentVerification.Status status;
        private Double finalScore;
        private String decisionReason;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;
        private boolean canRetry;
        private boolean canDownloadReport;
    }

    @lombok.Data
    public static class DocumentVerificationAdminResponse {
        private UUID verificationId;
        private UUID userId;
        private String documentType;
        private DocumentVerification.Status status;
        private Double qualityScore;
        private Double authenticityScore;
        private Double dataMatchScore;
        private Double fraudScore;
        private Double finalScore;
        private Map<String, String> extractedData;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;
    }

    @lombok.Data
    public static class DocumentReviewRequest {
        @NotNull
        private ReviewDecision decision;
        
        @Size(max = 1000)
        private String notes;
        
        public enum ReviewDecision {
            APPROVE,
            REJECT,
            REQUEST_RESUBMISSION
        }
    }

    @lombok.Data
    public static class DocumentVerificationStatistics {
        private Long totalVerifications;
        private Long pendingVerifications;
        private Long completedVerifications;
        private Long successRate;
        private Double averageProcessingTimeSeconds;
        private Map<String, Long> verificationsByType;
        private Map<String, Long> verificationsByStatus;
        private List<CommonRejectionReason> commonRejectionReasons;
        
        @lombok.Data
        public static class CommonRejectionReason {
            private String reason;
            private Long count;
            private Double percentage;
        }
    }
}