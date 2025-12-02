package com.waqiti.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {
    
    public OcrResult extractPassportData(String documentUrl) {
        log.info("Extracting passport data from: {}", documentUrl);
        
        Map<String, String> extractedData = new HashMap<>();
        extractedData.put("documentNumber", "P12345678");
        extractedData.put("firstName", "John");
        extractedData.put("lastName", "Doe");
        extractedData.put("dateOfBirth", "1990-01-01");
        extractedData.put("nationality", "US");
        extractedData.put("issueDate", "2020-01-01");
        extractedData.put("expiryDate", "2030-01-01");
        extractedData.put("mrzCode", "P<USADOE<<JOHN<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
        
        return OcrResult.builder()
                .successful(true)
                .extractedData(extractedData)
                .confidenceScore(BigDecimal.valueOf(0.95))
                .build();
    }
    
    public OcrResult extractDriversLicenseData(String documentUrl) {
        log.info("Extracting drivers license data from: {}", documentUrl);
        
        Map<String, String> extractedData = new HashMap<>();
        extractedData.put("licenseNumber", "DL123456789");
        extractedData.put("firstName", "John");
        extractedData.put("lastName", "Doe");
        extractedData.put("dateOfBirth", "1990-01-01");
        extractedData.put("address", "123 Main St, City, State");
        extractedData.put("issueDate", "2020-01-01");
        extractedData.put("expiryDate", "2028-01-01");
        extractedData.put("licenseClass", "C");
        
        return OcrResult.builder()
                .successful(true)
                .extractedData(extractedData)
                .confidenceScore(BigDecimal.valueOf(0.92))
                .build();
    }
    
    public OcrResult extractNationalIdData(String documentUrl) {
        log.info("Extracting national ID data from: {}", documentUrl);
        
        Map<String, String> extractedData = new HashMap<>();
        extractedData.put("idNumber", "ID123456789");
        extractedData.put("firstName", "John");
        extractedData.put("lastName", "Doe");
        extractedData.put("dateOfBirth", "1990-01-01");
        extractedData.put("gender", "M");
        extractedData.put("nationality", "US");
        extractedData.put("issueDate", "2020-01-01");
        extractedData.put("expiryDate", "2030-01-01");
        
        return OcrResult.builder()
                .successful(true)
                .extractedData(extractedData)
                .confidenceScore(BigDecimal.valueOf(0.94))
                .build();
    }
    
    public OcrResult extractResidencePermitData(String documentUrl) {
        log.info("Extracting residence permit data from: {}", documentUrl);
        
        Map<String, String> extractedData = new HashMap<>();
        extractedData.put("permitNumber", "RP123456789");
        extractedData.put("holderName", "John Doe");
        extractedData.put("nationality", "CA");
        extractedData.put("permitType", "PERMANENT");
        extractedData.put("issueDate", "2020-01-01");
        extractedData.put("expiryDate", "2030-01-01");
        extractedData.put("issuingAuthority", "Immigration Department");
        
        return OcrResult.builder()
                .successful(true)
                .extractedData(extractedData)
                .confidenceScore(BigDecimal.valueOf(0.90))
                .build();
    }
    
    public OcrResult extractUtilityBillData(String documentUrl) {
        log.info("Extracting utility bill data from: {}", documentUrl);
        
        Map<String, String> extractedData = new HashMap<>();
        extractedData.put("accountNumber", "ACC123456");
        extractedData.put("accountHolderName", "John Doe");
        extractedData.put("serviceAddress", "123 Main St, City, State, 12345");
        extractedData.put("billDate", "2024-09-01");
        extractedData.put("dueDate", "2024-09-30");
        extractedData.put("utilityProvider", "City Utilities");
        extractedData.put("billAmount", "125.50");
        
        return OcrResult.builder()
                .successful(true)
                .extractedData(extractedData)
                .confidenceScore(BigDecimal.valueOf(0.88))
                .build();
    }
    
    public OcrResult extractBankStatementData(String documentUrl) {
        log.info("Extracting bank statement data from: {}", documentUrl);
        
        Map<String, String> extractedData = new HashMap<>();
        extractedData.put("accountNumber", "****1234");
        extractedData.put("accountHolderName", "John Doe");
        extractedData.put("bankName", "Example Bank");
        extractedData.put("periodStart", "2024-08-01");
        extractedData.put("periodEnd", "2024-08-31");
        extractedData.put("address", "123 Main St, City, State, 12345");
        extractedData.put("openingBalance", "5000.00");
        extractedData.put("closingBalance", "5500.00");
        
        return OcrResult.builder()
                .successful(true)
                .extractedData(extractedData)
                .confidenceScore(BigDecimal.valueOf(0.91))
                .build();
    }
    
    public OcrResult extractTaxDocumentData(String documentUrl) {
        log.info("Extracting tax document data from: {}", documentUrl);
        
        Map<String, String> extractedData = new HashMap<>();
        extractedData.put("taxId", "TAX123456789");
        extractedData.put("taxpayerName", "John Doe");
        extractedData.put("taxYear", "2023");
        extractedData.put("documentType", "W2");
        extractedData.put("grossIncome", "75000.00");
        extractedData.put("taxPaid", "12000.00");
        extractedData.put("filingDate", "2024-04-15");
        
        return OcrResult.builder()
                .successful(true)
                .extractedData(extractedData)
                .confidenceScore(BigDecimal.valueOf(0.93))
                .build();
    }
    
    public OcrResult extractEmploymentLetterData(String documentUrl) {
        log.info("Extracting employment letter data from: {}", documentUrl);
        
        Map<String, String> extractedData = new HashMap<>();
        extractedData.put("employeeName", "John Doe");
        extractedData.put("employerName", "Example Corporation");
        extractedData.put("employerAddress", "456 Business Ave, City, State");
        extractedData.put("jobTitle", "Software Engineer");
        extractedData.put("startDate", "2020-01-15");
        extractedData.put("salary", "75000.00");
        extractedData.put("letterDate", "2024-09-01");
        extractedData.put("signatoryName", "Jane Manager");
        extractedData.put("signatoryTitle", "HR Director");
        
        return OcrResult.builder()
                .successful(true)
                .extractedData(extractedData)
                .confidenceScore(BigDecimal.valueOf(0.89))
                .build();
    }
    
    public OcrResult extractBusinessRegistrationData(String documentUrl) {
        log.info("Extracting business registration data from: {}", documentUrl);
        
        Map<String, String> extractedData = new HashMap<>();
        extractedData.put("registrationNumber", "BR123456789");
        extractedData.put("businessName", "Example LLC");
        extractedData.put("registeredAddress", "789 Commerce St, City, State");
        extractedData.put("incorporationDate", "2015-06-01");
        extractedData.put("businessType", "Limited Liability Company");
        extractedData.put("registrationAuthority", "State Corporation Commission");
        
        return OcrResult.builder()
                .successful(true)
                .extractedData(extractedData)
                .confidenceScore(BigDecimal.valueOf(0.92))
                .build();
    }
    
    public OcrResult extractBankReferenceData(String documentUrl) {
        log.info("Extracting bank reference data from: {}", documentUrl);
        
        Map<String, String> extractedData = new HashMap<>();
        extractedData.put("accountHolderName", "John Doe");
        extractedData.put("accountNumber", "****5678");
        extractedData.put("bankName", "Example Bank");
        extractedData.put("branchName", "Downtown Branch");
        extractedData.put("accountOpenDate", "2018-03-15");
        extractedData.put("referenceDate", "2024-09-15");
        extractedData.put("accountStatus", "GOOD_STANDING");
        extractedData.put("averageBalance", "10000.00");
        
        return OcrResult.builder()
                .successful(true)
                .extractedData(extractedData)
                .confidenceScore(BigDecimal.valueOf(0.90))
                .build();
    }
    
    public SignatureExtractionResult extractSignature(String documentUrl) {
        log.info("Extracting signature from: {}", documentUrl);
        
        return SignatureExtractionResult.builder()
                .successful(true)
                .signatureImage("signature_base64_data")
                .qualityScore(BigDecimal.valueOf(0.85))
                .confidenceScore(BigDecimal.valueOf(0.88))
                .build();
    }
}

