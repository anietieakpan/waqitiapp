package com.waqiti.payment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@FeignClient(
    name = "tax-service",
    url = "${services.tax-service.url:http://tax-service:8090}",
    configuration = TaxServiceClientConfig.class,
    fallback = TaxServiceClientFallback.class
)
public interface TaxServiceClient {

    @PostMapping("/api/v1/tax/calculate")
    TaxCalculationDto calculateTax(@Valid @RequestBody TaxCalculationRequest request);

    @PostMapping("/api/v1/tax/forms/1099")
    Tax1099Response generate1099(@Valid @RequestBody Generate1099Request request);

    @GetMapping("/api/v1/tax/documents")
    List<TaxDocumentDto> getUserTaxDocuments(
            @RequestParam String userId,
            @RequestParam(required = false) Integer taxYear);

    @GetMapping("/api/v1/tax/summary")
    TaxSummaryDto getTaxSummary(
            @RequestParam String userId,
            @RequestParam Integer taxYear);

    @GetMapping("/api/v1/tax/rates")
    TaxRateInfo getTaxRate(
            @RequestParam String country,
            @RequestParam(required = false) String state);

    @PostMapping("/api/v1/tax/reports/vat")
    VATReportResponse generateVATReport(@Valid @RequestBody VATReportRequest request);

    @PostMapping("/api/v1/tax/compliance/validate")
    TaxComplianceResult validateCompliance(@Valid @RequestBody TaxComplianceRequest request);

    @GetMapping("/api/v1/tax/health")
    String healthCheck();

    // DTOs for Feign Client
    record TaxCalculationRequest(
            String transactionId,
            String userId,
            BigDecimal amount,
            String currency,
            String country,
            String state,
            String transactionType,
            LocalDate transactionDate,
            String jurisdiction,
            BigDecimal transactionFee,
            List<String> taxExemptionCodes,
            String transactionCategory,
            String recipientType
    ) {}

    record TaxCalculationDto(
            String transactionId,
            BigDecimal totalTaxAmount,
            BigDecimal effectiveTaxRate,
            Map<String, BigDecimal> taxBreakdown,
            List<String> applicableRules,
            java.time.LocalDateTime calculationDate,
            String jurisdiction
    ) {}

    record Generate1099Request(
            String userId,
            String businessProfileId,
            int taxYear,
            List<PaymentSummaryDto> payments
    ) {}

    record PaymentSummaryDto(
            String paymentId,
            BigDecimal amount,
            LocalDate paymentDate,
            String paymentType,
            String description
    ) {}

    record Tax1099Response(
            String reportId,
            String userId,
            int taxYear,
            BigDecimal totalPayments,
            String documentUrl,
            java.time.Instant generatedAt
    ) {}

    record TaxDocumentDto(
            String documentId,
            String userId,
            String documentType,
            int taxYear,
            String documentUrl,
            String status,
            java.time.Instant createdAt
    ) {}

    record TaxSummaryDto(
            String userId,
            int taxYear,
            BigDecimal totalIncome,
            BigDecimal totalTaxPaid,
            BigDecimal effectiveTaxRate,
            int transactionCount,
            Map<String, BigDecimal> taxByType
    ) {}

    record TaxRateInfo(
            BigDecimal rate,
            String taxType,
            String jurisdiction,
            LocalDate effectiveDate,
            Map<String, Object> metadata
    ) {}

    record VATReportRequest(
            String businessProfileId,
            int quarter,
            int year,
            String country
    ) {}

    record VATReportResponse(
            String reportId,
            String businessProfileId,
            int quarter,
            int year,
            BigDecimal totalSales,
            BigDecimal totalVATCollected,
            BigDecimal totalPurchases,
            BigDecimal totalVATPaid,
            BigDecimal vatDue,
            String documentUrl,
            java.time.Instant generatedAt
    ) {}

    record TaxComplianceRequest(
            String businessProfileId,
            String country,
            String taxId,
            String registrationNumber,
            List<String> complianceChecks
    ) {}

    record TaxComplianceResult(
            boolean compliant,
            List<String> violations,
            List<String> warnings,
            Map<String, Object> details,
            java.time.Instant checkedAt
    ) {}
}