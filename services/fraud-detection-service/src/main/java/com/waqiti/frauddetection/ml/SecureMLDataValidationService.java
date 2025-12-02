package com.waqiti.frauddetection.ml;

import com.waqiti.frauddetection.model.FraudCase;
import com.waqiti.frauddetection.model.TrainingExample;
import com.waqiti.frauddetection.model.FraudStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * CRITICAL SECURITY SERVICE: ML Training Data Validation
 * Prevents model poisoning attacks by validating and sanitizing training data
 * Implements defense against data poisoning, label flipping, and feature manipulation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecureMLDataValidationService {

    /**
     * Cryptographically secure random number generator for differential privacy.
     * Using SecureRandom is critical for differential privacy implementations as it ensures
     * that the Laplace noise added to training data is truly unpredictable, preventing
     * potential privacy leaks through analysis of the noise patterns.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${fraud.ml.security.data-validation.enabled:true}")
    private boolean validationEnabled;
    
    @Value("${fraud.ml.security.outlier-threshold:3.5}")
    private double outlierThreshold;
    
    @Value("${fraud.ml.security.max-feature-value:10000}")
    private double maxFeatureValue;
    
    @Value("${fraud.ml.security.min-feature-value:-10000}")
    private double minFeatureValue;
    
    @Value("${fraud.ml.security.label-consistency-threshold:0.95}")
    private double labelConsistencyThreshold;
    
    @Value("${fraud.ml.security.data-integrity-key:${random.value}}")
    private String dataIntegrityKey;
    
    // Track data statistics for anomaly detection
    private final Map<String, FeatureStatistics> featureStats = new ConcurrentHashMap<>();
    private final Map<String, DataIntegrityRecord> integrityRecords = new ConcurrentHashMap<>();
    
    /**
     * CRITICAL SECURITY: Validate and sanitize training data before model training
     * Prevents model poisoning attacks
     */
    public List<TrainingExample> validateAndSanitizeTrainingData(List<FraudCase> rawData) {
        if (!validationEnabled) {
            log.warn("SECURITY WARNING: ML data validation is disabled - model poisoning attacks possible!");
            return convertToTrainingExamples(rawData);
        }
        
        log.info("SECURITY: Starting ML training data validation for {} samples", rawData.size());
        
        try {
            // Step 1: Data integrity verification
            List<FraudCase> integrityVerified = verifyDataIntegrity(rawData);
            
            // Step 2: Remove duplicates and suspicious patterns
            List<FraudCase> deduplicated = removeDuplicatesAndSuspicious(integrityVerified);
            
            // Step 3: Validate labels for consistency
            List<FraudCase> labelValidated = validateLabels(deduplicated);
            
            // Step 4: Feature validation and sanitization
            List<FraudCase> featureSanitized = sanitizeFeatures(labelValidated);
            
            // Step 5: Outlier detection and removal
            List<FraudCase> outlierRemoved = removeOutliers(featureSanitized);
            
            // Step 6: Balance dataset to prevent bias
            List<FraudCase> balanced = balanceDataset(outlierRemoved);
            
            // Step 7: Apply differential privacy noise
            List<FraudCase> privacyProtected = applyDifferentialPrivacy(balanced);
            
            // Step 8: Final validation
            List<TrainingExample> validated = performFinalValidation(privacyProtected);
            
            log.info("SECURITY: Training data validation complete. Original: {}, Validated: {}", 
                rawData.size(), validated.size());
            
            // Record validation metrics
            recordValidationMetrics(rawData.size(), validated.size());
            
            return validated;
            
        } catch (Exception e) {
            log.error("SECURITY ERROR: Training data validation failed", e);
            throw new SecurityException("Training data validation failed - potential poisoning attack", e);
        }
    }
    
    /**
     * Step 1: Verify data integrity using HMAC
     */
    private List<FraudCase> verifyDataIntegrity(List<FraudCase> data) {
        log.debug("SECURITY: Verifying data integrity for {} samples", data.size());
        
        List<FraudCase> verified = new ArrayList<>();
        int tamperedCount = 0;
        
        for (FraudCase fraudCase : data) {
            String dataHash = calculateDataHash(fraudCase);
            DataIntegrityRecord record = integrityRecords.get(fraudCase.getId());
            
            if (record != null) {
                // Verify existing data hasn't been tampered
                if (!record.hash.equals(dataHash)) {
                    log.warn("SECURITY: Data tampering detected for case {}", fraudCase.getId());
                    tamperedCount++;
                    continue; // Skip tampered data
                }
            } else {
                // New data - create integrity record
                integrityRecords.put(fraudCase.getId(), new DataIntegrityRecord(dataHash, LocalDateTime.now()));
            }
            
            verified.add(fraudCase);
        }
        
        if (tamperedCount > 0) {
            log.error("SECURITY ALERT: {} tampered training samples detected and removed", tamperedCount);
        }
        
        return verified;
    }
    
    /**
     * Step 2: Remove duplicates and suspicious patterns
     */
    private List<FraudCase> removeDuplicatesAndSuspicious(List<FraudCase> data) {
        log.debug("SECURITY: Removing duplicates and suspicious patterns");
        
        Set<String> seen = new HashSet<>();
        List<FraudCase> cleaned = new ArrayList<>();
        int duplicates = 0;
        int suspicious = 0;
        
        for (FraudCase fraudCase : data) {
            // Create unique signature
            String signature = createDataSignature(fraudCase);
            
            if (seen.contains(signature)) {
                duplicates++;
                continue;
            }
            
            // Check for suspicious patterns (e.g., too many similar cases from same source)
            if (isSuspiciousPattern(fraudCase, cleaned)) {
                suspicious++;
                log.warn("SECURITY: Suspicious pattern detected in case {}", fraudCase.getId());
                continue;
            }
            
            seen.add(signature);
            cleaned.add(fraudCase);
        }
        
        if (duplicates > 0 || suspicious > 0) {
            log.info("SECURITY: Removed {} duplicates and {} suspicious samples", duplicates, suspicious);
        }
        
        return cleaned;
    }
    
    /**
     * Step 3: Validate labels for consistency
     */
    private List<FraudCase> validateLabels(List<FraudCase> data) {
        log.debug("SECURITY: Validating labels for consistency");
        
        List<FraudCase> validated = new ArrayList<>();
        Map<String, LabelStatistics> userLabelStats = new HashMap<>();
        
        // Build label statistics per user
        for (FraudCase fraudCase : data) {
            String userId = fraudCase.getUserId();
            userLabelStats.computeIfAbsent(userId, k -> new LabelStatistics())
                .addCase(fraudCase);
        }
        
        // Validate each case
        int inconsistentLabels = 0;
        for (FraudCase fraudCase : data) {
            LabelStatistics stats = userLabelStats.get(fraudCase.getUserId());
            
            // Check for label flipping attacks
            if (isLabelFlipped(fraudCase, stats)) {
                inconsistentLabels++;
                log.warn("SECURITY: Potential label flipping detected for case {}", fraudCase.getId());
                continue;
            }
            
            validated.add(fraudCase);
        }
        
        if (inconsistentLabels > 0) {
            log.warn("SECURITY: {} cases with inconsistent labels removed", inconsistentLabels);
        }
        
        return validated;
    }
    
    /**
     * Step 4: Sanitize features to prevent manipulation
     */
    private List<FraudCase> sanitizeFeatures(List<FraudCase> data) {
        log.debug("SECURITY: Sanitizing features");
        
        List<FraudCase> sanitized = new ArrayList<>();
        
        for (FraudCase fraudCase : data) {
            Map<String, Double> features = fraudCase.getFeatures();
            Map<String, Double> sanitizedFeatures = new HashMap<>();
            
            for (Map.Entry<String, Double> entry : features.entrySet()) {
                String featureName = entry.getKey();
                Double value = entry.getValue();
                
                // Validate feature name (prevent injection)
                if (!isValidFeatureName(featureName)) {
                    log.warn("SECURITY: Invalid feature name detected: {}", featureName);
                    continue;
                }
                
                // Sanitize feature value
                Double sanitizedValue = sanitizeFeatureValue(featureName, value);
                sanitizedFeatures.put(featureName, sanitizedValue);
                
                // Update feature statistics
                updateFeatureStatistics(featureName, sanitizedValue);
            }
            
            // Only include if has minimum required features
            if (sanitizedFeatures.size() >= 10) {
                fraudCase.setFeatures(sanitizedFeatures);
                sanitized.add(fraudCase);
            }
        }
        
        return sanitized;
    }
    
    /**
     * Step 5: Remove statistical outliers
     */
    private List<FraudCase> removeOutliers(List<FraudCase> data) {
        log.debug("SECURITY: Removing statistical outliers");
        
        // Calculate feature statistics
        Map<String, Double> featureMeans = calculateFeatureMeans(data);
        Map<String, Double> featureStdDevs = calculateFeatureStdDevs(data, featureMeans);
        
        List<FraudCase> filtered = new ArrayList<>();
        int outliersRemoved = 0;
        
        for (FraudCase fraudCase : data) {
            boolean isOutlier = false;
            
            for (Map.Entry<String, Double> entry : fraudCase.getFeatures().entrySet()) {
                String feature = entry.getKey();
                Double value = entry.getValue();
                
                Double mean = featureMeans.get(feature);
                Double stdDev = featureStdDevs.get(feature);
                
                if (mean != null && stdDev != null && stdDev > 0) {
                    double zScore = Math.abs((value - mean) / stdDev);
                    
                    if (zScore > outlierThreshold) {
                        isOutlier = true;
                        break;
                    }
                }
            }
            
            if (!isOutlier) {
                filtered.add(fraudCase);
            } else {
                outliersRemoved++;
            }
        }
        
        if (outliersRemoved > 0) {
            log.info("SECURITY: Removed {} outlier samples", outliersRemoved);
        }
        
        return filtered;
    }
    
    /**
     * Step 6: Balance dataset to prevent bias attacks
     */
    private List<FraudCase> balanceDataset(List<FraudCase> data) {
        log.debug("SECURITY: Balancing dataset");
        
        // Separate by class
        List<FraudCase> fraudCases = data.stream()
            .filter(c -> c.getActualFraudStatus() == FraudStatus.CONFIRMED_FRAUD)
            .collect(Collectors.toList());
            
        List<FraudCase> legitimateCases = data.stream()
            .filter(c -> c.getActualFraudStatus() == FraudStatus.CONFIRMED_LEGITIMATE)
            .collect(Collectors.toList());
        
        // Ensure balanced ratio (max 1:3 imbalance)
        int fraudCount = fraudCases.size();
        int legitimateCount = legitimateCases.size();
        
        if (fraudCount == 0 || legitimateCount == 0) {
            log.error("SECURITY: Insufficient class diversity in training data");
            throw new SecurityException("Training data must contain both fraud and legitimate cases");
        }
        
        double ratio = (double) legitimateCount / fraudCount;
        
        List<FraudCase> balanced = new ArrayList<>();
        
        if (ratio > 3.0) {
            // Too many legitimate cases - downsample
            Collections.shuffle(legitimateCases);
            int maxLegitimate = fraudCount * 3;
            balanced.addAll(fraudCases);
            balanced.addAll(legitimateCases.subList(0, Math.min(maxLegitimate, legitimateCount)));
            log.info("SECURITY: Downsampled legitimate cases from {} to {}", legitimateCount, maxLegitimate);
        } else if (ratio < 0.33) {
            // Too many fraud cases - downsample
            Collections.shuffle(fraudCases);
            int maxFraud = legitimateCount * 3;
            balanced.addAll(legitimateCases);
            balanced.addAll(fraudCases.subList(0, Math.min(maxFraud, fraudCount)));
            log.info("SECURITY: Downsampled fraud cases from {} to {}", fraudCount, maxFraud);
        } else {
            // Ratio is acceptable
            balanced.addAll(data);
        }
        
        Collections.shuffle(balanced); // Randomize order
        return balanced;
    }
    
    /**
     * Step 7: Apply differential privacy to protect individual data
     *
     * Uses SecureRandom to generate cryptographically secure Laplace noise for differential privacy.
     * This is critical because differential privacy relies on adding carefully calibrated random noise
     * to protect individual data points. Using a predictable random source (java.util.Random) could
     * allow attackers to remove the noise and extract sensitive information.
     */
    private List<FraudCase> applyDifferentialPrivacy(List<FraudCase> data) {
        log.debug("SECURITY: Applying differential privacy with SecureRandom");

        double epsilon = 0.1; // Privacy parameter

        for (FraudCase fraudCase : data) {
            Map<String, Double> features = fraudCase.getFeatures();
            Map<String, Double> noisyFeatures = new HashMap<>();

            for (Map.Entry<String, Double> entry : features.entrySet()) {
                String feature = entry.getKey();
                Double value = entry.getValue();

                // Add cryptographically secure Laplace noise for differential privacy
                double noise = generateLaplaceNoise(SECURE_RANDOM, epsilon);
                double noisyValue = value + noise;

                // Ensure value stays within bounds
                noisyValue = Math.max(minFeatureValue, Math.min(maxFeatureValue, noisyValue));
                noisyFeatures.put(feature, noisyValue);
            }

            fraudCase.setFeatures(noisyFeatures);
        }

        return data;
    }
    
    /**
     * Step 8: Final validation before training
     */
    private List<TrainingExample> performFinalValidation(List<FraudCase> data) {
        log.debug("SECURITY: Performing final validation");
        
        List<TrainingExample> examples = new ArrayList<>();
        
        for (FraudCase fraudCase : data) {
            // Final security checks
            if (!passesSecurityChecks(fraudCase)) {
                log.warn("SECURITY: Case {} failed final security checks", fraudCase.getId());
                continue;
            }
            
            // Convert to training example
            TrainingExample example = TrainingExample.builder()
                .id(UUID.randomUUID().toString()) // New ID to prevent tracking
                .features(fraudCase.getFeatures())
                .label(fraudCase.getActualFraudStatus() == FraudStatus.CONFIRMED_FRAUD ? 1.0 : 0.0)
                .weight(calculateSampleWeight(fraudCase))
                .timestamp(LocalDateTime.now())
                .validated(true)
                .build();
            
            examples.add(example);
        }
        
        // Ensure minimum training size
        if (examples.size() < 100) {
            log.error("SECURITY: Insufficient validated training data: {} samples", examples.size());
            throw new SecurityException("Insufficient validated training data for secure model training");
        }
        
        return examples;
    }
    
    // Helper methods
    
    private String calculateDataHash(FraudCase fraudCase) {
        try {
            String dataString = fraudCase.getId() + "|" + 
                               fraudCase.getUserId() + "|" + 
                               fraudCase.getAmount() + "|" + 
                               fraudCase.getActualFraudStatus();
            
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(dataIntegrityKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            
            byte[] hash = mac.doFinal(dataString.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
            
        } catch (Exception e) {
            log.error("Error calculating data hash", e);
            return "";
        }
    }
    
    private String createDataSignature(FraudCase fraudCase) {
        try {
            String sig = fraudCase.getUserId() + "|" + 
                        fraudCase.getAmount().setScale(2, RoundingMode.HALF_UP) + "|" +
                        fraudCase.getTransactionId();
            
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(sig.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
            
        } catch (Exception e) {
            return fraudCase.getId();
        }
    }
    
    private boolean isSuspiciousPattern(FraudCase fraudCase, List<FraudCase> existing) {
        // Check for suspicious patterns like:
        // - Too many similar cases from same user in short time
        // - Unusual feature combinations
        // - Known attack patterns
        
        long similarCount = existing.stream()
            .filter(c -> c.getUserId().equals(fraudCase.getUserId()))
            .filter(c -> Math.abs(c.getAmount().subtract(fraudCase.getAmount()).doubleValue()) < 0.01)
            .count();
        
        return similarCount > 10; // Suspicious if too many identical amounts
    }
    
    private boolean isLabelFlipped(FraudCase fraudCase, LabelStatistics stats) {
        // Detect potential label flipping attacks
        double fraudRatio = stats.getFraudRatio();
        
        // If user has consistent pattern but this case breaks it
        if (stats.getTotalCases() > 10) {
            if (fraudRatio > 0.9 && fraudCase.getActualFraudStatus() != FraudStatus.CONFIRMED_FRAUD) {
                return true; // Likely flipped
            }
            if (fraudRatio < 0.1 && fraudCase.getActualFraudStatus() == FraudStatus.CONFIRMED_FRAUD) {
                return true; // Likely flipped
            }
        }
        
        return false;
    }
    
    private boolean isValidFeatureName(String featureName) {
        // Prevent injection attacks through feature names
        return featureName != null && 
               featureName.matches("^[a-zA-Z0-9_]{1,50}$") &&
               !featureName.contains("script") &&
               !featureName.contains("exec") &&
               !featureName.contains("eval");
    }
    
    private Double sanitizeFeatureValue(String featureName, Double value) {
        if (value == null || !Double.isFinite(value)) {
            return 0.0;
        }
        
        // Clamp to reasonable range
        double sanitized = Math.max(minFeatureValue, Math.min(maxFeatureValue, value));
        
        // Round to prevent precision attacks
        return Math.round(sanitized * 10000.0) / 10000.0;
    }
    
    private void updateFeatureStatistics(String featureName, Double value) {
        featureStats.computeIfAbsent(featureName, k -> new FeatureStatistics())
            .addValue(value);
    }
    
    private Map<String, Double> calculateFeatureMeans(List<FraudCase> data) {
        Map<String, List<Double>> featureValues = new HashMap<>();
        
        for (FraudCase fraudCase : data) {
            for (Map.Entry<String, Double> entry : fraudCase.getFeatures().entrySet()) {
                featureValues.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                    .add(entry.getValue());
            }
        }
        
        Map<String, Double> means = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : featureValues.entrySet()) {
            double mean = entry.getValue().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            means.put(entry.getKey(), mean);
        }
        
        return means;
    }
    
    private Map<String, Double> calculateFeatureStdDevs(List<FraudCase> data, Map<String, Double> means) {
        Map<String, List<Double>> featureValues = new HashMap<>();
        
        for (FraudCase fraudCase : data) {
            for (Map.Entry<String, Double> entry : fraudCase.getFeatures().entrySet()) {
                featureValues.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                    .add(entry.getValue());
            }
        }
        
        Map<String, Double> stdDevs = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : featureValues.entrySet()) {
            String feature = entry.getKey();
            List<Double> values = entry.getValue();
            double mean = means.getOrDefault(feature, 0.0);
            
            double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
            
            stdDevs.put(feature, Math.sqrt(variance));
        }
        
        return stdDevs;
    }
    
    private double generateLaplaceNoise(Random random, double epsilon) {
        double u = random.nextDouble() - 0.5;
        double b = 1.0 / epsilon; // Scale parameter
        return -b * Math.signum(u) * Math.log(1 - 2 * Math.abs(u));
    }
    
    private boolean passesSecurityChecks(FraudCase fraudCase) {
        // Final security validation
        if (fraudCase.getFeatures() == null || fraudCase.getFeatures().isEmpty()) {
            return false;
        }
        
        if (fraudCase.getActualFraudStatus() == null) {
            return false;
        }
        
        // Check for required features
        Set<String> requiredFeatures = Set.of("amount", "hour_of_day", "tx_frequency");
        return fraudCase.getFeatures().keySet().containsAll(requiredFeatures);
    }
    
    private double calculateSampleWeight(FraudCase fraudCase) {
        // Weight based on confidence and recency
        double baseWeight = 1.0;
        
        // Increase weight for high-confidence labels
        if (fraudCase.getVerificationConfidence() != null) {
            baseWeight *= fraudCase.getVerificationConfidence();
        }
        
        // Decrease weight for older data
        if (fraudCase.getCreatedAt() != null) {
            long daysOld = java.time.temporal.ChronoUnit.DAYS.between(
                fraudCase.getCreatedAt(), LocalDateTime.now());
            baseWeight *= Math.exp(-daysOld / 30.0); // Exponential decay
        }
        
        return Math.max(0.1, Math.min(1.0, baseWeight));
    }
    
    private List<TrainingExample> convertToTrainingExamples(List<FraudCase> data) {
        // Fallback conversion without validation (not recommended)
        return data.stream()
            .map(fc -> TrainingExample.builder()
                .features(fc.getFeatures())
                .label(fc.getActualFraudStatus() == FraudStatus.CONFIRMED_FRAUD ? 1.0 : 0.0)
                .weight(1.0)
                .validated(false)
                .build())
            .collect(Collectors.toList());
    }
    
    private void recordValidationMetrics(int originalSize, int validatedSize) {
        double reductionRate = 1.0 - ((double) validatedSize / originalSize);
        log.info("SECURITY METRICS: Data reduction rate: {:.2%}, Samples validated: {}/{}", 
            reductionRate, validatedSize, originalSize);
        
        // Would integrate with metrics service
    }
    
    // Inner classes
    
    private static class DataIntegrityRecord {
        final String hash;
        final LocalDateTime timestamp;
        
        DataIntegrityRecord(String hash, LocalDateTime timestamp) {
            this.hash = hash;
            this.timestamp = timestamp;
        }
    }
    
    private static class LabelStatistics {
        private int fraudCount = 0;
        private int legitimateCount = 0;
        
        void addCase(FraudCase fraudCase) {
            if (fraudCase.getActualFraudStatus() == FraudStatus.CONFIRMED_FRAUD) {
                fraudCount++;
            } else {
                legitimateCount++;
            }
        }
        
        int getTotalCases() {
            return fraudCount + legitimateCount;
        }
        
        double getFraudRatio() {
            int total = getTotalCases();
            return total > 0 ? (double) fraudCount / total : 0.0;
        }
    }
    
    private static class FeatureStatistics {
        private final List<Double> values = new ArrayList<>();
        private double sum = 0.0;
        private double sumSquares = 0.0;
        
        void addValue(double value) {
            values.add(value);
            sum += value;
            sumSquares += value * value;
        }
        
        double getMean() {
            return values.isEmpty() ? 0.0 : sum / values.size();
        }
        
        double getStdDev() {
            if (values.size() < 2) return 0.0;
            double mean = getMean();
            double variance = (sumSquares / values.size()) - (mean * mean);
            return Math.sqrt(variance);
        }
    }
}