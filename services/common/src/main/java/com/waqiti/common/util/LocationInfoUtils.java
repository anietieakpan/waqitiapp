package com.waqiti.common.util;

import com.waqiti.common.domain.LocationInfo;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Utility class for LocationInfo processing, validation, and geospatial operations
 * 
 * Provides enterprise-grade location data processing capabilities including:
 * - Geographic coordinate validation and normalization
 * - Distance calculations and proximity analysis
 * - Risk assessment and fraud scoring
 * - Compliance and regulatory checks
 * - Data quality validation and enhancement
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@UtilityClass
public class LocationInfoUtils {
    
    // Geographic constants
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90.0");
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90.0");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180.0");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180.0");
    
    // Risk assessment thresholds
    private static final BigDecimal HIGH_RISK_THRESHOLD = new BigDecimal("0.7");
    private static final BigDecimal MEDIUM_RISK_THRESHOLD = new BigDecimal("0.3");
    private static final BigDecimal LOW_RISK_THRESHOLD = new BigDecimal("0.1");
    
    // Data quality thresholds
    private static final BigDecimal MIN_CONFIDENCE_LEVEL = new BigDecimal("0.5");
    private static final int MAX_ACCURACY_RADIUS_KM = 100;
    private static final int MIN_QUALITY_SCORE = 50;
    
    // Compliance and regulatory lists
    private static final Set<String> EU_COUNTRY_CODES = Set.of(
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
        "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
        "PL", "PT", "RO", "SK", "SI", "ES", "SE"
    );
    
    private static final Set<String> HIGH_RISK_COUNTRY_CODES = Set.of(
        "AF", "BY", "CD", "CF", "CU", "ER", "GN", "GW", "HT", "IR",
        "KP", "LB", "LY", "ML", "MM", "NI", "PK", "SO", "SS", "SD",
        "SY", "VE", "YE", "ZW"
    );
    
    private static final Set<String> OFFSHORE_JURISDICTIONS = Set.of(
        "AG", "BB", "BZ", "BM", "KY", "CK", "CW", "DM", "GI", "GG",
        "IM", "JE", "KN", "LC", "LI", "MC", "NR", "NU", "PA", "SC",
        "TC", "TO", "VG", "VU", "WS"
    );
    
    // IP address validation patterns
    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)$"
    );
    
    private static final Pattern IPV6_PATTERN = Pattern.compile(
        "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|" +
        "^::([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4}$|" +
        "^([0-9a-fA-F]{1,4}:){1,6}::$|" +
        "^([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}$"
    );
    
    /**
     * Validates LocationInfo for completeness and data quality
     * 
     * @param locationInfo The LocationInfo to validate
     * @return ValidationResult with details of validation status
     */
    public static ValidationResult validateLocation(LocationInfo locationInfo) {
        if (locationInfo == null) {
            return ValidationResult.invalid("LocationInfo cannot be null");
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate coordinates
        if (!validateCoordinates(locationInfo.getLatitude(), locationInfo.getLongitude())) {
            errors.add("Invalid geographic coordinates");
        }
        
        // Validate country code
        if (locationInfo.getCountryCode() != null && 
            !isValidCountryCode(locationInfo.getCountryCode())) {
            errors.add("Invalid country code format");
        }
        
        // Check data quality indicators
        if (locationInfo.getConfidenceLevel() != null &&
            locationInfo.getConfidenceLevel().compareTo(MIN_CONFIDENCE_LEVEL) < 0) {
            warnings.add("Low confidence level in location data");
        }
        
        if (locationInfo.getAccuracyRadius() != null &&
            locationInfo.getAccuracyRadius() > MAX_ACCURACY_RADIUS_KM) {
            warnings.add("Low location accuracy (radius > " + MAX_ACCURACY_RADIUS_KM + "km)");
        }
        
        if (locationInfo.getQualityScore() != null &&
            locationInfo.getQualityScore() < MIN_QUALITY_SCORE) {
            warnings.add("Low data quality score");
        }
        
        // Validate IP address format if present
        if (locationInfo.getIpAddress() != null && 
            !isValidIpAddress(locationInfo.getIpAddress())) {
            warnings.add("Invalid or masked IP address format");
        }
        
        if (errors.isEmpty()) {
            return warnings.isEmpty() ? 
                ValidationResult.valid() : 
                ValidationResult.validWithWarnings(warnings);
        } else {
            return ValidationResult.invalid(errors, warnings);
        }
    }
    
    /**
     * Calculates comprehensive fraud risk score based on location attributes
     * 
     * @param locationInfo The LocationInfo to assess
     * @return calculated risk score between 0.0 and 1.0
     */
    public static BigDecimal calculateFraudRiskScore(LocationInfo locationInfo) {
        if (locationInfo == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal riskScore = BigDecimal.ZERO;
        
        // Base risk from existing score
        if (locationInfo.getRiskScore() != null) {
            riskScore = riskScore.add(locationInfo.getRiskScore().multiply(new BigDecimal("0.4")));
        }
        
        // Proxy/VPN/Tor indicators (high risk)
        if (Boolean.TRUE.equals(locationInfo.getIsProxy()) ||
            Boolean.TRUE.equals(locationInfo.getIsTor()) ||
            Boolean.TRUE.equals(locationInfo.getIsVpn())) {
            riskScore = riskScore.add(new BigDecimal("0.3"));
        }
        
        // Anonymizer or hosting (medium risk)
        if (Boolean.TRUE.equals(locationInfo.getIsAnonymizer()) ||
            Boolean.TRUE.equals(locationInfo.getIsHosting())) {
            riskScore = riskScore.add(new BigDecimal("0.2"));
        }
        
        // Malicious or botnet (critical risk)
        if (Boolean.TRUE.equals(locationInfo.getIsMalicious()) ||
            Boolean.TRUE.equals(locationInfo.getIsBotnet())) {
            riskScore = riskScore.add(new BigDecimal("0.4"));
        }
        
        // High-risk country
        if (Boolean.TRUE.equals(locationInfo.getIsHighRiskCountry()) ||
            isHighRiskCountry(locationInfo.getCountryCode())) {
            riskScore = riskScore.add(new BigDecimal("0.15"));
        }
        
        // Offshore jurisdiction
        if (isOffshoreJurisdiction(locationInfo.getCountryCode())) {
            riskScore = riskScore.add(new BigDecimal("0.1"));
        }
        
        // Sanctions or blacklists
        if (locationInfo.getSanctions() != null && !locationInfo.getSanctions().isEmpty()) {
            riskScore = riskScore.add(new BigDecimal("0.25"));
        }
        
        if (locationInfo.getBlacklists() != null && !locationInfo.getBlacklists().isEmpty()) {
            riskScore = riskScore.add(new BigDecimal("0.2"));
        }
        
        // Low confidence data increases risk
        if (locationInfo.getConfidenceLevel() != null &&
            locationInfo.getConfidenceLevel().compareTo(MIN_CONFIDENCE_LEVEL) < 0) {
            riskScore = riskScore.add(new BigDecimal("0.05"));
        }
        
        // Cap at 1.0
        return riskScore.min(BigDecimal.ONE).setScale(3, RoundingMode.HALF_UP);
    }
    
    /**
     * Enhanced distance calculation with geographic considerations
     * 
     * @param location1 First location
     * @param location2 Second location
     * @return DistanceResult with distance and metadata
     */
    public static DistanceResult calculateEnhancedDistance(LocationInfo location1, LocationInfo location2) {
        if (location1 == null || location2 == null) {
            return DistanceResult.invalid("One or both locations are null");
        }
        
        if (!location1.hasValidCoordinates() || !location2.hasValidCoordinates()) {
            return DistanceResult.invalid("Invalid coordinates in one or both locations");
        }
        
        double distance = calculateHaversineDistance(
            location1.getLatitude().doubleValue(),
            location1.getLongitude().doubleValue(),
            location2.getLatitude().doubleValue(),
            location2.getLongitude().doubleValue()
        );
        
        // Calculate accuracy margin
        Double accuracyMargin = null;
        if (location1.getAccuracyRadius() != null && location2.getAccuracyRadius() != null) {
            accuracyMargin = location1.getAccuracyRadius() + location2.getAccuracyRadius().doubleValue();
        }
        
        // Determine if locations are in same jurisdiction
        boolean sameCountry = Objects.equals(location1.getCountryCode(), location2.getCountryCode());
        boolean sameRegion = sameCountry && Objects.equals(location1.getRegionCode(), location2.getRegionCode());
        boolean sameCity = sameRegion && Objects.equals(location1.getCity(), location2.getCity());
        
        return DistanceResult.builder()
            .distance(distance)
            .accuracyMargin(accuracyMargin)
            .sameCountry(sameCountry)
            .sameRegion(sameRegion)
            .sameCity(sameCity)
            .reliable(location1.hasReliableLocation() && location2.hasReliableLocation())
            .build();
    }
    
    /**
     * Enriches LocationInfo with compliance and regulatory metadata
     * 
     * @param locationInfo The LocationInfo to enrich
     * @return enriched LocationInfo with compliance data
     */
    public static LocationInfo enrichWithComplianceData(LocationInfo locationInfo) {
        if (locationInfo == null) {
            return null;
        }
        
        LocationInfo.LocationInfoBuilder builder = locationInfo.toBuilder();
        
        String countryCode = locationInfo.getCountryCode();
        if (countryCode != null) {
            // Set EU country flag
            builder.isEuCountry(EU_COUNTRY_CODES.contains(countryCode.toUpperCase()));
            
            // Set high-risk country flag
            builder.isHighRiskCountry(HIGH_RISK_COUNTRY_CODES.contains(countryCode.toUpperCase()));
            
            // Set regulatory zone
            String regulatoryZone = determineRegulatoryZone(countryCode);
            builder.regulatoryZone(regulatoryZone);
            
            // Add sanctions information if applicable
            List<String> applicableSanctions = getApplicableSanctions(countryCode);
            if (!applicableSanctions.isEmpty()) {
                builder.sanctions(applicableSanctions);
            }
        }
        
        // Recalculate risk score with new compliance data
        LocationInfo enrichedLocation = builder.build();
        BigDecimal enhancedRiskScore = calculateFraudRiskScore(enrichedLocation);
        builder.riskScore(enhancedRiskScore);
        
        return builder.build();
    }
    
    /**
     * Creates a sanitized version safe for external APIs and logging
     * 
     * @param locationInfo The LocationInfo to sanitize
     * @return sanitized LocationInfo
     */
    public static LocationInfo createSanitizedVersion(LocationInfo locationInfo) {
        if (locationInfo == null) {
            return null;
        }
        
        return LocationInfo.builder()
            .countryCode(locationInfo.getCountryCode())
            .country(locationInfo.getCountry())
            .regionCode(locationInfo.getRegionCode())
            .state(locationInfo.getState())
            .city(locationInfo.getCity())
            .postalCode(maskPostalCode(locationInfo.getPostalCode()))
            .isProxy(locationInfo.getIsProxy())
            .isTor(locationInfo.getIsTor())
            .isVpn(locationInfo.getIsVpn())
            .isAnonymizer(locationInfo.getIsAnonymizer())
            .isMalicious(locationInfo.getIsMalicious())
            .riskScore(locationInfo.getRiskScore())
            .confidenceLevel(locationInfo.getConfidenceLevel())
            .dataSource(locationInfo.getDataSource())
            .provider(locationInfo.getProvider())
            .qualityScore(locationInfo.getQualityScore())
            .isEuCountry(locationInfo.getIsEuCountry())
            .isHighRiskCountry(locationInfo.getIsHighRiskCountry())
            .regulatoryZone(locationInfo.getRegulatoryZone())
            .timestamp(locationInfo.getTimestamp())
            .locationTimestamp(locationInfo.getLocationTimestamp())
            .build();
    }
    
    /**
     * Validates geographic coordinates
     */
    private static boolean validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        
        return latitude.compareTo(MIN_LATITUDE) >= 0 &&
               latitude.compareTo(MAX_LATITUDE) <= 0 &&
               longitude.compareTo(MIN_LONGITUDE) >= 0 &&
               longitude.compareTo(MAX_LONGITUDE) <= 0;
    }
    
    /**
     * Validates country code format (ISO 3166-1 alpha-2)
     */
    private static boolean isValidCountryCode(String countryCode) {
        return countryCode != null && countryCode.matches("^[A-Z]{2}$");
    }
    
    /**
     * Validates IP address format
     */
    private static boolean isValidIpAddress(String ipAddress) {
        if (ipAddress == null) {
            return false;
        }
        
        // Allow masked IP addresses
        if (ipAddress.contains("*")) {
            return true;
        }
        
        return IPV4_PATTERN.matcher(ipAddress).matches() ||
               IPV6_PATTERN.matcher(ipAddress).matches();
    }
    
    /**
     * Haversine distance calculation
     */
    private static double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM * c;
    }
    
    /**
     * Checks if country is high-risk for AML purposes
     */
    private static boolean isHighRiskCountry(String countryCode) {
        return countryCode != null && HIGH_RISK_COUNTRY_CODES.contains(countryCode.toUpperCase());
    }
    
    /**
     * Checks if country is offshore jurisdiction
     */
    private static boolean isOffshoreJurisdiction(String countryCode) {
        return countryCode != null && OFFSHORE_JURISDICTIONS.contains(countryCode.toUpperCase());
    }
    
    /**
     * Determines regulatory zone classification
     */
    private static String determineRegulatoryZone(String countryCode) {
        if (countryCode == null) {
            return "UNKNOWN";
        }
        
        String upperCode = countryCode.toUpperCase();
        
        if (EU_COUNTRY_CODES.contains(upperCode)) {
            return "EU";
        } else if (Set.of("US", "CA").contains(upperCode)) {
            return "NORTH_AMERICA";
        } else if (HIGH_RISK_COUNTRY_CODES.contains(upperCode)) {
            return "HIGH_RISK";
        } else if (OFFSHORE_JURISDICTIONS.contains(upperCode)) {
            return "OFFSHORE";
        } else if (Set.of("AU", "NZ", "JP", "SG", "HK").contains(upperCode)) {
            return "ASIA_PACIFIC";
        } else {
            return "OTHER";
        }
    }
    
    /**
     * Gets applicable sanctions for country
     */
    private static List<String> getApplicableSanctions(String countryCode) {
        List<String> sanctions = new ArrayList<>();
        
        if (countryCode != null) {
            String upperCode = countryCode.toUpperCase();
            
            // OFAC Specially Designated Nationals
            if (Set.of("IR", "KP", "SY", "CU").contains(upperCode)) {
                sanctions.add("OFAC_SDN");
            }
            
            // EU Sanctions
            if (Set.of("RU", "BY", "IR").contains(upperCode)) {
                sanctions.add("EU_SANCTIONS");
            }
            
            // UN Security Council Sanctions
            if (Set.of("KP", "IR", "AF").contains(upperCode)) {
                sanctions.add("UN_SANCTIONS");
            }
        }
        
        return sanctions;
    }
    
    /**
     * Masks postal code for privacy
     */
    private static String maskPostalCode(String postalCode) {
        if (postalCode == null || postalCode.length() <= 3) {
            return postalCode;
        }
        
        return postalCode.substring(0, 3) + "***";
    }
    
    /**
     * Validation result for LocationInfo validation
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        public static ValidationResult valid() {
            return new ValidationResult(true, Collections.emptyList(), Collections.emptyList());
        }
        
        public static ValidationResult validWithWarnings(List<String> warnings) {
            return new ValidationResult(true, Collections.emptyList(), warnings);
        }
        
        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, List.of(error), Collections.emptyList());
        }
        
        public static ValidationResult invalid(List<String> errors) {
            return new ValidationResult(false, errors, Collections.emptyList());
        }
        
        public static ValidationResult invalid(List<String> errors, List<String> warnings) {
            return new ValidationResult(false, errors, warnings);
        }
    }
    
    /**
     * Distance calculation result with metadata
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    public static class DistanceResult {
        private final boolean valid;
        private final double distance;
        private final Double accuracyMargin;
        private final boolean sameCountry;
        private final boolean sameRegion;
        private final boolean sameCity;
        private final boolean reliable;
        private final String error;
        
        public static DistanceResult invalid(String error) {
            return DistanceResult.builder()
                .valid(false)
                .error(error)
                .build();
        }
    }
}