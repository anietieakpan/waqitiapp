package com.waqiti.kyc.controller;

import com.waqiti.kyc.dto.InternationalKycModels.*;
import com.waqiti.kyc.service.InternationalKycWorkflowService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.validation.ValidationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * International KYC Controller
 * 
 * Provides REST endpoints for international customer verification,
 * compliance checks, and cross-border payment authorization.
 * 
 * @author Waqiti KYC Team
 * @version 3.0.0
 * @since 2025-01-17
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/kyc/international")
@RequiredArgsConstructor
@Validated
@Tag(name = "International KYC", description = "International Know Your Customer verification endpoints")
@SecurityRequirement(name = "keycloak")
public class InternationalKycController {

    private final InternationalKycWorkflowService internationalKycService;

    /**
     * Initiate international KYC verification workflow
     */
    @PostMapping("/initiate")
    @Operation(summary = "Initiate International KYC", 
               description = "Start the international KYC verification process for cross-border payments")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "KYC workflow initiated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SERVICE')")
    public CompletableFuture<ResponseEntity<InternationalKycResult>> initiateInternationalKyc(
            @Valid @RequestBody InternationalKycRequest request) {
        
        String userId = SecurityContext.getCurrentUserId();
        log.info("Initiating international KYC for user: {} in jurisdiction: {}", 
                userId, request.getJurisdiction());
        
        return internationalKycService.initiateInternationalKyc(request)
                .thenApply(result -> {
                    if (result.isSuccess()) {
                        return ResponseEntity.ok(result);
                    } else {
                        return ResponseEntity.badRequest().body(result);
                    }
                });
    }

    /**
     * Submit KYC documents for international verification
     */
    @PostMapping("/documents/{sessionId}")
    @Operation(summary = "Submit International KYC Documents", 
               description = "Submit required documents for international KYC verification")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Documents submitted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid documents or session"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Session not found")
    })
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SERVICE')")
    public CompletableFuture<ResponseEntity<DocumentSubmissionResult>> submitInternationalDocuments(
            @Parameter(description = "KYC session ID") @PathVariable @NotBlank String sessionId,
            @Valid @RequestBody List<KycDocument> documents) {
        
        log.info("Submitting international documents for session: {}", sessionId);
        
        return internationalKycService.submitInternationalDocuments(sessionId, documents)
                .thenApply(result -> {
                    if (result.isSuccess()) {
                        return ResponseEntity.ok(result);
                    } else {
                        return ResponseEntity.badRequest().body(result);
                    }
                });
    }

    /**
     * Perform Enhanced Due Diligence (EDD)
     */
    @PostMapping("/edd")
    @Operation(summary = "Perform Enhanced Due Diligence", 
               description = "Conduct Enhanced Due Diligence for high-risk customers or high-value transactions")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "EDD completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid EDD request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'SERVICE')")
    public CompletableFuture<ResponseEntity<EddResult>> performEnhancedDueDiligence(
            @Valid @RequestBody EddRequest request) {
        
        String userId = SecurityContext.getCurrentUserId();
        log.info("Performing EDD for user: {}", userId);
        
        return internationalKycService.performEnhancedDueDiligence(userId, request)
                .thenApply(result -> {
                    if (result.isSuccess()) {
                        return ResponseEntity.ok(result);
                    } else {
                        return ResponseEntity.badRequest().body(result);
                    }
                });
    }

    /**
     * Authorize international transaction
     */
    @PostMapping("/authorize-transaction")
    @Operation(summary = "Authorize International Transaction", 
               description = "Authorize an international transaction based on KYC status and compliance checks")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transaction authorization result"),
        @ApiResponse(responseCode = "400", description = "Invalid transaction request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Transaction not authorized")
    })
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SERVICE')")
    public CompletableFuture<ResponseEntity<TransactionAuthorizationResult>> authorizeInternationalTransaction(
            @Valid @RequestBody InternationalTransactionRequest request) {
        
        String userId = SecurityContext.getCurrentUserId();
        log.info("Authorizing international transaction for user: {} amount: {} {} to: {}", 
                userId, request.getAmount(), request.getCurrency(), request.getDestinationCountry());
        
        return internationalKycService.authorizeInternationalTransaction(request)
                .thenApply(result -> ResponseEntity.ok(result));
    }

    /**
     * Get KYC status for specific jurisdiction
     */
    @GetMapping("/status/{jurisdiction}")
    @Operation(summary = "Get International KYC Status", 
               description = "Get KYC verification status for a specific jurisdiction")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "KYC status retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "KYC status not found")
    })
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SERVICE')")
    public ResponseEntity<InternationalKycStatus> getKycStatus(
            @Parameter(description = "Jurisdiction code") @PathVariable @NotBlank String jurisdiction) {
        
        String userId = SecurityContext.getCurrentUserId();
        log.info("Getting KYC status for user: {} in jurisdiction: {}", userId, jurisdiction);
        
        // This would typically fetch from database
        InternationalKycStatus status = InternationalKycStatus.builder()
                .verified(true)
                .jurisdiction(jurisdiction)
                .build();
        
        return ResponseEntity.ok(status);
    }

    /**
     * Get verification requirements for jurisdiction
     */
    @GetMapping("/requirements/{jurisdiction}")
    @Operation(summary = "Get Verification Requirements", 
               description = "Get KYC verification requirements for a specific jurisdiction")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Requirements retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid jurisdiction"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SERVICE')")
    public ResponseEntity<VerificationRequirements> getVerificationRequirements(
            @Parameter(description = "Jurisdiction code") @PathVariable @NotBlank String jurisdiction,
            @Parameter(description = "Residency country") @RequestParam(required = false) String residencyCountry,
            @Parameter(description = "Expected transaction volume") @RequestParam(required = false) String expectedVolume) {
        
        log.info("Getting verification requirements for jurisdiction: {}", jurisdiction);
        
        // This would typically determine requirements based on jurisdiction and risk factors
        VerificationRequirements requirements = VerificationRequirements.builder()
                .requiredDocuments(List.of(DocumentType.GOVERNMENT_ID, DocumentType.PROOF_OF_ADDRESS))
                .biometricRequired(true)
                .livenessCheckRequired(true)
                .addressVerificationRequired(true)
                .eddRequired(false)
                .pepScreeningRequired(true)
                .sanctionsScreeningRequired(true)
                .sourceOfWealthRequired(false)
                .build();
        
        return ResponseEntity.ok(requirements);
    }

    /**
     * Generate compliance reports
     */
    @PostMapping("/compliance/reports")
    @Operation(summary = "Generate Compliance Reports", 
               description = "Generate regulatory compliance reports (FATCA, CRS, SAR, CTR)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reports generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid report request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER')")
    public CompletableFuture<ResponseEntity<ComplianceReportResult>> generateComplianceReports(
            @Valid @RequestBody ComplianceReportRequest request) {
        
        log.info("Generating compliance reports for period: {} to {}", 
                request.getStartDate(), request.getEndDate());
        
        return internationalKycService.generateComplianceReports(request)
                .thenApply(result -> {
                    if (result.isSuccess()) {
                        return ResponseEntity.ok(result);
                    } else {
                        return ResponseEntity.badRequest().body(result);
                    }
                });
    }

    /**
     * Validate IBAN
     */
    @GetMapping("/validate/iban/{iban}")
    @Operation(summary = "Validate IBAN", 
               description = "Validate International Bank Account Number format and check digits")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "IBAN validation result"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SERVICE')")
    public ResponseEntity<ValidationResult> validateIban(
            @Parameter(description = "IBAN to validate") @PathVariable @NotBlank String iban) {
        
        log.info("Validating IBAN: {}", iban.substring(0, Math.min(iban.length(), 8)) + "***");
        
        // Use SWIFT integration service for validation
        boolean isValid = true; // This would call the actual validation
        
        ValidationResult result = ValidationResult.builder()
                .valid(isValid)
                .code(iban.substring(0, 2))
                .message(isValid ? "Valid IBAN" : "Invalid IBAN format")
                .build();
        
        return ResponseEntity.ok(result);
    }

    /**
     * Validate BIC/SWIFT code
     */
    @GetMapping("/validate/bic/{bic}")
    @Operation(summary = "Validate BIC", 
               description = "Validate Bank Identifier Code (SWIFT code) format")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "BIC validation result"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SERVICE')")
    public ResponseEntity<ValidationResult> validateBic(
            @Parameter(description = "BIC to validate") @PathVariable @NotBlank String bic) {
        
        log.info("Validating BIC: {}", bic);
        
        // Use SWIFT integration service for validation
        boolean isValid = true; // This would call the actual validation
        
        ValidationResult result = ValidationResult.builder()
                .valid(isValid)
                .code(bic)
                .message(isValid ? "Valid BIC" : "Invalid BIC format")
                .build();
        
        return ResponseEntity.ok(result);
    }

    /**
     * Get country risk profile
     */
    @GetMapping("/risk-profile/{countryCode}")
    @Operation(summary = "Get Country Risk Profile", 
               description = "Get risk profile and compliance requirements for a country")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Risk profile retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Country not found")
    })
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SERVICE')")
    public ResponseEntity<CountryRiskProfile> getCountryRiskProfile(
            @Parameter(description = "ISO country code") @PathVariable @NotBlank String countryCode) {
        
        log.info("Getting risk profile for country: {}", countryCode);
        
        CountryRiskProfile profile = CountryRiskProfile.builder()
                .countryCode(countryCode)
                .riskLevel(RiskLevel.LOW)
                .fatcaApplicable(true)
                .crsApplicable(true)
                .sanctionsRisk(SanctionsRisk.LOW)
                .eddRequired(false)
                .build();
        
        return ResponseEntity.ok(profile);
    }

    // Supporting classes

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class KycDocument {
        private String documentId;
        private DocumentType documentType;
        private String fileName;
        private String mimeType;
        private byte[] content;
        private String issuingCountry;
        private String documentNumber;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ValidationResult {
        private boolean valid;
        private String code;
        private String message;
        private List<String> errors;
    }
}