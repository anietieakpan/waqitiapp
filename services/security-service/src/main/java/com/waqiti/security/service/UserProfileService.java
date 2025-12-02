package com.waqiti.security.service;

import com.waqiti.security.model.*;
import com.waqiti.security.repository.AuthenticationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * User Profile Service
 * Manages user security profiles and behavioral baselines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final AuthenticationHistoryRepository historyRepository;

    private static final Duration PROFILE_WINDOW = Duration.ofDays(90);
    private static final int MIN_HISTORY_FOR_PROFILE = 10;

    /**
     * Get user security profile
     */
    public UserSecurityProfile getUserProfile(String userId) {
        try {
            log.debug("Building security profile for user: {}", userId);

            Instant cutoff = Instant.now().minus(PROFILE_WINDOW);
            List<AuthenticationHistory> history = historyRepository.findRecentByUserId(userId, cutoff);

            if (history.isEmpty()) {
                return createDefaultProfile(userId);
            }

            // Extract profile data
            Set<String> typicalLocations = extractTypicalLocations(history);
            Set<String> knownDevices = extractKnownDevices(history);
            Set<String> knownIPs = extractKnownIPs(history);
            Map<Integer, Double> typicalLoginHours = extractTypicalLoginHours(history);
            String riskLevel = calculateRiskLevel(history);

            // Calculate statistics
            long totalLogins = history.size();
            long successfulLogins = history.stream()
                .filter(AuthenticationHistory::isSuccessful)
                .count();
            long failedLogins = totalLogins - successfulLogins;

            double successRate = totalLogins > 0 ?
                (double) successfulLogins / totalLogins : 0.0;

            // Get last activity
            Instant lastActivity = history.stream()
                .map(AuthenticationHistory::getAuthenticatedAt)
                .max(Instant::compareTo)
                .orElse(null);

            // Get most common location
            String primaryLocation = history.stream()
                .map(AuthenticationHistory::getCountry)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

            // Build profile
            return UserSecurityProfile.builder()
                .userId(userId)
                .riskLevel(riskLevel)
                .typicalLocations(typicalLocations)
                .knownDevices(knownDevices)
                .knownIPs(knownIPs)
                .typicalLoginHours(typicalLoginHours)
                .lastActivity(lastActivity)
                .totalLogins(totalLogins)
                .failedLogins(failedLogins)
                .successRate(successRate)
                .primaryLocation(primaryLocation)
                .accountAge(calculateAccountAge(history))
                .profileCompleteness(calculateProfileCompleteness(history))
                .build();

        } catch (Exception e) {
            log.error("Error building profile for user {}: {}", userId, e.getMessage(), e);
            return createDefaultProfile(userId);
        }
    }

    /**
     * Update user behavior baseline
     */
    public void updateBehaviorBaseline(String userId, UserBehaviorUpdate update) {
        try {
            log.debug("Updating behavior baseline for user: {}", userId);

            // Create authentication history record
            AuthenticationHistory history = AuthenticationHistory.builder()
                .historyId(UUID.randomUUID().toString())
                .userId(userId)
                .authenticatedAt(update.getLoginTime() != null ? update.getLoginTime() : Instant.now())
                .successful(update.getSuccessful() != null ? update.getSuccessful() : true)
                .ipAddress(update.getIpAddress())
                .deviceId(update.getDeviceId())
                .userAgent(update.getUserAgent())
                .country(update.getLoginLocation() != null ?
                    update.getLoginLocation().get("country") : null)
                .city(update.getLoginLocation() != null ?
                    update.getLoginLocation().get("city") : null)
                .latitude(null) // Would be extracted from location service
                .longitude(null)
                .authMethod(update.getAuthMethod())
                .metadata(update.getAdditionalContext())
                .build();

            historyRepository.save(history);
            log.debug("Behavior baseline updated for user: {}", userId);

        } catch (Exception e) {
            log.error("Error updating behavior baseline for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Check if behavior matches user profile
     */
    public boolean matchesUserProfile(AuthenticationEvent event, UserSecurityProfile profile) {
        if (profile == null) {
            return false;
        }

        int matches = 0;
        int checks = 0;

        // Check location
        if (event.getCountry() != null && profile.getTypicalLocations() != null) {
            checks++;
            if (profile.getTypicalLocations().contains(event.getCountry())) {
                matches++;
            }
        }

        // Check device
        if (event.getDeviceId() != null && profile.getKnownDevices() != null) {
            checks++;
            if (profile.getKnownDevices().contains(event.getDeviceId())) {
                matches++;
            }
        }

        // Check IP
        if (event.getIpAddress() != null && profile.getKnownIPs() != null) {
            checks++;
            if (profile.getKnownIPs().contains(event.getIpAddress())) {
                matches++;
            }
        }

        // Need at least 50% match
        return checks > 0 && ((double) matches / checks) >= 0.5;
    }

    /**
     * Calculate user risk score based on profile
     */
    public int calculateUserRiskScore(UserSecurityProfile profile) {
        if (profile == null) {
            return 50; // Unknown user = medium risk
        }

        int riskScore = 0;

        // Risk level assessment
        if ("HIGH".equals(profile.getRiskLevel())) {
            riskScore += 40;
        } else if ("MEDIUM".equals(profile.getRiskLevel())) {
            riskScore += 20;
        }

        // Failed login rate
        if (profile.getSuccessRate() < 0.8) {
            riskScore += 20;
        }

        // Account age (new accounts are riskier)
        if (profile.getAccountAge() != null && profile.getAccountAge() < 7) {
            riskScore += 15;
        }

        // Recent activity
        if (profile.getLastActivity() != null) {
            long daysSinceActivity = Duration.between(
                profile.getLastActivity(),
                Instant.now()
            ).toDays();

            if (daysSinceActivity > 90) {
                riskScore += 10; // Dormant account
            }
        }

        // Profile completeness
        if (profile.getProfileCompleteness() < 0.5) {
            riskScore += 10;
        }

        return Math.min(riskScore, 100);
    }

    /**
     * Extract typical locations from history
     */
    private Set<String> extractTypicalLocations(List<AuthenticationHistory> history) {
        // Get countries that appear in at least 10% of successful logins
        long totalSuccessful = history.stream()
            .filter(AuthenticationHistory::isSuccessful)
            .count();

        if (totalSuccessful == 0) {
            return new HashSet<>();
        }

        Map<String, Long> countryCounts = history.stream()
            .filter(AuthenticationHistory::isSuccessful)
            .map(AuthenticationHistory::getCountry)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(c -> c, Collectors.counting()));

        return countryCounts.entrySet().stream()
            .filter(e -> e.getValue() >= (totalSuccessful * 0.1))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    /**
     * Extract known devices from history
     */
    private Set<String> extractKnownDevices(List<AuthenticationHistory> history) {
        return history.stream()
            .filter(AuthenticationHistory::isSuccessful)
            .map(AuthenticationHistory::getDeviceId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    /**
     * Extract known IPs from history
     */
    private Set<String> extractKnownIPs(List<AuthenticationHistory> history) {
        // Get IPs that appear in at least 2 successful logins
        Map<String, Long> ipCounts = history.stream()
            .filter(AuthenticationHistory::isSuccessful)
            .map(AuthenticationHistory::getIpAddress)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(ip -> ip, Collectors.counting()));

        return ipCounts.entrySet().stream()
            .filter(e -> e.getValue() >= 2)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    /**
     * Extract typical login hours from history
     */
    private Map<Integer, Double> extractTypicalLoginHours(List<AuthenticationHistory> history) {
        Map<Integer, Long> hourCounts = history.stream()
            .filter(AuthenticationHistory::isSuccessful)
            .collect(Collectors.groupingBy(
                h -> h.getAuthenticatedAt().atZone(java.time.ZoneId.systemDefault()).getHour(),
                Collectors.counting()
            ));

        long total = hourCounts.values().stream().mapToLong(Long::longValue).sum();

        if (total == 0) {
            return new HashMap<>();
        }

        Map<Integer, Double> hourProbabilities = new HashMap<>();
        for (Map.Entry<Integer, Long> entry : hourCounts.entrySet()) {
            hourProbabilities.put(
                entry.getKey(),
                entry.getValue() / (double) total
            );
        }

        return hourProbabilities;
    }

    /**
     * Calculate risk level based on history
     */
    private String calculateRiskLevel(List<AuthenticationHistory> history) {
        long total = history.size();
        long failed = history.stream()
            .filter(h -> !h.isSuccessful())
            .count();

        double failureRate = total > 0 ? (double) failed / total : 0;

        if (failureRate > 0.3) return "HIGH";
        if (failureRate > 0.1) return "MEDIUM";
        return "LOW";
    }

    /**
     * Calculate account age in days
     */
    private Integer calculateAccountAge(List<AuthenticationHistory> history) {
        if (history.isEmpty()) {
            return null;
        }

        Instant firstAuth = history.stream()
            .map(AuthenticationHistory::getAuthenticatedAt)
            .min(Instant::compareTo)
            .orElse(null);

        if (firstAuth == null) {
            return null;
        }

        return (int) Duration.between(firstAuth, Instant.now()).toDays();
    }

    /**
     * Calculate profile completeness (0-1)
     */
    private Double calculateProfileCompleteness(List<AuthenticationHistory> history) {
        if (history.isEmpty()) {
            return 0.0;
        }

        int completenessScore = 0;
        int maxScore = 5;

        // Has location data
        if (history.stream().anyMatch(h -> h.getCountry() != null)) {
            completenessScore++;
        }

        // Has device data
        if (history.stream().anyMatch(h -> h.getDeviceId() != null)) {
            completenessScore++;
        }

        // Has IP data
        if (history.stream().anyMatch(h -> h.getIpAddress() != null)) {
            completenessScore++;
        }

        // Has sufficient history
        if (history.size() >= MIN_HISTORY_FOR_PROFILE) {
            completenessScore++;
        }

        // Has diverse data (multiple locations/devices)
        long uniqueCountries = history.stream()
            .map(AuthenticationHistory::getCountry)
            .filter(Objects::nonNull)
            .distinct()
            .count();

        if (uniqueCountries >= 2) {
            completenessScore++;
        }

        return (double) completenessScore / maxScore;
    }

    /**
     * Create default profile for new/unknown users
     */
    private UserSecurityProfile createDefaultProfile(String userId) {
        return UserSecurityProfile.builder()
            .userId(userId)
            .riskLevel("MEDIUM")
            .typicalLocations(new HashSet<>())
            .knownDevices(new HashSet<>())
            .knownIPs(new HashSet<>())
            .typicalLoginHours(new HashMap<>())
            .lastActivity(null)
            .totalLogins(0L)
            .failedLogins(0L)
            .successRate(0.0)
            .primaryLocation(null)
            .accountAge(null)
            .profileCompleteness(0.0)
            .build();
    }
}
