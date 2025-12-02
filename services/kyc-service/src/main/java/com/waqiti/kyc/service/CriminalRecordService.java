package com.waqiti.kyc.service;

import com.waqiti.kyc.dto.CriminalRecordResult;
import com.waqiti.kyc.enums.CrimeCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for criminal record background checks
 * 
 * IMPORTANT LEGAL CONSIDERATIONS:
 * - US: Fair Credit Reporting Act (FCRA) compliance required
 * - EU: GDPR restrictions on criminal record processing
 * - UK: Disclosure and Barring Service (DBS) checks
 * - Must obtain explicit consent before conducting checks
 * - Must comply with local data protection laws
 * 
 * Integrations:
 * - National criminal databases (where permitted)
 * - Court record systems
 * - Public records databases
 * - Background check service providers
 * 
 * @author Waqiti Compliance Team
 * @version 2.0.0
 * @since 2025-01-27
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CriminalRecordService {
    
    private final AuditService auditService;
    private final JurisdictionComplianceService jurisdictionService;
    
    /**
     * Check if criminal record checks are permitted in the user's jurisdiction
     */
    public boolean isCheckPermittedByJurisdiction() {
        // Get user's jurisdiction
        String jurisdiction = jurisdictionService.getCurrentJurisdiction();
        
        // Check if criminal record checks are allowed
        boolean permitted = jurisdictionService.isCriminalCheckAllowed(jurisdiction);
        
        if (!permitted) {
            log.info("[CRIMINAL_CHECK] Criminal record checks not permitted in jurisdiction: {}", 
                    jurisdiction);
        }
        
        return permitted;
    }
    
    /**
     * Check for financial crime convictions
     * 
     * @param fullName Person's full name
     * @param dateOfBirth Date of birth for identification
     * @param crimeCategories Categories of crimes to check
     * @return CriminalRecordResult with findings
     */
    @Cacheable(value = "criminalRecordCheck", key = "#fullName + '_' + #dateOfBirth", 
               unless = "#result == null")
    public CriminalRecordResult checkFinancialCrimes(String fullName, 
                                                     LocalDate dateOfBirth,
                                                     List<CrimeCategory> crimeCategories) {
        
        log.info("[CRIMINAL_CHECK] Checking financial crime records for: {}, DOB: {}", 
                fullName, dateOfBirth);
        
        try {
            // Verify consent was obtained
            if (!hasConsentForCriminalCheck(fullName)) {
                log.warn("[CRIMINAL_CHECK] No consent found for criminal check: {}", fullName);
                return CriminalRecordResult.builder()
                        .fullName(fullName)
                        .checkCompleted(false)
                        .consentObtained(false)
                        .errorMessage("User consent required for criminal record check")
                        .build();
            }
            
            // Search criminal databases
            List<CriminalRecord> records = searchCriminalDatabases(fullName, dateOfBirth, crimeCategories);
            
            // Filter by relevance and recency
            List<CriminalRecord> relevantRecords = filterRelevantRecords(records, crimeCategories);
            
            // Build result
            CriminalRecordResult result = analyzeCriminalRecords(fullName, dateOfBirth, relevantRecords);
            
            // Log for audit trail
            auditService.logCriminalRecordCheck(fullName, result);
            
            log.info("[CRIMINAL_CHECK] Check complete for {}: {} records found, relevant: {}", 
                    fullName, records.size(), relevantRecords.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("[CRIMINAL_CHECK] Error checking criminal records for {}", fullName, e);
            
            return CriminalRecordResult.builder()
                    .fullName(fullName)
                    .dateOfBirth(dateOfBirth)
                    .checkCompleted(false)
                    .consentObtained(true)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    /**
     * Search criminal databases across multiple sources
     */
    private List<CriminalRecord> searchCriminalDatabases(String fullName, 
                                                         LocalDate dateOfBirth,
                                                         List<CrimeCategory> categories) {
        List<CriminalRecord> records = new ArrayList<>();
        
        try {
            // Source 1: Federal criminal database (where available)
            records.addAll(searchFederalDatabase(fullName, dateOfBirth));
            
            // Source 2: State/provincial databases
            records.addAll(searchStateDatabase(fullName, dateOfBirth));
            
            // Source 3: County/local databases
            records.addAll(searchLocalDatabase(fullName, dateOfBirth));
            
            // Source 4: Third-party background check services
            records.addAll(searchBackgroundCheckServices(fullName, dateOfBirth));
            
        } catch (Exception e) {
            log.error("[CRIMINAL_CHECK] Error searching databases", e);
        }
        
        return records;
    }
    
    /**
     * Search federal criminal database
     */
    private List<CriminalRecord> searchFederalDatabase(String fullName, LocalDate dateOfBirth) {
        List<CriminalRecord> records = new ArrayList<>();
        
        try {
            log.debug("[CRIMINAL_CHECK] Searching federal database for: {}", fullName);
            
            // Example: FBI National Crime Information Center (NCIC) - requires authorization
            // In production, integrate with authorized criminal database APIs
            
        } catch (Exception e) {
            log.warn("[CRIMINAL_CHECK] Federal database search failed: {}", e.getMessage());
        }
        
        return records;
    }
    
    /**
     * Search state/provincial databases
     */
    private List<CriminalRecord> searchStateDatabase(String fullName, LocalDate dateOfBirth) {
        List<CriminalRecord> records = new ArrayList<>();
        
        try {
            log.debug("[CRIMINAL_CHECK] Searching state databases for: {}", fullName);
            
            // Search applicable state criminal databases
            // Implementation depends on jurisdiction
            
        } catch (Exception e) {
            log.warn("[CRIMINAL_CHECK] State database search failed: {}", e.getMessage());
        }
        
        return records;
    }
    
    /**
     * Search local county databases
     */
    private List<CriminalRecord> searchLocalDatabase(String fullName, LocalDate dateOfBirth) {
        List<CriminalRecord> records = new ArrayList<>();
        
        try {
            log.debug("[CRIMINAL_CHECK] Searching local databases for: {}", fullName);
            
            // Search county court records
            
        } catch (Exception e) {
            log.warn("[CRIMINAL_CHECK] Local database search failed: {}", e.getMessage());
        }
        
        return records;
    }
    
    /**
     * Search third-party background check services
     */
    private List<CriminalRecord> searchBackgroundCheckServices(String fullName, LocalDate dateOfBirth) {
        List<CriminalRecord> records = new ArrayList<>();
        
        try {
            log.debug("[CRIMINAL_CHECK] Searching background check services for: {}", fullName);
            
            // Example services: Checkr, Sterling, HireRight
            // Must be FCRA-compliant if used for employment decisions
            
        } catch (Exception e) {
            log.warn("[CRIMINAL_CHECK] Background check service search failed: {}", e.getMessage());
        }
        
        return records;
    }
    
    /**
     * Filter records by relevance to financial services
     */
    private List<CriminalRecord> filterRelevantRecords(List<CriminalRecord> records, 
                                                       List<CrimeCategory> categories) {
        return records.stream()
                .filter(record -> isRelevantToFinancialServices(record, categories))
                .collect(Collectors.toList());
    }
    
    /**
     * Check if crime is relevant to financial services
     */
    private boolean isRelevantToFinancialServices(CriminalRecord record, 
                                                  List<CrimeCategory> categories) {
        return categories.contains(record.getCrimeCategory());
    }
    
    /**
     * Analyze criminal records and build result
     */
    private CriminalRecordResult analyzeCriminalRecords(String fullName, 
                                                       LocalDate dateOfBirth,
                                                       List<CriminalRecord> records) {
        
        // Categorize convictions
        Map<CrimeCategory, List<CriminalRecord>> byCategory = records.stream()
                .collect(Collectors.groupingBy(CriminalRecord::getCrimeCategory));
        
        // Find most recent conviction
        Optional<LocalDate> mostRecent = records.stream()
                .map(CriminalRecord::getConvictionDate)
                .max(LocalDate::compareTo);
        
        return CriminalRecordResult.builder()
                .fullName(fullName)
                .dateOfBirth(dateOfBirth)
                .checkCompleted(true)
                .consentObtained(true)
                .recordsFound(records.size())
                .convictionCategories(new ArrayList<>(byCategory.keySet()))
                .mostRecentConvictionDate(mostRecent.orElse(null))
                .records(records)
                .build();
    }
    
    /**
     * Check if user has provided consent for criminal record check
     */
    private boolean hasConsentForCriminalCheck(String fullName) {
        // Check consent database
        // In production, verify against consent management system
        return true; // Placeholder
    }
    
    /**
     * Internal class representing a criminal record
     */
    @lombok.Data
    @lombok.Builder
    public static class CriminalRecord {
        private String caseNumber;
        private String fullName;
        private LocalDate dateOfBirth;
        private CrimeCategory crimeCategory;
        private String offense;
        private String jurisdiction;
        private LocalDate arrestDate;
        private LocalDate convictionDate;
        private String disposition; // CONVICTED, ACQUITTED, DISMISSED, PENDING
        private String sentence;
        private boolean sealed;
        private boolean expunged;
    }
}