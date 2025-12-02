package com.waqiti.frauddetection.service;

import com.waqiti.frauddetection.dto.FraudCheckRequest;
import com.waqiti.frauddetection.entity.DeviceProfile;
import com.waqiti.frauddetection.repository.DeviceProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Device Profile Service
 *
 * Tracks device fingerprints, behavioral patterns, and risk metrics
 * for fraud detection. Identifies device farms, device sharing,
 * and impossible travel scenarios.
 *
 * PRODUCTION-GRADE IMPLEMENTATION
 * - Device fingerprint tracking and analysis
 * - Multi-user device detection (device sharing)
 * - Device farm detection (automated fraud)
 * - Impossible travel detection
 * - Geographic anomaly detection
 * - Risk scoring based on device behavior
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0 - Production Implementation
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceProfileService {

    private final DeviceProfileRepository deviceProfileRepository;

    // Risk thresholds
    private static final int MAX_USERS_PER_DEVICE = 3; // Normal device sharing threshold
    private static final int DEVICE_FARM_USER_THRESHOLD = 10; // Likely device farm
    private static final int IMPOSSIBLE_TRAVEL_KM_PER_HOUR = 800; // Speed of commercial flight

    /**
     * Get device profile with caching
     */
    @Cacheable(value = "deviceProfiles", key = "#deviceFingerprint", unless = "#result == null")
    public Optional<DeviceProfile> getDeviceProfile(String deviceFingerprint) {
        log.debug("Fetching device profile for fingerprint: {}", maskFingerprint(deviceFingerprint));

        if (deviceFingerprint == null || deviceFingerprint.trim().isEmpty()) {
            log.warn("Attempted to fetch profile with null/empty device fingerprint");
            return Optional.empty();
        }

        return deviceProfileRepository.findByDeviceFingerprint(deviceFingerprint);
    }

    /**
     * Update device profile after transaction
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @CacheEvict(value = "deviceProfiles", key = "#deviceFingerprint")
    public void updateDeviceProfile(
            String deviceFingerprint,
            UUID userId,
            FraudCheckRequest request,
            double riskScore,
            boolean fraudDetected) {

        log.debug("Updating device profile for fingerprint: {}, user: {}",
            maskFingerprint(deviceFingerprint), userId);

        try {
            DeviceProfile profile = deviceProfileRepository
                .findByDeviceFingerprint(deviceFingerprint)
                .orElseGet(() -> createNewDeviceProfile(deviceFingerprint));

            // Update transaction statistics
            profile.setTotalTransactions(profile.getTotalTransactions() + 1);
            profile.setLastSeen(LocalDateTime.now());

            // Track associated users
            updateAssociatedUsers(profile, userId);

            // Update location tracking
            if (request.getCountryCode() != null) {
                updateLocationTracking(profile, request.getCountryCode(),
                    request.getLatitude(), request.getLongitude());
            }

            // Update risk metrics
            updateDeviceRiskMetrics(profile, riskScore, fraudDetected);

            // Detect anomalies
            detectDeviceAnomalies(profile, userId, request);

            // Calculate risk level
            profile.setCurrentRiskLevel(determineDeviceRiskLevel(profile));

            // Save updated profile
            deviceProfileRepository.save(profile);

            log.info("Device profile updated successfully for: {}", maskFingerprint(deviceFingerprint));

        } catch (Exception e) {
            log.error("Error updating device profile for: {}", maskFingerprint(deviceFingerprint), e);
            // Don't throw - device profile update is non-critical
        }
    }

    /**
     * Detect device sharing (multiple users on one device)
     */
    public boolean detectDeviceSharing(String deviceFingerprint) {
        Optional<DeviceProfile> profileOpt = getDeviceProfile(deviceFingerprint);

        if (profileOpt.isEmpty()) {
            return false;
        }

        DeviceProfile profile = profileOpt.get();
        int userCount = profile.getAssociatedUserIds() != null ?
            profile.getAssociatedUserIds().size() : 0;

        boolean isShared = userCount > MAX_USERS_PER_DEVICE;

        if (isShared) {
            log.warn("Device sharing detected: {} - {} associated users",
                maskFingerprint(deviceFingerprint), userCount);
        }

        return isShared;
    }

    /**
     * Detect device farm (automated fraud operation)
     */
    public boolean detectDeviceFarm(String deviceFingerprint) {
        Optional<DeviceProfile> profileOpt = getDeviceProfile(deviceFingerprint);

        if (profileOpt.isEmpty()) {
            return false;
        }

        DeviceProfile profile = profileOpt.get();

        // Device farm indicators:
        // 1. Large number of associated users
        int userCount = profile.getAssociatedUserIds() != null ?
            profile.getAssociatedUserIds().size() : 0;

        // 2. High transaction velocity
        long accountAge = ChronoUnit.DAYS.between(profile.getFirstSeen(), LocalDateTime.now());
        double transactionsPerDay = accountAge > 0 ?
            (double) profile.getTotalTransactions() / accountAge : 0;

        // 3. High fraud rate
        boolean highFraudRate = profile.getFraudRate() > 0.1; // 10% fraud rate

        boolean isDeviceFarm = userCount >= DEVICE_FARM_USER_THRESHOLD ||
                               (userCount > 5 && transactionsPerDay > 50) ||
                               (userCount > 5 && highFraudRate);

        if (isDeviceFarm) {
            log.error("DEVICE FARM DETECTED: {} - Users: {}, Tx/Day: {}, Fraud Rate: {}",
                maskFingerprint(deviceFingerprint), userCount, transactionsPerDay, profile.getFraudRate());
        }

        return isDeviceFarm;
    }

    /**
     * Detect impossible travel (device location change too fast)
     */
    public boolean detectImpossibleTravel(
            String deviceFingerprint,
            String currentCountry,
            Double currentLatitude,
            Double currentLongitude) {

        Optional<DeviceProfile> profileOpt = getDeviceProfile(deviceFingerprint);

        if (profileOpt.isEmpty()) {
            return false;
        }

        DeviceProfile profile = profileOpt.get();

        // No previous location data
        if (profile.getLastKnownLatitude() == null ||
            profile.getLastKnownLongitude() == null ||
            profile.getLastSeen() == null) {
            return false;
        }

        // Calculate distance traveled
        double distance = calculateDistance(
            profile.getLastKnownLatitude(), profile.getLastKnownLongitude(),
            currentLatitude, currentLongitude
        );

        // Calculate time elapsed
        long minutesElapsed = ChronoUnit.MINUTES.between(profile.getLastSeen(), LocalDateTime.now());

        if (minutesElapsed == 0) {
            return false; // Same time, no travel
        }

        // Calculate speed (km/h)
        double kmPerHour = (distance / minutesElapsed) * 60;

        boolean impossibleTravel = kmPerHour > IMPOSSIBLE_TRAVEL_KM_PER_HOUR;

        if (impossibleTravel) {
            log.error("IMPOSSIBLE TRAVEL DETECTED: {} - Distance: {}km in {}min ({}km/h)",
                maskFingerprint(deviceFingerprint), String.format("%.2f", distance),
                minutesElapsed, String.format("%.2f", kmPerHour));
        }

        return impossibleTravel;
    }

    /**
     * Get device risk assessment
     */
    @Cacheable(value = "deviceRiskAssessment", key = "#deviceFingerprint")
    public DeviceRiskAssessment getRiskAssessment(String deviceFingerprint) {
        log.debug("Getting risk assessment for device: {}", maskFingerprint(deviceFingerprint));

        Optional<DeviceProfile> profileOpt = getDeviceProfile(deviceFingerprint);

        if (profileOpt.isEmpty()) {
            return DeviceRiskAssessment.builder()
                .deviceFingerprint(deviceFingerprint)
                .riskLevel("UNKNOWN")
                .riskScore(0.6) // Medium-high default for unknown devices
                .isNewDevice(true)
                .isDeviceFarm(false)
                .isSharedDevice(false)
                .requiresAdditionalVerification(true)
                .build();
        }

        DeviceProfile profile = profileOpt.get();

        return DeviceRiskAssessment.builder()
            .deviceFingerprint(deviceFingerprint)
            .riskLevel(profile.getCurrentRiskLevel())
            .riskScore(profile.getAverageRiskScore())
            .fraudCount(profile.getFraudCount())
            .totalTransactions(profile.getTotalTransactions())
            .associatedUsers(profile.getAssociatedUserIds() != null ?
                profile.getAssociatedUserIds().size() : 0)
            .deviceAge(calculateDeviceAge(profile.getFirstSeen()))
            .isNewDevice(profile.getTotalTransactions() < 5)
            .isDeviceFarm(detectDeviceFarm(deviceFingerprint))
            .isSharedDevice(detectDeviceSharing(deviceFingerprint))
            .requiresAdditionalVerification(shouldRequireAdditionalVerification(profile))
            .lastSeen(profile.getLastSeen())
            .build();
    }

    /**
     * Update associated users list
     */
    private void updateAssociatedUsers(DeviceProfile profile, UUID userId) {
        Set<UUID> users = profile.getAssociatedUserIds() != null ?
            new HashSet<>(profile.getAssociatedUserIds()) : new HashSet<>();

        users.add(userId);
        profile.setAssociatedUserIds(new ArrayList<>(users));

        // Alert if device sharing threshold exceeded
        if (users.size() > MAX_USERS_PER_DEVICE) {
            log.warn("Device sharing threshold exceeded: {} - {} users",
                maskFingerprint(profile.getDeviceFingerprint()), users.size());
        }
    }

    /**
     * Update location tracking
     */
    private void updateLocationTracking(
            DeviceProfile profile,
            String countryCode,
            Double latitude,
            Double longitude) {

        // Check for impossible travel before updating
        if (latitude != null && longitude != null) {
            boolean impossibleTravel = detectImpossibleTravel(
                profile.getDeviceFingerprint(),
                countryCode,
                latitude,
                longitude
            );

            if (impossibleTravel) {
                profile.setImpossibleTravelDetected(true);
                profile.setImpossibleTravelCount(profile.getImpossibleTravelCount() + 1);
            }
        }

        // Update location
        profile.setLastKnownCountry(countryCode);
        profile.setLastKnownLatitude(latitude);
        profile.setLastKnownLongitude(longitude);

        // Track known countries
        Set<String> countries = profile.getKnownCountries() != null ?
            new HashSet<>(Arrays.asList(profile.getKnownCountries().split(","))) :
            new HashSet<>();
        countries.add(countryCode);
        profile.setKnownCountries(String.join(",", countries));
    }

    /**
     * Update device risk metrics
     */
    private void updateDeviceRiskMetrics(DeviceProfile profile, double riskScore, boolean fraudDetected) {
        // Exponential moving average for risk score
        double alpha = 0.3;
        double currentAvgRisk = profile.getAverageRiskScore();
        double newAvgRisk = (alpha * riskScore) + ((1 - alpha) * currentAvgRisk);
        profile.setAverageRiskScore(newAvgRisk);

        // Update fraud metrics
        if (fraudDetected) {
            profile.setFraudCount(profile.getFraudCount() + 1);
            profile.setLastFraudDate(LocalDateTime.now());

            // Calculate fraud rate
            if (profile.getTotalTransactions() > 0) {
                double fraudRate = (double) profile.getFraudCount() / profile.getTotalTransactions();
                profile.setFraudRate(fraudRate);
            }
        }
    }

    /**
     * Detect device anomalies
     */
    private void detectDeviceAnomalies(DeviceProfile profile, UUID userId, FraudCheckRequest request) {
        // Check for device farm
        if (detectDeviceFarm(profile.getDeviceFingerprint())) {
            log.error("Device farm detected during transaction: {}",
                maskFingerprint(profile.getDeviceFingerprint()));
        }

        // Check for device sharing
        if (detectDeviceSharing(profile.getDeviceFingerprint())) {
            log.warn("Device sharing detected during transaction: {}",
                maskFingerprint(profile.getDeviceFingerprint()));
        }
    }

    /**
     * Determine device risk level
     */
    private String determineDeviceRiskLevel(DeviceProfile profile) {
        // Critical risk criteria
        if (detectDeviceFarm(profile.getDeviceFingerprint())) {
            return "CRITICAL";
        }

        // High risk criteria
        if (profile.getFraudCount() >= 3 || profile.getAverageRiskScore() > 0.75) {
            return "HIGH";
        }

        if (profile.getImpossibleTravelDetected() &&
            profile.getImpossibleTravelCount() > 2) {
            return "HIGH";
        }

        // Medium risk criteria
        if (detectDeviceSharing(profile.getDeviceFingerprint())) {
            return "MEDIUM";
        }

        if (profile.getFraudCount() > 0 || profile.getAverageRiskScore() > 0.50) {
            return "MEDIUM";
        }

        return "LOW";
    }

    /**
     * Determine if additional verification is required
     */
    private boolean shouldRequireAdditionalVerification(DeviceProfile profile) {
        // Require verification if:
        // - Device farm detected
        // - High fraud rate
        // - Impossible travel detected
        // - New device with high-risk indicators

        if (detectDeviceFarm(profile.getDeviceFingerprint())) {
            return true;
        }

        if (profile.getFraudRate() > 0.1) { // 10% fraud rate
            return true;
        }

        if (profile.getImpossibleTravelDetected()) {
            return true;
        }

        // New device with suspicious activity
        long deviceAge = calculateDeviceAge(profile.getFirstSeen());
        if (deviceAge < 7 && profile.getTotalTransactions() > 50) {
            return true;
        }

        return false;
    }

    /**
     * Create new device profile
     */
    private DeviceProfile createNewDeviceProfile(String deviceFingerprint) {
        log.info("Creating new device profile for fingerprint: {}", maskFingerprint(deviceFingerprint));

        return DeviceProfile.builder()
            .deviceFingerprint(deviceFingerprint)
            .firstSeen(LocalDateTime.now())
            .lastSeen(LocalDateTime.now())
            .totalTransactions(0L)
            .associatedUserIds(new ArrayList<>())
            .averageRiskScore(0.0)
            .fraudCount(0)
            .fraudRate(0.0)
            .currentRiskLevel("UNKNOWN")
            .impossibleTravelDetected(false)
            .impossibleTravelCount(0)
            .build();
    }

    /**
     * Calculate device age in days
     */
    private long calculateDeviceAge(LocalDateTime firstSeen) {
        if (firstSeen == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(firstSeen, LocalDateTime.now());
    }

    /**
     * Calculate distance between two coordinates (Haversine formula)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS = 6371; // kilometers

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    /**
     * Mask device fingerprint for logging (PII protection)
     */
    private String maskFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.length() < 8) {
            return "***";
        }
        return fingerprint.substring(0, 4) + "..." +
               fingerprint.substring(fingerprint.length() - 4);
    }

    /**
     * DTO for device risk assessment
     */
    @lombok.Builder
    @lombok.Data
    public static class DeviceRiskAssessment {
        private String deviceFingerprint;
        private String riskLevel;
        private double riskScore;
        private int fraudCount;
        private long totalTransactions;
        private int associatedUsers;
        private long deviceAge;
        private boolean isNewDevice;
        private boolean isDeviceFarm;
        private boolean isSharedDevice;
        private boolean requiresAdditionalVerification;
        private LocalDateTime lastSeen;
    }
}
