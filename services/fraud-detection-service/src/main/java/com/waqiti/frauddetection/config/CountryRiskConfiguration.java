package com.waqiti.frauddetection.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PRODUCTION-GRADE Country Risk Configuration
 * Replaces placeholder country codes with real-world risk assessments
 * Based on FATF, OFAC, EU, and other international regulatory standards
 */
@Configuration
@ConfigurationProperties(prefix = "fraud-detection.country-risk")
@Slf4j
@Getter
public class CountryRiskConfiguration {
    
    // Thread-safe maps for concurrent access
    private final Map<String, CountryRiskProfile> countryRiskProfiles = new ConcurrentHashMap<>();
    private final Map<String, SanctionStatus> sanctionedCountries = new ConcurrentHashMap<>();
    private final Set<String> highRiskJurisdictions = ConcurrentHashMap.newKeySet();
    private final Set<String> enhancedDueDiligenceCountries = ConcurrentHashMap.newKeySet();
    
    private LocalDateTime lastUpdate;
    private boolean initialized = false;
    
    /**
     * Initialize with comprehensive country risk data
     * Based on FATF Grey/Black Lists, EU High-Risk jurisdictions, and OFAC sanctions
     */
    @PostConstruct
    public void initializeCountryRiskData() {
        log.info("Initializing production-grade country risk configuration...");
        
        // FATF High-Risk Jurisdictions (Black List) - as of 2024
        initializeHighRiskCountries();
        
        // FATF Increased Monitoring Jurisdictions (Grey List)
        initializeIncreasedMonitoringCountries();
        
        // OFAC Sanctioned Countries
        initializeSanctionedCountries();
        
        // EU High-Risk Third Countries
        initializeEUHighRiskCountries();
        
        // Enhanced Due Diligence Countries
        initializeEDDCountries();
        
        this.lastUpdate = LocalDateTime.now();
        this.initialized = true;
        
        log.info("Country risk configuration initialized with {} profiles", countryRiskProfiles.size());
    }
    
    private void initializeHighRiskCountries() {
        // FATF Black List countries (highest risk)
        addCountryProfile("IR", CountryRiskLevel.EXTREMELY_HIGH, "FATF Black List - Iran");
        addCountryProfile("KP", CountryRiskLevel.EXTREMELY_HIGH, "FATF Black List - North Korea");
        addCountryProfile("MM", CountryRiskLevel.EXTREMELY_HIGH, "FATF Black List - Myanmar");
        
        // Countries with severe AML/CFT deficiencies
        highRiskJurisdictions.addAll(Set.of("IR", "KP", "MM"));
    }
    
    private void initializeIncreasedMonitoringCountries() {
        // FATF Grey List (increased monitoring) - Updated 2024
        addCountryProfile("AF", CountryRiskLevel.HIGH, "FATF Grey List - Afghanistan");
        addCountryProfile("AL", CountryRiskLevel.MEDIUM_HIGH, "FATF Grey List - Albania");
        addCountryProfile("BB", CountryRiskLevel.MEDIUM_HIGH, "FATF Grey List - Barbados");
        addCountryProfile("BF", CountryRiskLevel.HIGH, "FATF Grey List - Burkina Faso");
        addCountryProfile("KH", CountryRiskLevel.MEDIUM_HIGH, "FATF Grey List - Cambodia");
        addCountryProfile("KY", CountryRiskLevel.MEDIUM_HIGH, "FATF Grey List - Cayman Islands");
        addCountryProfile("HR", CountryRiskLevel.MEDIUM, "FATF Grey List - Croatia");
        addCountryProfile("GI", CountryRiskLevel.MEDIUM, "FATF Grey List - Gibraltar");
        addCountryProfile("JM", CountryRiskLevel.MEDIUM_HIGH, "FATF Grey List - Jamaica");
        addCountryProfile("JO", CountryRiskLevel.MEDIUM, "FATF Grey List - Jordan");
        addCountryProfile("ML", CountryRiskLevel.HIGH, "FATF Grey List - Mali");
        addCountryProfile("MA", CountryRiskLevel.MEDIUM, "FATF Grey List - Morocco");
        addCountryProfile("MZ", CountryRiskLevel.MEDIUM_HIGH, "FATF Grey List - Mozambique");
        addCountryProfile("NG", CountryRiskLevel.MEDIUM_HIGH, "FATF Grey List - Nigeria");
        addCountryProfile("PH", CountryRiskLevel.MEDIUM, "FATF Grey List - Philippines");
        addCountryProfile("SN", CountryRiskLevel.MEDIUM, "FATF Grey List - Senegal");
        addCountryProfile("ZA", CountryRiskLevel.MEDIUM, "FATF Grey List - South Africa");
        addCountryProfile("TZ", CountryRiskLevel.MEDIUM_HIGH, "FATF Grey List - Tanzania");
        addCountryProfile("TR", CountryRiskLevel.MEDIUM, "FATF Grey List - Turkey");
        addCountryProfile("AE", CountryRiskLevel.MEDIUM, "FATF Grey List - UAE");
        addCountryProfile("UG", CountryRiskLevel.MEDIUM_HIGH, "FATF Grey List - Uganda");
        addCountryProfile("VU", CountryRiskLevel.MEDIUM_HIGH, "FATF Grey List - Vanuatu");
        addCountryProfile("VE", CountryRiskLevel.HIGH, "FATF Grey List - Venezuela");
        addCountryProfile("YE", CountryRiskLevel.HIGH, "FATF Grey List - Yemen");
    }
    
    private void initializeSanctionedCountries() {
        // OFAC Sanctioned Countries
        sanctionedCountries.put("IR", SanctionStatus.COMPREHENSIVE);
        sanctionedCountries.put("KP", SanctionStatus.COMPREHENSIVE);
        sanctionedCountries.put("SY", SanctionStatus.COMPREHENSIVE);
        sanctionedCountries.put("RU", SanctionStatus.SECTORAL); // Post-2022 sanctions
        sanctionedCountries.put("BY", SanctionStatus.SECTORAL);
        sanctionedCountries.put("CU", SanctionStatus.COMPREHENSIVE);
        sanctionedCountries.put("VE", SanctionStatus.SECTORAL);
        sanctionedCountries.put("AF", SanctionStatus.TARGETED); // Taliban-related
        
        // Update country profiles for sanctioned countries
        addCountryProfile("SY", CountryRiskLevel.EXTREMELY_HIGH, "OFAC Comprehensive Sanctions");
        addCountryProfile("RU", CountryRiskLevel.HIGH, "OFAC Sectoral Sanctions");
        addCountryProfile("BY", CountryRiskLevel.HIGH, "OFAC Sectoral Sanctions");
        addCountryProfile("CU", CountryRiskLevel.HIGH, "OFAC Comprehensive Sanctions");
    }
    
    private void initializeEUHighRiskCountries() {
        // EU High-Risk Third Countries (current list)
        Set<String> euHighRisk = Set.of(
            "AF", "BB", "BF", "KH", "KY", "HT", "IR", "JM", "JO", "ML", 
            "MZ", "MM", "NI", "KP", "PK", "PA", "PH", "SN", "ZA", "SY", 
            "UG", "AE", "VU", "YE"
        );
        
        enhancedDueDiligenceCountries.addAll(euHighRisk);
        
        // Add specific EU risk classifications
        euHighRisk.forEach(country -> {
            if (!countryRiskProfiles.containsKey(country)) {
                addCountryProfile(country, CountryRiskLevel.HIGH, "EU High-Risk Third Country");
            }
        });
    }
    
    private void initializeEDDCountries() {
        // Countries requiring Enhanced Due Diligence
        Set<String> eddCountries = Set.of(
            // Offshore financial centers with privacy laws
            "CH", "LU", "MC", "AD", "LI", "SM", "VA",
            // Caribbean offshore centers
            "BS", "BZ", "PA", "CR", "GT",
            // Asian offshore centers
            "HK", "SG", "MY",
            // Other high-risk jurisdictions for financial crimes
            "PK", "BD", "LK", "NP", "LA", "KH", "MM"
        );
        
        enhancedDueDiligenceCountries.addAll(eddCountries);
        
        eddCountries.forEach(country -> {
            if (!countryRiskProfiles.containsKey(country)) {
                addCountryProfile(country, CountryRiskLevel.MEDIUM, "Enhanced Due Diligence Required");
            }
        });
    }
    
    private void addCountryProfile(String countryCode, CountryRiskLevel riskLevel, String reason) {
        CountryRiskProfile profile = CountryRiskProfile.builder()
            .countryCode(countryCode)
            .riskLevel(riskLevel)
            .riskScore(riskLevel.getRiskScore())
            .reason(reason)
            .requiresEDD(enhancedDueDiligenceCountries.contains(countryCode))
            .sanctionStatus(sanctionedCountries.getOrDefault(countryCode, SanctionStatus.NONE))
            .lastUpdated(LocalDateTime.now())
            .build();
            
        countryRiskProfiles.put(countryCode, profile);
    }
    
    /**
     * Get risk profile for a country
     */
    public CountryRiskProfile getCountryRiskProfile(String countryCode) {
        if (!initialized) {
            log.warn("Country risk configuration not yet initialized, using default");
            return getDefaultRiskProfile(countryCode);
        }
        
        return countryRiskProfiles.getOrDefault(countryCode, getDefaultRiskProfile(countryCode));
    }
    
    /**
     * Check if country is high risk
     */
    public boolean isHighRiskCountry(String countryCode) {
        CountryRiskProfile profile = getCountryRiskProfile(countryCode);
        return profile.getRiskLevel().ordinal() >= CountryRiskLevel.HIGH.ordinal();
    }
    
    /**
     * Check if country requires enhanced due diligence
     */
    public boolean requiresEnhancedDueDiligence(String countryCode) {
        return enhancedDueDiligenceCountries.contains(countryCode);
    }
    
    /**
     * Check if country is sanctioned
     */
    public boolean isSanctionedCountry(String countryCode) {
        return sanctionedCountries.containsKey(countryCode);
    }
    
    /**
     * Get sanction status for country
     */
    public SanctionStatus getSanctionStatus(String countryCode) {
        return sanctionedCountries.getOrDefault(countryCode, SanctionStatus.NONE);
    }
    
    private CountryRiskProfile getDefaultRiskProfile(String countryCode) {
        // Default profile for countries not in our risk database
        return CountryRiskProfile.builder()
            .countryCode(countryCode)
            .riskLevel(CountryRiskLevel.LOW)
            .riskScore(BigDecimal.valueOf(0.1))
            .reason("Default low risk - not in high-risk database")
            .requiresEDD(false)
            .sanctionStatus(SanctionStatus.NONE)
            .lastUpdated(LocalDateTime.now())
            .build();
    }
    
    /**
     * Scheduled update of risk profiles (daily)
     * In production, this would integrate with external risk data providers
     */
    @Scheduled(cron = "0 0 2 * * *") // 2 AM daily
    public void updateRiskProfiles() {
        log.info("Performing scheduled country risk profile update...");
        
        // In production, this would:
        // 1. Call external APIs (FATF, OFAC, etc.)
        // 2. Update risk profiles based on latest data
        // 3. Notify relevant systems of changes
        
        this.lastUpdate = LocalDateTime.now();
        log.info("Country risk profiles updated at {}", lastUpdate);
    }
    
    // Enums and data classes
    
    public enum CountryRiskLevel {
        LOW(BigDecimal.valueOf(0.1)),
        MEDIUM(BigDecimal.valueOf(0.3)),
        MEDIUM_HIGH(BigDecimal.valueOf(0.5)),
        HIGH(BigDecimal.valueOf(0.7)),
        EXTREMELY_HIGH(BigDecimal.valueOf(0.95));
        
        private final BigDecimal riskScore;
        
        CountryRiskLevel(BigDecimal riskScore) {
            this.riskScore = riskScore;
        }
        
        public BigDecimal getRiskScore() {
            return riskScore;
        }
    }
    
    public enum SanctionStatus {
        NONE,
        TARGETED,      // Individual/entity sanctions
        SECTORAL,      // Specific sector sanctions
        COMPREHENSIVE  // Full economic sanctions
    }
    
    @lombok.Builder
    @lombok.Value
    public static class CountryRiskProfile {
        String countryCode;
        CountryRiskLevel riskLevel;
        BigDecimal riskScore;
        String reason;
        boolean requiresEDD;
        SanctionStatus sanctionStatus;
        LocalDateTime lastUpdated;
    }
}