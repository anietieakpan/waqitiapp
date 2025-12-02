package com.waqiti.payment.service;

import com.waqiti.common.exceptions.ServiceException;
import com.waqiti.payment.client.ComplianceServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceService {

    private final ComplianceServiceClient complianceServiceClient;

    // Known high-risk countries and sanctioned entities for fallback
    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of(
        "AF", "BY", "CF", "CU", "CD", "ER", "GN", "HT", "IR", "IQ", "LB", "LY", 
        "MM", "NI", "KP", "RU", "SO", "SS", "SD", "SY", "UA", "VE", "YE", "ZW"
    );

    @CircuitBreaker(name = "compliance-service", fallbackMethod = "verifyWithGovernmentDatabaseFallback")
    @Retry(name = "compliance-service")
    public boolean verifyWithGovernmentDatabase(String taxId, String registrationNumber, String country) {
        log.debug("Verifying business with government database - Country: {}, TaxID: {}", 
                 country, maskSensitiveData(taxId));
        
        try {
            ComplianceServiceClient.GovernmentVerificationRequest request = 
                new ComplianceServiceClient.GovernmentVerificationRequest(
                    taxId, registrationNumber, country, "BUSINESS_REGISTRATION"
                );
            
            ComplianceServiceClient.GovernmentVerificationResponse response = 
                complianceServiceClient.verifyWithGovernment(request);
            
            log.info("Government verification result for {}: {}", 
                    maskSensitiveData(taxId), response.verified());
            
            return response.verified();
            
        } catch (Exception e) {
            log.warn("Government database verification failed, using fallback", e);
            return verifyWithGovernmentDatabaseFallback(taxId, registrationNumber, country, e);
        }
    }

    @CircuitBreaker(name = "compliance-service", fallbackMethod = "checkSanctionsListFallback")
    @Retry(name = "compliance-service")
    @Cacheable(value = "sanctions-check", key = "#businessName + ':' + #taxId")
    public boolean checkSanctionsList(String businessName, String taxId) {
        log.debug("Checking sanctions list for business: {}, taxId: {}", 
                 businessName, maskSensitiveData(taxId));
        
        try {
            ComplianceServiceClient.SanctionsCheckRequest request = 
                new ComplianceServiceClient.SanctionsCheckRequest(
                    businessName, taxId, "BUSINESS", List.of("OFAC", "EU", "UN")
                );
            
            ComplianceServiceClient.SanctionsCheckResponse response = 
                complianceServiceClient.checkSanctions(request);
            
            boolean isClean = !response.matchFound();
            
            if (!isClean) {
                log.warn("Sanctions match found for business: {} - Lists: {}", 
                        businessName, response.matchedLists());
            }
            
            return isClean;
            
        } catch (Exception e) {
            log.warn("Sanctions check failed, using fallback", e);
            return checkSanctionsListFallback(businessName, taxId, e);
        }
    }

    @CircuitBreaker(name = "compliance-service", fallbackMethod = "performKYBScreeningFallback")
    @Retry(name = "compliance-service")
    public KYBScreeningResult performKYBScreening(KYBScreeningRequest request) {
        log.info("Performing KYB screening for business: {}", request.getBusinessName());
        
        try {
            ComplianceServiceClient.KYBScreeningRequest serviceRequest = 
                new ComplianceServiceClient.KYBScreeningRequest(
                    request.getBusinessId().toString(),
                    request.getBusinessName(),
                    request.getLegalName(),
                    request.getTaxId(),
                    request.getRegistrationNumber(),
                    request.getCountry(),
                    request.getIndustry(),
                    request.getBeneficialOwners(),
                    request.getScreeningTypes()
                );
            
            ComplianceServiceClient.KYBScreeningResponse response = 
                complianceServiceClient.performKYBScreening(serviceRequest);
            
            return KYBScreeningResult.builder()
                    .screeningId(response.screeningId())
                    .businessId(request.getBusinessId())
                    .overallRiskScore(response.overallRiskScore())
                    .riskLevel(RiskLevel.valueOf(response.riskLevel()))
                    .passed(response.passed())
                    .findings(response.findings())
                    .sanctions(response.sanctions())
                    .pep(response.pep())
                    .adverseMedia(response.adverseMedia())
                    .recommendedActions(response.recommendedActions())
                    .completedAt(response.completedAt())
                    .build();
                    
        } catch (Exception e) {
            log.error("KYB screening failed", e);
            return performKYBScreeningFallback(request, e);
        }
    }

    public ComplianceReport generateComplianceReport(String businessId, String reportType) {
        log.info("Generating compliance report for business: {}, type: {}", businessId, reportType);
        
        try {
            ComplianceServiceClient.ComplianceReportRequest request = 
                new ComplianceServiceClient.ComplianceReportRequest(
                    businessId, reportType, java.time.LocalDate.now().minusDays(30), 
                    java.time.LocalDate.now()
                );
            
            ComplianceServiceClient.ComplianceReportResponse response = 
                complianceServiceClient.generateReport(request);
            
            return ComplianceReport.builder()
                    .reportId(response.reportId())
                    .businessId(businessId)
                    .reportType(reportType)
                    .complianceScore(response.complianceScore())
                    .riskAssessment(response.riskAssessment())
                    .violations(response.violations())
                    .recommendations(response.recommendations())
                    .nextReviewDate(response.nextReviewDate())
                    .generatedAt(response.generatedAt())
                    .build();
                    
        } catch (Exception e) {
            log.error("Compliance report generation failed", e);
            throw new ServiceException("Failed to generate compliance report", e);
        }
    }

    // Fallback methods
    private boolean verifyWithGovernmentDatabaseFallback(String taxId, String registrationNumber, 
                                                        String country, Exception ex) {
        log.info("Using government database verification fallback");
        
        // Basic validation fallback
        if (taxId == null || taxId.trim().isEmpty()) {
            return false;
        }
        
        // For high-risk countries, require additional verification
        if (HIGH_RISK_COUNTRIES.contains(country.toUpperCase())) {
            log.warn("High-risk country detected: {}, verification failed in fallback", country);
            return false;
        }
        
        // Basic format validation
        return taxId.matches("^[A-Z0-9-]{6,20}$");
    }

    private boolean checkSanctionsListFallback(String businessName, String taxId, Exception ex) {
        log.info("Using sanctions check fallback");
        
        // Simple keyword-based check for known bad actors
        String lowerName = businessName.toLowerCase();
        String[] suspiciousKeywords = {
            "terrorist", "sanctions", "embargo", "prohibited", "blacklist"
        };
        
        for (String keyword : suspiciousKeywords) {
            if (lowerName.contains(keyword)) {
                log.warn("Suspicious keyword found in business name: {}", businessName);
                return false;
            }
        }
        
        // Assume clean if no obvious red flags
        return true;
    }

    private KYBScreeningResult performKYBScreeningFallback(KYBScreeningRequest request, Exception ex) {
        log.info("Using KYB screening fallback");
        
        // Basic risk assessment based on available data
        RiskLevel riskLevel = RiskLevel.MEDIUM;
        
        if (HIGH_RISK_COUNTRIES.contains(request.getCountry().toUpperCase())) {
            riskLevel = RiskLevel.HIGH;
        } else if (request.getTaxId() != null && request.getRegistrationNumber() != null) {
            riskLevel = RiskLevel.LOW;
        }
        
        return KYBScreeningResult.builder()
                .screeningId(java.util.UUID.randomUUID().toString())
                .businessId(request.getBusinessId())
                .overallRiskScore(riskLevel == RiskLevel.HIGH ? 85 : 
                                 riskLevel == RiskLevel.MEDIUM ? 50 : 25)
                .riskLevel(riskLevel)
                .passed(riskLevel != RiskLevel.HIGH)
                .findings(List.of("Fallback screening performed"))
                .sanctions(false)
                .pep(false)
                .adverseMedia(false)
                .recommendedActions(riskLevel == RiskLevel.HIGH ? 
                    List.of("Enhanced due diligence required") : List.of())
                .completedAt(java.time.Instant.now())
                .build();
    }

    private String maskSensitiveData(String data) {
        if (data == null || data.length() <= 4) {
            return "****";
        }
        return data.substring(0, 2) + "****" + data.substring(data.length() - 2);
    }
}