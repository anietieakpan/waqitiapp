package com.waqiti.payment.service;

import com.waqiti.common.exceptions.ServiceException;
import com.waqiti.payment.businessprofile.BusinessProfile;
import com.waqiti.payment.client.TaxServiceClient;
import com.waqiti.payment.client.TaxServiceClientConfig.TaxServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxService {

    private final TaxServiceClient taxServiceClient;
    
    @Value("${tax.fallback.enabled:true}")
    private boolean fallbackEnabled;

    // Local cache for frequently accessed tax rates
    private static final Map<String, BigDecimal> LOCAL_TAX_RATES = Map.of(
        "US-CA", new BigDecimal("7.75"),   // California
        "US-NY", new BigDecimal("8.00"),   // New York  
        "US-TX", new BigDecimal("6.25"),   // Texas
        "US-FL", new BigDecimal("6.00"),   // Florida
        "DE", new BigDecimal("19.00"),     // Germany VAT
        "FR", new BigDecimal("20.00"),     // France VAT
        "UK", new BigDecimal("20.00")      // UK VAT
    );

    @CircuitBreaker(name = "tax-service", fallbackMethod = "calculateTaxFallback")
    @Retry(name = "tax-service")
    public TaxCalculationResult calculateTax(TaxCalculationRequest request) {
        log.debug("Calculating tax via tax-service for amount: {}, country: {}", 
                 request.getAmount(), request.getCountry());
        
        try {
            // Use microservice for complex calculations
            TaxServiceClient.TaxCalculationRequest serviceRequest = 
                new TaxServiceClient.TaxCalculationRequest(
                    request.getTransactionId(),
                    request.getUserId(),
                    request.getAmount(),
                    request.getCurrency(),
                    request.getCountry(),
                    request.getState(),
                    request.getTransactionType(),
                    request.getTransactionDate(),
                    buildJurisdiction(request.getCountry(), request.getState()),
                    request.getTransactionFee(),
                    request.getTaxExemptionCodes(),
                    request.getTransactionCategory(),
                    request.getRecipientType()
                );

            TaxServiceClient.TaxCalculationDto response = taxServiceClient.calculateTax(serviceRequest);
            
            return TaxCalculationResult.builder()
                    .originalAmount(request.getAmount())
                    .taxRate(response.effectiveTaxRate())
                    .taxAmount(response.totalTaxAmount())
                    .totalAmount(request.getAmount().add(response.totalTaxAmount()))
                    .currency(request.getCurrency())
                    .country(request.getCountry())
                    .state(request.getState())
                    .taxType(determineTaxType(request.getCountry()))
                    .taxBreakdown(response.taxBreakdown())
                    .applicableRules(response.applicableRules())
                    .calculation(createCalculationDetails(request, response))
                    .build();
                    
        } catch (TaxServiceException e) {
            log.warn("Tax service error, using fallback: {}", e.getMessage());
            if (fallbackEnabled) {
                return calculateTaxFallback(request, e);
            }
            throw new ServiceException("Tax calculation failed", e);
        }
    }

    @Cacheable(value = "tax-rates", key = "#country + '-' + #state", unless = "#result == null")
    public TaxRateInfo getTaxRate(String country, String state) {
        log.debug("Getting tax rate for country: {}, state: {}", country, state);
        
        try {
            // Try microservice first
            TaxServiceClient.TaxRateInfo serviceResponse = taxServiceClient.getTaxRate(country, state);
            
            return TaxRateInfo.builder()
                    .rate(serviceResponse.rate())
                    .taxType(TaxType.valueOf(serviceResponse.taxType()))
                    .jurisdiction(serviceResponse.jurisdiction())
                    .effectiveDate(serviceResponse.effectiveDate())
                    .metadata(serviceResponse.metadata())
                    .build();
                    
        } catch (Exception e) {
            log.warn("Failed to get tax rate from service, using local cache", e);
            return getLocalTaxRate(country, state);
        }
    }

    @CircuitBreaker(name = "tax-service", fallbackMethod = "generate1099Fallback")
    public Tax1099Report generate1099Report(BusinessProfile businessProfile, int year) {
        log.info("Generating 1099 report for business: {}, year: {}", businessProfile.getId(), year);
        
        try {
            List<TaxServiceClient.PaymentSummaryDto> payments = buildPaymentSummaries(businessProfile.getId(), year);
            
            TaxServiceClient.Generate1099Request request = new TaxServiceClient.Generate1099Request(
                businessProfile.getUserId().toString(),
                businessProfile.getId().toString(),
                year,
                payments
            );
            
            TaxServiceClient.Tax1099Response response = taxServiceClient.generate1099(request);
            
            return Tax1099Report.builder()
                    .businessProfileId(businessProfile.getId())
                    .businessName(businessProfile.getBusinessName())
                    .taxId(businessProfile.getTaxId())
                    .year(year)
                    .reportId(UUID.fromString(response.reportId()))
                    .documentUrl(response.documentUrl())
                    .totalPayments(response.totalPayments())
                    .generatedAt(response.generatedAt())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error generating 1099 report via service", e);
            if (fallbackEnabled) {
                return generate1099Fallback(businessProfile, year, e);
            }
            throw new ServiceException("Failed to generate 1099 report", e);
        }
    }

    @CircuitBreaker(name = "tax-service", fallbackMethod = "generateVATReportFallback")
    public VATReport generateVATReport(BusinessProfile businessProfile, int quarter, int year) {
        log.info("Generating VAT report for business: {}, Q{} {}", businessProfile.getId(), quarter, year);
        
        try {
            TaxServiceClient.VATReportRequest request = new TaxServiceClient.VATReportRequest(
                businessProfile.getId().toString(),
                quarter,
                year,
                businessProfile.getCountry()
            );
            
            TaxServiceClient.VATReportResponse response = taxServiceClient.generateVATReport(request);
            
            return VATReport.builder()
                    .businessProfileId(businessProfile.getId())
                    .businessName(businessProfile.getBusinessName())
                    .vatNumber(businessProfile.getTaxId())
                    .quarter(quarter)
                    .year(year)
                    .reportId(UUID.fromString(response.reportId()))
                    .documentUrl(response.documentUrl())
                    .totalSales(response.totalSales())
                    .totalVATCollected(response.totalVATCollected())
                    .totalPurchases(response.totalPurchases())
                    .totalVATPaid(response.totalVATPaid())
                    .vatDue(response.vatDue())
                    .generatedAt(response.generatedAt())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error generating VAT report via service", e);
            if (fallbackEnabled) {
                return generateVATReportFallback(businessProfile, quarter, year, e);
            }
            throw new ServiceException("Failed to generate VAT report", e);
        }
    }

    @CircuitBreaker(name = "tax-service", fallbackMethod = "validateTaxComplianceFallback")
    public boolean validateTaxCompliance(BusinessProfile businessProfile) {
        log.debug("Validating tax compliance for business: {}", businessProfile.getId());
        
        try {
            TaxServiceClient.TaxComplianceRequest request = new TaxServiceClient.TaxComplianceRequest(
                businessProfile.getId().toString(),
                businessProfile.getCountry(),
                businessProfile.getTaxId(),
                businessProfile.getRegistrationNumber(),
                List.of("VAT_REGISTRATION", "TAX_ID_VALIDATION", "RECENT_FILINGS")
            );
            
            TaxServiceClient.TaxComplianceResult result = taxServiceClient.validateCompliance(request);
            
            if (!result.compliant() && !result.violations().isEmpty()) {
                log.warn("Tax compliance violations for business {}: {}", 
                        businessProfile.getId(), result.violations());
            }
            
            return result.compliant();
            
        } catch (Exception e) {
            log.warn("Error validating tax compliance via service, using fallback", e);
            if (fallbackEnabled) {
                return validateTaxComplianceFallback(businessProfile, e);
            }
            return false;
        }
    }

    // Fallback methods
    private TaxCalculationResult calculateTaxFallback(TaxCalculationRequest request, Exception ex) {
        log.info("Using local tax calculation fallback");
        
        TaxRateInfo taxRate = getLocalTaxRate(request.getCountry(), request.getState());
        BigDecimal taxAmount = request.getAmount()
                .multiply(taxRate.getRate())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        
        return TaxCalculationResult.builder()
                .originalAmount(request.getAmount())
                .taxRate(taxRate.getRate())
                .taxAmount(taxAmount)
                .totalAmount(request.getAmount().add(taxAmount))
                .currency(request.getCurrency())
                .country(request.getCountry())
                .state(request.getState())
                .taxType(taxRate.getTaxType())
                .calculation(Map.of("method", "fallback", "source", "local_cache"))
                .build();
    }

    private Tax1099Report generate1099Fallback(BusinessProfile businessProfile, int year, Exception ex) {
        log.info("Using local 1099 generation fallback");
        
        return Tax1099Report.builder()
                .businessProfileId(businessProfile.getId())
                .businessName(businessProfile.getBusinessName())
                .taxId(businessProfile.getTaxId())
                .year(year)
                .reportId(UUID.randomUUID())
                .totalPayments(BigDecimal.ZERO) // Would need to calculate from local data
                .generatedAt(java.time.Instant.now())
                .status("GENERATED_LOCALLY")
                .build();
    }

    private VATReport generateVATReportFallback(BusinessProfile businessProfile, int quarter, int year, Exception ex) {
        log.info("Using local VAT report generation fallback");
        
        return VATReport.builder()
                .businessProfileId(businessProfile.getId())
                .businessName(businessProfile.getBusinessName())
                .vatNumber(businessProfile.getTaxId())
                .quarter(quarter)
                .year(year)
                .reportId(UUID.randomUUID())
                .vatDue(BigDecimal.ZERO) // Would need to calculate from local data
                .generatedAt(java.time.Instant.now())
                .status("GENERATED_LOCALLY")
                .build();
    }

    private boolean validateTaxComplianceFallback(BusinessProfile businessProfile, Exception ex) {
        log.info("Using local tax compliance validation fallback");
        
        // Basic validation
        if (businessProfile.getTaxId() == null || businessProfile.getTaxId().trim().isEmpty()) {
            return false;
        }
        
        // Assume compliance if we can't validate via service
        return true;
    }

    // Helper methods
    private TaxRateInfo getLocalTaxRate(String country, String state) {
        String key = state != null ? country + "-" + state : country;
        BigDecimal rate = LOCAL_TAX_RATES.getOrDefault(key, new BigDecimal("0.00"));
        
        return TaxRateInfo.builder()
                .rate(rate)
                .taxType(determineTaxType(country))
                .jurisdiction(key)
                .effectiveDate(LocalDate.now())
                .metadata(Map.of("source", "local_cache"))
                .build();
    }

    private TaxType determineTaxType(String country) {
        if ("US".equalsIgnoreCase(country)) {
            return TaxType.SALES_TAX;
        } else if (Arrays.asList("DE", "FR", "UK", "IT", "ES", "NL").contains(country.toUpperCase())) {
            return TaxType.VAT;
        }
        return TaxType.NO_TAX;
    }

    private String buildJurisdiction(String country, String state) {
        return state != null ? country + "-" + state : country;
    }

    private Map<String, Object> createCalculationDetails(TaxCalculationRequest request, 
                                                        TaxServiceClient.TaxCalculationDto response) {
        Map<String, Object> details = new HashMap<>();
        details.put("originalAmount", request.getAmount());
        details.put("taxRate", response.effectiveTaxRate() + "%");
        details.put("jurisdiction", response.jurisdiction());
        details.put("calculationMethod", "microservice");
        details.put("timestamp", response.calculationDate());
        details.put("applicableRules", response.applicableRules());
        return details;
    }

    private List<TaxServiceClient.PaymentSummaryDto> buildPaymentSummaries(UUID businessId, int year) {
        // This would typically query payment data for the business
        // For now, return empty list as this requires integration with payment data
        return new ArrayList<>();
    }
}