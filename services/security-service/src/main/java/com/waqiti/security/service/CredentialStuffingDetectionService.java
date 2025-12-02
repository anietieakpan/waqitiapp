package com.waqiti.security.service;

import com.waqiti.security.model.AuthenticationEvent;
import com.waqiti.security.model.CredentialStuffingResult;
import com.waqiti.security.repository.FailedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Credential Stuffing Detection Service
 * Detects credential stuffing attack patterns
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialStuffingDetectionService {

    private final FailedEventRepository failedEventRepository;

    private static final Duration DETECTION_WINDOW = Duration.ofHours(1);
    private static final long UNIQUE_USER_THRESHOLD = 5;

    /**
     * Detect credential stuffing attack
     */
    public CredentialStuffingResult detectCredentialStuffing(AuthenticationEvent event) {
        try {
            Instant cutoff = Instant.now().minus(DETECTION_WINDOW);

            // Find patterns: multiple user attempts from same IP
            List<Object[]> patterns = failedEventRepository.findCredentialStuffingPatterns(
                cutoff,
                UNIQUE_USER_THRESHOLD
            );

            boolean isCredentialStuffing = patterns.stream()
                .anyMatch(p -> p[0].equals(event.getIpAddress()));

            long uniqueUsers = patterns.stream()
                .filter(p -> p[0].equals(event.getIpAddress()))
                .mapToLong(p -> ((Number) p[1]).longValue())
                .findFirst()
                .orElse(0L);

            int riskScore = calculateRiskScore(uniqueUsers);

            return CredentialStuffingResult.builder()
                .isCredentialStuffing(isCredentialStuffing)
                .uniqueUserAttempts(uniqueUsers)
                .timeWindow(DETECTION_WINDOW.toMinutes())
                .riskScore(riskScore)
                .build();

        } catch (Exception e) {
            log.error("Error detecting credential stuffing: {}", e.getMessage(), e);
            return CredentialStuffingResult.builder()
                .isCredentialStuffing(false)
                .uniqueUserAttempts(0L)
                .timeWindow(DETECTION_WINDOW.toMinutes())
                .riskScore(0)
                .build();
        }
    }

    /**
     * Calculate risk score based on unique user attempts
     */
    private int calculateRiskScore(long uniqueUsers) {
        if (uniqueUsers >= 50) return 100;
        if (uniqueUsers >= 20) return 80;
        if (uniqueUsers >= 10) return 60;
        if (uniqueUsers >= 5) return 40;

        return (int) (uniqueUsers * 5);
    }
}
