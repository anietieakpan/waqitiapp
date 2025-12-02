package com.waqiti.ml.service;

import com.waqiti.ml.model.FraudDetectionModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureExtractionService {
    
    private final UserProfileService userProfileService;
    private final TransactionHistoryService transactionHistoryService;
    private final GeoLocationService geoLocationService;
    private final DeviceAnalysisService deviceAnalysisService;
    
    private static final List<String> HOLIDAY_DATES = Arrays.asList(
        "01-01", "07-04", "12-25", "11-11", "02-14"
    );
    
    public FraudDetectionModel extractFeatures(FraudDetectionModel input) {
        log.debug("Extracting features for transaction: {}", input.getTransactionId());
        
        // Extract temporal features
        extractTemporalFeatures(input);
        
        // Extract user profile features
        extractUserProfileFeatures(input);
        
        // Extract transaction pattern features
        extractTransactionPatternFeatures(input);
        
        // Extract device and location features
        extractDeviceLocationFeatures(input);
        
        // Extract behavioral features
        extractBehavioralFeatures(input);
        
        // Extract network and payment features
        extractNetworkPaymentFeatures(input);
        
        // Extract historical risk features
        extractHistoricalRiskFeatures(input);
        
        // Extract external data features
        extractExternalDataFeatures(input);
        
        log.debug("Feature extraction completed for transaction: {}", input.getTransactionId());
        return input;
    }
    
    private void extractTemporalFeatures(FraudDetectionModel input) {
        LocalDateTime transactionTime = LocalDateTime.ofInstant(input.getTimestamp(), ZoneOffset.UTC);
        
        input.setHourOfDay(transactionTime.getHour());
        input.setDayOfWeek(transactionTime.getDayOfWeek().getValue());
        input.setIsWeekend(transactionTime.getDayOfWeek().getValue() >= 6);
        
        // Check if it's a holiday (simplified)
        String monthDay = String.format("%02d-%02d", transactionTime.getMonthValue(), transactionTime.getDayOfMonth());
        input.setIsHoliday(HOLIDAY_DATES.contains(monthDay));
    }
    
    private void extractUserProfileFeatures(FraudDetectionModel input) {
        try {
            // Get user profile data
            var userProfile = userProfileService.getUserProfile(input.getUserId());
            
            if (userProfile != null) {
                input.setUserAge(userProfile.getAge());
                input.setUserLocation(userProfile.getLocation());
                input.setAccountType(userProfile.getAccountType());
                
                // Calculate days since registration
                if (userProfile.getRegistrationDate() != null) {
                    long daysSinceRegistration = ChronoUnit.DAYS.between(
                        userProfile.getRegistrationDate().toInstant(), 
                        input.getTimestamp()
                    );
                    input.setDaysSinceRegistration((int) daysSinceRegistration);
                }
                
                input.setIsFirstTimeUser(userProfile.getTransactionCount() == 0);
            } else {
                // Set default values for new or unknown users
                input.setIsFirstTimeUser(true);
                input.setDaysSinceRegistration(0);
            }
        } catch (Exception e) {
            log.warn("Error extracting user profile features for user: {}", input.getUserId(), e);
            input.setIsFirstTimeUser(true);
        }
    }
    
    private void extractTransactionPatternFeatures(FraudDetectionModel input) {
        try {
            // Get transaction history
            var transactionHistory = transactionHistoryService.getTransactionHistory(
                input.getUserId(), 30
            );
            
            if (transactionHistory != null && !transactionHistory.isEmpty()) {
                // Calculate average transaction amount
                BigDecimal totalAmount = transactionHistory.stream()
                    .map(t -> t.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                BigDecimal avgAmount = totalAmount.divide(
                    BigDecimal.valueOf(transactionHistory.size()), 
                    2, 
                    RoundingMode.HALF_UP
                );
                input.setAverageTransactionAmount(avgAmount);
                
                // Calculate deviation from average
                if (avgAmount.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal deviation = input.getAmount()
                        .subtract(avgAmount)
                        .divide(avgAmount, 4, RoundingMode.HALF_UP)
                        .abs();
                    input.setAmountDeviationFromAverage(deviation);
                }
                
                // Count transactions in different time periods
                Instant now = input.getTimestamp();
                long count24h = transactionHistory.stream()
                    .filter(t -> t.getTimestamp().isAfter(now.minus(24, ChronoUnit.HOURS)))
                    .count();
                input.setTransactionCountLast24h((int) count24h);
                
                long count7d = transactionHistory.stream()
                    .filter(t -> t.getTimestamp().isAfter(now.minus(7, ChronoUnit.DAYS)))
                    .count();
                input.setTransactionCountLast7d((int) count7d);
                
                input.setTransactionCountLast30d(transactionHistory.size());
                
                // Calculate transaction velocity (transactions per hour in last 24h)
                if (count24h > 0) {
                    input.setTransactionVelocity((int) count24h);
                }
            }
            
            // Check if amount is round number
            input.setIsRoundAmount(input.getAmount().remainder(BigDecimal.valueOf(100)).equals(BigDecimal.ZERO));
            
        } catch (Exception e) {
            log.warn("Error extracting transaction pattern features for user: {}", input.getUserId(), e);
        }
    }
    
    private void extractDeviceLocationFeatures(FraudDetectionModel input) {
        try {
            // Analyze device information
            if (input.getDeviceId() != null) {
                var deviceInfo = deviceAnalysisService.analyzeDevice(input.getDeviceId(), input.getUserId());
                if (deviceInfo != null) {
                    input.setIsNewDevice(deviceInfo.isNewDevice());
                    input.setBrowserFingerprint(deviceInfo.getBrowserFingerprint());
                }
            }
            
            // Analyze IP and location
            if (input.getIpAddress() != null) {
                var ipAnalysis = geoLocationService.analyzeIpAddress(input.getIpAddress());
                if (ipAnalysis != null) {
                    input.setIsVpn(ipAnalysis.isVpn());
                    input.setIsProxy(ipAnalysis.isProxy());
                    input.setIsTor(ipAnalysis.isTor());
                    input.setGeoLocation(ipAnalysis.getCountryCode());
                    
                    // Calculate distance from usual location
                    var userUsualLocation = userProfileService.getUserUsualLocation(input.getUserId());
                    if (userUsualLocation != null && ipAnalysis.getLatitude() != null && ipAnalysis.getLongitude() != null) {
                        double distance = calculateDistance(
                            userUsualLocation.getLatitude(), userUsualLocation.getLongitude(),
                            ipAnalysis.getLatitude(), ipAnalysis.getLongitude()
                        );
                        input.setDistanceFromUsualLocation(distance);
                        input.setIsLocationChange(distance > 100); // More than 100km
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting device/location features for transaction: {}", input.getTransactionId(), e);
        }
    }
    
    private void extractBehavioralFeatures(FraudDetectionModel input) {
        try {
            double behavioralScore = 0.0;
            
            // Device-based behavioral analysis
            if (input.getIsNewDevice() != null && input.getIsNewDevice()) {
                behavioralScore += 0.3;
                log.debug("New device detected for user {}, increasing behavioral score", input.getUserId());
            }
            
            // Transaction velocity analysis
            if (input.getTransactionVelocity() != null && input.getTransactionVelocity() > 5) {
                behavioralScore += 0.2;
                input.setIsRushTransaction(true);
                log.debug("High transaction velocity detected: {} txns/24h", input.getTransactionVelocity());
            }
            
            // Get behavioral biometrics from session data if available
            BehavioralBiometrics biometrics = extractBehavioralBiometrics(input);
            if (biometrics != null) {
                // Typing pattern analysis
                if (biometrics.hasTypingAnomaly()) {
                    input.setIsTypingPatternAnomaly(true);
                    behavioralScore += 0.15;
                    log.debug("Typing pattern anomaly detected for transaction {}", input.getTransactionId());
                }
                
                // Click/touch pattern analysis
                if (biometrics.hasClickAnomaly()) {
                    input.setIsClickPatternAnomaly(true);
                    behavioralScore += 0.1;
                    log.debug("Click pattern anomaly detected for transaction {}", input.getTransactionId());
                }
                
                // Navigation pattern analysis
                if (biometrics.hasNavigationAnomaly()) {
                    behavioralScore += 0.12;
                    log.debug("Navigation pattern anomaly detected for transaction {}", input.getTransactionId());
                }
                
                // Copy-paste detection (suspicious for payment forms)
                if (biometrics.hasCopyPasteInSensitiveFields()) {
                    behavioralScore += 0.18;
                    log.warn("Copy-paste detected in sensitive fields for transaction {}", input.getTransactionId());
                }
                
                // Session duration analysis
                input.setSessionDuration(biometrics.getSessionDurationMinutes());
                if (biometrics.isAnomalouslyShortSession()) {
                    behavioralScore += 0.15;
                    log.debug("Anomalously short session detected: {} minutes", biometrics.getSessionDurationMinutes());
                }
                
                // Mouse/touch movement analysis
                if (biometrics.hasUnhumanLikeMovement()) {
                    behavioralScore += 0.25;
                    log.warn("Bot-like movement patterns detected for transaction {}", input.getTransactionId());
                }
                
                // Form fill speed analysis
                if (biometrics.isAnomalousFillSpeed()) {
                    behavioralScore += 0.2;
                    log.debug("Anomalous form fill speed detected for transaction {}", input.getTransactionId());
                }
            } else {
                // No biometric data available - use heuristics
                behavioralScore += applyBehavioralHeuristics(input);
            }
            
            // Time-based behavioral patterns
            behavioralScore += analyzeTemporalBehavior(input);
            
            // Cross-device consistency check
            behavioralScore += analyzeDeviceConsistency(input);
            
            input.setBehavioralScore(Math.min(behavioralScore, 1.0));
            log.debug("Final behavioral score for transaction {}: {}", input.getTransactionId(), input.getBehavioralScore());
            
        } catch (Exception e) {
            log.error("Error extracting behavioral features for transaction: {}", input.getTransactionId(), e);
            input.setBehavioralScore(0.5); // Conservative default on error
        }
    }
    
    /**
     * Extract behavioral biometrics from session/request data
     */
    private BehavioralBiometrics extractBehavioralBiometrics(FraudDetectionModel input) {
        try {
            // In production, this would retrieve data from Redis/session storage
            // that was collected by frontend JavaScript behavioral tracking
            
            String sessionId = input.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                log.debug("No session ID available for behavioral biometrics extraction");
                return null;
            }
            
            // Retrieve biometric data from session storage
            // This would typically be stored by frontend tracking libraries
            BehavioralBiometrics biometrics = new BehavioralBiometrics();
            
            // Placeholder for actual implementation
            // In production, retrieve from:
            // - Redis cache with session data
            // - Real-time event stream from frontend
            // - Behavioral analytics service (e.g., BioCatch, Forter, Sift)
            
            return null; // Return null to trigger heuristic fallback
            
        } catch (Exception e) {
            log.debug("Error extracting behavioral biometrics: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Apply behavioral heuristics when biometric data is unavailable
     */
    private double applyBehavioralHeuristics(FraudDetectionModel input) {
        double heuristicScore = 0.0;
        
        try {
            // Heuristic 1: First transaction on new account
            if (input.getIsFirstTimeUser() != null && input.getIsFirstTimeUser() && 
                input.getDaysSinceRegistration() != null && input.getDaysSinceRegistration() < 1) {
                heuristicScore += 0.15;
                log.debug("First transaction on brand new account detected");
            }
            
            // Heuristic 2: Large first transaction
            if (input.getIsFirstTimeUser() != null && input.getIsFirstTimeUser() && 
                input.getAmount() != null && input.getAmount().compareTo(BigDecimal.valueOf(500)) > 0) {
                heuristicScore += 0.2;
                log.debug("Large amount on first transaction: {}", input.getAmount());
            }
            
            // Heuristic 3: Unusual time of day for user
            if (isUnusualTimeForUser(input)) {
                heuristicScore += 0.1;
            }
            
            // Heuristic 4: Weekend transaction for typically weekday user
            if (input.getIsWeekend() != null && input.getIsWeekend()) {
                var userPattern = userProfileService.getUserTransactionPattern(input.getUserId());
                if (userPattern != null && userPattern.isTypicallyWeekdayUser()) {
                    heuristicScore += 0.08;
                    log.debug("Weekend transaction for typically weekday-only user");
                }
            }
            
            // Heuristic 5: Multiple failed attempts pattern
            if (input.getFailedAttemptsLast24h() != null && input.getFailedAttemptsLast24h() > 0) {
                heuristicScore += Math.min(0.15 * input.getFailedAttemptsLast24h(), 0.3);
                log.debug("Recent failed attempts detected: {}", input.getFailedAttemptsLast24h());
            }
            
        } catch (Exception e) {
            log.debug("Error applying behavioral heuristics: {}", e.getMessage());
        }
        
        return heuristicScore;
    }
    
    /**
     * Analyze temporal behavior patterns
     */
    private double analyzeTemporalBehavior(FraudDetectionModel input) {
        double temporalScore = 0.0;
        
        try {
            // Get user's typical transaction times
            var temporalPattern = userProfileService.getUserTemporalPattern(input.getUserId());
            
            if (temporalPattern != null) {
                int currentHour = input.getHourOfDay() != null ? input.getHourOfDay() : 0;
                
                // Check if current hour is atypical for user
                if (!temporalPattern.isTypicalHour(currentHour)) {
                    temporalScore += 0.15;
                    log.debug("Transaction at atypical hour {} for user {}", currentHour, input.getUserId());
                }
                
                // Check time since last transaction
                Long minutesSinceLastTransaction = temporalPattern.getMinutesSinceLastTransaction();
                if (minutesSinceLastTransaction != null) {
                    // Very quick successive transactions (< 2 minutes) are suspicious
                    if (minutesSinceLastTransaction < 2) {
                        temporalScore += 0.25;
                        log.warn("Rapid successive transaction detected: {} minutes since last", minutesSinceLastTransaction);
                    }
                    // Extremely long dormancy followed by activity can also be suspicious
                    else if (minutesSinceLastTransaction > 43200) { // 30 days
                        temporalScore += 0.1;
                        log.debug("Transaction after long dormancy: {} minutes", minutesSinceLastTransaction);
                    }
                }
            }
            
            // Late night transactions (2 AM - 5 AM) have higher fraud correlation
            int hour = input.getHourOfDay() != null ? input.getHourOfDay() : 0;
            if (hour >= 2 && hour <= 5) {
                temporalScore += 0.12;
                log.debug("Late night transaction detected at hour: {}", hour);
            }
            
        } catch (Exception e) {
            log.debug("Error analyzing temporal behavior: {}", e.getMessage());
        }
        
        return temporalScore;
    }
    
    /**
     * Analyze device consistency across user's transaction history
     */
    private double analyzeDeviceConsistency(FraudDetectionModel input) {
        double consistencyScore = 0.0;
        
        try {
            if (input.getDeviceId() == null) {
                // No device ID is itself suspicious
                consistencyScore += 0.2;
                log.debug("No device ID provided for transaction {}", input.getTransactionId());
                return consistencyScore;
            }
            
            var deviceHistory = deviceAnalysisService.getDeviceHistory(input.getUserId());
            
            if (deviceHistory != null) {
                // Check if device fingerprint matches known devices
                if (!deviceHistory.isKnownDevice(input.getDeviceId())) {
                    consistencyScore += 0.15;
                    log.debug("Unknown device for user {}: {}", input.getUserId(), input.getDeviceId());
                }
                
                // Check for suspicious device switching patterns
                if (deviceHistory.hasFrequentDeviceSwitching()) {
                    consistencyScore += 0.18;
                    log.debug("Frequent device switching detected for user {}", input.getUserId());
                }
                
                // Check if device has been associated with fraud before
                if (deviceHistory.hasDeviceBeenFlagged(input.getDeviceId())) {
                    consistencyScore += 0.35;
                    log.warn("Device {} has been previously flagged for fraud", input.getDeviceId());
                }
                
                // Check for device sharing patterns (same device, multiple users)
                if (deviceHistory.isSharedDevice(input.getDeviceId())) {
                    consistencyScore += 0.12;
                    log.debug("Shared device detected: {}", input.getDeviceId());
                }
            }
            
        } catch (Exception e) {
            log.debug("Error analyzing device consistency: {}", e.getMessage());
        }
        
        return consistencyScore;
    }
    
    /**
     * Check if transaction time is unusual for user's pattern
     */
    private boolean isUnusualTimeForUser(FraudDetectionModel input) {
        try {
            var timePattern = userProfileService.getUserTimePattern(input.getUserId());
            if (timePattern == null) {
                return false; // Can't determine without historical data
            }
            
            int currentHour = input.getHourOfDay() != null ? input.getHourOfDay() : 0;
            int currentDayOfWeek = input.getDayOfWeek() != null ? input.getDayOfWeek() : 1;
            
            // Check against user's typical transaction times
            return !timePattern.isTypicalTime(currentHour, currentDayOfWeek);
            
        } catch (Exception e) {
            log.debug("Error checking unusual time for user: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Inner class for behavioral biometrics data
     */
    private static class BehavioralBiometrics {
        private boolean typingAnomaly;
        private boolean clickAnomaly;
        private boolean navigationAnomaly;
        private boolean copyPasteInSensitiveFields;
        private boolean unhumanLikeMovement;
        private boolean anomalousFillSpeed;
        private boolean anomalouslyShortSession;
        private int sessionDurationMinutes;
        
        public boolean hasTypingAnomaly() {
            return typingAnomaly;
        }
        
        public boolean hasClickAnomaly() {
            return clickAnomaly;
        }
        
        public boolean hasNavigationAnomaly() {
            return navigationAnomaly;
        }
        
        public boolean hasCopyPasteInSensitiveFields() {
            return copyPasteInSensitiveFields;
        }
        
        public boolean hasUnhumanLikeMovement() {
            return unhumanLikeMovement;
        }
        
        public boolean isAnomalousFillSpeed() {
            return anomalousFillSpeed;
        }
        
        public boolean isAnomalouslyShortSession() {
            return anomalouslyShortSession;
        }
        
        public int getSessionDurationMinutes() {
            return sessionDurationMinutes;
        }
    }
    
    private void extractNetworkPaymentFeatures(FraudDetectionModel input) {
        try {
            // Extract payment method features
            extractPaymentMethodFeatures(input);
            
            // Extract merchant features
            extractMerchantFeatures(input);
            
            // Extract network risk features
            extractNetworkRiskFeatures(input);
            
            // Extract card features if applicable
            if ("CARD".equals(input.getPaymentMethod()) || "DEBIT".equals(input.getPaymentMethod())) {
                extractCardFeatures(input);
            }
            
            // Extract international transaction features
            extractInternationalTransactionFeatures(input);
            
        } catch (Exception e) {
            log.error("Error extracting network/payment features for transaction: {}", 
                input.getTransactionId(), e);
        }
    }
    
    /**
     * Extract payment method specific features
     */
    private void extractPaymentMethodFeatures(FraudDetectionModel input) {
        try {
            // Retrieve actual payment method from transaction data
            if (input.getPaymentMethod() == null || input.getPaymentMethod().isEmpty()) {
                // Try to infer from transaction context
                if (input.getTransactionId() != null) {
                    var transactionDetails = transactionHistoryService.getTransactionDetails(input.getTransactionId());
                    if (transactionDetails != null && transactionDetails.getPaymentMethod() != null) {
                        input.setPaymentMethod(transactionDetails.getPaymentMethod());
                    } else {
                        input.setPaymentMethod("CARD"); // Conservative default
                    }
                }
            }
            
            // Payment method risk scoring
            double paymentMethodRisk = assessPaymentMethodRisk(input.getPaymentMethod());
            
            // Check payment method velocity for user
            var userPaymentHistory = userProfileService.getUserPaymentMethodHistory(input.getUserId());
            if (userPaymentHistory != null) {
                // Check if this is a new payment method for user
                boolean isNewPaymentMethod = !userPaymentHistory.hasUsedPaymentMethod(input.getPaymentMethod());
                input.setIsNewPaymentMethod(isNewPaymentMethod);
                
                // Check for suspicious payment method switching
                if (userPaymentHistory.hasFrequentPaymentMethodChanges()) {
                    paymentMethodRisk += 0.15;
                    log.debug("Frequent payment method changes detected for user {}", input.getUserId());
                }
            }
            
            input.setPaymentMethodRiskScore(paymentMethodRisk);
            
        } catch (Exception e) {
            log.debug("Error extracting payment method features: {}", e.getMessage());
        }
    }
    
    /**
     * Extract merchant specific features
     */
    private void extractMerchantFeatures(FraudDetectionModel input) {
        try {
            String merchantId = input.getMerchantId();
            
            if (merchantId != null && !merchantId.isEmpty()) {
                // Retrieve merchant information from merchant service
                var merchantInfo = transactionHistoryService.getMerchantInfo(merchantId);
                
                if (merchantInfo != null) {
                    // Set merchant category from actual data
                    input.setMerchantCategory(merchantInfo.getCategory());
                    
                    // High-risk merchant categories
                    List<String> highRiskCategories = Arrays.asList(
                        "GAMBLING", "CRYPTO", "FOREX", "ADULT", "PHARMACEUTICALS",
                        "MONEY_TRANSFER", "DEBT_COLLECTION", "TIMESHARE", "TELEMARKETING"
                    );
                    input.setIsHighRiskMerchant(highRiskCategories.contains(merchantInfo.getCategory()));
                    
                    // Merchant reputation score
                    if (merchantInfo.getReputationScore() != null) {
                        if (merchantInfo.getReputationScore() < 0.5) {
                            input.setIsHighRiskMerchant(true);
                            log.warn("Low reputation merchant detected: {} (score: {})", 
                                merchantId, merchantInfo.getReputationScore());
                        }
                    }
                    
                    // Check if merchant has history of fraud
                    if (merchantInfo.hasFraudHistory()) {
                        input.setIsHighRiskMerchant(true);
                        log.warn("Merchant with fraud history detected: {}", merchantId);
                    }
                    
                    // Check merchant location vs user location
                    if (merchantInfo.getCountry() != null && input.getGeoLocation() != null) {
                        if (!merchantInfo.getCountry().equals(input.getGeoLocation())) {
                            log.debug("Cross-border merchant transaction detected");
                        }
                    }
                } else {
                    // No merchant info available - use heuristics
                    applyMerchantHeuristics(input);
                }
            } else {
                // No merchant ID - peer-to-peer or internal transfer
                input.setMerchantCategory("P2P_TRANSFER");
                input.setIsHighRiskMerchant(false);
            }
            
            // Check user's history with this merchant
            if (merchantId != null) {
                var userMerchantHistory = transactionHistoryService.getUserMerchantHistory(
                    input.getUserId(), merchantId);
                
                if (userMerchantHistory == null || userMerchantHistory.getTransactionCount() == 0) {
                    // First transaction with this merchant
                    log.debug("First transaction with merchant {} for user {}", 
                        merchantId, input.getUserId());
                    
                    // Higher risk for first transaction with new merchant
                    if (input.getAmount().compareTo(BigDecimal.valueOf(200)) > 0) {
                        log.debug("Large first transaction with new merchant");
                    }
                }
            }
            
        } catch (Exception e) {
            log.debug("Error extracting merchant features: {}", e.getMessage());
        }
    }
    
    /**
     * Extract network risk features (IP, device network patterns)
     */
    private void extractNetworkRiskFeatures(FraudDetectionModel input) {
        try {
            double networkRiskScore = 0.0;
            
            // VPN/Proxy/Tor detection
            if (input.getIsVpn() != null && input.getIsVpn()) {
                networkRiskScore += 0.25;
                log.debug("VPN detected for transaction {}", input.getTransactionId());
            }
            
            if (input.getIsProxy() != null && input.getIsProxy()) {
                networkRiskScore += 0.3;
                log.debug("Proxy detected for transaction {}", input.getTransactionId());
            }
            
            if (input.getIsTor() != null && input.getIsTor()) {
                networkRiskScore += 0.4;
                log.warn("Tor network detected for transaction {}", input.getTransactionId());
            }
            
            // Check for IP velocity abuse (multiple accounts from same IP)
            if (input.getIpAddress() != null) {
                var ipVelocity = deviceAnalysisService.getIpVelocity(input.getIpAddress());
                if (ipVelocity != null) {
                    if (ipVelocity.getUniqueUsersLast24h() > 5) {
                        networkRiskScore += 0.2;
                        log.warn("High IP velocity detected: {} users from IP {} in 24h", 
                            ipVelocity.getUniqueUsersLast24h(), input.getIpAddress());
                    }
                    
                    if (ipVelocity.getTransactionsLast1h() > 10) {
                        networkRiskScore += 0.15;
                        log.warn("High transaction velocity from IP: {} txns/hour", 
                            ipVelocity.getTransactionsLast1h());
                    }
                }
            }
            
            // Device network consistency
            if (input.getDeviceId() != null) {
                var deviceNetwork = deviceAnalysisService.getDeviceNetworkHistory(input.getDeviceId());
                if (deviceNetwork != null) {
                    // Check for suspicious IP switching
                    if (deviceNetwork.hasFrequentIpChanges()) {
                        networkRiskScore += 0.18;
                        log.debug("Frequent IP changes detected for device {}", input.getDeviceId());
                    }
                    
                    // Check for impossible travel (device in different locations too quickly)
                    if (deviceNetwork.hasImpossibleTravel()) {
                        networkRiskScore += 0.35;
                        log.warn("Impossible travel pattern detected for device {}", input.getDeviceId());
                    }
                }
            }
            
            input.setNetworkRiskScore(Math.min(networkRiskScore, 1.0));
            
        } catch (Exception e) {
            log.debug("Error extracting network risk features: {}", e.getMessage());
        }
    }
    
    /**
     * Extract card-specific features
     */
    private void extractCardFeatures(FraudDetectionModel input) {
        try {
            // Card present vs card not present
            String merchantCategory = input.getMerchantCategory();
            boolean isCardPresent = merchantCategory != null && 
                !merchantCategory.equals("ONLINE") && 
                !merchantCategory.equals("E_COMMERCE") &&
                !merchantCategory.equals("SUBSCRIPTION");
            
            input.setIsCardPresent(isCardPresent);
            
            // Card not present transactions have higher fraud risk
            if (!isCardPresent) {
                log.debug("Card-not-present transaction detected");
            }
            
            // Check for card testing patterns (small amounts followed by large)
            var recentTransactions = transactionHistoryService.getRecentTransactions(
                input.getUserId(), 10);
            
            if (recentTransactions != null && !recentTransactions.isEmpty()) {
                // Look for card testing pattern: multiple small amounts (< $5) followed by larger amount
                long smallAmountCount = recentTransactions.stream()
                    .filter(t -> t.getTimestamp().isAfter(
                        java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS)))
                    .filter(t -> t.getAmount().compareTo(BigDecimal.valueOf(5)) < 0)
                    .count();
                
                if (smallAmountCount >= 3 && input.getAmount().compareTo(BigDecimal.valueOf(100)) > 0) {
                    log.warn("Potential card testing pattern detected for user {}", input.getUserId());
                }
            }
            
        } catch (Exception e) {
            log.debug("Error extracting card features: {}", e.getMessage());
        }
    }
    
    /**
     * Extract international transaction features
     */
    private void extractInternationalTransactionFeatures(FraudDetectionModel input) {
        try {
            // Determine if transaction is international
            String userCountry = null;
            var userProfile = userProfileService.getUserProfile(input.getUserId());
            if (userProfile != null && userProfile.getCountry() != null) {
                userCountry = userProfile.getCountry();
            }
            
            String transactionCountry = input.getGeoLocation();
            
            if (userCountry != null && transactionCountry != null) {
                boolean isInternational = !userCountry.equals(transactionCountry);
                input.setIsInternationalTransaction(isInternational);
                
                if (isInternational) {
                    log.debug("International transaction detected: user in {}, transaction from {}", 
                        userCountry, transactionCountry);
                    
                    // Check user's international transaction history
                    var intlHistory = userProfileService.getUserInternationalTransactionHistory(
                        input.getUserId());
                    
                    if (intlHistory != null) {
                        // First international transaction
                        if (intlHistory.getInternationalTransactionCount() == 0) {
                            log.debug("First international transaction for user {}", input.getUserId());
                        }
                        
                        // Check if country is typical for user
                        if (!intlHistory.hasTransactedInCountry(transactionCountry)) {
                            log.debug("Transaction from new country {} for user {}", 
                                transactionCountry, input.getUserId());
                        }
                    }
                    
                    // High-risk countries
                    List<String> highRiskCountries = Arrays.asList(
                        "NG", "RU", "CN", "PK", "ID", "VN", "UA", "RO", "BR", "IN"
                    );
                    
                    if (highRiskCountries.contains(transactionCountry)) {
                        log.debug("Transaction from high-risk country: {}", transactionCountry);
                    }
                }
            }
            
        } catch (Exception e) {
            log.debug("Error extracting international transaction features: {}", e.getMessage());
        }
    }
    
    /**
     * Apply heuristics when merchant data unavailable
     */
    private void applyMerchantHeuristics(FraudDetectionModel input) {
        // Use transaction characteristics to infer merchant category
        BigDecimal amount = input.getAmount();
        
        // Round amounts often indicate ATM or certain merchant types
        if (input.getIsRoundAmount() != null && input.getIsRoundAmount()) {
            if (amount.compareTo(BigDecimal.valueOf(20)) <= 0 && 
                amount.compareTo(BigDecimal.ZERO) > 0) {
                input.setMerchantCategory("ATM");
            } else {
                input.setMerchantCategory("TRANSFER");
            }
        } else {
            // Non-round amounts suggest retail/online
            input.setMerchantCategory("RETAIL");
        }
        
        input.setIsHighRiskMerchant(false); // Conservative default
    }
    
    /**
     * Assess risk score for payment method
     */
    private double assessPaymentMethodRisk(String paymentMethod) {
        if (paymentMethod == null) {
            return 0.3; // Unknown payment method has moderate risk
        }
        
        switch (paymentMethod.toUpperCase()) {
            case "CARD":
            case "DEBIT":
                return 0.1; // Low risk for card payments
            case "BANK_TRANSFER":
            case "ACH":
                return 0.05; // Very low risk for bank transfers
            case "WALLET":
            case "MOBILE_MONEY":
                return 0.15; // Moderate risk
            case "CRYPTO":
            case "PREPAID":
                return 0.4; // Higher risk
            case "CASH":
                return 0.2; // Moderate risk
            default:
                return 0.25; // Unknown methods get moderate risk
        }
    }
    
    private void extractHistoricalRiskFeatures(FraudDetectionModel input) {
        try {
            // Get historical risk data
            var riskHistory = transactionHistoryService.getUserRiskHistory(input.getUserId());
            
            if (riskHistory != null) {
                input.setFailedAttemptsLast24h(riskHistory.getFailedAttemptsLast24h());
                input.setHasRecentChargebacks(riskHistory.hasChargebacksInLast90Days());
                input.setHasRecentDisputes(riskHistory.hasDisputesInLast90Days());
                input.setHistoricalFraudRate(riskHistory.getHistoricalFraudRate());
            } else {
                // Default values for new users
                input.setFailedAttemptsLast24h(0);
                input.setHasRecentChargebacks(false);
                input.setHasRecentDisputes(false);
                input.setHistoricalFraudRate(0.0);
            }
        } catch (Exception e) {
            log.warn("Error extracting historical risk features for user: {}", input.getUserId(), e);
        }
    }
    
    private void extractExternalDataFeatures(FraudDetectionModel input) {
        try {
            // Check external risk lists
            var externalRisk = userProfileService.getExternalRiskData(input.getUserId());
            
            if (externalRisk != null) {
                input.setIsOnWatchlist(externalRisk.isOnWatchlist());
                input.setIsPep(externalRisk.isPoliticallyExposedPerson());
                input.setIsSanctioned(externalRisk.isOnSanctionsList());
                input.setExternalRiskScore(externalRisk.getRiskScore());
            } else {
                input.setIsOnWatchlist(false);
                input.setIsPep(false);
                input.setIsSanctioned(false);
                input.setExternalRiskScore(0);
            }
        } catch (Exception e) {
            log.warn("Error extracting external data features for user: {}", input.getUserId(), e);
        }
    }
    
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Haversine formula for calculating distance between two points on Earth
        final int R = 6371; // Radius of the earth in km
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c; // Distance in km
    }
}