package com.waqiti.kyc.service;

import com.waqiti.kyc.integration.KYCProvider;
import com.waqiti.kyc.integration.onfido.OnfidoClient;
import com.waqiti.kyc.integration.jumio.JumioClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for identity verification across multiple providers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityVerificationService {

    private final OnfidoClient onfidoClient;
    private final JumioClient jumioClient;
    private final DataValidationService dataValidationService;
    private final FraudDetectionService fraudDetectionService;

    /**
     * Verify user identity with multiple data points
     */
    @CircuitBreaker(name = "identity-verification", fallbackMethod = "verifyIdentityFallback")
    @Retry(name = "identity-verification")
    public IdentityVerificationResult verifyIdentity(String firstName, String lastName, 
                                                    LocalDate dateOfBirth, String nationalId) {
        log.info("Starting identity verification for: {} {}", firstName, lastName);
        
        try {
            // Step 1: Validate input data
            validateIdentityData(firstName, lastName, dateOfBirth, nationalId);
            
            // Step 2: Check for fraud indicators
            FraudCheckResult fraudCheck = fraudDetectionService.checkIdentityFraud(
                firstName, lastName, dateOfBirth, nationalId
            );
            
            if (fraudCheck.isHighRisk()) {
                log.warn("High fraud risk detected for identity verification");
                return IdentityVerificationResult.builder()
                    .verified(false)
                    .confidenceScore(0.0)
                    .reason("High fraud risk detected")
                    .details(Map.of("fraudScore", fraudCheck.getScore()))
                    .build();
            }
            
            // Step 3: Verify with primary provider (Onfido)
            CompletableFuture<ProviderVerificationResult> onfidoFuture = 
                CompletableFuture.supplyAsync(() -> verifyWithOnfido(firstName, lastName, dateOfBirth, nationalId));
            
            // Step 4: Cross-verify with secondary provider (Jumio) for enhanced verification
            CompletableFuture<ProviderVerificationResult> jumioFuture = 
                CompletableFuture.supplyAsync(() -> verifyWithJumio(firstName, lastName, dateOfBirth, nationalId));
            
            // Wait for both results
            ProviderVerificationResult onfidoResult = onfidoFuture.get();
            ProviderVerificationResult jumioResult = jumioFuture.get();
            
            // Step 5: Aggregate results
            return aggregateVerificationResults(onfidoResult, jumioResult, fraudCheck);
            
        } catch (Exception e) {
            log.error("Identity verification failed", e);
            return IdentityVerificationResult.builder()
                .verified(false)
                .confidenceScore(0.0)
                .reason("Verification failed: " + e.getMessage())
                .build();
        }
    }

    /**
     * Verify identity data consistency
     */
    public IdentityConsistencyResult checkIdentityConsistency(Map<String, Object> identityData) {
        log.debug("Checking identity consistency for data points: {}", identityData.size());
        
        IdentityConsistencyResult result = new IdentityConsistencyResult();
        
        // Check name consistency
        if (identityData.containsKey("firstName") && identityData.containsKey("documentFirstName")) {
            String firstName = (String) identityData.get("firstName");
            String docFirstName = (String) identityData.get("documentFirstName");
            
            double nameSimilarity = calculateStringSimilarity(firstName, docFirstName);
            result.setNameConsistency(nameSimilarity);
            
            if (nameSimilarity < 0.8) {
                result.addInconsistency("First name mismatch between provided and document");
            }
        }
        
        // Check date of birth consistency
        if (identityData.containsKey("dateOfBirth") && identityData.containsKey("documentDOB")) {
            LocalDate dob = (LocalDate) identityData.get("dateOfBirth");
            LocalDate docDob = (LocalDate) identityData.get("documentDOB");
            
            if (!dob.equals(docDob)) {
                result.addInconsistency("Date of birth mismatch");
                result.setDobConsistency(0.0);
            } else {
                result.setDobConsistency(1.0);
            }
        }
        
        // Check address consistency
        if (identityData.containsKey("address") && identityData.containsKey("documentAddress")) {
            String address = (String) identityData.get("address");
            String docAddress = (String) identityData.get("documentAddress");
            
            double addressSimilarity = calculateAddressSimilarity(address, docAddress);
            result.setAddressConsistency(addressSimilarity);
            
            if (addressSimilarity < 0.7) {
                result.addInconsistency("Address mismatch between provided and document");
            }
        }
        
        // Calculate overall consistency score
        result.calculateOverallScore();
        
        return result;
    }

    /**
     * Perform watchlist screening
     */
    @Cacheable(value = "watchlist-screening", key = "#firstName + '-' + #lastName + '-' + #dateOfBirth")
    public WatchlistScreeningResult performWatchlistScreening(String firstName, String lastName, 
                                                             LocalDate dateOfBirth, String nationality) {
        log.info("Performing watchlist screening for: {} {}", firstName, lastName);
        
        WatchlistScreeningResult result = new WatchlistScreeningResult();
        
        try {
            // Check sanctions lists
            boolean onSanctionsList = checkSanctionsList(firstName, lastName, dateOfBirth, nationality);
            result.setOnSanctionsList(onSanctionsList);
            
            // Check PEP database
            boolean isPEP = checkPEPDatabase(firstName, lastName, nationality);
            result.setPoliticallyExposed(isPEP);
            
            // Check adverse media
            boolean hasAdverseMedia = checkAdverseMedia(firstName, lastName);
            result.setHasAdverseMedia(hasAdverseMedia);
            
            // Check criminal records (where legally permitted)
            boolean hasCriminalRecord = checkCriminalRecords(firstName, lastName, dateOfBirth);
            result.setHasCriminalRecord(hasCriminalRecord);
            
            // Calculate risk score
            result.calculateRiskScore();
            
        } catch (Exception e) {
            log.error("Watchlist screening failed", e);
            result.setScreeningFailed(true);
            result.setFailureReason(e.getMessage());
        }
        
        return result;
    }

    // Private helper methods

    private void validateIdentityData(String firstName, String lastName, LocalDate dateOfBirth, String nationalId) {
        if (!dataValidationService.isValidName(firstName)) {
            throw new IllegalArgumentException("Invalid first name format");
        }
        
        if (!dataValidationService.isValidName(lastName)) {
            throw new IllegalArgumentException("Invalid last name format");
        }
        
        if (dateOfBirth == null || dateOfBirth.isAfter(LocalDate.now().minusYears(18))) {
            throw new IllegalArgumentException("Invalid date of birth or user under 18");
        }
        
        if (!dataValidationService.isValidNationalId(nationalId)) {
            throw new IllegalArgumentException("Invalid national ID format");
        }
    }

    private ProviderVerificationResult verifyWithOnfido(String firstName, String lastName, 
                                                       LocalDate dateOfBirth, String nationalId) {
        try {
            return onfidoClient.verifyIdentity(firstName, lastName, dateOfBirth, nationalId);
        } catch (Exception e) {
            log.warn("Onfido verification failed: {}", e.getMessage());
            return ProviderVerificationResult.builder()
                .provider("Onfido")
                .verified(false)
                .confidence(0.0)
                .error(e.getMessage())
                .build();
        }
    }

    private ProviderVerificationResult verifyWithJumio(String firstName, String lastName, 
                                                      LocalDate dateOfBirth, String nationalId) {
        try {
            return jumioClient.verifyIdentity(firstName, lastName, dateOfBirth, nationalId);
        } catch (Exception e) {
            log.warn("Jumio verification failed: {}", e.getMessage());
            return ProviderVerificationResult.builder()
                .provider("Jumio")
                .verified(false)
                .confidence(0.0)
                .error(e.getMessage())
                .build();
        }
    }

    private IdentityVerificationResult aggregateVerificationResults(ProviderVerificationResult onfido,
                                                                  ProviderVerificationResult jumio,
                                                                  FraudCheckResult fraudCheck) {
        // Weight the results from different providers
        double onfidoWeight = 0.5;
        double jumioWeight = 0.3;
        double fraudWeight = 0.2;
        
        double overallConfidence = (onfido.getConfidence() * onfidoWeight) +
                                  (jumio.getConfidence() * jumioWeight) +
                                  ((1.0 - fraudCheck.getScore()) * fraudWeight);
        
        boolean verified = onfido.isVerified() || jumio.isVerified();
        
        Map<String, Object> details = new HashMap<>();
        details.put("onfido", onfido.toMap());
        details.put("jumio", jumio.toMap());
        details.put("fraudCheck", fraudCheck.toMap());
        
        return IdentityVerificationResult.builder()
            .verified(verified && overallConfidence > 0.7)
            .confidenceScore(overallConfidence)
            .reason(verified ? "Identity verified successfully" : "Identity verification failed")
            .details(details)
            .build();
    }

    private double calculateStringSimilarity(String str1, String str2) {
        // Implement Levenshtein distance or similar algorithm
        // Simplified implementation for now
        if (str1.equalsIgnoreCase(str2)) return 1.0;
        if (str1.toLowerCase().contains(str2.toLowerCase()) || 
            str2.toLowerCase().contains(str1.toLowerCase())) return 0.8;
        return 0.5;
    }

    private double calculateAddressSimilarity(String addr1, String addr2) {
        // Normalize and compare addresses
        // This would use more sophisticated address parsing in production
        String normalized1 = addr1.toLowerCase().replaceAll("[^a-z0-9]", "");
        String normalized2 = addr2.toLowerCase().replaceAll("[^a-z0-9]", "");
        
        if (normalized1.equals(normalized2)) return 1.0;
        
        // Check if one contains the other (partial match)
        if (normalized1.contains(normalized2) || normalized2.contains(normalized1)) {
            return 0.7;
        }
        
        return 0.3;
    }

    private boolean checkSanctionsList(String firstName, String lastName, LocalDate dateOfBirth, String nationality) {
        log.info("[COMPLIANCE] Checking sanctions lists for: {} {}, DOB: {}, Nationality: {}", 
                firstName, lastName, dateOfBirth, nationality);
        
        try {
            String fullName = (firstName + " " + lastName).toLowerCase().trim();
            
            // Check OFAC SDN List (Specially Designated Nationals)
            if (sanctionsListRepository.existsInOFACList(fullName, dateOfBirth, nationality)) {
                log.warn("[COMPLIANCE ALERT] Match found in OFAC SDN List: {} {}", firstName, lastName);
                auditService.logComplianceViolation("OFAC_SDN_MATCH", fullName, 
                    Map.of("dob", dateOfBirth, "nationality", nationality));
                return true;
            }
            
            // Check UN Sanctions List
            if (sanctionsListRepository.existsInUNList(fullName, dateOfBirth, nationality)) {
                log.warn("[COMPLIANCE ALERT] Match found in UN Sanctions List: {} {}", firstName, lastName);
                auditService.logComplianceViolation("UN_SANCTIONS_MATCH", fullName, 
                    Map.of("dob", dateOfBirth, "nationality", nationality));
                return true;
            }
            
            // Check EU Sanctions List
            if (sanctionsListRepository.existsInEUList(fullName, dateOfBirth, nationality)) {
                log.warn("[COMPLIANCE ALERT] Match found in EU Sanctions List: {} {}", firstName, lastName);
                auditService.logComplianceViolation("EU_SANCTIONS_MATCH", fullName, 
                    Map.of("dob", dateOfBirth, "nationality", nationality));
                return true;
            }
            
            // Check UK HM Treasury List
            if (sanctionsListRepository.existsInUKList(fullName, dateOfBirth, nationality)) {
                log.warn("[COMPLIANCE ALERT] Match found in UK Sanctions List: {} {}", firstName, lastName);
                auditService.logComplianceViolation("UK_SANCTIONS_MATCH", fullName, 
                    Map.of("dob", dateOfBirth, "nationality", nationality));
                return true;
            }
            
            // Fuzzy matching for similar names (catch spelling variations)
            double matchThreshold = 0.85;
            if (sanctionsListRepository.fuzzyMatchExists(fullName, matchThreshold)) {
                log.warn("[COMPLIANCE ALERT] Fuzzy match found in sanctions lists: {} {} (threshold: {})", 
                        firstName, lastName, matchThreshold);
                auditService.logComplianceAlert("SANCTIONS_FUZZY_MATCH", fullName, 
                    Map.of("threshold", matchThreshold, "dob", dateOfBirth));
                // Return true for manual review
                return true;
            }
            
            log.info("[COMPLIANCE] No sanctions match found for: {} {}", firstName, lastName);
            return false;
            
        } catch (Exception e) {
            // CRITICAL: On error, fail closed (assume sanctioned) for security
            log.error("[COMPLIANCE ERROR] Sanctions check failed for: {} {} - FAILING CLOSED", 
                    firstName, lastName, e);
            auditService.logComplianceError("SANCTIONS_CHECK_ERROR", 
                firstName + " " + lastName, e.getMessage());
            return true; // Fail closed - require manual review
        }
    }

    private boolean checkPEPDatabase(String firstName, String lastName, String nationality) {
        log.info("[COMPLIANCE] Checking PEP database for: {} {}, Nationality: {}", 
                firstName, lastName, nationality);
        
        try {
            String fullName = (firstName + " " + lastName).toLowerCase().trim();
            
            // Check Politically Exposed Person (PEP) databases
            if (pepDatabaseRepository.isPoliticallyExposed(fullName, nationality)) {
                log.warn("[COMPLIANCE ALERT] PEP match found: {} {} from {}", 
                        firstName, lastName, nationality);
                auditService.logComplianceAlert("PEP_MATCH", fullName, 
                    Map.of("nationality", nationality, "riskLevel", "HIGH"));
                return true;
            }
            
            // Check for close associates of PEPs (RCA - Relative or Close Associate)
            if (pepDatabaseRepository.isRelativeOrCloseAssociate(fullName, nationality)) {
                log.warn("[COMPLIANCE ALERT] PEP RCA match found: {} {} from {}", 
                        firstName, lastName, nationality);
                auditService.logComplianceAlert("PEP_RCA_MATCH", fullName, 
                    Map.of("nationality", nationality, "riskLevel", "MEDIUM"));
                return true;
            }
            
            // Check for former PEPs (typically 1-2 years after leaving office)
            if (pepDatabaseRepository.isFormerPEP(fullName, nationality)) {
                log.info("[COMPLIANCE] Former PEP detected: {} {} - Enhanced monitoring required", 
                        firstName, lastName);
                auditService.logComplianceAlert("FORMER_PEP_MATCH", fullName, 
                    Map.of("nationality", nationality, "riskLevel", "MEDIUM"));
                return true; // Still requires enhanced due diligence
            }
            
            log.info("[COMPLIANCE] No PEP match found for: {} {}", firstName, lastName);
            return false;
            
        } catch (Exception e) {
            // CRITICAL: On error, fail closed for security
            log.error("[COMPLIANCE ERROR] PEP check failed for: {} {} - FAILING CLOSED", 
                    firstName, lastName, e);
            auditService.logComplianceError("PEP_CHECK_ERROR", 
                firstName + " " + lastName, e.getMessage());
            return true; // Fail closed - require manual review
        }
    }

    private boolean checkAdverseMedia(String firstName, String lastName) {
        log.info("[COMPLIANCE] Checking adverse media for: {} {}", firstName, lastName);
        
        try {
            String fullName = (firstName + " " + lastName).toLowerCase().trim();
            
            // Check for negative news mentions (financial crimes, fraud, corruption)
            AdverseMediaResult mediaResult = adverseMediaService.searchAdverseNews(
                fullName, 
                List.of("fraud", "money laundering", "corruption", "embezzlement", 
                       "financial crime", "terrorism financing", "sanctions violation")
            );
            
            if (mediaResult.hasHighRiskMentions()) {
                log.warn("[COMPLIANCE ALERT] High-risk adverse media found for: {} {} - Severity: {}", 
                        firstName, lastName, mediaResult.getSeverity());
                auditService.logComplianceAlert("ADVERSE_MEDIA_HIGH_RISK", fullName, 
                    Map.of(
                        "severity", mediaResult.getSeverity(),
                        "mentionCount", mediaResult.getMentionCount(),
                        "categories", mediaResult.getCategories()
                    ));
                return true;
            }
            
            if (mediaResult.hasMediumRiskMentions()) {
                log.info("[COMPLIANCE] Medium-risk adverse media found for: {} {} - Enhanced due diligence required", 
                        firstName, lastName);
                auditService.logComplianceAlert("ADVERSE_MEDIA_MEDIUM_RISK", fullName, 
                    Map.of(
                        "severity", mediaResult.getSeverity(),
                        "mentionCount", mediaResult.getMentionCount()
                    ));
                return true; // Requires enhanced review
            }
            
            log.info("[COMPLIANCE] No significant adverse media found for: {} {}", firstName, lastName);
            return false;
            
        } catch (Exception e) {
            // Log error but don't fail closed - adverse media is supplementary
            log.warn("[COMPLIANCE WARNING] Adverse media check failed for: {} {} - {}", 
                    firstName, lastName, e.getMessage());
            auditService.logComplianceWarning("ADVERSE_MEDIA_CHECK_ERROR", 
                firstName + " " + lastName, e.getMessage());
            return false; // Don't block on adverse media check failure
        }
    }

    private boolean checkCriminalRecords(String firstName, String lastName, LocalDate dateOfBirth) {
        log.info("[COMPLIANCE] Checking criminal records for: {} {}, DOB: {}", 
                firstName, lastName, dateOfBirth);
        
        try {
            String fullName = (firstName + " " + lastName).toLowerCase().trim();
            
            // IMPORTANT: Criminal record checks must comply with local laws (FCRA in US, GDPR in EU)
            // Only check if legally permitted and with proper consent
            
            if (!criminalRecordService.isCheckPermittedByJurisdiction()) {
                log.info("[COMPLIANCE] Criminal record check not permitted in this jurisdiction - skipping");
                return false;
            }
            
            // Check for financial crimes specifically relevant to fintech
            CriminalRecordResult result = criminalRecordService.checkFinancialCrimes(
                fullName, 
                dateOfBirth,
                List.of(
                    CrimeCategory.FRAUD,
                    CrimeCategory.MONEY_LAUNDERING,
                    CrimeCategory.EMBEZZLEMENT,
                    CrimeCategory.IDENTITY_THEFT,
                    CrimeCategory.FORGERY,
                    CrimeCategory.WIRE_FRAUD,
                    CrimeCategory.TAX_EVASION,
                    CrimeCategory.RACKETEERING
                )
            );
            
            if (result.hasRelevantConvictions()) {
                log.warn("[COMPLIANCE ALERT] Relevant criminal record found for: {} {} - Categories: {}", 
                        firstName, lastName, result.getConvictionCategories());
                auditService.logComplianceViolation("CRIMINAL_RECORD_FINANCIAL_CRIME", fullName, 
                    Map.of(
                        "dob", dateOfBirth,
                        "convictions", result.getConvictionCategories(),
                        "mostRecentDate", result.getMostRecentConvictionDate()
                    ));
                return true;
            }
            
            // Check if crimes are within relevant time period (typically last 7-10 years)
            if (result.hasRecentFinancialCrimeHistory(7)) {
                log.warn("[COMPLIANCE ALERT] Recent financial crime history for: {} {} (within 7 years)", 
                        firstName, lastName);
                auditService.logComplianceViolation("CRIMINAL_RECORD_RECENT", fullName, 
                    Map.of("dob", dateOfBirth, "yearsBack", 7));
                return true;
            }
            
            log.info("[COMPLIANCE] No relevant criminal records found for: {} {}", firstName, lastName);
            return false;
            
        } catch (Exception e) {
            // Log error but don't fail closed - criminal checks are supplementary in most jurisdictions
            log.warn("[COMPLIANCE WARNING] Criminal record check failed for: {} {} - {}", 
                    firstName, lastName, e.getMessage());
            auditService.logComplianceWarning("CRIMINAL_CHECK_ERROR", 
                firstName + " " + lastName, e.getMessage());
            return false; // Don't block on criminal check failure (unless required by jurisdiction)
        }
    }

    // Fallback method
    public IdentityVerificationResult verifyIdentityFallback(String firstName, String lastName, 
                                                            LocalDate dateOfBirth, String nationalId,
                                                            Exception ex) {
        log.warn("Identity verification service unavailable, using fallback");
        return IdentityVerificationResult.builder()
            .verified(false)
            .confidenceScore(0.0)
            .reason("Service temporarily unavailable - manual review required")
            .requiresManualReview(true)
            .build();
    }

    // Result classes

    @lombok.Data
    @lombok.Builder
    public static class IdentityVerificationResult {
        private boolean verified;
        private double confidenceScore;
        private String reason;
        private Map<String, Object> details;
        private boolean requiresManualReview;
    }

    @lombok.Data
    @lombok.Builder
    public static class ProviderVerificationResult {
        private String provider;
        private boolean verified;
        private double confidence;
        private String error;
        private Map<String, Object> metadata;
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("provider", provider);
            map.put("verified", verified);
            map.put("confidence", confidence);
            if (error != null) map.put("error", error);
            if (metadata != null) map.put("metadata", metadata);
            return map;
        }
    }

    @lombok.Data
    public static class IdentityConsistencyResult {
        private double nameConsistency;
        private double dobConsistency;
        private double addressConsistency;
        private double overallScore;
        private List<String> inconsistencies = new ArrayList<>();
        
        public void addInconsistency(String issue) {
            inconsistencies.add(issue);
        }
        
        public void calculateOverallScore() {
            int count = 0;
            double total = 0;
            
            if (nameConsistency > 0) {
                total += nameConsistency;
                count++;
            }
            if (dobConsistency > 0) {
                total += dobConsistency;
                count++;
            }
            if (addressConsistency > 0) {
                total += addressConsistency;
                count++;
            }
            
            overallScore = count > 0 ? total / count : 0;
        }
    }

    @lombok.Data
    public static class WatchlistScreeningResult {
        private boolean onSanctionsList;
        private boolean politicallyExposed;
        private boolean hasAdverseMedia;
        private boolean hasCriminalRecord;
        private double riskScore;
        private boolean screeningFailed;
        private String failureReason;
        private List<String> matchedLists = new ArrayList<>();
        
        public void calculateRiskScore() {
            double score = 0;
            
            if (onSanctionsList) score += 1.0;
            if (politicallyExposed) score += 0.6;
            if (hasAdverseMedia) score += 0.7;
            if (hasCriminalRecord) score += 0.9;
            
            riskScore = Math.min(score, 1.0);
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class FraudCheckResult {
        private double score;
        private boolean highRisk;
        private String reason;
        private Map<String, Object> indicators;
        
        public boolean isHighRisk() {
            return highRisk || score > 0.7;
        }
        
        public Map<String, Object> toMap() {
            return Map.of(
                "score", score,
                "highRisk", highRisk,
                "reason", reason != null ? reason : "",
                "indicators", indicators != null ? indicators : Map.of()
            );
        }
    }
}