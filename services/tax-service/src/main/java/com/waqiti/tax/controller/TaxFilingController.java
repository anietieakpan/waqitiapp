package com.waqiti.tax.controller;

import com.waqiti.tax.dto.*;
import com.waqiti.tax.service.TaxFilingService;
import com.waqiti.common.security.SecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/tax")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Tax Filing", description = "Comprehensive tax preparation and filing services")
public class TaxFilingController {
    
    private final TaxFilingService taxFilingService;
    private final SecurityContext securityContext;
    
    @PostMapping("/returns")
    @Operation(summary = "Start new tax return", description = "Create a new tax return for the user")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TaxReturnResponse> startTaxReturn(
            @Valid @RequestBody TaxReturnRequest request) {
        
        UUID userId = securityContext.getCurrentUserId();
        log.info("Starting tax return for user: {} for year: {}", userId, request.getTaxYear());
        
        TaxReturn taxReturn = taxFilingService.startTaxReturn(userId, request);
        TaxReturnResponse response = mapToTaxReturnResponse(taxReturn);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/returns")
    @Operation(summary = "Get user's tax returns", description = "Retrieve all tax returns for the current user")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<TaxReturnResponse>> getTaxReturns(
            @Parameter(description = "Pagination parameters") Pageable pageable) {
        
        UUID userId = securityContext.getCurrentUserId();
        Page<TaxReturn> taxReturns = taxFilingService.getUserTaxReturns(userId, pageable);
        Page<TaxReturnResponse> response = taxReturns.map(this::mapToTaxReturnResponse);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/returns/{returnId}")
    @Operation(summary = "Get tax return details", description = "Retrieve detailed information about a specific tax return")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TaxReturnResponse> getTaxReturn(
            @Parameter(description = "Tax return ID") @PathVariable UUID returnId) {
        
        UUID userId = securityContext.getCurrentUserId();
        TaxReturn taxReturn = taxFilingService.getTaxReturnForUser(userId, returnId);
        TaxReturnResponse response = mapToTaxReturnResponse(taxReturn);
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/returns/{returnId}/documents/w2/import")
    @Operation(summary = "Import W-2 forms", description = "Automatically import W-2 forms from IRS and connected employers")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<TaxDocumentDto>> importW2Forms(
            @Parameter(description = "Tax return ID") @PathVariable UUID returnId) {
        
        UUID userId = securityContext.getCurrentUserId();
        validateUserOwnsReturn(userId, returnId);
        
        List<TaxDocument> documents = taxFilingService.importW2Forms(returnId);
        List<TaxDocumentDto> response = documents.stream()
                .map(this::mapToTaxDocumentDto)
                .toList();
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/returns/{returnId}/documents/1099/import")
    @Operation(summary = "Import 1099 forms", description = "Automatically import 1099 forms including 1099-K from Waqiti transactions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<TaxDocumentDto>> import1099Forms(
            @Parameter(description = "Tax return ID") @PathVariable UUID returnId) {
        
        UUID userId = securityContext.getCurrentUserId();
        validateUserOwnsReturn(userId, returnId);
        
        List<TaxDocument> documents = taxFilingService.import1099Forms(returnId);
        List<TaxDocumentDto> response = documents.stream()
                .map(this::mapToTaxDocumentDto)
                .toList();
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/returns/{returnId}/crypto/report")
    @Operation(summary = "Generate crypto tax report", description = "Generate comprehensive cryptocurrency tax report with Form 8949")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CryptoTaxReportDto> generateCryptoTaxReport(
            @Parameter(description = "Tax return ID") @PathVariable UUID returnId) {
        
        UUID userId = securityContext.getCurrentUserId();
        validateUserOwnsReturn(userId, returnId);
        
        CryptoTaxReport report = taxFilingService.generateCryptoTaxReport(returnId);
        CryptoTaxReportDto response = mapToCryptoTaxReportDto(report);
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/returns/{returnId}/estimate")
    @Operation(summary = "Calculate tax estimate", description = "Calculate comprehensive tax estimate with optimization suggestions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TaxEstimateDto> calculateTaxEstimate(
            @Parameter(description = "Tax return ID") @PathVariable UUID returnId,
            @RequestBody(required = false) TaxEstimateRequest request) {
        
        UUID userId = securityContext.getCurrentUserId();
        TaxReturn taxReturn = taxFilingService.getTaxReturnForUser(userId, returnId);
        
        TaxEstimate estimate = taxFilingService.calculateTaxEstimate(taxReturn);
        TaxEstimateDto response = mapToTaxEstimateDto(estimate);
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/returns/{returnId}/file")
    @Operation(summary = "File tax return", description = "Electronically file tax return with IRS and state")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CompletableFuture<FilingResultDto>> fileTaxReturn(
            @Parameter(description = "Tax return ID") @PathVariable UUID returnId,
            @Valid @RequestBody FilingRequest request) {
        
        UUID userId = securityContext.getCurrentUserId();
        validateUserOwnsReturn(userId, returnId);
        
        CompletableFuture<FilingResult> future = taxFilingService.fileTaxReturn(returnId, request);
        CompletableFuture<FilingResultDto> response = future.thenApply(this::mapToFilingResultDto);
        
        return ResponseEntity.accepted().body(response);
    }
    
    @GetMapping("/returns/{returnId}/refund/status")
    @Operation(summary = "Track refund status", description = "Check the status of tax refund with IRS")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RefundStatusDto> trackRefund(
            @Parameter(description = "Tax return ID") @PathVariable UUID returnId) {
        
        UUID userId = securityContext.getCurrentUserId();
        validateUserOwnsReturn(userId, returnId);
        
        RefundStatus status = taxFilingService.trackRefund(returnId);
        RefundStatusDto response = mapToRefundStatusDto(status);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/planning")
    @Operation(summary = "Get tax planning report", description = "Generate year-round tax planning and estimation report")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TaxPlanningReportDto> getTaxPlanningReport() {
        
        UUID userId = securityContext.getCurrentUserId();
        TaxPlanningReport report = taxFilingService.generateTaxPlanning(userId);
        TaxPlanningReportDto response = mapToTaxPlanningReportDto(report);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/documents/{documentId}")
    @Operation(summary = "Get tax document", description = "Retrieve a specific tax document")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TaxDocumentDto> getTaxDocument(
            @Parameter(description = "Document ID") @PathVariable UUID documentId) {
        
        UUID userId = securityContext.getCurrentUserId();
        TaxDocument document = taxFilingService.getTaxDocumentForUser(userId, documentId);
        TaxDocumentDto response = mapToTaxDocumentDto(document);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/forms/{formId}")
    @Operation(summary = "Get tax form", description = "Retrieve a specific generated tax form")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TaxFormDto> getTaxForm(
            @Parameter(description = "Form ID") @PathVariable UUID formId) {
        
        UUID userId = securityContext.getCurrentUserId();
        TaxForm form = taxFilingService.getTaxFormForUser(userId, formId);
        TaxFormDto response = mapToTaxFormDto(form);
        
        return ResponseEntity.ok(response);
    }
    
    // Helper methods for mapping entities to DTOs
    
    private TaxReturnResponse mapToTaxReturnResponse(TaxReturn taxReturn) {
        return TaxReturnResponse.builder()
                .id(taxReturn.getId())
                .userId(taxReturn.getUserId())
                .taxYear(taxReturn.getTaxYear())
                .filingStatus(taxReturn.getFilingStatus())
                .status(taxReturn.getStatus())
                .estimatedRefund(taxReturn.getEstimatedRefund())
                .estimatedTax(taxReturn.getEstimatedTax())
                .totalIncome(taxReturn.getTotalIncome())
                .adjustedGrossIncome(taxReturn.getAdjustedGrossIncome())
                .federalTax(taxReturn.getFederalTax())
                .stateTax(taxReturn.getStateTax())
                .capitalGains(taxReturn.getCapitalGains())
                .deductions(taxReturn.getDeductions())
                .taxCredits(taxReturn.getTaxCredits())
                .totalWithholdings(taxReturn.getTotalWithholdings())
                .isPremium(taxReturn.getIsPremium())
                .includeCrypto(taxReturn.getIncludeCrypto())
                .includeInvestments(taxReturn.getIncludeInvestments())
                .isStateReturnRequired(taxReturn.getIsStateReturnRequired())
                .refundReceived(taxReturn.getRefundReceived())
                .irsConfirmationNumber(taxReturn.getIrsConfirmationNumber())
                .stateConfirmationNumber(taxReturn.getStateConfirmationNumber())
                .createdAt(taxReturn.getCreatedAt())
                .lastModified(taxReturn.getLastModified())
                .filedAt(taxReturn.getFiledAt())
                .refundReceivedDate(taxReturn.getRefundReceivedDate())
                .personalInfo(mapToPersonalInfoDto(taxReturn.getPersonalInfo()))
                .build();
    }
    
    private TaxReturnResponse.PersonalInfoDto mapToPersonalInfoDto(PersonalInfo personalInfo) {
        if (personalInfo == null) return null;
        
        return TaxReturnResponse.PersonalInfoDto.builder()
                .firstName(personalInfo.getFirstName())
                .lastName(personalInfo.getLastName())
                .email(personalInfo.getEmail())
                .phone(personalInfo.getPhone())
                .address(personalInfo.getAddress())
                .city(personalInfo.getCity())
                .state(personalInfo.getState())
                .zipCode(personalInfo.getZipCode())
                .build();
    }
    
    private void validateUserOwnsReturn(UUID userId, UUID returnId) {
        if (!taxFilingService.userOwnsReturn(userId, returnId)) {
            throw new SecurityException("User does not own the specified tax return");
        }
    }
    
    // Additional mapping methods would go here...
    // For brevity, showing structure with placeholder methods
    
    private TaxDocumentDto mapToTaxDocumentDto(TaxDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Tax document cannot be null");
        }
        return TaxDocumentDto.builder()
                .id(document.getId())
                .returnId(document.getReturnId())
                .documentType(document.getDocumentType())
                .documentNumber(document.getDocumentNumber())
                .employer(document.getEmployer())
                .wages(document.getWages())
                .federalTaxWithheld(document.getFederalTaxWithheld())
                .stateTaxWithheld(document.getStateTaxWithheld())
                .socialSecurityWages(document.getSocialSecurityWages())
                .socialSecurityTaxWithheld(document.getSocialSecurityTaxWithheld())
                .medicareWages(document.getMedicareWages())
                .medicareTaxWithheld(document.getMedicareTaxWithheld())
                .uploadedAt(document.getUploadedAt())
                .verified(document.isVerified())
                .build();
    }
    
    private CryptoTaxReportDto mapToCryptoTaxReportDto(CryptoTaxReport report) {
        if (report == null) {
            throw new IllegalArgumentException("Crypto tax report cannot be null");
        }
        return CryptoTaxReportDto.builder()
                .id(report.getId())
                .returnId(report.getReturnId())
                .totalCapitalGains(report.getTotalCapitalGains())
                .totalCapitalLosses(report.getTotalCapitalLosses())
                .netCapitalGains(report.getNetCapitalGains())
                .shortTermGains(report.getShortTermGains())
                .longTermGains(report.getLongTermGains())
                .totalTransactions(report.getTotalTransactions())
                .form8949Required(report.isForm8949Required())
                .transactions(report.getTransactions())
                .generatedAt(report.getGeneratedAt())
                .build();
    }
    
    private TaxEstimateDto mapToTaxEstimateDto(TaxEstimate estimate) {
        if (estimate == null) {
            throw new IllegalArgumentException("Tax estimate cannot be null");
        }
        return TaxEstimateDto.builder()
                .id(estimate.getId())
                .returnId(estimate.getReturnId())
                .estimatedFederalTax(estimate.getEstimatedFederalTax())
                .estimatedStateTax(estimate.getEstimatedStateTax())
                .estimatedTotalTax(estimate.getEstimatedTotalTax())
                .estimatedRefund(estimate.getEstimatedRefund())
                .effectiveTaxRate(estimate.getEffectiveTaxRate())
                .marginalTaxRate(estimate.getMarginalTaxRate())
                .optimizations(estimate.getOptimizations())
                .calculatedAt(estimate.getCalculatedAt())
                .build();
    }
    
    private FilingResultDto mapToFilingResultDto(FilingResult result) {
        if (result == null) {
            throw new IllegalArgumentException("Filing result cannot be null");
        }
        return FilingResultDto.builder()
                .id(result.getId())
                .returnId(result.getReturnId())
                .filingStatus(result.getFilingStatus())
                .federalConfirmationNumber(result.getFederalConfirmationNumber())
                .stateConfirmationNumber(result.getStateConfirmationNumber())
                .acceptedAt(result.getAcceptedAt())
                .rejectedAt(result.getRejectedAt())
                .rejectionReasons(result.getRejectionReasons())
                .estimatedProcessingTime(result.getEstimatedProcessingTime())
                .refundExpectedDate(result.getRefundExpectedDate())
                .build();
    }
    
    private RefundStatusDto mapToRefundStatusDto(RefundStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Refund status cannot be null");
        }
        return RefundStatusDto.builder()
                .id(status.getId())
                .returnId(status.getReturnId())
                .refundStatus(status.getRefundStatus())
                .refundAmount(status.getRefundAmount())
                .issuedDate(status.getIssuedDate())
                .expectedDate(status.getExpectedDate())
                .trackingNumber(status.getTrackingNumber())
                .paymentMethod(status.getPaymentMethod())
                .lastUpdated(status.getLastUpdated())
                .build();
    }
    
    private TaxPlanningReportDto mapToTaxPlanningReportDto(TaxPlanningReport report) {
        if (report == null) {
            throw new IllegalArgumentException("Tax planning report cannot be null");
        }
        return TaxPlanningReportDto.builder()
                .id(report.getId())
                .userId(report.getUserId())
                .currentYearEstimate(report.getCurrentYearEstimate())
                .nextYearProjection(report.getNextYearProjection())
                .quarterlyEstimates(report.getQuarterlyEstimates())
                .recommendations(report.getRecommendations())
                .potentialSavings(report.getPotentialSavings())
                .riskFactors(report.getRiskFactors())
                .generatedAt(report.getGeneratedAt())
                .build();
    }
    
    private TaxFormDto mapToTaxFormDto(TaxForm form) {
        if (form == null) {
            throw new IllegalArgumentException("Tax form cannot be null");
        }
        return TaxFormDto.builder()
                .id(form.getId())
                .returnId(form.getReturnId())
                .formType(form.getFormType())
                .formNumber(form.getFormNumber())
                .description(form.getDescription())
                .formData(form.getFormData())
                .pdfUrl(form.getPdfUrl())
                .status(form.getStatus())
                .generatedAt(form.getGeneratedAt())
                .lastModified(form.getLastModified())
                .build();
    }
}