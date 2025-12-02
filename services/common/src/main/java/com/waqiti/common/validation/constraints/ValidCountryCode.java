package com.waqiti.common.validation.constraints;

import com.waqiti.common.validation.validators.CountryCodeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validation constraint for country codes (ISO 3166-1 alpha-2)
 * Supports comprehensive country code validation with sanctions checking,
 * regional restrictions, and configurable validation rules
 */
@Documented
@Constraint(validatedBy = CountryCodeValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ValidCountryCode.List.class)
public @interface ValidCountryCode {
    String message() default "Invalid country code: ${validatedValue}. Must be a valid ISO 3166-1 alpha-2 code";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Allow null values
     */
    boolean allowNull() default true;
    
    /**
     * Allow empty string values
     */
    boolean allowEmpty() default false;
    
    /**
     * Allow specific country codes only (whitelist)
     */
    String[] allowedCountries() default {};
    
    /**
     * Block specific country codes (blacklist)
     */
    String[] blockedCountries() default {};
    
    /**
     * Check against OFAC sanctioned countries
     */
    boolean checkSanctions() default true;
    
    /**
     * Allow only countries with active service support
     */
    boolean requireActiveService() default false;
    
    /**
     * Validation mode
     */
    ValidationMode mode() default ValidationMode.ISO_3166_ALPHA2;
    
    /**
     * Check against high-risk countries list
     */
    boolean checkHighRisk() default true;
    
    /**
     * Allow countries based on region
     */
    Region[] allowedRegions() default {};
    
    /**
     * Block countries based on region
     */
    Region[] blockedRegions() default {};
    
    /**
     * Custom validation provider class
     */
    Class<? extends CountryValidationProvider> provider() default DefaultCountryValidationProvider.class;
    
    /**
     * Validation modes
     */
    enum ValidationMode {
        ISO_3166_ALPHA2,    // Two-letter codes (US, GB, etc.)
        ISO_3166_ALPHA3,    // Three-letter codes (USA, GBR, etc.)
        ISO_3166_NUMERIC,   // Numeric codes (840, 826, etc.)
        FLEXIBLE            // Accept any valid format
    }
    
    /**
     * Geographic regions
     */
    enum Region {
        AFRICA,
        ASIA,
        EUROPE,
        NORTH_AMERICA,
        SOUTH_AMERICA,
        OCEANIA,
        MIDDLE_EAST,
        CARIBBEAN,
        CENTRAL_AMERICA,
        EASTERN_EUROPE,
        WESTERN_EUROPE,
        SOUTHEAST_ASIA,
        EAST_ASIA,
        SOUTH_ASIA,
        CENTRAL_ASIA,
        SUB_SAHARAN_AFRICA,
        NORTH_AFRICA,
        MENA, // Middle East and North Africa
        APAC, // Asia-Pacific
        EMEA, // Europe, Middle East, and Africa
        AMERICAS,
        G7,
        G20,
        EU,
        EEA,
        SCHENGEN,
        BRICS,
        ASEAN,
        COMMONWEALTH,
        ARAB_LEAGUE,
        AFRICAN_UNION
    }
    
    /**
     * Interface for custom validation providers
     */
    interface CountryValidationProvider {
        boolean isValid(String countryCode, ValidCountryCode annotation);
        CountryInfo getCountryInfo(String countryCode);
    }
    
    /**
     * Default implementation
     */
    class DefaultCountryValidationProvider implements CountryValidationProvider {
        @Override
        public boolean isValid(String countryCode, ValidCountryCode annotation) {
            return true; // Implementation in validator
        }
        
        @Override
        public CountryInfo getCountryInfo(String countryCode) {
            return null; // Implementation in validator
        }
    }
    
    /**
     * Country information
     */
    interface CountryInfo {
        String getAlpha2Code();
        String getAlpha3Code();
        String getNumericCode();
        String getName();
        Region[] getRegions();
        boolean isSanctioned();
        boolean isHighRisk();
        boolean hasActiveService();
        String getCurrency();
        String getCapital();
        String[] getLanguages();
        String getPhoneCode();
    }
    
    /**
     * Container annotation for repeated constraints
     */
    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.PARAMETER, ElementType.TYPE_USE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {
        ValidCountryCode[] value();
    }
}