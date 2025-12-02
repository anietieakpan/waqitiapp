package com.waqiti.ml.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User profile service for fraud detection
 * Provides user profile data for ML feature extraction
 */
@Service
@Slf4j
public class UserProfileService {
    
    private final Map<String, UserProfile> userProfiles = new ConcurrentHashMap<>();
    private final Map<String, UserLocation> userLocations = new ConcurrentHashMap<>();
    private final Map<String, ExternalRiskData> externalRiskCache = new ConcurrentHashMap<>();
    
    public UserProfile getUserProfile(String userId) {
        return userProfiles.computeIfAbsent(userId, this::createDefaultProfile);
    }
    
    public UserLocation getUserUsualLocation(String userId) {
        return userLocations.get(userId);
    }
    
    public ExternalRiskData getExternalRiskData(String userId) {
        return externalRiskCache.computeIfAbsent(userId, this::createDefaultExternalRisk);
    }
    
    private UserProfile createDefaultProfile(String userId) {
        // In real implementation, this would fetch from database
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setAge(30); // Default age
        profile.setLocation("US");
        profile.setAccountType("PERSONAL");
        profile.setRegistrationDate(new Date());
        profile.setTransactionCount(0);
        
        log.debug("Created default profile for user: {}", userId);
        return profile;
    }
    
    private ExternalRiskData createDefaultExternalRisk(String userId) {
        // In real implementation, this would check external risk lists
        ExternalRiskData risk = new ExternalRiskData();
        risk.setUserId(userId);
        risk.setOnWatchlist(false);
        risk.setPoliticallyExposedPerson(false);
        risk.setOnSanctionsList(false);
        risk.setRiskScore(0);
        
        return risk;
    }
    
    // Data classes
    public static class UserProfile {
        private String userId;
        private int age;
        private String location;
        private String accountType;
        private Date registrationDate;
        private int transactionCount;
        
        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        
        public String getAccountType() { return accountType; }
        public void setAccountType(String accountType) { this.accountType = accountType; }
        
        public Date getRegistrationDate() { return registrationDate; }
        public void setRegistrationDate(Date registrationDate) { this.registrationDate = registrationDate; }
        
        public int getTransactionCount() { return transactionCount; }
        public void setTransactionCount(int transactionCount) { this.transactionCount = transactionCount; }
    }
    
    public static class UserLocation {
        private String userId;
        private double latitude;
        private double longitude;
        private String country;
        private LocalDateTime lastUpdated;
        
        public UserLocation() {}
        
        public UserLocation(String userId, double latitude, double longitude) {
            this.userId = userId;
            this.latitude = latitude;
            this.longitude = longitude;
            this.lastUpdated = LocalDateTime.now();
        }
        
        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public double getLatitude() { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }
        
        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }
        
        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }
        
        public LocalDateTime getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
    }
    
    public static class ExternalRiskData {
        private String userId;
        private boolean isOnWatchlist;
        private boolean isPoliticallyExposedPerson;
        private boolean isOnSanctionsList;
        private int riskScore;
        
        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public boolean isOnWatchlist() { return isOnWatchlist; }
        public void setOnWatchlist(boolean onWatchlist) { isOnWatchlist = onWatchlist; }
        
        public boolean isPoliticallyExposedPerson() { return isPoliticallyExposedPerson; }
        public void setPoliticallyExposedPerson(boolean politicallyExposedPerson) { isPoliticallyExposedPerson = politicallyExposedPerson; }
        
        public boolean isOnSanctionsList() { return isOnSanctionsList; }
        public void setOnSanctionsList(boolean onSanctionsList) { isOnSanctionsList = onSanctionsList; }
        
        public int getRiskScore() { return riskScore; }
        public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
    }
}