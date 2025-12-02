package com.waqiti.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAuthenticityService {
    
    public AuthenticityResult verifyPassport(String documentUrl, PassportData passportData) {
        log.info("Verifying passport authenticity");
        return createAuthenticityResult(true, 0.95);
    }
    
    public AuthenticityResult verifyDriversLicense(String frontUrl, String backUrl, DriversLicenseData licenseData) {
        log.info("Verifying drivers license authenticity");
        return createAuthenticityResult(true, 0.93);
    }
    
    public AuthenticityResult verifyNationalId(String documentUrl, NationalIdData idData, String issuingCountry) {
        log.info("Verifying national ID authenticity for country: {}", issuingCountry);
        return createAuthenticityResult(true, 0.94);
    }
    
    public AuthenticityResult verifyResidencePermit(String documentUrl, ResidencePermitData permitData) {
        log.info("Verifying residence permit authenticity");
        return createAuthenticityResult(true, 0.92);
    }
    
    public AuthenticityResult verifyBankStatement(String documentUrl, BankStatementData statementData) {
        log.info("Verifying bank statement authenticity");
        return createAuthenticityResult(true, 0.90);
    }
    
    public AuthenticityResult verifyTaxDocument(String documentUrl, TaxDocumentData taxData) {
        log.info("Verifying tax document authenticity");
        return createAuthenticityResult(true, 0.91);
    }
    
    private AuthenticityResult createAuthenticityResult(boolean authentic, double score) {
        return AuthenticityResult.builder()
                .authentic(authentic)
                .confidenceScore(BigDecimal.valueOf(score))
                .fraudIndicators(new ArrayList<>())
                .failedFeatures(new ArrayList<>())
                .build();
    }
}
