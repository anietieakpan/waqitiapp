package com.waqiti.payment.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.waqiti.payment.client.TaxServiceClient.*;

@Slf4j
@Component
public class TaxServiceClientFallback implements TaxServiceClient {

    @Override
    public TaxCalculationDto calculateTax(TaxCalculationRequest request) {
        log.warn("FALLBACK ACTIVATED: Using default tax calculation - Tax Service unavailable. " +
                "TransactionId: {}, Amount: {}, Country: {}", 
                request.transactionId(), request.amount(), request.country());
        
        // Return zero tax temporarily - will need reconciliation
        return new TaxCalculationDto(
                request.transactionId(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Map.of("fallback", BigDecimal.ZERO),
                List.of("TAX_SERVICE_UNAVAILABLE", "REQUIRES_RECONCILIATION"),
                LocalDateTime.now(),
                request.jurisdiction()
        );
    }

    @Override
    public Tax1099Response generate1099(Generate1099Request request) {
        log.error("FALLBACK ACTIVATED: Cannot generate 1099 - Tax Service unavailable. " +
                "User: {}, Year: {}", request.userId(), request.taxYear());
        
        // Queue for later generation
        return new Tax1099Response(
                "QUEUED-" + UUID.randomUUID(),
                request.userId(),
                request.taxYear(),
                request.payments().stream()
                        .map(PaymentSummaryDto::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                null,
                java.time.Instant.now()
        );
    }

    @Override
    public List<TaxDocumentDto> getUserTaxDocuments(String userId, Integer taxYear) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve tax documents - Tax Service unavailable. " +
                "User: {}, Year: {}", userId, taxYear);
        
        return Collections.emptyList();
    }

    @Override
    public TaxSummaryDto getTaxSummary(String userId, Integer taxYear) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve tax summary - Tax Service unavailable. " +
                "User: {}, Year: {}", userId, taxYear);
        
        return new TaxSummaryDto(
                userId,
                taxYear,
                null,
                null,
                null,
                0,
                Collections.emptyMap()
        );
    }

    @Override
    public TaxRateInfo getTaxRate(String country, String state) {
        log.warn("FALLBACK ACTIVATED: Using default tax rate - Tax Service unavailable. " +
                "Country: {}, State: {}", country, state);
        
        // Return zero rate - transactions will need tax reconciliation
        return new TaxRateInfo(
                BigDecimal.ZERO,
                "UNAVAILABLE",
                "FALLBACK",
                LocalDate.now(),
                Map.of("fallback", true, "requiresReconciliation", true)
        );
    }

    @Override
    public VATReportResponse generateVATReport(VATReportRequest request) {
        log.error("FALLBACK ACTIVATED: Cannot generate VAT report - Tax Service unavailable. " +
                "Business: {}, Quarter: {}, Year: {}", 
                request.businessProfileId(), request.quarter(), request.year());
        
        // Queue for later generation
        return new VATReportResponse(
                "QUEUED-" + UUID.randomUUID(),
                request.businessProfileId(),
                request.quarter(),
                request.year(),
                null, null, null, null, null,
                null,
                java.time.Instant.now()
        );
    }

    @Override
    public TaxComplianceResult validateCompliance(TaxComplianceRequest request) {
        log.error("FALLBACK ACTIVATED: Cannot validate tax compliance - Tax Service unavailable. " +
                "Business: {}, Country: {}", request.businessProfileId(), request.country());
        
        // Return non-compliant for safety
        return new TaxComplianceResult(
                false,
                List.of("TAX_SERVICE_UNAVAILABLE"),
                List.of("Unable to verify compliance - manual review required"),
                Map.of("fallback", true, "requiresManualReview", true),
                java.time.Instant.now()
        );
    }

    @Override
    public String healthCheck() {
        log.debug("FALLBACK ACTIVATED: Tax Service health check failed");
        return "UNAVAILABLE";
    }
}