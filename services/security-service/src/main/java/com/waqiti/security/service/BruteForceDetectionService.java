package com.waqiti.security.service;

import com.waqiti.security.model.AuthenticationEvent;
import com.waqiti.security.model.BruteForceResult;
import com.waqiti.security.repository.FailedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Brute Force Detection Service
 * Detects brute force attack patterns
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BruteForceDetectionService {

    private final FailedEventRepository failedEventRepository;

    private static final Duration DETECTION_WINDOW = Duration.ofHours(1);
    private static final long THRESHOLD = 5;

    /**
     * Detect brute force attack
     */
    public BruteForceResult detectBruteForce(AuthenticationEvent event) {
        try {
            Instant cutoff = Instant.now().minus(DETECTION_WINDOW);

            // Check failures by IP
            long ipFailures = failedEventRepository.countRecentByIpAddress(
                event.getIpAddress(),
                cutoff
            );

            // Check failures by user
            long userFailures = failedEventRepository.countRecentByUserId(
                event.getUserId(),
                cutoff
            );

            boolean isBruteForce = ipFailures >= THRESHOLD || userFailures >= THRESHOLD;
            int riskScore = calculateRiskScore(ipFailures, userFailures);

            return BruteForceResult.builder()
                .isBruteForce(isBruteForce)
                .attemptCount(Math.max(ipFailures, userFailures))
                .timeWindow(DETECTION_WINDOW.toMinutes())
                .riskScore(riskScore)
                .build();

        } catch (Exception e) {
            log.error("Error detecting brute force: {}", e.getMessage(), e);
            return BruteForceResult.builder()
                .isBruteForce(false)
                .attemptCount(0L)
                .timeWindow(DETECTION_WINDOW.toMinutes())
                .riskScore(0)
                .build();
        }
    }

    /**
     * Calculate risk score based on attempt count
     */
    private int calculateRiskScore(long ipFailures, long userFailures) {
        long maxFailures = Math.max(ipFailures, userFailures);

        if (maxFailures >= 20) return 100;
        if (maxFailures >= 10) return 80;
        if (maxFailures >= 5) return 60;

        return (int) (maxFailures * 10);
    }
}
