package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User Behavior Profile DTO
 * 
 * Contains detailed behavioral analysis and patterns for a user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehaviorProfile {
    
    // User identification
    private String userId;
    private String profileId;
    private LocalDateTime profileCreatedAt;
    private LocalDateTime lastUpdatedAt;
    
    // Behavioral baseline
    private BehavioralBaseline baseline;
    
    // Activity patterns
    private ActivityPattern activityPattern;
    
    // Device usage patterns
    private DeviceUsagePattern deviceUsage;
    
    // Location patterns
    private LocationPattern locationPattern;
    
    // Transaction behavior
    private TransactionBehavior transactionBehavior;
    
    // Authentication patterns
    private AuthenticationPattern authenticationPattern;
    
    // Navigation and interaction patterns
    private NavigationPattern navigationPattern;
    
    // Risk indicators
    private List<BehaviorRiskIndicator> riskIndicators;
    
    // Anomaly detection
    private AnomalyDetection anomalyDetection;
    
    // Profile confidence and quality
    private Double profileConfidence; // 0.0 to 1.0
    private Integer dataPoints;
    private String profileQuality; // HIGH, MEDIUM, LOW, INSUFFICIENT
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralBaseline {
        private LocalDateTime baselineStartDate;
        private LocalDateTime baselineEndDate;
        private Integer observationDays;
        private Double stabilityScore;
        private Boolean baselineEstablished;
        private Map<String, Double> baselineMetrics;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityPattern {
        private Map<String, Double> hourlyActivity; // Hour of day -> activity level
        private Map<String, Double> dailyActivity; // Day of week -> activity level
        private Double averageSessionDuration;
        private Integer averageDailySessions;
        private String mostActiveTimeZone;
        private List<String> preferredActivityHours;
        private Boolean nightOwlPattern;
        private Boolean earlyBirdPattern;
        private Boolean workHoursPattern;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceUsagePattern {
        private String primaryDeviceType;
        private String primaryOperatingSystem;
        private String primaryBrowser;
        private Map<String, Integer> deviceTypeUsage;
        private Map<String, Integer> osUsage;
        private Map<String, Integer> browserUsage;
        private Boolean singleDeviceUser;
        private Boolean multiDeviceUser;
        private Double deviceConsistency;
        private Integer uniqueDevicesCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationPattern {
        private String primaryCountry;
        private String primaryCity;
        private String primaryTimezone;
        private List<String> frequentLocations;
        private List<String> countriesVisited;
        private Boolean stationaryUser;
        private Boolean travelingUser;
        private Double locationConsistency;
        private Integer uniqueLocationsCount;
        private Double averageLocationRadius;
        private Boolean workFromHomePattern;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionBehavior {
        private Double averageTransactionAmount;
        private Map<String, Double> transactionAmountByHour;
        private Map<String, Integer> transactionCountByDay;
        private String preferredTransactionType;
        private Map<String, Integer> transactionTypeFrequency;
        private Double transactionVelocity;
        private Boolean regularTransactionPattern;
        private List<String> frequentMerchants;
        private Boolean weekendTransactor;
        private Boolean businessHoursTransactor;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthenticationPattern {
        private String preferredAuthMethod;
        private Map<String, Integer> authMethodUsage;
        private Double averageLoginDuration;
        private Integer averageDailyLogins;
        private Boolean biometricUser;
        private Boolean mfaUser;
        private Double authSuccessRate;
        private Map<String, Double> loginTimePattern;
        private Boolean rememberMeUser;
        private Boolean frequentPasswordChanger;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NavigationPattern {
        private List<String> commonNavigationPaths;
        private Map<String, Integer> pageViewFrequency;
        private Double averagePageViewDuration;
        private String preferredLanguage;
        private Map<String, String> uiPreferences;
        private Boolean powerUser;
        private Boolean casualUser;
        private Double navigationEfficiency;
        private List<String> favoriteFeatures;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehaviorRiskIndicator {
        private String indicatorType;
        private String description;
        private Double riskLevel; // 0.0 to 1.0
        private String severity; // LOW, MEDIUM, HIGH, CRITICAL
        private LocalDateTime firstDetected;
        private LocalDateTime lastDetected;
        private Integer occurrenceCount;
        private Boolean active;
        private Map<String, Object> metadata;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyDetection {
        private List<BehaviorAnomaly> recentAnomalies;
        private Double anomalyScore; // 0.0 to 1.0
        private String anomalyTrend; // INCREASING, STABLE, DECREASING
        private Integer anomalyCount;
        private LocalDateTime lastAnomalyDetected;
        private Map<String, Double> anomalyTypeScores;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehaviorAnomaly {
        private String anomalyId;
        private String anomalyType;
        private String description;
        private Double severity; // 0.0 to 1.0
        private LocalDateTime detectedAt;
        private String detectionMethod;
        private Map<String, Object> anomalyData;
        private Boolean investigated;
        private String resolution;
    }

    /**
     * Get list of known devices
     */
    public List<String> getKnownDevices() {
        if (deviceUsage != null && deviceUsage.getDeviceTypeUsage() != null) {
            return new java.util.ArrayList<>(deviceUsage.getDeviceTypeUsage().keySet());
        }
        return new java.util.ArrayList<>();
    }
    
    /**
     * Get typical login hours
     */
    public List<String> getTypicalLoginHours() {
        if (activityPattern != null && activityPattern.getPreferredActivityHours() != null) {
            return activityPattern.getPreferredActivityHours();
        }
        return new java.util.ArrayList<>();
    }
    
    /**
     * Get users behavioral baseline metrics
     */
    public Map<String, Double> getBaselineMetrics() {
        if (baseline != null && baseline.getBaselineMetrics() != null) {
            return baseline.getBaselineMetrics();
        }
        return new java.util.HashMap<>();
    }

    /**
     * Get known countries from location pattern
     */
    public List<String> getKnownCountries() {
        if (locationPattern != null && locationPattern.getCountriesVisited() != null) {
            return locationPattern.getCountriesVisited();
        }
        return new java.util.ArrayList<>();
    }
    
    /**
     * Add a login time to activity pattern
     */
    public void addLoginTime(LocalDateTime loginTime) {
        if (authenticationPattern == null) {
            authenticationPattern = AuthenticationPattern.builder()
                .loginTimePattern(new java.util.HashMap<>())
                .averageDailyLogins(0)
                .authSuccessRate(1.0)
                .build();
        }
        
        String hour = String.valueOf(loginTime.getHour());
        Map<String, Double> loginPattern = authenticationPattern.getLoginTimePattern();
        if (loginPattern == null) {
            loginPattern = new java.util.HashMap<>();
            authenticationPattern.setLoginTimePattern(loginPattern);
        }
        
        loginPattern.merge(hour, 1.0, Double::sum);
        
        // Update average daily logins
        Integer currentLogins = authenticationPattern.getAverageDailyLogins();
        authenticationPattern.setAverageDailyLogins(currentLogins != null ? currentLogins + 1 : 1);
        
        // Update last updated
        this.lastUpdatedAt = LocalDateTime.now();
    }
    
    /**
     * Add a known device
     */
    public void addKnownDevice(String deviceFingerprint) {
        if (deviceFingerprint == null) return;
        
        if (deviceUsage == null) {
            deviceUsage = DeviceUsagePattern.builder()
                .deviceTypeUsage(new java.util.HashMap<>())
                .uniqueDevicesCount(0)
                .build();
        }
        
        Map<String, Integer> devices = deviceUsage.getDeviceTypeUsage();
        if (devices == null) {
            devices = new java.util.HashMap<>();
            deviceUsage.setDeviceTypeUsage(devices);
        }
        
        devices.merge(deviceFingerprint, 1, Integer::sum);
        deviceUsage.setUniqueDevicesCount(devices.size());
        
        // Update last updated
        this.lastUpdatedAt = LocalDateTime.now();
    }
    
    /**
     * Add a known country
     */
    public void addKnownCountry(String countryCode) {
        if (countryCode == null) return;
        
        if (locationPattern == null) {
            locationPattern = LocationPattern.builder()
                .countriesVisited(new java.util.ArrayList<>())
                .frequentLocations(new java.util.ArrayList<>())
                .uniqueLocationsCount(0)
                .build();
        }
        
        List<String> countries = locationPattern.getCountriesVisited();
        if (countries == null) {
            countries = new java.util.ArrayList<>();
            locationPattern.setCountriesVisited(countries);
        }
        
        if (!countries.contains(countryCode)) {
            countries.add(countryCode);
            locationPattern.setUniqueLocationsCount(countries.size());
        }
        
        // Update last updated
        this.lastUpdatedAt = LocalDateTime.now();
    }
    
    /**
     * Get normal login hours
     */
    public Set<Integer> getNormalLoginHours() {
        Set<Integer> normalHours = new java.util.HashSet<>();
        
        if (authenticationPattern != null && authenticationPattern.getLoginTimePattern() != null) {
            Map<String, Double> loginPattern = authenticationPattern.getLoginTimePattern();
            
            // Find hours with significant login activity (>5% of total)
            double total = loginPattern.values().stream().mapToDouble(Double::doubleValue).sum();
            if (total > 0) {
                for (Map.Entry<String, Double> entry : loginPattern.entrySet()) {
                    if (entry.getValue() / total > 0.05) {
                        try {
                            normalHours.add(Integer.parseInt(entry.getKey()));
                        } catch (NumberFormatException e) {
                            // Ignore invalid hour values
                        }
                    }
                }
            }
        }
        
        return normalHours;
    }
    
    /**
     * Get typical daily logins count
     */
    public int getTypicalDailyLogins() {
        if (authenticationPattern != null && authenticationPattern.getAverageDailyLogins() != null) {
            return authenticationPattern.getAverageDailyLogins();
        }
        return 1; // Default to 1 login per day
    }
}