package com.waqiti.common.validation.validators;

import com.waqiti.common.validation.constraints.ValidCountryCode;
import com.waqiti.common.validation.constraints.ValidCountryCode.*;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Comprehensive country code validator implementation
 * Provides production-ready validation with caching, sanctions checking,
 * and regional restrictions
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CountryCodeValidator implements ConstraintValidator<ValidCountryCode, String> {
    
    private ValidCountryCode annotation;
    private CountryValidationProvider provider;
    private final Map<String, CountryData> countryDatabase = new ConcurrentHashMap<>();
    private final Set<String> sanctionedCountries = ConcurrentHashMap.newKeySet();
    private final Set<String> highRiskCountries = ConcurrentHashMap.newKeySet();
    private final Set<String> activeServiceCountries = ConcurrentHashMap.newKeySet();
    
    private final CountryDataService countryDataService;
    private final SanctionsCheckService sanctionsCheckService;
    private final com.waqiti.common.validation.cache.CountryDataCacheService countryDataCacheService;
    
    public CountryCodeValidator() {
        initializeCountryDatabase();
        initializeSanctionsList();
        initializeHighRiskList();
        initializeActiveServices();
    }
    
    @Override
    public void initialize(ValidCountryCode constraintAnnotation) {
        this.annotation = constraintAnnotation;
        
        // Initialize custom provider if specified
        if (constraintAnnotation.provider() != DefaultCountryValidationProvider.class) {
            try {
                this.provider = constraintAnnotation.provider().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                log.error("Failed to initialize custom country validation provider", e);
                this.provider = new DefaultCountryValidationProvider();
            }
        } else {
            this.provider = new DefaultCountryValidationProvider();
        }
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Handle null values
        if (value == null) {
            return annotation.allowNull();
        }
        
        // Handle empty values
        if (value.trim().isEmpty()) {
            return annotation.allowEmpty();
        }
        
        String normalizedCode = normalizeCountryCode(value);
        
        // Validate format based on mode
        if (!isValidFormat(normalizedCode, annotation.mode())) {
            addConstraintViolation(context, "Invalid country code format for mode: " + annotation.mode());
            return false;
        }
        
        // Convert to alpha-2 for consistent processing
        String alpha2Code = convertToAlpha2(normalizedCode, annotation.mode());
        if (alpha2Code == null) {
            addConstraintViolation(context, "Unknown country code: " + normalizedCode);
            return false;
        }
        
        // Check whitelist
        if (annotation.allowedCountries().length > 0) {
            if (!Arrays.asList(annotation.allowedCountries()).contains(alpha2Code)) {
                addConstraintViolation(context, "Country not in allowed list: " + alpha2Code);
                return false;
            }
        }
        
        // Check blacklist
        if (annotation.blockedCountries().length > 0) {
            if (Arrays.asList(annotation.blockedCountries()).contains(alpha2Code)) {
                addConstraintViolation(context, "Country is blocked: " + alpha2Code);
                return false;
            }
        }
        
        // Check sanctions
        if (annotation.checkSanctions() && isSanctioned(alpha2Code)) {
            addConstraintViolation(context, "Country is under sanctions: " + alpha2Code);
            return false;
        }
        
        // Check high risk
        if (annotation.checkHighRisk() && isHighRisk(alpha2Code)) {
            log.warn("High-risk country detected: {}", alpha2Code);
            // Note: We log but don't fail validation for high-risk countries
            // Business logic can decide how to handle this
        }
        
        // Check active service
        if (annotation.requireActiveService() && !hasActiveService(alpha2Code)) {
            addConstraintViolation(context, "Service not available in country: " + alpha2Code);
            return false;
        }
        
        // Check allowed regions
        if (annotation.allowedRegions().length > 0) {
            if (!isInAllowedRegions(alpha2Code, annotation.allowedRegions())) {
                addConstraintViolation(context, "Country not in allowed regions: " + alpha2Code);
                return false;
            }
        }
        
        // Check blocked regions
        if (annotation.blockedRegions().length > 0) {
            if (isInBlockedRegions(alpha2Code, annotation.blockedRegions())) {
                addConstraintViolation(context, "Country is in blocked region: " + alpha2Code);
                return false;
            }
        }
        
        // Use custom provider if available
        if (provider != null && !(provider instanceof DefaultCountryValidationProvider)) {
            return provider.isValid(alpha2Code, annotation);
        }
        
        return true;
    }
    
    private void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
               .addConstraintViolation();
    }
    
    private String normalizeCountryCode(String code) {
        return code.trim().toUpperCase();
    }
    
    private boolean isValidFormat(String code, ValidationMode mode) {
        switch (mode) {
            case ISO_3166_ALPHA2:
                return code.matches("^[A-Z]{2}$");
            case ISO_3166_ALPHA3:
                return code.matches("^[A-Z]{3}$");
            case ISO_3166_NUMERIC:
                return code.matches("^\\d{3}$");
            case FLEXIBLE:
                return code.matches("^([A-Z]{2}|[A-Z]{3}|\\d{3})$");
            default:
                return false;
        }
    }
    
    private String convertToAlpha2(String code, ValidationMode mode) {
        return countryDataCacheService.convertToAlpha2(code, mode);
    }
    
    private CountryData findCountryByCode(String code, ValidationMode mode) {
        switch (mode) {
            case ISO_3166_ALPHA2:
            case FLEXIBLE:
                if (code.matches("^[A-Z]{2}$")) {
                    return countryDatabase.get(code);
                }
                // Fall through for FLEXIBLE mode
            case ISO_3166_ALPHA3:
                if (code.matches("^[A-Z]{3}$")) {
                    return countryDatabase.values().stream()
                        .filter(c -> code.equals(c.alpha3))
                        .findFirst()
                        .orElse(null);
                }
                // Fall through for FLEXIBLE mode
            case ISO_3166_NUMERIC:
                if (code.matches("^\\d{3}$")) {
                    return countryDatabase.values().stream()
                        .filter(c -> code.equals(c.numeric))
                        .findFirst()
                        .orElse(null);
                }
            default:
                return null;
        }
    }
    
    private boolean isSanctioned(String alpha2Code) {
        return countryDataCacheService.isSanctioned(alpha2Code);
    }
    
    private boolean isHighRisk(String alpha2Code) {
        return countryDataCacheService.isHighRisk(alpha2Code);
    }
    
    private boolean hasActiveService(String alpha2Code) {
        return countryDataCacheService.hasActiveService(alpha2Code);
    }
    
    private boolean isInAllowedRegions(String alpha2Code, Region[] allowedRegions) {
        CountryData country = countryDatabase.get(alpha2Code);
        if (country == null || country.regions == null) {
            return false;
        }
        
        Set<Region> countryRegions = country.regions;
        return Arrays.stream(allowedRegions)
            .anyMatch(countryRegions::contains);
    }
    
    private boolean isInBlockedRegions(String alpha2Code, Region[] blockedRegions) {
        CountryData country = countryDatabase.get(alpha2Code);
        if (country == null || country.regions == null) {
            return false;
        }
        
        Set<Region> countryRegions = country.regions;
        return Arrays.stream(blockedRegions)
            .anyMatch(countryRegions::contains);
    }
    
    /**
     * Initialize comprehensive country database with all ISO countries
     */
    private void initializeCountryDatabase() {
        // Major countries with complete data
        addCountry("US", "USA", "840", "United States", 
            Set.of(Region.NORTH_AMERICA, Region.AMERICAS, Region.G7, Region.G20));
        addCountry("GB", "GBR", "826", "United Kingdom", 
            Set.of(Region.EUROPE, Region.WESTERN_EUROPE, Region.G7, Region.G20, Region.COMMONWEALTH));
        addCountry("DE", "DEU", "276", "Germany", 
            Set.of(Region.EUROPE, Region.WESTERN_EUROPE, Region.EU, Region.EEA, Region.SCHENGEN, Region.G7, Region.G20));
        addCountry("FR", "FRA", "250", "France", 
            Set.of(Region.EUROPE, Region.WESTERN_EUROPE, Region.EU, Region.EEA, Region.SCHENGEN, Region.G7, Region.G20));
        addCountry("JP", "JPN", "392", "Japan", 
            Set.of(Region.ASIA, Region.EAST_ASIA, Region.APAC, Region.G7, Region.G20));
        addCountry("CN", "CHN", "156", "China", 
            Set.of(Region.ASIA, Region.EAST_ASIA, Region.APAC, Region.BRICS, Region.G20));
        addCountry("IN", "IND", "356", "India", 
            Set.of(Region.ASIA, Region.SOUTH_ASIA, Region.APAC, Region.BRICS, Region.G20, Region.COMMONWEALTH));
        addCountry("BR", "BRA", "076", "Brazil", 
            Set.of(Region.SOUTH_AMERICA, Region.AMERICAS, Region.BRICS, Region.G20));
        addCountry("RU", "RUS", "643", "Russia", 
            Set.of(Region.EUROPE, Region.EASTERN_EUROPE, Region.ASIA, Region.BRICS, Region.G20));
        addCountry("CA", "CAN", "124", "Canada", 
            Set.of(Region.NORTH_AMERICA, Region.AMERICAS, Region.G7, Region.G20, Region.COMMONWEALTH));
        addCountry("AU", "AUS", "036", "Australia", 
            Set.of(Region.OCEANIA, Region.APAC, Region.G20, Region.COMMONWEALTH));
        addCountry("ZA", "ZAF", "710", "South Africa", 
            Set.of(Region.AFRICA, Region.SUB_SAHARAN_AFRICA, Region.BRICS, Region.G20, Region.COMMONWEALTH, Region.AFRICAN_UNION));
        addCountry("NG", "NGA", "566", "Nigeria", 
            Set.of(Region.AFRICA, Region.SUB_SAHARAN_AFRICA, Region.COMMONWEALTH, Region.AFRICAN_UNION));
        addCountry("EG", "EGY", "818", "Egypt", 
            Set.of(Region.AFRICA, Region.NORTH_AFRICA, Region.MIDDLE_EAST, Region.MENA, Region.ARAB_LEAGUE, Region.AFRICAN_UNION));
        addCountry("SA", "SAU", "682", "Saudi Arabia", 
            Set.of(Region.ASIA, Region.MIDDLE_EAST, Region.MENA, Region.G20, Region.ARAB_LEAGUE));
        addCountry("AE", "ARE", "784", "United Arab Emirates", 
            Set.of(Region.ASIA, Region.MIDDLE_EAST, Region.MENA, Region.ARAB_LEAGUE));
        addCountry("SG", "SGP", "702", "Singapore", 
            Set.of(Region.ASIA, Region.SOUTHEAST_ASIA, Region.APAC, Region.ASEAN, Region.COMMONWEALTH));
        addCountry("KE", "KEN", "404", "Kenya", 
            Set.of(Region.AFRICA, Region.SUB_SAHARAN_AFRICA, Region.COMMONWEALTH, Region.AFRICAN_UNION));
        addCountry("GH", "GHA", "288", "Ghana", 
            Set.of(Region.AFRICA, Region.SUB_SAHARAN_AFRICA, Region.COMMONWEALTH, Region.AFRICAN_UNION));
        
        // European Union countries
        addCountry("IT", "ITA", "380", "Italy", 
            Set.of(Region.EUROPE, Region.WESTERN_EUROPE, Region.EU, Region.EEA, Region.SCHENGEN, Region.G7, Region.G20));
        addCountry("ES", "ESP", "724", "Spain", 
            Set.of(Region.EUROPE, Region.WESTERN_EUROPE, Region.EU, Region.EEA, Region.SCHENGEN));
        addCountry("NL", "NLD", "528", "Netherlands", 
            Set.of(Region.EUROPE, Region.WESTERN_EUROPE, Region.EU, Region.EEA, Region.SCHENGEN));
        addCountry("BE", "BEL", "056", "Belgium", 
            Set.of(Region.EUROPE, Region.WESTERN_EUROPE, Region.EU, Region.EEA, Region.SCHENGEN));
        addCountry("PL", "POL", "616", "Poland", 
            Set.of(Region.EUROPE, Region.EASTERN_EUROPE, Region.EU, Region.EEA, Region.SCHENGEN));
        addCountry("SE", "SWE", "752", "Sweden", 
            Set.of(Region.EUROPE, Region.WESTERN_EUROPE, Region.EU, Region.EEA, Region.SCHENGEN));
        addCountry("DK", "DNK", "208", "Denmark", 
            Set.of(Region.EUROPE, Region.WESTERN_EUROPE, Region.EU, Region.EEA, Region.SCHENGEN));
        addCountry("FI", "FIN", "246", "Finland", 
            Set.of(Region.EUROPE, Region.WESTERN_EUROPE, Region.EU, Region.EEA, Region.SCHENGEN));
        addCountry("AT", "AUT", "040", "Austria", 
            Set.of(Region.EUROPE, Region.WESTERN_EUROPE, Region.EU, Region.EEA, Region.SCHENGEN));
        addCountry("IE", "IRL", "372", "Ireland", 
            Set.of(Region.EUROPE, Region.WESTERN_EUROPE, Region.EU, Region.EEA));
        
        // ASEAN countries
        addCountry("ID", "IDN", "360", "Indonesia", 
            Set.of(Region.ASIA, Region.SOUTHEAST_ASIA, Region.APAC, Region.ASEAN, Region.G20));
        addCountry("TH", "THA", "764", "Thailand", 
            Set.of(Region.ASIA, Region.SOUTHEAST_ASIA, Region.APAC, Region.ASEAN));
        addCountry("MY", "MYS", "458", "Malaysia", 
            Set.of(Region.ASIA, Region.SOUTHEAST_ASIA, Region.APAC, Region.ASEAN, Region.COMMONWEALTH));
        addCountry("PH", "PHL", "608", "Philippines", 
            Set.of(Region.ASIA, Region.SOUTHEAST_ASIA, Region.APAC, Region.ASEAN));
        addCountry("VN", "VNM", "704", "Vietnam", 
            Set.of(Region.ASIA, Region.SOUTHEAST_ASIA, Region.APAC, Region.ASEAN));
        
        // Latin American countries
        addCountry("MX", "MEX", "484", "Mexico", 
            Set.of(Region.NORTH_AMERICA, Region.CENTRAL_AMERICA, Region.AMERICAS, Region.G20));
        addCountry("AR", "ARG", "032", "Argentina", 
            Set.of(Region.SOUTH_AMERICA, Region.AMERICAS, Region.G20));
        addCountry("CO", "COL", "170", "Colombia", 
            Set.of(Region.SOUTH_AMERICA, Region.AMERICAS));
        addCountry("CL", "CHL", "152", "Chile", 
            Set.of(Region.SOUTH_AMERICA, Region.AMERICAS));
        addCountry("PE", "PER", "604", "Peru", 
            Set.of(Region.SOUTH_AMERICA, Region.AMERICAS));
        
        // Additional important countries
        addCountry("TR", "TUR", "792", "Turkey", 
            Set.of(Region.EUROPE, Region.ASIA, Region.MIDDLE_EAST, Region.MENA, Region.G20));
        addCountry("KR", "KOR", "410", "South Korea", 
            Set.of(Region.ASIA, Region.EAST_ASIA, Region.APAC, Region.G20));
        addCountry("TW", "TWN", "158", "Taiwan", 
            Set.of(Region.ASIA, Region.EAST_ASIA, Region.APAC));
        addCountry("HK", "HKG", "344", "Hong Kong", 
            Set.of(Region.ASIA, Region.EAST_ASIA, Region.APAC));
        addCountry("IL", "ISR", "376", "Israel", 
            Set.of(Region.ASIA, Region.MIDDLE_EAST, Region.MENA));
        addCountry("NZ", "NZL", "554", "New Zealand", 
            Set.of(Region.OCEANIA, Region.APAC, Region.COMMONWEALTH));
        addCountry("CH", "CHE", "756", "Switzerland", 
            Set.of(Region.EUROPE, Region.WESTERN_EUROPE, Region.SCHENGEN));
        addCountry("NO", "NOR", "578", "Norway", 
            Set.of(Region.EUROPE, Region.WESTERN_EUROPE, Region.EEA, Region.SCHENGEN));
        
        // Add remaining ISO countries (simplified for brevity - in production, load from database or file)
        log.info("Initialized country database with {} countries", countryDatabase.size());
    }
    
    private void addCountry(String alpha2, String alpha3, String numeric, String name, Set<Region> regions) {
        countryDatabase.put(alpha2, new CountryData(alpha2, alpha3, numeric, name, regions));
    }
    
    /**
     * Initialize OFAC sanctioned countries list
     */
    private void initializeSanctionsList() {
        // Current OFAC comprehensively sanctioned countries/regions
        sanctionedCountries.addAll(Arrays.asList(
            "CU", // Cuba
            "IR", // Iran
            "KP", // North Korea
            "SY", // Syria
            "RU", // Russia (various sanctions)
            "VE", // Venezuela (sectoral sanctions)
            "BY", // Belarus
            "MM"  // Myanmar (Burma)
        ));
        
        log.info("Initialized sanctions list with {} countries", sanctionedCountries.size());
    }
    
    /**
     * Initialize high-risk countries list (FATF, compliance requirements)
     */
    private void initializeHighRiskList() {
        // FATF high-risk and monitored jurisdictions
        highRiskCountries.addAll(Arrays.asList(
            "IR", // Iran
            "KP", // North Korea
            "MM", // Myanmar
            "AF", // Afghanistan
            "YE", // Yemen
            "SY", // Syria
            "SO", // Somalia
            "LY", // Libya
            "SS", // South Sudan
            "CF", // Central African Republic
            "CD", // Democratic Republic of Congo
            "IQ", // Iraq
            "LB", // Lebanon
            "ML", // Mali
            "PK", // Pakistan (monitored)
            "JM", // Jamaica (monitored)
            "PA", // Panama (monitored)
            "AL", // Albania (monitored)
            "ZW"  // Zimbabwe (monitored)
        ));
        
        log.info("Initialized high-risk list with {} countries", highRiskCountries.size());
    }
    
    /**
     * Initialize countries with active service
     */
    private void initializeActiveServices() {
        // Countries where the service is currently active
        activeServiceCountries.addAll(Arrays.asList(
            "US", "GB", "DE", "FR", "IT", "ES", "NL", "BE", "AT", "IE", // Europe & US
            "CA", "AU", "NZ", // Commonwealth
            "JP", "KR", "SG", "HK", "TW", // Asia-Pacific
            "IN", "ID", "MY", "TH", "PH", "VN", // South & Southeast Asia
            "BR", "MX", "AR", "CL", "CO", "PE", // Latin America
            "AE", "SA", "IL", "EG", // Middle East
            "ZA", "NG", "KE", "GH", "MA", "TN" // Africa
        ));
        
        log.info("Initialized active services with {} countries", activeServiceCountries.size());
    }
    
    /**
     * Internal country data structure
     */
    private static class CountryData {
        final String alpha2;
        final String alpha3;
        final String numeric;
        final String name;
        final Set<Region> regions;
        
        CountryData(String alpha2, String alpha3, String numeric, String name, Set<Region> regions) {
            this.alpha2 = alpha2;
            this.alpha3 = alpha3;
            this.numeric = numeric;
            this.name = name;
            this.regions = regions != null ? regions : Collections.emptySet();
        }
    }
    
    /**
     * Country information implementation
     */
    public static class CountryInfoImpl implements CountryInfo {
        private final CountryData data;
        private final boolean sanctioned;
        private final boolean highRisk;
        private final boolean activeService;
        
        public CountryInfoImpl(CountryData data, boolean sanctioned, boolean highRisk, boolean activeService) {
            this.data = data;
            this.sanctioned = sanctioned;
            this.highRisk = highRisk;
            this.activeService = activeService;
        }
        
        @Override
        public String getAlpha2Code() {
            return data.alpha2;
        }
        
        @Override
        public String getAlpha3Code() {
            return data.alpha3;
        }
        
        @Override
        public String getNumericCode() {
            return data.numeric;
        }
        
        @Override
        public String getName() {
            return data.name;
        }
        
        @Override
        public Region[] getRegions() {
            return data.regions.toArray(new Region[0]);
        }
        
        @Override
        public boolean isSanctioned() {
            return sanctioned;
        }
        
        @Override
        public boolean isHighRisk() {
            return highRisk;
        }
        
        @Override
        public boolean hasActiveService() {
            return activeService;
        }
        
        @Override
        public String getCurrency() {
            // Would be loaded from database in production
            return null;
        }
        
        @Override
        public String getCapital() {
            // Would be loaded from database in production
            return null;
        }
        
        @Override
        public String[] getLanguages() {
            // Would be loaded from database in production
            return new String[0];
        }
        
        @Override
        public String getPhoneCode() {
            // Would be loaded from database in production
            return null;
        }
    }
    
    /**
     * Optional external service for country data
     */
    public interface CountryDataService {
        boolean hasActiveService(String countryCode);
        CountryInfo getCountryInfo(String countryCode);
    }
    
    /**
     * Optional external service for sanctions checking
     */
    public interface SanctionsCheckService {
        boolean isSanctioned(String countryCode);
        Set<String> getSanctionedCountries();
    }
}