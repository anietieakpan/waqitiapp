package com.waqiti.tax.controller;

import com.waqiti.tax.dto.*;
import com.waqiti.tax.entity.TaxDocument;
import com.waqiti.tax.entity.TaxJurisdiction;
import com.waqiti.tax.entity.TaxRule;
import com.waqiti.tax.entity.TaxTransaction;
import com.waqiti.tax.service.TaxService;
import com.waqiti.tax.service.TaxCalculationService;
import com.waqiti.tax.service.TaxReportingService;
import com.waqiti.tax.service.ExternalTaxApiService;
import com.waqiti.tax.repository.TaxJurisdictionRepository;
import com.waqiti.tax.repository.TaxRuleRepository;
import com.waqiti.tax.repository.TaxTransactionRepository;
import com.waqiti.common.util.ResultWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Tax Service REST API Controller
 * 
 * Provides endpoints for comprehensive tax calculation, reporting, and document generation
 */
@RestController
@RequestMapping("/api/v1/tax")
@RequiredArgsConstructor
@Slf4j
@Validated
@CrossOrigin(origins = {"http://localhost:3000", "https://example.com"})
@Tag(name = "Tax Service", description = "Comprehensive tax calculation and reporting operations")
public class TaxController {

    private final TaxService taxService;
    private final TaxCalculationService taxCalculationService;
    private final TaxReportingService taxReportingService;
    private final ExternalTaxApiService externalTaxApiService;
    private final TaxTransactionRepository taxTransactionRepository;
    private final TaxJurisdictionRepository taxJurisdictionRepository;
    private final TaxRuleRepository taxRuleRepository;

    /**
     * Calculate tax for transaction (legacy endpoint)
     */
    @PostMapping("/calculate")
    @Operation(summary = "Calculate tax for transaction", 
               description = "Calculates tax using legacy tax service")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TaxCalculationDto> calculateTax(
            @Valid @RequestBody TaxCalculationRequest request) {
        
        log.info("Calculating tax for user: {} amount: {}", 
            request.getUserId(), request.getAmount());
        
        TaxCalculationDto calculation = taxService.calculateTax(request);
        return ResponseEntity.ok(calculation);
    }

    /**
     * Calculate tax using comprehensive calculation engine
     */
    @PostMapping("/calculate/comprehensive")
    @Operation(summary = "Calculate tax using comprehensive engine", 
               description = "Calculates tax using the comprehensive multi-jurisdiction tax engine")
    @ApiResponse(responseCode = "200", description = "Tax calculation successful")
    @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    @ApiResponse(responseCode = "404", description = "Jurisdiction not found")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'TAX_OFFICER')")
    public ResponseEntity<TaxCalculationResponse> calculateTaxComprehensive(
            @Valid @RequestBody TaxCalculationRequest request) {
        
        log.info("Comprehensive tax calculation for jurisdiction: {}, amount: {}", 
                request.getJurisdiction(), request.getAmount());

        try {
            TaxCalculationResponse response = taxCalculationService.calculateTax(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tax calculation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Tax calculation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Calculate tax with external API validation
     */
    @PostMapping("/calculate/validated")
    @Operation(summary = "Calculate tax with external validation", 
               description = "Calculates tax and validates against external tax APIs")
    @PreAuthorize("hasAnyRole('ADMIN', 'TAX_OFFICER')")
    public ResponseEntity<TaxCalculationResponse> calculateTaxWithValidation(
            @Valid @RequestBody TaxCalculationRequest request) {
        
        log.info("Validated tax calculation for jurisdiction: {}", request.getJurisdiction());

        try {
            TaxCalculationResponse response = taxCalculationService.calculateTaxWithValidation(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Validated tax calculation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Generate 1099 form
     */
    @PostMapping("/forms/1099")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ResultWrapper<TaxDocument>> generate1099(
            @Valid @RequestBody Generate1099Request request) {
        
        log.info("Generating 1099 form for user: {} year: {}", 
            request.getUserId(), request.getTaxYear());
        
        ResultWrapper<TaxDocument> result = taxService.generate1099(request);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Get user tax documents
     */
    @GetMapping("/documents")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<TaxDocumentDto>> getUserTaxDocuments(
            @RequestParam String userId,
            @RequestParam(required = false) Integer taxYear,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Getting tax documents for user: {} year: {}", userId, taxYear);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<TaxDocumentDto> documents = 
            taxService.getUserTaxDocuments(userId, taxYear, pageable);
        
        return ResponseEntity.ok(documents.getContent());
    }

    /**
     * Get tax summary for year
     */
    @GetMapping("/summary")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TaxSummaryDto> getTaxSummary(
            @RequestParam String userId,
            @RequestParam Integer taxYear) {
        
        log.info("Getting tax summary for user: {} year: {}", userId, taxYear);
        
        TaxSummaryDto summary = taxService.getTaxSummary(userId, taxYear);
        return ResponseEntity.ok(summary);
    }

    /**
     * Generate comprehensive tax report
     */
    @PostMapping("/report")
    @Operation(summary = "Generate comprehensive tax report", 
               description = "Generates detailed tax reports with analytics")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'TAX_OFFICER')")
    public ResponseEntity<TaxReportResponse> generateTaxReport(
            @Valid @RequestBody TaxReportRequest request) {
        
        log.info("Tax report request for user: {}, period: {} to {}", 
                request.getUserId(), request.getStartDate(), request.getEndDate());

        try {
            TaxReportResponse response = taxReportingService.generateTaxReport(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Tax report generation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Generate user tax summary for specific year
     */
    @GetMapping("/report/user/{userId}/year/{taxYear}")
    @Operation(summary = "Generate user tax summary", 
               description = "Generates annual tax summary for a specific user")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'TAX_OFFICER') and (#userId == authentication.name or hasRole('ADMIN'))")
    public ResponseEntity<TaxReportResponse> generateUserTaxSummary(
            @PathVariable @NotBlank String userId,
            @PathVariable @NotNull Integer taxYear) {
        
        log.info("User tax summary for user: {}, year: {}", userId, taxYear);

        try {
            TaxReportResponse response = taxReportingService.generateUserTaxSummary(userId, taxYear);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("User tax summary generation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get tax transaction by ID
     */
    @GetMapping("/transaction/{transactionId}")
    @Operation(summary = "Get tax transaction details", 
               description = "Retrieves detailed tax transaction information")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'TAX_OFFICER')")
    public ResponseEntity<TaxTransaction> getTaxTransaction(@PathVariable @NotBlank String transactionId) {
        
        Optional<TaxTransaction> transaction = taxTransactionRepository.findByTransactionId(transactionId);
        
        if (transaction.isPresent()) {
            return ResponseEntity.ok(transaction.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get user tax transactions
     */
    @GetMapping("/transaction/user/{userId}")
    @Operation(summary = "Get user tax transactions", 
               description = "Retrieves all tax transactions for a specific user")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'TAX_OFFICER') and (#userId == authentication.name or hasRole('ADMIN'))")
    public ResponseEntity<List<TaxTransaction>> getUserTaxTransactions(
            @PathVariable @NotBlank String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        List<TaxTransaction> transactions;
        
        if (startDate != null && endDate != null) {
            transactions = taxTransactionRepository.findByUserIdAndCalculationDateBetween(
                    userId, startDate, endDate);
        } else {
            transactions = taxTransactionRepository.findByUserIdAndTaxYear(
                    userId, LocalDateTime.now().getYear());
        }
        
        return ResponseEntity.ok(transactions);
    }

    /**
     * Get all active jurisdictions
     */
    @GetMapping("/jurisdiction")
    @Operation(summary = "Get active tax jurisdictions", 
               description = "Retrieves all active tax jurisdictions")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'TAX_OFFICER')")
    public ResponseEntity<List<TaxJurisdiction>> getActiveJurisdictions() {
        List<TaxJurisdiction> jurisdictions = jurisdictionRepository.findByActiveTrue();
        return ResponseEntity.ok(jurisdictions);
    }

    /**
     * Get jurisdiction by code
     */
    @GetMapping("/jurisdiction/{code}")
    @Operation(summary = "Get jurisdiction details", 
               description = "Retrieves detailed jurisdiction information")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'TAX_OFFICER')")
    public ResponseEntity<TaxJurisdiction> getJurisdiction(@PathVariable @NotBlank String code) {
        Optional<TaxJurisdiction> jurisdiction = jurisdictionRepository.findByCode(code);
        
        if (jurisdiction.isPresent()) {
            return ResponseEntity.ok(jurisdiction.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get tax rules for jurisdiction
     */
    @GetMapping("/jurisdiction/{jurisdiction}/rules")
    @Operation(summary = "Get tax rules for jurisdiction", 
               description = "Retrieves all active tax rules for a jurisdiction")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'TAX_OFFICER')")
    public ResponseEntity<List<TaxRule>> getJurisdictionRules(@PathVariable @NotBlank String jurisdiction) {
        List<TaxRule> rules = ruleRepository.findByJurisdictionAndActiveTrue(jurisdiction);
        return ResponseEntity.ok(rules);
    }

    /**
     * Search tax transactions with filters
     */
    @GetMapping("/transaction/search")
    @Operation(summary = "Search tax transactions", 
               description = "Searches tax transactions with various filters")
    @PreAuthorize("hasAnyRole('ADMIN', 'TAX_OFFICER')")
    public ResponseEntity<Page<TaxTransaction>> searchTaxTransactions(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String jurisdiction,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String status,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {
        
        Page<TaxTransaction> transactions = taxTransactionRepository.findWithFilters(
                userId, jurisdiction, transactionType, status, startDate, endDate, pageable);
        
        return ResponseEntity.ok(transactions);
    }

    /**
     * Get high-value transactions
     */
    @GetMapping("/transaction/high-value")
    @Operation(summary = "Get high-value transactions", 
               description = "Retrieves transactions above specified threshold")
    @PreAuthorize("hasAnyRole('ADMIN', 'TAX_OFFICER')")
    public ResponseEntity<List<TaxTransaction>> getHighValueTransactions(
            @RequestParam @Positive BigDecimal threshold,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        List<TaxTransaction> transactions = taxTransactionRepository.findHighValueTransactions(
                threshold, startDate, endDate);
        
        return ResponseEntity.ok(transactions);
    }

    /**
     * Get cross-border transactions
     */
    @GetMapping("/transaction/cross-border")
    @Operation(summary = "Get cross-border transactions", 
               description = "Retrieves all cross-border tax transactions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TAX_OFFICER')")
    public ResponseEntity<List<TaxTransaction>> getCrossBorderTransactions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        List<TaxTransaction> transactions = taxTransactionRepository
                .findByCrossBorderTrueAndCalculationDateBetween(startDate, endDate);
        
        return ResponseEntity.ok(transactions);
    }

    /**
     * Sync tax rules from external APIs
     */
    @PostMapping("/sync/rules")
    @Operation(summary = "Sync tax rules from external APIs", 
               description = "Synchronizes tax rules with external tax authorities")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> syncTaxRules() {
        log.info("Manual tax rules sync initiated");
        
        try {
            externalTaxApiService.syncTaxRulesFromExternalApi();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Tax rules sync failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update tax rates from external sources
     */
    @PostMapping("/sync/rates")
    @Operation(summary = "Update tax rates from external sources", 
               description = "Updates tax rates from external tax authorities")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateTaxRates() {
        log.info("Manual tax rates update initiated");
        
        try {
            externalTaxApiService.updateTaxRatesFromExternalSources();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Tax rates update failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Validate tax calculation against external API
     */
    @PostMapping("/validate")
    @Operation(summary = "Validate tax calculation", 
               description = "Validates internal tax calculation against external APIs")
    @PreAuthorize("hasAnyRole('ADMIN', 'TAX_OFFICER')")
    public ResponseEntity<Boolean> validateTaxCalculation(
            @Valid @RequestBody TaxCalculationRequest request,
            @RequestParam BigDecimal calculatedTaxAmount) {
        
        try {
            TaxCalculationResponse internalResult = TaxCalculationResponse.builder()
                    .totalTaxAmount(calculatedTaxAmount)
                    .build();
            
            boolean isValid = externalTaxApiService.validateTaxCalculation(request, internalResult);
            return ResponseEntity.ok(isValid);
        } catch (Exception e) {
            log.error("Tax calculation validation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Tax service health check", 
               description = "Checks the health of the tax service")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Tax Service is healthy");
    }
}