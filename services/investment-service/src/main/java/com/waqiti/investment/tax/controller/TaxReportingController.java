package com.waqiti.investment.tax.controller;

import com.waqiti.investment.security.SecurityUtils;
import com.waqiti.investment.tax.domain.TaxDocument;
import com.waqiti.investment.tax.enums.DocumentType;
import com.waqiti.investment.tax.service.Form1099BService;
import com.waqiti.investment.tax.service.IRSFireIntegrationService;
import com.waqiti.investment.tax.service.TaxReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Tax Reporting REST Controller
 *
 * Provides endpoints for tax document generation and retrieval
 *
 * Security:
 * - All endpoints require authentication
 * - Users can only access their own tax documents
 * - Admin endpoints require ROLE_TAX_ADMIN
 *
 * @author Waqiti Platform Team
 * @since 2025-10-01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tax")
@RequiredArgsConstructor
@Tag(name = "Tax Reporting", description = "IRS tax reporting and Form 1099 generation")
public class TaxReportingController {

    private final TaxReportingService taxReportingService;

    /**
     * Generate all tax documents for a user and tax year
     *
     * POST /api/v1/tax/generate
     */
    @PostMapping("/generate")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Generate tax documents (1099-B, 1099-DIV) for a tax year")
    public ResponseEntity<TaxDocumentGenerationResponse> generateTaxDocuments(
        @RequestBody TaxDocumentGenerationRequest request) {

        log.info("Generating tax documents for user={}, tax_year={}",
            request.getUserId(), request.getTaxYear());

        try {
            // CRITICAL SECURITY: Validate user can only generate their own tax documents
            SecurityUtils.validateUserAccess(request.getUserId());

            Form1099BService.TaxpayerInfo taxpayerInfo = Form1099BService.TaxpayerInfo.builder()
                .tin(request.getTaxpayerTin())
                .name(request.getTaxpayerName())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .state(request.getState())
                .zip(request.getZip())
                .build();

            List<TaxDocument> documents = taxReportingService.generateAllTaxDocuments(
                request.getUserId(),
                request.getInvestmentAccountId(),
                request.getTaxYear(),
                taxpayerInfo);

            TaxDocumentGenerationResponse response = TaxDocumentGenerationResponse.builder()
                .success(true)
                .message("Tax documents generated successfully")
                .documentsGenerated(documents.size())
                .documentNumbers(documents.stream()
                    .map(TaxDocument::getDocumentNumber)
                    .toList())
                .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to generate tax documents", e);
            return ResponseEntity.internalServerError()
                .body(TaxDocumentGenerationResponse.builder()
                    .success(false)
                    .message("Failed to generate tax documents: " + e.getMessage())
                    .documentsGenerated(0)
                    .build());
        }
    }

    /**
     * Get all tax documents for a user and tax year
     *
     * GET /api/v1/tax/documents?userId={userId}&taxYear={taxYear}
     */
    @GetMapping("/documents")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get all tax documents for a user and tax year")
    public ResponseEntity<List<TaxDocumentDto>> getUserTaxDocuments(
        @RequestParam UUID userId,
        @RequestParam Integer taxYear) {

        log.info("Retrieving tax documents for user={}, tax_year={}", userId, taxYear);

        // CRITICAL SECURITY: Validate user can only access their own tax documents
        SecurityUtils.validateUserAccess(userId);

        List<TaxDocument> documents = taxReportingService.getUserTaxDocuments(userId, taxYear);

        List<TaxDocumentDto> response = documents.stream()
            .map(this::convertToDto)
            .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Get specific tax document by type
     *
     * GET /api/v1/tax/document/{documentId}
     */
    @GetMapping("/document/{documentId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get specific tax document by ID")
    public ResponseEntity<TaxDocumentDto> getTaxDocument(@PathVariable UUID documentId) {
        log.info("Retrieving tax document {}", documentId);

        // Retrieve document from repository
        TaxDocument document = taxReportingService.getTaxDocumentById(documentId);

        if (document == null) {
            log.warn("Tax document not found: {}", documentId);
            return ResponseEntity.notFound().build();
        }

        // CRITICAL SECURITY: Validate user can only access their own tax documents
        SecurityUtils.validateUserAccess(document.getUserId());

        TaxDocumentDto response = convertToDto(document);
        return ResponseEntity.ok(response);
    }

    /**
     * Admin: Complete tax filing workflow for a tax year
     *
     * POST /api/v1/tax/admin/file-all
     */
    @PostMapping("/admin/file-all")
    @PreAuthorize("hasRole('TAX_ADMIN')")
    @Operation(summary = "Admin: Complete tax filing workflow (IRS + recipient delivery)")
    public ResponseEntity<TaxReportingService.TaxFilingResult> completeTaxFiling(
        @RequestParam Integer taxYear) {

        log.info("Admin: Initiating complete tax filing workflow for tax_year={}", taxYear);

        TaxReportingService.TaxFilingResult result =
            taxReportingService.completeTaxFilingWorkflow(taxYear);

        return ResponseEntity.ok(result);
    }

    /**
     * Convert TaxDocument entity to DTO
     */
    private TaxDocumentDto convertToDto(TaxDocument document) {
        return TaxDocumentDto.builder()
            .documentId(document.getId())
            .documentNumber(document.getDocumentNumber())
            .documentType(document.getDocumentType())
            .taxYear(document.getTaxYear())
            .filingStatus(document.getFilingStatus())
            .generatedAt(document.getGeneratedAt())
            .filedAt(document.getFiledAt())
            .deliveredAt(document.getDeliveredToRecipientAt())
            .pdfAvailable(document.getPdfFilePath() != null)
            .message("Tax document details")
            .build();
    }

    /**
     * Tax Document Generation Request DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class TaxDocumentGenerationRequest {
        private UUID userId;
        private String investmentAccountId;
        private Integer taxYear;

        // Taxpayer information
        private String taxpayerTin; // Should be encrypted
        private String taxpayerName;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String zip;
    }

    /**
     * Tax Document Generation Response DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class TaxDocumentGenerationResponse {
        private boolean success;
        private String message;
        private int documentsGenerated;
        private List<String> documentNumbers;
    }

    /**
     * Tax Document DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class TaxDocumentDto {
        private UUID documentId;
        private String documentNumber;
        private DocumentType documentType;
        private Integer taxYear;
        private com.waqiti.investment.tax.enums.FilingStatus filingStatus;
        private java.time.LocalDate generatedAt;
        private java.time.LocalDate filedAt;
        private java.time.LocalDate deliveredAt;
        private boolean pdfAvailable;
        private String message;
    }
}
